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
import android.util.Log
import dev.octoshrimpy.quik.util.FileUtils
import dev.octoshrimpy.quik.util.Preferences
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.Locale
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

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
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

                // if uri of log file not yet determined, get one now
                if (logFileUri == null) {
                    val filename = "Quik-log-${
                        SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.getDefault()
                        ).format(System.currentTimeMillis())
                    }.log"

                    val (uri, e) = FileUtils.create(
                        FileUtils.Location.Downloads,
                        context,
                        filename,
                        "text/plain"
                    )
                    if (e is Exception)
                        Log.e(TAG, "Error opening log file", e)
                    else
                        logFileUri = uri
                }

                logFileUri?.let {
                    val e = FileUtils.append(context, it, logItem.toByteArray())
                    if (e is FileNotFoundException)
                        Log.e(TAG, "Log file went away. Lost log file item: $logItem", e)
                    else if (e is Exception)
                        Log.e(TAG, "Error while logging into file", e)
                }
            }
        }
    }
}