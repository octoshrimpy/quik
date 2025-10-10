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
package dev.octoshrimpy.quik.service

import android.app.IntentService
import android.content.Intent
import android.net.Uri
import android.telephony.TelephonyManager
import dagger.android.AndroidInjection
import dev.octoshrimpy.quik.interactor.SendNewMessage
import dev.octoshrimpy.quik.repository.ConversationRepository
import javax.inject.Inject

class HeadlessSmsSendService : IntentService("HeadlessSmsSendService") {
    @Inject lateinit var conversationRepo: ConversationRepository
    @Inject lateinit var sendNewMessage: SendNewMessage

    override fun onHandleIntent(intent: Intent?) {
        if (intent?.action != TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) return

        AndroidInjection.inject(this)
        intent.extras?.getString(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { body ->
            val recipients = intent.data?.let(::getRecipients)?.split(";") ?: return@let

            val conversation = conversationRepo.getOrCreateConversation(recipients)

            sendNewMessage.execute(SendNewMessage.Params(
                -1, 0, recipients, body, conversation?.sendAsGroup ?: false
            ))
        }
    }

    private fun getRecipients(uri: Uri): String {
        val base = uri.schemeSpecificPart
        val position = base.indexOf('?')
        return if (position == -1) base else base.substring(0, position)
    }

}