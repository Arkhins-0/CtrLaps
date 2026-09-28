package com.arkhins.ctrlaps.ui.screens

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
        val phoneSize = remember { runCatching { advertisedSize(StatFs(Environment.getDataDirectory().path).totalBytes) }.getOrNull() }
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
                if (phoneSize != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "${bytes(kept.total)} of $phoneSize",
                        style = MaterialTheme.typography.labelMedium,
                        color = SnowSoft,
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
        }
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
