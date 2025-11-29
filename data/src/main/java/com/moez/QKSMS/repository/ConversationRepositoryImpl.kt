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
import dev.octoshrimpy.quik.extensions.removeAccents
import dev.octoshrimpy.quik.filter.ConversationFilter
import dev.octoshrimpy.quik.mapper.CursorToConversation
import dev.octoshrimpy.quik.mapper.CursorToRecipient
import dev.octoshrimpy.quik.model.Contact
import dev.octoshrimpy.quik.model.Conversation
import dev.octoshrimpy.quik.model.Message
import dev.octoshrimpy.quik.model.Recipient
import dev.octoshrimpy.quik.model.SearchResult
import dev.octoshrimpy.quik.model.PhoneNumber
import dev.octoshrimpy.quik.util.PhoneNumberUtils
import dev.octoshrimpy.quik.util.tryOrNull
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.realm.Case
import io.realm.Realm
import io.realm.RealmResults
import io.realm.RealmList
import timber.log.Timber
import io.realm.Sort
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import android.telephony.TelephonyManager
import android.telephony.SubscriptionManager
import android.os.Build
import android.telephony.PhoneNumberUtils as AndroidPhoneNumberUtils


class ConversationRepositoryImpl @Inject constructor(
    private val context: Context,
    private val conversationFilter: ConversationFilter,
    private val cursorToConversation: CursorToConversation,
    private val cursorToRecipient: CursorToRecipient,
    private val phoneNumberUtils: PhoneNumberUtils
) : ConversationRepository {

    @Suppress("Range")
    private fun resolvePhoneNumbersForRcsParticipants(
        context: Context,
        recipients: List<Recipient>
    ): List<String> {

        val resolver = context.contentResolver
        val resolvedNumbers = mutableListOf<String>()

        for (rcsRecipient in recipients) {
            val rawAddr = rcsRecipient.address ?: continue

            // If it already looks like a phone number, use it
            if (android.telephony.PhoneNumberUtils.isGlobalPhoneNumber(rawAddr)) {
                resolvedNumbers.add(rawAddr)
                continue
            }

            // Try to find phone numbers for contacts whose display name resembles this RCS handle
            val cursor = resolver.query(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%${rawAddr.take(10)}%"),
                null
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val number = it.getString(0)
                    if (!number.isNullOrBlank()) {
                        resolvedNumbers.add(number)
                    }
                }
            }
        }

        return resolvedNumbers.distinct()
    }


    override fun duplicateOrShadowConversation(
        addresses: List<String>,
        originalThreadId: Long?
    ): List<String> {

        val realm = Realm.getDefaultInstance()

        return try {
            // Make sure we see the latest Realm state
            realm.refresh()

            val candidateNumbers = linkedSetOf<String>() // keeps order, de-dups

            // 1) Any explicit addresses passed in from ComposeViewModel
            addresses.forEach { addr ->
                if (phoneNumberUtils.isPossibleNumber(addr)) {
                    candidateNumbers.add(addr)
                }
            }

            // 2) Look up the original conversation (if we know the thread id)
            val original = originalThreadId?.let { id ->
                realm.where(Conversation::class.java)
                    .equalTo("id", id)
                    .findFirst()
            }

            // 2a) recipients' own addresses
            original?.recipients?.forEach { rec ->
                val addr = rec.address
                if (phoneNumberUtils.isPossibleNumber(addr)) {
                    candidateNumbers.add(addr)
                }
            }

            // 2b) any phone numbers on the linked Contact
            original?.recipients?.forEach { rec ->
                rec.contact?.numbers?.forEach { pn ->
                    val pnAddr = pn.address
                    if (phoneNumberUtils.isPossibleNumber(pnAddr)) {
                        candidateNumbers.add(pnAddr)
                    }
                }
            }

            // 2c) fallback: look at Message.address for this thread
            if (candidateNumbers.isEmpty() && originalThreadId != null) {
                realm.where(Message::class.java)
                    .equalTo("threadId", originalThreadId)
                    .findAll()
                    ?.forEach { msg ->
                        val addr = msg.address
                        if (phoneNumberUtils.isPossibleNumber(addr)) {
                            candidateNumbers.add(addr)
                        }
                    }
            }

            // 2d) last resort: Contacts lookup by RCS handle
            if (candidateNumbers.isEmpty() && original != null) {
                candidateNumbers.addAll(
                    resolvePhoneNumbersForRcsParticipants(
                        context = context,
                        recipients = original.recipients
                    )
                )
            }
            // 🔹 Strip out any numbers that belong to this device (self),
            // so Telephony sees only "other people" as recipients.
            filterOutSelfNumbers(candidateNumbers)
        } finally {
            realm.close()

        }

    }

    /**
     * Try to discover all phone numbers that belong to *this* device (SIMs),
     * normalize them, and use that to filter "self" out of a recipients list.
     */
    private fun filterOutSelfNumbers(numbers: Set<String>): List<String> {
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        val selfRaw = mutableSetOf<String>()

        // Newer Android: get numbers from active subscriptions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val subMgr = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val subs = try { subMgr.activeSubscriptionInfoList } catch (_: SecurityException) { null }

            subs?.forEach { info ->
                info.number
                    ?.takeIf { it.isNotBlank() }
                    ?.let { selfRaw.add(it) }
            }
        }

        // Fallback: line1Number
        telephonyManager.line1Number
            ?.takeIf { it.isNotBlank() }
            ?.let { selfRaw.add(it) }

        if (selfRaw.isEmpty()) {
            // We don't know our own MSISDN -> just return original list
            Timber.w("filterOutSelfNumbers: no self numbers detected, leaving list as-is")
            return numbers.toList()
        }

        fun norm(s: String): String =
            AndroidPhoneNumberUtils.normalizeNumber(s) ?: s

        val selfNorm = selfRaw.map(::norm).toSet()

        val filtered = numbers.filter { norm(it) !in selfNorm }

        Timber.d(
            "filterOutSelfNumbers: in=%s self=%s out=%s",
            numbers,
            selfNorm,
            filtered
        )

        return filtered
    }


    /**
     * Pull all phone numbers we can from a Contact's RealmList<PhoneNumber>,
     * without relying on the exact field name.
     */
    private fun extractPhonesFromContact(contact: Contact?): List<String> {
        if (contact == null) return emptyList()

        val out = mutableListOf<String>()

        contact.javaClass.declaredFields.forEach { field ->
            if (RealmList::class.java.isAssignableFrom(field.type)) {
                field.isAccessible = true
                val value = field.get(contact)
                if (value is RealmList<*>) {
                    value.filterIsInstance<PhoneNumber>().forEach { phone ->
                        val addr = phone.address
                        if (!addr.isNullOrBlank()) {
                            out += addr
                        }
                    }
                }
            }
        }

        return out
    }

    /**
     * Very forgiving "does this look like a phone number?" check.
     */
    private fun isSmsLike(raw: String): Boolean {
        if (raw.isBlank()) return false

        // libphonenumber-style check via your PhoneNumberUtils
        if (phoneNumberUtils.isPossibleNumber(raw)) return true

        // Strip everything except + and digits and try again
        val normalized = raw.filter { it == '+' || it.isDigit() }
        return normalized.isNotBlank() && phoneNumberUtils.isPossibleNumber(normalized)
    }









    override fun getConversations(unreadAtTop: Boolean, archived: Boolean): RealmResults<Conversation> {
        val sortOrder: MutableList<String> = arrayListOf("pinned", "draft", "lastMessage.date")
        val sortDirections: MutableList<Sort> = arrayListOf(Sort.DESCENDING, Sort.DESCENDING, Sort.DESCENDING)

        if (unreadAtTop) {
            sortOrder.add(0, "lastMessage.read")
            sortDirections.add(0, Sort.ASCENDING)
        }

        return Realm.getDefaultInstance()
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
            .sort(
                sortOrder.toTypedArray(),
                sortDirections.toTypedArray()
            )
            .findAllAsync()
    }

    override fun getConversationsSnapshot(unreadAtTop: Boolean): List<Conversation> {
        val sortOrder: MutableList<String> = arrayListOf("pinned", "draft", "lastMessage.date")
        val sortDirections: MutableList<Sort> = arrayListOf(Sort.DESCENDING, Sort.DESCENDING, Sort.DESCENDING)

        if (unreadAtTop) {
            sortOrder.add(0, "lastMessage.read")
            sortDirections.add(0, Sort.ASCENDING)
        }

        return Realm.getDefaultInstance().use { realm ->
            realm.refresh()
            realm.where(Conversation::class.java)
                .notEqualTo("id", 0L)
                .equalTo("archived", false)
                .equalTo("blocked", false)
                .isNotEmpty("recipients")
                .beginGroup()
                .isNotNull("lastMessage")
                .or()
                .isNotEmpty("draft")
                .endGroup()
                .sort(sortOrder.toTypedArray(), sortDirections.toTypedArray())
                .findAll()
                .let(realm::copyFromRealm)
        }
    }

    override fun getTopConversations() =
        Realm.getDefaultInstance().use { realm ->
            realm.where(Conversation::class.java)
                .notEqualTo("id", 0L)
                .isNotNull("lastMessage")
                .beginGroup()
                .equalTo("pinned", true)
                .or()
                .greaterThan("lastMessage.date", System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7))
                .endGroup()
                .equalTo("archived", false)
                .equalTo("blocked", false)
                .isNotEmpty("recipients")
                .findAll()
                .let(realm::copyFromRealm)
                .sortedWith(compareByDescending<Conversation> { conversation -> conversation.pinned }
                    .thenByDescending { conversation ->
                        realm.where(Message::class.java)
                            .equalTo("threadId", conversation.id)
                            .greaterThan("date", System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7))
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

        val normalizedQuery = query.removeAccents()
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
            .contains("body", normalizedQuery, Case.INSENSITIVE)
            .or()
            .contains("parts.text", normalizedQuery, Case.INSENSITIVE)
            .endGroup()
            .findAll())
            .asSequence()
            .groupBy { message -> message.threadId }
            .filter { (threadId, _) -> conversations.firstOrNull { it.id == threadId } != null }
            .map { (threadId, messages) -> Pair(conversations.first { it.id == threadId }, messages.size) }
            .map { (conversation, messages) -> SearchResult(normalizedQuery, conversation, messages) }
            .sortedByDescending { result -> result.messages }
            .toList()

        realm.close()

        return conversations
            .filter { conversation -> conversationFilter.filter(conversation, normalizedQuery) }
            .map {
                conversation -> SearchResult(normalizedQuery, conversation, 0)
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
        Realm.getDefaultInstance()
            .apply { refresh() }
            .where(Conversation::class.java)
            .equalTo("id", threadId)
            .findFirst()

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

    override fun getConversation(recipient: String) =
        getConversation(listOf(recipient))

    override fun getConversation(recipients: Collection<String>): Conversation? =
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()
            realm.where(Conversation::class.java)
                .findAll()
                .asSequence()
                .filter { conversation -> conversation.recipients.size == recipients.size }
                .find { conversation ->
                    conversation.recipients.map { it.address }.all { address ->
                        recipients.any { recipient -> phoneNumberUtils.compare(recipient, address) }
                    }
                }
                ?.let { realm.copyFromRealm(it) }
        }

    override fun getOrCreateConversation(threadId: Long) =
        tryOrNull(true) {
            getConversation(threadId) ?: createConversationFromCp(threadId)
        }

    override fun getOrCreateConversation(address: String) =
        getOrCreateConversation(listOf(address))

    override fun getOrCreateConversation(addresses: Collection<String>) =
        tryOrNull(true) {
            getConversation(addresses)
                ?: tryOrNull { TelephonyCompat.getOrCreateThreadId(context, addresses.toSet()) }
                    ?.takeIf { it != 0L }
                    ?.let { threadId -> getOrCreateConversation(threadId) }
        }

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

    override fun getOrCreateThreadId(addresses: List<String>): Long {
        return TelephonyCompat.getOrCreateThreadId(context, addresses.toSet())
    }


    override fun updateConversations(vararg threadIds: Long) =
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()

            threadIds.forEach { threadId ->
                val conversation = realm
                    .where(Conversation::class.java)
                    .equalTo("id", threadId)
                    .findFirst() ?: return@forEach

                val message = realm
                    .where(Message::class.java)
                    .equalTo("threadId", threadId)
                    .sort("date", Sort.DESCENDING)
                    .findFirst()

                realm.executeTransaction { conversation.lastMessage = message }
            }
        }

    override fun markArchived(vararg threadIds: Long) =
        Realm.getDefaultInstance().use { realm ->
            val conversations = realm.where(Conversation::class.java)
                .anyOf("id", threadIds)
                .findAll()

            realm.executeTransaction { conversations.forEach { it.archived = true } }
        }

    override fun markUnarchived(vararg threadIds: Long) =
        Realm.getDefaultInstance().use { realm ->
            val conversations = realm.where(Conversation::class.java)
                .anyOf("id", threadIds)
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
    private fun createConversationFromCp(threadId: Long) =
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
                                }
                            }

                        conversation.apply {
                            recipients.clear()
                            recipients.addAll(matchedRecipients)
                            lastMessage = realm.where(Message::class.java)
                                .equalTo("threadId", threadId)
                                .sort("date", Sort.DESCENDING)
                                .findFirst()
                        }

                        realm.executeTransaction { it.insertOrUpdate(conversation) }
                    }
                }
        }
}
