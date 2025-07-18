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
package dev.octoshrimpy.quik.interactor

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.net.toUri
import dev.octoshrimpy.quik.extensions.getName
import dev.octoshrimpy.quik.repository.ScheduledMessageRepository
import io.reactivex.Flowable
import io.realm.RealmList
import java.io.File
import java.util.UUID
import javax.inject.Inject

class AddScheduledMessage @Inject constructor(
    private val scheduledMessageRepo: ScheduledMessageRepository,
    private val updateScheduledMessageAlarms: UpdateScheduledMessageAlarms,
    private val context: Context,
) : Interactor<AddScheduledMessage.Params>() {

    data class Params(
        val date: Long,
        val subId: Int,
        val recipients: List<String>,
        val sendAsGroup: Boolean,
        val body: String,
        val attachments: List<String>,
        val conversationId: Long
    )

    @SuppressLint("Range")
    override fun buildObservable(params: Params): Flowable<*> {
        // have to 3-step save scheduled message
        return Flowable.just(params)
            .map {  // step 1 - save, as-is, to db to get primary key id
                scheduledMessageRepo.saveScheduledMessage(
                    it.date,
                    it.subId,
                    it.recipients,
                    it.sendAsGroup,
                    it.body,
                    it.attachments,
                    it.conversationId
                )
            }
            .map { scheduledMessageDb ->
                // step 2 - copy attachments to app local storage
                scheduledMessageDb.attachments = RealmList(
                    *scheduledMessageDb.attachments.mapNotNull {
                        val inUri = it.toUri()
                        try {
                            // get filename of input uri or use random uuid on fail to get
                            val filename = inUri.getName(context) ?: UUID.randomUUID()

                            // copy attachment data to app local dir - first, create dir
                            val localFile = File(
                                context.filesDir,
                                "scheduled-${scheduledMessageDb.id}/${UUID.randomUUID()}/${filename}"
                            )

                            // create directory tree in app local storage
                            localFile.parentFile?.mkdirs()

                            val localUri = localFile.toUri()

                            // copy attachment resource data to local file
                            context.contentResolver.openOutputStream(localUri, "w")
                                ?.use { outputStream ->
                                    context.contentResolver.openInputStream(inUri)
                                        ?.use { it.copyTo(outputStream, 4096) }
                                }

                            localUri.toString()
                        } catch (e: Exception) {
                            it  // on any error, use original uri string
                        }
                    }.toTypedArray()
                )

                // step 3 - update scheduled message with new attachment uris
                scheduledMessageRepo.updateScheduledMessage(scheduledMessageDb)
            }
            .flatMap { updateScheduledMessageAlarms.buildObservable(Unit) }
    }
}
