package com.arkhins.ctrlaps.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.arkhins.ctrlaps.LocalApp
import com.arkhins.ctrlaps.data.Entrant
import com.arkhins.ctrlaps.data.EntrantTeam
import com.arkhins.ctrlaps.data.SessionResult
import com.arkhins.ctrlaps.data.SessionResultsResponse
import com.arkhins.ctrlaps.data.pointsText
import com.arkhins.ctrlaps.ui.components.Chip
import com.arkhins.ctrlaps.ui.components.ErrorText
import com.arkhins.ctrlaps.ui.components.Field
import com.arkhins.ctrlaps.ui.components.GhostButton
import com.arkhins.ctrlaps.ui.components.GoldButton
import com.arkhins.ctrlaps.ui.components.IconAction
import com.arkhins.ctrlaps.ui.components.Panel
import com.arkhins.ctrlaps.ui.theme.Danger
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.NightLine
import com.arkhins.ctrlaps.ui.theme.NightPanel
import com.arkhins.ctrlaps.ui.theme.OnGold
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import com.arkhins.ctrlaps.ui.theme.SnowSoft
import kotlinx.coroutines.launch
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

private val STATUSES = listOf("finished" to "Finished", "dnf" to "DNF", "dns" to "DNS", "dsq" to "DSQ")
private val STATUS_SHORT = mapOf("dnf" to "DNF", "dns" to "DNS", "dsq" to "DSQ")

/** One row being edited. [userId] is the racer picked; null with [typed] for a name typed in (a late entry). */
private data class Row(
    val key: String,
    val status: String = "finished",
    val teamId: String? = null,
    val userId: String? = null,
    val typed: Boolean = false,
    val driverName: String = "",
    val carNumber: String = "",
    val bestLap: String = "",
    val pole: Boolean = false,
    val fastestLap: Boolean = false,
    val points: String = "",
    /** Points typed by hand: the table no longer fills them in. */
    val manual: Boolean = false,
)

private var seq = 0
private fun newKey() = "r${++seq}"

private fun SessionResult.row() = Row(
    newKey(), status, teamId, userId, userId == null, driverName, carNumber, bestLap, pole, fastestLap,
    if (points == 0.0) "" else pointsText(points), manualPoints,
)

/** Finishers first, in their order (that order is the result), then DNF, DNS and DSQ. */
private fun List<Row>.sortedRows() = filter { it.status == "finished" } + filter { it.status != "finished" }
private fun List<Row>.position(key: String) = filter { it.status == "finished" }.indexOfFirst { it.key == key } + 1

/**
 * Enter a session's results on the phone. The list is the finishing order: drag a card by its handle (the six dots)
 * to move it. Each card is a team, then one of its racers (or a name typed in for a late entry), the car, DNF / DNS /
 * DSQ, pole, fastest lap, best lap and points. With a points table, points fill in from it and can be typed over.
 * Saving replaces the list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultsEditor(sessionId: String, data: SessionResultsResponse, onSaved: (SessionResultsResponse) -> Unit, onCancel: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val scoring = data.scoring
    val entrants = data.entrants
    var scores by remember { mutableStateOf(data.scores) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Points from the table for every row not typed by hand, with positions as the list now stands.
    fun scored(all: List<Row>, on: Boolean = scores): List<Row> = all.map { r ->
        if (scoring == null || r.manual) r
        else {
            val n = if (on) scoring.pointsFor(r.status, if (r.status == "finished") all.position(r.key) else null, r.pole, r.fastestLap) else 0.0
            r.copy(points = if (n == 0.0) "" else pointsText(n))
        }
    }
    var rows by remember { mutableStateOf(scored(if (data.results.isEmpty()) List(3) { Row(newKey()) } else data.results.map { it.row() })) }
    fun commit(next: List<Row>) { rows = scored(next.sortedRows()) }
    fun set(key: String, f: (Row) -> Row) {
        val changed = f(rows.first { it.key == key })
        commit(rows.map { r ->
            when {
                r.key == key -> changed
                // One pole and one fastest lap: ticking one takes it from whoever had it.
                else -> r.copy(pole = r.pole && !changed.pole, fastestLap = r.fastestLap && !changed.fastestLap)
            }
        })
    }
    fun racer(id: String?): Pair<Entrant, String>? = entrants.firstNotNullOfOrNull { t -> t.racers.firstOrNull { it.id == id }?.let { it to t.id } }
    val used = rows.mapNotNull { it.userId }.toSet()
    val missing = entrants.filter { it.entered }.flatMap { t -> t.racers.map { it to t.id } }.filter { it.first.id !in used }

    // Dragging: the card held, and how far it has moved from where it is laid out.
    val list = rememberLazyListState()
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    fun dragBy(dy: Float) {
        val key = dragging ?: return
        dragY += dy
        val items = list.layoutInfo.visibleItemsInfo
        val me = items.firstOrNull { it.key == key } ?: return
        val centre = me.offset + dragY + me.size / 2f
        val over = items.firstOrNull { it.key != key && rows.any { r -> r.key == it.key } && centre > it.offset && centre < it.offset + it.size } ?: return
        val from = rows.indexOfFirst { it.key == key }
        val to = rows.indexOfFirst { it.key == over.key }
        val next = rows.toMutableList().apply { add(to, removeAt(from)) }
        rows = scored(next)
        // The card is laid out at its new place now: keep it under the finger.
        dragY -= (over.offset - me.offset)
    }
    fun dropped() {
        dragging = null
        dragY = 0f
        commit(rows)
    }

    LazyColumn(Modifier.fillMaxSize().imePadding(), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "head") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Results · ${data.session.name}", style = MaterialTheme.typography.titleLarge, color = Snow)
                Text(
                    "The list is the finishing order: hold the six dots and drag. Pick the team, then the racer; for someone without an account, choose \"Type a name\". DNF, DNS and DSQ go to the bottom.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SnowFaint,
                )
            }
        }
        if (scoring != null) item(key = "scoring") {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Points from the table", style = MaterialTheme.typography.titleSmall, color = Snow)
                        Text(scoring.summary(), style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                    }
                    Switch(
                        checked = scores,
                        onCheckedChange = { on -> scores = on; rows = scored(rows, on) },
                        colors = SwitchDefaults.colors(checkedThumbColor = OnGold, checkedTrackColor = Gold),
                    )
                }
            }
        }
        if (missing.isNotEmpty()) item(key = "addall") {
            GhostButton("Add all entrants · ${missing.size}", Modifier.fillMaxWidth(), enabled = !busy) {
                commit(rows.filter { it.userId != null || it.typed || it.driverName.isNotBlank() } +
                    missing.map { (p, team) -> Row(newKey(), teamId = team.ifEmpty { null }, userId = p.id, driverName = p.name, carNumber = p.carNumber) })
            }
        }
        item(key = "error") { ErrorText(error) }
        items(rows, key = { it.key }) { r ->
            val held = dragging == r.key
            ResultCard(
                r = r,
                position = if (r.status == "finished") rows.position(r.key) else null,
                entrants = entrants,
                used = used,
                hasTable = scoring != null,
                modifier = Modifier
                    .zIndex(if (held) 1f else 0f)
                    .graphicsLayer { translationY = if (held) dragY else 0f; shadowElevation = if (held) 16f else 0f }
                    .then(if (held) Modifier else Modifier.animateItem()),
                handle = Modifier.pointerInput(r.key) {
                    detectDragGestures(
                        onDragStart = { dragging = r.key; dragY = 0f },
                        onDrag = { change, amount -> change.consume(); dragBy(amount.y) },
                        onDragEnd = { dropped() },
                        onDragCancel = { dropped() },
                    )
                },
                onTeam = { team ->
                    set(r.key) { x ->
                        // A racer from another team no longer fits: the driver is chosen again.
                        if (x.typed || racer(x.userId)?.second == (team ?: "")) x.copy(teamId = team) else x.copy(teamId = team, userId = null, driverName = "", carNumber = "")
                    }
                },
                onDriver = { p, team -> set(r.key) { it.copy(userId = p.id, typed = false, driverName = p.name, teamId = team.ifEmpty { null } ?: it.teamId, carNumber = it.carNumber.ifBlank { p.carNumber }) } },
                onType = { set(r.key) { it.copy(userId = null, typed = true, driverName = "") } },
                onChange = { f -> set(r.key, f) },
                onRemove = { commit(rows.filter { it.key != r.key }) },
            )
        }
        item(key = "add") {
            GhostButton("+ Add driver", Modifier.fillMaxWidth(), enabled = !busy) {
                commit(rows.filter { it.status == "finished" } + Row(newKey()) + rows.filter { it.status != "finished" })
            }
        }
        item(key = "save") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Cancel", Modifier.weight(1f), enabled = !busy, onClick = onCancel)
                GoldButton(if (busy) "Saving…" else "Save results", Modifier.weight(1f), enabled = !busy) {
                    val kept = rows.filter { it.driverName.isNotBlank() }
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            app.api.put("/api/sessions/$sessionId/results", com.arkhins.ctrlaps.data.Ok.serializer()) {
                                putJsonArray("results") {
                                    kept.forEach { d ->
                                        addJsonObject {
                                            if (d.status == "finished") put("position", kept.position(d.key))
                                            put("status", d.status)
                                            put("carNumber", d.carNumber.trim())
                                            put("driverName", d.driverName.trim())
                                            d.userId?.let { put("userId", it) }
                                            d.teamId?.takeIf { it.isNotEmpty() }?.let { put("teamId", it) }
                                            put("points", d.points.toDoubleOrNull() ?: 0.0)
                                            put("bestLap", d.bestLap.trim())
                                            put("pole", d.pole)
                                            put("fastestLap", d.fastestLap)
                                            put("manualPoints", d.manual || scoring == null)
                                        }
                                    }
                                }
                                put("scores", scores)
                            }
                            onSaved(app.api.get("/api/sessions/$sessionId/results", SessionResultsResponse.serializer()))
                        } catch (e: Exception) {
                            error = e.message ?: "Could not save."
                        } finally {
                            busy = false
                        }
                    }
                }
            }
        }
    }
}

/** One driver's card: handle and place; team and driver; car; status; pole, fastest lap, best lap and points. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultCard(
    r: Row,
    position: Int?,
    entrants: List<EntrantTeam>,
    used: Set<String>,
    hasTable: Boolean,
    modifier: Modifier,
    handle: Modifier,
    onTeam: (String?) -> Unit,
    onDriver: (Entrant, String) -> Unit,
    onType: () -> Unit,
    onChange: ((Row) -> Row) -> Unit,
    onRemove: () -> Unit,
) {
    Panel(modifier, padding = PaddingValues(start = 6.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            // The handle: hold and drag.
            Box(handle.size(width = 30.dp, height = 44.dp), contentAlignment = Alignment.Center) { DragDots() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlaceBadge(position, r.status)
                    Spacer(Modifier.width(8.dp))
                    TeamBox(entrants, r.teamId, Modifier.weight(1f), onTeam)
                    IconAction(Icons.Outlined.Delete, "Remove", Danger, onClick = onRemove)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (r.typed) {
                        Field(r.driverName, { v -> onChange { it.copy(driverName = v.take(120)) } }, "Driver's name", modifier = Modifier.weight(1f))
                    } else {
                        DriverBox(entrants, r, used, Modifier.weight(1f), onDriver, onType)
                    }
                    Field(r.carNumber, { v -> onChange { it.copy(carNumber = v.take(10)) } }, "Car #", modifier = Modifier.width(76.dp))
                }
                if (r.typed) Text("Pick from the list instead", style = MaterialTheme.typography.labelSmall, color = Gold, modifier = Modifier.clickable { onChange { it.copy(typed = false, driverName = "") } })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    STATUSES.forEach { (key, label) -> Chip(label, if (key == "finished") Gold else Danger, filled = r.status == key) { onChange { it.copy(status = key) } } }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Chip("Pole", Gold, filled = r.pole) { onChange { it.copy(pole = !it.pole) } }
                    Chip("Fastest lap", Gold, filled = r.fastestLap) { onChange { it.copy(fastestLap = !it.fastestLap) } }
                    if (hasTable && r.manual) Chip("↺ Table", SnowSoft) { onChange { it.copy(manual = false) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(r.bestLap, { v -> onChange { it.copy(bestLap = v.take(20)) } }, "Best lap", modifier = Modifier.weight(1f), placeholder = "1:42.315")
                    Field(
                        r.points,
                        { v -> onChange { it.copy(points = v.filter { ch -> ch.isDigit() || ch == '.' }.take(7), manual = true) } },
                        if (hasTable && !r.manual) "Points (table)" else "Points",
                        modifier = Modifier.weight(1f),
                        keyboard = KeyboardType.Decimal,
                    )
                }
            }
        }
    }
}

/** The drag handle: two columns of three dots. */
@Composable
private fun DragDots() {
    Canvas(Modifier.size(width = 12.dp, height = 18.dp)) {
        val r = 2.dp.toPx()
        for (col in 0..1) for (row in 0..2) {
            drawCircle(SnowFaint, radius = r, center = Offset(r + col * (size.width - 2 * r), r + row * (size.height - 2 * r) / 2))
        }
    }
}

/** The place: 1, 2, 3 in gold, the rest plain; DNF / DNS / DSQ in red. */
@Composable
private fun PlaceBadge(position: Int?, status: String) {
    val (bg, fg) = when {
        position == null -> Danger.copy(alpha = 0.15f) to Danger
        position <= 3 -> Gold to OnGold
        else -> NightLine to Snow
    }
    Box(Modifier.defaultMinSize(minWidth = 36.dp).height(36.dp).background(bg, RoundedCornerShape(10.dp)).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
        Text(position?.toString() ?: STATUS_SHORT[status] ?: "–", style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = FontWeight.Bold)
    }
}

/** A box that opens a dropdown: the team or driver pickers. */
@Composable
private fun PickBox(label: String, value: String?, modifier: Modifier, menu: @Composable (close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, SnowFaint.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = SnowFaint)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value ?: "Choose…", color = if (value == null) SnowFaint else Snow, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("⌄", color = Gold)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = NightPanel) { menu { open = false } }
    }
}

@Composable
private fun MenuHeading(text: String) = Text(text, style = MaterialTheme.typography.labelSmall, color = SnowFaint, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))

/** The team: the ones entered in this category first, then the others. */
@Composable
private fun TeamBox(entrants: List<EntrantTeam>, teamId: String?, modifier: Modifier, onPick: (String?) -> Unit) {
    PickBox("Team", entrants.firstOrNull { it.id == (teamId ?: "") && it.id.isNotEmpty() }?.name, modifier) { close ->
        val (entered, others) = entrants.filter { it.id.isNotEmpty() }.partition { it.entered }
        if (entered.isNotEmpty()) MenuHeading("ENTERED IN THIS CATEGORY")
        entered.forEach { t -> DropdownMenuItem(text = { Text(t.name, color = Snow) }, onClick = { close(); onPick(t.id) }) }
        if (others.isNotEmpty()) MenuHeading("OTHER TEAMS")
        others.forEach { t -> DropdownMenuItem(text = { Text(t.name, color = SnowSoft) }, onClick = { close(); onPick(t.id) }) }
        DropdownMenuItem(text = { Text("No team", color = SnowFaint) }, onClick = { close(); onPick(null) })
    }
}

/** The driver: the chosen team's racers (everyone's when no team is chosen), or a name typed in. */
@Composable
private fun DriverBox(entrants: List<EntrantTeam>, r: Row, used: Set<String>, modifier: Modifier, onPick: (Entrant, String) -> Unit, onType: () -> Unit) {
    PickBox("Driver", r.driverName.ifBlank { null }, modifier) { close ->
        val teams = if (r.teamId != null) entrants.filter { it.id == r.teamId } else entrants.filter { it.racers.isNotEmpty() }
        teams.forEach { t ->
            if (r.teamId == null) MenuHeading(t.name.uppercase())
            if (t.racers.isEmpty()) MenuHeading("No racers in this team for this category")
            t.racers.forEach { p ->
                val taken = p.id in used && p.id != r.userId
                DropdownMenuItem(
                    text = { Text(p.name + if (p.carNumber.isNotBlank()) "  ·  #${p.carNumber}" else "", color = if (taken) SnowFaint else Snow) },
                    enabled = !taken,
                    onClick = { close(); onPick(p, t.id) },
                )
            }
        }
        DropdownMenuItem(text = { Text("+ Type a name…", color = Gold) }, onClick = { close(); onType() })
    }
}

