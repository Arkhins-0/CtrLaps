package com.arkhins.ctrlaps.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.NightHigh
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pull the list down to fetch it again: the round arrow in the accent shows while [onRefresh] runs (at least a moment,
 * so a quick answer still reads as "done").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullRefresh(onRefresh: suspend () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            scope.launch {
                val started = System.currentTimeMillis()
                runCatching { onRefresh() }
                delay((700 - (System.currentTimeMillis() - started)).coerceAtLeast(0))
                refreshing = false
            }
        },
        state = state,
        modifier = modifier,
        indicator = {
            PullToRefreshDefaults.Indicator(state = state, isRefreshing = refreshing, modifier = Modifier.align(Alignment.TopCenter), containerColor = NightHigh, color = Gold)
        },
        content = content,
    )
}
