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
package com.moez.QKSMS.manager

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import androidx.core.net.toUri
import java.io.File
import java.util.UUID

object MediaRecorderManager : MediaRecorder() {

    enum class RecordingState {
        Initial,
        DataSourceConfigured,
        Prepared,
        Recording,
        Error
    }

    const val AUDIO_FILE_PREFIX = "recorded-"
    const val AUDIO_FILE_SUFFIX = ".3ga"

    var recordingState: RecordingState = RecordingState.Initial
        private set

    var uri: Uri = Uri.EMPTY
        private set

    fun stopRecording(): Uri {
        return try {
            if (recordingState == RecordingState.Recording)
                stop()

            reset()
            recordingState = RecordingState.Initial

            uri
        }
        catch (e: Exception) {
            Uri.EMPTY
        }
    }

    fun startRecording(context: Context): Uri {
        return try {
            val file = File(
                context.cacheDir,
                AUDIO_FILE_PREFIX + UUID.randomUUID() + AUDIO_FILE_SUFFIX
            )

            // ensure stopped before using again
            stopRecording()

            // configure
            setAudioSource(AudioSource.MIC)
            setOutputFormat(OutputFormat.THREE_GPP)
            setAudioEncoder(AudioEncoder.AMR_WB)
            recordingState = RecordingState.DataSourceConfigured

            setOutputFile(file.path)

            prepare()
            recordingState = RecordingState.Prepared

            start()
            recordingState = RecordingState.Recording

            uri = file.toUri()

            uri
        }
        catch (e: Exception) {
            Uri.EMPTY
        }
    }

}
