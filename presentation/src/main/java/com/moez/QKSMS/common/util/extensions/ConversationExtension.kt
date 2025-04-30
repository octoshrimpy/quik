package dev.octoshrimpy.quik.common.util.extensions

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.widget.AvatarView
import dev.octoshrimpy.quik.model.Conversation
import timber.log.Timber

fun Conversation.getThemedIcon(context: Context, width: Int, height: Int): IconCompat {
    return try {
        val inflater = LayoutInflater.from(context)
        val parent = ConstraintLayout(context).apply {
            layoutParams = ConstraintLayout.LayoutParams(width, height)
        }
        val view = inflater.inflate(R.layout.group_avatar_view, parent, true).apply {
            layoutParams = ConstraintLayout.LayoutParams(width, height)
        }

        val avatar1 = view.findViewById<AvatarView>(R.id.avatar1)
        val avatar2 = view.findViewById<AvatarView>(R.id.avatar2)
        val avatar1Frame = view.findViewById<FrameLayout>(R.id.avatar1Frame)

        val multipleRecipients = recipients.size > 1

        avatar1Frame.setBackgroundTint(
            if (multipleRecipients)
                context.resolveThemeColor(android.R.attr.windowBackground)
            else
                context.getColorCompat(android.R.color.transparent)
        )
        avatar1Frame.updateLayoutParams<ConstraintLayout.LayoutParams> {
            matchConstraintPercentWidth = if (multipleRecipients) 0.75f else 1.0f
        }
        avatar2.isVisible = multipleRecipients

        recipients.getOrNull(0)?.let(avatar1::setRecipient)
        recipients.getOrNull(1)?.let(avatar2::setRecipient)

        view.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        Timber.v("w: ${view.measuredWidth}; h: ${view.measuredHeight}")

        val bitmap = createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)

        IconCompat.createWithBitmap(bitmap)
    } catch (e: Exception) {
        Timber.e(e, "Error creating default icon")
        IconCompat.createWithResource(context, R.mipmap.ic_shortcut_people)
    }
}
