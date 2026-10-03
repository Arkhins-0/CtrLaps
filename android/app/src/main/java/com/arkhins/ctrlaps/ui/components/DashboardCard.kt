package com.arkhins.ctrlaps.ui.components

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.data.Dashboard
import com.arkhins.ctrlaps.data.NamedRef
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import com.arkhins.ctrlaps.ui.theme.SnowSoft

/**
 * The admin's and coordinator's card on Home: today's sessions and time changes, and people to nudge (each list opens
 * to names; a name opens the person). Folds to one line, remembered on this phone. Nothing to show: no card.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardCard(d: Dashboard, onWeekend: (String) -> Unit, onPerson: (String) -> Unit) {
    val prefs = LocalContext.current.getSharedPreferences("ctrlaps.ui", Context.MODE_PRIVATE)
    var open by remember { mutableStateOf(prefs.getBoolean("dashboard.open", true)) }
    var shown by remember { mutableStateOf<String?>(null) }
    val toCheck = d.pending.size + d.unfinished.size + d.suspended.size
    if (d.sessions.isEmpty() && d.changes.isEmpty() && toCheck == 0) return
    val summary = listOfNotNull(
        d.sessions.size.takeIf { it > 0 }?.let { "$it session${if (it == 1) "" else "s"}" },
        d.changes.size.takeIf { it > 0 }?.let { "$it time change${if (it == 1) "" else "s"}" },
        toCheck.takeIf { it > 0 }?.let { "$it to check" },
    ).joinToString(" · ")

    @Composable
    fun people(key: String, label: String, list: List<NamedRef>) {
        if (list.isEmpty()) return
        Row(Modifier.fillMaxWidth().clickable { shown = if (shown == key) null else key }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${list.size}", style = MaterialTheme.typography.titleSmall, color = Snow)
            Text(" $label", style = MaterialTheme.typography.bodyMedium, color = SnowSoft, modifier = Modifier.weight(1f))
            Text(if (shown == key) "▾" else "›", color = Gold)
        }
        if (shown == key) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                list.forEach { p -> Chip(p.name, SnowSoft) { onPerson(p.id) } }
            }
        }
    }

    Panel {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable {
                    open = !open
                    prefs.edit().putBoolean("dashboard.open", open).apply()
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TODAY", style = MaterialTheme.typography.labelMedium, color = Gold)
                if (!open) Text("  $summary", style = MaterialTheme.typography.bodySmall, color = SnowSoft, modifier = Modifier.weight(1f), maxLines = 1)
                else androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Text(if (open) "Hide" else "Show", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
            }
            if (open) {
                d.sessions.forEach { s ->
                    Row(Modifier.fillMaxWidth().clickable { onWeekend(s.weekendId) }.padding(top = 6.dp)) {
                        Text("${s.name} · ${s.weekendName}", style = MaterialTheme.typography.bodyMedium, color = SnowSoft, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(s.track, style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                    }
                }
                d.changes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Gold, modifier = Modifier.padding(top = 4.dp)) }
                if (toCheck > 0) androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 4.dp))
                people("pending", "haven't accepted their invite", d.pending)
                people("unfinished", "haven't finished their profile", d.unfinished)
                people("suspended", "suspended", d.suspended)
            }
        }
    }
}
