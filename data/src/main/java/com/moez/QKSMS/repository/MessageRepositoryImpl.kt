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

import com.moez.QKSMS.manager.QkTransaction
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
import com.klinker.android.send_message.SmsManagerFactory
import dev.octoshrimpy.quik.common.util.extensions.now
import dev.octoshrimpy.quik.compat.TelephonyCompat
import dev.octoshrimpy.quik.extensions.anyOf
import dev.octoshrimpy.quik.extensions.insertOrUpdate
import dev.octoshrimpy.quik.extensions.isImage
import dev.octoshrimpy.quik.extensions.isVideo
import dev.octoshrimpy.quik.extensions.map
import dev.octoshrimpy.quik.extensions.resourceExists
import dev.octoshrimpy.quik.manager.ActiveConversationManager
import dev.octoshrimpy.quik.manager.KeyManager
import dev.octoshrimpy.quik.mapper.CursorToMessage
import dev.octoshrimpy.quik.mapper.CursorToPart
import dev.octoshrimpy.quik.model.Attachment
import dev.octoshrimpy.quik.model.Conversation
import dev.octoshrimpy.quik.model.Message
import dev.octoshrimpy.quik.model.Message.Companion.TYPE_MMS
import dev.octoshrimpy.quik.model.Message.Companion.TYPE_SMS
import dev.octoshrimpy.quik.model.MmsPart
import dev.octoshrimpy.quik.receiver.MessageDeliveredReceiver
import dev.octoshrimpy.quik.receiver.MessageSentReceiver
import dev.octoshrimpy.quik.receiver.SendDelayedMessageReceiver
import dev.octoshrimpy.quik.receiver.SendDelayedMessageReceiver.Companion.MESSAGE_ID_EXTRA
import dev.octoshrimpy.quik.util.ImageUtils
import dev.octoshrimpy.quik.util.PhoneNumberUtils
import dev.octoshrimpy.quik.util.Preferences
import dev.octoshrimpy.quik.util.sha256
import dev.octoshrimpy.quik.util.tryOrNull
import io.reactivex.Flowable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import io.realm.Case
import io.realm.Realm
import io.realm.RealmList
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
    private val reactions: EmojiReactionRepository,
    private val cursorToMessage: CursorToMessage,
    private val cursorToPart: CursorToPart,
) : MessageRepository {

    override val deduplicationProgress: Subject<MessageRepository.DeduplicationProgress> =
        BehaviorSubject.createDefault(MessageRepository.DeduplicationProgress.Idle)

    companion object {
        const val TELEPHONY_UPDATE_CHUNK_SIZE = 200
    }

    private fun getMessagesBase(threadId: Long, query: String) =
        Realm.getDefaultInstance()
            .where(Message::class.java)
            .equalTo("threadId", threadId)
            .equalTo("isEmojiReaction", false)
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

    override fun getMessage(messageId: Long) =
        Realm.getDefaultInstance()
            .also { it.refresh() }
            .where(Message::class.java)
            .equalTo("id", messageId)
            .findFirst()

    override fun getUnmanagedMessage(messageId: Long) =
        Realm.getDefaultInstance().use { realm ->
            getMessage(messageId)?.let(realm::copyFromRealm)
        }

    override fun getMessages(messageIds: Collection<Long>): RealmResults<Message> =
        Realm.getDefaultInstance()
            .also { it.refresh() }
            .where(Message::class.java)
            .anyOf("id", messageIds.toLongArray())
            .findAll()

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
            .equalTo("type", TYPE_SMS)
            .`in`("boxId", arrayOf(Sms.MESSAGE_TYPE_INBOX, Sms.MESSAGE_TYPE_ALL))
            .endGroup()
            .or()
            .beginGroup()
            .equalTo("type", TYPE_MMS)
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
                val whereClause = "${Sms.SEEN} = ${if (seen) 0 else 1} " +
                        "and ${Sms.THREAD_ID} in (${it.joinToString(",")})"

                // sms messages
                tryOrNull(true) {
                    countUpdated += context.contentResolver.update(
                        Sms.CONTENT_URI, values, whereClause, null
                    )
                }

                // mms messages
                tryOrNull(true) {
                    countUpdated += context.contentResolver.update(
                        Mms.CONTENT_URI, values, whereClause, null
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

    override fun markSeen(threadIds: Collection<Long>) =
        Realm.getDefaultInstance().use { realm ->
            realm.where(Message::class.java)
                .anyOf("threadId", threadIds.toLongArray())
                .equalTo("seen", false)
                .findAll()
                .let { messages ->
                    realm.executeTransaction {
                        messages.forEach { it.seen = true }
                    }
                }
        }.run {
            telephonyMarkSeenRead(true, null, threadIds)
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
                    telephonyMarkSeenRead(seen = true, read = true, threadIds = threadIds)
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

    private fun syncProviderMessage(uri: Uri, sendAsGroup: Boolean): Message? {
        // if uri doesn't have valid type
        val type = when {
            uri.toString().contains(TYPE_MMS) -> TYPE_MMS
            uri.toString().contains(TYPE_SMS) -> TYPE_SMS
            else -> return null
        }

        // if uri doesn't have a valid id, fail
        val contentId = tryOrNull(false) { ContentUris.parseId(uri) } ?: return null

        val stableUri = when (type) {
            TYPE_MMS -> ContentUris.withAppendedId(Mms.CONTENT_URI, contentId)
            else -> ContentUris.withAppendedId(Sms.CONTENT_URI, contentId)
        }

        return context.contentResolver.query(
            stableUri, null, null, null, null
        )?.use { cursor ->
            // if there are no rows, return null. else, move to the first row
            if (!cursor.moveToFirst())
                return null

            cursorToMessage.map(Pair(cursor, CursorToMessage.MessageColumns(cursor))).apply {
                this.sendAsGroup = sendAsGroup

                if (isMms()) {
                    parts = RealmList<MmsPart>().apply {
                        addAll(
                            cursorToPart.getPartsCursor(contentId)
                                ?.map { cursorToPart.map(it) }
                                .orEmpty()
                        )
                    }
                }

                insertOrUpdate()
            }
        }
    }

    override fun sendNewMessages(
        subId: Int, toAddresses: Collection<String>, body: String,
        attachments: Collection<Attachment>, sendAsGroup: Boolean, delayMs: Int
    ): Collection<Message> {
        Timber.v("sending message(s)")

        val parts = mutableListOf<com.google.android.mms.MMSPart>()

        if (attachments.isNotEmpty()) {
            Timber.v("has attachments")
            val smsManager = subId.takeIf { it != -1 }
                ?.let(SmsManagerFactory::createSmsManager)
                ?: SmsManager.getDefault()

            val maxWidth = smsManager.carrierConfigValues
                .getInt(SmsManager.MMS_CONFIG_MAX_IMAGE_WIDTH)
                .takeIf { prefs.mmsSize.get() == -1 }
                ?: Int.MAX_VALUE

            val maxHeight = smsManager.carrierConfigValues
                .getInt(SmsManager.MMS_CONFIG_MAX_IMAGE_HEIGHT)
                .takeIf { prefs.mmsSize.get() == -1 }
                ?: Int.MAX_VALUE

            var remainingBytes = when (prefs.mmsSize.get()) {
                -1 -> smsManager.carrierConfigValues.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE)
                0 -> Int.MAX_VALUE
                else -> prefs.mmsSize.get() * 1024
            } * 0.9 // Ugly, but buys us a bit of wiggle room

            remainingBytes -= body.takeIf { it.isNotEmpty() }?.toByteArray()?.size ?: 0

            // Attach those that can't be compressed (ie. everything but images)
            parts += attachments
                // filter in non-images only
                .filter { !it.isImage(context) }
                // filter in only items that exist (user may have deleted the file)
                .filter { it.uri.resourceExists(context) }
                .map {
                    remainingBytes -= it.getResourceBytes(context).size
                    val part = com.google.android.mms.MMSPart().apply {
                        MimeType = it.getType(context)
                        Name = it.getName(context)
                        Data = it.getResourceBytes(context)
                    }

                    // release the attachment hold on the image bytes so the GC can reclaim
                    it.releaseResourceBytes()

                    part
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
                            Timber.w(
                                "Failed to compress ${
                                    originalBytes.size / 1024
                                }Kb to ${maxBytes.toInt() / 1024}Kb"
                            )
                            return@forEach
                        }

                        val newArea = scale * width * height
                        val newWidth = sqrt(newArea * aspectRatio).toInt()
                        val newHeight = (newWidth / aspectRatio).toInt()

                        attempts++
                        scaledBytes = when (attachment.getType(context) == "image/gif") {
                            true -> ImageUtils.getScaledGif(
                                context, attachment.uri, newWidth, newHeight
                            )

                            false -> ImageUtils.getScaledImage(
                                context, attachment.uri, newWidth, newHeight
                            )
                        }

                        Timber.d(
                            "Compression attempt $attempts: ${
                                scaledBytes.size / 1024
                            }/${maxBytes.toInt() / 1024}Kb ($width*$height -> $newWidth*${
                                newHeight
                            })"
                        )

                        // release the attachment hold on the image bytes so the GC can reclaim
                        attachment.releaseResourceBytes()
                    }

                    Timber.v(
                        "Compressed ${originalBytes.size / 1024}Kb to ${
                            scaledBytes.size / 1024
                        }Kb with a target size of ${
                            maxBytes.toInt() / 1024
                        }Kb in $attempts attempts"
                    )
                    imageBytesByAttachment[attachment] = scaledBytes
                }
            }

            imageBytesByAttachment.forEach { (attachment, bytes) ->
                parts += com.google.android.mms.MMSPart().apply {
                    MimeType =
                        if (attachment.getType(context) == "image/gif") ContentType.IMAGE_GIF
                        else ContentType.IMAGE_JPEG
                    Name = attachment.getName(context)
                    Data = bytes
                }
            }
        }

        Timber.v("create os provider message")

        // 3 stage sending process - stage 1, create records in os provider
        val group = (sendAsGroup && (toAddresses.size > 1))
        val messageUri = QkTransaction.createMessage(
            context, subId, body, prefs.signature.get(),
            toAddresses.map(phoneNumberUtils::normalizeNumber).toTypedArray(),
            parts, group, prefs.longAsMms.get(), prefs.unicode.get()
        )

        if (messageUri == Uri.EMPTY) {
            Timber.v("create os provider message failed")
            return listOf()
        }

        val message = syncProviderMessage(messageUri, group)
        if (message == null) {
            Timber.v("sync message failed for uri $messageUri")
            return listOf()
        }

        Timber.v("created message id ${message.id} from uri $messageUri")

        if (delayMs > 0) {  // if delaying
            val sendTime = (now() + delayMs)

            // set delay time on the db message
            Realm.getDefaultInstance().use { realm ->
                realm.executeTransaction {
                    realm.copyToRealmOrUpdate(message.apply { date = sendTime })
                }
            }

            // create alarm that will trigger sending the message
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, sendTime, getIntentForDelayedSms(message.id)
                )

            Timber.v("set ${delayMs}ms delay for message id ${message.id}")

            return listOf(message)
        }

        // send now (message will be exploded, as required, and all sent)
        return sendMessage(message)
    }

    override fun sendMessage(message: Message): Collection<Message> {
        val retVal = mutableListOf<Message>()

        tryOrNull(true) {
            // explode message if needed
            val explodedMessages = QkTransaction.explodeMessage(
                context, message.getUri(), message.sendAsGroup
            ).filter { explodedMessageUri -> (explodedMessageUri != Uri.EMPTY) }

            // if multiple messages to send, create each and recurse to send
            if (explodedMessages.size > 1) {
                explodedMessages.forEach { explodedMessageUri ->
                    val childMessage = syncProviderMessage(explodedMessageUri, message.sendAsGroup)
                    if (childMessage != null) {
                        Timber.v("created message id ${childMessage.id} from uri $explodedMessageUri")
                        retVal.addAll(sendMessage(childMessage))
                    }
                    else
                        Timber.e("sync failed for uri $explodedMessageUri")
                }

                // mark original message as sent
                markSent(message.id)
            } else {
                markSending(message.id)

                // individual message to send, send it
                val sentIntent = Intent(context, MessageSentReceiver::class.java)
                    .putExtra(MessageSentReceiver.EXTRA_QUIK_MESSAGE_ID, message.id)

                val deliveryIntent =
                    if (prefs.delivery.get())
                        Intent(context, MessageDeliveredReceiver::class.java)
                            .putExtra(MessageDeliveredReceiver.EXTRA_QUIK_MESSAGE_ID, message.id)
                    else null

                // use values from os provider to resend the message, except subId
                if (!QkTransaction.sendMessage(context, message.getUri(), sentIntent, deliveryIntent))
                    Timber.e("message id ${message.id} not sent by smsmms")
            }

            retVal.add(message)
        }

        return retVal
    }

    override fun sendMessage(messageId: Long) =
        getMessage(messageId)
            ?.let { message -> sendMessage(message) }
            ?: listOf()

    override fun cancelDelayedSmsAlarm(messageId: Long) =
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .cancel(getIntentForDelayedSms(messageId))

    private fun getIntentForDelayedSms(messageId: Long) =
        PendingIntent.getBroadcast(
            context,
            messageId.toInt(),
            Intent(context, SendDelayedMessageReceiver::class.java)
                .putExtra(MESSAGE_ID_EXTRA, messageId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    override fun insertReceivedSms(subId: Int, address: String, body: String, sentTime: Long)
    : Message {
        val threadId = TelephonyCompat.getOrCreateThreadId(context, address)

        // insert the message to the native content provider
        val values = contentValuesOf(
            Sms.ADDRESS to address,
            Sms.BODY to body,
            Sms.DATE_SENT to sentTime,
            Sms.THREAD_ID to threadId
        )

        if (prefs.canUseSubId.get())
            values.put(Sms.SUBSCRIPTION_ID, subId)

        val providerContentId = context.contentResolver.insert(Sms.Inbox.CONTENT_URI, values)
            ?.let { insertedUri -> ContentUris.parseId(insertedUri) }
            ?: 0

        // insert the message to Realm
        val message = Message().apply {
            id = messageIds.newId()

            this.address = address
            this.body = body
            this.dateSent = sentTime
            this.threadId = threadId
            this.subId = subId

            date = System.currentTimeMillis()

            contentId = providerContentId
            boxId = Sms.MESSAGE_TYPE_INBOX
            type = TYPE_SMS
            read = (activeConversationManager.getActiveConversation() == threadId)
        }

        Realm.getDefaultInstance().use { realm ->
            var managedMessage: Message? = null
            realm.executeTransaction { managedMessage = realm.copyToRealmOrUpdate(message) }

            managedMessage?.let { savedMessage ->
                val parsedReaction = reactions.parseEmojiReaction(body)
                if (parsedReaction != null) {
                    val targetMessage = reactions.findTargetMessage(
                        savedMessage.threadId,
                        parsedReaction.originalMessage,
                        realm
                    )
                    realm.executeTransaction {
                        reactions.saveEmojiReaction(
                            savedMessage,
                            parsedReaction,
                            targetMessage,
                            realm,
                        )
                    }
                }
            }
        }

        return message
    }

    override fun markAsSendingNow(messageId: Long) =
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()
            realm.where(Message::class.java)
                .equalTo("id", messageId)
                .findFirst()
                ?.let { message ->
                    realm.executeTransaction {
                        message.date = System.currentTimeMillis()
                    }
                }
            Unit
        }

    /**
     * Marks the message as sending, in case we need to retry sending it
     */
    override fun markSending(messageId: Long) =
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()

            realm.where(Message::class.java)
                .equalTo("id", messageId)
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

    override fun markSent(messageId: Long) {
        Timber.v("mark message id $messageId as sent")

        Realm.getDefaultInstance().use { realm ->
            realm.refresh()

            realm.where(Message::class.java).equalTo("id", messageId).findFirst()
                ?.let { message ->
                    if (message.isSms()) {
                        // update the message in realm
                        realm.executeTransaction { message.boxId = Sms.MESSAGE_TYPE_SENT }

                        // Update the message in the native ContentProvider
                        context.contentResolver.update(
                            message.getUri(),
                            contentValuesOf(Sms.TYPE to Sms.MESSAGE_TYPE_SENT),
                            null,
                            null
                        )
                    } else {
                        // update the message in realm
                        realm.executeTransaction { message.boxId = Mms.MESSAGE_BOX_SENT }

                        // Update the message in the native ContentProvider
                        context.contentResolver.update(
                            message.getUri(),
                            contentValuesOf(Mms.MESSAGE_BOX to Mms.MESSAGE_BOX_SENT),
                            null,
                            null
                        )
                    }
                }
        }
    }

    override fun markFailed(messageId: Long, resultCode: Int) =
        Realm.getDefaultInstance().use { realm ->
            Timber.v("mark message id $messageId as failed. code $resultCode")

            realm.refresh()

            realm.where(Message::class.java).equalTo("id", messageId).findFirst()
                ?.let { message ->
                    if (message.isSms()) {
                        if (message.boxId != Sms.MESSAGE_TYPE_FAILED) {
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
                            true
                        } else false
                    } else {  // mms
                        if (message.boxId != Mms.MESSAGE_BOX_FAILED) {
                            // Update the message in realm
                            realm.executeTransaction {
                                message.boxId = Mms.MESSAGE_BOX_FAILED
                                message.errorCode = resultCode
                            }

                            // Update the message in the native ContentProvider
                            context.contentResolver.update(
                                message.getUri(),
                                contentValuesOf(
                                    Mms.MESSAGE_BOX to Mms.MESSAGE_BOX_FAILED
                                ),
                                null,
                                null
                            )

                            // TODO this query isn't able to find any results
                            // Need to figure out why the message isn't appearing in the PendingMessages Uri,
                            // so that we can properly assign the error type
                            context.contentResolver.update(
                                Telephony.MmsSms.PendingMessages.CONTENT_URI,
                                contentValuesOf(
                                    Telephony.MmsSms.PendingMessages.ERROR_TYPE to Telephony.MmsSms.ERR_TYPE_GENERIC_PERMANENT
                                ),
                                "${Telephony.MmsSms.PendingMessages.MSG_ID} = ?",
                                arrayOf(message.id.toString())
                            )
                            true
                        } else false
                    }
            } ?: false
        }

    override fun markDelivered(messageId: Long) =
        Realm.getDefaultInstance().use { realm ->
            Timber.v("mark message id $messageId as delivered")

            realm.refresh()

            realm.where(Message::class.java)
                .equalTo("id", messageId)
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

    override fun markDeliveryFailed(messageId: Long, resultCode: Int) =
        Realm.getDefaultInstance().use { realm ->
            Timber.v("mark message id $messageId as delivery failed result code $resultCode")

            realm.refresh()

            realm.where(Message::class.java)
                .equalTo("id", messageId)
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

            realm.where(Message::class.java)
                .anyOf("id", messageIds.toLongArray())
                .findAll()
                ?.let { messages ->
                    messages.mapNotNull { message ->
                        val uri = message.getUri()
                        if (uri != Uri.EMPTY)
                            context.contentResolver.delete(uri, null, null)
                    }

                    realm.executeTransaction { messages.deleteAllFromRealm() }
                } ?: Unit
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

    override fun deduplicateMessages(): Flowable<MessageRepository.DeduplicationResult> =
        Flowable.fromCallable {
            val duplicateIds = findDuplicateMessages()
            if (duplicateIds.isEmpty()) {
                MessageRepository.DeduplicationResult.NoDuplicates
            } else {
                deduplicationProgress.onNext(MessageRepository.DeduplicationProgress.Running(0, 0, true))
                deleteMessages(duplicateIds)
                MessageRepository.DeduplicationResult.Success
            }
        }
        .onErrorReturn { MessageRepository.DeduplicationResult.Failure(it) }
        .doFinally {
            deduplicationProgress.onNext(MessageRepository.DeduplicationProgress.Idle)
        }

    private fun findDuplicateMessages(): List<Long> {
        val seenSignatures = HashSet<String>()
        val duplicateIds = ArrayList<Long>()

        Realm.getDefaultInstance().use { realm ->
            val allMessages = realm.where(Message::class.java)
                .sort("id", Sort.ASCENDING)
                .findAll()

            val max = allMessages.size
            var progress = 0

            allMessages.forEach { message ->
                ++progress
                tryOrNull {
                    if (progress % 100 == 0 || progress == max) {
                        deduplicationProgress.onNext(
                            MessageRepository.DeduplicationProgress.Running(max, progress, false)
                        )
                    }
                    val signature = messageHash(message)
                    if (!seenSignatures.add(signature)) {
                        duplicateIds.add(message.id)
                    }
                }
            }
        }
        return duplicateIds
    }

    private fun messageHash(message: Message): String {
        val signatureString= buildString {
            append(message.address).append('|')
            append(message.dateSent).append('|')
            append(message.boxId).append('|')
            append(message.body).append('|')
            append(message.attachmentTypeString)
        }
        return sha256(signatureString)
    }
}
