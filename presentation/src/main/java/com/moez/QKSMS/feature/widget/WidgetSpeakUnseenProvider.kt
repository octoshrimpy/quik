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
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.receiver.SpeakThreadsReceiver

class WidgetSpeakUnseenProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        for (appWidgetId in appWidgetIds)
            updateWidget(context, appWidgetId)
    }

    private fun updateWidget(context: Context?, appWidgetId: Int) {
        super.onEnabled(context)

        if (context == null)
            return

        val remoteViews = RemoteViews(context.packageName, R.layout.widget_speak_unseen)

        remoteViews.setImageViewResource(R.id.speakUnseenImage, R.drawable.ic_speak_unseen_widget)

        // speak unseen intent
        val speakUnseenPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, SpeakThreadsReceiver::class.java)
                .putExtra("threadId", -1L),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        remoteViews.setOnClickPendingIntent(R.id.speakUnseenImage, speakUnseenPendingIntent)

        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, remoteViews)
    }

}
