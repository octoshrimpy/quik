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
package dev.octoshrimpy.quik.feature.blocking

import android.view.View
import com.bluelinelabs.conductor.RouterTransaction
import com.jakewharton.rxbinding2.view.clicks
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.QkChangeHandler
import dev.octoshrimpy.quik.common.base.QkController
import dev.octoshrimpy.quik.common.util.Colors
import dev.octoshrimpy.quik.common.util.extensions.animateLayoutChanges
import dev.octoshrimpy.quik.feature.blocking.manager.BlockingManagerController
import dev.octoshrimpy.quik.feature.blocking.messages.BlockedMessagesController
import dev.octoshrimpy.quik.feature.blocking.numbers.BlockedNumbersController
import dev.octoshrimpy.quik.feature.blocking.filters.MessageContentFiltersController
import dev.octoshrimpy.quik.injection.appComponent
import kotlinx.android.synthetic.main.blocking_controller.*
import kotlinx.android.synthetic.main.settings_switch_widget.view.*
import javax.inject.Inject

class BlockingController : QkController<BlockingView, BlockingState, BlockingPresenter>(), BlockingView {

    override val blockingManagerIntent by lazy { blockingManager.clicks() }
    override val blockedNumbersIntent by lazy { blockedNumbers.clicks() }
    override val messageContentFiltersIntent by lazy { messageContentFilters.clicks() }
    override val blockedMessagesIntent by lazy { blockedMessages.clicks() }
    override val dropClickedIntent by lazy { drop.clicks() }

    @Inject lateinit var colors: Colors
    @Inject override lateinit var presenter: BlockingPresenter

    init {
        appComponent.inject(this)
        retainViewMode = RetainViewMode.RETAIN_DETACH
        layoutRes = R.layout.blocking_controller
    }

    override fun onViewCreated() {
        super.onViewCreated()
        parent.postDelayed({ parent?.animateLayoutChanges = true }, 100)
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        presenter.bindIntents(this)
        setTitle(R.string.blocking_title)
        showBackButton(true)
    }

    override fun render(state: BlockingState) {
        blockingManager.summary = state.blockingManager
        drop.checkbox.isChecked = state.dropEnabled
        blockedMessages.isEnabled = !state.dropEnabled
    }

    override fun openBlockedNumbers() {
        router.pushController(RouterTransaction.with(BlockedNumbersController())
                .pushChangeHandler(QkChangeHandler())
                .popChangeHandler(QkChangeHandler()))
    }

    override fun openMessageContentFilters() {
        router.pushController(RouterTransaction.with(MessageContentFiltersController())
            .pushChangeHandler(QkChangeHandler())
            .popChangeHandler(QkChangeHandler()))
    }

    override fun openBlockedMessages() {
        router.pushController(RouterTransaction.with(BlockedMessagesController())
                .pushChangeHandler(QkChangeHandler())
                .popChangeHandler(QkChangeHandler()))
    }

    override fun openBlockingManager() {
        router.pushController(RouterTransaction.with(BlockingManagerController())
                .pushChangeHandler(QkChangeHandler())
                .popChangeHandler(QkChangeHandler()))
    }

}
