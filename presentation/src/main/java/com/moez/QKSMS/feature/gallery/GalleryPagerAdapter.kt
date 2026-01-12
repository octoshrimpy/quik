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
package dev.octoshrimpy.quik.feature.gallery

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.google.android.mms.ContentType
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkRealmAdapter
import dev.octoshrimpy.quik.common.base.QkViewHolder
import dev.octoshrimpy.quik.databinding.GalleryImagePageBinding
import dev.octoshrimpy.quik.databinding.GalleryVideoPageBinding
import dev.octoshrimpy.quik.extensions.isImage
import dev.octoshrimpy.quik.extensions.isVideo
import dev.octoshrimpy.quik.model.MmsPart
import dev.octoshrimpy.quik.util.GlideApp
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import java.util.*
import javax.inject.Inject

class GalleryPagerAdapter @Inject constructor(private val context: Context) : QkRealmAdapter<MmsPart, QkViewHolder>() {

    companion object {
        private const val VIEW_TYPE_INVALID = 0
        private const val VIEW_TYPE_IMAGE = 1
        private const val VIEW_TYPE_VIDEO = 2
    }

    val clicks: Subject<View> = PublishSubject.create()

    private val contentResolver = context.contentResolver
    private val exoPlayers = Collections.newSetFromMap(WeakHashMap<ExoPlayer?, Boolean>())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return QkViewHolder(when (viewType) {
            VIEW_TYPE_IMAGE -> {
                val binding = GalleryImagePageBinding.inflate(inflater, parent, false)
                // When calling the public setter, it doesn't allow the midscale to be the same as the
                // maxscale or the minscale. We don't want 3 levels and we don't want to modify the library
                // so let's celebrate the invention of reflection!
                binding.image.attacher.run {
                    javaClass.getDeclaredField("mMinScale").run {
                        isAccessible = true
                        setFloat(binding.image.attacher, 1f)
                    }
                    javaClass.getDeclaredField("mMidScale").run {
                        isAccessible = true
                        setFloat(binding.image.attacher, 1f)
                    }
                    javaClass.getDeclaredField("mMaxScale").run {
                        isAccessible = true
                        setFloat(binding.image.attacher, 3f)
                    }
                }
                binding.root
            }

            VIEW_TYPE_VIDEO -> inflater.inflate(R.layout.gallery_video_page, parent, false)

            else -> inflater.inflate(R.layout.gallery_invalid_page, parent, false)

        }.apply { setOnClickListener(clicks::onNext) })
    }

    override fun onBindViewHolder(holder: QkViewHolder, position: Int) {
        val part = getItem(position) ?: return
        when (getItemViewType(position)) {
            VIEW_TYPE_IMAGE -> {
                val binding = GalleryImagePageBinding.bind(holder.itemView)
                // We need to explicitly request a gif from glide for animations to work
                when (part.getUri().let(contentResolver::getType)) {
                    ContentType.IMAGE_GIF -> GlideApp.with(context)
                            .asGif()
                            .load(part.getUri())
                            .into(binding.image)

                    else -> GlideApp.with(context)
                            .asBitmap()
                            .load(part.getUri())
                            .into(binding.image)
                }
            }

            VIEW_TYPE_VIDEO -> {
                val binding = GalleryVideoPageBinding.bind(holder.itemView)
                val videoTrackSelectionFactory = AdaptiveTrackSelection.Factory(null)
                val trackSelector = DefaultTrackSelector(videoTrackSelectionFactory)
                val exoPlayer = ExoPlayerFactory.newSimpleInstance(context, trackSelector)
                binding.video.player = exoPlayer
                exoPlayers.add(exoPlayer)

                val dataSourceFactory = DefaultDataSourceFactory(context, Util.getUserAgent(context, "QUIK"))
                val videoSource = ExtractorMediaSource.Factory(dataSourceFactory).createMediaSource(part.getUri())
                exoPlayer?.prepare(videoSource)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val part = getItem(position)
        return when {
            part?.isImage() == true -> VIEW_TYPE_IMAGE
            part?.isVideo() == true -> VIEW_TYPE_VIDEO
            else -> VIEW_TYPE_INVALID
        }
    }

    fun destroy() {
        exoPlayers.forEach { exoPlayer -> exoPlayer?.release() }
    }

}
