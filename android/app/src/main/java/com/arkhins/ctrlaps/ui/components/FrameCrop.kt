package com.arkhins.ctrlaps.ui.components

import androidx.compose.ui.draw.clipToBounds
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.Night
import kotlin.math.min
import kotlin.math.roundToInt

private const val MAX_ZOOM = 5f
private const val OUT_WIDTH = 1600

/** The frame in the photo's own pixels, updated as it is drawn; read only when Save is tapped. */
private class FrameHolder { var rect: android.graphics.Rect? = null }

/**
 * Crop a weekend's photo to the header's shape before it is uploaded, full screen: drag to move it, pinch to zoom.
 * The frame previews the header itself (the fade into the page at its foot), and dashed lines mark the narrower band
 * Home's next-race card shows. Hands back the framed part, no wider than 1600 px.
 */
@Composable
fun HeaderCropDialog(source: Bitmap, aspect: Float = 16f / 9f, homeBand: Float = 0.6f, onCancel: () -> Unit, onDone: (Bitmap) -> Unit) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
            val crop = remember { FrameHolder() }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) { Text("Cancel", color = Color.White) }
                Text("Crop the header", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                TextButton(onClick = {
                    val r = crop.rect ?: return@TextButton
                    val part = Bitmap.createBitmap(source, r.left, r.top, r.width(), r.height())
                    val w = min(OUT_WIDTH, part.width)
                    onDone(if (w == part.width) part else Bitmap.createScaledBitmap(part, w, (w / aspect).roundToInt(), true))
                }) { Text("Save", color = Gold, fontWeight = FontWeight.Bold) }
            }
            // Clipped: a tall photo must not spill over the bar with Cancel and Save.
            BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
                val density = LocalDensity.current
                val areaW = with(density) { maxWidth.toPx() }
                val areaH = with(density) { maxHeight.toPx() }
                val frameW = areaW - with(density) { 32.dp.toPx() }
                val frameH = frameW / aspect
                val at = Offset((areaW - frameW) / 2f, (areaH - frameH) / 2f)
                // The picture always covers the frame.
                val base = maxOf(frameW / source.width, frameH / source.height)
                val image = remember(source) { source.asImageBitmap() }
                val page = Night

                var zoom by remember(source, frameW) { mutableFloatStateOf(1f) }
                var off by remember(source, frameW) {
                    mutableStateOf(Offset((frameW - source.width * base) / 2f, (frameH - source.height * base) / 2f))
                }
                fun clamp(o: Offset, k: Float) = Offset(
                    o.x.coerceIn(min(0f, frameW - source.width * k), 0f),
                    o.y.coerceIn(min(0f, frameH - source.height * k), 0f),
                )
                val k = base * zoom
                crop.rect = run {
                    val left = (-off.x / k).roundToInt().coerceIn(0, source.width - 1)
                    val top = (-off.y / k).roundToInt().coerceIn(0, source.height - 1)
                    val w = (frameW / k).roundToInt().coerceIn(1, source.width - left)
                    val h = (frameH / k).roundToInt().coerceIn(1, source.height - top)
                    android.graphics.Rect(left, top, left + w, top + h)
                }

                Canvas(
                    Modifier.fillMaxSize().clipToBounds().pointerInput(source, frameW) {
                        detectTransformGestures { centroid, pan, gestureZoom, _ ->
                            val oldK = base * zoom
                            val newZoom = (zoom * gestureZoom).coerceIn(1f, MAX_ZOOM)
                            val newK = base * newZoom
                            // Zoom about the fingers: the point under them stays under them.
                            val c = centroid - at
                            val moved = Offset(c.x - (c.x - off.x) * newK / oldK, c.y - (c.y - off.y) * newK / oldK) + pan
                            zoom = newZoom
                            off = clamp(moved, newK)
                        }
                    },
                ) {
                    drawImage(
                        image,
                        dstOffset = IntOffset((at.x + off.x).roundToInt(), (at.y + off.y).roundToInt()),
                        dstSize = IntSize((source.width * k).roundToInt(), (source.height * k).roundToInt()),
                    )
                    // Dim what falls outside the frame.
                    clipRect(at.x, at.y, at.x + frameW, at.y + frameH, ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.65f)) }
                    // Inside it, the header as it will look: the photo fading into the page at its foot.
                    drawRect(
                        Brush.verticalGradient(0f to Color.Transparent, 0.45f to page.copy(alpha = 0.35f), 1f to page, startY = at.y, endY = at.y + frameH),
                        topLeft = at,
                        size = Size(frameW, frameH),
                    )
                    drawRoundRect(Color.White, topLeft = at, size = Size(frameW, frameH), cornerRadius = CornerRadius(18.dp.toPx()), style = Stroke(2.dp.toPx()))
                    // Home's card shows the middle band of it.
                    val bandH = frameH * homeBand
                    val bandTop = at.y + (frameH - bandH) / 2f
                    val dash = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))
                    drawLine(Gold, Offset(at.x, bandTop), Offset(at.x + frameW, bandTop), strokeWidth = 1.5f.dp.toPx(), pathEffect = dash)
                    drawLine(Gold, Offset(at.x, bandTop + bandH), Offset(at.x + frameW, bandTop + bandH), strokeWidth = 1.5f.dp.toPx(), pathEffect = dash)
                }
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Drag to move · pinch to zoom", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                    Text("Between the gold lines: what Home's next-race card shows", style = MaterialTheme.typography.bodySmall, color = Gold)
                }
            }
        }
    }
}
