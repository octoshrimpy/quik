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
import dev.octoshrimpy.quik.interactor.ReceiveSms
import dev.octoshrimpy.quik.repository.ScheduledMessageRepository
import javax.inject.Inject

class InjectionWorkerFactory @Inject constructor(
    private val receiveSms: ReceiveSms,
    private val scheduledMessageRepository: ScheduledMessageRepository
)
: WorkerFactory() {
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
            is HousekeepingWorker -> {
                instance.scheduledMessageRepository = scheduledMessageRepository
            }
            is ReceiveSmsWorker -> {
                instance.receiveSms = receiveSms
            }
        }

        return instance
    }
}