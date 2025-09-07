package dev.octoshrimpy.quik.util

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class EmojiPatternStrings(
    @Json(name = "emoji_reaction_ios_generic_added") val iosGenericAdded: String? = null,
    @Json(name = "emoji_reaction_ios_generic_removed") val iosGenericRemoved: String? = null,

    @Json(name = "emoji_reaction_ios_heart_added") val iosHeartAdded: String? = null,
    @Json(name = "emoji_reaction_ios_heart_removed") val iosHeartRemoved: String? = null,

    @Json(name = "emoji_reaction_ios_like_added") val iosLikeAdded: String? = null,
    @Json(name = "emoji_reaction_ios_like_removed") val iosLikeRemoved: String? = null,

    @Json(name = "emoji_reaction_ios_dislike_added") val iosDislikeAdded: String? = null,
    @Json(name = "emoji_reaction_ios_dislike_removed") val iosDislikeRemoved: String? = null,

    @Json(name = "emoji_reaction_ios_laugh_added") val iosLaughAdded: String? = null,
    @Json(name = "emoji_reaction_ios_laugh_removed") val iosLaughRemoved: String? = null,

    @Json(name = "emoji_reaction_ios_exclamation_added") val iosExclamationAdded: String? = null,
    @Json(name = "emoji_reaction_ios_exclamation_removed") val iosExclamationRemoved: String? = null,

    @Json(name = "emoji_reaction_ios_question_mark_added") val iosQuestionMarkAdded: String? = null,
    @Json(name = "emoji_reaction_ios_question_mark_removed") val iosQuestionMarkRemoved: String? = null,
)
