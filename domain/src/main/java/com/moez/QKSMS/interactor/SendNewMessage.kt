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

import dev.octoshrimpy.quik.manager.ShortcutManager
import dev.octoshrimpy.quik.extensions.mapNotNull
import dev.octoshrimpy.quik.model.Attachment
import dev.octoshrimpy.quik.repository.ConversationRepository
import dev.octoshrimpy.quik.repository.MessageRepository
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import timber.log.Timber
import javax.inject.Inject

class SendNewMessage @Inject constructor(
    private val conversationRepo: ConversationRepository,
    private val messageRepo: MessageRepository,
    private val updateBadge: UpdateBadge,
    private val shortcutManager: ShortcutManager
) : Interactor<SendNewMessage.Params>() {

    data class Params(
        val subId: Int,
        val threadId: Long,
        val addresses: Collection<String>,
        val body: String,
        val sendAsGroup: Boolean,
        val attachments: Collection<Attachment> = listOf(),
        val delay: Int = 0
    )

    override fun buildObservable(params: Params): Flowable<*> = Flowable.just(Unit)
        .mapNotNull {
            // if addresses are provided, prefer them over the thread id because from a user
            // perspective it is more important that the intended recipients are messaged rather
            // than that messages go to a thread id
            when {
                params.addresses.isNotEmpty() ->
                    conversationRepo.getOrCreateConversation(params.addresses)

                (params.threadId > 0) ->
                    conversationRepo.getOrCreateConversation(params.threadId)

                else -> null
            }
            ?:let { Timber.e("unable to get or create a conversation record"); null }
        }
        .map { conversation ->
            // send the message
            messageRepo.sendNewMessages(params.subId, conversation.recipients.map { it.address },
                params.body, params.attachments, params.sendAsGroup, params.delay)
        }
        .map { messages -> messages.map { it.threadId } }
        .doOnNext { threadIds ->
            conversationRepo.updateConversations(threadIds)
            conversationRepo.markUnarchived(threadIds)

            AndroidSchedulers.mainThread().scheduleDirect {
                threadIds.forEach { shortcutManager.reportShortcutUsed(it) }
            }

            // delete attachment local files, if any, because they're saved to mms db by now
            params.attachments.forEach { it.removeCacheFile() }
        }
        .observeOn(AndroidSchedulers.mainThread())
        .flatMap { updateBadge.buildObservable(Unit) } // Update the widget

}