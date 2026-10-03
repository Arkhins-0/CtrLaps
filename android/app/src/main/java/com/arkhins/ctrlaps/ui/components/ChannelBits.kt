package com.arkhins.ctrlaps.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.put
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

/** Mute or unmute a channel's notifications (push only; unread still counts): a bell, struck through when muted. */
@Composable
fun MuteChip(muted: Boolean, enabled: Boolean = true, onToggle: () -> Unit) {
    IconAction(
        androidx.compose.ui.res.painterResource(if (muted) com.arkhins.ctrlaps.R.drawable.ic_bell_off else com.arkhins.ctrlaps.R.drawable.ic_bell),
        if (muted) "Muted. Tap for notifications" else "Notifications on. Tap to mute",
        if (muted) Gold else com.arkhins.ctrlaps.ui.theme.SnowSoft,
        enabled = enabled,
        onClick = onToggle,
    )
}

/** The small struck-through bell beside a muted channel's name in a list. */
@Composable
fun MutedMark() {
    androidx.compose.material3.Icon(
        androidx.compose.ui.res.painterResource(com.arkhins.ctrlaps.R.drawable.ic_bell_off),
        contentDescription = "Muted",
        tint = com.arkhins.ctrlaps.ui.theme.SnowFaint,
        modifier = Modifier.padding(start = 6.dp).size(14.dp),
    )
}

/**
 * Mute or unmute at once: the bell flips and a toast says so while the request runs behind it; if the server refuses,
 * the bell flips back and the toast says the change didn't take.
 */
fun toggleMute(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    api: com.arkhins.ctrlaps.data.CtrlapsApi,
    path: String,
    muted: Boolean,
    show: (Boolean) -> Unit,
) {
    val next = !muted
    show(next)
    android.widget.Toast.makeText(context, if (next) "Notifications muted" else "Notifications on", android.widget.Toast.LENGTH_SHORT).show()
    scope.launch {
        runCatching { api.put(path, com.arkhins.ctrlaps.data.MuteResponse.serializer()) { put("muted", next) } }
            .onSuccess { r -> show(r.muted) }
            .onFailure {
                show(muted)
                android.widget.Toast.makeText(context, "Couldn't change notifications. Try again.", android.widget.Toast.LENGTH_SHORT).show()
            }
    }
}

