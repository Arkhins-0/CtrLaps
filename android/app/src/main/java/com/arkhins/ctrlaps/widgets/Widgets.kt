package com.arkhins.ctrlaps.widgets

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.arkhins.ctrlaps.CtrlapsApplication
import com.arkhins.ctrlaps.MainActivity
import com.arkhins.ctrlaps.R
import com.arkhins.ctrlaps.data.Me
import com.arkhins.ctrlaps.push.Notifications
import com.arkhins.ctrlaps.reminders.Alarms
import kotlinx.coroutines.launch
import androidx.glance.appwidget.updateAll as glanceUpdateAll

/**
 * The home-screen widgets: the next race session, unread chats and the standings. Each draws only from the phone's
 * copy ([com.arkhins.ctrlaps.data.LocalStore]), never the network, so [updateAll] is called once that copy changes:
 * after the background run, and by whatever refreshes those lists in the app.
 */
object Widgets {
    private const val PREFS = "widgets"

    /** Redraw every widget on the home screen from the phone's copy. */
    suspend fun updateAll(context: Context) {
        val app = context.applicationContext
        runCatching { NextSessionWidget().glanceUpdateAll(app) }
        runCatching { UnreadWidget().glanceUpdateAll(app) }
        runCatching { StandingsWidget().glanceUpdateAll(app) }
    }

    /** [updateAll] for callers outside a coroutine. */
    fun refresh(context: Context) {
        val app = context.applicationContext as? CtrlapsApplication ?: return
        app.appScope.launch { updateAll(app) }
    }

    /**
     * The unread chats count as the app knows it right now (it polls more often than /api/me is kept): the Unread
     * widget shows it at once. The background run sets it from /api/me.
     */
    fun unreadChats(context: Context, count: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt("unreadChats", -1) == count) return
        prefs.edit().putInt("unreadChats", count).apply()
        val app = context.applicationContext as? CtrlapsApplication ?: return
        app.appScope.launch { runCatching { UnreadWidget().glanceUpdateAll(app) } }
    }

    /** The count for the Unread widget: the app's latest, else the phone's copy of /api/me. */
    internal suspend fun unreadChats(app: CtrlapsApplication): Int {
        val saved = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("unreadChats", -1)
        if (saved >= 0) return saved
        return app.store.read("/api/me", Me.serializer())?.unreadChats ?: 0
    }

    /** Forget the count kept for the widget (signed out): the widget falls back to what /api/me says, or nothing. */
    internal fun clearUnread(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("unreadChats").apply()
    }

    /**
     * The Next session widget's countdown moves on at [atMillis] ("in 35 min" to "in 34 min", or "Live now"): an
     * alarm then redraws it. It never wakes the phone; it only needs to be right when someone looks. Null cancels it.
     */
    internal suspend fun scheduleTick(context: Context, atMillis: Long?) {
        val pending = tickIntent(context)
        val placed = runCatching { GlanceAppWidgetManager(context).getGlanceIds(NextSessionWidget::class.java).isNotEmpty() }.getOrDefault(false)
        if (atMillis == null || !placed) Alarms.cancel(context, pending)
        else Alarms.set(context, atMillis, pending, wakeup = false)
    }

    internal fun cancelTick(context: Context) = Alarms.cancel(context, tickIntent(context))

    private fun tickIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, WidgetTickReceiver::class.java).setAction("com.arkhins.ctrlaps.WIDGET_TICK"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

/** The countdown's alarm (see [Widgets.scheduleTick]). */
class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? CtrlapsApplication ?: return
        val done = goAsync()
        app.appScope.launch {
            try {
                runCatching { NextSessionWidget().glanceUpdateAll(app) }
            } finally {
                done.finish()
            }
        }
    }
}

/* ─────────────────────────────── Look ─────────────────────────────── */

/** The app's dark look, whatever the phone's theme: night page, gold accent, white words. */
internal object WidgetColors {
    private fun fixed(c: Color) = ColorProvider(day = c, night = c)
    val night = fixed(Color(0xFF0B0B0C))
    val snow = fixed(Color(0xFFF4F4F5))
    val snowSoft = fixed(Color(0xFFB4B4BA))
    val snowFaint = fixed(Color(0xFF7A7A82))
    val gold = fixed(Color(0xFFFFD100))
    val line = fixed(Color(0xFF26262A))
}

internal fun text(color: ColorProvider, size: TextUnit, bold: Boolean = false) =
    TextStyle(color = color, fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

/** A tap that opens the app at an in-app link, the way a tapped notification does. */
internal fun openApp(context: Context, link: String) = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(Notifications.EXTRA_LINK, link),
)

/** Every widget's frame: the rounded night card, tapping through to [link]. */
@Composable
internal fun WidgetFrame(context: Context, link: String, content: @Composable () -> Unit) {
    Box(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(ImageProvider(R.drawable.widget_background))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clickable(openApp(context, link)),
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}

/** Signed out: nothing on the phone to show. */
@Composable
internal fun SignedOut() {
    Text("Sign in to ${com.arkhins.ctrlaps.Config.APP_NAME}", style = text(WidgetColors.snowSoft, 13.sp), maxLines = 2)
}
