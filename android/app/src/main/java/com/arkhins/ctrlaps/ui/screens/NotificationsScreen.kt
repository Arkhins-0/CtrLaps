package com.arkhins.ctrlaps.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.LocalApp
import com.arkhins.ctrlaps.R
import com.arkhins.ctrlaps.data.LoggedNotification
import com.arkhins.ctrlaps.ui.Links
import com.arkhins.ctrlaps.ui.components.Avatar
import com.arkhins.ctrlaps.ui.components.Chip
import com.arkhins.ctrlaps.ui.components.Empty
import com.arkhins.ctrlaps.ui.components.GhostButton
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.NightPanel
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import com.arkhins.ctrlaps.ui.theme.SnowSoft
import java.time.Instant

/** The Notifications page's filters, in this order; only those with something in them show. */
private val KINDS = listOf(
    "chats" to "Chats",
    "announcements" to "Announcements",
    "channels" to "Channels",
    "results" to "Results",
    "reminders" to "Reminders",
    "support" to "Support",
    "other" to "Other",
)

private const val FIRST = 100
private const val MORE = 50

/**
 * Every notification this phone has shown, newest first, so one swiped away can be found again: filter chips by
 * kind, the first 100 and then 50 more at a time. Opening the page clears the bell's badge; a tap goes where the
 * notification went.
 */
@Composable
fun NotificationsScreen() {
    val app = LocalApp.current
    val all by app.notificationLog.items.collectAsState()
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    var shown by rememberSaveable { mutableIntStateOf(FIRST) }
    var clearing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { app.notificationLog.markSeen() }
    LaunchedEffect(all.size) { if (all.isNotEmpty()) app.notificationLog.markSeen() }

    val counts = remember(all) { all.groupingBy { it.kind }.eachCount() }
    val list = remember(all, kind) { if (kind == null) all else all.filter { it.kind == kind } }

    Column(Modifier.fillMaxSize()) {
        if (counts.size > 1) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip("All ${all.size}", Gold, filled = kind == null) { kind = null; shown = FIRST }
                KINDS.filter { (k, _) -> counts.containsKey(k) }.forEach { (k, label) ->
                    Chip("$label ${counts[k]}", Gold, filled = kind == k) { kind = k; shown = FIRST }
                }
            }
        }
        if (list.isEmpty()) {
            Box(Modifier.padding(16.dp)) {
                Empty("Nothing yet. Messages, announcements, results and reminders the phone shows as notifications are kept here, so you can find one after swiping it away.")
            }
            return@Column
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(list.take(shown), key = { it.key }) { n -> NotificationRow(n, app.api.absolute(n.photo.ifBlank { null })) { Links.pending.value = n.link } }
            val left = list.size - shown
            if (left > 0) {
                item(key = "more") {
                    GhostButton("Load ${minOf(MORE, left)} more ($left left)", Modifier.fillMaxWidth()) { shown += MORE }
                }
            }
            item(key = "clear") {
                if (clearing) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Clear all ${all.size}?", style = MaterialTheme.typography.bodyMedium, color = SnowSoft, modifier = Modifier.weight(1f))
                        GhostButton("Cancel") { clearing = false }
                        GhostButton("Clear", danger = true) { app.notificationLog.clear(); clearing = false; kind = null }
                    }
                } else {
                    GhostButton("Clear all", Modifier.fillMaxWidth().padding(top = 8.dp)) { clearing = true }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: LoggedNotification, photo: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(NightPanel, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (photo != null) {
            Avatar(photo, n.title, size = 40)
        } else {
            Box(Modifier.size(40.dp).background(Gold.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(iconFor(n.kind)), contentDescription = null, tint = Gold, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                "${KINDS.firstOrNull { it.first == n.kind }?.second?.uppercase() ?: "OTHER"} · ${com.arkhins.ctrlaps.ui.ago(Instant.ofEpochMilli(n.at).toString())}",
                style = MaterialTheme.typography.labelSmall,
                color = Gold,
            )
            Text(n.title, style = MaterialTheme.typography.titleSmall, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (n.body.isNotBlank()) {
                Text(n.body, style = MaterialTheme.typography.bodySmall, color = SnowFaint, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun iconFor(kind: String): Int = when (kind) {
    "chats" -> R.drawable.ic_tab_chat
    "results" -> R.drawable.ic_trophy
    "reminders" -> R.drawable.ic_event
    "announcements", "channels" -> R.drawable.ic_tab_home
    else -> R.drawable.ic_bell
}
