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
package dev.octoshrimpy.quik.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.annotation.RequiresApi
import dagger.android.AndroidInjection
import dev.octoshrimpy.quik.interactor.SyncMessages
import dev.octoshrimpy.quik.util.Preferences
import javax.inject.Inject

class DefaultSmsChangedReceiver : BroadcastReceiver() {

    @Inject lateinit var prefs: Preferences
    @Inject lateinit var syncMessages: SyncMessages

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onReceive(context: Context, intent: Intent) {
        AndroidInjection.inject(this, context)

        if (intent.getBooleanExtra(Telephony.Sms.Intents.EXTRA_IS_DEFAULT_SMS_APP, false)) {
            val pendingResult = goAsync()
            syncMessages.execute(Unit) { pendingResult.finish() }
        }
    }

}