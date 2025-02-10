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
package dev.octoshrimpy.quik.feature.scheduled

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore.MATCH_DEFAULT
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkAdapter
import dev.octoshrimpy.quik.common.base.QkViewHolder
import dev.octoshrimpy.quik.extensions.resourceExists
import kotlinx.android.synthetic.main.scheduled_message_image_list_item.fileName
import kotlinx.android.synthetic.main.scheduled_message_image_list_item.thumbnail
import javax.inject.Inject


class ScheduledMessageAttachmentAdapter @Inject constructor(
    private val context: Context
) : QkAdapter<Uri, QkViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkViewHolder {
        return QkViewHolder(
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.scheduled_message_image_list_item, parent, false)
        )
    }

    override fun onBindViewHolder(holder: QkViewHolder, position: Int) {
        val uri = getItem(position)

        // if uri target doesn't exist anymore
        if (!uri.resourceExists(context)) {
            holder.thumbnail.setImageResource(android.R.drawable.ic_delete)
            holder.fileName.text = context.getString(R.string.attachment_missing)
            holder.fileName.visibility = View.VISIBLE
            return
        }

        val mimeType = context.contentResolver.getType(uri)

        // if attachment mime type is image/* or video/*, use image/video-frame
        if ((mimeType?.startsWith("image/") == true) ||
            (mimeType?.startsWith("video/") == true)
        ) {
            Glide
                .with(context)
                .load(uri)
                .into(holder.thumbnail)
            return
        }

        // if audio mime type, try and use embedded image if one exists
        if (mimeType?.startsWith("audio/") == true) {
            val metaDataRetriever = MediaMetadataRetriever()
            metaDataRetriever.setDataSource(context, uri)
            val embeddedPicture = metaDataRetriever.embeddedPicture
            if (embeddedPicture != null) {
                Glide
                    .with(context)
                    .load(embeddedPicture)
                    .into(holder.thumbnail)
                return
            }
        }

        // try and use icon from default app for type
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType)
        val appIcon = context
            .packageManager
            .resolveActivity(intent, MATCH_DEFAULT)
            ?.loadIcon(context.packageManager)
        if (appIcon != null)
            Glide
                .with(context)
                .load(appIcon)
                .into(holder.thumbnail)
        else if (mimeType?.startsWith("audio/") == true)
            // else, if audio, use default local audio icon
            Glide
                .with(context)
                .load(R.drawable.ic_round_volume_up_24)
                .into(holder.thumbnail)
        else
            // else, use default attachment icon
            Glide
                .with(context)
                .load(R.drawable.ic_attachment_black_24dp)
                .into(holder.thumbnail)

        // else, show file name
        val cursor = context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )
        cursor?.use {
            try {
                cursor.moveToFirst()
                holder.fileName.text = cursor.getString(
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) ?: 0
                )
                holder.fileName.visibility = View.VISIBLE
            } catch (e: Exception) {
            }
        }
    }

}
