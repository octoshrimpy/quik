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
package dev.octoshrimpy.quik.interactor

import dev.octoshrimpy.quik.model.Message
import dev.octoshrimpy.quik.repository.ConversationRepository
import dev.octoshrimpy.quik.repository.MessageRepository
import io.reactivex.Flowable
import timber.log.Timber
import javax.inject.Inject

class ActionDelayedMessage @Inject constructor(
    private val conversationRepo: ConversationRepository,
    private val messageRepo: MessageRepository
) : Interactor<ActionDelayedMessage.Params>() {

    enum class Action {
        Send,
        Cancel
    }

    data class Params(val messageId: Long, val action: Action)

    private fun isMessageATemplate(message: Message, moreThanOneRecipient: Boolean) =
        (((message.isSms() && moreThanOneRecipient)) ||
                (message.isMms() && !message.sendAsGroup && moreThanOneRecipient))

    override fun buildObservable(params: Params): Flowable<*> =
        Flowable.just(params).doOnNext {
            val updateConversations = mutableListOf<Long>()

            // cancel the timer before doing anything else
            messageRepo.cancelDelayedSmsAlarm(params.messageId)

            messageRepo.getUnmanagedMessage(params.messageId)?.let { message ->
                when (params.action) {
                    Action.Cancel -> {
                        messageRepo.deleteMessages(listOf(message.id))
                        updateConversations.add(message.threadId)
                    }

                    Action.Send -> {
                        conversationRepo.getOrCreateConversation(message.threadId)
                            ?.let { conversation ->
                                // if message is a 'template' that needs re-sending as many messages
                                if (isMessageATemplate(message, (conversation.recipients.size > 1))) {
                                    updateConversations.addAll(
                                        messageRepo.sendNewMessagesFromTemplate(
                                            message,
                                            conversation.recipients.map { it.address },
                                            0  // send already delayed so never delay here
                                        )
                                            .map { message -> message.threadId }
                                    )

                                    //  mark the template message as sent
                                    messageRepo.markSent(message.id)
                                }
                                else {
                                    // not a template message, send it
                                    messageRepo.sendMessage(
                                        message, conversation.recipients.map { it.address }
                                    )
                                    updateConversations.add(message.threadId)
                                }
                            } ?:let { Timber.e("couldn't find or create conversation") }
                    }
                }

                conversationRepo.updateConversations(updateConversations)
            } ?:let { Timber.e("couldn't find or create message id ${params.messageId}") }
        }

}
