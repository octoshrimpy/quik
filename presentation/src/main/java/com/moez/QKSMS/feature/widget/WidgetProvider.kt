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

package dev.octoshrimpy.quik.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.RemoteViews
import dagger.android.AndroidInjection
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.util.Colors
import dev.octoshrimpy.quik.common.util.extensions.getColorCompat
import dev.octoshrimpy.quik.feature.compose.ComposeActivity
import dev.octoshrimpy.quik.feature.main.MainActivity
import dev.octoshrimpy.quik.manager.WidgetManager
import dev.octoshrimpy.quik.receiver.StartActivityFromWidgetReceiver
import dev.octoshrimpy.quik.util.Preferences
import timber.log.Timber
import javax.inject.Inject
import androidx.core.net.toUri

class WidgetProvider : AppWidgetProvider() {

    @Inject lateinit var colors: Colors
    @Inject lateinit var prefs: Preferences

    override fun onReceive(context: Context, intent: Intent) {
        AndroidInjection.inject(this, context)

        when (intent.action) {
            "${context.packageName}.${WidgetManager.ACTION_NOTIFY_DATASET_CHANGED}" -> updateData(context)
            else -> super.onReceive(context, intent)
        }
    }

    /**
     * Update all widgets in the list
     */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        updateData(context)
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetId, isSmallWidget(appWidgetManager, appWidgetId))
        }
    }

    /**
     * Notify all the widgets that they should update their adapter data
     */
    private fun updateData(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, WidgetProvider::class.java))

        // We need to update all Mms appwidgets on the home screen.
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.conversations)
    }

    /**
     * Update widget when widget size changes
     */
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        updateWidget(context, appWidgetId, isSmallWidget(appWidgetManager, appWidgetId))
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    /**
     * Returns true when widget has less than 4 columns, else false
     */
    private fun isSmallWidget(appWidgetManager: AppWidgetManager, appWidgetId: Int): Boolean {
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val size = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)

        var n = 2
        while (70 * n - 30 < size) {
            ++n
        }

        val columns = n - 1

        return columns < 4
    }

    /**
     * Update the widget appWidgetId
     */
    private fun updateWidget(context: Context, appWidgetId: Int, smallWidget: Boolean) {
        Timber.v("updateWidget appWidgetId: $appWidgetId")
        val remoteViews = RemoteViews(context.packageName, R.layout.widget)

        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isNightMode = nightModeFlags == Configuration.UI_MODE_NIGHT_YES

        // Apply colors from theme
        val night = prefs.night.get() || isNightMode
        val black = prefs.black.get()

        remoteViews.setInt(R.id.background, "setColorFilter", context.getColorCompat(when {
            night && black -> R.color.black
            night && !black -> R.color.backgroundDark
            else -> R.color.white
        }))

        remoteViews.setInt(R.id.toolbar, "setColorFilter", context.getColorCompat(when {
            night && black -> R.color.black
            night && !black -> R.color.backgroundDark
            else -> R.color.backgroundLight
        }))

        remoteViews.setTextColor(R.id.title, context.getColorCompat(when (night) {
            true -> R.color.textPrimaryDark
            false -> R.color.textPrimary
        }))

        // Set adapter for conversations
        val intent = Intent(context, WidgetService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .putExtra("small_widget", smallWidget)
        intent.data = intent.toUri(Intent.URI_INTENT_SCHEME).toUri()
        remoteViews.setRemoteAdapter(R.id.conversations, intent)

        // compose new message image color and on click intent
        remoteViews.setInt(R.id.compose, "setColorFilter", colors.theme().theme)
        remoteViews.setOnClickPendingIntent(
            R.id.compose,
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, ComposeActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        // use a single pending intent for launching main activity because they
        // can sometimes be in short supply in the OS
        val mainActivityPendingIntent = PendingIntent.getActivity(
            context,
            99,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // title click to load app main activity / conversation view
        remoteViews.setOnClickPendingIntent(R.id.title, mainActivityPendingIntent)

        // pending intent template to be used for all list items
        remoteViews.setPendingIntentTemplate(
            R.id.conversations,
            PendingIntent.getBroadcast(
                context,
                1,
                Intent(context, StartActivityFromWidgetReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        )

        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, remoteViews)
    }

}
