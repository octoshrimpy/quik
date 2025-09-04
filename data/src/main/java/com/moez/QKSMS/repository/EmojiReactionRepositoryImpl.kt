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

import android.content.Context
import com.squareup.moshi.Moshi
import dev.octoshrimpy.quik.manager.KeyManager
import dev.octoshrimpy.quik.model.EmojiReaction
import dev.octoshrimpy.quik.model.Message
import io.realm.Realm
import io.realm.Sort
import timber.log.Timber
import javax.inject.Inject

class EmojiReactionRepositoryImpl @Inject constructor(
    private val context: Context,
    private val keyManager: KeyManager,
    private val moshi: Moshi,
) : EmojiReactionRepository {

    // We use an ordered map to make sure we test tapback regexes before generic ones
    private val reactionPatterns: LinkedHashMap<Regex, (MatchResult) -> ParsedEmojiReaction?> = linkedMapOf()
    private val removalPatterns: LinkedHashMap<Regex, (MatchResult) -> ParsedEmojiReaction?> = linkedMapOf()

    init {
        val assetEntries = loadEmojiPatternEntriesFromAssets()
        assetEntries.forEach { (localeTag, strings) ->
            try {
                addPatternsForLocaleStrings(localeTag, strings, reactionPatterns, removalPatterns)
            } catch (e: Exception) {
                Timber.w(e, "Failed to load asset patterns for locale: $localeTag")
            }
        }
        Timber.i("Loaded emoji reaction patterns for locales: ${assetEntries.map { it.first }}")
    }

    private fun addPatternsForLocaleStrings(
        localeTag: String,
        strings: EmojiPatternStrings,
        reactionPatterns: LinkedHashMap<Regex, (MatchResult) -> ParsedEmojiReaction?>,
        removalPatterns: LinkedHashMap<Regex, (MatchResult) -> ParsedEmojiReaction?>
    ) {
        // Google Messages
        reactionPatterns[Regex(strings.googleAdded)] = { match -> ParsedEmojiReaction(match.groupValues[1], match.groupValues[2]) }
        removalPatterns[Regex(strings.googleRemoved)] = { match -> ParsedEmojiReaction(match.groupValues[1], match.groupValues[2], isRemoval = true) }

        // iOS tapbacks (order matters: specific before generic)
        val tapbacks = listOf(
            Triple("❤️", strings.iosHeartAdded, strings.iosHeartRemoved),
            Triple("👍", strings.iosLikeAdded, strings.iosLikeRemoved),
            Triple("👎", strings.iosDislikeAdded, strings.iosDislikeRemoved),
            Triple("😂", strings.iosLaughAdded, strings.iosLaughRemoved),
            Triple("‼️", strings.iosExclamationAdded, strings.iosExclamationRemoved),
            Triple("❓", strings.iosQuestionMarkAdded, strings.iosQuestionMarkRemoved)
        )
        tapbacks.forEach { (emoji, added, removed) ->
            reactionPatterns[Regex(added)] = { match -> ParsedEmojiReaction(emoji, match.groupValues[1]) }

            removalPatterns[Regex(removed)] = { match -> ParsedEmojiReaction(emoji, match.groupValues[1], isRemoval = true) }
        }

        // Generic iOS emoji patterns
        reactionPatterns[Regex(strings.iosGenericAdded)] = { match ->
            if (match.groupValues.getOrNull(1) == "with a sticker") null // TODO: localize "with a sticker"
            else ParsedEmojiReaction(match.groupValues[1], match.groupValues[2])
        }
        removalPatterns[Regex(strings.iosGenericRemoved)] = { match ->
            // TODO: sticker?
            ParsedEmojiReaction(match.groupValues[1], match.groupValues[2], isRemoval = true)
        }

        Timber.d("Loaded emoji regex patterns for $localeTag from assets")
    }

    private fun loadEmojiPatternEntriesFromAssets(): List<Pair<String, EmojiPatternStrings>> {
        val dir = "emojis"
        val files = context.assets.list(dir) ?: emptyArray()
        return files.filter { it.endsWith(".json", ignoreCase = true) }
            .mapNotNull { filename ->
                val localeTag = filename.removeSuffix(".json")
                try {
                    val json = context.assets.open("$dir/$filename").bufferedReader().use { it.readText() }
                    val data = parseEmojiPatternsJson(json)
                    localeTag to data
                } catch (e: Exception) {
                    Timber.w(e, "Failed parsing emoji patterns asset: $filename")
                    null
                }
            }
    }

    private fun parseEmojiPatternsJson(json: String): EmojiPatternStrings {
        val adapter = moshi.adapter(EmojiPatternStrings::class.java)
        return requireNotNull(adapter.fromJson(json)) { "Invalid emoji patterns JSON" }
    }

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
     * We'll search recent messages first
     */
    override fun findTargetMessage(
        threadId: Long,
        originalMessageText: String,
        realm: Realm
    ): Message? {
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
            removeEmojiReaction(reactionMessage, parsedReaction, targetMessage)
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
