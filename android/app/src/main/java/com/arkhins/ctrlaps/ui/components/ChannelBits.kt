package com.arkhins.ctrlaps.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.OnGold

/** A channel's unread count: gold with a dark number; for a muted channel, inverted (a gold number in a gold ring). */
@Composable
fun UnreadBadge(count: Int, muted: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(999.dp)
    val text = if (count > 99) "99+" else "$count"
    if (muted) {
        Box(modifier.border(1.dp, Gold.copy(alpha = 0.7f), shape).padding(horizontal = 7.dp, vertical = 2.dp)) {
            Text(text, style = MaterialTheme.typography.labelSmall, color = Gold)
        }
    } else {
        Box(modifier.background(Gold, shape).padding(horizontal = 7.dp, vertical = 2.dp)) {
            Text(text, style = MaterialTheme.typography.labelSmall, color = OnGold)
        }
    }
}

/** Mute or unmute a channel's notifications (push only; unread still counts). */
@Composable
fun MuteChip(muted: Boolean, enabled: Boolean = true, onToggle: () -> Unit) {
    Chip(if (muted) "🔕 Muted" else "🔔 Notifications on", if (muted) Gold else com.arkhins.ctrlaps.ui.theme.SnowSoft, filled = false) { if (enabled) onToggle() }
}
