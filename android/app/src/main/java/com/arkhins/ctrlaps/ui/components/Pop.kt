package com.arkhins.ctrlaps.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** A toggle's icon as it pops: the state it shows (which lags behind while it shrinks) and its size. */
@Stable
class Pop<T>(initial: T) {
    var shown by mutableStateOf(initial)
        internal set
    internal val scale = Animatable(1f)
}

/**
 * After Arkhime's pop buttons, for a star or a bell: when [value] changes the icon shrinks to nothing, swaps to the
 * new state, overshoots to 1.4× and settles. Draw the icon from [Pop.shown] and add [popped] to it; pair the tint
 * with animateColorAsState so the colour fades across too. Tapped again mid-way, it settles where it should.
 */
@Composable
fun <T> rememberPop(value: T): Pop<T> {
    val pop = remember { Pop(value) }
    LaunchedEffect(value) {
        if (value != pop.shown) {
            pop.scale.animateTo(0f, tween(90, easing = FastOutLinearInEasing))
            pop.shown = value
            pop.scale.animateTo(1.4f, tween(120, easing = LinearOutSlowInEasing))
        }
        pop.scale.animateTo(1f, tween(140, easing = FastOutSlowInEasing))
    }
    return pop
}

fun Modifier.popped(pop: Pop<*>): Modifier = graphicsLayer {
    val s = pop.scale.value
    scaleX = s
    scaleY = s
}
