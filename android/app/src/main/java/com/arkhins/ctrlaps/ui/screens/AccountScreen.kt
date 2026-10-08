package com.arkhins.ctrlaps.ui.screens

import com.arkhins.ctrlaps.ui.components.copiesDeviceInfo

import com.arkhins.ctrlaps.ui.components.QuestionSheet
import com.arkhins.ctrlaps.ui.components.DontAsk
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.arkhins.ctrlaps.ui.components.PhotoPreview
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.collectAsState
import com.arkhins.ctrlaps.ui.theme.NightHighest
import com.arkhins.ctrlaps.ui.theme.NightHigh
import coil.compose.AsyncImage
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.border
import androidx.compose.material.icons.outlined.Call
import androidx.compose.ui.unit.sp
import com.arkhins.ctrlaps.ui.theme.NightLine
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.Icons
import com.arkhins.ctrlaps.data.FollowingResponse
import com.arkhins.ctrlaps.data.CategoryIdsResponse
import com.arkhins.ctrlaps.ui.components.Chip
import kotlinx.serialization.json.add
import kotlinx.serialization.json.putJsonArray
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.arkhins.ctrlaps.ui.theme.NightPanel
import com.arkhins.ctrlaps.ui.theme.Danger
import androidx.compose.ui.res.painterResource
import com.arkhins.ctrlaps.R
import com.arkhins.ctrlaps.ui.components.IconAction
import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.BuildConfig
import com.arkhins.ctrlaps.Config
import com.arkhins.ctrlaps.LocalApp
import com.arkhins.ctrlaps.ui.AppViewModel
import com.arkhins.ctrlaps.ui.components.Avatar
import com.arkhins.ctrlaps.ui.components.GhostButton
import com.arkhins.ctrlaps.ui.components.KeyValue
import com.arkhins.ctrlaps.ui.components.Panel
import androidx.compose.ui.platform.LocalContext
import com.arkhins.ctrlaps.ui.components.openPhoto
import com.arkhins.ctrlaps.ui.components.SearchPill
import com.arkhins.ctrlaps.ui.components.StatusChip
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import com.arkhins.ctrlaps.ui.theme.SnowSoft
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The account tab: photo, code and QR (with the scanner in its corner), then a menu into Account, Archive, Storage, Settings and About. */
@Composable
fun AccountScreen(
    vm: AppViewModel,
    onScan: () -> Unit,
    onArchive: () -> Unit,
    onDetails: () -> Unit,
    onStorage: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onActivity: () -> Unit = {},
) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val me = vm.me ?: return
    val u = me.user
    val qr = rememberQr(me.qrUrl)
    var confirmOut by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    // Debug builds: `--es sheet qr` opens the QR sheet over adb (see DebugHooks).
    if (BuildConfig.DEBUG) {
        val asked by com.arkhins.ctrlaps.ui.DebugHooks.sheet.collectAsState()
        androidx.compose.runtime.LaunchedEffect(asked) {
            when (asked) {
                "qr" -> showQr = true
                "photo" -> app.api.absolute(u.photoUrl)?.let { PhotoPreview.show(it, u.displayName) }
                else -> return@LaunchedEffect
            }
            com.arkhins.ctrlaps.ui.DebugHooks.sheet.value = null
        }
    }
    // Settings and Storage look things up on the phone; done now, in the background, they open already filled.
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching { preloadSettings(context) }
            runCatching { preloadStorage(app) }
        }
    }

    val open = LocalOpen.current
    var query by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Search, and beside it the QR: your code for the gate, and Verify someone, in one sheet.
        Row(verticalAlignment = Alignment.CenterVertically) {
            SearchPill(query, "Search settings", Modifier.weight(1f)) { query = it }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(NightPanel)
                    .border(1.dp, NightLine, RoundedCornerShape(18.dp))
                    .clickable { showQr = true },
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_scan), contentDescription = "My QR code", tint = Gold, modifier = Modifier.size(28.dp)) }
        }
        if (query.isNotBlank()) {
            val found = searchSettings(query, dev = me.isDev)
            if (found.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No settings match", style = MaterialTheme.typography.titleMedium, color = Snow)
                    Text("Try another word, like theme, password or storage.", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
                }
            } else {
                Column { found.forEach { e ->
                    MenuRow(e.title, e.path, icon = rememberVectorPainter(Icons.Outlined.Search)) {
                        query = ""
                        // The QR is a sheet on this page, not a screen of its own.
                        if (e.route == "#qr") showQr = true else open(e.route)
                    }
                }  }
            }
            return@Column
        }
        // Your photo only shows here; it is changed in Account details → Edit details.
        ProfileBanner(app.api.absolute(u.photoUrl), u.displayName, u.roleLabel + (u.teamName?.let { " · $it" } ?: "")) { StatusChip(u.status, u.statusLabel) }

        // Users (no role yet) follow race categories as fans; everyone else has theirs by role.
        if (u.role == "user") FollowCategoriesPanel(vm)

        // Arkhime-style settings list: flat rows with an accent icon, a bold title, a quieter line and an arrow.
        Column {
            // The QR in a sheet, a tap away at the gate; the list stays on the first screen.
            MenuRow("Account", "Email, date of birth and password", icon = rememberVectorPainter(Icons.Outlined.Person), onClick = onDetails)
            MenuRow("Notifications", "Popups, each kind, a test and the history", icon = painterResource(R.drawable.ic_bell)) { open("notification-settings") }
            MenuRow("Archive", "Past seasons: their weekends, channels and messages", icon = painterResource(R.drawable.ic_archive), onClick = onArchive)
            MenuRow("Storage", "What CTR[L]APS keeps on this phone", icon = painterResource(R.drawable.ic_download), onClick = onStorage)
            MenuRow("Settings", "Permissions, theme and email", icon = rememberVectorPainter(Icons.Outlined.Settings), onClick = onSettings)
            // The support team only: who did what, and when.
            if (me.isDev) MenuRow("Activity log", "Who did what, and when", icon = rememberVectorPainter(Icons.AutoMirrored.Outlined.List), onClick = onActivity)
            // Help in one place: the FAQs, the support form and your tickets.
            val support = vm.me?.unreadSupport ?: 0
            MenuRow(
                "Help & support",
                if (support > 0) "$support new ${if (support == 1) "reply" else "replies"} from support" else "FAQs, the support form and your tickets",
                icon = rememberVectorPainter(Icons.Outlined.Call),
                highlight = support > 0,
            ) { open("support") }
            val update = vm.updateInfo
            MenuRow(
                "About",
                if (update != null) "v${update.version} is available" else "Version, updates, the team, terms and privacy",
                icon = rememberVectorPainter(Icons.Outlined.Info),
                highlight = update != null,
                onClick = onAbout,
            )
            MenuRow("Sign out", "Your messages and files stay on this phone", icon = rememberVectorPainter(Icons.AutoMirrored.Outlined.ExitToApp), danger = true, arrow = false) {
                // Asked first, unless they said not to be.
                if (DontAsk.skipped(context, "sign-out")) scope.launch { vm.signOut() } else confirmOut = true
            }
        }

        // The version, and the organisation's main domain (MAIN_DOMAIN at build time) when there is one.
        Text(
            "${Config.APP_NAME} v${BuildConfig.VERSION_NAME}${if (Config.MAIN_DOMAIN.isNotBlank()) " · ${Config.MAIN_DOMAIN}" else ""}",
            style = MaterialTheme.typography.labelSmall,
            color = SnowFaint,
            textAlign = TextAlign.Center,
            // A long press copies the device details for a ticket.
            modifier = Modifier.fillMaxWidth().then(Modifier.copiesDeviceInfo()).padding(top = 4.dp, bottom = 8.dp),
        )
    }

    if (showQr) {
        AboutSheet("My QR code", onClose = { showQr = false }, expanded = true) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // The white square is there from the first frame; the code fills it a moment later if it wasn't ready.
                Box(Modifier.background(Color.White, RoundedCornerShape(16.dp)).padding(12.dp).size(240.dp)) {
                    if (qr != null) Image(qr.asImageBitmap(), contentDescription = "Your QR code", modifier = Modifier.size(240.dp))
                }
                Spacer(Modifier.height(14.dp))
                KeyValue("Account code", u.verifyCode, mono = true, copyable = true)
                Spacer(Modifier.height(16.dp))
                // Checking someone else starts from the same place.
                OutlinedButton(
                    onClick = { showQr = false; onScan() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    border = BorderStroke(1.dp, NightLine),
                ) {
                    Icon(rememberVectorPainter(Icons.Outlined.Search), contentDescription = null, tint = Gold, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Verify someone", color = Snow, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (confirmOut) {
        QuestionSheet(
            "Sign out?",
            "Your messages and files stay on this phone. Sign in again any time with your email and password.",
            confirm = "Sign out",
            danger = true,
            dontAskKey = "sign-out",
            onConfirm = { confirmOut = false; scope.launch { vm.signOut() } },
            onDismiss = { confirmOut = false },
        )
    }
}

/**
 * One line of a menu: an accent icon (when given), a bold title, a quieter line under it, and an arrow. [danger] is
 * for Sign out and the like; [arrow] false for a line that acts rather than opens a page.
 */
@Composable
fun MenuRow(
    title: String,
    hint: String,
    highlight: Boolean = false,
    icon: Painter? = null,
    danger: Boolean = false,
    arrow: Boolean = true,
    onClick: () -> Unit,
) {
    val tone = if (danger) Danger else Gold
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = if (icon != null) 14.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, lineHeight = 20.sp), fontWeight = FontWeight.Bold, color = if (danger) Danger else Snow)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = if (highlight) Gold else SnowSoft.copy(alpha = 0.8f))
        }
        if (arrow) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = if (icon != null) tone else SnowFaint)
    }
}

/** Something a search can find: where it lives ("Settings › Theme"), the screen that opens, and other words for it. */
private data class Findable(val title: String, val path: String, val route: String, val words: String = "", val devOnly: Boolean = false)

private val FINDABLE = listOf(
    Findable("Account details", "Account", "details", "name profile date of birth phone contact"),
    Findable("Change password", "Account", "details", "password security"),
    Findable("Change email", "Account", "details", "email address"),
    Findable("Notifications", "Account › Notifications", "notification-settings", "alerts popups channels chats announcements results"),
    Findable("Send a test notification", "Account › Notifications", "notification-settings", "test check alerts popups arrive"),
    Findable("Notification history", "Account › Notifications", "notifications", "history bell past alerts"),
    Findable("Archive", "Account › Archive", "archive", "past seasons old"),
    Findable("Storage", "Account › Storage", "storage", "space files phone clear"),
    Findable("Automatic downloads", "Account › Storage", "storage", "download photos audio documents data"),
    Findable("Permissions", "Settings › Permissions", "permissions", "allow camera location microphone photos"),
    Findable("Notification permission", "Settings › Permissions", "permissions", "popups alerts channels"),
    Findable("Run in background", "Settings › Permissions", "permissions", "battery optimisation sleep"),
    Findable("Battery saver", "Settings › Permissions", "permissions", "xiaomi no restrictions"),
    Findable("Autostart", "Settings › Permissions", "permissions", "start recent apps"),
    Findable("Install updates", "Settings › Permissions", "permissions", "unknown apps install"),
    Findable("Theme", "Settings › Theme", "theme", "dark light mode system appearance"),
    Findable("Pure black", "Settings › Theme", "theme", "amoled oled black dark battery"),
    Findable("Accent colour", "Settings › Theme", "theme", "color gold orange green blue violet"),
    Findable("Interface", "Settings › Interface", "interface", "look feel motion"),
    Findable("Animations", "Settings › Interface", "interface", "animation motion speed slow fast off reduce transitions"),
    Findable("Blur", "Settings › Interface", "interface", "blur dialogs banner effects"),
    Findable("Haptics", "Settings › Interface", "interface", "vibration vibrate buzz touch feedback"),
    Findable("Email", "Settings › Email", "email-settings", "mail newsletters unsubscribe"),
    Findable("Delete account", "Settings › Delete account", "delete-account", "remove erase close"),
    Findable("About", "About", "about", "version"),
    Findable("Check for updates", "About", "about", "update version new"),
    Findable("What's new", "About › What's new", "changelog", "changelog release notes changes"),
    Findable("Help & support", "Help & support", "support", "help contact support ticket form"),
    Findable("FAQs", "Help & support › FAQs", "support/faqs", "questions help faq"),
    Findable("Tickets", "Help & support › Tickets", "support/tickets", "requests help"),
    Findable("Terms and conditions", "About", "legal/terms", "rules legal"),
    Findable("Privacy Policy", "About", "legal/privacy", "data privacy legal"),
    Findable("License", "About › License", "license", "apache open source"),
    Findable("My QR code", "Account › QR", "#qr", "qr code id card gate show my account code"),
    Findable("Verify someone", "Account › QR › Verify someone", "scanner", "qr scan scanner code check gate id verify"),
    Findable("Activity log", "Account › Activity log", "activity", "audit who did what", devOnly = true),
)

/** Settings matching [query], best first: the title exactly, then starting with it, containing it, the path, other words. */
private fun searchSettings(query: String, dev: Boolean): List<Findable> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    val words = q.split(Regex("\\s+"))
    return FINDABLE.filter { dev || !it.devOnly }.mapNotNull { f ->
        val title = f.title.lowercase()
        var score = when {
            title == q -> 100
            title.startsWith(q) -> 75
            q in title -> 50
            else -> 0
        }
        if (q in f.path.lowercase()) score += 15
        val hay = "$title ${f.path.lowercase()} ${f.words}"
        score += words.count { w -> w in hay } * 10
        if (score > 0 && words.all { it in hay }) f to score else null
    }.sortedByDescending { it.second }.map { it.first }
}

/** QR bitmaps already drawn, so a screen coming back (as its page slides away) shows its code at once. */
private val qrCache = android.util.LruCache<String, Bitmap>(8)

/**
 * The QR for [text]: at once if it was drawn before, otherwise drawn off the main thread just after
 * the screen's first frame, so a page sliding in doesn't wait for it.
 */
@Composable
fun rememberQr(text: String?): Bitmap? {
    val qr by produceState(text?.let { qrCache.get("512:$it") }, text) {
        if (value == null && text != null) value = withContext(Dispatchers.Default) { qrBitmap(text) }
    }
    return qr
}

/** The account's QR as a bitmap: dark modules on white, drawn in one pass and kept for next time. */
fun qrBitmap(text: String, size: Int = 512): Bitmap? = qrCache.get("$size:$text") ?: runCatching {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) AColor.BLACK else AColor.WHITE }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565).also { qrCache.put("$size:$text", it) }
}.getOrNull()

/** A user picks the race categories they follow: those channels' posts come to Home, their sessions show under Mine. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FollowCategoriesPanel(vm: AppViewModel) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<FollowingResponse?>(null) }
    var ids by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { app.store.get("/api/me/following", FollowingResponse.serializer()) { data = it; ids = it.categoryIds } }
            .onSuccess { data = it; ids = it.categoryIds }
    }
    val d = data ?: return
    if (!d.canFollow || d.categories.isEmpty()) return
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Follow categories", style = MaterialTheme.typography.titleMedium, color = Snow)
            Text("Their channel posts come to your Home, and their sessions show under Mine.", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Danger) }
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                d.categories.forEach { c ->
                    Chip(c.code, categoryColor(c), filled = c.id in ids) {
                        if (busy) return@Chip
                        val before = ids
                        val next = if (c.id in ids) ids - c.id else ids + c.id
                        ids = next
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                ids = app.api.put("/api/me/following", CategoryIdsResponse.serializer()) {
                                    putJsonArray("categoryIds") { next.forEach { add(it) } }
                                }.categoryIds
                                vm.refreshMe()
                            } catch (e: Exception) {
                                ids = before
                                error = e.message ?: "Could not save."
                            } finally {
                                busy = false
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The profile at the top of the Account tab, after Arkhime's account card: the photo blurred behind, the name in the
 * accent, the role and status, and the photo itself on the right.
 */
@Composable
fun ProfileBanner(photo: String?, name: String, role: String, onChange: (() -> Unit)? = null, onRemove: (() -> Unit)? = null, status: @Composable () -> Unit) {
    val context = LocalContext.current
    var bounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val blur by com.arkhins.ctrlaps.ui.theme.InterfaceSetting.blur.collectAsState()
    Box(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(NightHigh)
            .border(1.dp, NightLine, RoundedCornerShape(18.dp)),
    ) {
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().then(if (blur) Modifier.blur(28.dp) else Modifier),
            )
        }
        // Darker on the left, where the words are, so they read on any photo.
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.78f), Color.Black.copy(alpha = 0.35f)))))
        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(role, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                status()
            }
            Spacer(Modifier.width(12.dp))
            // A tap: the photo large (Change and Remove for those who may); none yet, the gallery or "No photo yet".
            Box(
                Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .onGloballyPositioned { bounds = it.boundsInWindow() }
                    .clickable { openPhoto(context, photo, name, onChange, onRemove, from = bounds) },
            ) {
                if (photo != null) AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                else Box(Modifier.matchParentSize().background(NightHighest), contentAlignment = Alignment.Center) {
                    Text(name.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, color = Snow)
                }
            }
        }
    }
}
