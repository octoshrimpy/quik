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
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkRealmAdapter
import dev.octoshrimpy.quik.common.base.QkViewHolder
import dev.octoshrimpy.quik.common.util.DateFormatter
import dev.octoshrimpy.quik.model.Contact
import dev.octoshrimpy.quik.model.Recipient
import dev.octoshrimpy.quik.model.ScheduledMessage
import dev.octoshrimpy.quik.repository.ContactRepository
import dev.octoshrimpy.quik.util.PhoneNumberUtils
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.android.synthetic.main.scheduled_message_list_item.*
import kotlinx.android.synthetic.main.scheduled_message_list_item.view.*
import javax.inject.Inject

class ScheduledMessageAdapter @Inject constructor(
    private val context: Context,
    private val contactRepo: ContactRepository,
    private val dateFormatter: DateFormatter,
    private val phoneNumberUtils: PhoneNumberUtils
) : QkRealmAdapter<ScheduledMessage>() {

    private val contacts by lazy { contactRepo.getContacts() }
    private val contactCache = ContactCache()
    private val imagesViewPool = RecyclerView.RecycledViewPool()

    val clicks: Subject<Long> = PublishSubject.create()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.scheduled_message_list_item, parent, false)

        view.attachments.adapter = ScheduledMessageAttachmentAdapter(context)
        view.attachments.setRecycledViewPool(imagesViewPool)

        return QkViewHolder(view).apply {
            view.setOnClickListener {
                val message = getItem(adapterPosition) ?: return@setOnClickListener
                if (toggleSelection(message.id, false))
                    view.isActivated = isSelected(message.id)
            }
            view.setOnClickListener {
                val message = getItem(adapterPosition) ?: return@setOnClickListener
                toggleSelection(message.id)
                view.isActivated = isSelected(message.id)
            }
        }
    }

    override fun onBindViewHolder(holder: QkViewHolder, position: Int) {
        val message = getItem(position) ?: return

        // GroupAvatarView only accepts recipients, so map the phone numbers to recipients
        holder.avatars.recipients =
            message.recipients.map { address -> Recipient(address = address) }

        holder.recipients.text = message.recipients.joinToString(",") { address ->
            contactCache[address]?.name?.takeIf { it.isNotBlank() } ?: address
        }

        holder.date.text = dateFormatter.getScheduledTimestamp(message.date)
        holder.body.text = message.body

        // update the selected/highlighted state
        holder.containerView.isActivated = isSelected(message.id) || highlight == message.id

        val adapter = holder.attachments.adapter as ScheduledMessageAttachmentAdapter
        adapter.data = message.attachments.map(Uri::parse)
        holder.attachments.isVisible = message.attachments.isNotEmpty()
    }

    override fun getItemId(position: Int): Long {
        return getItem(position)?.id ?: -1
    }

    /**
     * Cache the contacts in a map by the address, because the messages we're binding don't have
     * a reference to the contact.
     */
    private inner class ContactCache : HashMap<String, Contact?>() {

        override fun get(key: String): Contact? {
            if (super.get(key)?.isValid != true) {
                set(key, contacts.firstOrNull { contact ->
                    contact.numbers.any {
                        phoneNumberUtils.compare(it.address, key)
                    }
                })
            }
            return super.get(key)?.takeIf { it.isValid }
        }
    }
}
