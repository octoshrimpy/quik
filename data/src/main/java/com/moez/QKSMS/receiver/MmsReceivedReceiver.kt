/*
 * Copyright (C) 2015 Jacob Klinker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 * modifications from original:
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
package dev.octoshrimpy.quik.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Parcelable
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.net.toUri
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
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
import com.google.android.mms.pdu_alt.PduParser
import com.google.android.mms.pdu_alt.PduPersister
import com.google.android.mms.pdu_alt.RetrieveConf
import com.google.android.mms.util_alt.SqliteWrapper
import com.klinker.android.send_message.MmsSentReceiver
import com.klinker.android.send_message.SmsManagerFactory
import com.klinker.android.send_message.Utils
import dev.octoshrimpy.quik.interactor.ReceiveMms
import dev.octoshrimpy.quik.worker.ReceiveMmsWorker
import dev.octoshrimpy.quik.worker.ReceiveMmsWorker.Companion.INPUT_DATA_EXTRA_FILE_PATH
import dev.octoshrimpy.quik.worker.ReceiveMmsWorker.Companion.INPUT_DATA_EXTRA_LOCATION_URL
import dev.octoshrimpy.quik.worker.ReceiveMmsWorker.Companion.INPUT_DATA_EXTRA_MMS_HTTP_STATUS
import dev.octoshrimpy.quik.worker.ReceiveMmsWorker.Companion.INPUT_DATA_EXTRA_URI
import dev.octoshrimpy.quik.worker.ReceiveMmsWorker.Companion.INPUT_DATA_SUBSCRIPTION_ID
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MmsReceivedReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_FILE_PATH: String = "file_path"
        const val EXTRA_LOCATION_URL: String = "location_url"
        const val EXTRA_URI: String = "notification_ind_uri"
        const val SUBSCRIPTION_ID: String = "subscription_id"

        private const val LOCATION_SELECTION =
            Telephony.Mms.MESSAGE_TYPE + "=? AND " + Telephony.Mms.CONTENT_LOCATION + " =?"

        private val RECEIVE_NOTIFICATION_EXECUTOR: ExecutorService =
            Executors.newSingleThreadExecutor()

        private fun getNotificationInd(context: Context, extraUri: String) =
            PduPersister.getPduPersister(context).load(extraUri.toUri()) as NotificationInd

        fun onReceiveWorker(
            context: Context, subscriptionId: Int, filePath: String, locationUrl: String,
            mmsHttpStatus: Int, extraUri: String, receiveMms: ReceiveMms
        ) {
            if (filePath.isEmpty()) {
                Timber.e("empty file path")
                return
            }

            if (locationUrl.isEmpty()) {
                Timber.e("empty location url")
                return
            }

            if (extraUri.isEmpty()) {
                Timber.e("empty extra uri")
                return
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
                    val notificationTasks =
                        getNotificationTasks(context, subscriptionId, extraUri, mmsData)

                    // persist message
                    val messageUri = DownloadRequest.persist(
                        context, mmsData, Overridden(MmsConfig(context), null),
                        locationUrl, subscriptionId, null
                    )

                    // run notifiers to notify back to mmsc
                    Timber.v("running the common async notifier for download")
                    for (task in notificationTasks)
                        task.executeOnExecutor(RECEIVE_NOTIFICATION_EXECUTOR)

                    handleHttpError(context, mmsHttpStatus, locationUrl)

                    DownloadManager.finishDownload(locationUrl)

                    // message is 'raw' received, so do quik app processing
                    if (messageUri != null)
                        receiveMms.execute(messageUri)
                }
                else Timber.e("empty mms data")
            } catch (e: FileNotFoundException) {
                Timber.e("file not found", e)
            } catch (e: IOException) {
                Timber.e("io exception", e)
            } finally {
                downloadFile.delete()
            }
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

        private abstract class CommonAsyncTask(
            protected val mContext: Context, private val subscriptionId: Int,
            protected val mNotificationInd: NotificationInd
        ) : AsyncTask<Void?, Void?, Void?>() {

            fun sendPdu(pduBytes: ByteArray) {
                // write pdu bytes to temporary file
                val fileName = "send.notify.${UUID.randomUUID()}.dat"

                val contentUri = Uri.Builder()
                    .authority("${mContext.packageName}.MmsFileProvider")
                    .path(fileName)
                    .scheme(ContentResolver.SCHEME_CONTENT)
                    .build()

                try {
                    FileOutputStream(File(mContext.cacheDir, fileName)).use { outputStream ->
                        outputStream.write(pduBytes)
                    }
                    Timber.v("notify send file $fileName")
                } catch (e: Exception) {
                    Timber.e("error writing notify send file", e)
                }

                val useWapMmsc = com.android.mms.MmsConfig.getNotifyWapMMSC()
                if (useWapMmsc)
                    Timber.v("using content location for wap mmsc")

                SmsManagerFactory.createSmsManager(subscriptionId).sendMultimediaMessage(
                    mContext, contentUri,
                    if (useWapMmsc) String(mNotificationInd.contentLocation)
                    else null,
                    null,
                    PendingIntent.getBroadcast(
                        mContext, 0,
                        Intent(mContext, MessageSentReceiver::class.java)
                            .putExtra(MmsSentReceiver.EXTRA_FILE_PATH, fileName)
                            .putExtra(MessageSentReceiver.EXTRA_IS_NOTIFY, 1),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
        }

        private class NotifyRespTask(context: Context, subscriptionId: Int, ind: NotificationInd) :
            CommonAsyncTask(context, subscriptionId, ind) {
            override fun doInBackground(vararg params: Void?): Void? {
                // Create the M-NotifyResp.ind
                Timber.v("create m-notifyresp.ind")
                try {
                    val notifyRespInd = NotifyRespInd(
                        PduHeaders.CURRENT_MMS_VERSION,
                        mNotificationInd.transactionId,
                        PduHeaders.STATUS_RETRIEVED
                    )

                    // Pack M-NotifyResp.ind and send it
                    sendPdu(PduComposer(mContext, notifyRespInd).make())
                } catch (e: Exception) {
                    Timber.e("error creating m-notifyresp.ind", e)
                }

                return null
            }
        }

        private class AcknowledgeIndTask(
            context: Context, subscriptionId: Int, ind: NotificationInd,
            private val mRetrieveConf: RetrieveConf
        ) : CommonAsyncTask(context, subscriptionId, ind) {
            override fun doInBackground(vararg params: Void?): Void? {
                // Send M-Acknowledge.ind to MMSC if required.
                // If the Transaction-ID isn't set in the M-Retrieve.conf, it means
                // the MMS proxy-relay doesn't require an ACK.
                Timber.v("create m-acknowledge.ind")
                mRetrieveConf.transactionId?.let { tranId ->
                    // Create M-Acknowledge.ind
                    try {
                        val acknowledgeInd = AcknowledgeInd(PduHeaders.CURRENT_MMS_VERSION, tranId)

                        // insert the 'from' address per spec
                        acknowledgeInd.from = EncodedStringValue(
                            Utils.getMyPhoneNumber(mContext).orEmpty()
                        )

                        // Pack M-Acknowledge.ind and send it
                        sendPdu(PduComposer(mContext, acknowledgeInd).make())
                    } catch (e: Exception) {
                        Timber.e("error creating m-acknowledge.ind", e)
                    }
                } ?: let { Timber.e("no transaction id for creating m-acknowledge.ind") }

                return null
            }
        }

        private fun getNotificationTasks(
            context: Context, subscriptionId: Int, extraUri: String, mmsData: ByteArray
        ): List<CommonAsyncTask> {
            val pdu = PduParser(
                mmsData,
                Overridden(MmsConfig(context), null).supportMmsContentDisposition
            ).parse()

            if (pdu is RetrieveConf) {
                try {
                    Timber.v("create notification tasks")
                    return getNotificationInd(context, extraUri).let { notificationInd ->
                        listOf(
                            AcknowledgeIndTask(context, subscriptionId, notificationInd, pdu),
                            NotifyRespTask(context, subscriptionId, notificationInd)
                        )
                    }
                } catch (e: Exception) {
                    Timber.e("error get notification tasks", e)
                }
            } else
                Timber.e("MmsReceivedReceiver.sendNotification failed to parse pdu")

            return listOf()
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Timber.v("mms downloaded, persisting it to the database with worker")

        // start worker with message id as param
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<ReceiveMmsWorker>()
                .setInputData(
                    workDataOf(
                        INPUT_DATA_SUBSCRIPTION_ID to intent.getIntExtra(
                            SUBSCRIPTION_ID,
                            Utils.getDefaultSubscriptionId()
                        ),
                        INPUT_DATA_EXTRA_FILE_PATH to intent.getStringExtra(EXTRA_FILE_PATH),
                        INPUT_DATA_EXTRA_LOCATION_URL to intent.getStringExtra(EXTRA_LOCATION_URL),
                        INPUT_DATA_EXTRA_MMS_HTTP_STATUS to intent.getIntExtra(
                            SmsManager.EXTRA_MMS_HTTP_STATUS,
                            0
                        ),
                        INPUT_DATA_EXTRA_URI to
                                (intent.getParcelableExtra<Parcelable>(EXTRA_URI) as Uri?)?.toString()
                    )
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
        )
    }

}
