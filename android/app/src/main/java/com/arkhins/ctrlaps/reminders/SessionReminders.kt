package com.arkhins.ctrlaps.reminders

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.arkhins.ctrlaps.CtrlapsApplication
import com.arkhins.ctrlaps.data.EventReminders
import com.arkhins.ctrlaps.data.Me
import com.arkhins.ctrlaps.data.RaceSession
import com.arkhins.ctrlaps.data.Weekend
import com.arkhins.ctrlaps.data.WeekendsResponse
import com.arkhins.ctrlaps.push.Notifications
import com.arkhins.ctrlaps.ui.instant
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/** A race session with its weekend, and the name it goes by: "ITC Qualifying". */
data class UpcomingSession(val session: RaceSession, val weekend: Weekend, val title: String) {
    val startsAt: Instant get() = instant(session.startsAt)
    val endsAt: Instant get() = instant(session.endsAt).takeIf { it.isAfter(startsAt) } ?: startsAt.plus(Duration.ofHours(1))

    /** "Round 3 · Kari Motor Speedway": where it is, for a notification's second line or a widget. */
    val where: String get() = listOf(weekend.name, weekend.venue.ifBlank { weekend.city }).filter { it.isNotBlank() }.distinct().joinToString(" · ")
}

/** The race sessions on the phone's copy of the weekends list: no network. */
object RaceSessions {
    /**
     * Sessions not yet over, soonest first. Unless [every], only those in the person's own categories and those for
     * everyone (no category), as Schedule's "Mine": the categories come from /api/me, where null means everything.
     */
    suspend fun upcoming(app: CtrlapsApplication, every: Boolean): List<UpcomingSession> {
        if (!app.session.signedIn) return emptyList()
        val list = app.store.read("/api/weekends", WeekendsResponse.serializer()) ?: return emptyList()
        val mine = if (every) null else app.store.read("/api/me", Me.serializer())?.categoryIds
        val codes = list.categories.associate { it.id to it.code }
        val now = Instant.now()
        return list.weekends.filterNot { it.seasonArchived }.flatMap { w ->
            w.sessions.mapNotNull { s ->
                if (mine != null && s.categoryId != null && s.categoryId !in mine) return@mapNotNull null
                val code = s.categoryId?.let { codes[it] }.orEmpty()
                // The category's code in front, unless the session's name already starts with it.
                val title = if (code.isBlank() || s.name.startsWith(code, ignoreCase = true)) s.name else "$code ${s.name}"
                UpcomingSession(s, w, title).takeIf { it.endsAt.isAfter(now) }
            }
        }.sortedBy { it.startsAt }
    }
}

/**
 * Race-session reminders, set on the phone so they come offline and with the app closed: "ITC Qualifying starts in
 * 15 minutes", on the "Results and reminders" channel, opening the weekend. Set again from the phone's copy of the
 * weekends whenever it is brought up to date (the background run, Schedule), when a setting changes, after a restart
 * of the phone and after an update; sessions that moved get the new time, removed ones lose their alarm.
 */
object SessionReminders {
    private const val PREFS = "session_reminders"
    private const val KEY = "set"
    /** Android allows an app 500 alarms; the next few dozen are plenty, as the background run tops them up. */
    private const val MAX = 40

    /** Set again in the background, from the phone's copy. */
    fun resync(context: Context) {
        val app = context.applicationContext as? CtrlapsApplication ?: return
        app.appScope.launch { runCatching { syncFromCache(app) } }
    }

    /** Set again from the phone's copy of the weekends and /api/me (nothing asked of the server). */
    suspend fun syncFromCache(app: CtrlapsApplication) {
        SessionReminderSettings.init(app)
        val on = SessionReminderSettings.enabled.value
        val sessions = if (on) RaceSessions.upcoming(app, SessionReminderSettings.everySession.value) else emptyList()
        sync(app, sessions, SessionReminderSettings.minutes.value)
    }

    @Synchronized
    private fun sync(context: Context, sessions: List<UpcomingSession>, minutes: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val before = prefs.getStringSet(KEY, emptySet()).orEmpty()
        val now = Instant.now()
        // A session already closer than the lead time gets no reminder: the alarm would ring late, and again on every sync.
        val due = sessions.mapNotNull { s ->
            val at = s.startsAt.minus(Duration.ofMinutes(minutes.toLong()))
            if (at.isBefore(now)) null else s to at
        }.take(MAX)
        due.forEach { (s, at) -> Alarms.set(context, at.toEpochMilli(), pending(context, s.session.id, s, minutes)) }
        val kept = due.map { it.first.session.id }.toSet()
        (before - kept).forEach { id -> Alarms.cancel(context, pending(context, id, null, minutes)) }
        prefs.edit().putStringSet(KEY, kept).apply()
    }

    private fun pending(context: Context, id: String, s: UpcomingSession?, minutes: Int): PendingIntent {
        val intent = Intent(context, SessionReminderReceiver::class.java).setAction("com.arkhins.ctrlaps.SESSION_REMINDER.$id")
        if (s != null) {
            intent.putExtra("id", id)
                .putExtra("title", s.title)
                .putExtra("where", s.where)
                .putExtra("weekendId", s.weekend.id)
                .putExtra("startsAt", s.startsAt.toEpochMilli())
                .putExtra("minutes", minutes)
        }
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

/** The alarm going off: "ITC Qualifying starts in 15 minutes", "Round 3 · Kari Motor Speedway" under it. */
class SessionReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val title = intent.getStringExtra("title") ?: return
        SessionReminderSettings.init(context)
        if (!SessionReminderSettings.enabled.value) return
        // Held back until after the start (the phone was off, say): it is no longer news.
        if (System.currentTimeMillis() > intent.getLongExtra("startsAt", Long.MAX_VALUE)) return
        Notifications.show(
            context,
            "$title starts ${EventReminders.lead(intent.getIntExtra("minutes", 0))}",
            intent.getStringExtra("where").orEmpty(),
            intent.getStringExtra("weekendId")?.let { "/w/$it" } ?: "/schedule",
            // "event:" files it under Reminders on the Notifications page, beside event reminders.
            "event:session:$id",
        )
    }
}
