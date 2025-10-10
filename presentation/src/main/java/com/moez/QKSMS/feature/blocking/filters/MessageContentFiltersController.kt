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
import androidx.appcompat.app.AlertDialog
import com.jakewharton.rxbinding2.view.clicks
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkController
import dev.octoshrimpy.quik.common.util.Colors
import dev.octoshrimpy.quik.common.util.extensions.setBackgroundTint
import dev.octoshrimpy.quik.common.util.extensions.setTint
import dev.octoshrimpy.quik.common.widget.PreferenceView
import dev.octoshrimpy.quik.injection.appComponent
import dev.octoshrimpy.quik.model.MessageContentFilterData
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.android.synthetic.main.message_content_filters_add_dialog.view.*
import kotlinx.android.synthetic.main.message_content_filters_controller.*
import kotlinx.android.synthetic.main.settings_switch_widget.view.*
import javax.inject.Inject

class MessageContentFiltersController : QkController<MessageContentFiltersView, MessageContentFiltersState,
        MessageContentFiltersPresenter>(), MessageContentFiltersView {

    @Inject override lateinit var presenter: MessageContentFiltersPresenter
    @Inject lateinit var colors: Colors

    private val adapter = MessageContentFiltersAdapter()
    private val saveFilterSubject: Subject<MessageContentFilterData> = PublishSubject.create()

    init {
        appComponent.inject(this)
        retainViewMode = RetainViewMode.RETAIN_DETACH
        layoutRes = R.layout.message_content_filters_controller
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        presenter.bindIntents(this)
        setTitle(R.string.message_content_filters_title)
        showBackButton(true)
    }

    override fun onViewCreated() {
        super.onViewCreated()
        add.setBackgroundTint(colors.theme().theme)
        add.setTint(colors.theme().textPrimary)
        adapter.emptyView = empty
        filters.adapter = adapter
    }

    override fun render(state: MessageContentFiltersState) {
        adapter.updateData(state.filters)
    }

    override fun removeFilter(): Observable<Long> = adapter.removeMessageContentFilter
    override fun addFilter(): Observable<*> = add.clicks()
    override fun saveFilter(): Observable<MessageContentFilterData> = saveFilterSubject

    override fun showAddDialog() {
        val layout = LayoutInflater.from(activity).inflate(R.layout.message_content_filters_add_dialog, null)

        (0 until layout.add_dialog.childCount)
            .map { index -> layout.add_dialog.getChildAt(index) }
            .mapNotNull { view -> view as? PreferenceView }
            .map { preference -> preference.clicks().map { preference } }
            .let { Observable.merge(it) }
            .autoDisposable(scope())
            .subscribe {
                it.checkbox.isChecked = !it.checkbox.isChecked
                layout.caseSensitivity.isEnabled = !layout.regexp.checkbox.isChecked
            }

        val dialog = AlertDialog.Builder(activity!!)
                .setView(layout)
                .setPositiveButton(R.string.message_content_filters_dialog_create) { _, _ ->
                    var text = layout.input.text.toString();
                    if (!text.isBlank()) {
                        if (!layout.regexp.checkbox.isChecked) text = text.trim()
                        saveFilterSubject.onNext(
                            MessageContentFilterData(
                                text,
                                layout.caseSensitivity.checkbox.isChecked && !layout.regexp.checkbox.isChecked,
                                layout.regexp.checkbox.isChecked,
                                layout.contacts.checkbox.isChecked
                            )
                        )
                    }
                }
                .setNegativeButton(R.string.button_cancel) { _, _ -> }
        dialog.show()
    }

}
