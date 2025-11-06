/*
 * Copyright (C) 2019 Moez Bhatti <moez.bhatti@gmail.com>
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
package dev.octoshrimpy.quik.feature.contacts

import androidx.lifecycle.ViewModel
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import dev.octoshrimpy.quik.injection.ViewModelKey

@Module
class ContactsActivityModule {

    @Provides
    fun provideIsSharing(activity: ContactsActivity): Boolean {
        return activity.intent.extras?.getBoolean(ContactsActivity.SHARING_KEY, false) ?: false
    }

    @Provides
    fun provideChips(activity: ContactsActivity): HashMap<String, String?> {
        return activity.intent.extras?.getSerializable(ContactsActivity.CHIPS_KEY)
                ?.let { serializable -> serializable as? HashMap<String, String?> }
                ?: hashMapOf()
    }

    @Provides
    @IntoMap
    @ViewModelKey(ContactsViewModel::class)
    fun provideContactsViewModel(viewModel: ContactsViewModel): ViewModel = viewModel

}
