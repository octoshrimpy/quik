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
import androidx.core.app.RemoteInput
import dagger.android.AndroidInjection
import dev.octoshrimpy.quik.compat.SubscriptionManagerCompat
import dev.octoshrimpy.quik.interactor.MarkRead
import dev.octoshrimpy.quik.interactor.SendNewMessage
import dev.octoshrimpy.quik.repository.ConversationRepository
import dev.octoshrimpy.quik.repository.MessageRepository
import javax.inject.Inject

class RemoteMessagingReceiver : BroadcastReceiver() {
    @Inject lateinit var conversationRepo: ConversationRepository
    @Inject lateinit var markRead: MarkRead
    @Inject lateinit var messageRepo: MessageRepository
    @Inject lateinit var sendNewMessage: SendNewMessage
    @Inject lateinit var subscriptionManager: SubscriptionManagerCompat

    override fun onReceive(context: Context, intent: Intent) {
        AndroidInjection.inject(this, context)

        val remoteInput = RemoteInput.getResultsFromIntent(intent) ?: return
        val bundle = intent.extras ?: return

        val threadId = bundle.getLong("threadId")

        markRead.execute(listOf(threadId))

        val lastMessage = messageRepo.getMessages(threadId).lastOrNull()
        val conversation = conversationRepo.getConversation(threadId)

        val pendingRepository = goAsync()
        sendNewMessage.execute(
            SendNewMessage.Params(
                subscriptionManager.activeSubscriptionInfoList
                    .firstOrNull { it.subscriptionId == lastMessage?.subId }
                    ?.subscriptionId ?: -1,
                0,
                conversation?.recipients?.map { it.address } ?: return,
                remoteInput.getCharSequence("body").toString(),
                conversation.sendAsGroup
            )
        ) { pendingRepository.finish() }
    }
}
