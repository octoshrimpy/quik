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
package dev.octoshrimpy.quik.feature.main

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.Navigator
import dev.octoshrimpy.quik.common.base.QkAdapter
import dev.octoshrimpy.quik.common.base.QkBindingViewHolder
import dev.octoshrimpy.quik.common.util.Colors
import dev.octoshrimpy.quik.common.util.DateFormatter
import dev.octoshrimpy.quik.common.util.extensions.setVisible
import dev.octoshrimpy.quik.extensions.removeAccents
import dev.octoshrimpy.quik.model.SearchResult
import dev.octoshrimpy.quik.databinding.SearchListItemBinding
import javax.inject.Inject

class SearchAdapter @Inject constructor(
    colors: Colors,
    private val context: Context,
    private val dateFormatter: DateFormatter,
    private val navigator: Navigator
) : QkAdapter<SearchResult, QkBindingViewHolder<SearchListItemBinding>>() {

    private val highlightColor: Int by lazy { colors.theme().highlight }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkBindingViewHolder<SearchListItemBinding> {
        val binding = SearchListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return QkBindingViewHolder(binding).apply {
            itemView.setOnClickListener {
                val result = getItem(adapterPosition)
                navigator.showConversation(result.conversation.id, result.query.takeIf { result.messages > 0 })
            }
        }
    }

    override fun onBindViewHolder(holder: QkBindingViewHolder<SearchListItemBinding>, position: Int) {
        val previous = data.getOrNull(position - 1)
        val result = getItem(position)

        holder.binding.resultsHeader.setVisible(result.messages > 0 && previous?.messages == 0)

        val query = result.query
        val title = SpannableString(result.conversation.getTitle())
        var index = title.removeAccents().indexOf(query, ignoreCase = true)

        while (index >= 0) {
            title.setSpan(BackgroundColorSpan(highlightColor), index, index + query.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            index = title.indexOf(query, index + query.length, true)
        }
        holder.binding.title.text = title

        holder.binding.avatars.recipients = result.conversation.recipients

        when (result.messages == 0) {
            true -> {
                holder.binding.date.setVisible(true)
                holder.binding.date.text = dateFormatter.getConversationTimestamp(result.conversation.date)
                holder.binding.snippet.text = when (result.conversation.me) {
                    true -> context.getString(R.string.main_sender_you, result.conversation.snippet)
                    false -> result.conversation.snippet
                }
            }

            false -> {
                holder.binding.date.setVisible(false)
                holder.binding.snippet.text = context.getString(R.string.main_message_results, result.messages)
            }
        }
    }

    override fun areItemsTheSame(old: SearchResult, new: SearchResult): Boolean {
        return old.conversation.id == new.conversation.id && old.messages > 0 == new.messages > 0
    }

    override fun areContentsTheSame(old: SearchResult, new: SearchResult): Boolean {
        return old.query == new.query && // Queries are the same
                old.conversation.id == new.conversation.id // Conversation id is the same
                && old.messages == new.messages // Result count is the same
    }
}
