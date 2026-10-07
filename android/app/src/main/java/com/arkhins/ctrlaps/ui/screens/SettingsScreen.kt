package com.arkhins.ctrlaps.ui.screens

import com.arkhins.ctrlaps.R
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import com.arkhins.ctrlaps.ui.theme.palette
import com.arkhins.ctrlaps.ui.theme.OnGold
import com.arkhins.ctrlaps.ui.theme.Accent
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.collectAsState
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.ThemeMode
import com.arkhins.ctrlaps.ui.theme.ThemeSetting
import com.arkhins.ctrlaps.data.MediaLibrary
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.arkhins.ctrlaps.ui.Battery
import com.arkhins.ctrlaps.ui.components.Chip
import com.arkhins.ctrlaps.ui.components.Panel
import com.arkhins.ctrlaps.ui.theme.Danger
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import com.arkhins.ctrlaps.ui.theme.SnowSoft

private val Allowed = Color(0xFF6EE7B7)

/** The list as last looked up, shown at once the next time the page opens. */
@Volatile private var lastSeen: List<Access> = emptyList()

/** Looks the permissions up ahead of time (from the Account tab, off the main thread), so Settings opens filled. */
fun preloadSettings(context: Context) {
    lastSeen = accessList(context)
}

/** One thing the phone lets CTR[L]APS do, whether it is allowed, and what goes wrong without it. */
private data class Access(
    val title: String,
    val hint: String,
    val warning: String,
    /** Null when the phone can't say (a maker's own switch): the line just offers its page. */
    val allowed: Boolean?,
    /** Runtime permissions asked for with the system dialog; empty for the ones only a settings page can change. */
    val permissions: List<String> = emptyList(),
    /** The phone's own page for this switch. */
    val page: Intent,
)

/**
 * How CTR[L]APS works on this phone: each permission on its own line with an
 * Allowed / Not allowed badge. Android does not let an app take back its own
 * permission, so tapping an allowed line opens the phone's page to switch it
 * off; tapping one that is not allowed asks for it. Each line is looked at
 * again whenever the app comes back to the front.
 */
/** Settings: a menu into its pages. */
@Composable
fun SettingsScreen(onPermissions: () -> Unit, onTheme: () -> Unit, onEmail: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var items by remember { mutableStateOf(lastSeen) }
    LaunchedEffect(Unit) { items = withContext(Dispatchers.Default) { accessList(context) }.also { lastSeen = it } }
    // Lines the phone can't read (a maker's own switch) don't count either way.
    val known = items.filter { it.allowed != null }
    val allowed = known.count { it.allowed == true }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The same flat list as the Account tab: an accent icon, a bold title, a quieter line and an arrow.
        Column {
            MenuRow(
                "Permissions",
                if (items.isEmpty()) "Notifications, location, camera and more" else "Notifications, location, camera and more · $allowed of ${known.size} allowed",
                highlight = known.isNotEmpty() && allowed < known.size,
                icon = rememberVectorPainter(Icons.Outlined.Lock),
                onClick = onPermissions,
            )
            val mode by ThemeSetting.mode.collectAsState()
            MenuRow("Theme", mode.label, icon = painterResource(R.drawable.ic_eye), onClick = onTheme)
            MenuRow("Email", "Which emails you get", icon = rememberVectorPainter(Icons.Outlined.Email), onClick = onEmail)
            MenuRow("Delete account", "Erase your account and the details we hold", icon = rememberVectorPainter(Icons.Outlined.Delete), danger = true, onClick = onDelete)
        }
    }
}

/** Follow the phone's theme, or always light, or always dark. The whole app changes at once. */
@Composable
fun ThemeScreen() {
    val mode by ThemeSetting.mode.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Panel(padding = PaddingValues(vertical = 4.dp)) {
            Column {
                ThemeMode.entries.forEachIndexed { i, m ->
                    if (i > 0) HorizontalDivider(color = SnowFaint.copy(alpha = 0.15f))
                    Row(
                        Modifier.fillMaxWidth().clickable { ThemeSetting.set(m) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(m.label, style = MaterialTheme.typography.bodyLarge, color = Snow)
                            Text(
                                when (m) {
                                    ThemeMode.System -> "Light or dark, as the phone is set"
                                    ThemeMode.Light -> "Light pages, dark text"
                                    ThemeMode.Dark -> "Dark pages, light text"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = SnowFaint,
                            )
                        }
                        RadioButton(selected = mode == m, onClick = { ThemeSetting.set(m) }, colors = RadioButtonDefaults.colors(selectedColor = Gold, unselectedColor = SnowFaint))
                    }
                }
            }
        }
        val black by ThemeSetting.black.collectAsState()
        Panel(padding = PaddingValues(vertical = 4.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { ThemeSetting.setBlack(!black) }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Pure black", style = MaterialTheme.typography.bodyLarge, color = Snow)
                    Text("A black page in the dark theme, kinder to OLED screens and the battery", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
                }
                Switch(
                    checked = black,
                    onCheckedChange = { ThemeSetting.setBlack(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = OnGold, checkedTrackColor = Gold),
                )
            }
        }
        val accent by ThemeSetting.accent.collectAsState()
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Accent", style = MaterialTheme.typography.bodyLarge, color = Snow)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Accent.entries.forEach { a ->
                        val shade = if (palette.dark) a.dark else a.light
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(shade)
                                .border(if (a == accent) 3.dp else 0.dp, if (a == accent) Snow else Color.Transparent, CircleShape)
                                .clickable { ThemeSetting.setAccent(a) }
                                .semantics { contentDescription = a.label + if (a == accent) ", chosen" else "" },
                            contentAlignment = Alignment.Center,
                        ) { if (a == accent) Icon(Icons.Filled.Check, contentDescription = null, tint = OnGold, modifier = Modifier.size(20.dp)) }
                    }
                }
                Text("${accent.label}: buttons, chips and highlights. Gold is CTR's own.", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
            }
        }
    }
}

@Composable
fun PermissionsScreen() {
    val context = LocalContext.current
    var looked by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) looked++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        looked++
        // Refused and Android will not ask again: only its settings page can allow it now.
        val activity = context.findActivity()
        if (result.values.any { !it } && activity != null && result.keys.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }) {
            runCatching { context.startActivity(appDetails(context)) }
        }
    }
    val openPage = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { looked++ }
    // Asking the phone about seven permissions takes a moment: done off the main thread, after the first
    // frame, starting from what was seen last time so the page slides in already filled.
    var items by remember { mutableStateOf(lastSeen) }
    LaunchedEffect(looked) { items = withContext(Dispatchers.Default) { accessList(context) }.also { lastSeen = it } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Panel {
            Column {
                items.forEachIndexed { i, item ->
                    if (i > 0) HorizontalDivider(color = SnowFaint.copy(alpha = 0.15f))
                    AccessRow(item) {
                        if (item.allowed == false && item.permissions.isNotEmpty()) ask.launch(item.permissions.toTypedArray())
                        else runCatching { openPage.launch(item.page) }.onFailure { runCatching { context.startActivity(appDetails(context)) } }
                    }
                }
            }
        }
        Text(
            "Tap a line to change it. Switching one off happens on the phone's own settings page.",
            style = MaterialTheme.typography.labelSmall,
            color = SnowFaint,
        )
    }
}

@Composable
private fun AccessRow(item: Access, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, color = Snow)
            Text(item.hint, style = MaterialTheme.typography.bodySmall, color = SnowFaint)
            if (item.allowed == false) Text(item.warning, style = MaterialTheme.typography.bodySmall, color = Danger)
            if (item.allowed == null) Text(item.warning, style = MaterialTheme.typography.bodySmall, color = SnowSoft)
        }
        Spacer(Modifier.width(10.dp))
        when (item.allowed) {
            true -> Chip("Allowed", Allowed)
            false -> Chip("Not allowed", Danger)
            null -> Chip("Check", SnowSoft)
        }
    }
}

private fun accessList(context: Context): List<Access> = buildList {
    val details = appDetails(context)
    val channelsOff = Battery.channelsOff(context)
    add(
        Access(
            "Notifications",
            "Popups for messages, announcements and race updates",
            channelsOff.takeIf { it.isNotEmpty() }?.let { "Switched off: ${it.joinToString()}. Those popups don't arrive." }
                ?: "No popups arrive, and CTR[L]APS will ask for this again before it opens.",
            NotificationManagerCompat.from(context).areNotificationsEnabled() && channelsOff.isEmpty(),
            // With notifications on but a channel off, only the settings page can help, not the permission dialog.
            permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && channelsOff.isEmpty()) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList(),
            page = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        ),
    )
    add(
        Access(
            "Location",
            "Share where you are in a chat",
            "You can't share your location in chats.",
            granted(context, Manifest.permission.ACCESS_FINE_LOCATION) || granted(context, Manifest.permission.ACCESS_COARSE_LOCATION),
            permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            page = details,
        ),
    )
    add(
        Access(
            "Photos",
            "Your recent photos in the attach sheet",
            "The attach sheet can't show your photos; Gallery opens the system picker instead.",
            MediaLibrary.granted(context),
            permissions = MediaLibrary.permissions.toList(),
            page = details,
        ),
    )
    add(
        Access(
            "Microphone",
            "Record voice notes",
            "You can't record voice notes.",
            granted(context, Manifest.permission.RECORD_AUDIO),
            permissions = listOf(Manifest.permission.RECORD_AUDIO),
            page = details,
        ),
    )
    add(
        Access(
            "Camera",
            "Scan QR codes to verify people",
            "You can't scan QR codes; people can only be checked by their account code.",
            granted(context, Manifest.permission.CAMERA),
            permissions = listOf(Manifest.permission.CAMERA),
            page = details,
        ),
    )
    add(
        Access(
            "Install updates",
            "Update CTR[L]APS from inside the app",
            "Updates can't install from inside CTR[L]APS; Android will stop to ask each time.",
            context.packageManager.canRequestPackageInstalls(),
            page = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
        ),
    )
    val exempt = Battery.isExempt(context)
    add(
        Access(
            "Run in background",
            "Battery optimisation off for CTR[L]APS",
            "The phone may hold back popups and downloads while it sleeps.",
            exempt,
            page = if (exempt) Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) else Battery.requestExemption(context),
        ),
    )
    // Xiaomi's own battery saver holds an app back even with Android's optimisation off; it can't be read, only opened.
    Battery.xiaomiBatteryIntent(context)?.let { page ->
        add(
            Access(
                "Battery saver",
                "Xiaomi's battery saver: choose No restrictions",
                "Set to No restrictions, or the phone may hold back popups while it sleeps.",
                null,
                page = page,
            ),
        )
    }
    // Phones with their own autostart switch: Xiaomi says whether it is on; the others can only be opened.
    Battery.autostartIntent(context)?.let { page ->
        add(
            Access(
                "Autostart",
                "Start again after recent apps are cleared",
                "Popups stop once recent apps are cleared, until CTR[L]APS is opened again.",
                Battery.autostartAllowed(context),
                page = page,
            ),
        )
    }
}

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun appDetails(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
