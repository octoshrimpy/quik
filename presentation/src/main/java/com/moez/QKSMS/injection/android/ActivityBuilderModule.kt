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
package dev.octoshrimpy.quik.injection.android

import dagger.Module
import dagger.android.ContributesAndroidInjector
import dev.octoshrimpy.quik.feature.backup.BackupActivity
import dev.octoshrimpy.quik.feature.blocking.BlockingActivity
import dev.octoshrimpy.quik.feature.compose.ComposeActivity
import dev.octoshrimpy.quik.feature.compose.ComposeActivityModule
import dev.octoshrimpy.quik.feature.contacts.ContactsActivity
import dev.octoshrimpy.quik.feature.contacts.ContactsActivityModule
import dev.octoshrimpy.quik.feature.conversationinfo.ConversationInfoActivity
import dev.octoshrimpy.quik.feature.gallery.GalleryActivity
import dev.octoshrimpy.quik.feature.gallery.GalleryActivityModule
import dev.octoshrimpy.quik.feature.main.MainActivity
import dev.octoshrimpy.quik.feature.main.MainActivityModule
import dev.octoshrimpy.quik.feature.messageutils.MessageUtilsActivity
import dev.octoshrimpy.quik.feature.notificationprefs.NotificationPrefsActivity
import dev.octoshrimpy.quik.feature.notificationprefs.NotificationPrefsActivityModule
import dev.octoshrimpy.quik.feature.plus.PlusActivity
import dev.octoshrimpy.quik.feature.plus.PlusActivityModule
import dev.octoshrimpy.quik.feature.qkreply.QkReplyActivity
import dev.octoshrimpy.quik.feature.qkreply.QkReplyActivityModule
import dev.octoshrimpy.quik.feature.scheduled.ScheduledActivity
import dev.octoshrimpy.quik.feature.scheduled.ScheduledActivityModule
import dev.octoshrimpy.quik.feature.settings.SettingsActivity
import dev.octoshrimpy.quik.injection.scope.ActivityScope

@Module
abstract class ActivityBuilderModule {

    @ActivityScope
    @ContributesAndroidInjector(modules = [MainActivityModule::class])
    abstract fun bindMainActivity(): MainActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [PlusActivityModule::class])
    abstract fun bindPlusActivity(): PlusActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [])
    abstract fun bindBackupActivity(): BackupActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [ComposeActivityModule::class])
    abstract fun bindComposeActivity(): ComposeActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [ContactsActivityModule::class])
    abstract fun bindContactsActivity(): ContactsActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [])
    abstract fun bindConversationInfoActivity(): ConversationInfoActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [GalleryActivityModule::class])
    abstract fun bindGalleryActivity(): GalleryActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [NotificationPrefsActivityModule::class])
    abstract fun bindNotificationPrefsActivity(): NotificationPrefsActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [QkReplyActivityModule::class])
    abstract fun bindQkReplyActivity(): QkReplyActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [ScheduledActivityModule::class])
    abstract fun bindScheduledActivity(): ScheduledActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [])
    abstract fun bindMessageUtilsActivity(): MessageUtilsActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [])
    abstract fun bindSettingsActivity(): SettingsActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [])
    abstract fun bindBlockingActivity(): BlockingActivity

}
