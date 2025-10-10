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
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkAdapter
import dev.octoshrimpy.quik.common.base.QkBindingViewHolder
import dev.octoshrimpy.quik.extensions.getName
import dev.octoshrimpy.quik.feature.extensions.LoadBestIconIntoImageView
import dev.octoshrimpy.quik.feature.extensions.loadBestIconIntoImageView
import dev.octoshrimpy.quik.databinding.ScheduledMessageImageListItemBinding
import javax.inject.Inject


class ScheduledMessageAttachmentAdapter @Inject constructor(
    private val context: Context
) : QkAdapter<Uri, QkBindingViewHolder<ScheduledMessageImageListItemBinding>>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkBindingViewHolder<ScheduledMessageImageListItemBinding> =
        QkBindingViewHolder(
            ScheduledMessageImageListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: QkBindingViewHolder<ScheduledMessageImageListItemBinding>, position: Int) {
        val uri = getItem(position)

        // set best image and text to use for icon
        when (getItem(position).loadBestIconIntoImageView(context, holder.binding.thumbnail)) {
            LoadBestIconIntoImageView.Missing -> {
                holder.binding.fileName.text = context.getString(R.string.attachment_missing)
                holder.binding.fileName.visibility = View.VISIBLE
            }
            LoadBestIconIntoImageView.ActivityIcon,
            LoadBestIconIntoImageView.DefaultAudioIcon,
            LoadBestIconIntoImageView.GenericIcon -> {
                // generic style icon used, also show name
                holder.binding.fileName.text = uri.getName(context)
                holder.binding.fileName.visibility = View.VISIBLE
            }
            else -> holder.binding.fileName.visibility = View.GONE
        }
    }
}
