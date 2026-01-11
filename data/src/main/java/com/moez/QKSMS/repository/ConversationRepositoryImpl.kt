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

import android.content.ContentUris
import android.content.Context
import dev.octoshrimpy.quik.compat.TelephonyCompat
import dev.octoshrimpy.quik.extensions.anyOf
import dev.octoshrimpy.quik.extensions.asObservable
import dev.octoshrimpy.quik.extensions.map
import dev.octoshrimpy.quik.filter.ConversationFilter
import dev.octoshrimpy.quik.mapper.CursorToConversation
import dev.octoshrimpy.quik.mapper.CursorToRecipient
import dev.octoshrimpy.quik.model.Contact
import dev.octoshrimpy.quik.model.Conversation
import dev.octoshrimpy.quik.model.Message
import dev.octoshrimpy.quik.model.Recipient
import dev.octoshrimpy.quik.model.SearchResult
import dev.octoshrimpy.quik.util.PhoneNumberUtils
import dev.octoshrimpy.quik.util.tryOrNull
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.realm.Case
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import io.realm.Sort
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ConversationRepositoryImpl @Inject constructor(
    private val context: Context,
    private val conversationFilter: ConversationFilter,
    private val cursorToConversation: CursorToConversation,
    private val cursorToRecipient: CursorToRecipient,
    private val phoneNumberUtils: PhoneNumberUtils
) : ConversationRepository {
    private fun getConversationsBase(
        realm: Realm,
        unreadAtTop: Boolean,
        archived: Boolean
    ): RealmQuery<Conversation> {
        val sortOrder = mutableListOf("pinned", "draft", "lastMessage.date")
        val sortDirections = mutableListOf(Sort.DESCENDING, Sort.DESCENDING, Sort.DESCENDING)

        if (unreadAtTop) {
            sortOrder.add(0, "lastMessage.read")
            sortDirections.add(0, Sort.ASCENDING)
        }

        return realm
            .where(Conversation::class.java)
            .notEqualTo("id", 0L)
            .equalTo("archived", archived)
            .equalTo("blocked", false)
            .isNotEmpty("recipients")
            .beginGroup()
            .isNotNull("lastMessage")
            .or()
            .isNotEmpty("draft")
            .endGroup()
            .sort(sortOrder.toTypedArray(), sortDirections.toTypedArray())
    }

    override fun getConversations(
        unreadAtTop: Boolean,
        archived: Boolean
    ): RealmResults<Conversation> =
        getConversationsBase(Realm.getDefaultInstance(), unreadAtTop, archived)
            .findAllAsync()

    override fun getConversationsSnapshot(unreadAtTop: Boolean): List<Conversation> =
        Realm.getDefaultInstance().use { realm ->
            getConversationsBase(realm, unreadAtTop, false)
                .findAll()
                .let(realm::copyFromRealm)
        }

    override fun getTopConversations() =
        Realm.getDefaultInstance().use { realm ->
            realm.where(Conversation::class.java)
                .notEqualTo("id", 0L)
                .isNotNull("lastMessage")
                .beginGroup()
                .equalTo("pinned", true)
                .or()
                .greaterThan(
                    "lastMessage.date",
                    System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
                )
                .endGroup()
                .equalTo("archived", false)
                .equalTo("blocked", false)
                .isNotEmpty("recipients")
                .findAll()
                .let(realm::copyFromRealm)
                .sortedWith(compareByDescending<Conversation> {
                        conversation -> conversation.pinned
                }
                    .thenByDescending { conversation ->
                        realm.where(Message::class.java)
                            .equalTo("threadId", conversation.id)
                            .greaterThan(
                                "date",
                                System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
                            )
                            .count()
                    }
                )
        }

    override fun setConversationName(id: Long, name: String) =
        Completable.fromAction {
            Realm.getDefaultInstance().use { realm ->
                realm.executeTransaction {
                    realm.where(Conversation::class.java)
                        .equalTo("id", id)
                        .findFirst()
                        ?.name = name
                }
            }
        }.subscribeOn(Schedulers.io()) // Ensure the operation is performed on a background thread

    override fun searchConversations(query: CharSequence): List<SearchResult> {
        val realm = Realm.getDefaultInstance()

        val searchQuery = query.toString()
        val conversations = realm.copyFromRealm(realm
            .where(Conversation::class.java)
            .notEqualTo("id", 0L)
            .isNotNull("lastMessage")
            .equalTo("blocked", false)
            .isNotEmpty("recipients")
            .sort("pinned", Sort.DESCENDING, "lastMessage.date", Sort.DESCENDING)
            .findAll())

        val messagesByConversation = realm.copyFromRealm(realm
            .where(Message::class.java)
            .beginGroup()
            .contains("body", searchQuery, Case.INSENSITIVE)
            .or()
            .contains("parts.text", searchQuery, Case.INSENSITIVE)
            .endGroup()
            .findAll())
            .groupBy { message -> message.threadId }
            .filter { (threadId, _) -> conversations.firstOrNull { it.id == threadId } != null }
            .map { (threadId, messages) -> Pair(conversations.first { it.id == threadId }, messages.size) }
            .map { (conversation, messages) -> SearchResult(searchQuery, conversation, messages) }
            .sortedByDescending { result -> result.messages }
            .toList()

        realm.close()

        return conversations
            .filter { conversation -> conversationFilter.filter(conversation, searchQuery) }
            .map {
                    conversation -> SearchResult(searchQuery, conversation, 0)
            } + messagesByConversation
    }

    override fun getBlockedConversations(): RealmResults<Conversation> =
        Realm.getDefaultInstance()
            .where(Conversation::class.java)
            .equalTo("blocked", true)
            .sort(
                arrayOf("lastMessage.date"),
                arrayOf(Sort.DESCENDING)
            )
            .findAll()

    override fun getBlockedConversationsAsync(): RealmResults<Conversation> =
        Realm.getDefaultInstance()
            .where(Conversation::class.java)
            .equalTo("blocked", true)
            .sort(
                arrayOf("lastMessage.date"),
                arrayOf(Sort.DESCENDING)
            )
            .findAllAsync()

    override fun getConversationAsync(threadId: Long): Conversation =
        Realm.getDefaultInstance()
            .where(Conversation::class.java)
            .equalTo("id", threadId)
            .findFirstAsync()

    override fun getConversation(threadId: Long) =
        tryOrNull(true) {
            Realm.getDefaultInstance()
                .apply { refresh() }
                .where(Conversation::class.java)
                .equalTo("id", threadId)
                .findFirst()
        }

    override fun updateSendAsGroup(threadId: Long, sendAsGroup: Boolean) =
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()

            realm.where(Conversation::class.java)
                .equalTo("id", threadId)
                .findFirst()
                ?.let { conversation ->
                    realm.executeTransaction { conversation.sendAsGroup = sendAsGroup }
                }
        }

    override fun getUnseenIds(archived: Boolean) =
        ArrayList<Long>().apply {
            Realm.getDefaultInstance()
                .where(Conversation::class.java)
                .notEqualTo("id", 0L)
                .equalTo("archived", archived)
                .equalTo("blocked", false)
                .equalTo("lastMessage.seen", false)
                .sort(
                    arrayOf("lastMessage.date"),
                    arrayOf(Sort.DESCENDING)
                )
                .findAllAsync()
                .forEach { conversation -> add(conversation.id) }
        }


    override fun getUnreadIds(archived: Boolean) =
        ArrayList<Long>().apply {
            Realm.getDefaultInstance()
                .where(Conversation::class.java)
                .notEqualTo("id", 0L)
                .equalTo("archived", archived)
                .equalTo("blocked", false)
                .equalTo("lastMessage.read", false)
                .sort(
                    arrayOf("lastMessage.date"),
                    arrayOf(Sort.DESCENDING)
                )
                .findAllAsync()
                .forEach { conversation -> add(conversation.id) }
        }

    override fun getConversationAndLastSenderContactName(threadId: Long): Pair<Conversation?, String?>? =
        Realm.getDefaultInstance()
            .apply { refresh() }
            .where(Conversation::class.java)
            .equalTo("id", threadId)
            .findFirst()
            ?.let { conversation ->
                val conversationLastSmsSender: String? = conversation.recipients.find { recipient ->
                    phoneNumberUtils.compare(recipient.address, conversation.lastMessage!!.address)
                }?.contact?.name

                Pair(conversation, conversationLastSmsSender)
            }

    override fun getConversations(vararg threadIds: Long): RealmResults<Conversation> =
        Realm.getDefaultInstance()
            .where(Conversation::class.java)
            .anyOf("id", threadIds)
            .findAll()

    override fun getUnmanagedConversations(): Observable<List<Conversation>> =
        Realm.getDefaultInstance().let { realm->
            realm.where(Conversation::class.java)
                .sort("lastMessage.date", Sort.DESCENDING)
                .notEqualTo("id", 0L)
                .isNotNull("lastMessage")
                .equalTo("archived", false)
                .equalTo("blocked", false)
                .isNotEmpty("recipients")
                .limit(5)
                .findAllAsync()
                .asObservable()
                .filter { it.isLoaded }
                .filter { it.isValid }
                .map { realm.copyFromRealm(it) }
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(Schedulers.io())
        }

    override fun getRecipients(): RealmResults<Recipient> =
        Realm.getDefaultInstance()
            .where(Recipient::class.java)
            .findAll()

    override fun getUnmanagedRecipients(): Observable<List<Recipient>> =
        Realm.getDefaultInstance().let { realm ->
            realm.where(Recipient::class.java)
                .isNotNull("contact")
                .findAllAsync()
                .asObservable()
                .filter { it.isLoaded && it.isValid }
                .map { realm.copyFromRealm(it) }
                .subscribeOn(AndroidSchedulers.mainThread())
        }

    override fun getRecipient(recipientId: Long): Recipient? =
        Realm.getDefaultInstance()
            .where(Recipient::class.java)
            .equalTo("id", recipientId)
            .findFirst()

    override fun createConversation(threadId: Long, sendAsGroup: Boolean) =
        createConversationFromCp(threadId, sendAsGroup)


    override fun getConversation(recipients: Collection<String>): Conversation? =
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()
            realm.where(Conversation::class.java)
                .findAll()
                .filter { conversation -> conversation.recipients.size == recipients.size }
                .find { conversation ->
                    conversation.recipients.map { it.address }.all { recipientAddress ->
                        recipients.any { phoneNumberUtils.compare(it, recipientAddress) }
                    }
                }
                ?.let { realm.copyFromRealm(it) }
        }

    override fun createConversation(addresses: Collection<String>, sendAsGroup: Boolean) =
        TelephonyCompat.getOrCreateThreadId(context, addresses.toSet())
            .takeIf { it != 0L }
            ?.let { providerThreadId ->
                createConversationFromCp(providerThreadId, sendAsGroup) ?:
                    createEmptyConversation(providerThreadId, addresses, sendAsGroup)
            }

    override fun getOrCreateConversation(threadId: Long, sendAsGroup: Boolean) =
        getConversation(threadId) ?: createConversation(threadId, sendAsGroup)

    override fun getOrCreateConversation(addresses: Collection<String>, sendAsGroup: Boolean) =
        getConversation(addresses) ?: createConversation(addresses, sendAsGroup)

    override fun saveDraft(threadId: Long, draft: String) =
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()

            val conversation = realm.where(Conversation::class.java)
                .equalTo("id", threadId)
                .findFirst()

            realm.executeTransaction {
                conversation?.takeIf { it.isValid }?.draft = draft
                conversation?.takeIf { it.isValid }?.draftDate = System.currentTimeMillis()
            }
        }

    override fun updateConversations(threadIds: Collection<Long>) =
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()

            realm.where(Conversation::class.java)
                .anyOf("id", threadIds.toLongArray())
                .findAll()
                ?.map { conversation ->
                    Pair(
                        conversation,
                        realm.where(Message::class.java)
                            .equalTo("threadId", conversation.id)
                            .sort("date", Sort.DESCENDING)
                            .findFirst()
                    )
                }
                ?.let { conversationAndMessages ->
                    realm.executeTransaction {
                        conversationAndMessages.forEach { (conversation, message) ->
                            conversation.lastMessage = message
                        }
                    }
                }

            Unit
        }

    override fun markArchived(vararg threadIds: Long) =
        Realm.getDefaultInstance().use { realm ->
            val conversations = realm.where(Conversation::class.java)
                .anyOf("id", threadIds)
                .findAll()

            realm.executeTransaction { conversations.forEach { it.archived = true } }
        }

    override fun markUnarchived(threadIds: Collection<Long>) =
        Realm.getDefaultInstance().use { realm ->
            val conversations = realm.where(Conversation::class.java)
                .anyOf("id", threadIds.toLongArray())
                .findAll()

            realm.executeTransaction { conversations.forEach { it.archived = false } }
        }

    override fun markPinned(vararg threadIds: Long) =
        Realm.getDefaultInstance().use { realm ->
            val conversations = realm.where(Conversation::class.java)
                .anyOf("id", threadIds)
                .findAll()

            realm.executeTransaction { conversations.forEach { it.pinned = true } }
        }

    override fun markUnpinned(vararg threadIds: Long) =
        Realm.getDefaultInstance().use { realm ->
            val conversations = realm.where(Conversation::class.java)
                .anyOf("id", threadIds)
                .findAll()

            realm.executeTransaction { conversations.forEach { it.pinned = false } }
        }

    override fun markBlocked(threadIds: Collection<Long>, blockingClient: Int, blockReason: String?) =
        Realm.getDefaultInstance().use { realm ->
            val conversations = realm.where(Conversation::class.java)
                .anyOf("id", threadIds.toLongArray())
                .equalTo("blocked", false)
                .findAll()

            realm.executeTransaction {
                conversations.forEach { conversation ->
                    conversation.blocked = true
                    conversation.blockingClient = blockingClient
                    conversation.blockReason = blockReason
                }
            }
        }

    override fun markUnblocked(vararg threadIds: Long) =
        Realm.getDefaultInstance().use { realm ->
            val conversations = realm.where(Conversation::class.java)
                .anyOf("id", threadIds)
                .findAll()

            realm.executeTransaction {
                conversations.forEach { conversation ->
                    conversation.blocked = false
                    conversation.blockingClient = null
                    conversation.blockReason = null
                }
            }
        }

    override fun deleteConversations(vararg threadIds: Long) {
        Realm.getDefaultInstance().use { realm ->
            val conversation = realm.where(Conversation::class.java)
                .anyOf("id", threadIds)
                .findAll()
            val messages = realm.where(Message::class.java)
                .anyOf("threadId", threadIds)
                .findAll()

            realm.executeTransaction {
                conversation.deleteAllFromRealm()
                messages.deleteAllFromRealm()
            }
        }

        threadIds.forEach {
            context.contentResolver.delete(
                ContentUris.withAppendedId(TelephonyCompat.THREADS_CONTENT_URI, it),
                null,
                null
            )
        }
    }

    /**
     * Returns a [Conversation] from the system SMS ContentProvider, based on the [threadId]
     *
     * It should be noted that even if we have a valid [threadId], that does not guarantee that
     * we can return a [Conversation]. On some devices, the ContentProvider won't return the
     * conversation unless it contains at least 1 message
     */
    private fun createConversationFromCp(threadId: Long, sendAsGroup: Boolean) =
        tryOrNull(true) {
            cursorToConversation.getConversationsCursor()
                ?.map(cursorToConversation::map)
                ?.firstOrNull { conversation -> conversation.id == threadId }
                ?.also { conversation ->
                    Realm.getDefaultInstance().use { realm ->
                        val realmContacts = realm.where(Contact::class.java).findAll()

                        // match recipients from provider to recipients in realm
                        val matchedRecipients = conversation.recipients
                            .mapNotNull { recipient ->
                                // map the recipient cursor to a list of recipients
                                cursorToRecipient.getRecipientCursor(recipient.id)?.use { cursor ->
                                    cursor.map { cursorToRecipient.map(it) }
                                }
                            }
                            .flatten()
                            .map { recipient ->
                                recipient.apply {
                                    contact = realmContacts.firstOrNull { realmContact ->
                                        realmContact.numbers.any {
                                            phoneNumberUtils.compare(it.address, address)
                                        }
                                    }
                                    ?.let { realm.copyFromRealm(it) }
                                }
                            }

                        conversation.apply {
                            recipients.clear()
                            recipients.addAll(matchedRecipients)

                            this.sendAsGroup =
                                if (recipients.size <= 1) false
                                else sendAsGroup

                            lastMessage = realm.where(Message::class.java)
                                .equalTo("threadId", threadId)
                                .sort("date", Sort.DESCENDING)
                                .findFirst()
                        }

                        realm.executeTransaction { it.insertOrUpdate(conversation) }
                    }
                }
        }

    /**
     * In some cases [createConversationFromCp] will return null if there are no messages present in the convo.
     * In order to allow the conversation to be accessed
     * we need to create an empty conversation in Realm to match the conversation created in the content provider.
     *
     * This is a bit of a hack, but is necessary on devices running HyperOS or variants.
     */
    private fun createEmptyConversation(threadId: Long, addresses: Collection<String>, sendAsGroup: Boolean): Conversation {
        Realm.getDefaultInstance().use { realm ->
            val realmContacts = realm.where(Contact::class.java).findAll()
            val matchedRecipients = addresses.map { address ->
                Recipient().apply {
                    this.address = address
                    contact = realmContacts.firstOrNull { realmContact ->
                            realmContact.numbers.any {
                                phoneNumberUtils.compare(it.address, address)
                            }
                        }
                        ?.let { realm.copyFromRealm(it) }
                }
            }
            val conversation = Conversation().apply {
                id = threadId
                recipients.clear()
                recipients.addAll(matchedRecipients)
                this.sendAsGroup =
                    if (recipients.size <= 1) false
                    else sendAsGroup
            }
            realm.executeTransaction { it.copyToRealmOrUpdate(conversation) }
            return conversation
        }
    }

}
