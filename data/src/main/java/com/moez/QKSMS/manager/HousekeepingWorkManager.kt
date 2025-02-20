package com.moez.QKSMS.manager

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.moez.QKSMS.manager.MediaRecorderManager.AUDIO_FILE_PREFIX
import dev.octoshrimpy.quik.repository.ScheduledMessageRepositoryImpl
import java.io.File
import java.util.concurrent.TimeUnit

class HousekeepingWorkManager(appContext: Context, workerParams: WorkerParameters)
    : Worker(appContext, workerParams) {

    companion object {
        private val WORKER_TAG: String = HousekeepingWorkManager::class.java.simpleName

        fun register(context: Context) {
            // don't check return value because, well, we can't do much about a failure
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORKER_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequest.Builder(
                    HousekeepingWorkManager::class.java,
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

    override fun doWork(): Result {
        removeOrphanedScheduledMessageAttachmentFiles()

        removeOrphanedComposeAudioRecording()

        return Result.success()
    }

    private fun removeOrphanedScheduledMessageAttachmentFiles() {
        // get list of all scheduled message ids
        val scheduledMessageIds = ScheduledMessageRepositoryImpl().getAllScheduledMessageIdsSnapshot()

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