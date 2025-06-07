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
package dev.octoshrimpy.quik.feature.compose.part

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dev.octoshrimpy.quik.common.base.QkViewHolder
import dev.octoshrimpy.quik.common.util.Colors
import dev.octoshrimpy.quik.common.widget.QkContextMenuRecyclerView
import dev.octoshrimpy.quik.extensions.isSmil
import dev.octoshrimpy.quik.extensions.isText
import dev.octoshrimpy.quik.feature.compose.BubbleUtils.canGroup
import dev.octoshrimpy.quik.feature.compose.MessagesAdapter
import dev.octoshrimpy.quik.model.Message
import dev.octoshrimpy.quik.model.MmsPart
import io.reactivex.Observable
import kotlinx.android.synthetic.main.message_list_item_in.*
import javax.inject.Inject


class PartsAdapter @Inject constructor(
    colors: Colors,
    fileBinder: FileBinder,
    imageBinder: ImageBinder,
    audioBinder: AudioBinder,
    vCardBinder: VCardBinder,
) : QkContextMenuRecyclerView.Adapter<Long, MmsPart, QkContextMenuRecyclerView.ViewHolder<MmsPart>>() {

    private val partBinders = listOf(audioBinder, imageBinder, vCardBinder, fileBinder)

    var theme: Colors.Theme = colors.theme()
        set(value) {
            field = value
            partBinders.forEach { binder -> binder.theme = value }
        }

    val clicks: Observable<Long> = Observable.merge(partBinders.map { it.clicks })

    private lateinit var message: Message
    private var previous: Message? = null
    private var next: Message? = null
    private var bodyVisible: Boolean = true
    private var audioState: MessagesAdapter.AudioState? = null

    fun setData(
        message: Message,
        previous: Message?,
        next: Message?,
        holder: QkViewHolder,
        audioState: MessagesAdapter.AudioState?
    ) {
        this.message = message
        this.previous = previous
        this.next = next
        this.bodyVisible = holder.body.visibility == View.VISIBLE
        this.data = message.parts.filter { !it.isSmil() && !it.isText() }
        this.audioState = audioState
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int)
        : QkContextMenuRecyclerView.ViewHolder<MmsPart> {
        val layout = partBinders.getOrNull(viewType)?.partLayout ?: 0
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return QkContextMenuRecyclerView.ViewHolder(view)
    }

    override fun onBindViewHolder(holder: QkContextMenuRecyclerView.ViewHolder<MmsPart>, position: Int) {
        val part = data[position]

        holder.contextMenuValue = part

        val canGroupWithPrevious = canGroup(message, previous) || position > 0
        val canGroupWithNext = canGroup(message, next) || position < itemCount - 1 || bodyVisible

        val binder = partBinders.firstOrNull { it.canBindPart(part) }
        if (binder == null)
            return

        // if audioState is set and binder is audio type, set it's audioState ref
        if ((audioState != null) && (binder is AudioBinder))
            binder.audioState = audioState!!

        binder.bindPart(holder, part, message, canGroupWithPrevious, canGroupWithNext)
    }

    override fun getItemViewType(position: Int): Int {
        return partBinders.indexOfFirst { it.canBindPart(data[position]) }
    }

}