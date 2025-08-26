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
package dev.octoshrimpy.quik.util

import dev.octoshrimpy.quik.manager.KeyManager
import dev.octoshrimpy.quik.model.EmojiReaction
import dev.octoshrimpy.quik.model.Message
import io.realm.Realm
import io.realm.Sort
import timber.log.Timber

data class ParsedEmojiReaction(val emoji: String, val originalMessage: String, val patternType: String)

object EmojiReactionUtils {

    fun parseEmojiReaction(body: String): ParsedEmojiReaction? {
        // Log the raw message with unicode escapes for debugging
        val escapedBody = body.map { char ->
            when (char.code) {
                in 0x20..0x7E -> char.toString() // printable ASCII
                else -> "\\u${char.code.toString(16).padStart(4, '0')}"
            }
        }.joinToString("")
        Timber.v("Parsing body: \"$escapedBody\"") // TODO: are we okay logging SMS content?

        val iosPattern = Regex("^Reacted (.+?) to \u201C(.+?)\u201D$")
        val iosMatch = iosPattern.find(body)
        if (iosMatch != null) {
            val emoji = iosMatch.groupValues[1]
            val originalMessage = iosMatch.groupValues[2]

            if (emoji == "with a sticker") {
                Timber.d("Skipping sticker reaction: '$originalMessage'")
                return null
            }

            Timber.d("iOS pattern detected - emoji: '$emoji', original: '$originalMessage'")
            return ParsedEmojiReaction(emoji, originalMessage, "ios")
        }

        // https://github.com/octoshrimpy/quik/issues/152#issuecomment-2330183516
        val googleMessagesPattern = Regex("^\u200A\u200B(.+?)\u200B to \u201C\u200A(.+?)\u200A\u201D\u200A$")
        val googleMatch = googleMessagesPattern.find(body)
        if (googleMatch != null) {
            val emoji = googleMatch.groupValues[1]
            val originalMessage = googleMatch.groupValues[2]
            Timber.d("Google Messages pattern detected - emoji: '$emoji', original: '$originalMessage'")
            return ParsedEmojiReaction(emoji, originalMessage, "google")
        }

        return null
    }

    /**
     * Search for messages in the same thread with matching text content
     * We'll search recent messages first (within reasonable time window)
     */
    fun findTargetMessage(threadId: Long, originalMessageText: String, realm: Realm): Message? {
        val messages = realm.where(Message::class.java)
            .equalTo("threadId", threadId)
            .sort("date", Sort.DESCENDING)
            .limit(50) // TODO: should we keep this limit?
            .findAll()

        val exactMatch = messages.find { message ->
            message.getText(false).trim() == originalMessageText.trim()
        }
        if (exactMatch != null) {
            Timber.d("Found exact match for reaction target: message ID ${exactMatch.id}")
            return exactMatch
        }

        // Try partial match (in case of truncation)
        val partialMatch = messages.find { message ->
            val text = message.getText(false).trim()
            text.isNotEmpty() && (
                text.contains(originalMessageText.trim(), ignoreCase = true) ||
                originalMessageText.trim().contains(text, ignoreCase = true)
            )
        }
        if (partialMatch != null) {
            Timber.d("Found partial match for reaction target: message ID ${partialMatch.id}")
            return partialMatch
        }

        Timber.w("No target message found for reaction text: '$originalMessageText'")
        return null
    }

    fun saveEmojiReaction(
        reactionMessage: Message,
        parsedReaction: ParsedEmojiReaction,
        targetMessage: Message?,
        keyManager: KeyManager,
        realm: Realm,
    ) {
        val reaction = EmojiReaction().apply {
            id = keyManager.newId()
            reactionMessageId = reactionMessage.id
            targetMessageId = targetMessage?.id ?: 0
            senderAddress = reactionMessage.address
            emoji = parsedReaction.emoji
            originalMessageText = parsedReaction.originalMessage
            threadId = reactionMessage.threadId
            patternType = parsedReaction.patternType
        }

        realm.insertOrUpdate(reaction)
        reactionMessage.isEmojiReaction = true
        realm.insertOrUpdate(reactionMessage)

        if (targetMessage != null) {
            Timber.i("Saved emoji reaction: ${reaction.emoji} from ${reaction.senderAddress} to message ${targetMessage.id}")
        } else {
            Timber.w("Saved emoji reaction without target message: ${reaction.emoji} from ${reaction.senderAddress}")
        }
    }
}