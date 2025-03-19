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
package dev.octoshrimpy.quik.common.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.contentValuesOf
import dev.octoshrimpy.quik.util.Preferences
import dev.octoshrimpy.quik.util.tryOrNull
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Based off Vipin Kumar's FileLoggingTree: https://medium.com/@vicky7230/file-logging-with-timber-4e63a1b86a66
 */
@Singleton
class FileLoggingTree @Inject constructor(
    private val prefs: Preferences,
    private val context: Context
) : Timber.DebugTree() {
    companion object {
        val TAG: String? = FileLoggingTree::class.simpleName
    }

    private var logFileUri: Uri? = null

    override fun log(priority: Int, tag: String, message: String, t: Throwable?) {
        if (!prefs.logging.get()) return

        Schedulers.io().scheduleDirect {
            synchronized(this) {    // one thread can access file at a time
                val logItem =
                    "${    // date/time
                        SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss:SSS",
                            Locale.getDefault()
                        ).format(System.currentTimeMillis())
                    } ${    // priority
                        when (priority) {
                            Log.VERBOSE -> "V"
                            Log.DEBUG -> "D"
                            Log.INFO -> "I"
                            Log.WARN -> "W"
                            Log.ERROR -> "E"
                            else -> "?"
                        }
                    }/${    // tag
                        tag
                    }: ${    // message
                        message
                    }${    // stack trace
                        Log.getStackTraceString(t)
                    }\n"

                try {
                    // if uri of log file not yet determined, get one now
                    if (logFileUri == null) {
                        val filename = "Quik-log-${
                            SimpleDateFormat(
                                "yyyy-MM-dd",
                                Locale.getDefault()
                            ).format(System.currentTimeMillis())
                        }.log"

                        logFileUri =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                                // use media store
                                context.contentResolver.insert(
                                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                    contentValuesOf(
                                        MediaStore.MediaColumns.MIME_TYPE to "text/plain",
                                        MediaStore.MediaColumns.RELATIVE_PATH to
                                                Environment.DIRECTORY_DOWNLOADS,
                                        MediaStore.MediaColumns.DISPLAY_NAME to filename,
                                    )
                                )
                            else
                                // use direct access to 'external' dir
                                File(Environment.getExternalStorageDirectory(), filename).let {
                                    tryOrNull { it.createNewFile() }
                                    Uri.fromFile(it)
                                }
                    }

                    logFileUri?.let {
                        context.contentResolver.openOutputStream(it, "wa")?.use {
                            // write the log entry
                            it.write(logItem.toByteArray())
                        }
                    }
                } catch (e: FileNotFoundException) {
                    Log.e(TAG, "Log file went away. Lost log file item: $logItem")
                    // log file seems to have gone away. start a new file next time through
                    logFileUri = null
                } catch (e: Exception) {
                    Log.e(TAG, "Error while logging into file", e)
                }
            }
        }
    }
}