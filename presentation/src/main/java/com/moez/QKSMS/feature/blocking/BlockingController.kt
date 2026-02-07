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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import dev.octoshrimpy.quik.databinding.BlockingControllerBinding
import javax.inject.Inject

class BlockingController : QkController<BlockingControllerBinding, BlockingView, BlockingState, BlockingPresenter>(), BlockingView {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup): BlockingControllerBinding =
        BlockingControllerBinding.inflate(inflater, container, false)

    override val blockingManagerIntent get() = binding.blockingManager.clicks()
    override val blockedNumbersIntent get() = binding.blockedNumbers.clicks()
    override val messageContentFiltersIntent get() = binding.messageContentFilters.clicks()
    override val blockedMessagesIntent get() = binding.blockedMessages.clicks()
    override val dropClickedIntent get() = binding.drop.clicks()

    @Inject lateinit var colors: Colors
    @Inject override lateinit var presenter: BlockingPresenter

    init {
        appComponent.inject(this)
        retainViewMode = RetainViewMode.RETAIN_DETACH
    }

    override fun onViewCreated() {
        super.onViewCreated()
        binding.parent.postDelayed({ binding.parent.animateLayoutChanges = true }, 100)
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        presenter.bindIntents(this)
        setTitle(R.string.blocking_title)
        showBackButton(true)
    }

    override fun render(state: BlockingState) {
        binding.blockingManager.summary = state.blockingManager
        binding.drop.checkbox?.isChecked = state.dropEnabled
        binding.blockedMessages.isEnabled = !state.dropEnabled
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
