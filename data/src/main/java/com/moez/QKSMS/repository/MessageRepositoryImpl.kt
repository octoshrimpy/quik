/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.octoshrimpy.quik.repository

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Telephony
import android.provider.Telephony.Mms
import android.provider.Telephony.Sms
import android.telephony.SmsManager
import android.webkit.MimeTypeMap
import androidx.core.content.contentValuesOf
import com.google.android.mms.ContentType
import com.google.android.mms.MMSPart
import com.google.android.mms.pdu_alt.MultimediaMessagePdu
import com.google.android.mms.pdu_alt.PduPersister
import com.klinker.android.send_message.SmsManagerFactory
import com.klinker.android.send_message.StripAccents
import com.klinker.android.send_message.Transaction
import dev.octoshrimpy.quik.common.util.extensions.now
import dev.octoshrimpy.quik.compat.TelephonyCompat
import dev.octoshrimpy.quik.extensions.anyOf
import dev.octoshrimpy.quik.extensions.isImage
import dev.octoshrimpy.quik.extensions.isVideo
import dev.octoshrimpy.quik.extensions.resourceExists
import dev.octoshrimpy.quik.manager.ActiveConversationManager
import dev.octoshrimpy.quik.manager.KeyManager
import dev.octoshrimpy.quik.model.Attachment
import dev.octoshrimpy.quik.model.Conversation
import dev.octoshrimpy.quik.model.Message
import dev.octoshrimpy.quik.model.MmsPart
import dev.octoshrimpy.quik.receiver.SendSmsReceiver
import dev.octoshrimpy.quik.receiver.SmsDeliveredReceiver
import dev.octoshrimpy.quik.receiver.SmsSentReceiver
import dev.octoshrimpy.quik.util.ImageUtils
import dev.octoshrimpy.quik.util.PhoneNumberUtils
import dev.octoshrimpy.quik.util.Preferences
import dev.octoshrimpy.quik.util.tryOrNull
import io.realm.Case
import io.realm.Realm
import io.realm.RealmResults
import io.realm.Sort
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
open class MessageRepositoryImpl @Inject constructor(
    private val activeConversationManager: ActiveConversationManager,
    private val context: Context,
    private val messageIds: KeyManager,
    private val phoneNumberUtils: PhoneNumberUtils,
    private val prefs: Preferences,
    private val syncRepository: SyncRepository,
) : MessageRepository {

    companion object {
        const val TELEPHONY_UPDATE_CHUNK_SIZE = 200
    }

    private fun getMessagesBase(threadId: Long, query: String) =
        Realm.getDefaultInstance()
            .where(Message::class.java)
            .equalTo("threadId", threadId)
            .let {
                when (query.isEmpty()) {
                    true -> it
                    false -> it
                        .beginGroup()
                        .contains("body", query, Case.INSENSITIVE)
                        .or()
                        .contains("parts.text", query, Case.INSENSITIVE)
                        .endGroup()
                }
            }
            .sort("date")

    override fun getMessages(threadId: Long, query: String): RealmResults<Message> =
        getMessagesBase(threadId, query).findAllAsync()

    override fun getMessagesSync(threadId: Long, query: String): RealmResults<Message> =
        getMessagesBase(threadId, query).findAll()

    override fun getMessage(id: Long) =
        Realm.getDefaultInstance()
            .also { it.refresh() }
            .where(Message::class.java)
            .equalTo("id", id)
            .findFirst()

    override fun getMessageForPart(id: Long) =
        Realm.getDefaultInstance()
            .where(Message::class.java)
            .equalTo("parts.id", id)
            .findFirst()

    override fun getLastIncomingMessage(threadId: Long): RealmResults<Message> =
        Realm.getDefaultInstance()
            .where(Message::class.java)
            .equalTo("threadId", threadId)
            .beginGroup()
            .beginGroup()
            .equalTo("type", "sms")
            .`in`("boxId", arrayOf(Sms.MESSAGE_TYPE_INBOX, Sms.MESSAGE_TYPE_ALL))
            .endGroup()
            .or()
            .beginGroup()
            .equalTo("type", "mms")
            .`in`("boxId", arrayOf(Mms.MESSAGE_BOX_INBOX, Mms.MESSAGE_BOX_ALL))
            .endGroup()
            .endGroup()
            .sort("date", Sort.DESCENDING)
            .findAll()

    override fun getUnreadCount() =
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()
            realm.where(Conversation::class.java)
                .equalTo("archived", false)
                .equalTo("blocked", false)
                .equalTo("lastMessage.read", false)
                .count()
        }

    override fun getPart(id: Long) =
        Realm.getDefaultInstance()
            .where(MmsPart::class.java)
            .equalTo("id", id)
            .findFirst()

    override fun getPartsForConversation(threadId: Long): RealmResults<MmsPart> =
        Realm.getDefaultInstance()
            .where(MmsPart::class.java)
            .equalTo("messages.threadId", threadId)
            .beginGroup()
            .contains("type", "image/")
            .or()
            .contains("type", "video/")
            .endGroup()
            .sort("id", Sort.DESCENDING)
            .findAllAsync()

    override fun savePart(id: Long): Uri? {
        val part = getPart(id) ?: return null

        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(part.type)
            ?: return null
        // fileDateAndTime is divided by 1000 in order to remove the extra 0's after date and time
        // This way the file name isn't so long.
        val fileDateAndTime = (part.messages?.first()?.date)?.div(1000)
        val fileName = "QUIK_${part.type.split("/").last()}_$fileDateAndTime.$extension"

        val values = contentValuesOf(
            MediaStore.MediaColumns.DISPLAY_NAME to fileName,
            MediaStore.MediaColumns.MIME_TYPE to part.type,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.IS_PENDING, 1)
            values.put(
                MediaStore.MediaColumns.RELATIVE_PATH, when {
                    part.isImage() -> "${Environment.DIRECTORY_PICTURES}/QUIK"
                    part.isVideo() -> "${Environment.DIRECTORY_MOVIES}/QUIK"
                    else -> "${Environment.DIRECTORY_DOWNLOADS}/QUIK"
                }
            )
        }

        val contentUri = when {
            part.isImage() -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            part.isVideo() -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            else -> MediaStore.Files.getContentUri("external")
        }

        val uri = context.contentResolver.insert(contentUri, values)
        Timber.v("Saving $fileName (${part.type}) to $uri")

        uri?.let {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                context.contentResolver.openInputStream(part.getUri())?.use { inputStream ->
                    inputStream.copyTo(outputStream, 1024)
                }
            }
            Timber.v("Saved $fileName (${part.type}) to $uri")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.update(
                    uri,
                    contentValuesOf(MediaStore.MediaColumns.IS_PENDING to 0),
                    null,
                    null
                )
                Timber.v("Marked $uri as not pending")
            }
        }

        return uri
    }

    override fun getUnreadUnseenMessages(threadId: Long): RealmResults<Message> =
        Realm.getDefaultInstance()
            .also { it.refresh() }
            .where(Message::class.java)
            .equalTo("seen", false)
            .equalTo("read", false)
            .equalTo("threadId", threadId)
            .sort("date")
            .findAll()

    override fun getUnreadMessages(threadId: Long): RealmResults<Message> =
        Realm.getDefaultInstance()
            .where(Message::class.java)
            .equalTo("read", false)
            .equalTo("threadId", threadId)
            .sort("date")
            .findAll()

    // marks all messages in threads as read and/or seen in the native provider
    private fun telephonyMarkSeenRead(
        seen: Boolean?,
        read: Boolean?,
        threadIds: Collection<Long>,
    ): Int {
        if (((seen == null) && (read == null)) || threadIds.isEmpty())
            return -1

        var countUpdated = 0

        // 'read' can be modified at the conversation level which updates all messages
        read?.let {
            tryOrNull(true) {
                // chunked so where clause doesn't get too long if there are many threads
                threadIds.forEach {
                    countUpdated += context.contentResolver.update(
                        ContentUris.withAppendedId(
                            Telephony.MmsSms.CONTENT_CONVERSATIONS_URI,
                            it
                        ),
                        contentValuesOf(Sms.READ to read),
                        "${Sms.READ} = ${if (read) 0 else 1}",
                        null
                    )
                }
            }
        }

        seen?.let {
            // 'seen' has to be modified at the messages level
            threadIds.chunked(TELEPHONY_UPDATE_CHUNK_SIZE).forEach {
                // chunked for smaller where clause size
                val values = contentValuesOf(Sms.SEEN to seen)
                val whereClause ="${Sms.SEEN} = ${if (seen) 0 else 1} " +
                        "and ${Sms.THREAD_ID} in (${it.joinToString(",")})"

                // sms messages
                tryOrNull(true) {
                    countUpdated += context.contentResolver.update(
                        Sms.CONTENT_URI,
                        values,
                        whereClause,
                        null
                    )
                }

                // mms messages
                tryOrNull(true) {
                    countUpdated += context.contentResolver.update(
                        Mms.CONTENT_URI,
                        values,
                        whereClause,
                        null
                    )
                }
            }
        }

        return countUpdated  // a mix of convo and message updates, so not overly useful. meh
    }

    override fun markAllSeen() =
        mutableSetOf<Long>().let { threadIds ->
            Realm.getDefaultInstance().use { realm ->
                realm.where(Message::class.java)
                    .equalTo("seen", false)
                    .findAll()
                    .takeIf { it.isNotEmpty() }
                    ?.let { messages ->
                        realm.executeTransaction {
                            messages.forEach {
                                it.seen = true
                                threadIds += it.threadId
                            }
                        }
                    }
            }.run {
                telephonyMarkSeenRead(true, null, threadIds)
            }
        }

    override fun markSeen(threadId: Long) =
        Realm.getDefaultInstance().use { realm ->
            realm.where(Message::class.java)
                .equalTo("threadId", threadId)
                .equalTo("seen", false)
                .findAll()
                .let { messages ->
                    realm.executeTransaction {
                        messages.forEach { it.seen = true }
                    }
                }
        }.run {
            telephonyMarkSeenRead(true, null, listOf(threadId))
        }

    override fun markRead(threadIds: Collection<Long>) =
        threadIds.takeIf { it.isNotEmpty() }
            ?.let {
                Realm.getDefaultInstance()?.use { realm ->
                    realm.where(Message::class.java)
                        .anyOf("threadId", threadIds.toLongArray())
                        .beginGroup()
                        .equalTo("read", false)
                        .or()
                        .equalTo("seen", false)
                        .endGroup()
                        .findAll()
                        .let { messages ->
                            realm.executeTransaction {
                                messages.forEach { it.seen = true; it.read = true }
                            }
                        }
                }.run {
                    telephonyMarkSeenRead(true, true, threadIds)
                }
            }
            ?: 0

    override fun markUnread(threadIds: Collection<Long>) =
        threadIds.takeIf { it.isNotEmpty() }
            ?.let {
                Realm.getDefaultInstance()?.use { realm ->
                    val conversations = realm.where(Conversation::class.java)
                        .anyOf("id", threadIds.toLongArray())
                        .equalTo("lastMessage.read", true)
                        .findAll()

                    realm.executeTransaction {
                        conversations.forEach { it.lastMessage?.read = false }
                    }
                }.run {
                    telephonyMarkSeenRead(null, false, threadIds)
                }
            }
            ?: 0

    override fun sendMessage(
        subId: Int,
        threadId: Long,
        addresses: Collection<String>,
        body: String,
        attachments: Collection<Attachment>,
        delay: Int
    ) {
        val signedBody = when {
            prefs.signature.get().isEmpty() -> body
            body.isNotEmpty() -> body + '\n' + prefs.signature.get()
            else -> prefs.signature.get()
        }

        val smsManager = subId.takeIf { it != -1 }
            ?.let(SmsManagerFactory::createSmsManager)
            ?: SmsManager.getDefault()

        // We only care about stripping SMS
        val strippedBody = when (prefs.unicode.get()) {
            true -> StripAccents.stripAccents(signedBody)
            false -> signedBody
        }

        val parts = smsManager.divideMessage(strippedBody).orEmpty()
        val forceMms = prefs.longAsMms.get() && parts.size > 1

        if (addresses.size == 1 && attachments.isEmpty() && !forceMms) { // SMS
            if (delay > 0) { // With delay
                val sendTime = System.currentTimeMillis() + delay
                val message = insertSentSms(
                    subId,
                    threadId,
                    addresses.first(),
                    strippedBody,
                    sendTime
                )

                val intent = getIntentForDelayedSms(message.id)

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, sendTime, intent)
            } else { // No delay
                val message = insertSentSms(
                    subId,
                    threadId,
                    addresses.first(),
                    strippedBody, now()
                )
                sendSms(message)
            }
        } else { // MMS
            val parts = arrayListOf<MMSPart>()

            val maxWidth = smsManager.carrierConfigValues
                .getInt(SmsManager.MMS_CONFIG_MAX_IMAGE_WIDTH)
                .takeIf { prefs.mmsSize.get() == -1 } ?: Int.MAX_VALUE

            val maxHeight = smsManager.carrierConfigValues
                .getInt(SmsManager.MMS_CONFIG_MAX_IMAGE_HEIGHT)
                .takeIf { prefs.mmsSize.get() == -1 } ?: Int.MAX_VALUE

            var remainingBytes = when (prefs.mmsSize.get()) {
                -1 -> smsManager.carrierConfigValues.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE)
                0 -> Int.MAX_VALUE
                else -> prefs.mmsSize.get() * 1024
            } * 0.9 // Ugly, but buys us a bit of wiggle room

            signedBody.takeIf { it.isNotEmpty() }?.toByteArray()?.let { bytes ->
                remainingBytes -= bytes.size
                parts += MMSPart("text", ContentType.TEXT_PLAIN, bytes)
            }

            // Attach those that can't be compressed (ie. everything but images)
            parts += attachments
                // filter in non-images only
                .filter { !it.isImage(context) }
                // filter in only items that exist (user may have deleted the file)
                .filter { it.uri.resourceExists(context) }
                .map {
                    remainingBytes -= it.getResourceBytes(context).size
                    val mmsPart = MMSPart(
                        it.getName(context),
                        it.getType(context),
                        it.getResourceBytes(context)
                    )

                    // release the attachment hold on the image bytes so the GC can reclaim
                    it.releaseResourceBytes()

                    mmsPart
                }

            val imageBytesByAttachment = attachments
                // filter in images only
                .filter { it.isImage(context) }
                // filter in only items that exist (user may have deleted the file)
                .filter { it.uri.resourceExists(context) }
                .associateWith {
                    when (it.getType(context) == "image/gif") {
                        true -> ImageUtils.getScaledGif(context, it.uri, maxWidth, maxHeight)
                        false -> ImageUtils.getScaledImage(context, it.uri, maxWidth, maxHeight)
                    }
                }
                .toMutableMap()

            val imageByteCount = imageBytesByAttachment.values.sumOf { it.size }
            if (imageByteCount > remainingBytes) {
                imageBytesByAttachment.forEach { (attachment, originalBytes) ->
                    val uri = attachment.uri
                    val maxBytes = originalBytes.size / imageByteCount.toFloat() * remainingBytes

                    // Get the image dimensions
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(
                        context.contentResolver.openInputStream(uri),
                        null,
                        options
                    )
                    val width = options.outWidth
                    val height = options.outHeight
                    val aspectRatio = width.toFloat() / height.toFloat()

                    var attempts = 0
                    var scaledBytes = originalBytes

                    while (scaledBytes.size > maxBytes) {
                        // Estimate how much we need to scale the image down by. If it's still
                        // too big, we'll need to try smaller and smaller values
                        val scale = maxBytes / originalBytes.size * (0.9 - attempts * 0.2)
                        if (scale <= 0) {
                            Timber.w("Failed to compress ${originalBytes.size / 1024
                            }Kb to ${maxBytes.toInt() / 1024}Kb")
                            return@forEach
                        }

                        val newArea = scale * width * height
                        val newWidth = sqrt(newArea * aspectRatio).toInt()
                        val newHeight = (newWidth / aspectRatio).toInt()

                        attempts++
                        scaledBytes = when (attachment.getType(context) == "image/gif") {
                            true -> ImageUtils.getScaledGif(
                                context,
                                attachment.uri,
                                newWidth,
                                newHeight
                            )
                            false -> ImageUtils.getScaledImage(
                                context,
                                attachment.uri,
                                newWidth,
                                newHeight
                            )
                        }

                        Timber.d("Compression attempt $attempts: ${scaledBytes.size / 1024
                        }/${maxBytes.toInt() / 1024}Kb ($width*$height -> $newWidth*${
                            newHeight})")

                        // release the attachment hold on the image bytes so the GC can reclaim
                        attachment.releaseResourceBytes()
                    }

                    Timber.v("Compressed ${originalBytes.size / 1024}Kb to ${
                        scaledBytes.size / 1024}Kb with a target size of ${
                        maxBytes.toInt() / 1024}Kb in $attempts attempts")
                    imageBytesByAttachment[attachment] = scaledBytes
                }
            }

            imageBytesByAttachment.forEach { (attachment, bytes) ->
                parts += when (attachment.getType(context) == "image/gif") {
                    true -> MMSPart(attachment.getName(context), ContentType.IMAGE_GIF, bytes)
                    false -> MMSPart(attachment.getName(context), ContentType.IMAGE_JPEG, bytes)
                }
            }

            // We need to strip the separators from outgoing MMS, or else they'll appear to have
            // sent and not go through
            val transaction = Transaction(context)
            val recipients = addresses.map(phoneNumberUtils::normalizeNumber)
            transaction.sendNewMessage(
                subId,
                threadId,
                recipients,
                parts,
                null,
                null
            )
        }
    }

    override fun sendSms(message: Message) {
        val smsManager = message.subId.takeIf { it != -1 }
            ?.let(SmsManagerFactory::createSmsManager)
            ?: SmsManager.getDefault()

        val parts = smsManager.divideMessage(
            if (prefs.unicode.get()) StripAccents.stripAccents(message.body)
            else message.body
        )
            ?: arrayListOf()

        try {
            smsManager.sendMultipartTextMessage(
                message.address,
                null,
                parts,
                ArrayList(
                    parts.map {
                        PendingIntent.getBroadcast(
                            context,
                            message.id.toInt(),
                            Intent(context, SmsSentReceiver::class.java)
                                .putExtra("id", message.id),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    }
                ),
                ArrayList(
                    parts.map {
                        val pendingIntent = PendingIntent.getBroadcast(
                            context,
                            message.id.toInt(),
                            Intent(context, SmsDeliveredReceiver::class.java)
                                .putExtra("id", message.id),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        if (prefs.delivery.get()) pendingIntent else null
                    }
                )
            )
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Message body lengths: ${parts.map { it?.length }}")
            markFailed(message.id, Telephony.MmsSms.ERR_TYPE_GENERIC)
        }
    }

    override fun resendMms(message: Message) =
        tryOrNull {
            PduPersister.getPduPersister(context).load(message.getUri()) as MultimediaMessagePdu
        }
            ?.let { pdu ->
                Transaction(context).sendNewMessage(
                    message.subId,
                    message.threadId,
                    pdu.to.map { it.string }.filter { it.isNotBlank() },
                    message.parts.mapNotNull { part ->
                        val bytes = tryOrNull(false) {
                            context.contentResolver.openInputStream(part.getUri())?.use {
                                    inputStream -> inputStream.readBytes()
                            }
                        } ?: return@mapNotNull null

                        MMSPart(part.name.orEmpty(), part.type, bytes)
                    },
                    message.subject,
                    message.getUri()
                )
            }
            ?: Unit

    override fun cancelDelayedSms(id: Long) =
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .cancel(getIntentForDelayedSms(id))

    private fun getIntentForDelayedSms(id: Long) =
        PendingIntent.getBroadcast(
            context,
            id.toInt(),
            Intent(context, SendSmsReceiver::class.java).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    override fun insertSentSms(
        subId: Int,
        threadId: Long,
        address: String,
        body: String,
        date: Long
    ): Message {
        // Insert the message to Realm
        val message = Message().apply {
            this.threadId = threadId
            this.address = address
            this.body = body
            this.date = date
            this.subId = subId

            id = messageIds.newId()
            boxId = Sms.MESSAGE_TYPE_OUTBOX
            type = "sms"
            read = true
            seen = true
        }

        // Insert the message to the native content provider
        val values = contentValuesOf(
            Sms.ADDRESS to address,
            Sms.BODY to body,
            Sms.DATE to System.currentTimeMillis(),
            Sms.READ to true,
            Sms.SEEN to true,
            Sms.TYPE to Sms.MESSAGE_TYPE_OUTBOX,
            Sms.THREAD_ID to threadId
        )

        if (prefs.canUseSubId.get())
            values.put(Sms.SUBSCRIPTION_ID, message.subId)

        var uri: Uri?
        var managedMessage: Message? = null

        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction { managedMessage = realm.copyToRealmOrUpdate(message) }

            // Update the contentId after the message has been inserted to the content provider
            // The message might have been deleted by now, so only proceed if it's valid
            //
            // do this after inserting the message because it might be slow, and we want the message
            // to be inserted into Realm immediately. We don't need to do this after receiving one
            uri = context.contentResolver.insert(Sms.CONTENT_URI, values)?.apply {
                lastPathSegment?.toLong()?.let { id ->
                    realm.executeTransaction {
                        managedMessage?.takeIf { it.isValid }?.contentId = id
                    }
                }
            }
        }

        // On some devices, we can't obtain a threadId until after the first message is sent in a
        // conversation. In this case, update the message's threadId after it gets added
        // to the native ContentProvider
        if (threadId == 0L)
            uri?.let(syncRepository::syncMessage)

        return message
    }

    override fun insertReceivedSms(
        subId: Int,
        address: String,
        body: String,
        sentTime: Long
    ): Message {
        // Insert the message to Realm
        val message = Message().apply {
            this.address = address
            this.body = body
            this.dateSent = sentTime
            this.date = System.currentTimeMillis()
            this.subId = subId

            id = messageIds.newId()
            threadId = TelephonyCompat.getOrCreateThreadId(context, address)
            boxId = Sms.MESSAGE_TYPE_INBOX
            type = "sms"
            read = activeConversationManager.getActiveConversation() == threadId
        }

        // Insert the message to the native content provider
        val values = contentValuesOf(
            Sms.ADDRESS to address,
            Sms.BODY to body,
            Sms.DATE_SENT to sentTime
        )

        if (prefs.canUseSubId.get())
            values.put(Sms.SUBSCRIPTION_ID, message.subId)

        Realm.getDefaultInstance().use { realm ->
            var managedMessage: Message? = null
            realm.executeTransaction { managedMessage = realm.copyToRealmOrUpdate(message) }

            context.contentResolver.insert(Sms.Inbox.CONTENT_URI, values)
                ?.lastPathSegment?.toLong()?.let { id ->
                    // Update contentId after the message has been inserted to the content provider
                    realm.executeTransaction { managedMessage?.contentId = id }
                }
        }

        return message
    }

    /**
     * Marks the message as sending, in case we need to retry sending it
     */
    override fun markSending(id: Long) =
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()

            realm.where(Message::class.java)
                .equalTo("id", id)
                .findFirst()
                ?.let { message ->
                    // Update the message in realm
                    realm.executeTransaction {
                        message.boxId = when (message.isSms()) {
                            true -> Sms.MESSAGE_TYPE_OUTBOX
                            false -> Mms.MESSAGE_BOX_OUTBOX
                        }
                    }

                    // Update the message in the native ContentProvider
                    context.contentResolver.update(
                        message.getUri(),
                        when (message.isSms()) {
                            true -> contentValuesOf(Sms.TYPE to Sms.MESSAGE_TYPE_OUTBOX)
                            false -> contentValuesOf(Mms.MESSAGE_BOX to Mms.MESSAGE_BOX_OUTBOX)
                        },
                        null,
                        null
                    )
                }
            Unit
        }

    override fun markSent(id: Long) =
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()

            realm.where(Message::class.java)
                .equalTo("id", id)
                .findFirst()
                ?.let { message ->
                    // Update the message in realm
                    realm.executeTransaction { message.boxId = Sms.MESSAGE_TYPE_SENT }

                    // Update the message in the native ContentProvider
                    context.contentResolver.update(
                        message.getUri(),
                        contentValuesOf(Sms.TYPE to Sms.MESSAGE_TYPE_SENT),
                        null,
                        null
                    )
                }
            Unit
        }

    override fun markFailed(id: Long, resultCode: Int) =
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()

            realm.where(Message::class.java)
                .equalTo("id", id)
                .findFirst()
                ?.let { message ->
                    // Update the message in realm
                    realm.executeTransaction {
                        message.boxId = Sms.MESSAGE_TYPE_FAILED
                        message.errorCode = resultCode
                    }

                    // Update the message in the native ContentProvider
                    context.contentResolver.update(
                        message.getUri(),
                        contentValuesOf(
                            Sms.TYPE to Sms.MESSAGE_TYPE_FAILED,
                            Sms.ERROR_CODE to resultCode,
                        ),
                        null,
                        null
                    )
                }
            Unit
        }

    override fun markDelivered(id: Long) =
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()

            realm.where(Message::class.java)
                .equalTo("id", id)
                .findFirst()
                ?.let { message ->
                    // Update the message in realm
                    realm.executeTransaction {
                        message.deliveryStatus = Sms.STATUS_COMPLETE
                        message.dateSent = System.currentTimeMillis()
                        message.read = true
                    }

                    // Update the message in the native ContentProvider
                    context.contentResolver.update(
                        message.getUri(),
                        contentValuesOf(
                            Sms.STATUS to Sms.STATUS_COMPLETE,
                            Sms.DATE_SENT to System.currentTimeMillis(),
                            Sms.READ to true,
                        ),
                        null,
                        null
                    )
                }
            Unit
        }

    override fun markDeliveryFailed(id: Long, resultCode: Int) =
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()

            realm.where(Message::class.java)
                .equalTo("id", id)
                .findFirst()
                ?.let { message ->
                    // Update the message in realm
                    realm.executeTransaction {
                        message.deliveryStatus = Sms.STATUS_FAILED
                        message.dateSent = System.currentTimeMillis()
                        message.read = true
                        message.errorCode = resultCode
                    }

                    // Update the message in the native ContentProvider
                    context.contentResolver.update(
                        message.getUri(),
                        contentValuesOf(
                            Sms.STATUS to Sms.STATUS_FAILED,
                            Sms.DATE_SENT to System.currentTimeMillis(),
                            Sms.READ to true,
                            Sms.ERROR_CODE to resultCode,
                        ),
                        null,
                        null
                    )
                }
            Unit
        }

    override fun deleteMessages(messageIds: Collection<Long>) =
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()

            val messages = realm.where(Message::class.java)
                .anyOf("id", messageIds.toLongArray())
                .findAll()

            val uris = messages.map { it.getUri() }

            realm.executeTransaction { messages.deleteAllFromRealm() }

            uris.forEach {
                uri -> context.contentResolver.delete(uri, null, null)
            }
        }

    override fun getOldMessageCounts(maxAgeDays: Int) =
        Realm.getDefaultInstance().use { realm ->
            realm.where(Message::class.java)
                .lessThan(
                    "date",
                    now() - TimeUnit.DAYS.toMillis(maxAgeDays.toLong())
                )
                .findAll()
                .groupingBy { message -> message.threadId }
                .eachCount()
        }

    override fun deleteOldMessages(maxAgeDays: Int) =
        Realm.getDefaultInstance().use { realm ->
            val messages = realm.where(Message::class.java)
                .lessThan(
                    "date",
                    now() - TimeUnit.DAYS.toMillis(maxAgeDays.toLong())
                )
                .findAll()

            val uris = messages.map { it.getUri() }

            realm.executeTransaction { messages.deleteAllFromRealm() }

            uris.forEach {
                uri -> context.contentResolver.delete(uri, null, null)
            }
        }
}
