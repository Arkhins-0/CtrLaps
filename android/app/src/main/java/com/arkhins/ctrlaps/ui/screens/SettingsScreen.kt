package com.arkhins.ctrlaps.ui.screens

import com.arkhins.ctrlaps.ui.components.Snack
import com.arkhins.ctrlaps.ui.components.rememberHaptics
import com.arkhins.ctrlaps.ui.components.DontAsk
import com.arkhins.ctrlaps.ui.theme.NightLine
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import com.arkhins.ctrlaps.ui.components.GroupTitle
import com.arkhins.ctrlaps.ui.theme.NightHigh
import com.arkhins.ctrlaps.ui.components.ColorPickerDialog
import com.arkhins.ctrlaps.ui.components.ColorDot
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.height
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
import com.arkhins.ctrlaps.ui.theme.InterfaceSetting
import com.arkhins.ctrlaps.ui.theme.AnimationSpeed
import com.arkhins.ctrlaps.ui.theme.AppMotion
import androidx.compose.ui.draw.alpha
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
    /** Its icon on the page. */
    val icon: Int,
    val title: String,
    val hint: String,
    val warning: String,
    /** Null when the phone can't say (a maker's own switch): the line just offers its page. */
    val allowed: Boolean?,
    /** Runtime permissions asked for with the system dialog; empty for the ones only a settings page can change. */
    val permissions: List<String> = emptyList(),
    /** The phone's own page for this switch. */
    val page: Intent,
    /** Under "Updates and background" rather than "What CTR[L]APS can use". */
    val background: Boolean = false,
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
fun SettingsScreen(onPermissions: () -> Unit, onTheme: () -> Unit, onInterface: () -> Unit, onEmail: () -> Unit, onDelete: () -> Unit) {
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
            val animations by InterfaceSetting.animations.collectAsState()
            val speed by InterfaceSetting.speed.collectAsState()
            MenuRow("Interface", if (animations) "Animations: ${speed.label.lowercase()}" else "Animations off", icon = painterResource(R.drawable.ic_animation), onClick = onInterface)
            MenuRow("Email", "Which emails you get", icon = rememberVectorPainter(Icons.Outlined.Email), onClick = onEmail)
            MenuRow("Delete account", "Erase your account and the details we hold", icon = rememberVectorPainter(Icons.Outlined.Delete), danger = true, onClick = onDelete)
            // Shown only when some "Don't ask me again" was ticked.
            val context = LocalContext.current
            var turnedOff by remember { mutableStateOf(DontAsk.any(context)) }
            if (turnedOff) {
                MenuRow("Show the questions I turned off", "Ask again before signing out and the like", arrow = false) {
                    DontAsk.askAgain(context)
                    turnedOff = false
                    Snack.show("Questions will be asked again")
                }
            }
        }
    }
}

/**
 * How the app looks, after Arkhime's theme page: light, dark or the phone's (three buttons by the heading); a colour
 * theme; then switches for pure black, the phone's own font, Material You (the wallpaper's colour) and a custom colour
 * picked on a wheel. The whole app changes at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen() {
    val mode by ThemeSetting.mode.collectAsState()
    val black by ThemeSetting.black.collectAsState()
    val accent by ThemeSetting.accent.collectAsState()
    val deviceFont by ThemeSetting.deviceFont.collectAsState()
    val materialYou by ThemeSetting.materialYou.collectAsState()
    val custom by ThemeSetting.custom.collectAsState()
    val customColor by ThemeSetting.customColor.collectAsState()
    var picking by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val canMaterialYou = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // Debug builds: `--es sheet picker` opens the colour picker over adb (see DebugHooks).
    if (com.arkhins.ctrlaps.BuildConfig.DEBUG) {
        val asked by com.arkhins.ctrlaps.ui.DebugHooks.sheet.collectAsState()
        LaunchedEffect(asked) {
            if (asked == "picker") { picking = true; com.arkhins.ctrlaps.ui.DebugHooks.sheet.value = null }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Light, dark, or as the phone is: the chosen one bright, the others dim.
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Mode", style = MaterialTheme.typography.titleMedium, color = Snow, modifier = Modifier.weight(1f))
            listOf(
                Triple(ThemeMode.Light, R.drawable.ic_light_mode, "Light"),
                Triple(ThemeMode.Dark, R.drawable.ic_dark_mode, "Dark"),
                Triple(ThemeMode.System, R.drawable.ic_brightness_auto, "As the phone is"),
            ).forEach { (m, icon, label) ->
                IconButton(onClick = { ThemeSetting.set(m) }) {
                    Icon(
                        painterResource(icon),
                        contentDescription = label + if (mode == m) ", chosen" else "",
                        tint = if (mode == m) Gold else SnowFaint.copy(alpha = 0.6f),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
        Text(
            when (mode) {
                ThemeMode.Light -> "Light pages, dark text"
                ThemeMode.Dark -> "Dark pages, light text"
                ThemeMode.System -> "Light or dark, as the phone is set"
            },
            style = MaterialTheme.typography.bodySmall,
            color = SnowFaint,
        )

        // The colour theme: a preset; Material You or a custom colour take its place while on.
        ExposedDropdownMenuBox(expanded = menu, onExpandedChange = { menu = it }, modifier = Modifier.padding(top = 12.dp)) {
            OutlinedTextField(
                value = when {
                    materialYou && canMaterialYou -> "Material You"
                    custom -> "Custom colour"
                    else -> "CTR ${accent.label}".takeIf { accent == Accent.Gold } ?: accent.label
                },
                onValueChange = {},
                readOnly = true,
                label = { Text("Colour theme") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_palette), contentDescription = null, tint = Gold) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menu) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, unfocusedBorderColor = NightLine, focusedLabelColor = Gold, unfocusedLabelColor = SnowFaint, focusedTextColor = Snow, unfocusedTextColor = Snow),
            )
            ExposedDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                Accent.entries.forEach { a ->
                    DropdownMenuItem(
                        text = { Text(if (a == Accent.Gold) "CTR Gold" else a.label, color = Snow) },
                        leadingIcon = { Box(Modifier.size(20.dp).background(if (palette.dark) a.dark else a.light, CircleShape)) },
                        onClick = { ThemeSetting.setAccent(a); menu = false },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        ThemeSwitch(R.drawable.ic_dark_mode, "Pure black", "As dark as it gets: a black page in the dark theme, kinder to OLED screens", black) { ThemeSetting.setBlack(it) }
        ThemeSwitch(R.drawable.ic_text_fields, "Use device font", "The phone's own font instead of the app's", deviceFont) { ThemeSetting.setDeviceFont(it) }
        if (canMaterialYou) {
            ThemeSwitch(R.drawable.ic_palette, "Material You", "The same colour as your wallpaper", materialYou) { ThemeSetting.setMaterialYou(it) }
        }
        ThemeSwitch(R.drawable.ic_palette, "Custom colour", "Your own colour for the accent", custom) { ThemeSetting.setCustom(it) }
        Row(
            Modifier.fillMaxWidth().clickable { picking = true }.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_palette), contentDescription = null, tint = Gold, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text("Colour picker", style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp), fontWeight = FontWeight.Bold, color = Snow)
                Text("Choose a colour: #%06X".format(customColor and 0xFFFFFF), style = MaterialTheme.typography.bodySmall, color = SnowSoft.copy(alpha = 0.8f))
            }
            ColorDot(Color(customColor))
        }
    }
    if (picking) {
        ColorPickerDialog(Color(customColor), onDismiss = { picking = false }) { c ->
            ThemeSetting.setCustomColor(c.toArgb())
            picking = false
        }
    }
}

/**
 * How the app moves, after Arkhime's interface settings: animations on or off and how quick they are (every
 * animation follows it, see AppMotion), haptics, and blur behind dialogs and on the profile banner (Android 12+).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun InterfaceScreen() {
    val animations by InterfaceSetting.animations.collectAsState()
    val speed by InterfaceSetting.speed.collectAsState()
    val blur by InterfaceSetting.blur.collectAsState()
    val haptics by InterfaceSetting.haptics.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column {
            GroupTitle("Motion")
            ThemeSwitch(R.drawable.ic_animation, "Animations", "Screens slide, sheets rise and photos grow. Off: everything changes at once", animations) { InterfaceSetting.setAnimations(it) }
            // The phone's own switch (Developer options, Accessibility) wins: say so rather than look broken.
            if (AppMotion.system == 0f) {
                Text(
                    "Animations are off on this phone (Accessibility or Developer options), so nothing moves here either.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gold,
                    modifier = Modifier.padding(start = 44.dp, bottom = 8.dp),
                )
            }
            Column(Modifier.padding(start = 44.dp, top = 4.dp, bottom = 8.dp).alpha(if (animations) 1f else 0.4f)) {
                Text("Speed", style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp), fontWeight = FontWeight.Bold, color = Snow)
                Text("How long each animation takes", style = MaterialTheme.typography.bodySmall, color = SnowSoft.copy(alpha = 0.8f))
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimationSpeed.entries.forEach { s ->
                        Chip(s.label, Gold, filled = s == speed, onClick = if (animations) ({ InterfaceSetting.setSpeed(s) }) else null)
                    }
                }
            }
        }
        Column {
            GroupTitle("Feel")
            ThemeSwitch(R.drawable.ic_vibration, "Haptics", "A light buzz on switches, stars, sends and picks", haptics) { InterfaceSetting.setHaptics(it) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Column {
                GroupTitle("Effects")
                ThemeSwitch(R.drawable.ic_blur, "Blur", "What is behind a dialog, and the photo behind your profile banner", blur) { InterfaceSetting.setBlur(it) }
            }
        }
    }
}

/** A theme switch: an accent icon, a bold title, a quieter line, and the switch. */
@Composable
private fun ThemeSwitch(icon: Int, title: String, hint: String, on: Boolean, onFlip: (Boolean) -> Unit) {
    val haptics = rememberHaptics()
    val onChange = { v: Boolean -> haptics.toggle(v); onFlip(v) }
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Gold, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp), fontWeight = FontWeight.Bold, color = Snow)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = SnowSoft.copy(alpha = 0.8f))
        }
        Spacer(Modifier.width(12.dp))
        // Off is outlined, so it still shows on a pure black page.
        Switch(
            checked = on,
            onCheckedChange = onChange,
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
        val open = { item: Access ->
            if (item.allowed == false && item.permissions.isNotEmpty()) ask.launch(item.permissions.toTypedArray())
            else runCatching { openPage.launch(item.page) }.onFailure { runCatching { context.startActivity(appDetails(context)) } }
        }
        if (items.isNotEmpty()) AccessSummary(items)
        listOf(false to "What CTR[L]APS can use", true to "Updates and background").forEach { (background, title) ->
            val group = items.filter { it.background == background }
            if (group.isNotEmpty()) {
                Column {
                    GroupTitle(title)
                    group.forEach { item -> AccessRow(item) { open(item) } }
                }
            }
        }
        Text(
            "Tap a line to change it. Switching one off happens on the phone's own settings page.",
            style = MaterialTheme.typography.bodySmall,
            color = SnowFaint,
        )
    }
}

/** The page's top: all set, or how many still need allowing, with the tone to match. */
@Composable
private fun AccessSummary(items: List<Access>) {
    val known = items.filter { it.allowed != null }
    val missing = known.count { it.allowed == false }
    val tone = if (missing == 0) Allowed else Danger
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(tone.copy(alpha = 0.12f)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(if (missing == 0) R.drawable.ic_shield_check else R.drawable.ic_error), contentDescription = null, tint = tone, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (missing == 0) "All set" else if (missing == 1) "1 needs your OK" else "$missing need your OK",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, lineHeight = 26.sp),
                fontWeight = FontWeight.Bold,
                color = Snow,
            )
            Text(
                "${known.size - missing} of ${known.size} allowed" + if (missing == 0) ". Popups, updates and the scanner all work." else ". Tap one below to allow it.",
                style = MaterialTheme.typography.bodySmall,
                color = SnowSoft,
            )
        }
    }
}

/**
 * One permission, as the Account pages' rows: an icon, a bold title, what it's for (and, when it is off, what goes
 * wrong in red), and a switch that shows whether it is on. One the phone can't report gets an arrow to its page.
 */
@Composable
private fun AccessRow(item: Access, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(item.icon), contentDescription = null, tint = if (item.allowed == false) Danger else Gold, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, lineHeight = 20.sp), fontWeight = FontWeight.Bold, color = Snow)
            Text(item.hint, style = MaterialTheme.typography.bodySmall, color = SnowSoft.copy(alpha = 0.8f))
            if (item.allowed == false) Text(item.warning, style = MaterialTheme.typography.bodySmall, color = Danger, modifier = Modifier.padding(top = 2.dp))
            if (item.allowed == null) Text(item.warning, style = MaterialTheme.typography.bodySmall, color = SnowFaint, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(12.dp))
        if (item.allowed == null) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Open", tint = Gold)
        } else {
            // Shows the state; the row's tap does the changing (a dialog, or the phone's page).
            Switch(
                checked = item.allowed,
                onCheckedChange = null,
                modifier = Modifier.semantics { contentDescription = if (item.allowed) "Allowed" else "Not allowed" },
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
}

private fun accessList(context: Context): List<Access> = buildList {
    val details = appDetails(context)
    val channelsOff = Battery.channelsOff(context)
    add(
        Access(
            R.drawable.ic_bell,
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
            R.drawable.ic_location,
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
            R.drawable.ic_gallery,
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
            R.drawable.ic_mic,
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
            R.drawable.ic_camera,
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
            R.drawable.ic_download,
            "Install updates",
            "Update CTR[L]APS from inside the app",
            "Updates can't install from inside CTR[L]APS; Android will stop to ask each time.",
            context.packageManager.canRequestPackageInstalls(),
            page = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
            background = true,
        ),
    )
    val exempt = Battery.isExempt(context)
    add(
        Access(
            R.drawable.ic_dark_mode,
            "Run in background",
            "Battery optimisation off for CTR[L]APS",
            "The phone may hold back popups and downloads while it sleeps.",
            exempt,
            page = if (exempt) Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) else Battery.requestExemption(context),
            background = true,
        ),
    )
    add(
        Access(
            R.drawable.ic_alarm,
            "Exact reminders",
            "Race-session and event reminders right on time",
            "Reminders may come a few minutes late.",
            com.arkhins.ctrlaps.reminders.Alarms.exactAllowed(context),
            // Android 12+ only; below it exact alarms are always allowed and the line shows as on.
            page = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")) else details,
            background = true,
        ),
    )
    // Xiaomi's own battery saver holds an app back even with Android's optimisation off; it can't be read, only opened.
    Battery.xiaomiBatteryIntent(context)?.let { page ->
        add(
            Access(
                R.drawable.ic_battery,
                "Battery saver",
                "Xiaomi's own battery saver, apart from Android's",
                "Set to No restrictions, or the phone may hold back popups while it sleeps.",
                null,
                page = page,
                background = true,
            ),
        )
    }
    // Phones with their own autostart switch: Xiaomi says whether it is on; the others can only be opened.
    Battery.autostartIntent(context)?.let { page ->
        add(
            Access(
                R.drawable.ic_restart,
                "Autostart",
                "Start again after recent apps are cleared",
                "Popups stop once recent apps are cleared, until CTR[L]APS is opened again.",
                Battery.autostartAllowed(context),
                page = page,
                background = true,
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
