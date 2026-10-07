package com.arkhins.ctrlaps.ui.screens

import androidx.compose.animation.animateContentSize
import com.arkhins.ctrlaps.data.Category
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.key
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.LocalApp
import com.arkhins.ctrlaps.data.DriverRound
import com.arkhins.ctrlaps.data.DriverStanding
import com.arkhins.ctrlaps.data.SessionResultsResponse
import com.arkhins.ctrlaps.data.StandingSession
import com.arkhins.ctrlaps.data.StandingsResponse
import com.arkhins.ctrlaps.data.pointsText
import com.arkhins.ctrlaps.ui.AppViewModel
import com.arkhins.ctrlaps.ui.components.Chip
import com.arkhins.ctrlaps.ui.components.Divider
import com.arkhins.ctrlaps.ui.components.Empty
import com.arkhins.ctrlaps.ui.components.ErrorText
import com.arkhins.ctrlaps.ui.components.GoldButton
import com.arkhins.ctrlaps.ui.components.Loading
import com.arkhins.ctrlaps.ui.components.Panel
import com.arkhins.ctrlaps.ui.components.SectionTitle
import com.arkhins.ctrlaps.ui.components.GroupTitle
import com.arkhins.ctrlaps.ui.components.Avatar
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import com.arkhins.ctrlaps.ui.contrastText
import com.arkhins.ctrlaps.ui.theme.Danger
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.NightLine
import com.arkhins.ctrlaps.ui.theme.OnGold
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import com.arkhins.ctrlaps.ui.theme.SnowSoft
import com.arkhins.ctrlaps.ui.whenLabel

private val STATUS_SHORT = mapOf("dnf" to "DNF", "dns" to "DNS", "dsq" to "DSQ")

/** A place badge: 1, 2, 3 in gold, the rest plain; DNF / DNS / DSQ in red. */
@Composable
private fun Place(position: Int?, status: String = "finished", size: Int = 32) {
    val (bg, fg) = when {
        status != "finished" -> Danger.copy(alpha = 0.15f) to Danger
        position != null && position <= 3 -> Gold to OnGold
        else -> NightLine to Snow
    }
    Box(Modifier.defaultMinSize(minWidth = size.dp).height(size.dp).background(bg, RoundedCornerShape(8.dp)).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
        Text(if (status != "finished") STATUS_SHORT[status] ?: "–" else position?.toString() ?: "–", style = MaterialTheme.typography.labelMedium, color = fg, fontWeight = FontWeight.Bold)
    }
}

/**
 * A season's standings (the current one, or [startSeason]; archived seasons keep theirs), one race category per page:
 * swipe left and right to move between categories, or tap one's chip (the chips follow the swipe). Each page: drivers
 * ranked by points with wins and podiums (tap one for their points session by session, R1, R2…, as the website's
 * grid), the teams, and the sessions with results.
 */
@Composable
fun StandingsScreen(vm: AppViewModel, onOpenResults: (String) -> Unit, startSeason: String? = null) {
    val app = LocalApp.current
    var season by rememberSaveable { mutableStateOf(startSeason) }
    var head by remember { mutableStateOf<StandingsResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // The season as it first opens: its categories, and which one to start on (your own first).
    LaunchedEffect(season, vm.refreshTick) {
        val path = "/api/standings" + (season?.let { "?season=$it" } ?: "")
        try {
            head = app.store.get(path, StandingsResponse.serializer()) { if (head == null || head?.seasonId != it.seasonId) head = it }
            error = null
        } catch (e: Exception) {
            if (head == null) error = e.message
        }
    }

    val h = head
    when {
        error != null && h == null -> Box(Modifier.fillMaxSize().padding(16.dp)) { ErrorText(error) }
        h == null -> Box(Modifier.fillMaxSize().padding(16.dp)) { Loading() }
        else -> Column(Modifier.fillMaxSize()) {
            // Past seasons keep their standings.
            if (h.seasons.size > 1) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    h.seasons.forEach { s -> Chip(s.name, SnowSoft, filled = s.id == h.seasonId) { if (s.id != h.seasonId) season = s.id } }
                }
            }
            if (h.categories.isEmpty()) {
                Box(Modifier.padding(16.dp)) { Empty("This season has no race categories yet.") }
                return@Column
            }
            // A pager per season: a new season starts again on its own first category.
            key(h.seasonId) {
                val scope = rememberCoroutineScope()
                val start = h.categories.indexOfFirst { it.id == h.categoryId }.coerceAtLeast(0)
                val pager = rememberPagerState(initialPage = start) { h.categories.size }
                val chips = rememberLazyListState()
                // The chips follow the swipe: the current one is kept in view.
                LaunchedEffect(pager.currentPage) { chips.animateScrollToItem((pager.currentPage - 1).coerceAtLeast(0)) }
                LazyRow(
                    state = chips,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(h.categories, key = { _, c -> c.id }) { i, c ->
                        Chip(c.code, categoryColor(c), filled = i == pager.currentPage) { scope.launch { pager.animateScrollToPage(i) } }
                    }
                }
                // A past season is asked for by id; the current one without, as the background download keeps it.
                val pastSeason = h.seasonId?.takeIf { id -> h.seasons.firstOrNull { it.id == id }?.current == false }
                HorizontalPager(pager, Modifier.weight(1f), beyondViewportPageCount = 1, key = { h.categories[it].id }) { page ->
                    val c = h.categories[page]
                    CategoryStandings(vm, pastSeason, c, if (c.id == h.categoryId) h else null, onOpenResults)
                }
            }
        }
    }
}

/**
 * One category's page: drivers, teams and the sessions with results. [pastSeason] is set for an archived season;
 * [first] is data already loaded for it, if any.
 */
@Composable
private fun CategoryStandings(vm: AppViewModel, pastSeason: String?, category: Category, first: StandingsResponse?, onOpenResults: (String) -> Unit) {
    val app = LocalApp.current
    var data by remember(category.id) { mutableStateOf(first) }
    var error by remember(category.id) { mutableStateOf<String?>(null) }
    var open by rememberSaveable(category.id) { mutableStateOf<String?>(null) }
    // Asked the way the background download keeps it (see Prefetch), so it opens offline.
    LaunchedEffect(category.id, pastSeason, vm.refreshTick) {
        try {
            val path = "/api/standings?" + listOfNotNull(pastSeason?.let { "season=$it" }, "category=${category.id}").joinToString("&")
            data = app.store.get(path, StandingsResponse.serializer()) { if (data == null) data = it }
            error = null
        } catch (e: Exception) {
            if (data == null) error = e.message
        }
    }

    val d = data
    val tone = categoryColor(category)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        when {
            error != null && d == null -> item { ErrorText(error) }
            d == null -> item { Loading() }
            else -> {
                // Sessions oldest first, as R1, R2…
                val rounds = d.sessions
                item {
                    Column(Modifier.padding(bottom = 4.dp)) {
                        Text(category.name, style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, lineHeight = 30.sp), fontWeight = FontWeight.Bold, color = Snow)
                        Text(
                            if (rounds.isEmpty()) "No results yet" else "After ${rounds.size} ${if (rounds.size == 1) "session" else "sessions"} · ${d.drivers.size} drivers",
                            style = MaterialTheme.typography.bodySmall,
                            color = SnowSoft.copy(alpha = 0.8f),
                        )
                    }
                }
                if (d.drivers.size >= 2) item { Podium(d.drivers.take(3), tone) }
                if (d.drivers.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.Bottom) {
                            GroupTitle("Drivers", d.drivers.size, Modifier.weight(1f).padding(top = 8.dp))
                            Text("Wins · Podiums", style = MaterialTheme.typography.bodySmall, color = SnowFaint, modifier = Modifier.padding(bottom = 4.dp))
                        }
                    }
                    val leader = d.drivers.first().points
                    itemsIndexed(d.drivers, key = { _, s -> "d-" + s.key }) { i, s ->
                        DriverRow(i + 1, s, leader, rounds, tone, open == s.key) { open = if (open == s.key) null else s.key }
                    }
                } else {
                    item { Text("No results yet. They appear here once a session's results are in.", style = MaterialTheme.typography.bodyMedium, color = SnowFaint, modifier = Modifier.padding(vertical = 12.dp)) }
                }
                if (d.teams.isNotEmpty()) {
                    item { GroupTitle("Teams", d.teams.size, Modifier.padding(top = 12.dp)) }
                    val top = d.teams.first().points
                    itemsIndexed(d.teams, key = { _, t -> "t-" + t.id }) { i, t ->
                        StandingRow(i + 1, t.name, "${t.wins} ${if (t.wins == 1) "win" else "wins"} · ${t.podiums} ${if (t.podiums == 1) "podium" else "podiums"}", t.points, top, tone, initials = true)
                    }
                }
                if (rounds.isNotEmpty()) {
                    item { GroupTitle("Sessions", rounds.size, Modifier.padding(top = 12.dp)) }
                    val list = rounds.withIndex().reversed()
                    items(list.toList(), key = { "s-" + it.value.id }) { (i, s) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onOpenResults(s.id) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(tone.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                                Text("R${i + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = tone)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.name, style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, lineHeight = 20.sp), fontWeight = FontWeight.Bold, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${s.weekendName} · ${whenLabel(s.startsAt)} · ${s.rows} drivers", style = MaterialTheme.typography.bodySmall, color = SnowSoft.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Gold)
                        }
                    }
                }
            }
        }
    }
}

/** The top three as a podium: second, first (tallest, in the category's colour), third. */
@Composable
private fun Podium(top: List<DriverStanding>, tone: Color) {
    val order = listOfNotNull(top.getOrNull(1)?.let { 2 to it }, top.getOrNull(0)?.let { 1 to it }, top.getOrNull(2)?.let { 3 to it })
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        order.forEach { (place, s) ->
            val height = when (place) { 1 -> 96.dp; 2 -> 72.dp; else -> 56.dp }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(null, s.name, size = if (place == 1) 56 else 46)
                Spacer(Modifier.height(6.dp))
                Text(s.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Text(s.teamName ?: "No team", style = MaterialTheme.typography.bodySmall, color = SnowSoft.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(height)
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                        .background(if (place == 1) tone else tone.copy(alpha = if (place == 2) 0.32f else 0.2f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("$place", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (place == 1) tone.contrastText() else Snow)
                    Text("${pointsText(s.points)} pts", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = if (place == 1) tone.contrastText() else SnowSoft)
                }
            }
        }
    }
}

/** A place: 1, 2, 3 on the category's colour, the rest plain. */
@Composable
private fun PlaceNumber(place: Int, tone: Color) {
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(if (place <= 3) tone.copy(alpha = if (place == 1) 1f else 0.25f) else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Text("$place", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (place == 1) tone.contrastText() else if (place <= 3) Snow else SnowFaint)
    }
}

/** A team (or any entry) in the standings: place, name, a quieter line, the points and a bar against the leader. */
@Composable
private fun StandingRow(place: Int, name: String, line: String, points: Double, leader: Double, tone: Color, initials: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlaceNumber(place, tone)
            Spacer(Modifier.width(10.dp))
            if (initials) {
                Avatar(null, name, size = 36)
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, lineHeight = 20.sp), fontWeight = FontWeight.Bold, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(line, style = MaterialTheme.typography.bodySmall, color = SnowSoft.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(pointsText(points), style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), color = Snow, fontWeight = FontWeight.Bold)
        }
        PointsBar(points, leader, tone, Modifier.padding(start = 42.dp, top = 6.dp))
    }
}

/** A thin line, as long as the points are against the leader's. */
@Composable
private fun PointsBar(points: Double, leader: Double, tone: Color, modifier: Modifier = Modifier) {
    val share = if (leader > 0) (points / leader).toFloat().coerceIn(0f, 1f) else 0f
    Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(NightLine)) {
        Box(Modifier.fillMaxWidth(share).height(4.dp).clip(RoundedCornerShape(2.dp)).background(tone))
    }
}

/** A driver in the standings; open, their points session by session. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DriverRow(place: Int, s: DriverStanding, leader: Double, rounds: List<StandingSession>, tone: Color, open: Boolean, onToggle: () -> Unit) {
    Column(Modifier.fillMaxWidth().animateContentSize().clip(RoundedCornerShape(14.dp)).clickable(onClick = onToggle).padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlaceNumber(place, tone)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, lineHeight = 20.sp), fontWeight = FontWeight.Bold, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (s.carNumber.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Chip("#${s.carNumber}", tone)
                    }
                }
                val gap = leader - s.points
                Text(
                    listOfNotNull(s.teamName ?: "No team", if (place > 1 && gap > 0) "${pointsText(Math.round(gap * 100) / 100.0)} behind" else null).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = SnowSoft.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("${s.wins} · ${s.podiums}", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
            Spacer(Modifier.width(12.dp))
            Text(pointsText(s.points), style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), color = Snow, fontWeight = FontWeight.Bold)
        }
        PointsBar(s.points, leader, tone, Modifier.padding(start = 42.dp, top = 6.dp))
        if (open && rounds.isNotEmpty()) {
            FlowRow(Modifier.padding(start = 42.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rounds.forEachIndexed { i, session -> RoundTile("R${i + 1}", s.rounds[session.id]) }
            }
        }
    }
}

/** One session in a driver's row: R1, their points, and the finish (P1, DNF…) with pole and fastest lap. */
@Composable
private fun RoundTile(label: String, r: DriverRound?) {
    Column(
        Modifier.width(58.dp).background(NightLine.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.3.sp), color = SnowFaint)
        if (r == null) {
            Text("·", style = MaterialTheme.typography.titleSmall, color = SnowFaint)
        } else {
            Text(pointsText(r.points), style = MaterialTheme.typography.titleSmall, color = if (r.position == 1) Gold else Snow, fontWeight = FontWeight.Bold)
            Text(
                (if (r.status == "finished") "P${r.position ?: "–"}" else STATUS_SHORT[r.status] ?: "") + (if (r.pole) " P" else "") + (if (r.fastestLap) " FL" else ""),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.3.sp),
                color = if (r.status == "finished") SnowSoft else Danger,
            )
        }
    }
}

/** One session's classification; admins, coordinators and the category's race officials enter or edit it here. */
@Composable
fun ResultsScreen(vm: AppViewModel, sessionId: String, startEditing: Boolean = false) {
    val app = LocalApp.current
    var data by remember { mutableStateOf<SessionResultsResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // Opened with results/<id>/edit: straight into the editor, for those who may.
    var editing by remember { mutableStateOf(startEditing) }
    LaunchedEffect(sessionId, vm.refreshTick) {
        try {
            data = app.store.get("/api/sessions/$sessionId/results", SessionResultsResponse.serializer()) { if (data == null) data = it }
            error = null
        } catch (e: Exception) {
            if (data == null) error = e.message
        }
    }
    val d = data
    if (editing && d != null && d.canEdit) {
        ResultsEditor(sessionId, d, onSaved = { data = it; editing = false }, onCancel = { editing = false })
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            error != null && d == null -> item { ErrorText(error) }
            d == null -> item { Loading() }
            else -> {
                item {
                    Column {
                        Text(d.session.name, style = MaterialTheme.typography.titleLarge, color = Snow)
                        Text(
                            listOfNotNull(d.category?.code, d.session.weekendName, whenLabel(d.session.startsAt)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = SnowSoft,
                        )
                    }
                }
                if (d.canEdit && d.category != null) item {
                    GoldButton(if (d.results.isEmpty()) "Enter results" else "Edit results", Modifier.fillMaxWidth()) { editing = true }
                }
                if (d.results.isEmpty()) item { Empty("No results yet.") }
                else item {
                    Panel(padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                        Column {
                            d.results.forEachIndexed { i, r ->
                                if (i > 0) Divider()
                                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Place(r.position, r.status)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                (if (r.carNumber.isNotBlank()) "#${r.carNumber}  " else "") + r.driverName,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = Snow,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                            if (r.pole) { Spacer(Modifier.width(6.dp)); Chip("P", SnowSoft) }
                                            if (r.fastestLap) { Spacer(Modifier.width(4.dp)); Chip("FL", Gold) }
                                        }
                                        val sub = listOfNotNull(r.teamName, r.bestLap.takeIf { it.isNotBlank() }?.let { "Best $it" }).joinToString(" · ")
                                        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall, color = SnowFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (r.points > 0) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(pointsText(r.points), style = MaterialTheme.typography.titleMedium, color = Snow, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
