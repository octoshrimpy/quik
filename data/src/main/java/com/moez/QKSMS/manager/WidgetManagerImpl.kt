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
package dev.octoshrimpy.quik.manager

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.klinker.android.send_message.BroadcastUtils
import dev.octoshrimpy.quik.util.Preferences
import dev.octoshrimpy.quik.util.nonDebugPackageName
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

class WidgetManagerImpl @Inject constructor(private val context: Context, prefs: Preferences)
    : WidgetManager {
    companion object {
        private var staticUnreadAtTopPrefsDisposable = AtomicReference<Disposable>(null)

        fun sendDatasetChanged(context: Context) {
            BroadcastUtils.sendExplicitBroadcast(context, Intent(), "${context.packageName}.${WidgetManager.ACTION_NOTIFY_DATASET_CHANGED}")
        }
    }

    init {
        // this class is always instantiated by it's factory with the application context so we can capture that context value below
        // when unreadAtTop preference changes, send broadcast to widgets to update themselves
        if (staticUnreadAtTopPrefsDisposable.get() === null)
            staticUnreadAtTopPrefsDisposable.set(prefs.unreadAtTop.asObservable()
                .skip(1)
                .debounce(400, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext { sendDatasetChanged(context) }
                .subscribe())
    }

    override fun sendDatasetChanged() {
        sendDatasetChanged(context)
    }

    override fun updateTheme() {
        val ids = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(
                ComponentName(
                    context.packageName,
                    "${nonDebugPackageName(context.packageName)}.feature.widget.WidgetProvider"
                )
            )

        val intent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)

        BroadcastUtils.sendExplicitBroadcast(context, intent, AppWidgetManager.ACTION_APPWIDGET_UPDATE)
    }

}