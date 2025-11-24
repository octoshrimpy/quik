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

    fun getUnseenIds(archived: Boolean = false): List<Long>

    fun getUnreadIds(archived: Boolean = false): List<Long>

    fun getConversationAndLastSenderContactName(threadId: Long): Pair<Conversation?, String?>?

    fun getConversations(vararg threadIds: Long): RealmResults<Conversation>

    fun getUnmanagedConversations(): Observable<List<Conversation>>

    fun getRecipients(): RealmResults<Recipient>

    fun getUnmanagedRecipients(): Observable<List<Recipient>>

    fun getRecipient(recipientId: Long): Recipient?

    fun getConversation(recipient: String): Conversation?

    fun getConversation(recipients: Collection<String>): Conversation?

    fun getOrCreateConversation(threadId: Long): Conversation?

    fun getOrCreateConversation(address: String): Conversation?

    fun getOrCreateConversation(addresses: Collection<String>): Conversation?

    fun saveDraft(threadId: Long, draft: String)

    fun updateConversations(vararg threadIds: Long)

    fun markArchived(vararg threadIds: Long)

    fun markUnarchived(vararg threadIds: Long)

    fun markPinned(vararg threadIds: Long)

    fun markUnpinned(vararg threadIds: Long)

    fun markBlocked(threadIds: Collection<Long>, blockingClient: Int, blockReason: String?)

    fun markUnblocked(vararg threadIds: Long)

    fun deleteConversations(vararg threadIds: Long)

    // NEW CLEAN METHOD — NO context reference here
    fun getOrCreateThreadId(addresses: List<String>): Long

    // 🆕 New helper for duplication / shadow RCS
    fun duplicateOrShadowConversation(addresses: List<String>, originalThreadId: Long?): Conversation
}
