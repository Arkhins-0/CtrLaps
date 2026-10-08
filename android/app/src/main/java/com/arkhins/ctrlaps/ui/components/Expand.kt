package com.arkhins.ctrlaps.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How long a section takes to open or close, after Arkhime's: 200 ms. */
private const val EXPAND_MS = 200

/**
 * A section that opens and closes in place: it slides down from its top and fades in, and shrinks back up as it
 * fades out (after Arkhime's expanding sections). Pair it with [Chevron] on the row that opens it.
 */
@Composable
fun Expand(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(tween(EXPAND_MS), expandFrom = Alignment.Top) + fadeIn(tween(EXPAND_MS)),
        exit = shrinkVertically(tween(EXPAND_MS), shrinkTowards = Alignment.Top) + fadeOut(tween(EXPAND_MS * 3 / 4)),
    ) { content() }
}

/** The arrow of a row that opens: down while shut, turning to point up as the section opens. */
@Composable
fun Chevron(open: Boolean, tint: Color, size: Dp = 24.dp, description: String? = null) {
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(EXPAND_MS), label = "chevron")
    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = description, tint = tint, modifier = Modifier.size(size).rotate(turn))
}
