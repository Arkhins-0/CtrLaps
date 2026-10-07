package com.arkhins.ctrlaps.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.data.RaceSession
import com.arkhins.ctrlaps.ui.instant
import com.arkhins.ctrlaps.ui.theme.Danger
import com.arkhins.ctrlaps.ui.theme.Gold
import kotlinx.coroutines.delay

/** "1d 4h 12m", "4h 12m", "12m 30s", "40s": short, the seconds only in the last hour. */
fun humanCountdown(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val d = total / 86_400
    val h = (total % 86_400) / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return when {
        d > 0 -> "${d}d ${h}h ${m}m"
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}

/**
 * The next session of [sessions] as a ticking line: "ITC Qualifying in 1d 4h 12m", or "Live now · ITC Race 1 · ends
 * in 12m" with a red dot while one is on; nothing once they are all over. It ticks every second in the last hour,
 * every half minute before that, and stops when it leaves the screen.
 */
@Composable
fun SessionCountdown(sessions: List<RaceSession>, label: (RaceSession) -> String = { it.name }, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val times = remember(sessions) { sessions.map { Triple(it, instant(it.startsAt).toEpochMilli(), instant(it.endsAt).toEpochMilli()) }.sortedBy { it.second } }
    val live = times.firstOrNull { (_, start, end) -> start <= now && end > now }
    val next = times.firstOrNull { (_, start, _) -> start > now }
    LaunchedEffect(times) {
        while (true) {
            now = System.currentTimeMillis()
            val soonest = listOfNotNull(live?.third, next?.second).minOrNull() ?: break
            delay(if (soonest - now < 3_600_000) 1_000 else 30_000)
        }
    }
    val text = when {
        live != null -> "Live now · ${label(live.first)} · ends in ${humanCountdown(live.third - now)}"
        next != null -> "${label(next.first)} in ${humanCountdown(next.second - now)}"
        else -> return
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (live != null) {
            Box(Modifier.size(8.dp).background(Danger, CircleShape))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Gold)
    }
}
