package com.arkhins.ctrlaps.reminders

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.arkhins.ctrlaps.CtrlapsApplication
import com.arkhins.ctrlaps.data.EventReminders
import com.arkhins.ctrlaps.data.UpcomingEventsResponse
import com.arkhins.ctrlaps.widgets.Widgets
import kotlinx.coroutines.launch

/**
 * Alarms don't outlive a restart of the phone or an update of the app, and those set inexact stay inexact once exact
 * ones are allowed. Each of these sets every reminder again from the phone's copy, without waiting for the next
 * background run or a network, and redraws the widgets.
 */
class RemindersBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> Unit
            else -> return
        }
        val app = context.applicationContext as? CtrlapsApplication ?: return
        val done = goAsync()
        app.appScope.launch {
            try {
                runCatching { SessionReminders.syncFromCache(app) }
                runCatching {
                    val events = if (app.session.signedIn) app.store.read("/api/events/upcoming", UpcomingEventsResponse.serializer())?.events.orEmpty() else emptyList()
                    EventReminders.sync(app, events)
                }
                runCatching { Widgets.updateAll(app) }
            } finally {
                done.finish()
            }
        }
    }
}
