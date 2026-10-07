package com.arkhins.ctrlaps.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build

/**
 * Setting an alarm on time when the phone lets us. Android 12+ only allows exact alarms once "Alarms & reminders" is
 * on for the app (Settings → Permissions → Exact reminders); without it the alarm is still set, inexact, and may come
 * a few minutes late while the phone dozes. Nothing here asks for the permission.
 */
object Alarms {
    /** Whether exact alarms are allowed: always below Android 12. */
    fun exactAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return false
        return alarms.canScheduleExactAlarms()
    }

    /**
     * Rings at [atMillis], waking the phone, even while it dozes. [wakeup] false is for things only worth doing once the
     * screen is on anyway (a widget's countdown): it never wakes the phone.
     */
    fun set(context: Context, atMillis: Long, pending: PendingIntent, wakeup: Boolean = true) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val type = if (wakeup) AlarmManager.RTC_WAKEUP else AlarmManager.RTC
        val exact = exactAllowed(context)
        // The permission can be taken back between the check and the call: then it is set inexact after all.
        try {
            when {
                exact && wakeup -> alarms.setExactAndAllowWhileIdle(type, atMillis, pending)
                exact -> alarms.setExact(type, atMillis, pending)
                wakeup -> alarms.setAndAllowWhileIdle(type, atMillis, pending)
                else -> alarms.set(type, atMillis, pending)
            }
        } catch (_: SecurityException) {
            if (wakeup) alarms.setAndAllowWhileIdle(type, atMillis, pending) else alarms.set(type, atMillis, pending)
        }
    }

    fun cancel(context: Context, pending: PendingIntent) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending)
    }
}
