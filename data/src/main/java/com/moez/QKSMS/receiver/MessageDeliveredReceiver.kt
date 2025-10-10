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

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.android.AndroidInjection
import dev.octoshrimpy.quik.interactor.MarkDelivered
import dev.octoshrimpy.quik.interactor.MarkDeliveryFailed
import timber.log.Timber
import javax.inject.Inject

class MessageDeliveredReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_QUIK_MESSAGE_ID = "messageId"
    }

    @Inject lateinit var markDelivered: MarkDelivered
    @Inject lateinit var markDeliveryFailed: MarkDeliveryFailed

    override fun onReceive(context: Context, intent: Intent) {
        AndroidInjection.inject(this, context)

        Timber.v("received")

        intent.extras?.getLong(EXTRA_QUIK_MESSAGE_ID)?.takeIf { it > 0 }
            ?.let { messageId ->
                val pendingResult = goAsync()

                Timber.v("resultcode: ${pendingResult.resultCode}")

                when (pendingResult.resultCode) {
                    // TODO notify about delivery
                    Activity.RESULT_OK -> markDelivered.execute(messageId) { pendingResult.finish() }

                    // TODO notify about delivery failure
                    else ->
                        markDeliveryFailed.execute(MarkDeliveryFailed.Params(messageId, resultCode)) {
                            pendingResult.finish()
                        }
                }
            } ?: let { Timber.e("couldn't get message id") }
    }

}
