package com.arkhins.ctrlaps.ui.screens

import com.arkhins.ctrlaps.ui.theme.OnGold
import androidx.compose.foundation.clickable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.arkhins.ctrlaps.data.AutoDownload
import com.arkhins.ctrlaps.ui.theme.Night
import androidx.compose.ui.geometry.CornerRadius
import com.arkhins.ctrlaps.ui.theme.Gold
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.arkhins.ctrlaps.ui.theme.NightLine
import com.arkhins.ctrlaps.ui.theme.SnowSoft
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.LocalApp
import com.arkhins.ctrlaps.ui.bytes
import com.arkhins.ctrlaps.ui.components.GhostButton
import com.arkhins.ctrlaps.ui.components.KeyValue
import com.arkhins.ctrlaps.ui.components.Panel
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bytes kept on the phone: chat messages, attachments, and every other page. */
private data class Kept(val chats: Long, val media: Long, val pages: Long) {
    val total get() = chats + media + pages
}

/** The sizes as last measured, shown at once the next time the page opens. */
@Volatile private var lastKept = Kept(0, 0, 0)

private fun measure(app: com.arkhins.ctrlaps.CtrlapsApplication) =
    Kept(app.chatCache.sizeBytes(), app.chatMedia.sizeBytes(), app.store.sizeBytes()).also { lastKept = it }

/** Measures ahead of time (from the Account tab, off the main thread), so Storage opens with its numbers. */
fun preloadStorage(app: com.arkhins.ctrlaps.CtrlapsApplication) {
    measure(app)
}

/** How much CTR[L]APS keeps on this phone, by kind, with a way to clear it (it comes back from the server). */
@Composable
fun StorageScreen() {
    val app = LocalApp.current
    val landed by app.chatMedia.version.collectAsState()
    var cleared by remember { mutableIntStateOf(0) }
    // Walking the folders takes a moment: done off the main thread, after the first frame.
    var kept by remember { mutableStateOf(lastKept) }
    LaunchedEffect(landed, cleared) { kept = withContext(Dispatchers.IO) { measure(app) } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The phone as a whole, measured once: its size, and how much of it is free.
        val phone = remember(kept) { runCatching { StatFs(Environment.getDataDirectory().path).let { it.totalBytes to it.availableBytes } }.getOrNull() }
        val parts = listOf(
            Triple("Chat messages", kept.chats, ChatsColor),
            Triple("Photos, documents and voice notes", kept.media, MediaColor),
            Triple("Announcements, channels, schedule and people", kept.pages, PagesColor),
        )
        Panel {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(bytes(kept.total), style = MaterialTheme.typography.headlineMedium, color = Snow)
                        Text("Kept on this phone", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
                        Spacer(Modifier.height(14.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            parts.forEach { (label, size, color) ->
                                Column {
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                                    Text(bytes(size), style = MaterialTheme.typography.bodyMedium, color = Snow)
                                    Spacer(Modifier.height(4.dp))
                                    Box(Modifier.width(40.dp).height(4.dp).background(color, RoundedCornerShape(2.dp)))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Donut(parts.map { it.second to it.third }, bytes(kept.total))
                }
                if (phone != null) {
                    Spacer(Modifier.height(18.dp))
                    PhoneBar(total = phone.first, free = phone.second, ours = kept.total)
                }
            }
        }
        AutoDownloadPanel(app.chatMedia.auto)
        Panel {
            Column {
                Text(
                    "Everything is kept so it opens at once and without signal. Clearing it frees the space; it downloads again in the background.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SnowFaint,
                )
                Spacer(Modifier.height(10.dp))
                GhostButton("Clear", enabled = kept.total > 0) {
                    app.chatCache.wipe()
                    app.chatMedia.wipe()
                    app.store.wipe()
                    cleared++
                }
            }
        }
    }
}

private val ChatsColor = Color(0xFF4ADE80)
private val MediaColor = Color(0xFF3B82F6)
private val PagesColor = Color(0xFFA855F7)

/** A ring split by size, each part in its colour with a small gap, the total in the middle. */
@Composable
private fun Donut(parts: List<Pair<Long, Color>>, center: String) {
    val total = parts.sumOf { it.first }.toFloat()
    Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(128.dp)) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            if (total <= 0f) {
                drawArc(NightLine, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
                return@Canvas
            }
            val shown = parts.filter { it.first > 0 }
            val gap = if (shown.size > 1) 4f else 0f
            // Every part that holds anything gets at least a visible sliver; the rest share what is left.
            val raw = shown.map { maxOf(it.first / total * 360f, 10f) }
            val scale = 360f / raw.sum()
            var start = -90f
            shown.forEachIndexed { i, (_, color) ->
                val sweep = raw[i] * scale
                drawArc(color, start + gap / 2f, sweep - gap, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        Text(center, style = MaterialTheme.typography.titleSmall, color = Snow)
    }
}

/**
 * The phone's size as it is sold (128 GB, 256 GB…), the way Android's own Settings shows it: the space
 * apps can use, rounded up to the next power of two in decimal gigabytes.
 */
private fun advertisedSize(dataBytes: Long): String {
    val gb = dataBytes / 1_000_000_000.0
    var size = 1L
    while (size < gb) size *= 2
    return if (size >= 1000) "${size / 1000} TB" else "$size GB"
}

private val OtherColor = Color(0xFF6B6B73)

/**
 * The whole phone as one bar: CTR[L]APS in gold (always at least a sliver, so it can be found),
 * everything else in grey, the free space empty. Under it, what each part is and the phone's size.
 */
@Composable
private fun PhoneBar(total: Long, free: Long, ours: Long) {
    if (total <= 0) return
    val used = (total - free).coerceIn(0, total)
    val others = (used - ours).coerceAtLeast(0)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Phone storage", style = MaterialTheme.typography.labelMedium, color = SnowSoft, modifier = Modifier.weight(1f))
            Text("${bytes(ours)} of ${advertisedSize(total)}", style = MaterialTheme.typography.labelMedium, color = Snow)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(4.dp)) {
            val r = CornerRadius(size.height / 2f)
            drawRoundRect(NightLine, cornerRadius = r)
            val usedW = size.width * used / total
            if (usedW > 0f) drawRoundRect(OtherColor, size = Size(usedW, size.height), cornerRadius = r)
            val oursW = maxOf(size.width * ours / total, if (ours > 0) 4.dp.toPx() else 0f)
            if (oursW > 0f) drawRoundRect(Gold, size = Size(oursW, size.height), cornerRadius = r)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legend(Gold, "CTR[L]APS ${bytes(ours)}")
            Legend(OtherColor, "Other ${bytes(others)}")
            Legend(NightLine, "Free ${bytes(free)}")
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = SnowFaint)
    }
}

/** Which kinds download by themselves as they arrive. Off: they wait for a tap (a photo shows blurred until then). */
@Composable
private fun AutoDownloadPanel(auto: AutoDownload) {
    val photos by auto.photos.collectAsState()
    val audio by auto.audio.collectAsState()
    val documents by auto.documents.collectAsState()
    Panel {
        Column {
            Text("Automatic downloads", style = MaterialTheme.typography.titleMedium, color = Snow)
            Spacer(Modifier.height(2.dp))
            Text("When off, they download only when you tap them.", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
            Spacer(Modifier.height(6.dp))
            SwitchRow("Photos", photos, auto::setPhotos)
            SwitchRow("Voice notes and audio", audio, auto::setAudio)
            SwitchRow("Documents", documents, auto::setDocuments)
        }
    }
}

@Composable
private fun SwitchRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Snow, modifier = Modifier.weight(1f))
        Switch(
            checked = on,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = OnGold, checkedTrackColor = Gold, uncheckedThumbColor = SnowFaint, uncheckedTrackColor = NightLine, uncheckedBorderColor = NightLine),
        )
    }
}
