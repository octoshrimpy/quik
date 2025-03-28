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
package dev.octoshrimpy.quik.extensions

import java.text.Normalizer

/**
 * Strip the accents from a string
 */
fun CharSequence.removeAccents(): String = Normalizer.normalize(this, Normalizer.Form.NFKD).replace(Regex("\\p{M}"), "")

fun String.joinTo(rhs:String, separator: String) =
    when {
        this.isEmpty() -> rhs
        rhs.isEmpty() -> this
        else -> "$this$separator$rhs"
}

fun String.truncateWithEllipses(maxLengthIncEllipses: Int) =
    when (this.length) {
        in 0..maxLengthIncEllipses -> this
        else -> this.take(maxLengthIncEllipses - 1) + "…"
    }
