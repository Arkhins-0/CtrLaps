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
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import com.arkhins.ctrlaps.CtrlapsApplication
import com.arkhins.ctrlaps.data.StandingsResponse
import com.arkhins.ctrlaps.data.pointsText

private data class StandingRow(val name: String, val points: String)

/**
 * Home screen: the top five of your first race category this season (the one the Standings page opens on), name and
 * points. Fewer rows when it is made short. A tap opens Standings.
 */
class StandingsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SHORT, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as CtrlapsApplication
        val signedIn = app.session.signedIn
        // Kept by the background run as the page first opens: the server puts the person's own category first.
        val r = if (signedIn) app.store.read("/api/standings", StandingsResponse.serializer()) else null
        val category = r?.let { s -> s.categories.firstOrNull { it.id == s.categoryId } ?: s.categories.firstOrNull() }
        val title = if (r == null || category == null) null
            else listOfNotNull(category.name, r.seasons.firstOrNull { it.id == r.seasonId }?.name).joinToString(" · ")
        // The answer's drivers belong to its own category; another one's aren't on the phone under this path.
        val rows = if (r != null && category != null && category.id == r.categoryId) r.drivers.take(5).map { StandingRow(it.name, pointsText(it.points)) } else emptyList()
        provideContent { Content(context, signedIn, title, rows) }
    }

    @Composable
    private fun Content(context: Context, signedIn: Boolean, title: String?, rows: List<StandingRow>) {
        val size = LocalSize.current
        WidgetFrame(context, "/standings") {
            if (!signedIn) {
                SignedOut()
                return@WidgetFrame
            }
            Column(GlanceModifier.fillMaxWidth()) {
                Text("STANDINGS", style = text(WidgetColors.gold, 10.sp, bold = true))
                Text(title ?: "No standings yet", style = text(WidgetColors.snow, 14.sp, bold = true), maxLines = 1)
                Spacer(GlanceModifier.height(4.dp))
                if (title != null && rows.isEmpty()) Text("No results yet", style = text(WidgetColors.snowSoft, 12.sp))
                rows.take(if (size.height < TALL.height) 3 else 5).forEachIndexed { i, row ->
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", style = text(if (i == 0) WidgetColors.gold else WidgetColors.snowFaint, 13.sp, bold = true), modifier = GlanceModifier.width(20.dp))
                        Text(row.name, style = text(WidgetColors.snow, 13.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                        Spacer(GlanceModifier.width(8.dp))
                        Text(row.points, style = text(if (i == 0) WidgetColors.gold else WidgetColors.snowSoft, 13.sp, bold = true), maxLines = 1)
                    }
                }
            }
        }
    }

    private companion object {
        val SHORT = DpSize(180.dp, 110.dp)
        val TALL = DpSize(180.dp, 170.dp)
    }
}

class StandingsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StandingsWidget()
}
