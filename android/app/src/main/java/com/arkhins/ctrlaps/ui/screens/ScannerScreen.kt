package com.arkhins.ctrlaps.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import com.arkhins.ctrlaps.R
import com.arkhins.ctrlaps.data.RecentCheck
import com.arkhins.ctrlaps.ui.components.Divider
import com.arkhins.ctrlaps.ui.components.SectionTitle
import com.arkhins.ctrlaps.ui.theme.Danger
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.NightLine
import com.arkhins.ctrlaps.ui.theme.NightPanel
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.arkhins.ctrlaps.LocalApp
import com.arkhins.ctrlaps.data.IdResponse
import com.arkhins.ctrlaps.data.Verified
import com.arkhins.ctrlaps.ui.components.Avatar
import com.arkhins.ctrlaps.ui.components.ErrorText
import com.arkhins.ctrlaps.ui.components.Field
import com.arkhins.ctrlaps.ui.components.GhostButton
import com.arkhins.ctrlaps.ui.components.GoldButton
import com.arkhins.ctrlaps.ui.components.IdCard
import com.arkhins.ctrlaps.ui.components.Panel
import com.arkhins.ctrlaps.ui.components.StatusChip
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import com.arkhins.ctrlaps.ui.theme.SnowSoft
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * Point the camera at someone's QR, or type their code; the result is who they are and their status.
 * Tapping the code box turns the screen into code entry ([typing]): the camera closes, eight boxes take
 * the code, and the header's scan icon brings the camera back.
 */
@Composable
fun ScannerScreen(initialToken: String? = null, typing: Boolean, onTyping: (Boolean) -> Unit, onOpenChat: (String) -> Unit = {}) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var scanning by remember { mutableStateOf(initialToken == null) }
    var code by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Verified?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted && initialToken == null) ask.launch(Manifest.permission.CAMERA) }

    fun lookup(query: String) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                result = app.api.get("/api/verify?$query", Verified.serializer()).also { app.verifyHistory.add(it) }
                scanning = false
            } catch (e: Exception) {
                error = e.message ?: "No match."
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(initialToken) { if (initialToken != null) lookup("token=${URLEncoder.encode(initialToken, "UTF-8")}") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (typing) {
            Text("Type the 8-character account code shown under their QR on their Account page.", style = MaterialTheme.typography.bodyMedium, color = SnowSoft)
            Spacer(Modifier.height(8.dp))
            CodeBoxes(code, enabled = !busy) { typed ->
                code = typed
                error = null
                if (typed.length == 8) lookup("code=${URLEncoder.encode(typed.take(4) + "-" + typed.drop(4), "UTF-8")}")
            }
            if (busy) Text("Checking…", style = MaterialTheme.typography.labelMedium, color = SnowFaint, modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (scanning && granted) {
            CameraPreview(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))) { value ->
                val token = Regex("/v/([A-Za-z0-9_-]+)").find(value)?.groupValues?.get(1)
                lookup(if (token != null) "token=${URLEncoder.encode(token, "UTF-8")}" else "code=${URLEncoder.encode(value, "UTF-8")}")
            }
        } else if (scanning && !granted) {
            Panel {
                Column {
                    Text("The camera is needed to scan. Allow it, or type the code below.", color = SnowSoft, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    GhostButton("Allow camera") { ask.launch(Manifest.permission.CAMERA) }
                }
            }
        } else {
            GhostButton("Scan again") { result = null; error = null; scanning = true }
        }
        if (!typing) {
            // The same eight boxes, empty; a tap turns the screen into code entry.
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { code = ""; error = null; result = null; onTyping(true) }.padding(vertical = 4.dp)) {
                CodeRow("", current = null)
            }
        }
        ErrorText(error)
        if (result == null) RecentChecks { person -> lookup("code=${URLEncoder.encode(person.verifyCode, "UTF-8")}") }
        result?.let { v ->
            IdCard(v)
            // No chat button for someone with no role yet: they have no chats.
            if (v.status == "active" && v.role != "user" && v.id != vm_me_id(app)) {
                var chatError by remember(v.id) { mutableStateOf<String?>(null) }
                var opening by remember(v.id) { mutableStateOf(false) }
                GoldButton(if (opening) "Opening chat…" else "Chat with ${v.name ?: "this person"}", Modifier.fillMaxWidth(), enabled = !opening) {
                    opening = true
                    chatError = null
                    scope.launch {
                        try {
                            onOpenChat(app.api.post("/api/conversations", IdResponse.serializer()) { put("memberId", v.id) }.id)
                        } catch (e: Exception) {
                            chatError = e.message ?: "Could not open a chat."
                            opening = false
                        }
                    }
                }
                ErrorText(chatError)
            }
        }
    }
}

@Composable
fun VerifiedCard(v: Verified) {
    val app = LocalApp.current
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(app.api.absolute(v.photoUrl), v.name ?: "?", 64)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(v.name ?: "Profile not completed", style = MaterialTheme.typography.titleLarge, color = Snow)
                Text(v.roleLabel + (v.teamName?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyMedium, color = SnowSoft)
                Text(v.verifyCode, style = MaterialTheme.typography.labelSmall, color = SnowFaint)
            }
            StatusChip(v.status, v.statusLabel)
        }
    }
}

/** CameraX preview with ML Kit reading QR codes from every frame. Calls [onFound] once per distinct value. */
@Composable
private fun CameraPreview(modifier: Modifier, onFound: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()) }
    val last = remember { arrayOfNulls<String>(1) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            executor.shutdown()
            scanner.close()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val view = PreviewView(ctx)
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { image ->
                    analyse(scanner, image) { value ->
                        if (value != last[0]) {
                            last[0] = value
                            onFound(value)
                        }
                    }
                }
                provider.unbindAll()
                runCatching { provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis) }
            }, ContextCompat.getMainExecutor(ctx))
            view
        },
    )
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun analyse(scanner: BarcodeScanner, image: ImageProxy, onValue: (String) -> Unit) {
    val media = image.image
    if (media == null) {
        image.close()
        return
    }
    scanner.process(InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees))
        .addOnSuccessListener { codes -> codes.firstOrNull()?.rawValue?.let(onValue) }
        .addOnCompleteListener { image.close() }
}

/** The signed-in person's id, from the session the app keeps; blank when unknown. */
private fun vm_me_id(app: com.arkhins.ctrlaps.CtrlapsApplication): String = app.currentUserId ?: ""

/**
 * Eight boxes for an account code, a dash between the fourth and fifth, as WhatsApp asks for a linking
 * code: one hidden text field takes the typing (letters and digits, upper-cased) and the boxes show it,
 * the next one to fill outlined in gold. The keyboard opens at once.
 */
@Composable
private fun CodeBoxes(code: String, enabled: Boolean, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = code,
        onValueChange = { v -> onChange(v.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(8)) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        decorationBox = { CodeRow(code, current = if (enabled) code.length else null) },
    )
}

/** The eight boxes and the dash between them, showing [code]; the box at [current] (the next to fill) is outlined in gold. */
@Composable
private fun CodeRow(code: String, current: Int?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until 8) {
            if (i == 4) Text("-", style = MaterialTheme.typography.titleLarge, color = Snow, modifier = Modifier.padding(horizontal = 8.dp))
            val ch = code.getOrNull(i)
            val here = i == current
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = 34.dp, height = 44.dp)
                    .background(NightPanel, RoundedCornerShape(8.dp))
                    .border(if (here) 2.dp else 1.dp, if (here) Gold else NightLine, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) { if (ch != null) Text(ch.toString(), style = MaterialTheme.typography.titleLarge, color = Snow) }
        }
    }
}

/**
 * Who this phone has checked, under the scanner and the code boxes: starred people first, then the rest,
 * newest first. A tap checks the person again (their status now, not as it was); the star keeps them at hand.
 */
@Composable
private fun RecentChecks(onCheck: (Verified) -> Unit) {
    val app = LocalApp.current
    val history = app.verifyHistory
    val checks by history.checks.collectAsState()
    val starred by history.starred.collectAsState()
    if (checks.isEmpty()) return
    val (pinned, rest) = checks.partition { it.person.id in starred }

    @Composable
    fun Section(title: String, list: List<RecentCheck>, action: (@Composable () -> Unit)? = null) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(title, Modifier.weight(1f))
            action?.invoke()
        }
        Panel(padding = PaddingValues(4.dp)) {
            Column {
                list.forEachIndexed { i, c ->
                    if (i > 0) Divider()
                    val p = c.person
                    Row(
                        Modifier.fillMaxWidth().clickable { onCheck(p) }.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(app.api.absolute(p.photoUrl), p.name ?: p.verifyCode, 44)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name ?: "Profile not completed", style = MaterialTheme.typography.titleSmall, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${p.roleLabel} · ${p.verifyCode}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (p.role == "user") Danger else SnowFaint,
                                maxLines = 1,
                            )
                        }
                        Text(checkedWhen(c.at), style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                        val on = p.id in starred
                        IconButton(onClick = { history.toggleStar(p.id) }) {
                            Icon(
                                painterResource(if (on) R.drawable.ic_star else R.drawable.ic_star_border),
                                contentDescription = if (on) "Unstar" else "Star",
                                tint = if (on) Gold else SnowFaint,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (pinned.isNotEmpty()) Section("STARRED", pinned)
    if (rest.isNotEmpty()) Section("RECENT", rest) {
        Text("Clear", style = MaterialTheme.typography.labelMedium, color = SnowFaint, modifier = Modifier.clickable { history.clearUnstarred() }.padding(6.dp))
    }
}

/** "11:14 pm" today, "Yesterday", or "12 Sep". */
private fun checkedWhen(at: Long): String {
    val then = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault())
    val today = java.time.LocalDate.now()
    return when (then.toLocalDate()) {
        today -> then.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.ENGLISH)).lowercase()
        today.minusDays(1) -> "Yesterday"
        else -> then.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH))
    }
}
