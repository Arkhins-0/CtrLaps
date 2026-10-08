package com.arkhins.ctrlaps.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkhins.ctrlaps.ui.theme.Snow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.abs

private val ROW = 40.dp
private const val SHOWN = 5

/**
 * A scroll wheel, as a phone's time picker: five rows, the middle one chosen and bright, the others smaller and fading
 * towards the edges. Flick or drag to turn it; it settles on a row. A tap on a row turns to it. Setting [selected]
 * from outside turns the wheel there too.
 */
@Composable
fun WheelPicker(items: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
    val scope = rememberCoroutineScope()
    val fling = rememberSnapFlingBehavior(state, SnapPosition.Center)
    // The row nearest the middle.
    val centre by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.index ?: selected
        }
    }
    // Settled: report the row. Moved from outside: turn there.
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.distinctUntilChanged().filter { !it }.collect { if (centre != selected) onSelect(centre) }
    }
    LaunchedEffect(selected) { if (!state.isScrollInProgress && centre != selected) state.animateScrollToItem(selected) }
    // A step each time a new row reaches the middle while the wheel turns.
    val haptics = rememberHaptics()
    LaunchedEffect(state) {
        snapshotFlow { centre }.distinctUntilChanged().drop(1).collect { if (state.isScrollInProgress) haptics.step() }
    }

    Box(modifier.height(ROW * SHOWN), contentAlignment = Alignment.Center) {
        // The band the chosen row sits in.
        Box(Modifier.fillMaxWidth().height(ROW)) {
            Divider(Modifier.align(Alignment.TopCenter))
            Divider(Modifier.align(Alignment.BottomCenter))
        }
        LazyColumn(
            state = state,
            flingBehavior = fling,
            userScrollEnabled = enabled,
            contentPadding = PaddingValues(vertical = ROW * (SHOWN / 2)),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().height(ROW * SHOWN),
        ) {
            itemsIndexed(items) { i, label ->
                val distance = abs(i - centre)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(ROW)
                        .clickable(enabled = enabled) { scope.launch { state.animateScrollToItem(i) }; onSelect(i) }
                        .graphicsLayer {
                            val s = when (distance) { 0 -> 1f; 1 -> 0.86f; else -> 0.74f }
                            scaleX = s
                            scaleY = s
                        }
                        .alpha(when (distance) { 0 -> 1f; 1 -> 0.45f; else -> 0.2f }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
                        fontWeight = if (distance == 0) FontWeight.Bold else FontWeight.Normal,
                        color = Snow,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
