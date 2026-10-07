package com.arkhins.ctrlaps.reminders

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.arkhins.ctrlaps.R
import com.arkhins.ctrlaps.ui.components.Chip
import com.arkhins.ctrlaps.ui.theme.Danger
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.NightHigh
import com.arkhins.ctrlaps.ui.theme.OnGold
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import com.arkhins.ctrlaps.ui.theme.SnowSoft

/**
 * The race-session reminder settings, as rows in the Notifications page's style: on or off, how long before, and
 * whether every session or only your own. While exact alarms aren't allowed the first row says so in red, and a tap
 * there opens the phone's page for them. Placed by whichever page shows it (a column of rows; no title of its own).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionReminderSettings() {
    val context = LocalContext.current
    remember { SessionReminderSettings.init(context) }
    val enabled by SessionReminderSettings.enabled.collectAsState()
    val minutes by SessionReminderSettings.minutes.collectAsState()
    val every by SessionReminderSettings.everySession.collectAsState()

    // Exact alarms are switched on elsewhere: looked at again each time the page comes back.
    var looked by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) looked++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val exact = remember(looked) { Alarms.exactAllowed(context) }

    Column(Modifier.fillMaxWidth()) {
        ReminderRow(
            R.drawable.ic_alarm,
            "Race session reminders",
            "A notification before each session starts",
            warning = "Reminders may come a few minutes late. Tap to allow exact reminders.".takeIf { enabled && !exact },
            on = enabled,
            onClick = {
                if (enabled && !exact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) }
                } else {
                    SessionReminderSettings.setEnabled(context, !enabled)
                }
            },
            onSwitch = { SessionReminderSettings.setEnabled(context, it) },
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
            Icon(painterResource(R.drawable.ic_history), contentDescription = null, tint = if (enabled) Gold else SnowFaint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                RowTitle("How long before", dim = !enabled)
                RowHint("${lead(minutes)} before the start", dim = !enabled)
                FlowRow(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SessionReminderSettings.choices.forEach { m ->
                        Chip(lead(m), if (enabled) Gold else SnowFaint, filled = m == minutes) { SessionReminderSettings.setMinutes(context, m) }
                    }
                }
            }
        }
        ReminderRow(
            R.drawable.ic_filter,
            "Every session",
            if (every) "Every category's sessions" else "Only your categories, and sessions for everyone",
            on = every,
            dim = !enabled,
            onClick = { SessionReminderSettings.setEverySession(context, !every) },
            onSwitch = { SessionReminderSettings.setEverySession(context, it) },
        )
    }
}

private fun lead(minutes: Int) = if (minutes == 60) "1 hour" else "$minutes min"

@Composable
private fun RowTitle(text: String, dim: Boolean) =
    Text(text, style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, lineHeight = 20.sp), fontWeight = FontWeight.Bold, color = if (dim) SnowFaint else Snow)

@Composable
private fun RowHint(text: String, dim: Boolean) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = SnowSoft.copy(alpha = if (dim) 0.5f else 0.8f))

/** As the Notifications page's rows: an icon, a bold title, a quieter line (and in red what goes wrong), a switch. */
@Composable
private fun ReminderRow(
    icon: Int,
    title: String,
    hint: String,
    warning: String? = null,
    on: Boolean,
    dim: Boolean = false,
    onClick: () -> Unit,
    onSwitch: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = if (warning != null) Danger else if (dim) SnowFaint else Gold, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            RowTitle(title, dim)
            RowHint(hint, dim)
            if (warning != null) Text(warning, style = MaterialTheme.typography.bodySmall, color = Danger, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = on,
            onCheckedChange = onSwitch,
            enabled = !dim,
            modifier = Modifier.semantics { contentDescription = if (on) "On" else "Off" },
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnGold,
                checkedTrackColor = Gold,
                uncheckedThumbColor = SnowFaint,
                uncheckedTrackColor = NightHigh,
                uncheckedBorderColor = SnowFaint,
            ),
        )
    }
}
