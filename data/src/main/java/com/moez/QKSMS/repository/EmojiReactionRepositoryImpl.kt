/*
 * Copyright (C) 2025
 *
 * This file is part of QUIK.
 *
 * QUIK is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QUIK is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QUIK.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.octoshrimpy.quik.repository

import dev.octoshrimpy.quik.manager.KeyManager
import dev.octoshrimpy.quik.model.EmojiReaction
import dev.octoshrimpy.quik.model.Message
import dev.octoshrimpy.quik.repository.EmojiReactionUtils.reactionPatterns
import dev.octoshrimpy.quik.repository.EmojiReactionUtils.removalPatterns
import io.realm.Realm
import io.realm.Sort
import timber.log.Timber
import javax.inject.Inject

class EmojiReactionRepositoryImpl @Inject constructor(
    private val keyManager: KeyManager
) : EmojiReactionRepository {
    override fun parseEmojiReaction(body: String): ParsedEmojiReaction? {
        val removal = parseRemoval(body)
        if (removal != null) return removal

        for ((pattern, parser) in reactionPatterns) {
            val match = pattern.find(body)
            if (match == null) continue;

            val result = parser(match)
            if (result == null) continue

            Timber.d("Reaction found with ${result.emoji}")
            return result
        }

        return null
    }

    private fun parseRemoval(body: String): ParsedEmojiReaction? {
        for ((pattern, parser) in removalPatterns) {
            val match = pattern.find(body)
            if (match == null) continue;

            val result = parser(match)
            if (result == null) continue

            Timber.d("Removal found with ${result.emoji}")
            return result
        }

        return null
    }

    /**
     * Search for messages in the same thread with matching text content
     * We'll search recent messages first (within reasonable time window)
     */
    override fun findTargetMessage(threadId: Long, originalMessageText: String, realm: Realm): Message? {
        val startTime = System.currentTimeMillis()
        val messages = realm.where(Message::class.java)
            .equalTo("threadId", threadId)
            .sort("date", Sort.DESCENDING)
            .findAll()
        val endTime = System.currentTimeMillis()
        Timber.d("Found ${messages.size} messages as emoji targets in ${endTime - startTime}ms")

        val match = messages.find { message ->
            message.getText(false).trim() == originalMessageText.trim()
        }
        if (match != null) {
            Timber.d("Found match for reaction target: message ID ${match.id}")
            return match
        }

        Timber.w("No target message found for reaction text: '$originalMessageText'")
        return null
    }

    private fun removeEmojiReaction(
        reactionMessage: Message,
        reaction: ParsedEmojiReaction,
        targetMessage: Message?,
        realm: Realm,
    ) {
        if (targetMessage == null) {
            Timber.w("Cannot remove emoji reaction '${reaction.emoji}': no target message found")
            reactionMessage.isEmojiReaction = true
            return
        }

        val existingReaction = targetMessage.emojiReactions.find { candidate ->
            candidate.senderAddress == reactionMessage.address && candidate.emoji == reaction.emoji
        }

        if (existingReaction != null) {
            existingReaction.deleteFromRealm()
            Timber.d("Removed emoji reaction: ${reaction.emoji} to message ${targetMessage.id}")
        } else {
            Timber.w("No existing emoji reaction found to remove: ${reaction.emoji} to message ${targetMessage.id}")
        }

        reactionMessage.isEmojiReaction = true
    }

    override fun saveEmojiReaction(
        reactionMessage: Message,
        parsedReaction: ParsedEmojiReaction,
        targetMessage: Message?,
        realm: Realm,
    ) {
        if (parsedReaction.isRemoval) {
            removeEmojiReaction(reactionMessage, parsedReaction, targetMessage, realm)
            return
        }

        val reaction = EmojiReaction().apply {
            id = keyManager.newId()
            reactionMessageId = reactionMessage.id
            senderAddress = reactionMessage.address
            emoji = parsedReaction.emoji
            originalMessageText = parsedReaction.originalMessage
            threadId = reactionMessage.threadId
        }
        realm.insertOrUpdate(reaction)

        reactionMessage.isEmojiReaction = true

        if (targetMessage != null) {
            targetMessage.emojiReactions.add(reaction)

            Timber.i("Saved emoji reaction: ${reaction.emoji} to message ${targetMessage.id}")
        } else {
            Timber.w("Saved emoji reaction without target message: ${reaction.emoji}")
        }
    }

    override fun deleteAndReparseAllEmojiReactions(realm: Realm) {
        val startTime = System.currentTimeMillis()

        realm.delete(EmojiReaction::class.java)
        realm.where(Message::class.java).findAll().map {
            it.isEmojiReaction = false
        }

        val allMessages = realm.where(Message::class.java)
            .beginGroup()
                .beginGroup()
                    .equalTo("type", "sms")
                    .isNotEmpty("body")
                .endGroup()
                .or()
                .beginGroup()
                    .equalTo("type", "mms")
                    .isNotEmpty("parts.text")
                .endGroup()
            .endGroup()
            .sort("date", Sort.ASCENDING) // parse oldest to newest to handle reactions & removals properly
            .findAll()

        allMessages.forEach { message ->
            val text = message.getText(false)
            val parsedReaction = parseEmojiReaction(text)
            if (parsedReaction != null) {
                val targetMessage = findTargetMessage(
                    message.threadId,
                    parsedReaction.originalMessage,
                    realm
                )
                saveEmojiReaction(
                    message,
                    parsedReaction,
                    targetMessage,
                    realm,
                )
            }
        }

        val endTime = System.currentTimeMillis()
        Timber.d("Deleted and reparsed all emoji reactions in ${endTime - startTime}ms")
    }

}

object EmojiReactionUtils {
    private fun tapback(emoji: String, isRemoval: Boolean = false): (MatchResult) -> ParsedEmojiReaction {
        return { match ->
            ParsedEmojiReaction(emoji, match.groupValues[1], isRemoval)
        }
    }

    val reactionPatterns: Map<Regex, (MatchResult) -> ParsedEmojiReaction?> = mapOf(
        // Google Messages - https://github.com/octoshrimpy/quik/issues/152#issuecomment-2330183516
        Regex("^\u200A\u200B(.+?)\u200B to \u201C\u200A(.+?)\u200A\u201D\u200A$") to { match ->
            ParsedEmojiReaction(match.groupValues[1], match.groupValues[2])
        },
        // iOS
        Regex("^Reacted (.+?) to \u201C(.+?)\u201D$") to { match ->
            if (match.groupValues[1] == "with a sticker")
                null
            else
                ParsedEmojiReaction(match.groupValues[1], match.groupValues[2])
        },
        // iOS tapbacks
        Regex("^Loved \u201C(.+?)\u201D$") to tapback("❤️"),
        Regex("^Liked \u201C(.+?)\u201D$") to tapback("👍"),
        Regex("^Disliked \u201C(.+?)\u201D$") to tapback("👎"),
        Regex("^Laughed at \u201C(.+?)\u201D$") to tapback("😂"),
        Regex("^Emphasized \u201C(.+?)\u201D$") to tapback("‼️"),
        Regex("^Questioned \u201C(.+?)\u201D$") to tapback("❓"),
    )

    val removalPatterns: Map<Regex, (MatchResult) -> ParsedEmojiReaction?> = mapOf(
        // Google Messages
        Regex("^\u200ARemoved \u200C(.+?)\u200C from \u201C\u200A(.+?)\u200A\u201D\u200A$") to { match ->
            ParsedEmojiReaction(match.groupValues[1], match.groupValues[2], isRemoval = true)
        },
        // iOS tapbacks
        Regex("^Removed a heart from \u201C(.+?)\u201D$") to tapback("❤️", true),
        Regex("^Removed a like from \u201C(.+?)\u201D$") to tapback("👍", true),
        Regex("^Removed a dislike from \u201C(.+?)\u201D$") to tapback("👎", true),
        Regex("^Removed a laugh from \u201C(.+?)\u201D$") to tapback("😂", true),
        Regex("^Removed an exclamation from \u201C(.+?)\u201D$") to tapback("‼️", true),
        Regex("^Removed a question mark from \u201C(.+?)\u201D$") to tapback("❓", true),
        // iOS emoji - keep this below tapbacks as this regex would otherwise also match the patterns above
        Regex("^Removed (.+?) from \u201C(.+?)\u201D$") to { match ->
            ParsedEmojiReaction(match.groupValues[1], match.groupValues[2], isRemoval = true)
        },
    )
}
