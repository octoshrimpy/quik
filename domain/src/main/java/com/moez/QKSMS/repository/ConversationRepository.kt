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

    /**
     * Convenience helper to get or create a Telephony thread id for a set of
     * addresses, without leaking `Context` out of the data layer.
     */
    fun getOrCreateThreadId(addresses: List<String>): Long

    /**
     * Used when the user chooses to "duplicate" an RCS group as an SMS/MMS group.
     *
     * Given the raw addresses from the UI and (optionally) the original RCS
     * thread id, this method should:
     *
     *  - Resolve / normalize / deduplicate SMS-capable phone numbers.
     *  - Optionally persist any metadata about the relationship to the
     *    original thread.
     *
     * Returns the final list of SMS-ready addresses to use for Telephony.
     */
    fun duplicateOrShadowConversation(
        addresses: List<String>,
        originalThreadId: Long?
    ): List<String>

    /**
     * Ensures that the specified thread behaves like a *group MMS* thread
     * rather than a mass-SMS fanout. Implementation will typically touch
     * the Telephony provider's MMS/SMS conversation rows.
     */
    fun ensureMmsConversation(threadId: Long, addresses: List<String>)

    /**
     * Persist a mapping from an RCS thread id to its SMS/MMS "shadow" thread id,
     * so we can later recognize that an SMS group is a duplicate of an RCS group.
     */
    fun saveShadowLink(
        rcsThreadId: Long,
        smsThreadId: Long
    )

    /**
     * Optional helper to look up an existing SMS/MMS shadow thread for
     * a given RCS thread, if it was previously created.
     */
    fun findShadowSmsThread(rcsThreadId: Long): Long?
}
