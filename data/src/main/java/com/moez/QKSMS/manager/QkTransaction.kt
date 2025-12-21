/*
 * Copyright 2013 Jacob Klinker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (C) 2025
 *
 * modified from original for QUIK
 *
 * This file is part of QUIK.
 *
 * QUIK is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QUIK is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QUIK.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.moez.QKSMS.manager

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SmsMessage
import androidx.core.content.contentValuesOf
import androidx.core.net.toUri
import com.android.mms.MmsConfig
import com.android.mms.dom.smil.parser.SmilXmlSerializer
import com.android.mms.util.DownloadManager
import com.android.mms.util.RateController
import com.google.android.mms.ContentType
import com.google.android.mms.InvalidHeaderValueException
import com.google.android.mms.MMSPart
import com.google.android.mms.MmsException
import com.google.android.mms.pdu_alt.CharacterSets
import com.google.android.mms.pdu_alt.EncodedStringValue
import com.google.android.mms.pdu_alt.GenericPdu
import com.google.android.mms.pdu_alt.PduBody
import com.google.android.mms.pdu_alt.PduComposer
import com.google.android.mms.pdu_alt.PduHeaders
import com.google.android.mms.pdu_alt.PduPart
import com.google.android.mms.pdu_alt.PduPersister
import com.google.android.mms.pdu_alt.SendReq
import com.google.android.mms.smil.SmilHelper
import com.google.android.mms.util_alt.SqliteWrapper
import com.klinker.android.send_message.MmsSentReceiver
import com.klinker.android.send_message.SmsManagerFactory
import com.klinker.android.send_message.StripAccents
import com.klinker.android.send_message.Utils
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Calendar
import java.util.UUID

object QkTransaction {
    private const val DEFAULT_EXPIRY_TIME: Long = (7 * 24 * 60 * 60).toLong()
    private const val DEFAULT_PRIORITY: Int = PduHeaders.PRIORITY_NORMAL

    private fun getNumSmsPages(stripUnicode: Boolean, body: String?): Int {
        var text = body
        if (stripUnicode) {
            text = StripAccents.stripAccents(text)
        }

        val data = SmsMessage.calculateLength(text, false)
        return data[0]
    }

    private fun checkMMS(
        body: String?, numToAddresses: Int, numParts: Int, asGroup: Boolean, longAsMms: Boolean,
        stripUnicode: Boolean
    ): Boolean {
        return (numParts > 0) ||
                ((numToAddresses > 1) && asGroup) ||
                (longAsMms && getNumSmsPages(stripUnicode, body) > 3)
    }

    fun createMessage(
        context: Context,
        subscriptionId: Int,
        body: String?,
        signature: String,
        toAddresses: Array<String>,
        parts: MutableCollection<MMSPart>,
        asGroup: Boolean,
        longAsMms: Boolean,
        stripUnicode: Boolean
    ): Uri {
        Timber.v("creating message")

        // add signature to original text to be saved in db
        var text = body
        if (signature.isNotEmpty())
            text += "\n$signature"

        // strip unicode if flagged to do so
        if (stripUnicode)
            text = StripAccents.stripAccents(text)

        if (checkMMS(text, toAddresses.size, parts.size, asGroup, longAsMms, stripUnicode)) {
            RateController.init(context)
            DownloadManager.init(context)

            return createMmsMessage(context, subscriptionId, text, toAddresses, parts)
        }

        return createSmsMessage(context, subscriptionId, text, toAddresses)
    }

    fun explodeMessage(context: Context, messageUri: Uri, asGroup: Boolean): Collection<Uri> {
        Timber.v("exploding message uri $messageUri")

        if (asGroup)
            return listOf(messageUri)

        return if (messageUri.toString().startsWith(Telephony.Mms.CONTENT_URI.toString()))
            explodeMmsMessage(context, messageUri)
            else explodeSmsMessage(context, messageUri)
    }

    fun sendMessage(context: Context, messageUri: Uri, sentIntent: Intent, deliveryIntent: Intent?
    ): Boolean {
        Timber.v("sending message uri $messageUri")

        return if (messageUri.toString().startsWith(Telephony.Mms.CONTENT_URI.toString()))
            sendMmsMessage(context, messageUri, sentIntent)
            else sendSmsMessage(context, messageUri, sentIntent, deliveryIntent)
    }

    private fun getProviderRecipients(context: Context, threadId: Long): Collection<String> {
        // get recipient ids
        val spaceSeparatedRecipients = context.contentResolver.query(
            (Telephony.MmsSms.CONTENT_CONVERSATIONS_URI.toString() + "?simple=true").toUri(),
            arrayOf(Telephony.Threads._ID, Telephony.Threads.RECIPIENT_IDS),
            Telephony.Threads._ID + " = ?", arrayOf(threadId.toString()),
            null
        )
            ?.use { cursor ->
                if (cursor.moveToFirst())
                    cursor.getString(1)
                else {
                    Timber.e("recipient id provider db query returned no rows")
                    ""
                }
            }
            ?: let {
                Timber.e("recipient id provider db query failed")
                ""
            }

        if (spaceSeparatedRecipients.isEmpty()) {
            Timber.e("failed to get provider recipient ids for thread $threadId")
            return listOf()
        }

        // get addresses from recipient ids
        val addresses = ArrayList<String>()

        context.contentResolver.query(
            "content://mms-sms/canonical-addresses".toUri(),
            arrayOf(
                Telephony.CanonicalAddressesColumns._ID,
                Telephony.CanonicalAddressesColumns.ADDRESS
            ),
            Telephony.CanonicalAddressesColumns._ID + " in (" +
                    spaceSeparatedRecipients.replace(' ', ',') + ")",
            null,
            null
        )
            ?.use { cursor ->
                if (cursor.moveToFirst())
                    do {
                        addresses.add(cursor.getString(1))
                    } while (cursor.moveToNext())
                else
                    Timber.e("address provider db query returned no rows")
            }
            ?: let { Timber.e("address provider db query failed") }

        if (addresses.isEmpty())
            Timber.e("no addresses from recipient ids for thread $threadId")

        return addresses
    }

    private fun createSmsMessage(
        context: Context, subscriptionId: Int, body: String?, addresses: Array<String>
    ): Uri {
        val threadId = Utils.getOrCreateThreadId(context, addresses.toSet())

        val cal = Calendar.getInstance()
        val values = ContentValues()

        values.put(Telephony.Sms.ADDRESS, if (addresses.size > 1) "" else addresses[0])
        values.put(Telephony.Sms.BODY, body)
        values.put(Telephony.Sms.DATE, cal.timeInMillis.toString() + "")
        values.put(Telephony.Sms.READ, 1)
        values.put(Telephony.Sms.TYPE, 4)
        values.put(Telephony.Sms.THREAD_ID, threadId)
        values.put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)

        val messageUri = context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
        if (messageUri == null) {
            Timber.e("unable to create sms provider record")
            return Uri.EMPTY
        }

        return messageUri
    }

    private fun explodeSmsMessage(context: Context, messageUri: Uri): Collection<Uri> {
        val retVal: MutableCollection<Uri> = ArrayList()

        var threadId: Long = -1
        var subscriptionId = -1
        var body = ""

        // get pertinent message values of source message
        context.contentResolver.query(
            messageUri,
            arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.SUBSCRIPTION_ID, Telephony.Sms.BODY),
            null, null, null
        )
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    threadId = cursor.getInt(0).toLong()
                    subscriptionId = cursor.getInt(1)
                    body = cursor.getString(2)
                } else {
                    Timber.e("provider db query returned no rows")
                    return retVal
                }
            }
            ?: let {
                Timber.e("provider db query failed")
                return retVal
            }

        // get thread recipients
        val addresses = getProviderRecipients(context, threadId)

        // if zero (!) or one recipient in thread, return original message only
        if (addresses.size <= 1) {
            retVal.add(messageUri)
            return retVal
        }

        // create individual messages
        for (address in addresses)
            retVal.add(createSmsMessage(context, subscriptionId, body, arrayOf(address)))

        return retVal
    }

    private fun sendSmsMessage(
        context: Context, messageUri: Uri, sentIntent: Intent, deliveryIntent: Intent?
    ): Boolean {
        if (messageUri === Uri.EMPTY) {
            Timber.e("can't send sms. message uri is empty")
            return false
        }

        var id = -1
        var subscriptionId = -1
        var body = ""
        var address = ""

        // get message values from provider db
        context.contentResolver.query(
            messageUri,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.SUBSCRIPTION_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY
            ),
            null, null, null
        )
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    id = cursor.getInt(0)
                    subscriptionId = cursor.getInt(1)
                    address = cursor.getString(2)
                    body = cursor.getString(3)
                } else {
                    Timber.e("sms provider db query returned no rows")
                    return false
                }
            }
            ?: let {
                Timber.e("sms provider db query failed")
                return false
            }

        val sPI = ArrayList<PendingIntent>()
        val dPI = ArrayList<PendingIntent>()

        // set up sent and delivered pending intents to be used with message request
        val sentPI = PendingIntent.getBroadcast(
            context, id, sentIntent.setData(messageUri),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deliveredPI =
            if (deliveryIntent != null) PendingIntent.getBroadcast(
                context, -id, deliveryIntent.setData(messageUri),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            else null

        val smsManager = SmsManagerFactory.createSmsManager(subscriptionId)

        val parts = smsManager.divideMessage(body)

        // add pending intent for each part
        for (i in parts.indices) {
            sPI.add(sentPI)
            if (deliveredPI != null) dPI.add(deliveredPI)
        }

        Timber.v("send sms message uri $messageUri")
        try {
            smsManager.sendMultipartTextMessage(
                address, null, parts, sPI, dPI.ifEmpty { null }
            )
        } catch (e: Exception) {
            Timber.e(e, "send sms exception")
        }

        return true
    }

    private fun createMmsMessage(
        context: Context, subscriptionId: Int, text: String?, addresses: Array<String>,
        parts: MutableCollection<MMSPart>
    ): Uri {
        // add text to the end of the part and send
        if (!text.isNullOrEmpty())
            parts.add(MMSPart().apply {
                Name = "text"
                MimeType = "text/plain"
                Data = text.toByteArray()
            })

        try {
            return PduPersister.getPduPersister(context).persist(
                buildPdu(context, subscriptionId, addresses, ArrayList(parts)),
                Telephony.Mms.Outbox.CONTENT_URI, true, true,
                null, subscriptionId
            )
        } catch (e: Exception) {
            Timber.e(e, "failed to create mms message")
        }

        return Uri.EMPTY
    }

    private fun explodeMmsMessage(context: Context, messageUri: Uri): Collection<Uri> {
        val retVal: MutableCollection<Uri> = ArrayList()

        Timber.v("explode mms message $messageUri")

        // load message from provider db as a generic pdu
        val pdu: GenericPdu
        try {
            pdu = PduPersister.getPduPersister(context).load(messageUri)
        } catch (e: MmsException) {
            Timber.e(e, "load pdu from provider failed")
            return retVal
        }

        // get recipient addresses from pdu
        val sendReq = pdu as SendReq
        val encodedTos = sendReq.to

        // if one recipient in thread, return original message only
        if (encodedTos.size == 1) {
            Timber.v("single recipient thread")
            retVal.add(messageUri)
            return retVal
        }

        // get subscription id from provider
        val subscriptionId = context.contentResolver.query(
            messageUri,
            arrayOf(Telephony.Mms.SUBSCRIPTION_ID),
            null, null, null
        )
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getInt(0)
                } else {
                    Timber.e("sub id provider db query returned no rows")
                    return retVal
                }
            }
            ?: let {
                Timber.e("sub id provider db query failed")
                return retVal
            }

        // create individual messages
        for (encodedTo in encodedTos) {
            try {
                sendReq.to = arrayOf(encodedTo)
                retVal.add(
                    PduPersister.getPduPersister(context).persist(
                        sendReq,
                        Telephony.Mms.Outbox.CONTENT_URI,
                        true,
                        true,
                        null,
                        subscriptionId
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "failed creating provider mms message")
            }
        }

        return retVal
    }

    private fun sendMmsMessage(context: Context, messageUri: Uri, sentIntent: Intent): Boolean {
        try {
            Timber.v("send mms message uri $messageUri")

            // update message status to outbox in provider
            if (SqliteWrapper.update(
                context, context.contentResolver,
                messageUri,
                contentValuesOf(
                    Telephony.Mms.MESSAGE_BOX to Telephony.Mms.MESSAGE_BOX_OUTBOX
                ),
                null, null
            ) <= 0)
                Timber.v("failed to update $messageUri to mms outbox")

            // get mms id and subscription id from provider
            var id = -1
            var subscriptionId = -1

            context.contentResolver.query(
                messageUri,
                arrayOf(Telephony.Mms._ID, Telephony.Mms.SUBSCRIPTION_ID),
                null, null, null
            )
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        id = cursor.getInt(0)
                        subscriptionId = cursor.getInt(1)
                    } else {
                        Timber.e("sub id provider db query returned no rows")
                        Utils.getDefaultSubscriptionId()
                    }
                }
                ?: let {
                    Timber.e("sub id provider db query failed")
                    Utils.getDefaultSubscriptionId()
                }

            // load message from provider db as a generic pdu
            val sendPdu = try {
                PduPersister.getPduPersister(context).load(messageUri)
            } catch (e: MmsException) {
                Timber.e(e, "pdu from provider db failed")
                return false
            }

            val fileName = "send." + UUID.randomUUID() + ".dat"
            val mSendFile = File(context.cacheDir, fileName)

            Timber.v("using file name $fileName")

            val contentUri = try {
                FileOutputStream(mSendFile).use {
                    it.write(PduComposer(context, sendPdu).make())
                }
                (Uri.Builder())
                    .authority(context.packageName + ".MmsFileProvider")
                    .path(fileName)
                    .scheme(ContentResolver.SCHEME_CONTENT)
                    .build()
            } catch (e: IOException) {
                Timber.e(e, "error writing send file")
                return false
            }

            // build intent to send when message sent
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                id,
                sentIntent
                    .setData(messageUri)
                    .putExtra(MmsSentReceiver.EXTRA_FILE_PATH, mSendFile.path),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            SmsManagerFactory.createSmsManager(subscriptionId).sendMultimediaMessage(
                context,
                contentUri,
                null,
                Bundle().apply { putBoolean(SmsManager.MMS_CONFIG_GROUP_MMS_ENABLED, true) },
                pendingIntent
            )
        } catch (e: Exception) {
            Timber.e(e, "failed sending mms")
            return false
        }

        return true
    }

    private fun buildPdu(
        context: Context, subscriptionId: Int, recipients: Array<String>, parts: List<MMSPart>
    ): SendReq {
        val req = SendReq()

        // From, per spec
        req.prepareFromAddress(context, "", subscriptionId)

        // To
        for (recipient in recipients)
            req.addTo(EncodedStringValue(recipient))

        // Subject { commented but left for future use }
//        if (subject.isNotEmpty())
//            req.subject = EncodedStringValue(subject)

        // Date
        req.date = System.currentTimeMillis() / 1000

        // Body
        val body = PduBody()

        var size = 0

        // Add text part. Always add a smil part for compatibility, without it there
        // may be issues on some carriers/client apps
        parts.forEach { size += addTextPart(body, it) }

        // add a SMIL document for compatibility
        val out = ByteArrayOutputStream()
        SmilXmlSerializer.serialize(SmilHelper.createSmilDocument(body), out)
        body.addPart(0, PduPart().apply {
            contentId = "smil".toByteArray()
            contentLocation = "smil.xml".toByteArray()
            contentType = ContentType.APP_SMIL.toByteArray()
            data = out.toByteArray()
        })

        req.body = body

        // Message size
        req.messageSize = size.toLong()

        // Message class
        req.messageClass = PduHeaders.MESSAGE_CLASS_PERSONAL_STR.toByteArray()

        // Expiry
        req.expiry = DEFAULT_EXPIRY_TIME

        try {
            // Priority
            req.priority = DEFAULT_PRIORITY

            // Delivery report
            req.deliveryReport = PduHeaders.VALUE_NO

            // Read report
            req.readReport = PduHeaders.VALUE_NO
        } catch (e: InvalidHeaderValueException) {
            Timber.e("invalid header value when building pdu")
        }

        return req
    }

    private fun addTextPart(pb: PduBody, p: MMSPart): Int {
        val filename = p.Name

        val part = PduPart().apply {
            // Set Charset if it's a text media.
            if (p.MimeType.startsWith("text"))
                charset = CharacterSets.UTF_8

            // Set Content-Type.
            contentType = p.MimeType.toByteArray()

            // Set Content-Location.
            contentLocation = filename.toByteArray()

            val index = filename.lastIndexOf(".")
            contentId =
                (
                    if (index == -1) filename
                    else filename.substring(0, index)
                ).toByteArray()
            data = p.Data
        }

        pb.addPart(part)

        return part.data.size
    }
}
