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

import dev.octoshrimpy.quik.blocking.BlockingClient
import dev.octoshrimpy.quik.extensions.mapNotNull
import dev.octoshrimpy.quik.manager.NotificationManager
import dev.octoshrimpy.quik.manager.ShortcutManager
import dev.octoshrimpy.quik.repository.ContactRepository
import dev.octoshrimpy.quik.repository.ConversationRepository
import dev.octoshrimpy.quik.repository.MessageContentFilterRepository
import dev.octoshrimpy.quik.repository.MessageRepository
import dev.octoshrimpy.quik.util.Preferences
import io.reactivex.Flowable
import timber.log.Timber
import javax.inject.Inject

class ReceiveSms @Inject constructor(
    private val conversationRepo: ConversationRepository,
    private val blockingClient: BlockingClient,
    private val prefs: Preferences,
    private val messageRepo: MessageRepository,
    private val notificationManager: NotificationManager,
    private val updateBadge: UpdateBadge,
    private val shortcutManager: ShortcutManager,
    private val filterRepo: MessageContentFilterRepository,
    private val contactsRepo: ContactRepository
) : Interactor<Long>() {

    override fun buildObservable(params: Long): Flowable<*> {
        return Flowable.just(params)
            .mapNotNull { messageRepo.getMessage(it) }
            .mapNotNull {
                val action = blockingClient.shouldBlock(it.address).blockingGet()

                when {
                    ((action is BlockingClient.Action.Block) && prefs.drop.get()) ->  {
                        // blocked and 'drop blocked.' remove from db and don't continue
                        Timber.v("address is blocked and drop blocked is on. dropped")
                        messageRepo.deleteMessages(listOf(it.id))
                        return@mapNotNull null
                    }
                    action is BlockingClient.Action.Block -> {
                        // blocked
                        Timber.v("address is blocked")
                        messageRepo.markRead(listOf(it.threadId))
                        conversationRepo.markBlocked(
                            listOf(it.threadId),
                            prefs.blockingManager.get(),
                            action.reason
                        )
                    }
                    action is BlockingClient.Action.Unblock -> {
                        // unblock
                        Timber.v("unblock conversation if blocked")
                        conversationRepo.markUnblocked(it.threadId)
                    }
                }

                if (filterRepo.isBlocked(it.getText(), it.address, contactsRepo)) {
                    Timber.v("message dropped based on content filters")
                    messageRepo.deleteMessages(listOf(it.id))
                    return@mapNotNull null
                }

                // update and fetch conversation
                conversationRepo.updateConversations(it.threadId)
                conversationRepo.getOrCreateConversation(it.threadId)
            }
            .mapNotNull {
                // don't notify (continue) for blocked conversations
                if (it.blocked) {
                    Timber.v("no notifications for blocked")
                    return@mapNotNull null
                }

                // unarchive conversation if necessary
                if (it.archived) {
                    Timber.v("conversation unarchived")
                    conversationRepo.markUnarchived(it.id)
                }

                // update/create notification
                Timber.v("update/create notification")
                notificationManager.update(it.id)

                // update shortcuts
                Timber.v("update shortcuts")
                shortcutManager.updateShortcuts()
                shortcutManager.reportShortcutUsed(it.id)

                // update the badge and widget
                Timber.v("update badge and widget")
                updateBadge.buildObservable(Unit)
            }
    }

}
