package com.arkhins.ctrlaps.ui.screens

import androidx.compose.animation.animateContentSize
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
 * A season's standings (the current one, or [startSeason]; archived seasons keep theirs), one race category at a
 * time. Drivers ranked by points with wins and podiums; tap one for their points session by session (R1, R2…, as the
 * website's grid). Then the teams, and the sessions with results.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StandingsScreen(vm: AppViewModel, onOpenResults: (String) -> Unit, startSeason: String? = null) {
    val app = LocalApp.current
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    var season by rememberSaveable { mutableStateOf(startSeason) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var data by remember { mutableStateOf<StandingsResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(chosen, season, vm.refreshTick) {
        val query = listOfNotNull(season?.let { "season=$it" }, chosen?.let { "category=$it" }).joinToString("&")
        val path = "/api/standings" + if (query.isEmpty()) "" else "?$query"
        try {
            data = app.store.get(path, StandingsResponse.serializer()) { if (data?.categoryId != it.categoryId || data?.seasonId != it.seasonId || data == null) data = it }
            error = null
        } catch (e: Exception) {
            if (data == null) error = e.message
        }
    }

    val d = data
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            error != null && d == null -> item { ErrorText(error) }
            d == null -> item { Loading() }
            else -> {
                // Past seasons keep their standings.
                if (d.seasons.size > 1) item {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        d.seasons.forEach { s -> Chip(s.name, SnowSoft, filled = s.id == d.seasonId) { if (s.id != d.seasonId) { season = s.id; chosen = null } } }
                    }
                }
                if (d.categories.isEmpty()) item { Empty("This season has no race categories yet.") }
                else item {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        d.categories.forEach { c -> Chip(c.code, categoryColor(c), filled = c.id == d.categoryId) { chosen = c.id } }
                    }
                }
                val category = d.categories.firstOrNull { it.id == d.categoryId }
                // Sessions oldest first, as R1, R2…
                val rounds = d.sessions
                if (category != null) item {
                    Panel(padding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SectionTitle("${category.name} · DRIVERS".uppercase(), Modifier.weight(1f))
                                if (d.drivers.isNotEmpty()) Text("W  ·  POD  ·  PTS", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                            }
                            if (d.drivers.isEmpty()) Text("No results yet.", style = MaterialTheme.typography.bodySmall, color = SnowFaint, modifier = Modifier.padding(top = 8.dp))
                            val leader = d.drivers.firstOrNull()?.points ?: 0.0
                            d.drivers.forEachIndexed { i, s ->
                                if (i > 0) Divider()
                                DriverRow(i + 1, s, leader, rounds, open == s.key) { open = if (open == s.key) null else s.key }
                            }
                        }
                    }
                }
                if (category != null && d.teams.isNotEmpty()) item {
                    Panel(padding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
                        Column {
                            SectionTitle("${category.name} · TEAMS".uppercase())
                            d.teams.forEachIndexed { i, t ->
                                if (i > 0) Divider()
                                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Place(i + 1)
                                    Spacer(Modifier.width(10.dp))
                                    Text(t.name, style = MaterialTheme.typography.titleSmall, color = Snow, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${t.wins}  ·  ${t.podiums}  ·  ", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                                    Text(pointsText(t.points), style = MaterialTheme.typography.titleMedium, color = Snow, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                if (category != null && rounds.isNotEmpty()) item {
                    Panel(padding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
                        Column {
                            SectionTitle("SESSIONS")
                            rounds.withIndex().reversed().forEach { (i, s) ->
                                if (i < rounds.size - 1) Divider()
                                Row(Modifier.fillMaxWidth().clickable { onOpenResults(s.id) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("R${i + 1}", style = MaterialTheme.typography.labelMedium, color = SnowFaint, fontFamily = FontFamily.Monospace, modifier = Modifier.width(36.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(s.name, style = MaterialTheme.typography.titleSmall, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${s.weekendName} · ${whenLabel(s.startsAt)}", style = MaterialTheme.typography.labelSmall, color = SnowFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text("${s.rows} drivers ›", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A driver in the standings; open, their points session by session. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DriverRow(place: Int, s: DriverStanding, leader: Double, rounds: List<StandingSession>, open: Boolean, onToggle: () -> Unit) {
    Column(Modifier.fillMaxWidth().animateContentSize().clickable(onClick = onToggle).padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Place(place)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    (if (s.carNumber.isNotBlank()) "#${s.carNumber}  " else "") + s.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Snow,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val gap = leader - s.points
                Text(
                    listOfNotNull(s.teamName ?: "No team", if (place > 1 && gap > 0) "−${pointsText(Math.round(gap * 100) / 100.0)}" else null).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = SnowFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("${s.wins}  ·  ${s.podiums}  ·  ", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
            Text(pointsText(s.points), style = MaterialTheme.typography.titleMedium, color = Snow, fontWeight = FontWeight.Bold)
        }
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
        Text(label, style = MaterialTheme.typography.labelSmall, color = SnowFaint)
        if (r == null) {
            Text("·", style = MaterialTheme.typography.titleSmall, color = SnowFaint)
        } else {
            Text(pointsText(r.points), style = MaterialTheme.typography.titleSmall, color = if (r.position == 1) Gold else Snow, fontWeight = FontWeight.Bold)
            Text(
                (if (r.status == "finished") "P${r.position ?: "–"}" else STATUS_SHORT[r.status] ?: "") + (if (r.pole) " P" else "") + (if (r.fastestLap) " FL" else ""),
                style = MaterialTheme.typography.labelSmall,
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
