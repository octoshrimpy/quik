/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.octoshrimpy.quik.feature.compose

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.net.toUri
import com.jakewharton.rxbinding2.view.clicks
import com.moez.QKSMS.common.QkMediaPlayer
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkRealmAdapter
import dev.octoshrimpy.quik.common.base.QkViewHolder
import dev.octoshrimpy.quik.common.util.Colors
import dev.octoshrimpy.quik.common.util.DateFormatter
import dev.octoshrimpy.quik.common.util.TextViewStyler
import dev.octoshrimpy.quik.common.util.extensions.dpToPx
import dev.octoshrimpy.quik.common.util.extensions.setBackgroundTint
import dev.octoshrimpy.quik.common.util.extensions.setPadding
import dev.octoshrimpy.quik.common.util.extensions.setTint
import dev.octoshrimpy.quik.common.util.extensions.setVisible
import dev.octoshrimpy.quik.common.util.extensions.withAlpha
import dev.octoshrimpy.quik.compat.SubscriptionManagerCompat
import dev.octoshrimpy.quik.extensions.isSmil
import dev.octoshrimpy.quik.extensions.isText
import dev.octoshrimpy.quik.extensions.joinTo
import dev.octoshrimpy.quik.extensions.millisecondsToMinutes
import dev.octoshrimpy.quik.extensions.truncateWithEllipses
import dev.octoshrimpy.quik.feature.compose.BubbleUtils.canGroup
import dev.octoshrimpy.quik.feature.compose.BubbleUtils.getBubble
import dev.octoshrimpy.quik.feature.compose.part.PartsAdapter
import dev.octoshrimpy.quik.feature.extensions.isEmojiOnly
import dev.octoshrimpy.quik.model.Conversation
import dev.octoshrimpy.quik.model.Message
import dev.octoshrimpy.quik.model.Recipient
import dev.octoshrimpy.quik.util.PhoneNumberUtils
import dev.octoshrimpy.quik.util.Preferences
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import io.realm.RealmResults
import kotlinx.android.synthetic.main.message_list_item_in.*
import kotlinx.android.synthetic.main.message_list_item_in.body
import kotlinx.android.synthetic.main.message_list_item_in.reactionText
import kotlinx.android.synthetic.main.message_list_item_in.parts
import kotlinx.android.synthetic.main.message_list_item_in.reactions
import kotlinx.android.synthetic.main.message_list_item_in.sim
import kotlinx.android.synthetic.main.message_list_item_in.simIndex
import kotlinx.android.synthetic.main.message_list_item_in.status
import kotlinx.android.synthetic.main.message_list_item_in.timestamp
import kotlinx.android.synthetic.main.message_list_item_in.view.*
import kotlinx.android.synthetic.main.message_list_item_out.*
import kotlinx.android.synthetic.main.message_list_item_out.view.cancel
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider

class MessagesAdapter @Inject constructor(
    subscriptionManager: SubscriptionManagerCompat,
    private val context: Context,
    private val colors: Colors,
    private val dateFormatter: DateFormatter,
    private val partsAdapterProvider: Provider<PartsAdapter>,
    private val phoneNumberUtils: PhoneNumberUtils,
    private val prefs: Preferences,
    private val textViewStyler: TextViewStyler,
) : QkRealmAdapter<Message, QkViewHolder>() {
    class AudioState(
        var partId: Long = -1,
        var state: QkMediaPlayer.PlayingState = QkMediaPlayer.PlayingState.Stopped,
        var seekBarUpdater: Disposable? = null,
        var viewHolder: QkViewHolder? = null
    )

    companion object {
        private const val VIEW_TYPE_MESSAGE_IN = 0
        private const val VIEW_TYPE_MESSAGE_OUT = 1

        private const val MAX_MESSAGE_DISPLAY_LENGTH = 5000
    }

    // click events passed back to compose view model
    val partClicks: Subject<Long> = PublishSubject.create()
    val messageLinkClicks: Subject<Uri> = PublishSubject.create()
    val cancelSendingClicks: Subject<Long> = PublishSubject.create()
    val sendNowClicks: Subject<Long> = PublishSubject.create()
    val resendClicks: Subject<Long> = PublishSubject.create()
    val partContextMenuRegistrar: Subject<View> = PublishSubject.create()
    val reactionClicks: Subject<Long> = PublishSubject.create()

    var data: Pair<Conversation, RealmResults<Message>>? = null
        set(value) {
            if (field === value) return

            field = value
            contactCache.clear()

            updateData(value?.second)
        }

    /**
     * Safely return the conversation, if available
     */
    private val conversation: Conversation?
        get() = data?.first?.takeIf { it.isValid }

    private val contactCache = ContactCache()
    private val expanded = HashMap<Long, Boolean>()
    private val subs = subscriptionManager.activeSubscriptionInfoList

    var theme: Colors.Theme = colors.theme()

    private val audioState = AudioState()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkViewHolder {
        // Use the parent's context to inflate the layout, otherwise link clicks will crash the app
        val inflater = LayoutInflater.from(parent.context)

        val view = if (viewType == VIEW_TYPE_MESSAGE_OUT) {
            inflater.inflate(R.layout.message_list_item_out, parent,false).apply {
                findViewById<ImageView>(R.id.cancelIcon).setTint(theme.theme)
                findViewById<ProgressBar>(R.id.cancel).setTint(theme.theme)
                findViewById<ImageView>(R.id.sendNowIcon).setTint(theme.theme)
                findViewById<ImageView>(R.id.resendIcon).setTint(theme.theme)
            }
        } else
            inflater.inflate(R.layout.message_list_item_in, parent, false)

        view.body.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE

        // register recycler view with compose activity for context menus
        partContextMenuRegistrar.onNext(view.parts)

        return QkViewHolder(view).apply {
            view.setOnClickListener {
                getItem(adapterPosition)?.let {
                    when (toggleSelection(it.id, false)) {
                        true -> view.isActivated = isSelected(it.id)
                        false -> {
                            expanded[it.id] = view.status.visibility != View.VISIBLE
                            notifyItemChanged(adapterPosition)
                        }
                    }
                }
            }
            view.setOnLongClickListener {
                getItem(adapterPosition)?.let {
                    toggleSelection(it.id)
                    view.isActivated = isSelected(it.id)
                }
                true
            }
        }
    }

    override fun onBindViewHolder(holder: QkViewHolder, position: Int) {
        val message = getItem(position) ?: return
        val previous = if (position == 0) null else getItem(position - 1)
        val next = if (position == itemCount - 1) null else getItem(position + 1)

        val theme = when (message.isOutgoingMessage()) {
            true -> colors.theme()
            false -> colors.theme(contactCache[message.address])
        }

        // Update the selected state
        holder.containerView.isActivated = isSelected(message.id) || highlight == message.id

        // bind the cancelFrame (cancel button) and send now button
        holder.cancelFrame?.let { cancelFrame ->
            holder.sendNowIcon?.let { sendNowIcon ->
                val isCancellable = message.isSending() && message.date > System.currentTimeMillis()

                if (isCancellable) {
                    cancelFrame.visibility = View.VISIBLE
                    sendNowIcon.visibility = View.VISIBLE

                    cancelFrame.setOnClickListener { cancelSendingClicks.onNext(message.id) }
                    sendNowIcon.setOnClickListener {  sendNowClicks.onNext(message.id) }

                    cancelFrame.cancel.progress = 2

                    val delay = when (prefs.sendDelay.get()) {
                        Preferences.SEND_DELAY_SHORT -> 3000
                        Preferences.SEND_DELAY_MEDIUM -> 5000
                        Preferences.SEND_DELAY_LONG -> 10000
                        else -> 0
                    }
                    val progress =
                        (1 - (message.date - System.currentTimeMillis()) / delay.toFloat()) * 100

                    ObjectAnimator.ofInt(cancelFrame.cancel, "progress", progress.toInt(), 100)
                        .setDuration(message.date - System.currentTimeMillis())
                        .start()
                }
                else {
                    cancelFrame.visibility = View.GONE
                    sendNowIcon.visibility = View.GONE

                    cancelFrame.setOnClickListener(null)
                    sendNowIcon.setOnClickListener(null)
                }
            }
        }

        // bind the resend icon view
        holder.resendIcon?.let { resendIcon ->
            if (message.isFailedMessage()) {
                resendIcon.visibility = View.VISIBLE
                resendIcon.clicks().subscribe {
                    resendClicks.onNext(message.id)
                    resendIcon.visibility = View.GONE
                }
            } else
                resendIcon.visibility = View.GONE
        }

        val subject = message.getCleansedSubject()

        var isMsgTextTruncated = false

        // get message text to display, which may need to be truncated
        val displayText = subject.joinTo(message.getText(false), "\n").let {
            isMsgTextTruncated = (it.length > MAX_MESSAGE_DISPLAY_LENGTH)

            // make subject sub-string bold, if subject is not blank
            if (subject.isNotBlank())
                SpannableString(it.truncateWithEllipses(MAX_MESSAGE_DISPLAY_LENGTH)).apply {
                    setSpan(
                        StyleSpan(Typeface.BOLD),
                        0,
                        subject.length,
                        Spannable.SPAN_INCLUSIVE_EXCLUSIVE
                    )
                }
            else
                it.truncateWithEllipses(MAX_MESSAGE_DISPLAY_LENGTH)
        }

        // Bind the message status
        bindStatus(holder, isMsgTextTruncated, message, next)

        // Bind the timestamp
        val subscription = subs.find { it.subscriptionId == message.subId }

        holder.timestamp.apply {
            text = dateFormatter.getMessageTimestamp(message.date)
            setVisible(
                    ((message.date - (previous?.date ?: 0))
                        .millisecondsToMinutes() >= BubbleUtils.TIMESTAMP_THRESHOLD) ||
                            (message.subId != previous?.subId) &&
                            (subscription != null)
            )
        }

        holder.simIndex.text = subscription?.simSlotIndex?.plus(1)?.toString()

        ((message.subId != previous?.subId) && (subscription != null) && (subs.size > 1)).also {
            holder.sim.setVisible(it)
            holder.simIndex.setVisible(it)
        }

        // Bind the grouping
        holder.containerView.setPadding(
            bottom = if (canGroup(message, next)) 0 else 16.dpToPx(context)
        )

        // Bind the avatar and bubble colour
        if (!message.isMe()) {
            holder.avatar.apply {
                setRecipient(contactCache[message.address])
                setVisible(!canGroup(message, next), View.INVISIBLE)
            }

            holder.body.apply {
                setTextColor(theme.textPrimary)
                setBackgroundTint(theme.theme)
                highlightColor = R.attr.bubbleColor.withAlpha(0x5d)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    textSelectHandle?.setTint(R.attr.bubbleColor.withAlpha(0x7d))
                    textSelectHandleLeft?.setTint(R.attr.bubbleColor.withAlpha(0x7d))
                    textSelectHandleRight?.setTint(R.attr.bubbleColor.withAlpha(0x7d))
                }
            }
        } else
            holder.body.apply {
                highlightColor = theme.theme.withAlpha(0x5d)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    textSelectHandle?.setTint(theme.theme.withAlpha(0xad))
                    textSelectHandleLeft?.setTint(theme.theme.withAlpha(0xad))
                    textSelectHandleRight?.setTint(theme.theme.withAlpha(0xad))
                }
            }

        // Bind the body text
        val emojiOnly = displayText.isEmojiOnly()
        textViewStyler.setTextSize(
            holder.body,
            when (emojiOnly) {
                true -> TextViewStyler.SIZE_EMOJI
                false -> TextViewStyler.SIZE_PRIMARY
            }
        )

        val spanString = SpannableStringBuilder(displayText)

        when (prefs.messageLinkHandling.get()) {
            Preferences.MESSAGE_LINK_HANDLING_BLOCK -> holder.body.autoLinkMask = 0
            Preferences.MESSAGE_LINK_HANDLING_ASK -> {
                //  manually handle link clicks if user has set to ask before opening links
                holder.body.apply {
                    isClickable = false
                    linksClickable = false
                    movementMethod = LinkMovementMethod.getInstance()

                    Linkify.addLinks(spanString, autoLinkMask)
                }

                spanString.apply {
                    for (span in getSpans(0, length, URLSpan::class.java)) {
                        // set handler for when user touches a link into new span
                        setSpan(
                            object : ClickableSpan() {
                                override fun onClick(widget: View) {
                                    messageLinkClicks.onNext(span.url.toUri())
                                }
                            },
                            getSpanStart(span),
                            getSpanEnd(span),
                            getSpanFlags(span)
                        )

                        // remove original span
                        removeSpan(span)
                    }
                }
            }
            else -> holder.body.movementMethod = LinkMovementMethod.getInstance()
        }

        holder.body.apply {
            text = spanString
            setVisible(message.isSms() || spanString.isNotBlank())

            setBackgroundResource(
                getBubble(
                    emojiOnly = emojiOnly,
                    canGroupWithPrevious = canGroup(message, previous) ||
                            message.parts.any { !it.isSmil() && !it.isText() },
                    canGroupWithNext = canGroup(message, next),
                    isMe = message.isMe()
                )
            )
        }

        // Bind the parts
        holder.parts.adapter = partsAdapterProvider.get().apply {
            this.theme = theme
            setData(message, previous, next, holder, audioState)
            contextMenuValue = message.id
            clicks.subscribe(partClicks)    // part clicks gets passed back to compose view model
        }

        showEmojiReactions(holder, message)
    }

    private fun showEmojiReactions(holder: QkViewHolder, message: Message) {
        holder.reactions?.let { reactionsContainer ->
            val reactions = message.emojiReactions
            val hasReactions = reactions.isNotEmpty()

            if (hasReactions) {
                val uniqueEmojis = reactions.map { it.emoji }.distinct()
                val totalCount = reactions.size

                // Show unique emojis followed by total count
                val reactionText = if (totalCount == 1) {
                    uniqueEmojis.first()
                } else {
                    "${uniqueEmojis.joinToString("")}\u00A0$totalCount"
                }

                holder.reactionText?.text = reactionText
                holder.reactionText?.setOnClickListener { reactionClicks.onNext(message.id) }
                reactionsContainer.setVisible(true)
            } else {
                reactionsContainer.setVisible(false)
                holder.reactionText?.setOnClickListener(null)
            }
        }
    }

    private fun bindStatus(
        holder: QkViewHolder,
        bodyTextTruncated: Boolean,
        message: Message,
        next: Message?
    ) {
        holder.status.apply {
            text = when {
                message.isSending() -> context.getString(R.string.message_status_sending)
                message.isDelivered() -> context.getString(
                    R.string.message_status_delivered,
                    dateFormatter.getTimestamp(message.dateSent)
                )
                message.isFailedMessage() -> context.getString(R.string.message_status_failed)
                bodyTextTruncated -> context.getString(R.string.message_body_too_long_to_display)
                (!message.isMe() && (conversation?.recipients?.size ?: 0) > 1) ->
                    // incoming group message
                    "${contactCache[message.address]?.getDisplayName()} • ${
                        dateFormatter.getTimestamp(message.date)}"
                else -> dateFormatter.getTimestamp(message.date)
            }

            val age = TimeUnit.MILLISECONDS.toMinutes(
                System.currentTimeMillis() - message.date
            )

            setVisible(
                when {
                    expanded[message.id] == true -> true
                    message.isSending() -> true
                    message.isFailedMessage() -> true
                    bodyTextTruncated -> true
                    expanded[message.id] == false -> false
                    ((conversation?.recipients?.size ?: 0) > 1) &&
                            !message.isMe() && next?.compareSender(message) != true -> true
                    (message.isDelivered() &&
                            (next?.isDelivered() != true) &&
                            (age <= BubbleUtils.TIMESTAMP_THRESHOLD)) -> true

                    else -> false
                }
            )
        }
    }

    override fun getItemId(position: Int): Long {
        return getItem(position)?.id ?: -1
    }

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position) ?: return -1
        return when (message.isMe()) {
            true -> VIEW_TYPE_MESSAGE_OUT
            false -> VIEW_TYPE_MESSAGE_IN
        }
    }

    fun expandMessages(messageIds: List<Long>, expand: Boolean) {
        messageIds.forEach { expanded[it] = expand }
        notifyDataSetChanged()
    }

    /**
     * Cache the contacts in a map by the address, because the messages we're binding don't have
     * a reference to the contact.
     */
    private inner class ContactCache : HashMap<String, Recipient?>() {
        override fun get(key: String): Recipient? {
            if (super.get(key)?.isValid != true)
                set(
                    key,
                    conversation?.recipients?.firstOrNull {
                        phoneNumberUtils.compare(it.address, key)
                    }
                )

            return super.get(key)?.takeIf { it.isValid }
        }

    }
}
