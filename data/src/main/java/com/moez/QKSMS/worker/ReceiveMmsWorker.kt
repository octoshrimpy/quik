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

import android.content.Context
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import dev.octoshrimpy.quik.interactor.ReceiveMms
import dev.octoshrimpy.quik.manager.NotificationManager
import dev.octoshrimpy.quik.receiver.MmsReceivedReceiver
import timber.log.Timber
import javax.inject.Inject

class ReceiveMmsWorker(appContext: Context, workerParams: WorkerParameters)
    : Worker(appContext, workerParams) {
    companion object {
        const val INPUT_DATA_SUBSCRIPTION_ID = "subscriptionId"
        const val INPUT_DATA_EXTRA_FILE_PATH = "filePath"
        const val INPUT_DATA_EXTRA_LOCATION_URL = "locationUrl"
        const val INPUT_DATA_EXTRA_MMS_HTTP_STATUS = "mmsHttpStatus"
        const val INPUT_DATA_EXTRA_URI = "extraUri"
    }

    @Inject lateinit var receiveMms: ReceiveMms
    @Inject lateinit var notificationManager: NotificationManager

    override fun doWork(): Result {
        Timber.v("started")

        MmsReceivedReceiver.onReceiveWorker(
            applicationContext,
            inputData.getInt(INPUT_DATA_SUBSCRIPTION_ID, -1),
            inputData.getString(INPUT_DATA_EXTRA_FILE_PATH) ?: "",
            inputData.getString(INPUT_DATA_EXTRA_LOCATION_URL) ?: "",
            inputData.getInt(INPUT_DATA_EXTRA_MMS_HTTP_STATUS, 0),
            inputData.getString(INPUT_DATA_EXTRA_URI) ?: "",
            receiveMms
        )

        Timber.v("finished")

        return Result.success()
    }

    override fun getForegroundInfo() = ForegroundInfo(
        0,
        notificationManager.getForegroundNotificationForWorkersOnOlderAndroids()
    )

}
