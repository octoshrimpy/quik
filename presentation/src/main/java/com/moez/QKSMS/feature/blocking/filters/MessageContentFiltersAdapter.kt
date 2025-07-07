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
import dev.octoshrimpy.quik.common.base.QkViewHolder
import dev.octoshrimpy.quik.model.MessageContentFilter
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.android.synthetic.main.message_content_filter_list_item.*
import kotlinx.android.synthetic.main.message_content_filter_list_item.view.*

class MessageContentFiltersAdapter : QkRealmAdapter<MessageContentFilter>() {

    val removeMessageContentFilter: Subject<Long> = PublishSubject.create()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.message_content_filter_list_item, parent, false)
        return QkViewHolder(view).apply {
            containerView.removeFilter.setOnClickListener {
                val filter = getItem(adapterPosition) ?: return@setOnClickListener
                removeMessageContentFilter.onNext(filter.id)
            }
        }
    }

    override fun onBindViewHolder(holder: QkViewHolder, position: Int) {
        val item = getItem(position)!!
        holder.caseIcon.visibility = if (item.caseSensitive) View.VISIBLE else View.GONE
        holder.regexIcon.visibility = if (item.isRegex) View.VISIBLE else View.GONE
        holder.contactsIcon.visibility = if (item.includeContacts) View.VISIBLE else View.GONE
        holder.filter.text = item.value
    }

}
