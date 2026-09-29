package com.arkhins.ctrlaps.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.LocalApp
import com.arkhins.ctrlaps.data.CategoriesResponse
import com.arkhins.ctrlaps.data.ResultTeam
import com.arkhins.ctrlaps.data.SessionResult
import com.arkhins.ctrlaps.data.SessionResultsResponse
import com.arkhins.ctrlaps.data.TeamRecord
import com.arkhins.ctrlaps.data.TeamsResponse
import com.arkhins.ctrlaps.ui.components.Chip
import com.arkhins.ctrlaps.ui.components.Empty
import com.arkhins.ctrlaps.ui.components.ErrorText
import com.arkhins.ctrlaps.ui.components.Field
import com.arkhins.ctrlaps.ui.components.GhostButton
import com.arkhins.ctrlaps.ui.components.GoldButton
import com.arkhins.ctrlaps.ui.components.IconAction
import com.arkhins.ctrlaps.ui.components.Loading
import com.arkhins.ctrlaps.ui.components.Panel
import com.arkhins.ctrlaps.ui.components.SectionTitle
import com.arkhins.ctrlaps.ui.theme.Danger
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.NightPanel
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import com.arkhins.ctrlaps.ui.theme.SnowSoft
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Open a screen by its route ("results/<id>") from deep inside a screen, without passing callbacks down. */
val LocalOpen = staticCompositionLocalOf<(String) -> Unit> { {} }

/** The colours a category can take: the website's list (src/lib/categoryColors.ts). */
private val CATEGORY_COLORS = listOf("#3B82F6", "#F97316", "#EC4899", "#14B8A6", "#A855F7", "#84CC16", "#D97706", "#06B6D4", "#EF4444", "#64748B")

private fun hex(c: String): Color = runCatching { Color(android.graphics.Color.parseColor(c)) }.getOrDefault(SnowSoft)

/* ───────────────────────────── Categories ───────────────────────────── */

private data class CategoryDraft(val id: String?, val name: String, val code: String, val color: String)

/** Admin: the current season's race categories — name, short code, colour and order — saved as one list. */
@Composable
fun CategoriesEditorScreen(onSaved: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var seasonId by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf<List<CategoryDraft>?>(null) }
    var picking by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val r = app.api.get("/api/teams", TeamsResponse.serializer())
            seasonId = r.seasonId
            rows = r.categories.map { CategoryDraft(it.id, it.name, it.code, it.color) }
        } catch (e: Exception) {
            error = e.message
        }
    }

    fun set(i: Int, f: (CategoryDraft) -> CategoryDraft) { rows = rows?.mapIndexed { j, r -> if (j == i) f(r) else r } }
    fun move(i: Int, by: Int) {
        val list = rows?.toMutableList() ?: return
        val j = i + by
        if (j !in list.indices) return
        list[i] = list[j].also { list[j] = list[i] }
        rows = list
    }

    val r = rows
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Each category has a name, a short code shown on tags (like ITC) and a colour. The order here is the order everywhere.", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
        }
        item { ErrorText(error) }
        when {
            r == null && error == null -> item { Loading() }
            r != null -> {
                itemsIndexed(r) { i, c ->
                    Panel {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(30.dp).background(hex(c.color), CircleShape).border(2.dp, Snow.copy(alpha = 0.3f), CircleShape).clickable { picking = if (picking == i) null else i },
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(c.code.ifBlank { "New" }, style = MaterialTheme.typography.titleMedium, color = hex(c.color), modifier = Modifier.weight(1f))
                                IconAction(Icons.Filled.KeyboardArrowUp, "Move up", SnowSoft, enabled = i > 0) { move(i, -1) }
                                IconAction(Icons.Filled.KeyboardArrowDown, "Move down", SnowSoft, enabled = i < r.size - 1) { move(i, 1) }
                                IconAction(Icons.Outlined.Delete, "Remove", Danger) { rows = r.filterIndexed { j, _ -> j != i } }
                            }
                            if (picking == i) {
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CATEGORY_COLORS.forEach { col ->
                                        Box(
                                            Modifier
                                                .size(32.dp)
                                                .background(hex(col), CircleShape)
                                                .border(if (col.equals(c.color, true)) 3.dp else 0.dp, Snow, CircleShape)
                                                .clickable { set(i) { it.copy(color = col) }; picking = null },
                                        )
                                    }
                                }
                            }
                            Field(c.name, { v -> set(i) { it.copy(name = v) } }, "Name", placeholder = "Indian Touring Cars")
                            Field(c.code, { v -> set(i) { it.copy(code = v.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(8)) } }, "Code", placeholder = "ITC")
                        }
                    }
                }
                item {
                    GhostButton("Add category", Modifier.fillMaxWidth(), enabled = !busy) {
                        val used = r.map { it.color }.toSet()
                        rows = r + CategoryDraft(null, "", "", CATEGORY_COLORS.firstOrNull { it !in used } ?: CATEGORY_COLORS[0])
                    }
                }
                item {
                    GoldButton(if (busy) "Saving…" else "Save", Modifier.fillMaxWidth(), enabled = !busy && seasonId != null) {
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                app.api.put("/api/seasons/$seasonId/categories", CategoriesResponse.serializer()) {
                                    putJsonArray("categories") {
                                        r.forEach { c ->
                                            addJsonObject {
                                                c.id?.let { put("id", it) }
                                                put("name", c.name.trim())
                                                put("code", c.code)
                                                put("color", c.color)
                                            }
                                        }
                                    }
                                }
                                onSaved()
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
}

/* ─────────────────────────────── Teams ──────────────────────────────── */

/** Admins and coordinators: add, rename and delete teams, and tap a category to enter a team in it (or withdraw it). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeamsScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<TeamsResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<TeamRecord?>(null) }
    var deleting by remember { mutableStateOf<TeamRecord?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            data = app.api.get("/api/teams", TeamsResponse.serializer())
        } catch (e: Exception) {
            error = e.message
        }
    }
    // Every change answers with the whole list; keep the categories we already have.
    fun act(call: suspend () -> TeamsResponse) {
        busy = true
        error = null
        scope.launch {
            try {
                val r = call()
                data = r.copy(categories = r.categories.ifEmpty { data?.categories.orEmpty() }, seasonId = r.seasonId.ifBlank { data?.seasonId.orEmpty() })
            } catch (e: Exception) {
                error = e.message ?: "Could not save."
            } finally {
                busy = false
            }
        }
    }

    val d = data
    val shown = d?.teams?.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("NEW TEAM")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Field(newName, { newName = it }, "Team name", modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        GoldButton("Add", enabled = !busy && newName.trim().length >= 2) {
                            val name = newName.trim()
                            newName = ""
                            act { app.api.post("/api/teams", TeamsResponse.serializer()) { put("name", name); putJsonArray("categoryIds") {} } }
                        }
                    }
                }
            }
        }
        item { ErrorText(error) }
        when {
            d == null && error == null -> item { Loading() }
            d != null -> {
                item { Field(query, { query = it }, "Search teams") }
                if (shown.isNullOrEmpty()) item { Empty(if (d.teams.isEmpty()) "No teams yet." else "No team matches.") }
                shown?.let { list ->
                    itemsIndexed(list, key = { _, t -> t.id }) { _, t ->
                        Panel {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(t.name, style = MaterialTheme.typography.titleMedium, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(if (t.members == 1) "1 person" else "${t.members} people", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                                    }
                                    IconAction(Icons.Outlined.Edit, "Rename", Gold) { renaming = t }
                                    IconAction(Icons.Outlined.Delete, "Delete", Danger) { deleting = t }
                                }
                                if (d.categories.isNotEmpty()) {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        d.categories.forEach { c ->
                                            val on = c.id in t.categoryIds
                                            Chip(c.code, categoryColor(c), filled = on) {
                                                if (busy) return@Chip
                                                val next = if (on) t.categoryIds - c.id else t.categoryIds + c.id
                                                // At once on screen; the server's answer replaces it.
                                                data = d.copy(teams = d.teams.map { if (it.id == t.id) it.copy(categoryIds = next) else it })
                                                act { app.api.patch("/api/teams/${t.id}", TeamsResponse.serializer()) { putJsonArray("categoryIds") { next.forEach { add(JsonPrimitive(it)) } } } }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item { Text("Tap a category to enter the team in it this season; tap again to withdraw it.", style = MaterialTheme.typography.labelSmall, color = SnowFaint) }
            }
        }
    }

    renaming?.let { t ->
        var name by remember(t.id) { mutableStateOf(t.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = NightPanel,
            title = { Text("Rename team", color = Snow) },
            text = { Field(name, { name = it }, "Team name") },
            confirmButton = {
                TextButton(enabled = name.trim().length >= 2, onClick = {
                    renaming = null
                    act { app.api.patch("/api/teams/${t.id}", TeamsResponse.serializer()) { put("name", name.trim()) } }
                }) { Text("Save", color = Gold) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel", color = SnowFaint) } },
        )
    }
    deleting?.let { t ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = NightPanel,
            title = { Text("Delete ${t.name}?", color = Snow) },
            text = { Text("Its entries go too. Its people keep their roles, with no team.", color = SnowSoft) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    act {
                        app.api.delete("/api/teams/${t.id}")
                        app.api.get("/api/teams", TeamsResponse.serializer())
                    }
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel", color = SnowFaint) } },
        )
    }
}

/* ────────────────────────────── Results ─────────────────────────────── */

private data class ResultDraft(
    val position: String,
    val status: String,
    val carNumber: String,
    val driverName: String,
    val teamId: String?,
    val points: String,
    val bestLap: String,
)

private val STATUSES = listOf("finished" to "Finished", "dnf" to "DNF", "dns" to "DNS", "dsq" to "DSQ")

private fun SessionResult.draft() = ResultDraft(
    position?.toString().orEmpty(), status, carNumber, driverName, teamId,
    if (points == 0.0) "" else if (points % 1.0 == 0.0) points.toLong().toString() else points.toString(), bestLap,
)

private fun blankRow(position: Int) = ResultDraft(position.toString(), "finished", "", "", null, "", "")

/**
 * Enter a session's results on the phone: one card per driver (position, DNF / DNS / DSQ, car, driver, team, points,
 * best lap). Saving replaces the list; finishers are kept in position order.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultsEditor(sessionId: String, data: SessionResultsResponse, onSaved: (SessionResultsResponse) -> Unit, onCancel: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf(if (data.results.isEmpty()) (1..3).map(::blankRow) else data.results.map { it.draft() }) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val teams = data.teams
    fun set(i: Int, f: (ResultDraft) -> ResultDraft) { rows = rows.mapIndexed { j, r -> if (j == i) f(r) else r } }

    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column {
                Text("Results · ${data.session.name}", style = MaterialTheme.typography.titleLarge, color = Snow)
                Text("A driver whose name matches a racer's account is linked to them. Points are as your series scores them.", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
            }
        }
        item { ErrorText(error) }
        itemsIndexed(rows) { i, r ->
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            STATUSES.forEach { (key, label) -> Chip(label, if (key == "finished") Gold else Danger, filled = r.status == key) { set(i) { it.copy(status = key) } } }
                        }
                        IconAction(Icons.Outlined.Delete, "Remove row", Danger) { rows = rows.filterIndexed { j, _ -> j != i } }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (r.status == "finished") Field(r.position, { v -> set(i) { it.copy(position = v.filter(Char::isDigit).take(3)) } }, "Pos", modifier = Modifier.weight(1f), keyboard = KeyboardType.Number)
                        Field(r.carNumber, { v -> set(i) { it.copy(carNumber = v.take(10)) } }, "Car #", modifier = Modifier.weight(1f))
                        Field(r.points, { v -> set(i) { it.copy(points = v.filter { ch -> ch.isDigit() || ch == '.' }.take(7)) } }, "Points", modifier = Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                    }
                    Field(r.driverName, { v -> set(i) { it.copy(driverName = v.take(120)) } }, "Driver")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TeamPicker(teams, r.teamId, Modifier.weight(1.4f)) { id -> set(i) { it.copy(teamId = id) } }
                        Field(r.bestLap, { v -> set(i) { it.copy(bestLap = v.take(20)) } }, "Best lap", modifier = Modifier.weight(1f), placeholder = "1:42.315")
                    }
                }
            }
        }
        item {
            GhostButton("Add driver", Modifier.fillMaxWidth(), enabled = !busy) { rows = rows + blankRow(rows.count { it.status == "finished" } + 1) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Cancel", Modifier.weight(1f), enabled = !busy, onClick = onCancel)
                GoldButton(if (busy) "Saving…" else "Save results", Modifier.weight(1f), enabled = !busy) {
                    val kept = rows.filter { it.driverName.isNotBlank() }
                    val ordered = kept.filter { it.status == "finished" }.sortedBy { it.position.toIntOrNull() ?: 999 } + kept.filter { it.status != "finished" }
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            app.api.put("/api/sessions/$sessionId/results", ResultsOnly.serializer()) {
                                putJsonArray("results") {
                                    ordered.forEach { d ->
                                        addJsonObject {
                                            d.position.toIntOrNull()?.takeIf { d.status == "finished" }?.let { put("position", it) }
                                            put("status", d.status)
                                            put("carNumber", d.carNumber)
                                            put("driverName", d.driverName.trim())
                                            d.teamId?.let { put("teamId", it) }
                                            put("points", d.points.toDoubleOrNull() ?: 0.0)
                                            put("bestLap", d.bestLap)
                                        }
                                    }
                                }
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

/** What a results save answers with. */
@kotlinx.serialization.Serializable
private data class ResultsOnly(val results: List<SessionResult> = emptyList())

/** The team of a result row: the teams entered in the category first, then the rest. */
@Composable
private fun TeamPicker(teams: List<ResultTeam>, teamId: String?, modifier: Modifier, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val name = teams.firstOrNull { it.id == teamId }?.name
    Box(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .border(1.dp, SnowFaint.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .clickable { open = true }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(name ?: "No team", color = if (name == null) SnowFaint else Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = NightPanel) {
            DropdownMenuItem(text = { Text("No team", color = SnowFaint) }, onClick = { open = false; onPick(null) })
            val (entered, others) = teams.partition { it.entered }
            if (entered.isNotEmpty()) Text("ENTERED IN THIS CATEGORY", style = MaterialTheme.typography.labelSmall, color = SnowFaint, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            entered.forEach { t -> DropdownMenuItem(text = { Text(t.name, color = Snow) }, onClick = { open = false; onPick(t.id) }) }
            if (others.isNotEmpty()) Text("OTHER TEAMS", style = MaterialTheme.typography.labelSmall, color = SnowFaint, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            others.forEach { t -> DropdownMenuItem(text = { Text(t.name, color = Snow) }, onClick = { open = false; onPick(t.id) }) }
        }
    }
}
