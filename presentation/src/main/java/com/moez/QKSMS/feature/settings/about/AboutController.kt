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
package dev.octoshrimpy.quik.feature.settings.about

import android.view.View
import android.view.ViewGroup
import com.jakewharton.rxbinding2.view.clicks
import dev.octoshrimpy.quik.BuildConfig
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkController
import dev.octoshrimpy.quik.common.widget.PreferenceView
import dev.octoshrimpy.quik.injection.appComponent
import io.reactivex.Observable
import kotlinx.android.synthetic.main.about_controller.*
import javax.inject.Inject

class AboutController : QkController<AboutView, Unit, AboutPresenter>(), AboutView {

    @Inject override lateinit var presenter: AboutPresenter

    init {
        appComponent.inject(this)
        layoutRes = R.layout.about_controller
    }

    override fun onViewCreated() {
        version.summary = BuildConfig.VERSION_NAME
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        presenter.bindIntents(this)
        setTitle(R.string.about_title)
        showBackButton(true)
    }

    override fun toggleOpenSourceContentVisibility() {
        val isVisible = open_source_content.visibility == View.VISIBLE
        open_source_content.visibility = if (isVisible) View.GONE else View.VISIBLE
        openSourceHeader.summary = if (isVisible) "Tap to expand" else "Tap to collapse"
    }

    override fun preferenceClicks(): Observable<PreferenceView> {
        val preferences = findPreferenceViews(preferences) // Get all PreferenceView elements
        return preferences
            .map { preference -> preference.clicks().map { preference } }
            .let { Observable.merge(it) }
    }

    private fun findPreferenceViews(viewGroup: ViewGroup): List<PreferenceView> {
        val preferences = mutableListOf<PreferenceView>()
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is PreferenceView) {
                preferences.add(child)
            } else if (child is ViewGroup) {
                preferences.addAll(findPreferenceViews(child)) // Recursively search in child ViewGroups
            }
        }
        return preferences
    }

    override fun render(state: Unit) {
        // No special rendering required
    }

}