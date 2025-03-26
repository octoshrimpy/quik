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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import dev.octoshrimpy.quik.R

class ClipboardUtils {
    companion object {
        fun copy(context: Context, string: String) {
            try {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("SMS", string))
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    if ((e is RuntimeException) &&
                        e.message?.startsWith("android.os.TransactionTooLargeException") == true
                    ) R.string.clipboard_too_large_to_copy
                    else R.string.clipboard_unable_to_copy_to,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

}
