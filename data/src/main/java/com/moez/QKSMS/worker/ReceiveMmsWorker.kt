/*
 * Copyright (C) 2025
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
package dev.octoshrimpy.quik.worker

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import androidx.core.net.toUri
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.android.mms.service_alt.DownloadRequest
import com.android.mms.service_alt.MmsConfig
import com.android.mms.service_alt.MmsConfig.Overridden
import com.android.mms.transaction.DownloadManager
import com.google.android.mms.pdu_alt.AcknowledgeInd
import com.google.android.mms.pdu_alt.EncodedStringValue
import com.google.android.mms.pdu_alt.NotificationInd
import com.google.android.mms.pdu_alt.NotifyRespInd
import com.google.android.mms.pdu_alt.PduComposer
import com.google.android.mms.pdu_alt.PduHeaders
import com.google.android.mms.pdu_alt.PduPersister
import com.google.android.mms.util_alt.SqliteWrapper
import com.klinker.android.send_message.MmsSentReceiver
import com.klinker.android.send_message.SmsManagerFactory
import com.klinker.android.send_message.Utils
import dev.octoshrimpy.quik.blocking.BlockingClient
import dev.octoshrimpy.quik.interactor.UpdateBadge
import dev.octoshrimpy.quik.manager.ActiveConversationManager
import dev.octoshrimpy.quik.manager.NotificationManager
import dev.octoshrimpy.quik.manager.ShortcutManager
import dev.octoshrimpy.quik.receiver.MessageSentReceiver
import dev.octoshrimpy.quik.repository.ContactRepository
import dev.octoshrimpy.quik.repository.ConversationRepository
import dev.octoshrimpy.quik.repository.MessageContentFilterRepository
import dev.octoshrimpy.quik.repository.MessageRepository
import dev.octoshrimpy.quik.repository.SyncRepository
import dev.octoshrimpy.quik.util.Preferences
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

class ReceiveMmsWorker(appContext: Context, workerParams: WorkerParameters)
    : Worker(appContext, workerParams) {
    companion object {
        const val INPUT_DATA_SUBSCRIPTION_ID = "subscriptionId"
        const val INPUT_DATA_EXTRA_FILE_PATH = "filePath"
        const val INPUT_DATA_EXTRA_LOCATION_URL = "locationUrl"
        const val INPUT_DATA_EXTRA_MMS_HTTP_STATUS = "mmsHttpStatus"
        const val INPUT_DATA_EXTRA_URI = "extraUri"

        private const val LOCATION_SELECTION =
            Telephony.Mms.MESSAGE_TYPE + "=? AND " + Telephony.Mms.CONTENT_LOCATION + " =?"
    }

    @Inject lateinit var activeConversationManager: ActiveConversationManager
    @Inject lateinit var conversationRepo: ConversationRepository
    @Inject lateinit var blockingClient: BlockingClient
    @Inject lateinit var prefs: Preferences
    @Inject lateinit var syncRepo: SyncRepository
    @Inject lateinit var messageRepo: MessageRepository
    @Inject lateinit var notificationManager: NotificationManager
    @Inject lateinit var updateBadge: UpdateBadge
    @Inject lateinit var shortcutManager: ShortcutManager
    @Inject lateinit var filterRepo: MessageContentFilterRepository
    @Inject lateinit var contactsRepo: ContactRepository

    override fun doWork(): Result {
        Timber.v("started")

        val subscriptionId = inputData.getInt(INPUT_DATA_SUBSCRIPTION_ID, -1)
        val filePath = inputData.getString(INPUT_DATA_EXTRA_FILE_PATH) ?: ""
        val locationUrl = inputData.getString(INPUT_DATA_EXTRA_LOCATION_URL) ?: ""
        val extraUri = inputData.getString(INPUT_DATA_EXTRA_URI) ?: ""

        if (filePath.isEmpty()) {
            Timber.e("empty file path")
            return Result.failure(inputData)
        }

        if (locationUrl.isEmpty()) {
            Timber.e("empty location url")
            return Result.failure(inputData)
        }

        if (extraUri.isEmpty()) {
            Timber.e("empty extra uri")
            return Result.failure(inputData)
        }

        Timber.v(filePath)

        val downloadFile = File(filePath)
        try {
            // read file into a bytearray
            val mmsData = FileInputStream(downloadFile).use { inputStream ->
                val fileLength = downloadFile.length().toInt()
                ByteArray(fileLength).let { bytes ->
                    inputStream.read(bytes, 0, fileLength)
                    bytes
                }
            }

            Timber.v("response buffered length: ${mmsData.size}")

            if (mmsData.isNotEmpty()) {
                // build notification ind (beware this must be done prior to downloadrequest.persist)
                val notificationInd =
                    PduPersister.getPduPersister(applicationContext).load(extraUri.toUri())
                            as NotificationInd

                // persist message
                val messageUri = DownloadRequest.persist(
                    applicationContext, mmsData,
                    Overridden(MmsConfig(applicationContext), null),
                    locationUrl, subscriptionId, null
                )

                handleHttpError(
                    applicationContext,
                    inputData.getInt(INPUT_DATA_EXTRA_MMS_HTTP_STATUS, 0),
                    locationUrl
                )

                DownloadManager.finishDownload(locationUrl)

                // if message was persisted ok
                if (messageUri != null) {
                    // Sync the message
                    val message = syncRepo.syncMessage(messageUri)
                        ?: return Result.failure(inputData)

                    // TODO: Ideally this is done when we're saving the MMS to ContentResolver
                    // This change can be made once we move the MMS storing code to the Data module
                    if (activeConversationManager.getActiveConversation() == message.threadId) {
                        messageRepo.markRead(listOf(message.threadId))
                    }

                    // Because we use the smsmms library for receiving and storing MMS, we'll need
                    // to check if it should be blocked after we've pulled it into realm. If it
                    // turns out that it should be dropped, then delete it
                    // TODO Don't store blocked messages in the first place
                    val action = blockingClient.shouldBlock(message.address).blockingGet()
                    val shouldDrop = prefs.drop.get()
                    Timber.v("block=$action, drop=$shouldDrop")

                    if (action is BlockingClient.Action.Block && shouldDrop) {
                        messageRepo.deleteMessages(listOf(message.id))
                    } else {
                        when (action) {
                            is BlockingClient.Action.Block -> {
                                messageRepo.markRead(listOf(message.threadId))
                                conversationRepo.markBlocked(
                                    listOf(message.threadId),
                                    prefs.blockingManager.get(),
                                    action.reason
                                )
                            }

                            is BlockingClient.Action.Unblock ->
                                conversationRepo.markUnblocked(message.threadId)

                            else -> Unit
                        }

                        val messageFilterAction = filterRepo.isBlocked(message.getText(), message.address, contactsRepo)
                        if (messageFilterAction) {
                            Timber.v("message dropped based on content filters")
                            messageRepo.deleteMessages(listOf(message.id))
                            return Result.failure(inputData)
                        }

                        // update the conversation
                        conversationRepo.updateConversations(listOf(message.threadId))
                        val conversation =
                            conversationRepo.getOrCreateConversation(message.threadId)
                                ?: return Result.failure(inputData)

                        // don't notify (continue) for blocked conversations
                        if (conversation.blocked) {
                            Timber.v("no notifications for blocked")
                            return Result.success(inputData)
                        }

                        // unarchive conversation if necessary
                        if (conversation.archived)
                            conversationRepo.markUnarchived(listOf(conversation.id))

                        // unarchive conversation if necessary
                        if (conversation.archived) {
                            Timber.v("conversation unarchived")
                            conversationRepo.markUnarchived(listOf(conversation.id))
                        }

                        // update/create notification
                        Timber.v("update/create notification")
                        notificationManager.update(conversation.id)

                        // update shortcuts
                        Timber.v("update shortcuts")
                        shortcutManager.updateShortcuts()
                        shortcutManager.reportShortcutUsed(conversation.id)

                        // update the badge and widget
                        Timber.v("update badge and widget")
                        updateBadge.execute(Unit)
                    }

                    // send ack to mmsc
                    sendAcknowledgeInd(applicationContext, subscriptionId, notificationInd)

                    // send notify ind to mmsc
                    sendNotifyRespInd(applicationContext, subscriptionId, notificationInd)
                }
            }
            else Timber.e("empty mms data")
        } catch (e: FileNotFoundException) {
            Timber.e("file not found: ${e.message}")
        } catch (e: IOException) {
            Timber.e("io exception: ${e.message}")
        } catch (e: Exception) {
            Timber.e("mms receive worker exception: ${e.message}")
        } finally {
            downloadFile.delete()
        }

        Timber.v("finished")

        return Result.success()
    }

    private fun handleHttpError(context: Context, mmsHttpStatus: Int, locationUrl: String) {
        if ((mmsHttpStatus == 404) || (mmsHttpStatus == 400)) {
            // Delete the corresponding NotificationInd
            SqliteWrapper.delete(
                context, context.contentResolver, Telephony.Mms.CONTENT_URI,
                LOCATION_SELECTION,
                arrayOf(
                    PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND.toString(),
                    locationUrl
                )
            )
        }
    }

    private fun sendResponsePdu(
        context: Context, subscriptionId: Int, contentLocation: ByteArray, pduBytes: ByteArray
    ) {
        // write pdu bytes to temporary file
        val fileName = "send.notify.${UUID.randomUUID()}.dat"

        val contentUri = Uri.Builder()
            .authority("${context.packageName}.MmsFileProvider")
            .path(fileName)
            .scheme(ContentResolver.SCHEME_CONTENT)
            .build()

        try {
            FileOutputStream(File(context.cacheDir, fileName)).use { outputStream ->
                outputStream.write(pduBytes)
            }
            Timber.v("notify send file $fileName")
        } catch (e: Exception) {
            Timber.e(e, "error writing notify send file")
        }

        val useWapMmsc = com.android.mms.MmsConfig.getNotifyWapMMSC()
        if (useWapMmsc)
            Timber.v("using content location for wap mmsc")

        SmsManagerFactory.createSmsManager(subscriptionId).sendMultimediaMessage(
            context, contentUri,
            if (useWapMmsc) String(contentLocation) else null,
            null,
            PendingIntent.getBroadcast(
                context, 0,
                Intent(context, MessageSentReceiver::class.java)
                    .setData(contentUri) // this is only used to make pending intent unique
                    .putExtra(MmsSentReceiver.EXTRA_FILE_PATH, fileName)
                    .putExtra(MessageSentReceiver.EXTRA_IS_NOTIFY, 1),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    private fun sendAcknowledgeInd(
        context: Context, subscriptionId: Int, notificationInd: NotificationInd
    ) {
        Timber.v("create m-acknowledge.ind")

        try {
            // if the Transaction-ID isn't set in the M-Retrieve.conf, it means
            // the MMS proxy-relay doesn't require an ACK
            if (notificationInd.transactionId != null) {
                val ackInd = AcknowledgeInd(
                    PduHeaders.CURRENT_MMS_VERSION, notificationInd.transactionId
                ).apply {
                    // insert the 'from' address per spec
                    from = EncodedStringValue(Utils.getMyPhoneNumber(context).orEmpty())
                }

                val pdu = PduComposer(context, ackInd).make()

                // pack m-acknowledge.ind and send it
                sendResponsePdu(context, subscriptionId, notificationInd.contentLocation, pdu)
            }
        } catch (e: Exception) {
            Timber.e(e, "error creating m-acknowledge.ind")
        }
    }

    private fun sendNotifyRespInd(
        context: Context, subscriptionId: Int, notificationInd: NotificationInd
    ) {
        Timber.v("create m-notifyresp.ind")

        try {
            if (notificationInd.transactionId != null) {
                val notifyRespInd = NotifyRespInd(
                    PduHeaders.CURRENT_MMS_VERSION,
                    notificationInd.transactionId,
                    PduHeaders.STATUS_RETRIEVED
                )

                val pdu = PduComposer(context, notifyRespInd).make()

                // pack m-notifyresp.ind and send it
                sendResponsePdu(context, subscriptionId, notificationInd.contentLocation, pdu)
            }
        } catch (e: Exception) {
            Timber.e(e, "error creating m-notifyresp.ind")
        }
    }

    override fun getForegroundInfo() = ForegroundInfo(
        0,
        notificationManager.getForegroundNotificationForWorkersOnOlderAndroids()
    )

}
