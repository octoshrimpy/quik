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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import android.telephony.SmsManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.klinker.android.send_message.MmsReceivedReceiver.EXTRA_FILE_PATH
import com.klinker.android.send_message.MmsReceivedReceiver.EXTRA_LOCATION_URL
import com.klinker.android.send_message.MmsReceivedReceiver.EXTRA_URI
import com.klinker.android.send_message.MmsReceivedReceiver.SUBSCRIPTION_ID
import com.klinker.android.send_message.Utils
import dev.octoshrimpy.quik.worker.ReceiveMmsWorker
import dev.octoshrimpy.quik.worker.ReceiveMmsWorker.Companion.INPUT_DATA_EXTRA_FILE_PATH
import dev.octoshrimpy.quik.worker.ReceiveMmsWorker.Companion.INPUT_DATA_EXTRA_LOCATION_URL
import dev.octoshrimpy.quik.worker.ReceiveMmsWorker.Companion.INPUT_DATA_EXTRA_MMS_HTTP_STATUS
import dev.octoshrimpy.quik.worker.ReceiveMmsWorker.Companion.INPUT_DATA_EXTRA_URI
import dev.octoshrimpy.quik.worker.ReceiveMmsWorker.Companion.INPUT_DATA_SUBSCRIPTION_ID
import timber.log.Timber

class MmsReceivedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Timber.v("mms downloaded. create worker to process")

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
