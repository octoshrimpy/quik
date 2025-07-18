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
package dev.octoshrimpy.quik.interactor

import android.annotation.SuppressLint
import android.content.Context
import dev.octoshrimpy.quik.repository.ScheduledMessageRepository
import io.reactivex.Flowable
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class DeleteScheduledMessages @Inject constructor(
    private val scheduledMessageRepo: ScheduledMessageRepository,
    private val context: Context,
) : Interactor<List<Long>>() {

    @SuppressLint("Range")
    override fun buildObservable(scheduledMessageIds: List<Long>): Flowable<*> {
        return Flowable.just(scheduledMessageIds)
            .map {
                try {
                    // for each message id to delete
                    it.forEach {
                        // recursively delete scheduled message top level dir
                        val topDir = File(context.filesDir, "scheduled-${it}")
                        topDir.exists() && topDir.deleteRecursively()
                    }
                } catch (e: Exception) {
                    Timber.e("Unable to delete scheduled messages.")
                }

                // delete the db entries
                scheduledMessageRepo.deleteScheduledMessages(it)
            }
    }
}
