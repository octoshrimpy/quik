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
import dev.octoshrimpy.quik.feature.widget.WidgetProvider
import dev.octoshrimpy.quik.injection.scope.ActivityScope
import dev.octoshrimpy.quik.receiver.BlockThreadReceiver
import dev.octoshrimpy.quik.receiver.BootReceiver
import dev.octoshrimpy.quik.receiver.DefaultSmsChangedReceiver
import dev.octoshrimpy.quik.receiver.DeleteMessagesReceiver
import dev.octoshrimpy.quik.receiver.MarkArchivedReceiver
import dev.octoshrimpy.quik.receiver.MarkReadReceiver
import dev.octoshrimpy.quik.receiver.MarkSeenReceiver
import dev.octoshrimpy.quik.receiver.MmsReceivedReceiver
import dev.octoshrimpy.quik.receiver.MmsReceiver
import dev.octoshrimpy.quik.receiver.MmsSentReceiver
import dev.octoshrimpy.quik.receiver.MmsUpdatedReceiver
import dev.octoshrimpy.quik.receiver.NightModeReceiver
import dev.octoshrimpy.quik.receiver.RemoteMessagingReceiver
import dev.octoshrimpy.quik.receiver.SendScheduledMessageReceiver
import dev.octoshrimpy.quik.receiver.SmsDeliveredReceiver
import dev.octoshrimpy.quik.receiver.SmsProviderChangedReceiver
import dev.octoshrimpy.quik.receiver.SmsReceiver
import dev.octoshrimpy.quik.receiver.SmsSentReceiver
import dev.octoshrimpy.quik.receiver.SpeakThreadsReceiver
import dev.octoshrimpy.quik.receiver.StartActivityFromWidgetReceiver

@Module
abstract class BroadcastReceiverBuilderModule {

    @ActivityScope
    @ContributesAndroidInjector()
    abstract fun bindBlockThreadReceiver(): BlockThreadReceiver

    @ActivityScope
    @ContributesAndroidInjector()
    abstract fun bindBootReceiver(): BootReceiver

    @ActivityScope
    @ContributesAndroidInjector()
    abstract fun bindDefaultSmsChangedReceiver(): DefaultSmsChangedReceiver

    @ActivityScope
    @ContributesAndroidInjector()
    abstract fun bindDeleteMessagesReceiver(): DeleteMessagesReceiver

    @ActivityScope
    @ContributesAndroidInjector
    abstract fun bindMarkArchivedReceiver(): MarkArchivedReceiver

    @ActivityScope
    @ContributesAndroidInjector()
    abstract fun bindMarkReadReceiver(): MarkReadReceiver

    @ActivityScope
    @ContributesAndroidInjector()
    abstract fun bindSpeakThreadsReceiver(): SpeakThreadsReceiver

    @ActivityScope
    @ContributesAndroidInjector()
    abstract fun bindStartActivityFromWidgetReceiver(): StartActivityFromWidgetReceiver

    @ActivityScope
    @ContributesAndroidInjector()
    abstract fun bindMarkSeenReceiver(): MarkSeenReceiver

    @ActivityScope
    @ContributesAndroidInjector()
    abstract fun bindMmsReceivedReceiver(): MmsReceivedReceiver

    @ActivityScope
    @ContributesAndroidInjector()
    abstract fun bindMmsReceiver(): MmsReceiver

    @ActivityScope
    @ContributesAndroidInjector()
    abstract fun bindMmsSentReceiver(): MmsSentReceiver

    @ActivityScope
    @ContributesAndroidInjector()
    abstract fun bindMmsUpdatedReceiver(): MmsUpdatedReceiver

    @ActivityScope
    @ContributesAndroidInjector()
    abstract fun bindNightModeReceiver(): NightModeReceiver

    @ActivityScope
    @ContributesAndroidInjector()
    abstract fun bindRemoteMessagingReceiver(): RemoteMessagingReceiver

    @ActivityScope
    @ContributesAndroidInjector()
    abstract fun bindSendScheduledMessageReceiver(): SendScheduledMessageReceiver

    @ActivityScope
    @ContributesAndroidInjector()
    abstract fun bindSmsDeliveredReceiver(): SmsDeliveredReceiver

    @ActivityScope
    @ContributesAndroidInjector()
    abstract fun bindSmsProviderChangedReceiver(): SmsProviderChangedReceiver

    @ActivityScope
    @ContributesAndroidInjector()
    abstract fun bindSmsReceiver(): SmsReceiver

    @ActivityScope
    @ContributesAndroidInjector()
    abstract fun bindSmsSentReceiver(): SmsSentReceiver

    @ActivityScope
    @ContributesAndroidInjector()
    abstract fun bindWidgetProvider(): WidgetProvider

}