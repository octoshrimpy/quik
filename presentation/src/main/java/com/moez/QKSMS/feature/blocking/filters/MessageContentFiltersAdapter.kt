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
package dev.octoshrimpy.quik.feature.blocking.filters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkRealmAdapter
import dev.octoshrimpy.quik.common.base.QkBindingViewHolder
import dev.octoshrimpy.quik.model.MessageContentFilter
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import dev.octoshrimpy.quik.databinding.MessageContentFilterListItemBinding

class MessageContentFiltersAdapter : QkRealmAdapter<MessageContentFilter, QkBindingViewHolder<MessageContentFilterListItemBinding>>() {

    val removeMessageContentFilter: Subject<Long> = PublishSubject.create()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkBindingViewHolder<MessageContentFilterListItemBinding> {
        val binding = MessageContentFilterListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return QkBindingViewHolder(binding).apply {
            binding.removeFilter.setOnClickListener {
                val filter = getItem(adapterPosition) ?: return@setOnClickListener
                removeMessageContentFilter.onNext(filter.id)
            }
        }
    }

    override fun onBindViewHolder(holder: QkBindingViewHolder<MessageContentFilterListItemBinding>, position: Int) {
        val item = getItem(position)!!
        holder.binding.caseIcon.visibility = if (item.caseSensitive) View.VISIBLE else View.GONE
        holder.binding.regexIcon.visibility = if (item.isRegex) View.VISIBLE else View.GONE
        holder.binding.contactsIcon.visibility = if (item.includeContacts) View.VISIBLE else View.GONE
        holder.binding.filter.text = item.value
    }

}
