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
package dev.octoshrimpy.quik.feature.compose

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.provider.MediaStore.MATCH_DEFAULT
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkAdapter
import dev.octoshrimpy.quik.common.base.QkViewHolder
import dev.octoshrimpy.quik.common.util.extensions.getDisplayName
import dev.octoshrimpy.quik.extensions.resourceExists
import dev.octoshrimpy.quik.model.Attachment
import ezvcard.Ezvcard
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.android.synthetic.main.attachment_file_list_item.*
import kotlinx.android.synthetic.main.attachment_contact_list_item.*
import javax.inject.Inject


class AttachmentAdapter @Inject constructor(
    private val context: Context
) : QkAdapter<Attachment, QkViewHolder>() {

    companion object {
        private const val VIEW_TYPE_FILE = 0
        private const val VIEW_TYPE_CONTACT = 1
    }

    val attachmentDeleted: Subject<Attachment> = PublishSubject.create()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        val view = inflater.inflate(
            if (viewType == VIEW_TYPE_CONTACT) R.layout.attachment_contact_list_item
            else R.layout.attachment_file_list_item,
            parent,
            false
        )

        return QkViewHolder(view).apply {
            view.setOnClickListener {
                val attachment = getItem(adapterPosition)
                attachmentDeleted.onNext(attachment)
            }
        }
    }

    override fun onBindViewHolder(holder: QkViewHolder, position: Int) {
        val attachment = getItem(position)

        if (attachment.isVCard(context)) {
            try {
                val displayName = Ezvcard.parse(
                    String(attachment.getResourceBytes(context))
                ).first().getDisplayName() ?: ""
                holder.name.text = displayName
                holder.name.isVisible = displayName.isNotEmpty()
            } catch (e: Exception) {
                // npe from Ezvcard first() call above can be thrown if resource bytes cannot
                // be retrieved from contact resource provider
                holder.vCardAvatar.setImageResource(android.R.drawable.ic_delete)
                holder.name.text = context.getString(R.string.attachment_missing)
                holder.name.isVisible = true
            }
            return
        }

        holder.fileName.visibility = View.GONE

        // if attachment uri is missing
        if (!attachment.uri.resourceExists(context)) {
            holder.thumbnail.setImageResource(android.R.drawable.ic_delete)
            holder.fileName.text = context.getString(R.string.attachment_missing)
            holder.fileName.visibility = View.VISIBLE
            return
        }

        val uri = attachment.uri
        val mimeType = attachment.getType(context)

        // if attachment mime type is image/* or video/*, use image/frame
        if (attachment.hasDisplayableImage(context)) {
            Glide
                .with(context)
                .load(uri)
                .into(holder.thumbnail)
            return
        }

        // if audio mime type, try and use embedded image if one exists
        if (attachment.isAudio(context)) {
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
        holder.fileName.text = attachment.getName(context)
        holder.fileName.visibility = View.VISIBLE
    }

    override fun getItemViewType(position: Int) = when (getItem(position).isVCard(context)) {
        true -> VIEW_TYPE_CONTACT
        else -> VIEW_TYPE_FILE
    }

}
