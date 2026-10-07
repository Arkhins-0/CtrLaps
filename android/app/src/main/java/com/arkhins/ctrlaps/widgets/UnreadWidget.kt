package com.arkhins.ctrlaps.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import com.arkhins.ctrlaps.CtrlapsApplication
import com.arkhins.ctrlaps.R

/** Home screen: the unread private and group messages (the Chats tab's count), with the chat icon. A tap opens Chats. */
class UnreadWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as CtrlapsApplication
        val signedIn = app.session.signedIn
        if (!signedIn) Widgets.clearUnread(app)
        val count = if (signedIn) runCatching { Widgets.unreadChats(app) }.getOrDefault(0) else 0
        provideContent { Content(context, signedIn, count) }
    }

    @Composable
    private fun Content(context: Context, signedIn: Boolean, count: Int) {
        WidgetFrame(context, "/chats") {
            if (!signedIn) {
                SignedOut()
                return@WidgetFrame
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    ImageProvider(R.drawable.ic_tab_chat),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(if (count > 0) WidgetColors.gold else WidgetColors.snowFaint),
                    modifier = GlanceModifier.size(26.dp),
                )
                Spacer(GlanceModifier.width(10.dp))
                Column {
                    Text(
                        if (count > 0) "$count" else "No",
                        style = text(if (count > 0) WidgetColors.gold else WidgetColors.snow, if (count > 0) 22.sp else 16.sp, bold = true),
                        maxLines = 1,
                    )
                    Text(if (count == 1) "unread message" else "unread messages", style = text(WidgetColors.snowSoft, 12.sp), maxLines = 1)
                }
            }
        }
    }
}

class UnreadWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UnreadWidget()
}
