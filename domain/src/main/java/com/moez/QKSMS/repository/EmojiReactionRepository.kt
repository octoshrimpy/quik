package dev.octoshrimpy.quik.repository

import dev.octoshrimpy.quik.model.Message
import io.realm.Realm

data class ParsedEmojiReaction(val emoji: String, val originalMessage: String, val isRemoval: Boolean = false)

interface EmojiReactionRepository {
    fun parseEmojiReaction(body: String): ParsedEmojiReaction?

    fun findTargetMessage(threadId: Long, originalMessageText: String, realm: Realm): Message?

    fun saveEmojiReaction(
        reactionMessage: Message,
        parsedReaction: ParsedEmojiReaction,
        targetMessage: Message?,
        realm: Realm,
    )

    fun deleteAndReparseAllEmojiReactions(realm: Realm)
}