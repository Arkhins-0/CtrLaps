package com.arkhins.ctrlaps.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.ui.theme.AppMotion

/** How long one row takes to arrive, and how far behind the one above it it starts. */
private const val ARRIVE_MS = 320
private const val STAGGER_MS = 30
/** Only the first rows on screen take part; later ones would only make the list wait. */
private const val MAX_ROWS = 10
/** Rows that turn up this long after the list first showed (scrolled to, or new) simply appear. */
private const val OPEN_MS = 1500L
/** Rows drawn this close together are one batch, counted from the top again (the saved copy, then the server's). */
private const val BATCH_GAP_MS = 80L

/** Arkhime's overshoot (tension 1.4): past the end a little, then back. */
private val Overshoot = Easing { t ->
    val x = t - 1f
    x * x * ((1.4f + 1f) * x + 1.4f) + 1f
}

/** One list's arrival: which of its rows are drawn as the list first shows, in order from the top. */
@Stable
class Arrival internal constructor(private val enabled: Boolean) {
    private var first = 0L
    private var last = 0L
    private var slot = 0

    /** This row's place in its batch, or -1 when it should just appear. */
    internal fun claim(): Int {
        if (!enabled) return -1
        val now = SystemClock.uptimeMillis()
        if (first == 0L) first = now
        if (now - first > OPEN_MS) return -1
        if (now - last > BATCH_GAP_MS) slot = 0
        last = now
        return if (slot < MAX_ROWS) slot++ else -1
    }
}

/** A new arrival each time the screen is shown; none with animations off (Settings → Interface, or the phone's). */
@Composable
fun rememberArrival(): Arrival = remember { Arrival(AppMotion.on) }

/**
 * After Arkhime's list animation: the row rises a little and fades in, a moment after the one above it, overshooting
 * slightly before it settles. Only rows drawn as the list first shows; rows scrolled to later are simply there.
 */
fun Modifier.arrive(arrival: Arrival): Modifier = composed {
    val slot = remember { arrival.claim() }
    if (slot < 0) return@composed Modifier
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(ARRIVE_MS, delayMillis = slot * STAGGER_MS, easing = Overshoot)) }
    val rise = with(LocalDensity.current) { 24.dp.toPx() }
    Modifier.graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * rise
    }
}
