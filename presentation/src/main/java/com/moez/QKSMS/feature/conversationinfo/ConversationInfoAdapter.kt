package dev.octoshrimpy.quik.feature.conversationinfo

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.jakewharton.rxbinding2.view.clicks
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkAdapter
import dev.octoshrimpy.quik.common.base.QkViewHolder
import dev.octoshrimpy.quik.common.util.Colors
import dev.octoshrimpy.quik.common.util.extensions.setTint
import dev.octoshrimpy.quik.common.util.extensions.setVisible
import dev.octoshrimpy.quik.databinding.ConversationInfoSettingsBinding
import dev.octoshrimpy.quik.databinding.ConversationMediaListItemBinding
import dev.octoshrimpy.quik.databinding.ConversationRecipientListItemBinding
import dev.octoshrimpy.quik.extensions.isVideo
import dev.octoshrimpy.quik.feature.conversationinfo.ConversationInfoItem.*
import dev.octoshrimpy.quik.util.GlideApp
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

class ConversationInfoAdapter @Inject constructor(
    private val context: Context,
    private val colors: Colors
) : QkAdapter<ConversationInfoItem, QkViewHolder>() {

    val recipientClicks: Subject<Long> = PublishSubject.create()
    val recipientLongClicks: Subject<Long> = PublishSubject.create()
    val themeClicks: Subject<Long> = PublishSubject.create()
    val nameClicks: Subject<Unit> = PublishSubject.create()
    val notificationClicks: Subject<Unit> = PublishSubject.create()
    val markUnreadClicks: Subject<Unit> = PublishSubject.create()
    val archiveClicks: Subject<Unit> = PublishSubject.create()
    val blockClicks: Subject<Unit> = PublishSubject.create()
    val deleteClicks: Subject<Unit> = PublishSubject.create()
    val mediaClicks: Subject<Long> = PublishSubject.create()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> {
                val binding = ConversationRecipientListItemBinding.inflate(inflater, parent, false)
                QkViewHolder(binding.root).apply {
                    itemView.setOnClickListener {
                        val item = getItem(adapterPosition) as? ConversationInfoRecipient
                        item?.value?.id?.run(recipientClicks::onNext)
                    }

                    itemView.setOnLongClickListener {
                        val item = getItem(adapterPosition) as? ConversationInfoRecipient
                        item?.value?.id?.run(recipientLongClicks::onNext)
                        true
                    }

                    binding.theme.setOnClickListener {
                        val item = getItem(adapterPosition) as? ConversationInfoRecipient
                        item?.value?.id?.run(themeClicks::onNext)
                    }
                }
            }

            1 -> {
                val binding = ConversationInfoSettingsBinding.inflate(inflater, parent, false)
                QkViewHolder(binding.root).apply {
                    binding.groupName.clicks().subscribe(nameClicks)
                    binding.notifications.clicks().subscribe(notificationClicks)
                    binding.markUnread.clicks().subscribe(markUnreadClicks)
                    binding.archive.clicks().subscribe(archiveClicks)
                    binding.block.clicks().subscribe(blockClicks)
                    binding.delete.clicks().subscribe(deleteClicks)
                }
            }

            2 -> {
                val binding = ConversationMediaListItemBinding.inflate(inflater, parent, false)
                QkViewHolder(binding.root).apply {
                    itemView.setOnClickListener {
                        val item = getItem(adapterPosition) as? ConversationInfoMedia
                        item?.value?.id?.run(mediaClicks::onNext)
                    }
                }
            }

            else -> throw IllegalStateException()
        }
    }

    override fun onBindViewHolder(holder: QkViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ConversationInfoRecipient -> {
                val binding = ConversationRecipientListItemBinding.bind(holder.itemView)
                val recipient = item.value
                binding.avatar.setRecipient(recipient)

                binding.name.text = recipient.contact?.name ?: recipient.address

                binding.address.text = recipient.address
                binding.address.setVisible(recipient.contact != null)

                binding.add.setVisible(recipient.contact == null)

                val theme = colors.theme(recipient)
                binding.theme.setTint(theme.theme)
            }

            is ConversationInfoSettings -> {
                val binding = ConversationInfoSettingsBinding.bind(holder.itemView)
                binding.groupName.summary = item.name

                binding.notifications.isEnabled = !item.blocked

                binding.archive.isEnabled = !item.blocked
                binding.archive.title = context.getString(when (item.archived) {
                    true -> R.string.info_unarchive
                    false -> R.string.info_archive
                })

                binding.block.title = context.getString(when (item.blocked) {
                    true -> R.string.info_unblock
                    false -> R.string.info_block
                })
            }

            is ConversationInfoMedia -> {
                val binding = ConversationMediaListItemBinding.bind(holder.itemView)
                val part = item.value

                GlideApp.with(context)
                        .load(part.getUri())
                        .fitCenter()
                        .into(binding.thumbnail)

                binding.video.isVisible = part.isVideo()
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (data[position]) {
            is ConversationInfoRecipient -> 0
            is ConversationInfoSettings -> 1
            is ConversationInfoMedia -> 2
        }
    }

    override fun areItemsTheSame(old: ConversationInfoItem, new: ConversationInfoItem): Boolean {
        return when {
            old is ConversationInfoRecipient && new is ConversationInfoRecipient -> {
               old.value.id == new.value.id
            }

            old is ConversationInfoSettings && new is ConversationInfoSettings -> {
                true
            }

            old is ConversationInfoMedia && new is ConversationInfoMedia -> {
                old.value.id == new.value.id
            }

            else -> false
        }
    }

}
