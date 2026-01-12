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
package dev.octoshrimpy.quik.feature.themepicker

import android.content.Context
import android.content.res.Resources
import android.view.LayoutInflater
import android.view.ViewGroup
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayout
import dev.octoshrimpy.quik.common.base.QkAdapter
import dev.octoshrimpy.quik.common.base.QkBindingViewHolder
import dev.octoshrimpy.quik.common.util.Colors
import dev.octoshrimpy.quik.common.util.extensions.dpToPx
import dev.octoshrimpy.quik.common.util.extensions.setBackgroundTint
import dev.octoshrimpy.quik.common.util.extensions.setTint
import dev.octoshrimpy.quik.common.util.extensions.setVisible
import dev.octoshrimpy.quik.databinding.ThemeListItemBinding
import dev.octoshrimpy.quik.databinding.ThemePaletteListItemBinding
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

class ThemeAdapter @Inject constructor(
    private val context: Context,
    private val colors: Colors
) : QkAdapter<List<Int>, QkBindingViewHolder<ThemePaletteListItemBinding>>() {

    val colorSelected: Subject<Int> = PublishSubject.create()

    var selectedColor: Int = -1
        set(value) {
            val oldPosition = data.indexOfFirst { it.contains(field) }
            val newPosition = data.indexOfFirst { it.contains(value) }

            field = value
            iconTint = colors.textPrimaryOnThemeForColor(value)

            oldPosition.takeIf { it != -1 }?.let { position -> notifyItemChanged(position) }
            newPosition.takeIf { it != -1 }?.let { position -> notifyItemChanged(position) }
        }

    private var iconTint = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkBindingViewHolder<ThemePaletteListItemBinding> {
        val binding = ThemePaletteListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.palette.flexWrap = FlexWrap.WRAP
        binding.palette.flexDirection = FlexDirection.ROW

        return QkBindingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QkBindingViewHolder<ThemePaletteListItemBinding>, position: Int) {
        val palette = getItem(position)
        val binding = holder.binding

        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        val minPadding = (16 * 6).dpToPx(context)
        val size = if (screenWidth - minPadding > (56 * 5).dpToPx(context)) {
            56.dpToPx(context)
        } else {
            (screenWidth - minPadding) / 5
        }
        val swatchPadding = (screenWidth - size * 5) / 12

        binding.palette.removeAllViews()
        binding.palette.setPadding(swatchPadding, swatchPadding, swatchPadding, swatchPadding)

        (palette.subList(0, 5) + palette.subList(5, 10).reversed())
                .mapIndexed { index, color ->
                    val itemBinding = ThemeListItemBinding.inflate(LayoutInflater.from(context), binding.palette, false)

                    // Send clicks to the selected subject
                    itemBinding.root.setOnClickListener { colorSelected.onNext(color) }

                    // Apply the color to the view
                    itemBinding.theme.setBackgroundTint(color)

                    // Control the check visibility and tint
                    itemBinding.check.setVisible(color == selectedColor)
                    itemBinding.check.setTint(iconTint)

                    // Update the size so that the spacing is perfectly even
                    itemBinding.root.layoutParams = (itemBinding.root.layoutParams as FlexboxLayout.LayoutParams).apply {
                        height = size
                        width = size
                        isWrapBefore = index % 5 == 0
                        setMargins(swatchPadding, swatchPadding, swatchPadding, swatchPadding)
                    }

                    itemBinding.root
                }
                .forEach { theme -> binding.palette.addView(theme) }
    }

}