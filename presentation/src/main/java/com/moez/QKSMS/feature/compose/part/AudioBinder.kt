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
package dev.octoshrimpy.quik.feature.compose.part

import android.content.Context
import android.content.res.ColorStateList
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.METADATA_KEY_TITLE
import android.view.View
import android.widget.SeekBar
import com.moez.QKSMS.common.QkMediaPlayer
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.Navigator
import dev.octoshrimpy.quik.common.base.QkViewHolder
import dev.octoshrimpy.quik.common.util.Colors
import dev.octoshrimpy.quik.common.util.extensions.resolveThemeColor
import dev.octoshrimpy.quik.common.util.extensions.setBackgroundTint
import dev.octoshrimpy.quik.common.util.extensions.setTint
import dev.octoshrimpy.quik.common.util.extensions.withAlpha
import dev.octoshrimpy.quik.common.widget.BubbleImageView
import dev.octoshrimpy.quik.extensions.isAudio
import dev.octoshrimpy.quik.extensions.resourceExists
import dev.octoshrimpy.quik.feature.compose.MessagesAdapter
import dev.octoshrimpy.quik.model.Message
import dev.octoshrimpy.quik.model.MmsPart
import dev.octoshrimpy.quik.util.GlideApp
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.mms_audio_preview_list_item.*
import kotlinx.android.synthetic.main.mms_image_preview_list_item.thumbnail
import java.util.concurrent.TimeUnit
import javax.inject.Inject


class AudioBinder @Inject constructor(colors: Colors, private val context: Context) :
    PartBinder() {

    @Inject lateinit var navigator: Navigator

    override val partLayout = R.layout.mms_audio_preview_list_item
    override var theme = colors.theme()

    override fun canBindPart(part: MmsPart) = part.isAudio()

    var audioState = MessagesAdapter.AudioState(-1, QkMediaPlayer.PlayingState.Stopped)

    private fun startSeekBarUpdateTimer() {
        audioState.apply {
            seekBarUpdater?.dispose()
            seekBarUpdater = Observable.interval(500, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext {
                    viewHolder?.seekBar?.progress = QkMediaPlayer.currentPosition
                }
                .subscribe()
        }
    }

    private fun uiToPlaying(viewHolder: QkViewHolder) {
        viewHolder.seekBar.max = QkMediaPlayer.duration
        viewHolder.seekBar.isEnabled = true
        viewHolder.seekBar.progress = QkMediaPlayer.currentPosition
        viewHolder.playPause.setImageResource(R.drawable.exo_icon_pause)
        viewHolder.playPause.tag = QkMediaPlayer.PlayingState.Playing
        viewHolder.metadataTitle.isSelected = true     // start marquee
    }

    private fun uiToPaused(viewHolder: QkViewHolder) {
        viewHolder.playPause.setImageResource(R.drawable.exo_icon_play)
        viewHolder.playPause.tag = QkMediaPlayer.PlayingState.Paused
    }

    private fun uiToStopped(viewHolder: QkViewHolder) {
        viewHolder.seekBar.progress = 0
        viewHolder.seekBar.max = 0
        viewHolder.seekBar.isEnabled = false
        viewHolder.playPause.setImageResource(R.drawable.exo_icon_play)
        viewHolder.playPause.tag = QkMediaPlayer.PlayingState.Stopped
        viewHolder.metadataTitle.isSelected = false   // stop marquee
    }

    override fun bindPart(
        holder: QkViewHolder,
        part: MmsPart,
        message: Message,
        canGroupWithPrevious: Boolean,
        canGroupWithNext: Boolean,
    ) {
        // click on background - passes back to compose view model
        holder.containerView.setOnClickListener { clicks.onNext(part.id) }

        // play/pause button click handling
        holder.playPause.setOnClickListener {
            when (holder.playPause.tag) {
                QkMediaPlayer.PlayingState.Playing -> {
                    if (audioState.partId == part.id) {
                        QkMediaPlayer.pause()
                        uiToPaused(holder)
                        audioState.state = QkMediaPlayer.PlayingState.Paused

                        // stop progress bar update timer
                        audioState.seekBarUpdater?.dispose()
                    }
                }
                QkMediaPlayer.PlayingState.Paused -> {
                    if (audioState.partId == part.id) {
                        QkMediaPlayer.start()
                        uiToPlaying(holder)
                        audioState.state = QkMediaPlayer.PlayingState.Playing

                        // start progress bar update timer
                        startSeekBarUpdateTimer()
                    }
                }
                else -> {
                    if (part.getUri().resourceExists(context)) {
                        QkMediaPlayer.reset() // make sure reset before trying to (re-)use

                        QkMediaPlayer.setOnPreparedListener {
                            // start media playing
                            QkMediaPlayer.start()

                            uiToPlaying(holder)

                            // set current view holder and part as active
                            audioState.apply {
                                audioState.state = QkMediaPlayer.PlayingState.Playing
                                partId = part.id
                                viewHolder = holder
                            }

                            // start progress bar update timer
                            startSeekBarUpdateTimer()
                        }

                        QkMediaPlayer.setOnCompletionListener {   // also called on error because we don't have an onerrorlistener
                            audioState.apply {
                                // if this part is currently active, set it to stopped and inactive
                                if ((partId == part.id) && (viewHolder != null))
                                    uiToStopped(viewHolder!!)

                                state = QkMediaPlayer.PlayingState.Stopped
                                partId = -1
                                viewHolder = null
                            }
                        }

                        // start the media player play sequence
                        QkMediaPlayer.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)     // music, maybe?? could be voice. don't want to use CONTENT_TYPE_UNKNOWN though
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        )

                        QkMediaPlayer.setDataSource(context, part.getUri())

                        QkMediaPlayer.prepareAsync()
                    }
                }
            }
        }

        // if this item is the active active audio item update the active view holder
        if (audioState.partId == part.id)
            audioState.viewHolder = holder
        // else, this is not the active item so ensure the stored view holder is not this one
        else if (audioState.viewHolder == holder)
            audioState.viewHolder = null

        // tint colours
        val secondaryColor =
            if (!message.isMe())
                theme.theme
            else
                holder.containerView.context.resolveThemeColor(R.attr.bubbleColor)
        val primaryColor =
            if (!message.isMe())
                theme.textPrimary
            else
                holder.containerView.context.resolveThemeColor(android.R.attr.textColorPrimary)

        // sound wave
        holder.soundWave.setTint(primaryColor)

        // seek bar
        holder.seekBar.apply {
            setTint(secondaryColor)
            thumbTintList = ColorStateList.valueOf(secondaryColor)

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(p0: SeekBar?, progress: Int, fromUser: Boolean) {
                    // if seek was initiated by the user and this part is currently playing
                    if (fromUser)
                        QkMediaPlayer.seekTo(progress)
                }
                override fun onStartTrackingTouch(p0: SeekBar?) { /* nothing */ }
                override fun onStopTrackingTouch(p0: SeekBar?) { /* nothing */ }
            })
        }

        // playPause button
        holder.playPause. apply {
            if ((audioState.partId == part.id) &&
                (audioState.state == QkMediaPlayer.PlayingState.Playing))
                uiToPlaying(holder)
            else if ((audioState.partId == part.id) &&
                (audioState.state == QkMediaPlayer.PlayingState.Paused))
                uiToPaused(holder)
            else
                uiToStopped(holder)

            setTint(secondaryColor)
            setBackgroundTint(primaryColor)
        }

        MediaMetadataRetriever().apply {
            try {
                if (part.getUri().resourceExists(context))
                    setDataSource(context, part.getUri())

                // metadata title
                holder.metadataTitle.apply {
                    text = extractMetadata(METADATA_KEY_TITLE)

                    if (text.isEmpty())
                        visibility = View.GONE
                    else {
                        visibility = View.VISIBLE
                        setTextColor(primaryColor)
                        setBackgroundTint(secondaryColor.withAlpha(0xcc))    // hex value is alpha
                    }
                }

                // bubble / embedded image
                holder.thumbnail.apply {
                    bubbleStyle = when {
                        !canGroupWithPrevious && canGroupWithNext ->
                            if (message.isMe()) BubbleImageView.Style.OUT_FIRST else BubbleImageView.Style.IN_FIRST

                        canGroupWithPrevious && canGroupWithNext ->
                            if (message.isMe()) BubbleImageView.Style.OUT_MIDDLE else BubbleImageView.Style.IN_MIDDLE

                        canGroupWithPrevious && !canGroupWithNext ->
                            if (message.isMe()) BubbleImageView.Style.OUT_LAST else BubbleImageView.Style.IN_LAST

                        else -> BubbleImageView.Style.ONLY
                    }

                    val embeddedPicture = embeddedPicture
                    if (embeddedPicture == null) {
                        holder.frame.layoutParams.height = (holder.frame.layoutParams.width / 2)
                        setTint(secondaryColor)
                        setImageResource(R.drawable.rectangle)
                    } else {
                        holder.frame.layoutParams.height = holder.frame.layoutParams.width
                        setTint(null)
                        GlideApp.with(context)
                            .asBitmap()
                            .load(embeddedPicture)
                            .override(
                                holder.frame.layoutParams.width,
                                holder.frame.layoutParams.height
                            )
                            .into(this)
                    }
                }
            } catch (e: Exception) { /* nothing */ }
            finally {
                release()
            }
        }
    }
}