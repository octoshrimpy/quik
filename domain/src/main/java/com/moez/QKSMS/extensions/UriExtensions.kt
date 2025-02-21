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
package dev.octoshrimpy.quik.extensions

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.net.toFile

fun Uri.isEmpty() = this == Uri.EMPTY

fun Uri.isVCard(context: Context): Boolean = getType(context) == "text/x-vcard"

fun Uri.isAudio(context: Context): Boolean = getType(context).startsWith("audio/")

fun Uri.isImage(context: Context): Boolean = getType(context).startsWith("image/")

fun Uri.isVideo(context: Context): Boolean = getType(context).startsWith("video/")

fun Uri.isContact(context: Context): Boolean =
    (getType(context).lowercase() == ContactsContract.Contacts.CONTENT_ITEM_TYPE)

fun Uri.getResourceBytes(context: Context): ByteArray =
    try {
        context.contentResolver.openInputStream(this)?.use {
            it.readBytes()
        } ?: ByteArray(0)
    } catch (e: Exception) { ByteArray(0) }

fun Uri.resourceExists(context: Context): Boolean {
    var retVal: Boolean? = null
    try {
        when (scheme) {
            ContentResolver.SCHEME_CONTENT -> {
                retVal = context.contentResolver.query(
                    this, null, null, null, null
                )?.use { it.moveToFirst() }
            }
            ContentResolver.SCHEME_FILE -> retVal = this.toFile().exists()
        }
    } catch (e: Exception) { /* nothing */
    }

    return retVal ?: false
}

fun Uri.getType(context: Context): String {
    var retVal: String? = null
    when (scheme) {
        ContentResolver.SCHEME_CONTENT -> retVal = context.contentResolver.getType(this)
        ContentResolver.SCHEME_FILE -> retVal =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(this.toFile().extension)
    }
    return retVal ?: "application/octect-stream"
}

@SuppressLint("Range")
fun Uri.getName(context: Context): String? {
    var retVal: String? = null
    try {
        when (scheme) {
            ContentResolver.SCHEME_CONTENT -> {
                context.contentResolver.query(
                    this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use {
                    it.moveToFirst()
                    retVal = it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                }
            }
            ContentResolver.SCHEME_FILE -> retVal = this.toFile().name
        }
    } catch (e: Exception) { /* nothing */ }

    return retVal
}

@SuppressLint("Range")
fun Uri.getSize(context: Context): Long {
    var retVal: Long? = null
    try {
        when (scheme) {
            ContentResolver.SCHEME_CONTENT -> {
                context.contentResolver.query(
                    this, arrayOf(OpenableColumns.SIZE), null, null, null
                )?.use {
                    it.moveToFirst()
                    retVal = it.getLong(it.getColumnIndex(OpenableColumns.SIZE))
                }
            }
            ContentResolver.SCHEME_FILE -> retVal = this.toFile().length()
        }
    } catch (e: Exception) { /* nothing */
    }

    return retVal ?: -1
}

@SuppressLint("Range")
fun Uri.contactToVCard(context: Context): Uri =
    try {
        Uri.withAppendedPath(
            ContactsContract.Contacts.CONTENT_VCARD_URI,
            context.contentResolver.query(
                this, null, null, null, null
            )?.use {
                it.moveToFirst()
                it.getString(it.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)) ?: ""
            }
        )
    } catch (e: Exception) {
        Uri.EMPTY
    }

fun Uri.getDefaultActivityIconForMimeType(context: Context): Drawable? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager
            .queryIntentActivities(
                Intent(Intent.ACTION_VIEW).setDataAndType(this, getType(context)),
                PackageManager.ResolveInfoFlags.of(
                    PackageManager.MATCH_DEFAULT_ONLY.toLong()
                )
            ).let {
                if (it.size > 0) it[0].loadIcon(context.packageManager)
                else null
            }
    } else {
        // else, pre-tiramisu versions
        context.packageManager
            .queryIntentActivities(
                Intent(Intent.ACTION_VIEW).setDataAndType(this, getType(context)),
                PackageManager.MATCH_DEFAULT_ONLY
            ).let {
                if (it.size > 0) it[0].loadIcon(context.packageManager)
                else null
            }
    }
