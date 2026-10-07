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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MAX_ZOOM = 5f
private const val OUT = 900

/** The square in the photo's own pixels, updated as it is drawn; read only when Done is tapped. */
private class CropHolder { var rect: android.graphics.Rect? = null }

/**
 * Crop a chosen photo to a square before it is used, full screen: drag to move
 * it, pinch to zoom. The picture always covers the square; a circle shows how
 * it will look as a profile photo. Hands back a square no larger than 900 px.
 */
@Composable
fun SquareCropDialog(source: Bitmap, onCancel: () -> Unit, onDone: (Bitmap) -> Unit) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
            val crop = remember { CropHolder() }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.TextButton(onClick = onCancel) { Text("Cancel", color = Color.White) }
                Text("Crop photo", style = MaterialTheme.typography.titleMedium, color = Color.White)
                GoldButton("Done") {
                    val r = crop.rect ?: return@GoldButton
                    val square = Bitmap.createBitmap(source, r.left, r.top, r.width(), r.height())
                    val side = min(OUT, square.width)
                    onDone(if (side == square.width) square else Bitmap.createScaledBitmap(square, side, side, true))
                }
            }
            // Clipped: a tall photo must not spill over the bar with Cancel and Save.
            BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
                val density = LocalDensity.current
                val areaW = with(density) { maxWidth.toPx() }
                val areaH = with(density) { maxHeight.toPx() }
                val side = min(areaW, areaH) - with(density) { 32.dp.toPx() }
                val sq = Offset((areaW - side) / 2f, (areaH - side) / 2f)
                val base = side / min(source.width, source.height)
                val image = remember(source) { source.asImageBitmap() }

                var zoom by remember(source, side) { mutableFloatStateOf(1f) }
                var off by remember(source, side) {
                    mutableStateOf(Offset((side - source.width * base) / 2f, (side - source.height * base) / 2f))
                }
                // The picture always covers the square. When it fills it exactly, rounding can put the far limit a hair
                // past zero, so the limit is capped at zero rather than trusted.
                fun clamp(o: Offset, k: Float) = Offset(
                    o.x.coerceIn(min(0f, side - source.width * k), 0f),
                    o.y.coerceIn(min(0f, side - source.height * k), 0f),
                )
                val k = base * zoom
                crop.rect = run {
                    val left = (-off.x / k).roundToInt().coerceIn(0, source.width - 1)
                    val top = (-off.y / k).roundToInt().coerceIn(0, source.height - 1)
                    val size = (side / k).roundToInt().coerceAtMost(min(source.width - left, source.height - top)).coerceAtLeast(1)
                    android.graphics.Rect(left, top, left + size, top + size)
                }

                Canvas(
                    Modifier.fillMaxSize().clipToBounds().pointerInput(source, side) {
                        detectTransformGestures { centroid, pan, gestureZoom, _ ->
                            val oldK = base * zoom
                            val newZoom = (zoom * gestureZoom).coerceIn(1f, MAX_ZOOM)
                            val newK = base * newZoom
                            // Zoom about the fingers: the point under them stays under them.
                            val c = centroid - sq
                            val moved = Offset(c.x - (c.x - off.x) * newK / oldK, c.y - (c.y - off.y) * newK / oldK) + pan
                            zoom = newZoom
                            off = clamp(moved, newK)
                        }
                    },
                ) {
                    drawImage(
                        image,
                        dstOffset = IntOffset((sq.x + off.x).roundToInt(), (sq.y + off.y).roundToInt()),
                        dstSize = IntSize((source.width * k).roundToInt(), (source.height * k).roundToInt()),
                    )
                    // Dim everything outside the circle, then outline the square and the circle.
                    val circle = Path().apply { addOval(Rect(sq, Size(side, side))) }
                    clipPath(circle, ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.6f)) }
                    drawRect(Color.White.copy(alpha = 0.5f), topLeft = sq, size = Size(side, side), style = Stroke(1.dp.toPx()))
                    drawCircle(Color.White, radius = side / 2f, center = sq + Offset(side / 2f, side / 2f), style = Stroke(2.dp.toPx()))
                }
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) {
                    Text("Drag to move · pinch to zoom", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}
