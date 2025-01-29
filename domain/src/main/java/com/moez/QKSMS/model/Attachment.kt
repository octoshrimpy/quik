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
package dev.octoshrimpy.quik.model

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.view.inputmethod.InputContentInfoCompat


class Attachment(
    private val uri: Uri? = null,
    private val inputContent: InputContentInfoCompat? = null
) {
    private var resourceBytes: ByteArray? = null

    fun getUri(): Uri {
        return (
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1)
                inputContent?.contentUri ?: uri ?: Uri.EMPTY
            else uri ?: Uri.EMPTY
        )
    }

    fun getType(context: Context): String {
        return context.contentResolver.getType(getUri()) ?: "application/octect-stream"
    }

    fun getName(context: Context): String {
        try {
            context.contentResolver.query(
                getUri(),
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )
            ?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex == -1)
                    return "unknown"
                cursor.moveToFirst()
                return cursor.getString(nameIndex) ?: "unknown"
            }
        }
        catch (e: Exception) { /* nothing*/ }

        return "unknown"
    }

    fun isVCard(context: Context): Boolean {
        return (getType(context) == "text/x-vcard")
    }

    fun hasDisplayableImage(context: Context): Boolean {
        val mimeType = getType(context)
        return (mimeType.startsWith("image/") || mimeType.startsWith("video/"))
    }

    fun isAudio(context: Context): Boolean {
        val mimeType = getType(context)
        return (mimeType.startsWith("audio/"))
    }

    fun isImage(context: Context): Boolean {
        val mimeType = getType(context)
        return (mimeType.startsWith("image/"))
    }

    fun getSize(context: Context): Long {
        try {
            context.contentResolver.query(
                getUri(),
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null
            )
            ?.use {
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex == -1)
                    return -1
                it.moveToFirst()
                return it.getLong(sizeIndex)
            }
        }
        catch (e: Exception) { /* nothing */ }

        return -1
    }

    fun getResourceBytes(context: Context): ByteArray {
        if (resourceBytes != null)
            return resourceBytes!!

        resourceBytes = ByteArray(0)

        try {
            context.contentResolver.openInputStream(getUri())?.use {
                resourceBytes = it.readBytes()
            }
        } catch (e: Exception) {
        }

        return resourceBytes!!
    }

}

class Attachments(attachments: List<Attachment>) : List<Attachment> by attachments
