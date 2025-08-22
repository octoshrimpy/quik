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
package dev.octoshrimpy.quik.common.util

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.Build
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import dev.octoshrimpy.quik.common.util.extensions.getThemedIcon
import dev.octoshrimpy.quik.common.util.extensions.toPerson
import dev.octoshrimpy.quik.feature.compose.ComposeActivity
import dev.octoshrimpy.quik.model.Conversation
import dev.octoshrimpy.quik.repository.ConversationRepository
import dev.octoshrimpy.quik.repository.MessageRepository
import me.leolin.shortcutbadger.ShortcutBadger
import timber.log.Timber
import javax.inject.Inject

class ShortcutManagerImpl @Inject constructor(
    private val context: Context,
    private val conversationRepo: ConversationRepository,
    private val messageRepo: MessageRepository,
    private val colors: Colors
) : dev.octoshrimpy.quik.manager.ShortcutManager {

    override fun updateBadge() {
        val count = messageRepo.getUnreadCount().toInt()
        ShortcutBadger.applyCount(context, count)
    }

    override fun updateShortcuts() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val shortcutManager = context.getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager
            if (shortcutManager.isRateLimitingActive) return

            val shortcuts: List<ShortcutInfoCompat> = conversationRepo.getTopConversations()
                    .take(shortcutManager.maxShortcutCountPerActivity - shortcutManager.manifestShortcuts.size)
                    .map { conversation -> createShortcutForConversation(conversation) }

            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }
    }

    /**
     * Get the shortcut for a threadId. Will create it if it doesn't exist.
     */
    override fun getShortcut(threadId: Long): ShortcutInfoCompat? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            var sc = getShortcuts().find { it.id == threadId.toString() }
            if(sc != null)
                sc = updateShortcut(sc)
            if (sc == null)  {
                val conv = conversationRepo.getConversation(threadId)
                if (conv == null)
                    return null
                else
                    sc =  createShortcutForConversation(conv)
            }
            return sc
        } else {
            return null
        }
    }

    /**
     * Report thread usage. Will create the shortcut if it doesn't exist.
     */
    override fun reportShortcutUsed(threadId: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val shortcutManager = context.getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager
            if(getShortcut(threadId ) == null) {
                val conversation = conversationRepo.getOrCreateConversation(threadId)
                if (conversation == null)
                    return
                val shortcut = createShortcutForConversation(conversation)
                ShortcutManagerCompat.setDynamicShortcuts(context, listOf(shortcut))
            }
            shortcutManager.reportShortcutUsed(threadId.toString())
        }
    }

    @TargetApi(29)
    private fun createShortcutForConversation(conversation: Conversation): ShortcutInfoCompat {
        Timber.v("creating shortcut for conversation ${conversation.id}")
        val icon = when {
            conversation.recipients.size == 1 -> {
                val recipient = conversation.recipients.first()!!
                recipient.getThemedIcon(context,
                    colors.theme(recipient),
                    ShortcutManagerCompat.getIconMaxWidth(context),
                    ShortcutManagerCompat.getIconMaxHeight(context)
                )
            }

            else -> {
                conversation.getThemedIcon(context,
                    ShortcutManagerCompat.getIconMaxWidth(context),
                    ShortcutManagerCompat.getIconMaxHeight(context)
                )
            }
        }

        val persons: Array<Person> = conversation.recipients.map { it -> it.toPerson(context, colors) }.toTypedArray();

        val intent = Intent(context, ComposeActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra("threadId", conversation.id)
                .putExtra("fromShortcut", true)

        val sc = ShortcutInfoCompat.Builder(context, "${conversation.id}")
                .setShortLabel(conversation.getTitle())
                .setLongLabel(conversation.getTitle())
                .setIcon(icon)
                .setIntent(intent)
                .setPersons(persons)
                .setLongLived(true)
                .build()

        ShortcutManagerCompat.pushDynamicShortcut(context, sc)
        return sc
    }

    private fun updateShortcut(shortcut: ShortcutInfoCompat): ShortcutInfoCompat {
        val conversation = conversationRepo.getConversation(shortcut.id.toLong())
        if (conversation == null)
            return shortcut
        else {
            val sc = createShortcutForConversation(conversation)
            ShortcutManagerCompat.pushDynamicShortcut(context, sc)
            return sc
        }
    }

    private fun getShortcuts() : List<ShortcutInfoCompat> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            return ShortcutManagerCompat.getDynamicShortcuts(context)
        } else {
            return emptyList()
        }
    }
}