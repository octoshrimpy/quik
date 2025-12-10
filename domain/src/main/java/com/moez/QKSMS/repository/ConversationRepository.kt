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
package dev.octoshrimpy.quik.repository

import dev.octoshrimpy.quik.model.Conversation
import dev.octoshrimpy.quik.model.Recipient
import dev.octoshrimpy.quik.model.SearchResult
import io.reactivex.Completable
import io.reactivex.Observable
import io.realm.RealmResults

interface ConversationRepository {

    fun getConversations(unreadAtTop: Boolean, archived: Boolean = false): RealmResults<Conversation>

    fun getConversationsSnapshot(unreadAtTop: Boolean): List<Conversation>

    fun getTopConversations(): List<Conversation>

    fun setConversationName(id: Long, name: String): Completable

    fun searchConversations(query: CharSequence): List<SearchResult>

    fun getBlockedConversations(): RealmResults<Conversation>

    fun getBlockedConversationsAsync(): RealmResults<Conversation>

    fun getConversationAsync(threadId: Long): Conversation

    fun getConversation(threadId: Long): Conversation?

    fun updateSendAsGroup(threadId: Long, sendAsGroup: Boolean): Unit?

    fun getUnseenIds(archived: Boolean = false): List<Long>

    fun getUnreadIds(archived: Boolean = false): List<Long>

    fun getConversationAndLastSenderContactName(threadId: Long): Pair<Conversation?, String?>?

    fun getConversations(vararg threadIds: Long): RealmResults<Conversation>

    fun getUnmanagedConversations(): Observable<List<Conversation>>

    fun getRecipients(): RealmResults<Recipient>

    fun getUnmanagedRecipients(): Observable<List<Recipient>>

    fun getRecipient(recipientId: Long): Recipient?

    fun getOrCreateConversation(threadId: Long, sendAsGroup: Boolean = true): Conversation?

    fun createConversation(threadId: Long, sendAsGroup: Boolean = true): Conversation?

    fun getConversation(addresses: Collection<String>): Conversation?

    fun getOrCreateConversation(
        addresses: Collection<String>, sendAsGroup: Boolean = true
    ): Conversation?

    fun createConversation(
        addresses: Collection<String>, sendAsGroup: Boolean = true
    ): Conversation?

    fun saveDraft(threadId: Long, draft: String)

    fun updateConversations(threadIds: Collection<Long>)

    fun markArchived(vararg threadIds: Long)

    fun markUnarchived(threadIds: Collection<Long>)

    fun markPinned(vararg threadIds: Long)

    fun markUnpinned(vararg threadIds: Long)

    fun markBlocked(threadIds: Collection<Long>, blockingClient: Int, blockReason: String?)

    fun markUnblocked(vararg threadIds: Long)

    fun deleteConversations(vararg threadIds: Long)

}
