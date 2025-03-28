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
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.moez.QKSMS.manager.MediaRecorderManager
import com.moez.QKSMS.manager.MediaRecorderManager.AUDIO_FILE_PREFIX
import dev.octoshrimpy.quik.repository.ScheduledMessageRepository
import dev.octoshrimpy.quik.repository.ScheduledMessageRepositoryImpl
import dev.octoshrimpy.quik.worker.ReceiveSmsWorker.Companion.INPUT_DATA_KEY_MESSAGE_ID
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class HousekeepingWorker(appContext: Context, workerParams: WorkerParameters)
: Worker(appContext, workerParams) {
    companion object {
        private val WORKER_TAG: String = HousekeepingWorker::class.java.simpleName

        fun register(context: Context) {
            // don't check return value because, well, we can't do much about a failure
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORKER_TAG,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequest.Builder(
                    HousekeepingWorker::class.java,
                    24,
                    TimeUnit.HOURS
                )
                    .setConstraints(
                        Constraints.Builder()
                            // idle device constraint kinda guarantees quik won't be in use
                            // as files are deleted (primarily for deleting audio recordings)
                            .setRequiresDeviceIdle(true)
                            // good citizens don't use up low batteries
                            .setRequiresBatteryNotLow(true)
                            .build()
                    )
                    .addTag(WORKER_TAG)
                    .build()
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORKER_TAG)
        }
    }

    @Inject lateinit var scheduledMessageRepository: ScheduledMessageRepository

    override fun doWork(): Result {
        removeOrphanedScheduledMessageAttachmentFiles()

        removeOrphanedComposeAudioRecording()

        return Result.success()
    }

    private fun removeOrphanedScheduledMessageAttachmentFiles() {
        // get list of all scheduled message ids
        val scheduledMessageIds = scheduledMessageRepository.getAllScheduledMessageIdsSnapshot()

        // remove orphaned scheduled message dirs in files dir
        File(applicationContext.filesDir,"")
            // get dirs that match prefix 'scheduled-'
            .listFiles { entry -> entry.isDirectory && entry.name.startsWith("scheduled-") }
            // filter out any dirs that have an associated scheduled message in db
            ?.filterNot {
                scheduledMessageIds.contains(it.name.substringAfter('-').toLong())
            }
            // recursively delete orphan dir
            ?.forEach { it.deleteRecursively() }
    }

    private fun removeOrphanedComposeAudioRecording() {
        // find recording files in cache dir
        applicationContext.cacheDir.listFiles { entry ->
            entry.isFile &&
                    entry.name.startsWith(AUDIO_FILE_PREFIX) &&
                    entry.name.endsWith(MediaRecorderManager.AUDIO_FILE_SUFFIX)
        }
        // delete recording file
        ?.forEach { it.delete() }
    }

}