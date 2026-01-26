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
package dev.octoshrimpy.quik.feature.settings.swipe

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.jakewharton.rxbinding2.view.clicks
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.QkDialog
import dev.octoshrimpy.quik.common.base.QkController
import dev.octoshrimpy.quik.common.util.Colors
import dev.octoshrimpy.quik.common.util.extensions.animateLayoutChanges
import dev.octoshrimpy.quik.common.util.extensions.setBackgroundTint
import dev.octoshrimpy.quik.common.util.extensions.setTint
import dev.octoshrimpy.quik.databinding.SwipeActionsControllerBinding
import dev.octoshrimpy.quik.injection.appComponent
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

class SwipeActionsController : QkController<SwipeActionsControllerBinding, SwipeActionsView, SwipeActionsState, SwipeActionsPresenter>(), SwipeActionsView {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup): SwipeActionsControllerBinding =
        SwipeActionsControllerBinding.inflate(inflater, container, false)

    @Inject override lateinit var presenter: SwipeActionsPresenter
    @Inject lateinit var actionsDialog: QkDialog
    @Inject lateinit var colors: Colors

    /**
     * Allows us to subscribe to [actionClicks] more than once
     */
    private val actionClicks: Subject<SwipeActionsView.Action> = PublishSubject.create()

    init {
        appComponent.inject(this)

        actionsDialog.adapter.setData(R.array.settings_swipe_actions)
    }

    override fun onViewCreated() {
        colors.theme().let { theme ->
            binding.rightIcon.setBackgroundTint(theme.theme)
            binding.rightIcon.setTint(theme.textPrimary)
            binding.leftIcon.setBackgroundTint(theme.theme)
            binding.leftIcon.setTint(theme.textPrimary)
        }

        binding.right.postDelayed({ binding.right.animateLayoutChanges = true }, 100)
        binding.left.postDelayed({ binding.left.animateLayoutChanges = true }, 100)

        Observable.merge(
                binding.right.clicks().map { SwipeActionsView.Action.RIGHT },
                binding.left.clicks().map { SwipeActionsView.Action.LEFT })
                .autoDisposable(scope())
                .subscribe(actionClicks)
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        presenter.bindIntents(this)
        setTitle(R.string.settings_swipe_actions)
        showBackButton(true)
    }

    override fun actionClicks(): Observable<SwipeActionsView.Action> = actionClicks

    override fun actionSelected(): Observable<Int> = actionsDialog.adapter.menuItemClicks

    override fun showSwipeActions(selected: Int) {
        actionsDialog.adapter.selectedItem = selected
        activity?.let(actionsDialog::show)
    }

    override fun render(state: SwipeActionsState) {
        binding.rightIcon.isVisible = state.rightIcon != 0
        binding.rightIcon.setImageResource(state.rightIcon)
        binding.rightLabel.text = state.rightLabel

        binding.leftIcon.isVisible = state.leftIcon != 0
        binding.leftIcon.setImageResource(state.leftIcon)
        binding.leftLabel.text = state.leftLabel
    }

}