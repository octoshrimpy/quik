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
package dev.octoshrimpy.quik.feature.compose

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import dev.octoshrimpy.quik.injection.ViewModelKey
import dev.octoshrimpy.quik.model.Attachment
import javax.inject.Named

@Module
class ComposeActivityModule {

    @Provides
    @Named("query")
    fun provideQuery(activity: ComposeActivity): String = activity.intent.extras?.getString("query") ?: ""

    @Provides
    @Named("threadId")
    fun provideThreadId(activity: ComposeActivity): Long = activity.intent.extras?.getLong("threadId") ?: 0L

    @Provides
    @Named("addresses")
    fun provideAddresses(activity: ComposeActivity): List<String> =
        if ((activity.intent?.data?.scheme == "sms") || (activity.intent?.data?.scheme == "smsto"))
            activity.intent?.data
                ?.schemeSpecificPart
                ?.removeSuffix("?${activity.intent?.data?.query}")
                ?.split(",", ";")
                ?.filter { it.isNotEmpty() }
                ?: listOf()
        else
            listOf()

    @Provides
    @Named("text")
    fun provideSharedText(activity: ComposeActivity): String {
        val retVal = StringBuilder()

        // from subject, if passed in intent
        retVal.append(activity.intent?.getStringExtra(Intent.EXTRA_SUBJECT) ?: "")
        if (retVal.isNotEmpty())
            retVal.append("\n")

        // from extra_text or sms_body extras, if passed in intent
        retVal.append(
            activity.intent?.extras?.getString(Intent.EXTRA_TEXT)
                ?: activity.intent?.extras?.getString("sms_body")
                ?: "")

        // from body param value(s) if intent data uri is like
        // sms:12345678?body=hello%20there&body=goodbye
        if ((activity.intent?.data?.scheme == "sms") || (activity.intent?.data?.scheme == "smsto"))
            retVal.append(
                activity.intent?.data?.query
                    ?.split("&")
                    ?.filter { it.startsWith("body=") }
                    ?.joinToString("\n") { it.removePrefix("body=") }
                    ?: ""
            )

        return retVal.toString()
    }

    @Provides
    @Named("attachments")
    fun provideSharedAttachments(activity: ComposeActivity): List<Attachment> {
        val uris = mutableListOf<Uri>()
        activity.intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.run(uris::add)
        activity.intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.run(uris::addAll)

        return uris.map { Attachment(activity, it) }
    }

    @Provides
    @Named("mode")
    fun provideSharedAction(activity: ComposeActivity): String =
        activity.intent.getStringExtra("mode") ?: ""

    @Provides
    @Named("subscriptionId")
    fun provideSubscriptionId(activity: ComposeActivity): Int =
        activity.intent.getIntExtra("subscriptionId", -1)

    @Provides
    @Named("sendAsGroup")
    fun provideSendAsGroup(activity: ComposeActivity): Boolean? =
        if (!activity.intent.hasExtra("sendAsGroup")) null
        else activity.intent.getBooleanExtra("sendAsGroup", false)

    @Provides
    @Named("scheduleDateTime")
    fun provideSharedScheduleDateTime(activity: ComposeActivity): Long =
        activity.intent.getLongExtra("scheduleDateTime", 0L)

    @Provides
    @IntoMap
    @ViewModelKey(ComposeViewModel::class)
    fun provideComposeViewModel(viewModel: ComposeViewModel): ViewModel = viewModel

}
