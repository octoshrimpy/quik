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

import android.content.Context
import dev.octoshrimpy.quik.manager.ShortcutManager
import dev.octoshrimpy.quik.model.Attachment
import dev.octoshrimpy.quik.repository.ConversationRepository
import dev.octoshrimpy.quik.repository.MessageRepository
import io.reactivex.Flowable
import javax.inject.Inject

class SendMessage @Inject constructor(
    private val context: Context,
    private val conversationRepo: ConversationRepository,
    private val messageRepo: MessageRepository,
    private val updateBadge: UpdateBadge,
    private val shortcutManager: ShortcutManager
) : Interactor<SendMessage.Params>() {

    data class Params(
        val subId: Int,
        val threadId: Long,
        val addresses: List<String>,
        val body: String,
        val attachments: List<Attachment> = listOf(),
        val delay: Int = 0
    )

    override fun buildObservable(params: Params): Flowable<*> =
        Flowable.just(Unit)
            .filter { params.addresses.isNotEmpty() }
            .doOnNext {
                // Resolve the effective threadId to send on.
                val effectiveThreadId = when (params.threadId) {
                    0L -> {
                        // New: use the clean threadId helper first
                        val rawThreadId = conversationRepo.getOrCreateThreadId(params.addresses)

                        if (rawThreadId != 0L) {
                            // Best-effort: make sure we have a local Conversation object in Realm
                            conversationRepo.getOrCreateConversation(rawThreadId)
                        }

                        rawThreadId
                    }

                    else -> params.threadId
                }

                // If we still couldn't get a valid threadId, bail out
                if (effectiveThreadId == 0L) {
                    return@doOnNext
                }

                // Actually send the message
                params.apply {
                    messageRepo.sendMessage(
                        subId,
                        effectiveThreadId,
                        addresses,
                        body,
                        attachments,
                        delay
                    )
                }

                // Update conversation metadata + unarchive, same as before
                conversationRepo.updateConversations(effectiveThreadId)
                conversationRepo.markUnarchived(effectiveThreadId)

                // Update shortcuts
                shortcutManager.reportShortcutUsed(effectiveThreadId)

                // Delete attachment local files, if any, because they're saved to MMS DB by now
                params.attachments.forEach { it.removeCacheFile() }
            }
            // Update the widget badge
            .flatMap { updateBadge.buildObservable(Unit) }
}
