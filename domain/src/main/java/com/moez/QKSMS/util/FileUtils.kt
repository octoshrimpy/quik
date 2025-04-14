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
package dev.octoshrimpy.quik.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.contentValuesOf
import androidx.core.net.toFile
import dev.octoshrimpy.quik.extensions.isEmpty
import java.io.File

object FileUtils {
    enum class Location {
        Downloads,
        ExternalCache,
        Cache,
        Files,
        Obb,
        CodeCache,
        NoBackup,
    }

    fun create(
        location: Location,
        context: Context,
        filename: String,
        mimeType: String
    ) =
        if (location == Location.Downloads) createDownloadsFile(context, filename, mimeType)
        else createLocation(location, context, filename)

    private fun createDownloadsFile(
        context: Context,
        filename: String,
        mimeType: String
    ): Pair<Uri, Exception?> =
        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // use media store
                context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    contentValuesOf(
                        MediaStore.MediaColumns.MIME_TYPE to mimeType,
                        MediaStore.MediaColumns.RELATIVE_PATH to
                                Environment.DIRECTORY_DOWNLOADS,
                        MediaStore.MediaColumns.DISPLAY_NAME to filename,
                    )
                ) ?: Uri.EMPTY
            } else { // use direct access to 'external' dir
                File(Environment.getExternalStorageDirectory(), filename).let {
                    if (tryOrNull { it.createNewFile() } == true) Uri.fromFile(it)
                    else Uri.EMPTY
                }
            }

            if (uri.isEmpty())
                throw Exception("Opening file returned an empty uri")

            Pair(uri, null)
        } catch (e: Exception) {
            Pair(Uri.EMPTY, e)
        }

    private fun createLocation(
        location: Location,
        context: Context,
        filename: String
    ): Pair<Uri, Exception?> =
        try {
            val uri = File(
                when (location) {
                    Location.ExternalCache -> context.externalCacheDir
                    Location.Files -> context.filesDir
                    Location.Obb -> context.obbDir
                    Location.CodeCache -> context.codeCacheDir
                    Location.NoBackup -> context.noBackupFilesDir
                    else -> context.cacheDir
                },
                filename
            ).let {

                if (tryOrNull {
                        it.parentFile?.mkdirs()
                        it.createNewFile()
                    } == true) Uri.fromFile(it)
                else Uri.EMPTY
            }

            if (uri.isEmpty())
                throw Exception("Opening file returned an empty uri")

            Pair(uri, null)
        } catch (e: Exception) {
            Pair(Uri.EMPTY, e)
        }

    private fun writeBytes(
        context: Context,
        uri: Uri,
        bytes: ByteArray,
        mode: String
    ): Exception? =
        try {
            context.contentResolver.openOutputStream(uri, mode)?.use { it.write(bytes) }
            null
        } catch (e: Exception) {
            e
        }

    fun append(context: Context, uri: Uri, bytes: ByteArray): Exception? =
        writeBytes(context, uri, bytes, "wa")

    fun write(context: Context, uri: Uri, bytes: ByteArray): Exception? =
        writeBytes(context, uri, bytes, "w")

    fun truncateAndWrite(context: Context, uri: Uri, bytes: ByteArray): Exception? =
        writeBytes(context, uri, bytes, "wt")

    fun createAndWrite(
        context: Context,
        location: Location,
        filename: String,
        mimeType: String,
        bytes: ByteArray
    ): Pair<Uri, Exception?> =
        create(location, context, filename, mimeType).let { (uri, e) ->
            if (e is Exception) Pair(Uri.EMPTY, e)
            else {
                val we = writeBytes(context, uri, bytes, "w")
                if (we is Exception) {
                    deleteFile(uri)
                    Pair(Uri.EMPTY, we)
                }
                else
                    Pair(uri, null)
            }
        }

    fun deleteFile(uri: Uri): Boolean {
        return try {
            if (uri == Uri.EMPTY) false
            else uri.toFile().delete()
        }
        catch (e: Exception) { false }
    }

    fun renameTo(fromUri: Uri, toUri: Uri) {
        fromUri.toFile().renameTo(toUri.toFile())
    }
}