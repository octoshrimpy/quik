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
package dev.octoshrimpy.quik.feature.changelog

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkAdapter
import dev.octoshrimpy.quik.common.base.QkBindingViewHolder
import dev.octoshrimpy.quik.databinding.ChangelogListItemBinding
import dev.octoshrimpy.quik.manager.ChangelogManager

class ChangelogAdapter(private val context: Context) : QkAdapter<ChangelogAdapter.ChangelogItem, QkBindingViewHolder<ChangelogListItemBinding>>() {

    data class ChangelogItem(val type: Int, val label: String)

    fun setChangelog(changelog: ChangelogManager.CumulativeChangelog) {
        val changes = mutableListOf<ChangelogItem>()
        if (changelog.added.isNotEmpty()) {
            changes += ChangelogItem(0, context.getString(R.string.changelog_added))
            changes += changelog.added.map { change -> ChangelogItem(1, "• $change") }
            changes += ChangelogItem(0, "")
        }
        if (changelog.improved.isNotEmpty()) {
            changes += ChangelogItem(0, context.getString(R.string.changelog_improved))
            changes += changelog.improved.map { change -> ChangelogItem(1, "• $change") }
            changes += ChangelogItem(0, "")
        }
        if (changelog.fixed.isNotEmpty()) {
            changes += ChangelogItem(0, context.getString(R.string.changelog_fixed))
            changes += changelog.fixed.map { change -> ChangelogItem(1, "• $change") }
            changes += ChangelogItem(0, "")
        }
        if (changelog.removed.isNotEmpty()) {
            changes += ChangelogItem(0, context.getString(R.string.changelog_removed))
            changes += changelog.removed.map { change -> ChangelogItem(1, "• $change") }
            changes += ChangelogItem(0, "")
        }
        data = changes
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkBindingViewHolder<ChangelogListItemBinding> {
        val binding = ChangelogListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return QkBindingViewHolder(binding).apply {
            if (viewType == 0) {
                binding.changelogItem.setTypeface(binding.changelogItem.typeface, Typeface.BOLD)
            }
        }
    }

    override fun onBindViewHolder(holder: QkBindingViewHolder<ChangelogListItemBinding>, position: Int) {
        val item = getItem(position)
        holder.binding.changelogItem.text = item.label
    }

    override fun getItemViewType(position: Int): Int {
        return getItem(position).type
    }

}
