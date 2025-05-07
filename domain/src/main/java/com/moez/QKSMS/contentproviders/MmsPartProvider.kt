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
package com.moez.QKSMS.contentproviders

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException


// the primary use case for this provider is to re-provide mms attachments but with a file name
// as the last part of the uri as opposed to the system mms provider uri which has a part id as
// it's 'filename' component. with this provider external applications can save/display using the
// original file name, if available, of the attachment when it is shared and/or saved
// it seems a needlessly complicated way to achieve the requirement, but it has to be done
class MmsPartProvider : ContentProvider() {
    companion object {
        private fun getMmsPartIdFromUri(uri: Uri): String? {
            // ensure at least 2 path segments exist
            if (uri.pathSegments.size < 2)
                return null

            return try {
                return uri.pathSegments[uri.pathSegments.size - 2]
            } catch (e: Exception) {
                null
            }
        }

        private fun getUnderlyingUriFromUri(uri: Uri): Uri {
            val partId = getMmsPartIdFromUri(uri) ?: return Uri.EMPTY
            return Uri
                .Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority("mms")
                .encodedPath("part/$partId")
                .build()
        }
    }

    override fun onCreate(): Boolean {
        return true
    }

    fun getUriForMmsPartId(context: Context, partId: Long, partName: String): Uri {
        return Uri
            .Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority("${context.packageName}.mmspart")
            .encodedPath("part/$partId/$partName")
            .build()
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        return context?.contentResolver?.query(
            getUnderlyingUriFromUri(uri),
            projection,
            selection,
            selectionArgs,
            sortOrder
        )
    }

    override fun getType(uri: Uri): String? {
        return context?.contentResolver?.getType(getUnderlyingUriFromUri(uri))
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return context?.contentResolver?.insert(getUnderlyingUriFromUri(uri), values)
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        return context?.contentResolver?.update(
            getUnderlyingUriFromUri(uri),
            values,
            selection,
            selectionArgs
        ) ?: 0
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        return context?.contentResolver?.delete(
            getUnderlyingUriFromUri(uri),
            selection,
            selectionArgs
        ) ?: 0
    }

    @Throws(FileNotFoundException::class)
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        return context?.contentResolver?.openFileDescriptor(
            getUnderlyingUriFromUri(uri),
            mode)
    }
}