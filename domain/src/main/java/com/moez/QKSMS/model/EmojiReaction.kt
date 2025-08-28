/*
 * Copyright (C) 2025
 *
 * This file is part of QUIK.
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
package dev.octoshrimpy.quik.model

import io.realm.RealmObject
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey

open class EmojiReaction : RealmObject() {
    @PrimaryKey var id: Long = 0

    /** The reaction message ID itself */
    @Index var reactionMessageId: Long = 0

    /** The sender's address (phone number) */
    var senderAddress: String = ""

    /** The emoji used in the reaction */
    var emoji: String = ""

    /** The original message text that was reacted to */
    var originalMessageText: String = ""

    /** Thread ID for easier querying */
    @Index var threadId: Long = 0
}
