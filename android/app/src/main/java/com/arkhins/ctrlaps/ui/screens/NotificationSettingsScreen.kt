package com.arkhins.ctrlaps.ui.screens

import com.arkhins.ctrlaps.reminders.SessionReminderSettings
import androidx.compose.runtime.LaunchedEffect
import kotlinx.serialization.json.put
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import com.arkhins.ctrlaps.data.PushKindSetting
import com.arkhins.ctrlaps.data.PushPreferencesResponse
import com.arkhins.ctrlaps.ui.components.Snack
import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.arkhins.ctrlaps.LocalApp
import com.arkhins.ctrlaps.R
import com.arkhins.ctrlaps.push.Notifications
import com.arkhins.ctrlaps.ui.Battery
import com.arkhins.ctrlaps.ui.components.GroupTitle
import com.arkhins.ctrlaps.ui.theme.Danger
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.NightHigh
import com.arkhins.ctrlaps.ui.theme.OnGold
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import com.arkhins.ctrlaps.ui.theme.SnowSoft

/**
 * Account → Notifications, after Arkhime's notification settings: whether popups are on, each kind (the phone's own
 * channels), what keeps them on time (background access), a test of each kind, and the history (also the bell on
 * Home). Looked at again each time the phone's own settings page is left.
 */
@Composable
fun NotificationSettingsScreen(onHistory: () -> Unit, onPermissions: () -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    var looked by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) looked++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        looked++
        if (!granted) runCatching { context.startActivity(appNotificationPage(context)) }
    }
    val history by app.notificationLog.items.collectAsState()
    val scope = rememberCoroutineScope()
    var prefs by remember { mutableStateOf<List<PushKindSetting>?>(null) }
    LaunchedEffect(Unit) {
        prefs = runCatching { app.store.get("/api/me/push-preferences", PushPreferencesResponse.serializer()) { if (prefs == null) prefs = it.kinds }.kinds }.getOrNull() ?: prefs
    }

    // Read fresh on every look (cheap: a few system calls).
    val state = remember(looked) { PopupState.of(context) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GroupTitle("On this phone")
        StateRow(
            R.drawable.ic_bell,
            "Notifications",
            "Popups from CTR[L]APS",
            warning = "Nothing pops up: messages and announcements only show inside the app.".takeIf { !state.enabled },
            on = state.enabled,
        ) {
            val needsAsking = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (needsAsking) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
            else runCatching { context.startActivity(appNotificationPage(context)) }
        }
        listOf(
            Triple(Notifications.CHANNEL_CHATS, R.drawable.ic_tab_chat, "Private and group messages"),
            Triple(Notifications.CHANNEL_POSTS, R.drawable.ic_campaign, "Announcements, race-weekend and category channels"),
            Triple(Notifications.CHANNEL_OTHER, R.drawable.ic_trophy, "Results, reminders, support replies and app updates"),
        ).forEach { (id, icon, hint) ->
            val on = state.enabled && state.channelOn[id] != false
            StateRow(
                icon,
                state.channelName[id] ?: id,
                hint,
                warning = "Switched off on this phone.".takeIf { state.enabled && state.channelOn[id] == false },
                on = on,
                dim = !state.enabled,
            ) { runCatching { context.startActivity(channelPage(context, id)) }.onFailure { runCatching { context.startActivity(appNotificationPage(context)) } } }
        }

        // Which kinds pop up at all: kept on the server, so every phone and the website follow it.
        GroupTitle("What you hear about", modifier = Modifier.padding(top = 12.dp))
        val kinds = prefs
        if (kinds == null) Text("Loading…", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
        kinds?.forEach { k ->
            StateRow(KIND_ICONS[k.key] ?: R.drawable.ic_bell, k.label, k.hint, on = k.on, dim = !state.enabled, onChange = true) {
                val next = !k.on
                prefs = kinds.map { if (it.key == k.key) it.copy(on = next) else it }
                scope.launch {
                    runCatching { app.api.put("/api/me/push-preferences", PushPreferencesResponse.serializer()) { put("kind", k.key); put("enabled", next) } }
                        .onSuccess { prefs = it.kinds }
                        .onFailure {
                            prefs = kinds
                            Snack.error(it.message ?: "Couldn't save that. Try again.")
                        }
                }
            }
        }
        Text("Urgent messages always come. Messages still arrive in the app; this is only the popup.", style = MaterialTheme.typography.bodySmall, color = SnowFaint)

        // Reminders before each session, set on the phone itself so they come offline too.
        GroupTitle("Race reminders", modifier = Modifier.padding(top = 12.dp))
        SessionReminderSettings()

        GroupTitle("Arriving on time", modifier = Modifier.padding(top = 12.dp))
        MenuRow(
            "Background access",
            if (state.background) "Running in the background is allowed" else "Running in the background is off: popups may come late",
            highlight = false,
            icon = painterResource(R.drawable.ic_battery),
            danger = !state.background,
            onClick = onPermissions,
        )
        MenuRow(
            "Send a test",
            "One of each kind, to see that they arrive",
            icon = painterResource(R.drawable.ic_send_test),
            arrow = false,
        ) {
            if (!state.enabled) {
                Snack.show("Notifications are off. Switch them on first.")
            } else {
                Notifications.sendTest(context)
                Snack.show("Sent 3 test notifications")
            }
        }

        GroupTitle("History", modifier = Modifier.padding(top = 12.dp))
        MenuRow(
            "Notification history",
            when (history.size) {
                0 -> "Nothing yet · also from the bell on Home"
                1 -> "1 kept on this phone · also from the bell on Home"
                else -> "${history.size} kept on this phone · also from the bell on Home"
            },
            icon = painterResource(R.drawable.ic_history),
            onClick = onHistory,
        )
    }
}

/** What the phone says about CTR[L]APS's popups right now. */
private data class PopupState(val enabled: Boolean, val channelOn: Map<String, Boolean>, val channelName: Map<String, String>, val background: Boolean) {
    companion object {
        fun of(context: Context): PopupState {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channels = listOf(Notifications.CHANNEL_CHATS, Notifications.CHANNEL_POSTS, Notifications.CHANNEL_OTHER).mapNotNull { manager.getNotificationChannel(it) }
            return PopupState(
                enabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
                channelOn = channels.associate { it.id to (it.importance != NotificationManager.IMPORTANCE_NONE) },
                channelName = channels.associate { it.id to it.name.toString() },
                background = Battery.isExempt(context),
            )
        }
    }
}

/** Each kind's icon, as the phone's own channels have them. */
private val KIND_ICONS = mapOf("chats" to R.drawable.ic_tab_chat, "announcements" to R.drawable.ic_campaign, "channels" to R.drawable.ic_tab_calendar, "results" to R.drawable.ic_trophy)

private fun appNotificationPage(context: Context) =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

private fun channelPage(context: Context, channel: String) =
    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, channel)

/**
 * A row that shows whether something is on, as the Permissions page's: an icon, a bold title, what it is (and in red
 * what goes wrong when it is off), and a switch for the state. The tap opens where it is changed. `dim` while the
 * whole app's notifications are off, so a kind can't be on.
 */
@Composable
private fun StateRow(icon: Int, title: String, hint: String, warning: String? = null, on: Boolean, dim: Boolean = false, onChange: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = if (warning != null) Danger else if (dim) SnowFaint else Gold, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, lineHeight = 20.sp), fontWeight = FontWeight.Bold, color = if (dim) SnowFaint else Snow)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = SnowSoft.copy(alpha = if (dim) 0.5f else 0.8f))
            if (warning != null) Text(warning, style = MaterialTheme.typography.bodySmall, color = Danger, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(12.dp))
        // Shows the phone's state (the row opens its page), or for a choice of ours, switches it.
        Switch(
            checked = on,
            onCheckedChange = if (onChange) ({ onClick() }) else null,
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
