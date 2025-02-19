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

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.net.toFile
import androidx.core.view.inputmethod.InputContentInfoCompat


@SuppressLint("Range")
class Attachment (
    context: Context,
    var uri: Uri = Uri.EMPTY,
    inputContent: InputContentInfoCompat? = null
) {
    private var resourceBytes: ByteArray? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1)
            uri = inputContent?.contentUri ?: uri

        // if constructed with a uri to a contact, convert uri to associated vcard uri
        if (context.contentResolver.getType(uri)?.lowercase() ==
            ContactsContract.Contacts.CONTENT_ITEM_TYPE
        ) {
            uri = try {
                Uri.withAppendedPath(
                    ContactsContract.Contacts.CONTENT_VCARD_URI,
                    context.contentResolver.query(
                        uri, null, null, null, null
                    )?.use {
                        it.moveToFirst()
                        it.getString(it.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)) ?: ""
                    }
                )
            } catch (e: Exception) {
                Uri.EMPTY
            }
        }
    }

    fun getType(context: Context): String {
        var retVal: String? = null
        when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> retVal = context.contentResolver.getType(uri)
            ContentResolver.SCHEME_FILE -> retVal =
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(uri.toFile().extension)
        }
        return retVal ?: "application/octect-stream"
    }

    fun getName(context: Context): String {
        var retVal: String? = null
        try {
            when (uri.scheme) {
                ContentResolver.SCHEME_CONTENT -> {
                    context.contentResolver.query(
                        uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                    )?.use {
                        it.moveToFirst()
                        retVal = it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                    }
                }

                ContentResolver.SCHEME_FILE -> retVal = uri.toFile().name
            }
        } catch (e: Exception) { /* nothing */
        }

        return retVal ?: "unknown"
    }

    fun isVCard(context: Context): Boolean {
        return (getType(context) == "text/x-vcard")
    }

    fun hasDisplayableImage(context: Context): Boolean {
        val mimeType = getType(context)
        return (mimeType.startsWith("image/") || mimeType.startsWith("video/"))
    }

    fun isAudio(context: Context): Boolean {
        return (getType(context).startsWith("audio/"))
    }

    fun isImage(context: Context): Boolean {
        return (getType(context).startsWith("image/"))
    }

    fun getSize(context: Context): Long {
        var retVal: Long? = null
        try {
            when (uri.scheme) {
                ContentResolver.SCHEME_CONTENT -> {
                    context.contentResolver.query(
                        uri, arrayOf(OpenableColumns.SIZE), null, null, null
                    )?.use {
                        it.moveToFirst()
                        retVal = it.getLong(it.getColumnIndex(OpenableColumns.SIZE))
                    }
                }

                ContentResolver.SCHEME_FILE -> retVal = uri.toFile().length()
            }
        } catch (e: Exception) { /* nothing */
        }

        return retVal ?: -1
    }

    fun getResourceBytes(context: Context): ByteArray {
        if (resourceBytes != null)
            return resourceBytes!!

        try {
            context.contentResolver.openInputStream(uri)?.use {
                resourceBytes = it.readBytes()
            }
        } catch (e: Exception) {
        }

        return resourceBytes ?: ByteArray(0)
    }

    fun releaseResourceBytes() {
        resourceBytes = null
    }

    fun removeCacheFile(): Boolean {
        // all file:// scheme files are local to the app cache dir, so can be deleted
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return try { uri.toFile().delete() }
            catch (e: Exception) { false }
        }

        return false
    }
}

