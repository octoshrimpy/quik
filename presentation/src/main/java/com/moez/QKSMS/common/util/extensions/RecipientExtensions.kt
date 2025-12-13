package dev.octoshrimpy.quik.common.util.extensions

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.provider.ContactsContract
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View.GONE
import android.view.View.MeasureSpec
import android.view.View.VISIBLE
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.Person
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import androidx.core.widget.TextViewCompat
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.util.Colors
import dev.octoshrimpy.quik.common.widget.QkTextView
import dev.octoshrimpy.quik.model.Recipient
import dev.octoshrimpy.quik.util.GlideApp
import dev.octoshrimpy.quik.util.tryOrNull
import timber.log.Timber


fun Recipient.getThemedIcon(context: Context, theme: Colors.Theme, width: Int, height: Int): IconCompat {
    var icon : IconCompat? = null
    val photoUri = contact?.photoUri
    if (photoUri != null) {
        val req = GlideApp.with(context)
            .asBitmap()
            .circleCrop()
            .load(photoUri)
            .submit(width, height)

        val bitmap = tryOrNull { req.get() }
        if (bitmap == null)
            icon = null
        else
            icon = IconCompat.createWithBitmap(bitmap)
    }
    if (icon == null) {
        // If there is no contact or no photo, create the default icon using the avatar_view layout
        try {
            val themedContext = ContextThemeWrapper(context, R.style.AppTheme)
            val inflater = LayoutInflater.from(themedContext)
            val container = FrameLayout(themedContext)
            container.layoutParams = FrameLayout.LayoutParams(width, height)
            val view = inflater.inflate(R.layout.avatar_view, container)
            val textView = view.findViewById<QkTextView>(R.id.initial)
            val iconView = view.findViewById<ImageView>(R.id.icon)
            val photoView = view.findViewById<ImageView>(R.id.photo)

            photoView.visibility = GONE
            view.setBackgroundColor(theme.theme)
            view.setBackgroundTint(theme.theme)
            textView.setTextColor(theme.textPrimary)
            TextViewCompat.setAutoSizeTextTypeWithDefaults(textView, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE)
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, height * 0.5f)
            iconView.layoutParams = FrameLayout.LayoutParams((width * 0.5).toInt(), (height * 0.5).toInt(),
                Gravity.CENTER)

            if (contact != null) {
                val initials = contact!!.name
                    .substringBefore(',')
                    .split(" ")
                    .filter { name -> name.isNotEmpty() }
                    .map { name -> name[0] }
                    .filter { initial -> initial.isLetterOrDigit() }
                    .map { initial -> initial.toString() }
                if (initials.isNotEmpty()) {
                    textView.text =
                        if (initials.size > 1) initials.first() + initials.last() else initials.first()
                    iconView.visibility = GONE
                } else {
                    textView.text = null
                    iconView.visibility = VISIBLE
                }
            }
            else {
                textView.visibility = GONE
                iconView.visibility = VISIBLE
                iconView.setTint(theme.textPrimary)
            }

            container.apply {
                measure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
                )
                layout(0, 0, measuredWidth, measuredHeight)
            }

            val bitmap = createBitmap(container.measuredWidth, container.measuredHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.clipPath(Path().apply {
                addCircle(
                    (width / 2).toFloat(),
                    (height / 2).toFloat(),
                    (width / 2).toFloat(),
                    Path.Direction.CW
                )
            })
            container.draw(canvas)

            icon = IconCompat.createWithBitmap(bitmap)
        }
        catch (e: Exception) {
            Timber.e(e)
            return IconCompat.createWithResource(context, R.mipmap.ic_shortcut_person)
        }
    }
    return icon
}

@TargetApi(29)
fun Person.Builder.fromRecipient(
    recipient: Recipient,
    context: Context,
    colors: Colors
): Person.Builder {
    setName(recipient.getDisplayName())
    setIcon(recipient.getThemedIcon(context, colors.theme(recipient), 512, 512))

    recipient.contact
        ?.let { contact -> "${ContactsContract.Contacts.CONTENT_LOOKUP_URI}/${contact.lookupKey}" }
        ?.let(this::setUri)

    setKey(recipient.address)

    return this
}

/**
 * Return a Person object corresponding to this recipient
 */
@TargetApi(29)
fun Recipient.toPerson(context : Context, colors: Colors): Person {
    val person = Person.Builder().fromRecipient(this, context, colors)
    return person.build()
}
