package dev.octoshrimpy.quik.repository.migrations

import io.realm.DynamicRealm
import io.realm.FieldAttribute
import io.realm.RealmMigration

class AppRealmMigration : RealmMigration {

    companion object {
        const val SCHEMA_VERSION: Long = 15
    }

    override fun migrate(realm: DynamicRealm, oldVersion: Long, newVersion: Long) {
        var version = oldVersion

        if (version < 11L) {
            realm.schema.get("Conversation")
                ?.addField("sendAsGroup", Boolean::class.java, FieldAttribute.REQUIRED)
                ?.transform { obj ->
                    val list = obj.getList("recipients")
                    obj.setBoolean("sendAsGroup", list != null && list.size > 1)
                }

            realm.schema.get("Message")
                ?.addField("sendAsGroup", Boolean::class.java, FieldAttribute.REQUIRED)
                ?.transform { obj ->
                    obj.setBoolean("sendAsGroup", false)
                }

            version = 11L
        }

        if (version < 12L) {
            realm.schema.get("Conversation")
                ?.addField("draftDate", Long::class.java, FieldAttribute.REQUIRED)
                ?.transform { obj ->
                    obj.setLong("draftDate", 0L)
                }

            version = 12L
        }

        if (version < 13L) {
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

            realm.createObject("EmojiSyncNeeded").apply {
                setLong("createdAt", System.currentTimeMillis())
            }

            version = 13L
        }

        if (version < 14L) {
            version = 14L
        }

        if (version < SCHEMA_VERSION) {
            version = SCHEMA_VERSION
        }
    }
}
