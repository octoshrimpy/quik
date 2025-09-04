package dev.octoshrimpy.quik.repository

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class EmojiPatternStrings(
    @Json(name = "emoji_reaction_google_messages_added") val googleAdded: String,
    @Json(name = "emoji_reaction_google_messages_removed") val googleRemoved: String,

    @Json(name = "emoji_reaction_ios_generic_added") val iosGenericAdded: String,
    @Json(name = "emoji_reaction_ios_generic_removed") val iosGenericRemoved: String,

    @Json(name = "emoji_reaction_ios_heart_added") val iosHeartAdded: String,
    @Json(name = "emoji_reaction_ios_heart_removed") val iosHeartRemoved: String,

    @Json(name = "emoji_reaction_ios_like_added") val iosLikeAdded: String,
    @Json(name = "emoji_reaction_ios_like_removed") val iosLikeRemoved: String,

    @Json(name = "emoji_reaction_ios_dislike_added") val iosDislikeAdded: String,
    @Json(name = "emoji_reaction_ios_dislike_removed") val iosDislikeRemoved: String,

    @Json(name = "emoji_reaction_ios_laugh_added") val iosLaughAdded: String,
    @Json(name = "emoji_reaction_ios_laugh_removed") val iosLaughRemoved: String,

    @Json(name = "emoji_reaction_ios_exclamation_added") val iosExclamationAdded: String,
    @Json(name = "emoji_reaction_ios_exclamation_removed") val iosExclamationRemoved: String,

    @Json(name = "emoji_reaction_ios_question_mark_added") val iosQuestionMarkAdded: String,
    @Json(name = "emoji_reaction_ios_question_mark_removed") val iosQuestionMarkRemoved: String,
)
