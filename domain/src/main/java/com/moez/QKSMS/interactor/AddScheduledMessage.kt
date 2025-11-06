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
import android.net.Uri
import androidx.core.net.toUri
import dev.octoshrimpy.quik.extensions.getName
import dev.octoshrimpy.quik.extensions.getResourceBytes
import dev.octoshrimpy.quik.repository.ScheduledMessageRepository
import dev.octoshrimpy.quik.util.Constants
import dev.octoshrimpy.quik.util.FileUtils
import io.reactivex.Flowable
import io.realm.RealmList
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
        val attachments: List<Uri>,
        val conversationId: Long
    )

    @SuppressLint("Range")
    override fun buildObservable(params: Params): Flowable<*> {
        // have to 3-step save scheduled message
        return Flowable.just(params)
            .map {  // step 1 - save, without attachments, to db to get primary key id which is
                    // needed for building the save file location dir name in step 2
                scheduledMessageRepo.saveScheduledMessage(
                    it.date,
                    it.subId,
                    it.recipients,
                    it.sendAsGroup,
                    it.body,
                    listOf(),
                    it.conversationId
                )
            }
            .map { scheduledMessage ->
                // step 2 - copy attachments to app local storage
                scheduledMessage.attachments = RealmList(
                    *params.attachments.map { attachmentUri ->
                        try {
                            // get filename of input uri or use random uuid on fail to get
                            val filename = attachmentUri.getName(context) ?: UUID.randomUUID()

                            val (localUri, e) = FileUtils.createAndWrite(
                                context,
                                FileUtils.Location.Files,  // files dir so not deleted if app cache wiped by user
                                "${Constants.SCHEDULED_MESSAGE_FILE_PREFIX}-${scheduledMessage.id}/" +
                                                    "${UUID.randomUUID()}/${filename}",
                                "",
                                attachmentUri.getResourceBytes(context)
                            )
                            if (e is Exception)
                                throw e

                            localUri.toString()
                        } catch (e: Exception) {
                            attachmentUri.toString()  // on any error, use original uri string
                        }
                    }.toTypedArray()
                )

                // step 3 - update scheduled message with new attachment uris
                scheduledMessageRepo.updateScheduledMessage(scheduledMessage)
            }
            .flatMap { updateScheduledMessageAlarms.buildObservable(Unit) }
    }
}
