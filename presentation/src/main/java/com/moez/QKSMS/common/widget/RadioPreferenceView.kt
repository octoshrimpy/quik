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
package dev.octoshrimpy.quik.common.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.util.Colors
import dev.octoshrimpy.quik.common.util.extensions.forwardTouches
import dev.octoshrimpy.quik.common.util.extensions.resolveThemeAttribute
import dev.octoshrimpy.quik.common.util.extensions.resolveThemeColor
import dev.octoshrimpy.quik.common.util.extensions.setVisible
import dev.octoshrimpy.quik.databinding.RadioPreferenceViewBinding
import dev.octoshrimpy.quik.injection.appComponent
import javax.inject.Inject

class RadioPreferenceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

    @Inject lateinit var colors: Colors
    private var layout: RadioPreferenceViewBinding

    var title: String? = null
        set(value) {
            field = value

            if (isInEditMode) {
                findViewById<TextView>(R.id.titleView).text = value
            } else {
                layout.titleView.text = value
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
                layout.summaryView.text = value
                layout.summaryView.setVisible(value?.isNotEmpty() == true)
            }
        }

    init {
        if (!isInEditMode) {
            appComponent.inject(this)
        }

        layout = RadioPreferenceViewBinding.inflate(LayoutInflater.from(context), this)
        setBackgroundResource(context.resolveThemeAttribute(R.attr.selectableItemBackground))

        val states = arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked))

        val themeColor = when (isInEditMode) {
            true -> context.resources.getColor(R.color.tools_theme)
            false -> colors.theme().theme
        }
        val textSecondary = context.resolveThemeColor(android.R.attr.textColorTertiary)
        layout.radioButton.buttonTintList = ColorStateList(states, intArrayOf(themeColor, textSecondary))
        layout.radioButton.forwardTouches(this)

        context.obtainStyledAttributes(attrs, R.styleable.RadioPreferenceView).run {
            title = getString(R.styleable.RadioPreferenceView_title)
            summary = getString(R.styleable.RadioPreferenceView_summary)

            // If there's a custom view used for the preference's widget, inflate it
            getResourceId(R.styleable.RadioPreferenceView_widget, -1).takeIf { it != -1 }?.let { id ->
                View.inflate(context, id, layout.widgetFrame)
            }

            recycle()
        }
    }

}