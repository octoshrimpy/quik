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
package dev.octoshrimpy.quik.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import dagger.android.AndroidInjection
import dev.octoshrimpy.quik.util.nonDebugPackageName

class StartActivityFromWidgetReceiver : BroadcastReceiver() {
    // why does this shim class exist rather than the widget launching activities more directly?
    // the main widget listview items all need to launch the compose activity at an existing
    // threadId *except* the 'view more conversations' footer item, which needs to launch
    // the main activity. a listview in a widget *must* use a single pending intent template
    // for all items which doesn't allow for more than one component/class. this shim class
    // allows a single component/class for the widget listview pending intent template that, based
    // on the activityToStart value, launches the appropriate activity

    companion object {
        const val COMPOSE_ACTIVITY = ".feature.compose.ComposeActivity"
        const val MAIN_ACTIVITY = ".feature.main.MainActivity"
    }

    override fun onReceive(context: Context, intent: Intent) {
        AndroidInjection.inject(this, context)

        var activityToStartName = intent.getStringExtra("activityToStart")

        // protect against invalid launch activity
        activityToStartName = when (activityToStartName) {
            COMPOSE_ACTIVITY -> activityToStartName
            MAIN_ACTIVITY -> activityToStartName
            else -> return
        }

        context.startActivity(
            Intent(context, Class.forName(nonDebugPackageName(context.packageName) + activityToStartName))
                .setFlags(FLAG_ACTIVITY_NEW_TASK)
                .putExtra("threadId", intent.getLongExtra("threadId", 0L))
        )
    }

}