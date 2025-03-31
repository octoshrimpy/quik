package dev.octoshrimpy.quik.feature.extensions

import android.text.Spannable
import androidx.emoji2.text.EmojiCompat
import androidx.emoji2.text.EmojiCompat.REPLACE_STRATEGY_ALL
import androidx.emoji2.text.EmojiSpan


fun CharSequence.isEmojiOnly(considerWhitespace: Boolean = false): Boolean {
    val cs =
        if (considerWhitespace) this
        else this.replace(Regex("[\\s\n\r]"), "")

    if (cs.isEmpty())
        return false

    return when (val spannable = EmojiCompat.get().process(
        cs,
        0,
        (cs.length - 1),
        Int.MAX_VALUE,
        REPLACE_STRATEGY_ALL
    )) {
        is Spannable -> {
            (spannable
                .getSpans(0, (spannable.length - 1), EmojiSpan::class.java)
                .fold(0) { acc, emojiSpan ->
                    acc + (spannable.getSpanEnd(emojiSpan) - spannable.getSpanStart(emojiSpan))
                } == cs.length)
        }
        else -> false
    }
}