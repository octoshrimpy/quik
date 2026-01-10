/*
 * Copyright (C) 2019 Moez Bhatti <moez.bhatti@gmail.com>
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
package dev.octoshrimpy.quik.util

import timber.log.Timber
import java.security.MessageDigest

private val sha256Digest = object : ThreadLocal<MessageDigest>() {
    override fun initialValue(): MessageDigest {
        return MessageDigest.getInstance("SHA-256")
    }
}

fun <T> tryOrNull(logOnError: Boolean = true, body: () -> T?): T? {
    return try {
        body()
    } catch (e: Exception) {
        if (logOnError) {
            Timber.w(e)
        }

        null
    }
}

fun nonDebugPackageName(packageName: String): String {
    return if (packageName.endsWith(".debug")) packageName.removeSuffix(".debug")
    else packageName
}

fun sha256(input: String): String {
    val digest = sha256Digest.get()
    digest!!.reset()

    val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
    return hashBytes.joinToString("") { String.format("%02x", it) }
}
