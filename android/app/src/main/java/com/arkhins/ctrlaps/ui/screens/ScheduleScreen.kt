package com.arkhins.ctrlaps.ui.screens

import com.arkhins.ctrlaps.data.Category
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.LocalApp
import com.arkhins.ctrlaps.R
import com.arkhins.ctrlaps.data.RaceSession
import com.arkhins.ctrlaps.data.Season
import com.arkhins.ctrlaps.data.SeasonResponse
import com.arkhins.ctrlaps.data.SeasonsResponse
import com.arkhins.ctrlaps.data.Weekend
import com.arkhins.ctrlaps.data.WeekendResponse
import com.arkhins.ctrlaps.data.WeekendsResponse
import com.arkhins.ctrlaps.ui.components.Chip
import com.arkhins.ctrlaps.ui.components.DateField
import com.arkhins.ctrlaps.ui.components.DateTimeField
import com.arkhins.ctrlaps.ui.components.Divider
import com.arkhins.ctrlaps.ui.components.Empty
import com.arkhins.ctrlaps.ui.components.ErrorText
import com.arkhins.ctrlaps.ui.components.Field
import com.arkhins.ctrlaps.ui.components.IconAction
import com.arkhins.ctrlaps.ui.components.Loading
import com.arkhins.ctrlaps.ui.components.Panel
import com.arkhins.ctrlaps.ui.components.SectionTitle
import com.arkhins.ctrlaps.ui.instant
import com.arkhins.ctrlaps.ui.localDateTime
import com.arkhins.ctrlaps.ui.localTime
import com.arkhins.ctrlaps.ui.theme.Danger
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.NightPanel
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import com.arkhins.ctrlaps.ui.theme.SnowSoft
import com.arkhins.ctrlaps.ui.trackDateTime
import com.arkhins.ctrlaps.ui.trackTime
import com.arkhins.ctrlaps.ui.zone
import com.arkhins.ctrlaps.ui.components.GroupTitle
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Every race weekend and its sessions. Admins create and edit both here. */
@Composable
fun ScheduleScreen(isAdmin: Boolean, onOpenWeekend: (String) -> Unit, onArchive: () -> Unit, mine: List<String>? = null, onStandings: () -> Unit = {}, editTimes: Boolean = false) {
    val app = LocalApp.current
    var seasons by remember { mutableStateOf<List<Season>>(emptyList()) }
    var weekends by remember { mutableStateOf<List<Weekend>?>(null) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    // Show one category's sessions (and those for everyone); null = all.
    // "mine" (the person's own categories, the default when they have any), a category's id, or null for all.
    var filter by rememberSaveable { mutableStateOf(if (mine.isNullOrEmpty()) null else "mine") }
    val only: List<String>? = when (filter) { null -> null; "mine" -> mine; else -> listOf(filter!!) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(reload) {
        try {
            seasons = runCatching { app.store.get("/api/seasons", SeasonsResponse.serializer()) { seasons = it.seasons }.seasons }.getOrDefault(seasons)
            val r = app.store.get("/api/weekends", WeekendsResponse.serializer()) { weekends = it.weekends; categories = it.categories }
            weekends = r.weekends
            categories = r.categories
            error = null
        } catch (e: Exception) {
            if (weekends == null) error = e.message
        }
    }

    val w = weekends
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SeasonHeader(seasons, isAdmin, onArchive = onArchive, onStandings = onStandings, onChanged = { reload++ }) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GroupTitle("Race weekends", w?.size, Modifier.weight(1f))
                if (isAdmin) IconAction(Icons.Outlined.Add, "New race weekend", Gold) { creating = true }
            }
        }
        val currentSeason = seasons.firstOrNull { it.current }?.id ?: w?.firstOrNull()?.seasonId
        val chips = categories.filter { it.seasonId == currentSeason }
        if (chips.isNotEmpty()) item { CategoryChips(chips, filter, hasMine = !mine.isNullOrEmpty()) { filter = it } }
        // A weekend that lists no categories is for everyone.
        val shown = w?.filter { wk -> only == null || wk.categoryIds.isEmpty() || wk.categoryIds.any { it in only } || wk.sessions.any { it.categoryId in only } }
        when {
            error != null && w == null -> item { ErrorText(error) }
            w == null || shown == null -> item { Loading() }
            w.isEmpty() -> item { Empty(if (isAdmin) "No race weekend yet. Create the first one." else "No race weekend has been scheduled yet.") }
            shown.isEmpty() -> item { Empty("No weekend has this category yet.") }
            else -> {
                val groups = shown.groupBy { it.seasonName ?: "" }
                groups.forEach { (seasonName, list) ->
                    if (seasonName.isNotBlank() && groups.size > 1) item(key = "season-$seasonName") { GroupTitle(seasonName) }
                    itemsIndexed(list, key = { _, it -> it.id }) { i, weekend ->
                        if (i > 0) Divider()
                        WeekendCard(weekend, isAdmin, onOpen = { onOpenWeekend(weekend.id) }, onChanged = { reload++ }, categories = categories, only = only, editTimes = editTimes)
                    }
                }
            }
        }
    }
    if (creating) {
        WeekendDialog(null, seasons.filter { it.status == "active" }, categories, onDismiss = { creating = false }, onSaved = { creating = false; reload++ })
    }
}

/** The current season's name; admins get the season list with new / edit / archive / delete. */
@Composable
private fun SeasonHeader(seasons: List<Season>, isAdmin: Boolean, onArchive: () -> Unit, onStandings: () -> Unit, onChanged: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Season?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Pair<Season, String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val current = seasons.firstOrNull { it.current }
    Column {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(current?.name ?: "No season yet", style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, lineHeight = 30.sp), fontWeight = FontWeight.Bold, color = Snow)
                    current?.let { Text(dateRange(it.startsOn, it.endsOn) + " · ${it.weekends} race weekend" + if (it.weekends == 1) "" else "s", style = MaterialTheme.typography.bodySmall, color = SnowSoft.copy(alpha = 0.8f)) }
                }
                IconAction(painterResource(R.drawable.ic_trophy), "Standings", SnowSoft, onClick = onStandings)
                IconAction(painterResource(R.drawable.ic_archive), "Archive", SnowSoft, onClick = onArchive)
                if (isAdmin) {
                    IconAction(Icons.Outlined.Settings, if (open) "Close" else "Manage seasons", if (open) Gold else SnowSoft) { open = !open }
                    IconAction(Icons.Outlined.Add, "New season", Gold) { creating = true }
                }
            }
            ErrorText(error)
            if (isAdmin) {
                if (open) {
                    Spacer(Modifier.height(6.dp))
                    seasons.filter { it.status == "active" }.forEachIndexed { i, s ->
                        if (i > 0) Divider()
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.name, style = MaterialTheme.typography.titleSmall, color = Snow, modifier = Modifier.weight(1f))
                                if (s.current) Chip("Current", Gold)
                            }
                            Text(dateRange(s.startsOn, s.endsOn) + " · ${s.weekends} weekend" + if (s.weekends == 1) "" else "s", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                if (!s.current) IconAction(Icons.Outlined.CheckCircle, "Make current", Gold) { confirm = s to "current" }
                                IconAction(Icons.Outlined.Edit, "Edit season", SnowSoft) { editing = s }
                                IconAction(painterResource(R.drawable.ic_archive), "Archive season", SnowSoft) { confirm = s to "archive" }
                                IconAction(Icons.Outlined.Delete, "Delete season", Danger) { confirm = s to "delete" }
                            }
                        }
                    }
                }
            }
        }
    }
    if (creating || editing != null) {
        SeasonDialog(editing, onDismiss = { creating = false; editing = null }, onSaved = { creating = false; editing = null; onChanged() })
    }
    confirm?.let { (s, what) ->
        // A season is a big switch, so each of these says what it means before it happens.
        val old = seasons.firstOrNull { it.current && it.id != s.id }
        val title = when (what) {
            "delete" -> "Delete ${s.name}?"
            "current" -> "Make ${s.name} the current season?"
            else -> "Archive ${s.name}?"
        }
        val risk = when (what) {
            "delete" -> "Everything in it — its race weekends, sessions, channel posts, announcements and the private chat messages sent in it — is removed from the database. There is no way to bring it back."
            "current" -> "New race weekends, announcements and messages go into ${s.name} from now on." +
                (old?.let { " ${it.name} is archived: its announcements, race weekend channels and calendar leave everyone's live pages and become read-only under Archive, until it is brought back." } ?: "")
            else -> "Its announcements, race weekend channels and calendar leave everyone's live pages and become read-only under Archive, until it is brought back."
        }
        val action = when (what) {
            "delete" -> "Delete for good"
            "current" -> "Continue"
            else -> "Archive"
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = NightPanel,
            title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (what == "delete") "This cannot be undone." else "Everyone is affected.", style = MaterialTheme.typography.labelLarge, color = if (what == "delete") Danger else Gold)
                    Text(risk, color = SnowSoft)
                    if (what != "delete") Text("Private chats are not touched.", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    scope.launch {
                        try {
                            when (what) {
                                "delete" -> app.api.delete("/api/seasons/${s.id}")
                                "current" -> app.api.patch("/api/seasons/${s.id}", SeasonResponse.serializer()) { put("current", true) }
                                else -> app.api.patch("/api/seasons/${s.id}", SeasonResponse.serializer()) { put("archived", true) }
                            }
                            onChanged()
                        } catch (e: Exception) {
                            error = e.message ?: "Could not do that."
                        }
                    }
                }) { Text(action, color = if (what == "delete") Danger else Gold) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

/** Name and dates of a season. The one with the latest first day is current. */
@Composable
private fun SeasonDialog(season: Season?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val year = java.time.LocalDate.now().year
    var name by remember { mutableStateOf(season?.name ?: "$year Season") }
    var startsOn by remember { mutableStateOf(season?.startsOn ?: "$year-01-01") }
    var endsOn by remember { mutableStateOf(season?.endsOn ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = NightPanel,
        title = { Text(if (season == null) "New season" else "Edit season", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ErrorText(error)
                Field(name, { name = it }, "Name", enabled = !busy)
                DateField(startsOn, { startsOn = it }, "First day", enabled = !busy)
                DateField(endsOn, { endsOn = it }, "Last day (optional)", enabled = !busy)
                Text("New weekends and messages go into the season with the latest first day.", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && name.isNotBlank() && startsOn.isNotBlank(), onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        val body: JsonObjectBuilder.() -> Unit = {
                            put("name", name.trim())
                            put("startsOn", startsOn.trim())
                            if (endsOn.isNotBlank()) put("endsOn", endsOn.trim())
                        }
                        if (season == null) app.api.post("/api/seasons", SeasonResponse.serializer(), body)
                        else app.api.patch("/api/seasons/${season.id}", SeasonResponse.serializer(), body)
                        onSaved()
                    } catch (e: Exception) {
                        error = e.message ?: "Could not save."
                        busy = false
                    }
                }
            }) { Text(if (busy) "Saving…" else "Save", color = Gold) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

/**
 * One weekend: the header, an arrow that drops the sessions down and folds
 * them away, and — for admins — a menu to add a session or change the weekend.
 */
@Composable
fun WeekendCard(
    w: Weekend,
    isAdmin: Boolean,
    onOpen: () -> Unit,
    onChanged: () -> Unit,
    startOpen: Boolean = false,
    /** The race categories of the weekend's season. */
    categories: List<Category> = emptyList(),
    /** Show only this category's sessions (and those for everyone). */
    only: List<String>? = null,
    /** A coordinator: may change a session's times (not add, rename or remove one). */
    editTimes: Boolean = false,
) {
    val openRoute = LocalOpen.current
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<RaceSession?>(null) }
    var adding by remember { mutableStateOf(false) }
    var editingWeekend by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf(startOpen) }
    var menu by remember { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "sessions-arrow")
    val now = System.currentTimeMillis()
    val byId = categories.associateBy { it.id }
    val running = w.categoryIds.mapNotNull { byId[it] }
    val sessions = if (only == null) w.sessions else w.sessions.filter { it.categoryId == null || it.categoryId in only }
    val past = runCatching { java.time.LocalDate.parse(w.endsOn).isBefore(java.time.LocalDate.now()) }.getOrDefault(false)
    // The track's own clock is shown only when it isn't the phone's.
    val otherZone = differsFromPhone(w.timezone, w.startsOn)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                DateBlock(w.startsOn, w.endsOn, past, Modifier.clickable(onClick = onOpen))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
                    Text(w.name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 22.sp), fontWeight = FontWeight.Bold, color = if (past) SnowSoft else Snow)
                    if (w.place.isNotBlank()) Text(w.place, style = MaterialTheme.typography.bodySmall, color = SnowSoft.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (otherZone) Text("Times in your time zone; the track is on ${w.timezone}", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
                    if (running.isNotEmpty() || (isAdmin && !w.channelOpen)) {
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (isAdmin && !w.channelOpen) Chip("Channel closed", SnowSoft)
                            running.forEach { CategoryTag(it) }
                        }
                    }
                }
                if (sessions.isNotEmpty()) {
                    IconButton(onClick = { open = !open }) {
                        Icon(
                            Icons.Outlined.KeyboardArrowDown,
                            contentDescription = if (open) "Hide sessions" else "Show sessions",
                            tint = Gold,
                            modifier = Modifier.size(28.dp).rotate(turn),
                        )
                    }
                }
                if (isAdmin) {
                    Box {
                        IconAction(Icons.Outlined.MoreVert, "More", SnowSoft) { menu = true }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = NightPanel) {
                            DropdownMenuItem(
                                text = { Text("Add session", color = Gold) },
                                leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null, tint = Gold) },
                                onClick = { menu = false; adding = true },
                            )
                            DropdownMenuItem(
                                text = { Text(if (w.channelOpen) "Close channel" else "Open channel", color = if (w.channelOpen) SnowSoft else Gold) },
                                leadingIcon = { Icon(painterResource(if (w.channelOpen) R.drawable.ic_archive else R.drawable.ic_unarchive), contentDescription = null, tint = if (w.channelOpen) SnowSoft else Gold) },
                                onClick = {
                                    menu = false
                                    scope.launch {
                                        try {
                                            app.api.patch("/api/weekends/${w.id}/channel", WeekendResponse.serializer()) { put("open", !w.channelOpen) }
                                            onChanged()
                                        } catch (e: Exception) {
                                            error = e.message ?: "Could not change the channel."
                                        }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Edit weekend", color = SnowSoft) },
                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null, tint = SnowSoft) },
                                onClick = { menu = false; editingWeekend = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete weekend", color = Danger) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = Danger) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                }
            }
            ErrorText(error)
            AnimatedVisibility(
                visible = open && sessions.isNotEmpty(),
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                Column(Modifier.padding(start = 70.dp)) {
                    Spacer(Modifier.height(6.dp))
                    sessions.forEachIndexed { i, s ->
                        if (i > 0) Divider()
                        val start = instant(s.startsAt).toEpochMilli()
                        val end = instant(s.endsAt).toEpochMilli()
                        val live = start <= now && end > now
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    s.categoryId?.let { byId[it] }?.let {
                                        CategoryTag(it)
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(s.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = if (end <= now) SnowFaint else Snow)
                                    if (live) {
                                        Spacer(Modifier.width(6.dp))
                                        Chip("LIVE", Gold, filled = true)
                                    }
                                }
                                Text("${localDateTime(s.startsAt)} – ${localTime(s.endsAt)}", style = MaterialTheme.typography.bodySmall, color = SnowSoft)
                                if (otherZone) Text("${trackDateTime(s.startsAt, w.timezone)} – ${trackTime(s.endsAt, w.timezone)} at the track", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
                            }
                            // A category's session has results once it has started.
                            if (s.categoryId != null && start <= now) {
                                Text(
                                    "Results",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Gold,
                                    modifier = Modifier.clickable { openRoute("results/${s.id}") }.padding(horizontal = 6.dp, vertical = 8.dp),
                                )
                            }
                            if (isAdmin || editTimes) IconAction(Icons.Outlined.Edit, if (isAdmin) "Edit session" else "Change the times", Gold) { editing = s }
                        }
                    }
                }
            }
        }
    }
    if (editing != null || adding) {
        SessionDialog(w, editing, categories, timesOnly = !isAdmin, onDismiss = { editing = null; adding = false }, onSaved = { editing = null; adding = false; onChanged() })
    }
    if (editingWeekend) {
        WeekendDialog(w, emptyList(), categories, onDismiss = { editingWeekend = false }, onSaved = { editingWeekend = false; onChanged() })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = NightPanel,
            title = { Text("Delete ${w.name}?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("Its sessions and channel go with it.", color = SnowSoft) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        try {
                            app.api.delete("/api/weekends/${w.id}")
                            onChanged()
                        } catch (e: Exception) {
                            error = e.message ?: "Could not delete."
                        }
                    }
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private val inputFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

private fun asInput(iso: String, tz: String): String = inputFormat.format(Instant.parse(iso).atZone(zone(tz)).toLocalDateTime())

/** Name, venue, dates and the track's time zone. */
@Composable
private fun WeekendDialog(w: Weekend?, seasons: List<Season>, categories: List<Category>, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(w?.name ?: "") }
    var venue by remember { mutableStateOf(w?.venue ?: "") }
    var city by remember { mutableStateOf(w?.city ?: "") }
    var country by remember { mutableStateOf(w?.country ?: "") }
    var timezone by remember { mutableStateOf(w?.timezone ?: ZoneId.systemDefault().id) }
    var startsOn by remember { mutableStateOf(w?.startsOn ?: "") }
    var endsOn by remember { mutableStateOf(w?.endsOn ?: "") }
    var channelOpen by remember { mutableStateOf(w?.channelOpen ?: true) }
    var seasonId by remember { mutableStateOf(w?.seasonId ?: seasons.firstOrNull { it.current }?.id ?: "") }
    var categoryIds by remember { mutableStateOf(w?.categoryIds ?: emptyList()) }
    val offered = categories.filter { it.seasonId == seasonId }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val day = Regex("\\d{4}-\\d{2}-\\d{2}")
    val valid = name.isNotBlank() && day.matches(startsOn.trim()) && day.matches(endsOn.trim())

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = NightPanel,
        title = { Text(if (w == null) "New race weekend" else "Edit race weekend", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ErrorText(error)
                Field(name, { name = it }, "Name", placeholder = "Round 4 — Sepang", enabled = !busy)
                Field(venue, { venue = it }, "Venue", enabled = !busy)
                Field(city, { city = it }, "City", enabled = !busy)
                Field(country, { country = it }, "Country", enabled = !busy)
                Field(timezone, { timezone = it }, "Track time zone", placeholder = "Asia/Kuala_Lumpur", enabled = !busy)
                DateField(startsOn, { startsOn = it; if (endsOn.isBlank() || endsOn < it) endsOn = it }, "First day", enabled = !busy)
                DateField(endsOn, { endsOn = it }, "Last day", enabled = !busy)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(enabled = !busy) { channelOpen = !channelOpen }) {
                    Checkbox(channelOpen, { channelOpen = it }, enabled = !busy, colors = CheckboxDefaults.colors(checkedColor = Gold))
                    Text("Channel open for posts", style = MaterialTheme.typography.bodyMedium, color = SnowSoft)
                }
                if (offered.isNotEmpty()) {
                    Text("CATEGORIES RACING THIS ROUND", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                    offered.forEach { c ->
                        val on = c.id in categoryIds
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) { categoryIds = if (on) categoryIds - c.id else categoryIds + c.id },
                        ) {
                            Checkbox(on, { categoryIds = if (on) categoryIds - c.id else categoryIds + c.id }, enabled = !busy, colors = CheckboxDefaults.colors(checkedColor = Gold))
                            CategoryTag(c)
                            Spacer(Modifier.width(8.dp))
                            Text(c.name, style = MaterialTheme.typography.bodyMedium, color = SnowSoft)
                        }
                    }
                }
                if (seasons.size > 1) {
                    Text("SEASON", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        seasons.forEach { s -> Chip(s.name, Gold, filled = seasonId == s.id) { seasonId = s.id } }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && valid, onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        val body: JsonObjectBuilder.() -> Unit = {
                            put("name", name.trim())
                            put("venue", venue.trim())
                            put("city", city.trim())
                            put("country", country.trim())
                            put("timezone", timezone.trim())
                            put("startsOn", startsOn.trim())
                            put("endsOn", endsOn.trim())
                            put("channelOpen", channelOpen)
                            if (seasonId.isNotBlank()) put("seasonId", seasonId)
                            put("categoryIds", buildJsonArray { categoryIds.filter { id -> offered.any { it.id == id } }.forEach { add(JsonPrimitive(it)) } })
                        }
                        if (w == null) app.api.post("/api/weekends", WeekendResponse.serializer(), body)
                        else app.api.patch("/api/weekends/${w.id}", WeekendResponse.serializer(), body)
                        onSaved()
                    } catch (e: Exception) {
                        error = e.message ?: "Could not save."
                        busy = false
                    }
                }
            }) { Text(if (busy) "Saving…" else "Save", color = Gold) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

/** Name, start and end as the track's wall clock. Saving tells everyone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionDialog(w: Weekend, session: RaceSession?, categories: List<Category>, timesOnly: Boolean = false, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(session?.name ?: "") }
    var starts by remember { mutableStateOf(session?.let { asInput(it.startsAt, w.timezone) } ?: "${w.startsOn}T09:00") }
    var ends by remember { mutableStateOf(session?.let { asInput(it.endsAt, w.timezone) } ?: "${w.startsOn}T10:00") }
    // The weekend's own categories; all of its season's when it lists none.
    val seasonCats = categories.filter { it.seasonId == w.seasonId }
    val offered = if (w.categoryIds.isNotEmpty()) seasonCats.filter { it.id in w.categoryIds || it.id == session?.categoryId } else seasonCats
    var categoryId by remember { mutableStateOf(session?.categoryId) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val valid = runCatching { LocalDateTime.parse(starts.trim(), inputFormat); LocalDateTime.parse(ends.trim(), inputFormat) }.isSuccess

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = NightPanel,
        title = { Text(if (session == null) "Add session" else if (timesOnly) "Change the times" else "Edit session", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ErrorText(error)
                Field(name, { name = it }, "Session", placeholder = "Qualifying", enabled = !busy && !timesOnly)
                DateTimeField(starts, { starts = it; if (ends <= it) ends = it }, "Starts (${w.timezone})", enabled = !busy, defaultDay = w.startsOn)
                DateTimeField(ends, { ends = it }, "Ends (${w.timezone})", enabled = !busy, defaultDay = w.startsOn)
                // A coordinator changes the times only: the category stays as it is.
                if (offered.isNotEmpty() && !timesOnly) {
                    Text("CATEGORY", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("Everyone", Gold, filled = categoryId == null) { if (!busy) categoryId = null }
                        offered.forEach { c -> Chip(c.code, categoryColor(c), filled = categoryId == c.id) { if (!busy) categoryId = c.id } }
                    }
                }
                Text("Saving a new or changed time sends an urgent notice to everyone.", style = MaterialTheme.typography.labelSmall, color = SnowFaint, textAlign = TextAlign.Start)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && name.isNotBlank() && valid, onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        app.api.post("/api/weekends/${w.id}/sessions", WeekendResponse.serializer()) {
                            if (session != null) put("id", session.id)
                            put("name", name.trim())
                            put("startsAt", starts.trim())
                            put("endsAt", ends.trim())
                            put("categoryId", categoryId ?: "")
                        }
                        onSaved()
                    } catch (e: Exception) {
                        error = e.message ?: "Could not save."
                        busy = false
                    }
                }
            }) { Text(if (busy) "Saving…" else "Save", color = Gold) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

/** A category's own colour. */
fun categoryColor(c: Category): Color = runCatching { Color(android.graphics.Color.parseColor(c.color)) }.getOrDefault(SnowSoft)

/** A race category as a small tag in its colour: "ITC". */
@Composable
fun CategoryTag(c: Category) {
    val color = categoryColor(c)
    Box(
        Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) { Text(c.code, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp), color = color, fontWeight = FontWeight.SemiBold) }
}

/** "Mine" (when the person has categories), "All" and a chip per category. */
@Composable
private fun CategoryChips(categories: List<Category>, filter: String?, hasMine: Boolean, onChange: (String?) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (hasMine) Chip("Mine", Gold, filled = filter == "mine") { onChange("mine") }
        Chip("All", Gold, filled = filter == null) { onChange(null) }
        categories.forEach { c -> Chip(c.code, categoryColor(c), filled = filter == c.id) { onChange(if (filter == c.id) null else c.id) } }
    }
}

private val monthShort: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", java.util.Locale.getDefault())
private val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault())
private val dayMonthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.getDefault())

/** "11 Dec 2026 – 13 Dec 2026" read as "11 – 13 Dec 2026"; the year only once, and only when needed. */
private fun dateRange(startsOn: String, endsOn: String?): String {
    val a = runCatching { java.time.LocalDate.parse(startsOn) }.getOrNull() ?: return listOfNotNull(startsOn, endsOn).joinToString(" – ")
    val b = endsOn?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: return dayMonthYear.format(a)
    return when {
        a == b -> dayMonthYear.format(a)
        a.year == b.year && a.month == b.month -> "${a.dayOfMonth} – ${dayMonthYear.format(b)}"
        a.year == b.year -> "${dayMonth.format(a)} – ${dayMonthYear.format(b)}"
        else -> "${dayMonthYear.format(a)} – ${dayMonthYear.format(b)}"
    }
}

/** Whether the track's clock differs from the phone's on the weekend's first day. */
private fun differsFromPhone(tz: String, startsOn: String): Boolean {
    val at = runCatching { java.time.LocalDate.parse(startsOn).atStartOfDay(ZoneId.systemDefault()).toInstant() }.getOrDefault(Instant.now())
    return zone(tz).rules.getOffset(at) != ZoneId.systemDefault().rules.getOffset(at)
}

/**
 * A weekend's days at a glance, on the left of its row: "11–13" over "DEC" (and the year when it isn't this one), on
 * the accent; faded once the weekend is over.
 */
@Composable
private fun DateBlock(startsOn: String, endsOn: String, past: Boolean, modifier: Modifier = Modifier) {
    val a = runCatching { java.time.LocalDate.parse(startsOn) }.getOrNull()
    val b = runCatching { java.time.LocalDate.parse(endsOn) }.getOrNull() ?: a
    val tone = if (past) SnowFaint else Gold
    Column(
        modifier.width(56.dp).clip(RoundedCornerShape(14.dp)).background(tone.copy(alpha = 0.14f)).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (a == null || b == null) {
            Text("TBA", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = tone)
            return@Column
        }
        Text(
            if (a == b) "${a.dayOfMonth}" else "${a.dayOfMonth}–${b.dayOfMonth}",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 20.sp),
            fontWeight = FontWeight.Bold,
            color = tone,
            maxLines = 1,
        )
        Text(
            (if (a.month == b.month) monthShort.format(a) else "${monthShort.format(a)}–${monthShort.format(b)}").uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
            color = if (past) SnowFaint else Snow,
            maxLines = 1,
        )
        if (b.year != java.time.LocalDate.now().year) Text("${b.year}", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp), color = SnowFaint)
    }
}
