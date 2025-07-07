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

import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import dev.octoshrimpy.quik.common.base.QkPresenter
import dev.octoshrimpy.quik.repository.MessageContentFilterRepository
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class MessageContentFiltersPresenter @Inject constructor(
    private val filterRepo: MessageContentFilterRepository,
) : QkPresenter<MessageContentFiltersView, MessageContentFiltersState>(
        MessageContentFiltersState(filters = filterRepo.getMessageContentFilters())
) {

    override fun bindIntents(view: MessageContentFiltersView) {
        super.bindIntents(view)

        view.removeFilter()
            .observeOn(Schedulers.io())
            .doOnNext(filterRepo::removeFilter)
            .subscribeOn(Schedulers.io())
            .autoDisposable(view.scope())
            .subscribe()

        view.addFilter()
            .autoDisposable(view.scope())
            .subscribe { view.showAddDialog() }

        view.saveFilter()
            .observeOn(Schedulers.io())
            .subscribeOn(Schedulers.io())
            .autoDisposable(view.scope())
            .subscribe { filterData -> filterRepo.createFilter(filterData) }
    }

}
