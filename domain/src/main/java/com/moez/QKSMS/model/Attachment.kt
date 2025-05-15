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

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.net.toFile
import androidx.core.view.inputmethod.InputContentInfoCompat
import dev.octoshrimpy.quik.extensions.contactToVCard
import dev.octoshrimpy.quik.extensions.getName
import dev.octoshrimpy.quik.extensions.getResourceBytes
import dev.octoshrimpy.quik.extensions.getSize
import dev.octoshrimpy.quik.extensions.getType
import dev.octoshrimpy.quik.extensions.isAudio
import dev.octoshrimpy.quik.extensions.isContact
import dev.octoshrimpy.quik.extensions.isImage
import dev.octoshrimpy.quik.extensions.isVCard


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
        if (uri.isContact(context))
            uri = uri.contactToVCard(context)
    }

    fun isVCard(context: Context): Boolean = uri.isVCard(context)

    fun isAudio(context: Context): Boolean = uri.isAudio(context)

    fun isImage(context: Context): Boolean = uri.isImage(context)

    fun getType(context: Context): String = uri.getType(context)

    fun getName(context: Context): String = uri.getName(context) ?: "unknown"

    fun getSize(context: Context): Long = uri.getSize(context)

    fun hasDisplayableImage(context: Context): Boolean {
        val mimeType = getType(context)
        return (mimeType.startsWith("image/") || mimeType.startsWith("video/"))
    }

    fun getResourceBytes(context: Context): ByteArray {
        // cache resource bytes by loading first time only
        if (resourceBytes != null)
            return resourceBytes!!

        resourceBytes = uri.getResourceBytes(context)

        return resourceBytes!!
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

