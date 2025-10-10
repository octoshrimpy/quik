/*
 * Copyright (C) 2020 Moez Bhatti <moez.bhatti@gmail.com>
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

package dev.octoshrimpy.quik.feature.blocking.manager

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.util.extensions.resolveThemeAttribute
import dev.octoshrimpy.quik.common.util.extensions.setVisible
import dev.octoshrimpy.quik.databinding.BlockingManagerPreferenceViewBinding

class BlockingManagerPreferenceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

    private val binding: BlockingManagerPreferenceViewBinding =
        BlockingManagerPreferenceViewBinding.inflate(LayoutInflater.from(context), this)

    var icon: Drawable? = null
        set(value) {
            field = value

            if (isInEditMode) {
                findViewById<ImageView>(R.id.iconView).setImageDrawable(value)
            } else {
                binding.iconView.setImageDrawable(value)
            }
        }

    var title: String? = null
        set(value) {
            field = value

            if (isInEditMode) {
                findViewById<TextView>(R.id.titleView).text = value
            } else {
                binding.titleView.text = value
            }
        }

    var summary: String? = null
        set(value) {
            field = value

            if (isInEditMode) {
                findViewById<TextView>(R.id.summaryView).run {
                    text = value
                    setVisible(value?.isNotEmpty() == true)
                }
            } else {
                binding.summaryView.text = value
                binding.summaryView.setVisible(value?.isNotEmpty() == true)
            }
        }

    init {
        setBackgroundResource(context.resolveThemeAttribute(R.attr.selectableItemBackground))

        context.obtainStyledAttributes(attrs, R.styleable.BlockingManagerPreferenceView).run {
            icon = getDrawable(R.styleable.BlockingManagerPreferenceView_icon)
            title = getString(R.styleable.BlockingManagerPreferenceView_title)
            summary = getString(R.styleable.BlockingManagerPreferenceView_summary)

            // If there's a custom view used for the preference's widget, inflate it
            getResourceId(R.styleable.BlockingManagerPreferenceView_widget, -1).takeIf { it != -1 }?.let { id ->
                View.inflate(context, id, binding.widgetFrame)
            }

            recycle()
        }
    }
}
