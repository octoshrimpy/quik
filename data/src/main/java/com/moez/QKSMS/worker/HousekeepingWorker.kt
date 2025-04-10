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
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.moez.QKSMS.manager.MediaRecorderManager
import dev.octoshrimpy.quik.repository.ScheduledMessageRepository
import dev.octoshrimpy.quik.util.Constants
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
                    1,
                    TimeUnit.DAYS
                )
                    .setConstraints(
                        Constraints.Builder()
                            // idle device constraint helps guarantees quik won't be in use
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
        val twoHoursAgo = (System.currentTimeMillis() - (2 * 60 * 60 * 1000))

        removeOrphanedScheduledMessageAttachmentFiles()

        removeOrphanedComposeAudioRecordings(twoHoursAgo)

        removeSavedMessagesTexts(twoHoursAgo)

        removeOrphanedComposeDelayCancelledAttachments(twoHoursAgo)

        return Result.success()
    }

    private fun removeOrphanedScheduledMessageAttachmentFiles() {
        // get list of all scheduled message ids
        val scheduledMessageIds = scheduledMessageRepository.getAllScheduledMessageIdsSnapshot()

        // remove orphaned scheduled message dirs in files dir
        File(applicationContext.filesDir,"")
            // get dirs that match prefix 'scheduled-'
            .listFiles { entry -> entry.isDirectory && entry.name.startsWith(Constants.SCHEDULED_MESSAGE_FILE_PREFIX) }
            // filter out any dirs that have an associated scheduled message in db
            ?.filterNot {
                scheduledMessageIds.contains(it.name.substringAfter('-').toLong())
            }
            // recursively delete orphan dir
            ?.forEach { it.deleteRecursively() }
    }

    private fun removeOrphanedComposeAudioRecordings(removeOlderThan: Long) =
        // find recording files in cache dir
        applicationContext.cacheDir.listFiles { entry ->
            entry.isFile &&
                    entry.name.startsWith(MediaRecorderManager.AUDIO_FILE_PREFIX) &&
                    entry.name.endsWith(MediaRecorderManager.AUDIO_FILE_SUFFIX) &&
                    (entry.lastModified() < removeOlderThan)
        }?.forEach { it.delete() }  // delete recording file

    private fun removeSavedMessagesTexts(removeOlderThan: Long) =
        // find saved message text files in cache dir
        applicationContext.cacheDir.listFiles { entry ->
            entry.isFile &&
                    entry.name.startsWith(Constants.SAVED_MESSAGE_TEXT_FILE_PREFIX) &&
                    (entry.lastModified() < removeOlderThan)
        }?.forEach { it.delete() }  // delete message text file

    private fun removeOrphanedComposeDelayCancelledAttachments(removeOlderThan: Long) =
        // find dirs in cache dir
        applicationContext.cacheDir.listFiles { entry ->
            entry.isDirectory &&
                    entry.name.startsWith(Constants.DELAY_CANCELLED_CACHED_ATTACHMENTS_FILE_PREFIX)
                    (entry.lastModified() < removeOlderThan)
        }?.forEach { it.deleteRecursively() }  // recursively delete dir

}