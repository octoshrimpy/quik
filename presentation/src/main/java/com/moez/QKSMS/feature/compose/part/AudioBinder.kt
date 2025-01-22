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
import android.view.View
import android.widget.SeekBar
import com.moez.QKSMS.common.QkMediaPlayer
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkViewHolder
import dev.octoshrimpy.quik.common.util.Colors
import dev.octoshrimpy.quik.common.util.extensions.resolveThemeColor
import dev.octoshrimpy.quik.common.util.extensions.setBackgroundTint
import dev.octoshrimpy.quik.common.util.extensions.setTint
import dev.octoshrimpy.quik.common.widget.BubbleImageView
import dev.octoshrimpy.quik.extensions.isAudio
import dev.octoshrimpy.quik.model.Message
import dev.octoshrimpy.quik.model.MmsPart
import dev.octoshrimpy.quik.util.GlideApp
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.mms_audio_preview_list_item.*
import kotlinx.android.synthetic.main.mms_audio_preview_list_item.view.playPause
import kotlinx.android.synthetic.main.mms_audio_preview_list_item.view.seekBar
import kotlinx.android.synthetic.main.mms_image_preview_list_item.thumbnail
import java.util.concurrent.TimeUnit
import javax.inject.Inject


class AudioBinder @Inject constructor(colors: Colors, private val context: Context) :
    PartBinder() {

    override val partLayout = R.layout.mms_audio_preview_list_item
    override var theme = colors.theme()
    private var thisPlayingState = QkMediaPlayer.PlayingState.Stopped

    override fun canBindPart(part: MmsPart) = part.isAudio()

    override fun bindPart(
        holder: QkViewHolder,
        part: MmsPart,
        message: Message,
        canGroupWithPrevious: Boolean,
        canGroupWithNext: Boolean
    ) {
        // play/pause button click handling
        holder.playPause.setOnClickListener { view ->
            var seekBarUpdater: Disposable? = null

            when (thisPlayingState) {
                QkMediaPlayer.PlayingState.Stopped -> {
                    QkMediaPlayer.apply {
                        reset() // make sure reset before trying to (re-)use

                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)     // music, maybe?? could be voice. don't want to use CONTENT_TYPE_UNKNOWN though
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        )

                        setDataSource(context, part.getUri())

                        setOnPreparedListener {
                            // start timer to update seek bar
                            seekBarUpdater = Observable.interval(500, TimeUnit.MILLISECONDS)
                                .subscribeOn(Schedulers.single())
                                .observeOn(AndroidSchedulers.mainThread())
                                .map { System.nanoTime() }
                                .doOnNext { holder.seekBar.progress = QkMediaPlayer.currentPosition }
                                .subscribe()

                            // set seek bar max value
                            holder.seekBar.max = QkMediaPlayer.duration

                            start()

                            thisPlayingState = QkMediaPlayer.PlayingState.Playing
                            view.playPause.setImageResource(R.drawable.exo_icon_pause)
                            holder.containerView.seekBar.isEnabled = true
                            holder.metadataTitle.isSelected = true
                        }

                        setOnCompletionListener {   // also called on error because we don't have an onerrorlistener
                            seekBarUpdater?.dispose()   // stop timer

                            thisPlayingState = QkMediaPlayer.PlayingState.Stopped
                            holder.containerView.seekBar.seekBar.isEnabled = false
                            holder.containerView.seekBar.seekBar.progress = 0
                            view.playPause.setImageResource(R.drawable.exo_icon_play)
                            holder.metadataTitle.isSelected = false
                        }

                        // prepare to play
                        prepareAsync()
                    }
                }
                QkMediaPlayer.PlayingState.Playing -> {
                    QkMediaPlayer.pause()
                    view.playPause.setImageResource(R.drawable.exo_icon_play)
                    thisPlayingState = QkMediaPlayer.PlayingState.Paused
                }
                QkMediaPlayer.PlayingState.Paused -> {
                    QkMediaPlayer.start()
                    view.playPause.setImageResource(R.drawable.exo_icon_pause)
                    thisPlayingState = QkMediaPlayer.PlayingState.Playing
                }
            }
        }

        holder.thumbnail.bubbleStyle = when {
            !canGroupWithPrevious && canGroupWithNext ->
                if (message.isMe()) BubbleImageView.Style.OUT_FIRST else BubbleImageView.Style.IN_FIRST
            canGroupWithPrevious && canGroupWithNext ->
                if (message.isMe()) BubbleImageView.Style.OUT_MIDDLE else BubbleImageView.Style.IN_MIDDLE
            canGroupWithPrevious && !canGroupWithNext ->
                if (message.isMe()) BubbleImageView.Style.OUT_LAST else BubbleImageView.Style.IN_LAST
            else -> BubbleImageView.Style.ONLY
        }

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

        val metaDataRetriever = MediaMetadataRetriever()
        metaDataRetriever.setDataSource(context, part.getUri())

        // sound wave
        holder.soundWave.setTint(primaryColor)

        // seek bar
        holder.seekBar.apply {
            setTint(secondaryColor)
            thumbTintList = ColorStateList.valueOf(secondaryColor)
            max = 0
            isEnabled = false
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(p0: SeekBar?, progress: Int, fromUser: Boolean) {
                    if ((thisPlayingState != QkMediaPlayer.PlayingState.Stopped) && fromUser)
                        QkMediaPlayer.seekTo(progress)
                }
                override fun onStartTrackingTouch(p0: SeekBar?) { /* nothing */ }
                override fun onStopTrackingTouch(p0: SeekBar?) { /* nothing */ }
            })
        }

        // playPause button
        holder.playPause. apply {
            setTint(secondaryColor)
            setBackgroundTint(primaryColor)
        }

        holder.metadataTitle.apply {
            // metadata title
            text = metaDataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""

            if (text == "")
                visibility = View.GONE
            else {
                setTextColor(primaryColor)
                setBackgroundTint(0xccffffff.toInt() and secondaryColor)    // hex value is alpha
            }
        }

        // embedded image
        val embeddedPicture = metaDataRetriever.embeddedPicture
        if (embeddedPicture == null) {
            holder.thumbnail.setTint(secondaryColor)
            holder.frame.layoutParams.height /= 2
            holder.thumbnail.requestLayout()
        } else {
            GlideApp.with(context)
                .asBitmap()
                .load(embeddedPicture)
                .into(holder.thumbnail)
        }

        metaDataRetriever.close()
    }
}