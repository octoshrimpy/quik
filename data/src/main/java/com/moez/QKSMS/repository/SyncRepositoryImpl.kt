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

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.Telephony
import com.f2prateek.rx.preferences2.RxSharedPreferences
import dev.octoshrimpy.quik.extensions.forEach
import dev.octoshrimpy.quik.extensions.insertOrUpdate
import dev.octoshrimpy.quik.extensions.map
import dev.octoshrimpy.quik.manager.KeyManager
import dev.octoshrimpy.quik.mapper.CursorToContact
import dev.octoshrimpy.quik.mapper.CursorToContactGroup
import dev.octoshrimpy.quik.mapper.CursorToContactGroupMember
import dev.octoshrimpy.quik.mapper.CursorToConversation
import dev.octoshrimpy.quik.mapper.CursorToMessage
import dev.octoshrimpy.quik.mapper.CursorToPart
import dev.octoshrimpy.quik.mapper.CursorToRecipient
import dev.octoshrimpy.quik.model.Contact
import dev.octoshrimpy.quik.model.ContactGroup
import dev.octoshrimpy.quik.model.Conversation
import dev.octoshrimpy.quik.model.EmojiReaction
import dev.octoshrimpy.quik.model.Message
import dev.octoshrimpy.quik.model.MmsPart
import dev.octoshrimpy.quik.model.PhoneNumber
import dev.octoshrimpy.quik.model.Recipient
import dev.octoshrimpy.quik.model.SyncLog
import dev.octoshrimpy.quik.interactor.DeduplicateMessages
import dev.octoshrimpy.quik.util.PhoneNumberUtils
import dev.octoshrimpy.quik.util.tryOrNull
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import io.realm.Realm
import io.realm.RealmList
import io.realm.Sort
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val contentResolver: ContentResolver,
    private val conversationRepo: ConversationRepository,
    private val cursorToConversation: CursorToConversation,
    private val cursorToMessage: CursorToMessage,
    private val cursorToPart: CursorToPart,
    private val cursorToRecipient: CursorToRecipient,
    private val cursorToContact: CursorToContact,
    private val cursorToContactGroup: CursorToContactGroup,
    private val cursorToContactGroupMember: CursorToContactGroupMember,
    private val keys: KeyManager,
    private val phoneNumberUtils: PhoneNumberUtils,
    private val messageRepo: Provider<MessageRepository>,
    private val rxPrefs: RxSharedPreferences,
    private val reactions: EmojiReactionRepository,
) : SyncRepository {

    override val syncProgress: Subject<SyncRepository.SyncProgress> =
            BehaviorSubject.createDefault(SyncRepository.SyncProgress.Idle)

    override fun syncMessages() {

        val oldBlockedSenders = rxPrefs.getStringSet("pref_key_blocked_senders")

        // If the sync is already running, don't try to do another one
        if (syncProgress.blockingFirst() is SyncRepository.SyncProgress.Running) return
        syncProgress.onNext(SyncRepository.SyncProgress.Running(0, 0, true))

        val handlerThread = HandlerThread("RealmSyncThread")
        handlerThread.start()
        Handler(handlerThread.looper).post {
            Realm.getDefaultInstance().executeTransactionAsync(
                { realm ->
                // Prepare existing conversation data
                val persistedData = realm.copyFromRealm(
                    realm.where(Conversation::class.java)
                        .beginGroup()
                        .equalTo("archived", true)
                        .or()
                        .equalTo("blocked", true)
                        .or()
                        .equalTo("pinned", true)
                        .or()
                        .isNotEmpty("name")
                        .or()
                        .isNotNull("blockingClient")
                        .or()
                        .isNotEmpty("blockReason")
                        .endGroup()
                        .findAll()
                ).associateBy { conversation -> conversation.id }.toMutableMap()

                removeOldMessages(realm)

                keys.reset()

                val partsCursor = cursorToPart.getPartsCursor()
                val messageCursor = cursorToMessage.getMessagesCursor()
                val conversationCursor = cursorToConversation.getConversationsCursor()
                val recipientCursor = cursorToRecipient.getRecipientCursor()

                val max = (partsCursor?.count ?: 0) +
                        (messageCursor?.count ?: 0) +
                        (conversationCursor?.count ?: 0) +
                        (recipientCursor?.count ?: 0)

                var progress = 0

                // Sync message parts
                partsCursor?.use {
                    partsCursor.forEach { cursor ->
                        tryOrNull {
                            val part = cursorToPart.map(partsCursor)
                            realm.insertOrUpdate(part)
                            progress++
                        }
                    }
                }

                // Sync messages
                messageCursor?.use {
                    val messageColumns = CursorToMessage.MessageColumns(messageCursor)
                    messageCursor.forEach { cursor ->
                        tryOrNull {
                            syncProgress.onNext(
                                SyncRepository.SyncProgress.Running(
                                    max,
                                    ++progress,
                                    false
                                )
                            )
                            val message = cursorToMessage.map(Pair(cursor, messageColumns)).apply {
                                if (isMms()) {
                                    parts = RealmList<MmsPart>().apply {
                                        addAll(
                                            realm.where(MmsPart::class.java)
                                                .equalTo("messageId", contentId)
                                                .findAll()
                                        )
                                    }
                                }
                            }
                            realm.insertOrUpdate(message)
                        }
                    }
                }

                // Migrate blocked conversations from 2.7.3
                oldBlockedSenders.get()
                    .map { threadIdString -> threadIdString.toLong() }
                    .filter { threadId -> !persistedData.contains(threadId) }
                    .forEach { threadId ->
                        persistedData[threadId] = Conversation(id = threadId, blocked = true)
                    }

                // Sync conversations
                conversationCursor?.use {
                    conversationCursor.forEach { cursor ->
                        tryOrNull {
                            syncProgress.onNext(
                                SyncRepository.SyncProgress.Running(
                                    max,
                                    ++progress,
                                    false
                                )
                            )
                            val conversation = cursorToConversation.map(cursor).apply {
                                persistedData[id]?.let { persistedConversation ->
                                    archived = persistedConversation.archived
                                    blocked = persistedConversation.blocked
                                    pinned = persistedConversation.pinned
                                    name = persistedConversation.name
                                    blockingClient = persistedConversation.blockingClient
                                    blockReason = persistedConversation.blockReason
                                    sendAsGroup = persistedConversation.sendAsGroup
                                }
                                lastMessage = realm.where(Message::class.java)
                                    .sort("date", Sort.DESCENDING)
                                    .equalTo("threadId", id)
                                    .findFirst()
                            }
                            realm.insertOrUpdate(conversation)
                        }
                    }
                }

                // Sync recipients
                val contacts = realm.copyToRealmOrUpdate(getContacts())
                recipientCursor?.use {
                    recipientCursor.forEach { cursor ->
                        tryOrNull {
                            syncProgress.onNext(
                                SyncRepository.SyncProgress.Running(
                                    max,
                                    ++progress,
                                    false
                                )
                            )
                            val rec = cursorToRecipient.map(cursor).apply {
                                contact = contacts.firstOrNull { c ->
                                    c.numbers.any { num ->
                                        phoneNumberUtils.compare(
                                            address,
                                            num.address
                                        )
                                    }
                                }
                            }
                            realm.insertOrUpdate(rec)
                        }
                    }
                }

                syncProgress.onNext(SyncRepository.SyncProgress.ParsingEmojis(0, 0, true))

                // Now that we have all the messages, we can scan for emoji reactions
                reactions.deleteAndReparseAllEmojiReactions(
                    realm,
                    onProgress = { progress ->
                        syncProgress.onNext(progress)
                    })

                if (rxPrefs.getBoolean("autoDeduplicateMessages").get()) {
                    DeduplicateMessages(messageRepo.get())
                        .buildObservable(Unit)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe { result ->
                            when (result) {
                                is MessageRepository.DeduplicationResult.NoDuplicates -> {
                                    Timber.i("No duplicate messages found.")
                                }
                                is MessageRepository.DeduplicationResult.Success -> {
                                    Timber.i("Deleted duplicate messages")
                                }
                                is MessageRepository.DeduplicationResult.Failure -> {
                                    Timber.e(result.error, "Deduplication failed")
                                }
                            }
                        }
                }

                realm.insert(SyncLog())
            }, {
                handlerThread.quitSafely()
                oldBlockedSenders.delete()
                syncProgress.onNext(SyncRepository.SyncProgress.Idle)
            },
                { error ->
                    handlerThread.quitSafely()
                    Timber.e(error, "syncMessages Failed")
                    syncProgress.onNext(SyncRepository.SyncProgress.Idle)
                })
        }
    }

    override fun syncMessage(uri: Uri, messageId: Long): Message? {

        // If we don't have a valid type, return null
        val type = when {
            uri.toString().contains(Message.TYPE_MMS) -> Message.TYPE_MMS
            uri.toString().contains(Message.TYPE_SMS) -> Message.TYPE_SMS
            else -> return null
        }

        // If we don't have a valid id, return null
        val contentId = tryOrNull(false) { ContentUris.parseId(uri) } ?: return null

        // Check if the message already exists, so we can reuse the id
        val existingId = Realm.getDefaultInstance().use { realm ->
            realm.refresh()
            realm.where(Message::class.java)
                .equalTo("type", type)
                .equalTo("contentId", contentId)
                .or()
                .beginGroup()
                .equalTo("id", messageId)
                .and()
                .equalTo("contentId", 0L)
                .endGroup()
                .findFirst()
                ?.id
        }

        // The uri might be something like content://mms/inbox/id
        // The box might change though, so we should just use the mms/id uri
        val stableUri = when (type) {
            Message.TYPE_MMS -> ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, contentId)
            else -> ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, contentId)
        }

        return contentResolver.query(stableUri, null, null, null, null)?.use { cursor ->

            // If there are no rows, return null. Otherwise, we've moved to the first row
            if (!cursor.moveToFirst()) return null

            val columnsMap = CursorToMessage.MessageColumns(cursor)
            cursorToMessage.map(Pair(cursor, columnsMap)).apply {
                existingId?.let { this.id = it }

                if (isMms()) {
                    parts = RealmList<MmsPart>().apply {
                        addAll(cursorToPart.getPartsCursor(contentId)?.map { cursorToPart.map(it) }.orEmpty())
                    }
                }

                conversationRepo.getOrCreateConversation(threadId)
                insertOrUpdate()

                val text = getText(false)
                val parsedReaction = reactions.parseEmojiReaction(text)
                if (parsedReaction != null) {
                    Realm.getDefaultInstance().use { realm ->
                        val targetMessage = reactions.findTargetMessage(
                            threadId,
                            parsedReaction.originalMessage,
                            realm
                        )
                        realm.executeTransaction {
                            reactions.saveEmojiReaction(
                                this,
                                parsedReaction,
                                targetMessage,
                                realm,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun syncContacts() {
        // Load all the contacts
        var contacts = getContacts()

        Realm.getDefaultInstance()?.use { realm ->
            val recipients = realm.where(Recipient::class.java).findAll()

            realm.executeTransaction {
                realm.delete(Contact::class.java)
                realm.delete(ContactGroup::class.java)

                contacts = realm.copyToRealmOrUpdate(contacts)
                realm.insertOrUpdate(getContactGroups(contacts))

                // Update all the recipients with the new contacts
                recipients.forEach { recipient ->
                    recipient.contact = contacts.find { contact ->
                        contact.numbers.any { phoneNumberUtils.compare(recipient.address, it.address) }
                    }
                }

                realm.insertOrUpdate(recipients)
            }

        }
    }

    private fun getContacts(): List<Contact> {
        val defaultNumberIds = Realm.getDefaultInstance().use { realm ->
            realm.where(PhoneNumber::class.java)
                    .equalTo("isDefault", true)
                    .findAll()
                    .map { number -> number.id }
        }

        return cursorToContact.getContactsCursor()
                ?.map { cursor -> cursorToContact.map(cursor) }
                ?.groupBy { contact -> contact.lookupKey }
                ?.map { contacts ->
                    // Sometimes, contacts providers on the phone will create duplicate phone number entries. This
                    // commonly happens with Whatsapp. Let's try to detect these duplicate entries and filter them out
                    val uniqueNumbers = mutableListOf<PhoneNumber>()
                    contacts.value
                            .flatMap { it.numbers }
                            .forEach { number ->
                                number.isDefault = defaultNumberIds.any { id -> id == number.id }
                                val duplicate = uniqueNumbers.find { other ->
                                    phoneNumberUtils.compare(number.address, other.address)
                                }

                                if (duplicate == null) {
                                    uniqueNumbers += number
                                } else if (!duplicate.isDefault && number.isDefault) {
                                    duplicate.isDefault = true
                                }
                            }

                    contacts.value.first().apply {
                        numbers.clear()
                        numbers.addAll(uniqueNumbers)
                    }
                } ?: listOf()
    }

    private fun getContactGroups(contacts: List<Contact>): List<ContactGroup> {
        val groupMembers = cursorToContactGroupMember.getGroupMembersCursor()
                ?.map(cursorToContactGroupMember::map)
                .orEmpty()

        val groups = cursorToContactGroup.getContactGroupsCursor()
                ?.map(cursorToContactGroup::map)
                .orEmpty()

        groups.forEach { group ->
            group.contacts.addAll(groupMembers
                    .filter { member -> member.groupId == group.id }
                    .mapNotNull { member -> contacts.find { contact -> contact.lookupKey == member.lookupKey } })
        }

        return groups
    }

    private fun removeOldMessages(realm: Realm) {
        realm.delete(Contact::class.java)
        realm.delete(ContactGroup::class.java)
        realm.delete(Conversation::class.java)
        realm.delete(Message::class.java)
        realm.delete(MmsPart::class.java)
        realm.delete(Recipient::class.java)
    }

}
