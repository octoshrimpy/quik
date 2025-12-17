/*
 * Copyright (C) 2025
 *
 * This file is part of QUIK.
 *
 * QUIK is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QUIK is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QUIK.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.octoshrimpy.quik.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dev.octoshrimpy.quik.blocking.BlockingClient
import dev.octoshrimpy.quik.interactor.UpdateBadge
import dev.octoshrimpy.quik.manager.ActiveConversationManager
import dev.octoshrimpy.quik.manager.NotificationManager
import dev.octoshrimpy.quik.manager.ShortcutManager
import dev.octoshrimpy.quik.repository.ContactRepository
import dev.octoshrimpy.quik.repository.ConversationRepository
import dev.octoshrimpy.quik.repository.MessageContentFilterRepository
import dev.octoshrimpy.quik.repository.MessageRepository
import dev.octoshrimpy.quik.repository.ScheduledMessageRepository
import dev.octoshrimpy.quik.repository.SyncRepository
import dev.octoshrimpy.quik.util.Preferences
import javax.inject.Inject

class InjectionWorkerFactory @Inject constructor(
    private val conversationRepo: ConversationRepository,
    private val blockingClient: BlockingClient,
    private val prefs: Preferences,
    private val messageRepo: MessageRepository,
    private val updateBadge: UpdateBadge,
    private val shortcutManager: ShortcutManager,
    private val scheduledMessageRepository: ScheduledMessageRepository,
    private val notificationManager: NotificationManager,
    private val activeConversationManager: ActiveConversationManager,
    private val syncRepo: SyncRepository,
    private val filterRepo: MessageContentFilterRepository,
    private val contactRepo: ContactRepository,

) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        val instance = Class
            .forName(workerClassName)
            .asSubclass(Worker::class.java)
            .getDeclaredConstructor(Context::class.java, WorkerParameters::class.java)
            .newInstance(appContext, workerParameters)

        when (instance) {
            is HousekeepingWorker ->
                instance.scheduledMessageRepository = scheduledMessageRepository
            is ReceiveSmsWorker -> {
                instance.conversationRepo  = conversationRepo
                instance.blockingClient = blockingClient
                instance.prefs = prefs
                instance.messageRepo = messageRepo
                instance.shortcutManager = shortcutManager
                instance.notificationManager = notificationManager
                instance.updateBadge =  updateBadge
                instance.filterRepo = filterRepo
                instance.contactsRepo = contactRepo
            }
            is ReceiveMmsWorker -> {
                instance.syncRepo = syncRepo
                instance.activeConversationManager = activeConversationManager
                instance.conversationRepo = conversationRepo
                instance.blockingClient = blockingClient
                instance.prefs = prefs
                instance.messageRepo = messageRepo
                instance.shortcutManager = shortcutManager
                instance.notificationManager = notificationManager
                instance.updateBadge = updateBadge
                instance.filterRepo = filterRepo
                instance.contactsRepo = contactRepo
            }
        }

        return instance
    }
}