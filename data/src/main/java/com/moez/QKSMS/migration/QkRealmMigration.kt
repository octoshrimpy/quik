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
package dev.octoshrimpy.quik.migration

import android.annotation.SuppressLint
import dev.octoshrimpy.quik.extensions.map
import dev.octoshrimpy.quik.mapper.CursorToContactImpl
import dev.octoshrimpy.quik.util.Preferences
import io.realm.DynamicRealm
import io.realm.DynamicRealmObject
import io.realm.FieldAttribute
import io.realm.RealmList
import io.realm.RealmMigration
import io.realm.Sort
import timber.log.Timber
import javax.inject.Inject

class QkRealmMigration @Inject constructor(
    private val cursorToContact: CursorToContactImpl,
    private val prefs: Preferences
) : RealmMigration {

    companion object {
        const val SCHEMA_VERSION: Long = 15
    }

    @SuppressLint("ApplySharedPref")
    override fun migrate(realm: DynamicRealm, oldVersion: Long, newVersion: Long) {
        var version = oldVersion

        if (version == 0L) {
            realm.schema.get("MmsPart")
                ?.removeField("image")

            version++
        }

        if (version == 1L) {
            realm.schema.get("Message")
                ?.addField("subId", Int::class.java)

            version++
        }

        if (version == 2L) {
            realm.schema.get("Conversation")
                ?.addField("name", String::class.java, FieldAttribute.REQUIRED)

            version++
        }

        if (version == 3L) {
            realm.schema.create("ScheduledMessage")
                .addField("id", Long::class.java, FieldAttribute.PRIMARY_KEY, FieldAttribute.REQUIRED)
                .addField("date", Long::class.java, FieldAttribute.REQUIRED)
                .addField("subId", Long::class.java, FieldAttribute.REQUIRED)
                .addRealmListField("recipients", String::class.java)
                .addField("sendAsGroup", Boolean::class.java, FieldAttribute.REQUIRED)
                .addField("body", String::class.java, FieldAttribute.REQUIRED)
                .addRealmListField("attachments", String::class.java)

            version++
        }

        if (version == 4L) {
            realm.schema.get("Conversation")
                ?.addField("pinned", Boolean::class.java, FieldAttribute.REQUIRED, FieldAttribute.INDEXED)

            version++
        }

        if (version == 5L) {
            realm.schema.create("BlockedNumber")
                .addField("id", Long::class.java, FieldAttribute.PRIMARY_KEY, FieldAttribute.REQUIRED)
                .addField("address", String::class.java, FieldAttribute.REQUIRED)

            version++
        }

        if (version == 6L) {
            realm.schema.get("Conversation")
                ?.addField("blockingClient", Integer::class.java)
                ?.addField("blockReason", String::class.java)

            realm.schema.get("MmsPart")
                ?.addField("seq", Integer::class.java, FieldAttribute.REQUIRED)
                ?.addField("name", String::class.java)

            version++
        }

        if (version == 7L) {
            realm.schema.get("Conversation")
                ?.addRealmObjectField("lastMessage", realm.schema.get("Message"))
                ?.removeField("count")
                ?.removeField("date")
                ?.removeField("snippet")
                ?.removeField("read")
                ?.removeField("me")

            val conversations = realm.where("Conversation")
                .findAll()

            val messages = realm.where("Message")
                .sort("date", Sort.DESCENDING)
                .distinct("threadId")
                .findAll()
                .associateBy { message -> message.getLong("threadId") }

            conversations.forEach { conversation ->
                conversation.setObject("lastMessage", messages[conversation.getLong("id")])
            }

            version++
        }

        if (version == 8L) {
            // Delete this data since we'll need to repopulate it with its new primaryKey
            realm.delete("PhoneNumber")

            realm.schema.create("ContactGroup")
                .addField("id", Long::class.java, FieldAttribute.PRIMARY_KEY, FieldAttribute.REQUIRED)
                .addField("title", String::class.java, FieldAttribute.REQUIRED)
                .addRealmListField("contacts", realm.schema.get("Contact"))

            realm.schema.get("PhoneNumber")
                ?.addField("id", Long::class.java, FieldAttribute.PRIMARY_KEY, FieldAttribute.REQUIRED)
                ?.addField("accountType", String::class.java)
                ?.addField("isDefault", Boolean::class.java, FieldAttribute.REQUIRED)

            val phoneNumbers = cursorToContact.getContactsCursor()
                ?.map(cursorToContact::map)
                ?.distinctBy { contact -> contact.numbers.firstOrNull()?.id } // Each row has only one number
                ?.groupBy { contact -> contact.lookupKey }
                ?: mapOf()

            realm.schema.get("Contact")
                ?.addField("starred", Boolean::class.java, FieldAttribute.REQUIRED)
                ?.addField("photoUri", String::class.java)
                ?.transform { realmContact ->
                    val numbers = RealmList<DynamicRealmObject>()
                    phoneNumbers[realmContact.get("lookupKey")]
                        ?.flatMap { contact -> contact.numbers }
                        ?.map { number ->
                            realm.createObject("PhoneNumber", number.id).apply {
                                setString("accountType", number.accountType)
                                setString("address", number.address)
                                setString("type", number.type)
                            }
                        }
                        ?.let(numbers::addAll)

                    val photoUri = phoneNumbers[realmContact.get("lookupKey")]
                        ?.firstOrNull { number -> number.photoUri != null }
                        ?.photoUri

                    realmContact.setList("numbers", numbers)
                    realmContact.setString("photoUri", photoUri)
                }

            // Migrate conversation themes
            val recipients = mutableMapOf<Long, Int>() // Map of recipientId:theme
            realm.where("Conversation").findAll().forEach { conversation ->
                val pref = prefs.theme(conversation.getLong("id"))
                if (pref.isSet) {
                    conversation.getList("recipients").forEach { recipient ->
                        recipients[recipient.getLong("id")] = pref.get()
                    }

                    pref.delete()
                }
            }

            recipients.forEach { (recipientId, theme) ->
                prefs.theme(recipientId).set(theme)
            }

            version++
        }

        if (version == 9L) {
            val migrateNotificationAction = { pref: Int ->
                when (pref) {
                    1 -> Preferences.NOTIFICATION_ACTION_READ
                    2 -> Preferences.NOTIFICATION_ACTION_REPLY
                    3 -> Preferences.NOTIFICATION_ACTION_CALL
                    4 -> Preferences.NOTIFICATION_ACTION_DELETE
                    else -> pref
                }
            }

            val migrateSwipeAction = { pref: Int ->
                when (pref) {
                    2 -> Preferences.SWIPE_ACTION_DELETE
                    3 -> Preferences.SWIPE_ACTION_CALL
                    4 -> Preferences.SWIPE_ACTION_READ
                    5 -> Preferences.SWIPE_ACTION_UNREAD
                    else -> pref
                }
            }

            if (prefs.notifAction1.isSet) prefs.notifAction1.set(migrateNotificationAction(prefs.notifAction1.get()))
            if (prefs.notifAction2.isSet) prefs.notifAction2.set(migrateNotificationAction(prefs.notifAction2.get()))
            if (prefs.notifAction3.isSet) prefs.notifAction3.set(migrateNotificationAction(prefs.notifAction3.get()))
            if (prefs.swipeLeft.isSet) prefs.swipeLeft.set(migrateSwipeAction(prefs.swipeLeft.get()))
            if (prefs.swipeRight.isSet) prefs.swipeRight.set(migrateSwipeAction(prefs.swipeRight.get()))

            version++
        }

        if (version == 10L) {
            realm.schema.get("MmsPart")
                ?.addField("messageId", Long::class.java, FieldAttribute.INDEXED, FieldAttribute.REQUIRED)
                ?.transform { part ->
                    val messageId = part.linkingObjects("Message", "parts").firstOrNull()?.getLong("contentId") ?: 0
                    part.setLong("messageId", messageId)
                }

            version++
        }
        if (version == 11L) {
            realm.schema.get("ScheduledMessage")
                ?.addField("conversationId", Long::class.java, FieldAttribute.REQUIRED)
            // Because there was never any property associated with which conversation/recipients a scheduled message was for,
            // we can't update this field on a realm migration. It will be set to a default of 0

            realm.schema.create("MessageContentFilter")
                .addField("id", Long::class.java, FieldAttribute.PRIMARY_KEY, FieldAttribute.REQUIRED)
                .addField("value", String::class.java, FieldAttribute.REQUIRED)
                .addField("caseSensitive", Boolean::class.java, FieldAttribute.REQUIRED)
                .addField("isRegex", Boolean::class.java, FieldAttribute.REQUIRED)
                .addField("includeContacts", Boolean::class.java, FieldAttribute.REQUIRED)

            version++
        }

        if (version == 12L) {
            realm.schema.get("Conversation")
                ?.addField("draftDate", Long::class.java, FieldAttribute.REQUIRED)

            version++
        }

        if (version == 13L) {
            val emojiReactionTable = realm.schema.create("EmojiReaction")
                .addField("id", Long::class.java, FieldAttribute.PRIMARY_KEY, FieldAttribute.REQUIRED)
                .addField("reactionMessageId", Long::class.java, FieldAttribute.INDEXED, FieldAttribute.REQUIRED)
                .addField("senderAddress", String::class.java, FieldAttribute.REQUIRED)
                .addField("emoji", String::class.java, FieldAttribute.REQUIRED)
                .addField("originalMessageText", String::class.java, FieldAttribute.REQUIRED)
                .addField("threadId", Long::class.java, FieldAttribute.INDEXED, FieldAttribute.REQUIRED)

            realm.schema.get("Message")
                ?.addField("isEmojiReaction", Boolean::class.java, FieldAttribute.REQUIRED)
                ?.addRealmListField("emojiReactions", emojiReactionTable)
                ?.transform { msg ->
                    msg.setBoolean("isEmojiReaction", false)
                }

            realm.schema.create("EmojiSyncNeeded")
                .addField("createdAt", Long::class.java, FieldAttribute.REQUIRED)

            realm.createObject("EmojiSyncNeeded")

            version++
        }

        if (version == 14L) {
            if (realm.schema.get("Conversation")?.hasField("sendAsGroup") == false) {
                realm.schema.get("Conversation")
                    ?.addField("sendAsGroup", Boolean::class.java, FieldAttribute.REQUIRED)
                    ?.transform { conversation ->
                        conversation.setBoolean(
                            "sendAsGroup",
                            (conversation.getList("recipients").size > 1)
                        )
                    }
            }
            if (realm.schema.get("Message")?.hasField("sendAsGroup") == false) {
                realm.schema.get("Message")
                    ?.addField("sendAsGroup", Boolean::class.java, FieldAttribute.REQUIRED)
            }

            version ++
        }

        check(version >= SCHEMA_VERSION) {
            "Migration from v$oldVersion to v$newVersion failed at v$version"
        }

        // throw an exception if migration failed
        check(version >= newVersion) {
            "Realm migration from v$oldVersion to v$newVersion after v$version"
        }

        // else
        Timber.d("Realm migration from v$oldVersion to v$newVersion succeeded")
    }

}
