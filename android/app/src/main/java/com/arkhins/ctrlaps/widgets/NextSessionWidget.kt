package com.arkhins.ctrlaps.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.Text
import com.arkhins.ctrlaps.CtrlapsApplication
import com.arkhins.ctrlaps.reminders.RaceSessions
import com.arkhins.ctrlaps.reminders.UpcomingSession
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the widget shows, worked out when it is drawn. */
private data class NextSession(val title: String, val where: String, val weekendId: String, val countdown: String, val live: Boolean, val time: String)

/**
 * Home screen: the next race session in your categories (or for everyone), its weekend, and how long until it starts
 * ("in 1d 4h", "in 35 min", "Live now"). Small, just the countdown and the name. A tap opens the weekend.
 */
class NextSessionWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as CtrlapsApplication
        val signedIn = app.session.signedIn
        val now = Instant.now()
        val next = if (signedIn) runCatching { RaceSessions.upcoming(app, every = false).firstOrNull() }.getOrNull() else null
        val shown = next?.let { describe(it, now) }
        Widgets.scheduleTick(context, next?.let { nextChange(it, now) })
        provideContent { Content(context, signedIn, shown) }
    }

    @Composable
    private fun Content(context: Context, signedIn: Boolean, next: NextSession?) {
        val size = LocalSize.current
        WidgetFrame(context, next?.let { "/w/${it.weekendId}" } ?: "/schedule") {
            when {
                !signedIn -> SignedOut()
                next == null -> Column {
                    Text("NEXT SESSION", style = text(WidgetColors.gold, 10.sp, bold = true))
                    Text("Nothing on the schedule", style = text(WidgetColors.snowSoft, 13.sp), maxLines = 2)
                }
                size.height < MEDIUM.height || size.width < MEDIUM.width -> Column {
                    Text(next.countdown, style = text(WidgetColors.gold, 16.sp, bold = true), maxLines = 1)
                    Text(next.title, style = text(WidgetColors.snow, 13.sp), maxLines = 1)
                }
                else -> Column(GlanceModifier.fillMaxWidth()) {
                    Text(if (next.live) "ON TRACK" else "NEXT SESSION", style = text(WidgetColors.gold, 10.sp, bold = true))
                    Spacer(GlanceModifier.height(2.dp))
                    Text(next.title, style = text(WidgetColors.snow, 17.sp, bold = true), maxLines = 1)
                    Text(next.where, style = text(WidgetColors.snowSoft, 12.sp), maxLines = 1)
                    Spacer(GlanceModifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(next.countdown, style = text(WidgetColors.gold, if (size.width >= WIDE.width) 22.sp else 18.sp, bold = true), maxLines = 1)
                        if (!next.live) {
                            Spacer(GlanceModifier.width(8.dp))
                            Text(next.time, style = text(WidgetColors.snowFaint, 12.sp), maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    private companion object {
        val SMALL = DpSize(110.dp, 40.dp)
        val MEDIUM = DpSize(170.dp, 100.dp)
        val WIDE = DpSize(250.dp, 100.dp)

        /** The start in the phone's zone, as the countdown's companion: "Sat 2:30 pm". */
        val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE h:mm a", Locale.getDefault())

        fun describe(s: UpcomingSession, now: Instant): NextSession {
            val live = !now.isBefore(s.startsAt)
            return NextSession(
                title = s.title,
                where = s.where,
                weekendId = s.weekend.id,
                countdown = if (live) "Live now" else countdown(Duration.between(now, s.startsAt)),
                live = live,
                time = clock.format(s.startsAt.atZone(ZoneId.systemDefault())),
            )
        }

        /** "in 1d 4h", "in 4h 12m", "in 35 min" (rounded up, so it never says "in 0 min"). */
        fun countdown(d: Duration): String {
            val minutes = d.toMinutes()
            return when {
                d.toDays() >= 1 -> "in ${d.toDays()}d ${d.toHours() % 24}h"
                d.toHours() >= 1 -> "in ${d.toHours()}h ${minutes % 60}m"
                else -> "in ${(d.toMillis() + 59_999) / 60_000} min"
            }
        }

        /** When the words above next change: on the hour while days away, each minute after that, then at the start and end. */
        fun nextChange(s: UpcomingSession, now: Instant): Long {
            if (!now.isBefore(s.startsAt)) return s.endsAt.toEpochMilli() + 1_000
            val left = Duration.between(now, s.startsAt).toMillis()
            val step = if (left >= Duration.ofDays(1).toMillis()) 3_600_000L else 60_000L
            val untilChange = (left % step).let { if (it == 0L) step else it }
            return now.toEpochMilli() + untilChange + 500
        }
    }
}

class NextSessionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextSessionWidget()

    /** The last one taken off the home screen: its countdown alarm goes too. */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Widgets.cancelTick(context)
    }
}
