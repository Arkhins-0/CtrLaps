package com.arkhins.ctrlaps.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.NightLine
import com.arkhins.ctrlaps.ui.theme.NightPanel
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Quick picks under the wheel: the presets and a few more. */
private val SWATCHES = listOf(
    0xFFFFD100, 0xFFFF8A3D, 0xFFFF5C8A, 0xFFE040FB, 0xFFB7A4FF, 0xFF60A5FA, 0xFF22D3EE, 0xFF4ADE80, 0xFFA3E635, 0xFFF4F4F5,
).map { Color(it) }

/**
 * A colour wheel: the ring picks the hue; the triangle inside it (pure colour, white, black) picks how strong and how
 * light; a hex box takes one typed in; the bar shows the result. OK hands it back.
 */
@Composable
fun ColorPickerDialog(initial: Color, onDismiss: () -> Unit, onPick: (Color) -> Unit) {
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    val color = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
    var hex by remember { mutableStateOf(hexOf(color)) }
    fun set(c: Color) {
        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(c.toArgb(), it) }
        hue = hsv[0]; sat = hsv[1]; value = hsv[2]
        hex = hexOf(c)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NightPanel,
        title = { Text("Pick a colour", color = Snow) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Wheel(hue, sat, value) { h, s, v ->
                    hue = h; sat = s; value = v
                    hex = hexOf(Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SWATCHES.forEach { c ->
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(c, CircleShape)
                                .border(1.dp, NightLine, CircleShape)
                                .clickable { set(c) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { typed ->
                            val clean = typed.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                            hex = clean
                            if (clean.length == 6) set(Color(0xFF000000 or clean.toLong(16)))
                        },
                        prefix = { Text("#", color = SnowFaint) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, color = Snow),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, unfocusedBorderColor = NightLine, cursorColor = Gold),
                    )
                    Box(Modifier.width(88.dp).height(52.dp).background(color, RoundedCornerShape(12.dp)).border(1.dp, NightLine, RoundedCornerShape(12.dp)))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color) }) { Text("OK", color = Gold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = SnowFaint) } },
    )
}

private fun hexOf(c: Color): String = String.format("%06X", c.toArgb() and 0xFFFFFF)

/**
 * The ring and the triangle. The triangle turns with the hue: its corners are the pure colour, white and black, and a
 * point inside is a mix of the three (value = colour + white share, saturation = colour's part of that).
 */
@Composable
private fun Wheel(hue: Float, sat: Float, value: Float, onChange: (Float, Float, Float) -> Unit) {
    val ringColors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
    // The gesture outlives a recomposition: it reads the latest colour through this.
    val now by rememberUpdatedState(Triple(hue, sat, value))
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val outer = min(size.width, size.height) / 2f
                    val ring = outer * 0.16f
                    // Where the touch started decides what it moves: the ring the hue, inside it the triangle.
                    val onRing = (down.position - c).getDistance() > outer - ring - 6.dp.toPx()
                    var h = now.first
                    fun handle(p: Offset) {
                        if (onRing) {
                            h = ((Math.toDegrees(atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())) + 360) % 360).toFloat()
                            onChange(h, now.second, now.third)
                        } else {
                            val (s, v) = triangleSv(p, c, outer - ring - 10.dp.toPx(), h)
                            onChange(h, s, v)
                        }
                    }
                    handle(down.position)
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { ch -> if (ch.pressed) { handle(ch.position); ch.consume() } }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val outer = min(size.width, size.height) / 2f
        val ring = outer * 0.16f
        drawCircle(Brush.sweepGradient(ringColors, c), radius = outer - ring / 2f, center = c, style = Stroke(ring))
        val r = outer - ring - 10.dp.toPx()
        val (pH, pW, pK) = corners(c, r, hue)
        val pure = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
        val tri = Path().apply { moveTo(pH.x, pH.y); lineTo(pW.x, pW.y); lineTo(pK.x, pK.y); close() }
        drawPath(tri, pure)
        drawPath(tri, Brush.linearGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), pW, (pH + pK) / 2f))
        drawPath(tri, Brush.linearGradient(listOf(Color.Black, Color.Black.copy(alpha = 0f)), pK, (pH + pW) / 2f))
        // The markers: on the ring at the hue, in the triangle at the colour.
        val a = Math.toRadians(hue.toDouble())
        val onRing = Offset(c.x + (outer - ring / 2f) * cos(a).toFloat(), c.y + (outer - ring / 2f) * sin(a).toFloat())
        drawCircle(Color.White, radius = ring * 0.42f, center = onRing, style = Stroke(3.dp.toPx()))
        val wh = sat * value
        val wb = value - wh
        val wk = 1f - value
        val inTri = Offset(pH.x * wh + pW.x * wb + pK.x * wk, pH.y * wh + pW.y * wb + pK.y * wk)
        drawCircle(if (value > 0.6f && sat < 0.5f) Color.Black else Color.White, radius = 7.dp.toPx(), center = inTri, style = Stroke(2.5f.dp.toPx()))
    }
}

/** The triangle's corners for [hue]: the pure colour at the hue's angle, white and black a third of a turn on. */
private fun corners(c: Offset, r: Float, hue: Float): Triple<Offset, Offset, Offset> {
    fun at(deg: Float): Offset {
        val a = deg * PI / 180.0
        return Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
    }
    return Triple(at(hue), at(hue + 120f), at(hue + 240f))
}

/** Saturation and value for a point, held inside the triangle. */
private fun triangleSv(p: Offset, c: Offset, r: Float, hue: Float): Pair<Float, Float> {
    val (a, b, k) = corners(c, r, hue)
    val det = (b.y - k.y) * (a.x - k.x) + (k.x - b.x) * (a.y - k.y)
    var wa = ((b.y - k.y) * (p.x - k.x) + (k.x - b.x) * (p.y - k.y)) / det
    var wb = ((k.y - a.y) * (p.x - k.x) + (a.x - k.x) * (p.y - k.y)) / det
    wa = wa.coerceIn(0f, 1f)
    wb = wb.coerceIn(0f, 1f - wa)
    val v = (wa + wb).coerceIn(0f, 1f)
    val s = if (v > 0.001f) (wa / v).coerceIn(0f, 1f) else 0f
    return s to v
}

/** A row's colour dot, for the picker row. */
@Composable
fun ColorDot(color: Color) {
    Box(Modifier.size(28.dp).background(color, CircleShape).border(2.dp, Snow.copy(alpha = 0.6f), CircleShape))
}
