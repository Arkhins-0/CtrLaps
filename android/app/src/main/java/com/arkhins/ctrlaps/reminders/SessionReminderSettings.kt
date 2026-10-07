package com.arkhins.ctrlaps.reminders

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Race-session reminders as this phone has them set: on or off (on to start), how long before a session (15 minutes
 * to start), and whose sessions: your own categories' and those for everyone, or every session. Kept on the phone
 * only; each change sets the reminders again at once.
 */
object SessionReminderSettings {
    /** The lead times offered, in minutes. */
    val choices = listOf(5, 10, 15, 30, 60)

    private const val PREFS = "session_reminders_settings"
    private var prefs: SharedPreferences? = null

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled
    private val _minutes = MutableStateFlow(15)
    val minutes: StateFlow<Int> = _minutes
    private val _everySession = MutableStateFlow(false)
    val everySession: StateFlow<Boolean> = _everySession

    /** Reads what was saved; cheap to call again (alarms, the boot receiver and the page each call it first). */
    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { p ->
            _enabled.value = p.getBoolean("enabled", true)
            _minutes.value = p.getInt("minutes", 15).takeIf { it in choices } ?: 15
            _everySession.value = p.getBoolean("every", false)
        }
    }

    fun setEnabled(context: Context, on: Boolean) {
        init(context)
        _enabled.value = on
        prefs?.edit()?.putBoolean("enabled", on)?.apply()
        SessionReminders.resync(context)
    }

    fun setMinutes(context: Context, minutes: Int) {
        if (minutes !in choices) return
        init(context)
        _minutes.value = minutes
        prefs?.edit()?.putInt("minutes", minutes)?.apply()
        SessionReminders.resync(context)
    }

    fun setEverySession(context: Context, every: Boolean) {
        init(context)
        _everySession.value = every
        prefs?.edit()?.putBoolean("every", every)?.apply()
        SessionReminders.resync(context)
    }
}
