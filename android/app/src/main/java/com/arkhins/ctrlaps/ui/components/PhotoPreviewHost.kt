package com.arkhins.ctrlaps.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.arkhins.ctrlaps.ui.theme.Danger
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.NightHigh
import com.arkhins.ctrlaps.ui.theme.Snow
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A photo pulled up (Arkhime's "photo grows into the viewer"): it grows from where it was tapped (round from a circle,
 * rounded from a banner) to the middle of the screen, large (square, or 16:9 for a weekend), as the page behind
 * darkens; its name above, and under it Close, plus Change (the gallery) and Remove (with Undo on the message bar) for
 * those who may change it. A tap outside, Close or Back shrinks it back to where it came from.
 */
@Composable
fun PhotoPreviewHost() {
    val asked = PhotoPreview.shown.value
    // What is on screen: kept while it shrinks back after closing.
    var view by remember { mutableStateOf<PhotoView?>(null) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(asked) {
        if (asked != null) {
            view = asked
            progress.snapTo(0f)
            progress.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 420f))
        } else if (view != null) {
            progress.animateTo(0f, tween(200))
            view = null
        }
    }
    val v = view ?: return
    val close = { PhotoPreview.shown.value = null }
    BackHandler(enabled = asked != null) { close() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val px = { dp: Float -> with(density) { dp.dp.toPx() } }
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        // Where it ends: as wide as the screen allows (not beyond 480dp), a little above the middle.
        val tw = min(w - px(32f), px(480f))
        val th = if (v.wide) tw * 9f / 16f else tw
        val target = Rect(Offset((w - tw) / 2f, (h - th) / 2f - px(40f)), Size(tw, th))
        // Where it starts: the tapped photo; with none known, a smaller copy in the middle.
        val start = v.from ?: Rect(target.center - Offset(tw * 0.3f, th * 0.3f), Size(tw * 0.6f, th * 0.6f))
        val p = progress.value.coerceIn(0f, 1.05f)
        val at = lerp(start, target, p.coerceAtMost(1f))
        val startCorner = if (v.round) start.width / 2f else px(16f)
        val corner = startCorner + (px(24f) - startCorner) * p.coerceAtMost(1f)
        val shown = p.coerceIn(0f, 1f)

        // The page behind, darkened; a tap on it closes.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f * shown))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() },
        )
        // Its name, over the photo.
        Text(
            v.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .offset { IntOffset(0, (target.top - px(48f)).roundToInt()) }
                .alpha(shown),
        )
        AsyncImage(
            model = v.url,
            contentDescription = "${v.name}'s photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .offset { IntOffset(at.left.roundToInt(), at.top.roundToInt()) }
                .size(with(density) { at.width.toDp() }, with(density) { at.height.toDp() })
                .clip(RoundedCornerShape(with(density) { corner.toDp() })),
        )
        // Close, and for those who may: Change and Remove.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .offset { IntOffset(0, (target.bottom + px(18f)).roundToInt()) }
                .alpha(shown),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            val tile = Modifier.width(110.dp)
            v.onChange?.let { change ->
                PhotoAction(Icons.Outlined.Edit, "Change", Gold, tile) {
                    close()
                    change()
                }
            }
            v.onRemove?.let { remove ->
                PhotoAction(Icons.Outlined.Delete, "Remove", Danger, tile) {
                    close()
                    Snack.undo("Photo removed") { remove() }
                }
            }
            PhotoAction(Icons.Outlined.Close, "Close", Snow, tile) { close() }
        }
    }
}

/** An icon over its word, on a soft tile: Change, Remove, Close. */
@Composable
private fun PhotoAction(icon: ImageVector, label: String, tone: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(NightHigh).clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (tone == Danger) Danger else Snow)
    }
}
