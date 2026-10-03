package com.arkhins.ctrlaps.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.LocalApp
import com.arkhins.ctrlaps.data.ActivityEntry
import com.arkhins.ctrlaps.data.ActivityPage
import com.arkhins.ctrlaps.ui.components.Chip
import com.arkhins.ctrlaps.ui.components.ErrorText
import com.arkhins.ctrlaps.ui.components.GhostButton
import com.arkhins.ctrlaps.ui.components.Loading
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import com.arkhins.ctrlaps.ui.theme.SnowSoft
import com.arkhins.ctrlaps.ui.whenLabel
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.res.painterResource
import com.arkhins.ctrlaps.R
import com.arkhins.ctrlaps.ui.components.Field
import com.arkhins.ctrlaps.ui.components.IconAction
import com.arkhins.ctrlaps.ui.theme.NightPanel
import kotlinx.coroutines.delay

private val KINDS = listOf("" to "All", "messages" to "Messages", "schedule" to "Schedule", "results" to "Results", "people" to "People")

/** Who did it, for the filter: the roles, a developer, or CTR[L]APS itself. */
private val WHO = listOf(
    "" to "Anyone", "developer" to "Developer", "admin" to "Admin", "coordinator" to "Coordinator", "race_official" to "Race official",
    "team_manager" to "Team manager", "racer" to "Racer", "crew" to "Crew", "security_head" to "Security head", "security" to "Security",
    "volunteer" to "Volunteer", "user" to "User", "system" to "CTR[L]APS",
)
private val WHEN = listOf("" to "All time", "today" to "Today", "7d" to "Last 7 days", "30d" to "Last 30 days")

/** What the lists show: words to find, who did it, how far back, and the order. */
private data class ActivityFilter(val q: String = "", val role: String = "", val period: String = "", val oldest: Boolean = false)

/**
 * The Activity log (Account → Activity log, developers only): who did what and when — announcements, emails, channel
 * posts, schedule and results changes, people's roles and status. Private chats and groups are never logged. Swipe
 * left and right between All, Messages, Schedule, Results and People; the chips follow, and a tap slides. A search,
 * a filter (who did it, and when) and the order apply to every page.
 */
@Composable
fun ActivityScreen() {
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { KINDS.size }
    val chips = rememberLazyListState()
    LaunchedEffect(pager.currentPage) { chips.animateScrollToItem((pager.currentPage - 1).coerceAtLeast(0)) }
    var typed by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ActivityFilter()) }
    var filterMenu by remember { mutableStateOf(false) }
    // The search waits for a pause in typing.
    LaunchedEffect(typed) {
        delay(350)
        filter = filter.copy(q = typed.trim())
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Who did what, and when. Only the support team sees this. Private chats and groups are never logged.",
            style = MaterialTheme.typography.bodySmall,
            color = SnowFaint,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Field(typed, { typed = it }, "Search", modifier = Modifier.weight(1f), placeholder = "People, actions, weekends, details")
            Box {
                val on = filter.role.isNotEmpty() || filter.period.isNotEmpty()
                IconAction(painterResource(R.drawable.ic_filter), "Filter by who and when", if (on) Gold else SnowSoft) { filterMenu = true }
                DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }, containerColor = NightPanel) {
                    Text("WHO DID IT", style = MaterialTheme.typography.labelSmall, color = SnowFaint, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    WHO.forEach { (key, label) ->
                        DropdownMenuItem(text = { Text(label, color = if (filter.role == key) Gold else Snow) }, onClick = { filter = filter.copy(role = key); filterMenu = false })
                    }
                    HorizontalDivider(color = SnowFaint.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 4.dp))
                    Text("WHEN", style = MaterialTheme.typography.labelSmall, color = SnowFaint, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    WHEN.forEach { (key, label) ->
                        DropdownMenuItem(text = { Text(label, color = if (filter.period == key) Gold else Snow) }, onClick = { filter = filter.copy(period = key); filterMenu = false })
                    }
                }
            }
            IconAction(painterResource(R.drawable.ic_sort), if (filter.oldest) "Oldest first; show newest first" else "Newest first; show oldest first", if (filter.oldest) Gold else SnowSoft) {
                filter = filter.copy(oldest = !filter.oldest)
            }
        }
        val summary = listOfNotNull(
            WHO.firstOrNull { it.first == filter.role && it.first.isNotEmpty() }?.second,
            WHEN.firstOrNull { it.first == filter.period && it.first.isNotEmpty() }?.second,
            if (filter.oldest) "Oldest first" else null,
        )
        if (summary.isNotEmpty()) {
            Text(summary.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = Gold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        }
        LazyRow(
            state = chips,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(KINDS) { i, (_, label) ->
                Chip(label, Gold, filled = i == pager.currentPage) { scope.launch { pager.animateScrollToPage(i) } }
            }
        }
        HorizontalPager(pager, Modifier.weight(1f), beyondViewportPageCount = 1, key = { KINDS[it].first.ifEmpty { "all" } }) { page ->
            ActivityList(KINDS[page].first, filter)
        }
    }
}

/** One kind's entries, searched and filtered, in the chosen order, with more on demand. Loaded when its page is shown. */
@Composable
private fun ActivityList(kind: String, filter: ActivityFilter) {
    val app = LocalApp.current
    val openRoute = LocalOpen.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<ActivityEntry>?>(null) }
    var next by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load(before: String?) {
        busy = true
        error = null
        try {
            val query = listOfNotNull(
                kind.takeIf { it.isNotEmpty() }?.let { "kind=$it" },
                filter.q.takeIf { it.isNotEmpty() }?.let { "q=" + java.net.URLEncoder.encode(it, "UTF-8") },
                filter.role.takeIf { it.isNotEmpty() }?.let { "role=$it" },
                filter.period.takeIf { it.isNotEmpty() }?.let { "period=$it" },
                if (filter.oldest) "sort=oldest" else null,
                before?.let { "before=$it" },
            ).joinToString("&")
            val page = app.api.get("/api/activity" + if (query.isEmpty()) "" else "?$query", ActivityPage.serializer())
            entries = if (before == null) page.entries else (entries ?: emptyList()) + page.entries
            next = page.next
        } catch (e: Exception) {
            error = e.message ?: "Could not load."
        } finally {
            busy = false
        }
    }
    LaunchedEffect(kind, filter) { load(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ErrorText(error) }
        val list = entries
        when {
            list == null -> item { Loading() }
            list.isEmpty() -> item { Text(if (filter.q.isNotEmpty() || filter.role.isNotEmpty() || filter.period.isNotEmpty()) "Nothing matches." else "Nothing logged yet.", style = MaterialTheme.typography.bodyMedium, color = SnowFaint) }
            else -> items(list, key = { it.id }) { e ->
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            (e.actor?.name ?: "CTR[L]APS") + (e.actor?.roleLabel?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.titleSmall,
                            color = Snow,
                            modifier = Modifier.weight(1f),
                        )
                        Text(whenLabel(e.at), style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                    }
                    Row {
                        Text(e.title + if (e.target != null) " — " else "", style = MaterialTheme.typography.bodyMedium, color = SnowSoft)
                        e.target?.let { t ->
                            Text(
                                t.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Gold,
                                modifier = Modifier.clickable { openRoute(if (t.kind == "person") "person/${t.id}" else "weekend/${t.id}") },
                            )
                        }
                    }
                    e.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = SnowFaint) }
                    HorizontalDivider(Modifier.padding(top = 10.dp), color = SnowFaint.copy(alpha = 0.15f))
                }
            }
        }
        if (next != null) {
            item { GhostButton(if (busy) "Loading…" else if (filter.oldest) "Show newer" else "Show older", Modifier.fillMaxWidth(), enabled = !busy) { scope.launch { load(next) } } }
        }
    }
}
