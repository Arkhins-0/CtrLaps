package com.arkhins.ctrlaps.ui.screens

import com.arkhins.ctrlaps.ui.components.SaveProgress
import com.arkhins.ctrlaps.ui.components.SaveBar
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.res.painterResource
import com.arkhins.ctrlaps.R
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
import com.arkhins.ctrlaps.data.Scoring
import com.arkhins.ctrlaps.data.pointsText
import com.arkhins.ctrlaps.data.Ok
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
import com.arkhins.ctrlaps.ui.theme.OnGold
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

private data class CategoryDraft(val id: String?, val name: String, val code: String, val color: String, val scoring: Scoring? = null)

/** Admin: the current season's race categories — name, short code, colour and order — saved as one list. */
@Composable
fun CategoriesEditorScreen(onSaved: () -> Unit) {
    val app = LocalApp.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var seasonId by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf<List<CategoryDraft>?>(null) }
    // The list as saved: Revert puts it back.
    var original by remember { mutableStateOf<List<CategoryDraft>>(emptyList()) }
    var picking by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // The phone's copy first (it opens offline), then the server's; an edit in progress is left alone.
    LaunchedEffect(Unit) {
        fun show(r: TeamsResponse) {
            if (rows != null && rows != original) return
            seasonId = r.seasonId
            rows = r.categories.map { CategoryDraft(it.id, it.name, it.code, it.color, it.scoring) }
            original = rows.orEmpty()
        }
        try {
            show(app.store.get("/api/teams", TeamsResponse.serializer()) { show(it) })
            error = null
        } catch (e: Exception) {
            if (rows == null) error = e.message
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
                            // The points table is saved on its own (a saved category only).
                            c.id?.let { id ->
                                PointsTablePanel(id, c.scoring) { saved ->
                                    rows = rows?.map { if (it.id == id) it.copy(scoring = saved) else it }
                                    original = original.map { if (it.id == id) it.copy(scoring = saved) else it }
                                }
                            }
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
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Revert", Modifier.weight(1f), enabled = !busy && r != original) { rows = original; picking = null; error = null }
                    GoldButton(if (busy) "Saving…" else "Save", Modifier.weight(1f), enabled = !busy && seasonId != null && r != original) {
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
                                    .categories.let { saved -> rows = saved.map { CategoryDraft(it.id, it.name, it.code, it.color, it.scoring) }; original = rows.orEmpty() }
                                android.widget.Toast.makeText(context, "Categories saved", android.widget.Toast.LENGTH_SHORT).show()
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
}

/** A common table to start from. */
private val DEFAULT_SCORING = Scoring(points = listOf(25.0, 18.0, 15.0, 12.0, 10.0, 8.0, 6.0, 4.0, 2.0, 1.0))

/**
 * A category's points table: points for 1st, 2nd…; for a DNF, DNS or DSQ; and the pole and fastest-lap bonuses.
 * Saved at once; results not typed by hand are scored again from it.
 */
@Composable
private fun PointsTablePanel(categoryId: String, scoring: Scoring?, onSaved: (Scoring?) -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val start = scoring ?: DEFAULT_SCORING
    var points by remember(editing) { mutableStateOf(start.points.joinToString(", ") { pointsText(it) }) }
    var extras by remember(editing) {
        mutableStateOf(listOf(start.dnf, start.dns, start.dsq, start.pole, start.fastestLap).map { pointsText(it) })
    }
    fun save(s: Scoring?) {
        busy = true
        error = null
        scope.launch {
            try {
                app.api.put("/api/categories/$categoryId/scoring", Ok.serializer()) {
                    if (s == null) put("scoring", kotlinx.serialization.json.JsonNull)
                    else put("scoring", kotlinx.serialization.json.buildJsonObject {
                        putJsonArray("points") { s.points.forEach { add(it) } }
                        put("dnf", s.dnf); put("dns", s.dns); put("dsq", s.dsq); put("pole", s.pole); put("fastestLap", s.fastestLap)
                    })
                }
                onSaved(s)
                editing = false
            } catch (e: Exception) {
                error = e.message ?: "Could not save."
            } finally {
                busy = false
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Points table", style = MaterialTheme.typography.labelMedium, color = SnowSoft)
                Text(scoring?.summary() ?: "None: points are typed", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
            }
            if (!editing) Chip(if (scoring == null) "Add" else "Edit", Gold) { editing = true }
        }
        if (editing) {
            ErrorText(error)
            Field(points, { points = it }, "Points for 1st, 2nd, 3rd…", placeholder = "25, 18, 15, 12, 10")
            val labels = listOf("DNF", "DNS", "DSQ", "Pole bonus", "Fastest lap")
            labels.chunked(3).forEachIndexed { row, chunk ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chunk.forEachIndexed { k, label ->
                        val idx = row * 3 + k
                        Field(extras[idx], { v -> extras = extras.toMutableList().also { it[idx] = v.filter { ch -> ch.isDigit() || ch == '.' }.take(6) } }, label, modifier = Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                    }
                    if (chunk.size < 3) Spacer(Modifier.weight((3 - chunk.size).toFloat()))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (scoring != null) GhostButton("Remove", enabled = !busy, danger = true) { save(null) }
                Spacer(Modifier.weight(1f))
                GhostButton("Cancel", enabled = !busy) { editing = false; error = null }
                GoldButton(if (busy) "Saving…" else "Save", enabled = !busy) {
                    val list = points.split(Regex("[\\s,]+")).filter { it.isNotBlank() }.map { it.toDoubleOrNull() }
                    if (list.any { it == null }) { error = "Points must be numbers, with commas between."; return@GoldButton }
                    val e = extras.map { it.toDoubleOrNull() ?: 0.0 }
                    save(Scoring(list.filterNotNull(), e[0], e[1], e[2], e[3], e[4]))
                }
            }
        }
    }
}

/* ─────────────────────────────── Teams ──────────────────────────────── */

/**
 * Admins and coordinators: add and delete teams (at once), and rename them or tap a category to enter a team in it
 * (or withdraw it). Renames and taps wait, so many teams can be set up and saved together from the bar at the bottom.
 */
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
    // Categories tapped on a card but not saved yet, by team; and new names, by team.
    var drafts by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var names by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var progress by remember { mutableStateOf<SaveProgress?>(null) }
    var askDiscard by remember { mutableStateOf(false) }
    val waiting = drafts.keys + names.keys
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    // Leaving with changes not saved asks first (system Back and the header's arrow alike).
    BackHandler(enabled = waiting.isNotEmpty() && progress == null) { askDiscard = true }

    // The phone's copy first (it opens offline), then the server's.
    LaunchedEffect(Unit) {
        try {
            data = app.store.get("/api/teams", TeamsResponse.serializer()) { if (data == null) data = it }
            error = null
        } catch (e: Exception) {
            if (data == null) error = e.message
        }
    }
    // Every change answers with the whole list; keep the categories we already have.
    fun act(onDone: () -> Unit = {}, call: suspend () -> TeamsResponse) {
        busy = true
        error = null
        scope.launch {
            try {
                val r = call()
                data = r.copy(categories = r.categories.ifEmpty { data?.categories.orEmpty() }, seasonId = r.seasonId.ifBlank { data?.seasonId.orEmpty() })
                onDone()
            } catch (e: Exception) {
                error = e.message ?: "Could not save."
            } finally {
                busy = false
            }
        }
    }

    /** Every waiting change, one team after another; the ones that fail stay for another try. */
    fun saveAll() {
        val current = data ?: return
        val ids = waiting.toList()
        error = null
        progress = SaveProgress(0, ids.size)
        scope.launch {
            var failed = 0
            var reason: String? = null
            ids.forEachIndexed { i, id ->
                try {
                    val order = drafts[id]?.let { p -> current.categories.map { it.id }.filter { it in p } }
                    val newName = names[id]
                    val r = app.api.patch("/api/teams/$id", TeamsResponse.serializer()) {
                        if (newName != null) put("name", newName)
                        if (order != null) putJsonArray("categoryIds") { order.forEach { add(JsonPrimitive(it)) } }
                    }
                    data = r.copy(categories = r.categories.ifEmpty { data?.categories.orEmpty() }, seasonId = r.seasonId.ifBlank { data?.seasonId.orEmpty() })
                    drafts = drafts - id
                    names = names - id
                } catch (e: Exception) {
                    failed++
                    if (reason == null) reason = e.message
                }
                progress = SaveProgress(i + 1, ids.size)
            }
            progress = null
            if (failed > 0) error = "$failed could not be saved (${reason ?: "no connection"}). They're still here to try again."
        }
    }

    val d = data
    val shown = d?.teams?.filter { query.isBlank() || (names[it.id] ?: it.name).contains(query.trim(), ignoreCase = true) }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (waiting.isNotEmpty()) 96.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
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
                                        Text(names[t.id] ?: t.name, style = MaterialTheme.typography.titleMedium, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(if (t.members == 1) "1 person" else "${t.members} people", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                                    }
                                    IconAction(Icons.Outlined.Edit, "Rename", Gold) { renaming = t }
                                    IconAction(Icons.Outlined.Delete, "Delete", Danger) { deleting = t }
                                }
                                if (d.categories.isNotEmpty()) {
                                    val saved = t.categoryIds.toSet()
                                    val picked = drafts[t.id] ?: saved
                                    val changed = picked != saved
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        d.categories.forEach { c ->
                                            val on = c.id in picked
                                            Chip(c.code, categoryColor(c), filled = on) {
                                                if (busy || progress != null) return@Chip
                                                val next = if (on) picked - c.id else picked + c.id
                                                drafts = if (next == saved) drafts - t.id else drafts + (t.id to next)
                                            }
                                        }
                                    }
                                }
                                // A card with changes says so, and can put just its own back.
                                if (t.id in waiting) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text("Not saved", style = MaterialTheme.typography.labelSmall, color = Gold, modifier = Modifier.weight(1f))
                                        IconAction(painterResource(R.drawable.ic_undo), "Put back", SnowSoft, enabled = progress == null) {
                                            drafts = drafts - t.id
                                            names = names - t.id
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item { Text("Tap categories to enter a team in them this season (or withdraw it), and rename teams; then Save at the bottom saves them all.", style = MaterialTheme.typography.labelSmall, color = SnowFaint) }
            }
        }
    }
    SaveBar(
        count = waiting.size,
        progress = progress,
        onSave = ::saveAll,
        onDiscard = { drafts = emptyMap(); names = emptyMap(); error = null },
        modifier = Modifier.align(Alignment.BottomCenter),
    )
    }

    if (askDiscard) {
        AlertDialog(
            onDismissRequest = { askDiscard = false },
            containerColor = NightPanel,
            title = { Text("Discard changes?", color = Snow) },
            text = { Text("${waiting.size} ${if (waiting.size == 1) "team has" else "teams have"} changes that aren't saved.", color = SnowSoft) },
            confirmButton = {
                TextButton(onClick = {
                    askDiscard = false
                    drafts = emptyMap()
                    names = emptyMap()
                    // With nothing waiting the handler is off, so this Back leaves the screen.
                    scope.launch { dispatcher?.onBackPressed() }
                }) { Text("Discard", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { askDiscard = false }) { Text("Keep editing", color = SnowFaint) } },
        )
    }

    renaming?.let { t ->
        var name by remember(t.id) { mutableStateOf(names[t.id] ?: t.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = NightPanel,
            title = { Text("Rename team", color = Snow) },
            text = { Field(name, { name = it }, "Team name") },
            confirmButton = {
                TextButton(enabled = name.trim().length >= 2, onClick = {
                    renaming = null
                    names = if (name.trim() == t.name) names - t.id else names + (t.id to name.trim())
                }) { Text("Done", color = Gold) }
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
