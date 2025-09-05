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
package dev.octoshrimpy.quik.feature.compose.editing

import android.view.LayoutInflater
import android.view.ViewGroup
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkAdapter
import dev.octoshrimpy.quik.common.base.QkBindingViewHolder
import dev.octoshrimpy.quik.databinding.ContactNumberListItemBinding
import dev.octoshrimpy.quik.model.PhoneNumber

class PhoneNumberAdapter : QkAdapter<PhoneNumber, QkBindingViewHolder<ContactNumberListItemBinding>>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkBindingViewHolder<ContactNumberListItemBinding> {
        val binding = ContactNumberListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return QkBindingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QkBindingViewHolder<ContactNumberListItemBinding>, position: Int) {
        val number = getItem(position)

        holder.binding.address.text = number.address
        holder.binding.type.text = number.type
    }

    override fun areItemsTheSame(old: PhoneNumber, new: PhoneNumber): Boolean {
        return old.type == new.type && old.address == new.address
    }

    override fun areContentsTheSame(old: PhoneNumber, new: PhoneNumber): Boolean {
        return old.type == new.type && old.address == new.address
    }

}