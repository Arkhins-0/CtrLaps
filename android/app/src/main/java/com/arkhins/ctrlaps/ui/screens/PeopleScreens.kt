package com.arkhins.ctrlaps.ui.screens

import com.arkhins.ctrlaps.ui.components.TeamPicker
import com.arkhins.ctrlaps.ui.components.PullRefresh
import com.arkhins.ctrlaps.ui.components.LoadingShape
import com.arkhins.ctrlaps.ui.theme.NightHigh
import com.arkhins.ctrlaps.ui.components.CopyButton
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.AccountBox
import com.arkhins.ctrlaps.data.RosterCategory
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.ui.text.font.FontWeight
import com.arkhins.ctrlaps.data.CategoryIdsResponse
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import androidx.compose.material3.HorizontalDivider
import com.arkhins.ctrlaps.data.Verified
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.IconButton
import com.arkhins.ctrlaps.ui.theme.OnGold
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.painterResource
import com.arkhins.ctrlaps.R
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import com.arkhins.ctrlaps.ui.theme.Night
import com.arkhins.ctrlaps.ui.theme.NightLine
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.arkhins.ctrlaps.ui.components.SquareCropDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.LocalApp
import com.arkhins.ctrlaps.data.IdResponse
import com.arkhins.ctrlaps.data.Me
import com.arkhins.ctrlaps.data.Ok
import com.arkhins.ctrlaps.data.PublicUser
import com.arkhins.ctrlaps.data.SentResponse
import com.arkhins.ctrlaps.data.UserResponse
import com.arkhins.ctrlaps.data.UsersResponse
import com.arkhins.ctrlaps.data.VolunteerGroupsResponse
import com.arkhins.ctrlaps.data.VolunteerGroupRow
import com.arkhins.ctrlaps.ui.components.Avatar
import androidx.compose.ui.unit.sp
import com.arkhins.ctrlaps.ui.components.GroupTitle
import com.arkhins.ctrlaps.ui.components.SearchPill
import com.arkhins.ctrlaps.ui.components.Chip
import com.arkhins.ctrlaps.ui.components.Composer
import com.arkhins.ctrlaps.ui.components.DateField
import com.arkhins.ctrlaps.ui.components.Divider
import com.arkhins.ctrlaps.ui.components.Empty
import com.arkhins.ctrlaps.ui.components.ErrorText
import com.arkhins.ctrlaps.ui.components.Field
import com.arkhins.ctrlaps.ui.components.GhostButton
import com.arkhins.ctrlaps.ui.components.GoldButton
import com.arkhins.ctrlaps.ui.components.IconAction
import com.arkhins.ctrlaps.ui.components.KeyValue
import com.arkhins.ctrlaps.ui.components.Loading
import com.arkhins.ctrlaps.ui.components.Panel
import com.arkhins.ctrlaps.ui.components.SectionTitle
import com.arkhins.ctrlaps.ui.components.StatusChip
import com.arkhins.ctrlaps.ui.components.statusTone
import com.arkhins.ctrlaps.ui.theme.Danger
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.NightPanel
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import com.arkhins.ctrlaps.ui.theme.SnowSoft
import kotlinx.coroutines.launch
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

val ROLE_ORDER = listOf("admin", "coordinator", "race_official", "team_manager", "racer", "crew", "security_head", "security", "volunteer", "user")
/** The People list's groups: developers first, apart from other admins, then each role. */
private val PEOPLE_GROUPS = listOf("developer") + ROLE_ORDER
val ROLE_LABELS = mapOf(
    "developer" to "Developer", "admin" to "Admin", "coordinator" to "Coordinator", "race_official" to "Delegate", "team_manager" to "Team manager",
    "racer" to "Racer", "crew" to "Crew", "security_head" to "Security head", "security" to "Security", "volunteer" to "Volunteer", "user" to "User",
)

/** A role group's title: Admins, Delegates; Security and Crew stay as they are. */
private fun rolePlural(role: String): String = when (role) {
    "security", "crew" -> ROLE_LABELS[role].orEmpty()
    else -> "${ROLE_LABELS[role] ?: role}s"
}

/** The People list's group for a person: developers show apart from other admins. */
private val PublicUser.group: String get() = if (isDev) "developer" else role

/** The People tab's pages, as its header names them: People; Teams for admins and coordinators; Categories for admins. */
fun peoplePages(role: String?): List<String> = when (role) {
    "admin" -> listOf("People", "Teams", "Categories")
    "coordinator" -> listOf("People", "Teams")
    else -> listOf("People")
}

/** The People tab: the people, and a swipe to the left for the teams and (admins) the race categories. */
@Composable
fun PeopleTab(me: Me?, page: Int, onPage: (Int) -> Unit, onOpen: (String) -> Unit, onAdd: () -> Unit, onEmail: (String?) -> Unit) {
    val pages = peoplePages(me?.user?.role)
    if (pages.size == 1) {
        PeopleScreen(me, onOpen, onAdd, onEmail)
        return
    }
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = page.coerceIn(0, pages.size - 1)) { pages.size }
    LaunchedEffect(page) { if (pager.currentPage != page && page in pages.indices) pager.animateScrollToPage(page) }
    LaunchedEffect(pager.currentPage) { if (pager.currentPage != page) onPage(pager.currentPage) }
    androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { i ->
        when (i) {
            0 -> PeopleScreen(me, onOpen, onAdd, onEmail)
            1 -> TeamsScreen()
            else -> CategoriesEditorScreen(onSaved = {})
        }
    }
}

/** Everyone below the signed-in person, grouped by role. */
@Composable
fun PeopleScreen(me: Me?, onOpen: (String) -> Unit, onAdd: () -> Unit, onEmail: (String?) -> Unit) {
    val app = LocalApp.current
    var people by remember { mutableStateOf<List<PublicUser>?>(null) }
    var roster by remember { mutableStateOf<List<RosterCategory>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var mailMenu by remember { mutableStateOf(false) }
    var filterMenu by remember { mutableStateOf(false) }
    // Roles left out of the list; people who registered and have no role yet, and developers, start hidden.
    var hiddenRoles by rememberSaveable { mutableStateOf(listOf("user", "developer")) }
    // Only the people starred (on their page, their card or Verify); off to start.
    var starredOnly by rememberSaveable { mutableStateOf(false) }
    val starred by app.verifyHistory.starred.collectAsState()
    // One race category (its people) and one team; blank is any.
    var category by rememberSaveable { mutableStateOf("") }
    var team by rememberSaveable { mutableStateOf("") }

    // Pulling the list down asks for it again.
    var reload by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        try {
            val r = app.store.get("/api/users", UsersResponse.serializer()) { people = it.users; roster = it.categories }
            people = r.users
            roster = r.categories
            error = null
        } catch (e: Exception) {
            if (people == null) error = e.message
        }
    }

    val canCreate = me?.canCreate?.isNotEmpty() == true
    val canEmail = me?.canBulkEmail == true || me?.canRelay == true
    val inCategory = roster.firstOrNull { it.id == category }?.memberIds?.toSet()
    val teams = people.orEmpty().mapNotNull { it.teamName?.trim()?.takeIf { t -> t.isNotEmpty() } }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
    val p = people?.filter { u ->
        u.group !in hiddenRoles && (!starredOnly || u.id in starred) &&
            (inCategory == null || u.id in inCategory) && (team.isBlank() || u.teamName?.trim().equals(team, ignoreCase = true)) && (query.isBlank() || "${u.displayName} ${u.roleLabel} ${u.teamName ?: ""}".contains(query.trim(), ignoreCase = true))
    }
    val presentRoles = PEOPLE_GROUPS.filter { r -> people?.any { it.group == r } == true }
    // Each person's race category codes, for the line under their name.
    val codes = remember(roster) { roster.flatMap { c -> c.memberIds.map { it to c.code } }.groupBy({ it.first }, { it.second }) }
    PullRefresh(onRefresh = { reload++ }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SearchPill(query, "Name, designation or team", Modifier.weight(1f)) { query = it }
                    if (presentRoles.isNotEmpty()) {
                        Box {
                            IconAction(painterResource(R.drawable.ic_filter), "Filter by role", Gold) { filterMenu = true }
                            DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }, containerColor = NightPanel) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = starredOnly,
                                                onCheckedChange = null,
                                                colors = CheckboxDefaults.colors(checkedColor = Gold, checkmarkColor = OnGold, uncheckedColor = SnowFaint),
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Icon(painterResource(R.drawable.ic_star), contentDescription = null, tint = Gold, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Starred only", color = Snow)
                                        }
                                    },
                                    onClick = { starredOnly = !starredOnly },
                                )
                                HorizontalDivider(color = NightLine, modifier = Modifier.padding(vertical = 4.dp))
                                Text("SHOW", style = MaterialTheme.typography.labelSmall, color = SnowFaint, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                                presentRoles.forEach { r ->
                                    val shown = r !in hiddenRoles
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = shown,
                                                    onCheckedChange = null,
                                                    colors = CheckboxDefaults.colors(checkedColor = Gold, checkmarkColor = OnGold, uncheckedColor = SnowFaint),
                                                )
                                                Spacer(Modifier.width(10.dp))
                                                Text(ROLE_LABELS[r] ?: r, color = Snow)
                                            }
                                        },
                                        onClick = { hiddenRoles = if (shown) hiddenRoles + r else hiddenRoles - r },
                                    )
                                }
                                // One category or one team at a time; tapping the chosen one again clears it.
                                if (roster.isNotEmpty()) {
                                    HorizontalDivider(color = NightLine, modifier = Modifier.padding(vertical = 4.dp))
                                    Text("CATEGORY", style = MaterialTheme.typography.labelSmall, color = SnowFaint, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                                    roster.forEach { c ->
                                        val on = category == c.id
                                        val color = runCatching { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(c.color)) }.getOrDefault(Gold)
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    RadioButton(selected = on, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Gold, unselectedColor = SnowFaint))
                                                    Spacer(Modifier.width(10.dp))
                                                    Text(c.code, color = color, fontWeight = FontWeight.SemiBold)
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(c.name, color = SnowSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            },
                                            onClick = { category = if (on) "" else c.id },
                                        )
                                    }
                                }
                                if (teams.isNotEmpty()) {
                                    HorizontalDivider(color = NightLine, modifier = Modifier.padding(vertical = 4.dp))
                                    Text("TEAM", style = MaterialTheme.typography.labelSmall, color = SnowFaint, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                                    teams.forEach { t ->
                                        val on = team.equals(t, ignoreCase = true)
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    RadioButton(selected = on, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Gold, unselectedColor = SnowFaint))
                                                    Spacer(Modifier.width(10.dp))
                                                    Text(t, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            },
                                            onClick = { team = if (on) "" else t },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (canCreate) IconAction(Icons.Outlined.Add, "Add person", Gold, onClick = onAdd)
                    if (canEmail) {
                        Box {
                            IconAction(Icons.Outlined.Email, "Email", Danger) {
                                if (me?.canBulkEmail == true) onEmail(null) else mailMenu = true
                            }
                            DropdownMenu(expanded = mailMenu, onDismissRequest = { mailMenu = false }, containerColor = NightPanel) {
                                DropdownMenuItem(text = { Text("Email volunteers", color = Snow) }, onClick = { mailMenu = false; onEmail("volunteers") })
                                DropdownMenuItem(text = { Text("Email security", color = Snow) }, onClick = { mailMenu = false; onEmail("security") })
                            }
                        }
                    }
                }
            }
            // What the filter narrows to, each with a tap to take it off.
            val cat = roster.firstOrNull { it.id == category }
            if (starredOnly || cat != null || team.isNotBlank()) {
                item {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (starredOnly) Chip("Starred  ✕", Gold, filled = true) { starredOnly = false }
                        if (cat != null) Chip("${cat.code}  ✕", Gold, filled = true) { category = "" }
                        if (team.isNotBlank()) Chip("$team  ✕", Gold, filled = true) { team = "" }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
            when {
                error != null && p == null -> item { ErrorText(error) }
                p == null -> item { Loading() }
                p.isEmpty() -> item {
                    Empty(
                        title = if (people?.isEmpty() == true) "Nobody here yet" else "No one to show",
                        action = if (people?.isEmpty() == true && canCreate) "Add a person" else null,
                        onAction = onAdd,
                        text = when {
                            people?.isEmpty() == true -> if (canCreate) "Invite people by email, or promote someone who registered." else "Nobody reports to you."
                            (category.isNotBlank() || team.isNotBlank()) && query.isBlank() -> "Nobody matches this category or team."
                            starredOnly && query.isBlank() -> "No starred people here. Star someone from their page, or turn off Starred only."
                            query.isBlank() -> "Everyone here is hidden by the filter. Tap the filter icon to show them."
                            else -> "No one matches."
                        },
                    )
                }
                else -> {
                    val groups = PEOPLE_GROUPS.mapNotNull { r -> p.filter { it.group == r }.takeIf { it.isNotEmpty() }?.let { r to it } }
                    groups.forEach { (role, list) ->
                        item(key = "g-$role") {
                        // Volunteers and security can be emailed from their heading, by those who may.
                        val mail = when {
                            role != "volunteer" && role != "security" -> null
                            me?.canBulkEmail == true -> ({ onEmail(null) })
                            me?.canRelay == true -> ({ onEmail(if (role == "volunteer") "volunteers" else "security") })
                            else -> null
                        }
                        GroupTitle(rolePlural(role), list.size, Modifier.padding(top = 10.dp), action = if (mail != null) "Email" else null, onAction = mail)
                    }
                        items(list, key = { it.id }) { u -> PersonRow(app.api.absolute(u.photoUrl), u, u.id in starred, codes[u.id].orEmpty()) { onOpen(u.id) } }
                    }
                }
            }
        }
    }
}

/**
 * A person in the People list, flat as the Account pages: their photo, their name (a star if starred), and under it
 * their team and race categories (else their email); "Invite not accepted" in the accent while invited. A status
 * shows only when it is not the usual one.
 */
@Composable
private fun PersonRow(photo: String?, u: PublicUser, starred: Boolean, codes: List<String>, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(photo, u.displayName, size = 46)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    u.displayName,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, lineHeight = 20.sp),
                    fontWeight = FontWeight.Bold,
                    color = Snow,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (starred) {
                    Spacer(Modifier.width(6.dp))
                    Icon(painterResource(R.drawable.ic_star), contentDescription = "Starred", tint = Gold, modifier = Modifier.size(14.dp))
                }
            }
            val invited = u.name == null
            val line = when {
                invited -> "Invite not accepted"
                else -> listOfNotNull(u.teamName?.trim()?.takeIf { it.isNotEmpty() }, codes.joinToString(" · ").takeIf { it.isNotEmpty() }).joinToString(" · ").ifEmpty { u.email }
            }
            Text(line, style = MaterialTheme.typography.bodySmall, color = if (invited) Gold else SnowSoft.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (u.status != "active" && u.status != "pending") {
            Spacer(Modifier.width(8.dp))
            StatusChip(u.status, u.statusLabel)
        }
    }
}

/** One person: the profile, and what may be done to it. */
@Composable
fun PersonScreen(me: Me?, userId: String, onOpenChat: (String) -> Unit, onTitle: (String) -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<UserResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var promoting by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    val context = LocalContext.current
    var cropping by remember { mutableStateOf<Bitmap?>(null) }
    // A photo picked while the edit form is open waits for Save; otherwise it is saved at once.
    var newPhoto by remember { mutableStateOf<Bitmap?>(null) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { cropping = withContext(Dispatchers.IO) { loadShrunk(context, uri, 1600) } }
    }

    LaunchedEffect(userId, reload) {
        try {
            data = app.store.get("/api/users/$userId", UserResponse.serializer()) {
                if (data == null) data = it
                onTitle(it.user.displayName)
            }.also { onTitle(it.user.displayName) }
        } catch (e: Exception) {
            error = e.message
        }
    }

    fun run(done: String?, block: suspend () -> Unit) {
        busy = true
        error = null
        note = null
        scope.launch {
            try {
                block()
                note = done
                reload++
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            } finally {
                busy = false
            }
        }
    }

    val d = data
    if (showQr && d != null) {
        val qr = rememberQr(d.qrUrl)
        AboutSheet("${d.user.displayName}'s QR", onClose = { showQr = false }, expanded = true) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.background(Color.White, RoundedCornerShape(16.dp)).padding(12.dp).size(240.dp)) {
                    if (qr != null) Image(qr.asImageBitmap(), contentDescription = "QR code", modifier = Modifier.size(240.dp))
                }
                Spacer(Modifier.height(14.dp))
                KeyValue("Account code", d.user.verifyCode, mono = true, copyable = true)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    cropping?.let { src ->
        SquareCropDialog(src, onCancel = { cropping = null }) { square ->
            cropping = null
            if (editing) {
                newPhoto = square
                return@SquareCropDialog
            }
            run("Photo changed.") {
                val file = withContext(Dispatchers.IO) {
                    File(context.cacheDir, "photo.jpg").also { f -> f.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
                }
                try {
                    app.api.postForm("/api/users/$userId/photo", emptyMap(), "photo" to file, "image/jpeg", Ok.serializer())
                } finally {
                    file.delete()
                }
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (d == null) {
            item { if (error != null) ErrorText(error) else Loading(shape = LoadingShape.Banner) }
            return@LazyColumn
        }
        val u = d.user
        // The banner, as on the Account tab: photo blurred behind, name in the accent; a tap on the photo pulls it up.
        // The photo only shows here; it is changed in Edit, with the rest of their profile.
        item { ProfileBanner(app.api.absolute(u.photoUrl), u.displayName, u.roleLabel + (u.teamName?.let { " · $it" } ?: "")) { StatusChip(u.status, u.statusLabel) } }
        item {
            val starred by app.verifyHistory.starred.collectAsState()
            val on = u.id in starred
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (u.id != me?.user?.id && u.status == "active" && u.role != "user" && me?.user?.role != "user") {
                    QuickAction(painterResource(R.drawable.ic_tab_chat), "Message", Modifier.weight(1f), enabled = !busy) {
                        run(null) { onOpenChat(app.api.post("/api/conversations", IdResponse.serializer()) { put("memberId", u.id) }.id) }
                    }
                }
                // The same star as Verify's list: starred people are pinned there.
                QuickAction(painterResource(if (on) R.drawable.ic_star else R.drawable.ic_star_border), if (on) "Starred" else "Star", Modifier.weight(1f), highlight = on) {
                    app.verifyHistory.toggleStar(Verified(u.id, u.name, u.role, u.roleLabel, u.teamName, u.status, u.statusLabel, u.verifyCode, u.photoUrl, u.profileComplete))
                }
                QuickAction(painterResource(R.drawable.ic_scan), "QR code", Modifier.weight(1f)) { showQr = true }
                if (d.canEdit && u.profileComplete && !editing) QuickAction(rememberVectorPainter(Icons.Outlined.Edit), "Edit", Modifier.weight(1f), enabled = !busy) { editing = true }
            }
        }
        item {
            Column(Modifier.padding(top = 8.dp)) {
                DetailRow(Icons.Outlined.Email, "Email", u.email)
                DetailRow(Icons.Outlined.Phone, "Contact", u.phone ?: "—")
                DetailRow(Icons.Outlined.DateRange, "Date of birth", u.dob ?: "—")
                // The team opens its page.
                if (u.teamName != null) {
                    val openTeam = LocalOpen.current
                    Box(Modifier.then(if (u.teamId != null) Modifier.clickable { openTeam("team/${u.teamId}") } else Modifier)) {
                        DetailRow(Icons.Outlined.Person, "Team", u.teamName) {
                            if (u.teamId != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Open the team", tint = Gold)
                        }
                    }
                }
                DetailRow(Icons.Outlined.Lock, "Account code", u.verifyCode) { CopyButton(u.verifyCode) }
            }
        }
        item { ErrorText(error) }
        if (note != null) item { Text(note!!, style = MaterialTheme.typography.bodyMedium, color = Gold) }
        if (u.status == "pending") item {
            MenuRow("Resend invite", "The link to join, emailed again", icon = rememberVectorPainter(Icons.Outlined.Email), arrow = false) {
                if (!busy) run("Invite sent again.") { app.api.post("/api/users/${u.id}/invite", Ok.serializer()) }
            }
        }
        // Promote someone who registered (or change the role of someone this person manages): the roles they may
        // give, and only the boxes that role needs. The server checks the same rules.
        val canPromote = u.id != me?.user?.id && me?.canCreate?.isNotEmpty() == true &&
            u.status != "banned" && u.status != "dismissed" && (u.role == "user" || d.canEdit)
        item { RaceCategoriesPanel(d) }
        if (canPromote) {
            item {
                MenuRow(
                    if (u.role == "user") "Promote" else "Change role",
                    if (u.role == "user") "Give ${u.displayName} a role" else "Now ${u.roleLabel}",
                    icon = rememberVectorPainter(Icons.Outlined.AccountBox),
                    arrow = false,
                ) { if (!busy) promoting = true }
            }
        }
        if (canPromote && promoting) {
            item {
                AboutSheet(if (u.role == "user") "Promote" else "Change role", onClose = { promoting = false }, expanded = true) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                PromotePanel(me!!, u, busy = busy, onCancel = { promoting = false }) { role, team, delegation ->
                    run("Now a ${ROLE_LABELS[role] ?: role}.") {
                        app.api.post("/api/users", UserResponse.serializer()) {
                            put("email", u.email)
                            put("role", role)
                            put("teamName", team)
                            delegation?.let { put("delegationId", it) }
                        }
                        promoting = false
                    }
                }
                }
                }
            }
        }
        if (d.canDelete) {
            item {
                DeletePanel(u.name ?: u.email, d.deletionDueAt, busy,
                    onDelete = { run("Will be deleted in 7 days.") { app.api.post("/api/users/${u.id}/deletion", Ok.serializer()) } },
                    onCancel = { run("Deletion cancelled.") { app.api.delete("/api/users/${u.id}/deletion") } })
            }
        }
        if (d.canSetDev) item { DeveloperPanel(u.isDev, busy) { dev -> run(if (dev) "Now a developer." else "No longer a developer.") { app.api.put("/api/users/${u.id}/developer", UserResponse.serializer()) { put("dev", dev) } } } }
        if (d.canEdit) {
            item {
                Panel {
                    Column {
                        SectionTitle("STATUS")
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val choices = if (u.status == "pending") listOf("dismissed", "banned") else listOf("active", "suspended", "dismissed", "banned")
                            choices.forEach { s ->
                                Chip(s.replaceFirstChar { it.uppercase() }, statusTone(s), filled = u.status == s) {
                                    if (u.status != s && !busy) run("Status changed.") { app.api.patch("/api/users/${u.id}", UserResponse.serializer()) { put("status", s) } }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (editing) {
            item {
                EditProfilePanel(
                    u,
                    photoUrl = app.api.absolute(u.photoUrl),
                    newPhoto = newPhoto,
                    busy = busy,
                    onPickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onCancel = { editing = false; newPhoto = null },
                ) { name, dob, phone, team ->
                    run("Saved.") {
                        app.api.patch("/api/users/${u.id}", UserResponse.serializer()) {
                            put("name", name)
                            put("dob", dob)
                            put("phone", phone)
                            if (u.role == "team_manager") put("teamName", team)
                        }
                        newPhoto?.let { square ->
                            val file = withContext(Dispatchers.IO) {
                                File(context.cacheDir, "photo.jpg").also { f -> f.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
                            }
                            try {
                                app.api.postForm("/api/users/${u.id}/photo", emptyMap(), "photo" to file, "image/jpeg", Ok.serializer())
                            } finally {
                                file.delete()
                            }
                        }
                        newPhoto = null
                        editing = false
                    }
                }
            }
        }
    }
}

@Composable
private fun EditProfilePanel(
    u: PublicUser,
    photoUrl: String?,
    newPhoto: Bitmap?,
    busy: Boolean,
    onPickPhoto: () -> Unit,
    onCancel: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf(u.name ?: "") }
    var dob by remember { mutableStateOf(u.dob ?: "") }
    var phone by remember { mutableStateOf(u.phone ?: "") }
    var team by remember { mutableStateOf(u.teamName ?: "") }
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EditablePhoto(photoUrl, newPhoto, u.displayName, 72, editable = !busy, onClick = onPickPhoto)
                Spacer(Modifier.width(14.dp))
                Text("Tap the photo to change it.", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
            }
            Field(name, { name = it }, "Full name", enabled = !busy)
            DateField(dob, { dob = it }, "Date of birth", enabled = !busy, maxToday = true)
            Field(phone, { phone = it }, "Contact number", keyboard = KeyboardType.Phone, enabled = !busy)
            if (u.role == "team_manager") TeamPicker(team, { team = it }, "Team", enabled = !busy)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Cancel", enabled = !busy, onClick = onCancel)
                GoldButton(if (busy) "Saving…" else "Save", enabled = !busy) { onSave(name.trim(), dob.trim(), phone.trim(), team.trim()) }
            }
        }
    }
}

/** An email, a role and optionally a photo: a new email gets an invite, one that already has an account is promoted. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewPersonScreen(me: Me?, onCreated: (String) -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val roles = me?.canCreate ?: emptyList()
    val iAmTeamManager = me?.user?.role == "team_manager"
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(roles.firstOrNull() ?: "") }
    var team by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var cropping by remember { mutableStateOf<Bitmap?>(null) }
    var createdId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // A delegate can go straight into their delegation (JK Tyre, FMSCI…); none leaves them for the Delegations page.
    var delegations by remember { mutableStateOf<List<VolunteerGroupRow>>(emptyList()) }
    var delegation by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(role) {
        if (role == "race_official" && delegations.isEmpty()) {
            delegations = runCatching { app.api.get("/api/volunteer-groups?kind=delegation", VolunteerGroupsResponse.serializer()).groups }.getOrDefault(emptyList())
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { cropping = withContext(Dispatchers.IO) { loadShrunk(context, uri, 1600) } }
    }
    fun choosePhoto() = pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    cropping?.let { src -> SquareCropDialog(src, onCancel = { cropping = null }) { photo = it; cropping = null } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ErrorText(error)
                createdId?.let { id -> GhostButton("Open their page", Modifier.fillMaxWidth()) { onCreated(id) } }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Night)
                            .border(1.dp, NightLine, CircleShape)
                            .clickable(enabled = !busy && createdId == null) { choosePhoto() },
                        contentAlignment = Alignment.Center,
                    ) {
                        val bmp = photo
                        if (bmp != null) Image(bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp))
                        else Text("Photo", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        GhostButton(if (photo == null) "Add photo" else "Change photo", enabled = !busy && createdId == null) { choosePhoto() }
                        Text("Optional. They can add their own when they set up.", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
                    }
                }

                Field(email, { email = it }, "Email", keyboard = KeyboardType.Email, enabled = !busy && createdId == null)

                Column {
                    SectionTitle("ROLE")
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        roles.forEach { r -> Chip(ROLE_LABELS[r] ?: r, Gold, filled = role == r) { if (!busy && createdId == null) role = r } }
                    }
                }

                if (role == "team_manager") TeamPicker(team, { team = it }, "Team", enabled = !busy && createdId == null)
                if (role == "race_official") {
                    Column {
                        SectionTitle("DELEGATION")
                        Spacer(Modifier.height(8.dp))
                        if (delegations.isEmpty()) {
                            Text("No delegations yet. Make one under Chats → Delegations; they can be put in it later.", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Chip("None for now", SnowSoft, filled = delegation == null) { if (!busy && createdId == null) delegation = null }
                                delegations.forEach { g -> Chip(g.name, Gold, filled = delegation == g.id) { if (!busy && createdId == null) delegation = g.id } }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("They join its group chat straight away.", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
                        }
                    }
                }
                if (role == "racer" || role == "crew") {
                    if (iAmTeamManager) me?.user?.teamName?.let { Text("Team: $it", style = MaterialTheme.typography.bodySmall, color = SnowFaint) }
                    else TeamPicker(team, { team = it }, "Team (optional)", enabled = !busy && createdId == null)
                }

                Text(
                    "A new email gets a link to choose a password and fill in their profile. If the email already has an account, they are given this role and told by email.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SnowFaint,
                )

                GoldButton(if (busy) "Saving…" else "Give role", Modifier.fillMaxWidth(), enabled = !busy && createdId == null && email.isNotBlank() && role.isNotBlank()) {
                    busy = true
                    error = null
                    scope.launch {
                        val id = try {
                            app.api.post("/api/users", UserResponse.serializer()) {
                                put("email", email.trim())
                                put("role", role)
                                put("teamName", team.trim())
                                if (role == "race_official") delegation?.let { put("delegationId", it) }
                            }.user.id
                        } catch (e: Exception) {
                            error = e.message ?: "Could not save."
                            busy = false
                            return@launch
                        }
                        val bmp = photo
                        if (bmp != null) {
                            val file = withContext(Dispatchers.IO) {
                                File(context.cacheDir, "photo.jpg").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
                            }
                            try {
                                app.api.postForm("/api/users/$id/photo", emptyMap(), "photo" to file, "image/jpeg", Ok.serializer())
                            } catch (e: Exception) {
                                createdId = id
                                error = "The role was given, but the photo didn't upload: ${e.message ?: "unknown error"}. Add it from their page."
                                busy = false
                                return@launch
                            } finally {
                                file.delete()
                            }
                        }
                        onCreated(id)
                    }
                }
            }
        }
    }
}

/**
 * The coordinator's relay to volunteers/security, or the admin's mail to the chosen roles. The groups (chips) scroll on
 * their own above; the subject and the message box stay at the bottom and rise with the keyboard, as in a chat.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmailScreen(group: String?, onSent: () -> Unit) {
    val app = LocalApp.current
    var roles by remember { mutableStateOf(ROLE_ORDER.toSet()) }
    var sent by remember { mutableStateOf<Int?>(null) }
    // The mail's subject line; left empty, the first words of the message are used.
    var subject by remember { mutableStateOf("") }

    val s = sent
    if (s != null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Panel {
                Column {
                    Text("Sent to $s ${if (s == 1) "person" else "people"}.", color = Snow)
                    Spacer(Modifier.height(10.dp))
                    GoldButton("Back to people", onClick = onSent)
                }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        // Who it goes to: scrolls on its own, so the message box below never leaves the screen.
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (group != null) "Goes by email and as an urgent message to your $group." else "Pick the groups. Every active person in them gets the email and an urgent message.",
                style = MaterialTheme.typography.bodySmall,
                color = SnowSoft,
            )
            if (group == null) {
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("To · ${roles.size} of ${ROLE_ORDER.size} groups", style = MaterialTheme.typography.titleSmall, color = Snow, modifier = Modifier.weight(1f))
                            Chip("All", Gold, filled = roles.size == ROLE_ORDER.size) { roles = ROLE_ORDER.toSet() }
                            Spacer(Modifier.width(6.dp))
                            Chip("None", SnowSoft, filled = roles.isEmpty()) { roles = emptySet() }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ROLE_ORDER.forEach { r ->
                                Chip(ROLE_LABELS[r] ?: r, Gold, filled = r in roles) { roles = if (r in roles) roles - r else roles + r }
                            }
                        }
                    }
                }
            }
        }
        // The subject and the message, at the bottom, above the keyboard.
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(subject, { subject = it.take(150) }, "Subject", placeholder = "What the email is about")
            // One email, all the files attached to it.
            Composer(placeholder = "What everyone needs to know", urgentOption = false, sendLabel = "Send email", voiceNoteSends = false, oneMessagePerFile = false, linkPreviews = false) { d ->
                val r = if (group != null) {
                    app.api.post("/api/email/relay", SentResponse.serializer()) {
                        put("group", group)
                        put("subject", subject.trim())
                        put("body", d.body)
                        putJsonArray("fileIds") { d.fileIds.forEach { add(it) } }
                    }
                } else {
                    app.api.post("/api/email/bulk", SentResponse.serializer()) {
                        putJsonArray("roles") { roles.forEach { add(it) } }
                        put("subject", subject.trim())
                        put("body", d.body)
                        putJsonArray("fileIds") { d.fileIds.forEach { add(it) } }
                    }
                }
                sent = r.delivered
            }
        }
    }
}

/** A round photo; when [editable], tapping it picks a new one. */
@Composable
private fun EditablePhoto(url: String?, picked: Bitmap?, name: String, size: Int, editable: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(size.dp).then(if (editable) Modifier.clip(CircleShape).clickable(onClick = onClick) else Modifier)) {
        if (picked != null) Image(picked.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size.dp).clip(CircleShape))
        else Avatar(url, name, size)
    }
}

/** The roles [me] may give, as chips, then only what the chosen role needs: a team for a team manager, racers and crew. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PromotePanel(me: Me, u: PublicUser, busy: Boolean, onCancel: () -> Unit, onSave: (role: String, team: String, delegation: String?) -> Unit) {
    val app = LocalApp.current
    val roles = me.canCreate.filter { it != u.role }
    var role by remember { mutableStateOf(roles.firstOrNull() ?: "") }
    var team by remember { mutableStateOf(u.teamName ?: "") }
    // Made a delegate: their delegation can be picked here too.
    var delegations by remember { mutableStateOf<List<VolunteerGroupRow>>(emptyList()) }
    var delegation by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(role) {
        if (role == "race_official" && delegations.isEmpty()) {
            delegations = runCatching { app.api.get("/api/volunteer-groups?kind=delegation", VolunteerGroupsResponse.serializer()).groups }.getOrDefault(emptyList())
        }
    }
    val iAmTeamManager = me.user.role == "team_manager"
    val needsTeam = role == "team_manager"
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (u.role == "user") "Promote ${u.displayName}" else "Change ${u.displayName}'s role", style = MaterialTheme.typography.titleMedium, color = Snow)
            SectionTitle("NEW ROLE")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                roles.forEach { r -> Chip(ROLE_LABELS[r] ?: r, Gold, filled = role == r) { if (!busy) role = r } }
            }
            when (role) {
                "team_manager" -> TeamPicker(team, { team = it }, "Team name", enabled = !busy)
                "racer", "crew" ->
                    if (iAmTeamManager) me.user.teamName?.let { Text("Team: $it", style = MaterialTheme.typography.bodySmall, color = SnowSoft) }
                    else TeamPicker(team, { team = it }, "Team (optional)", enabled = !busy)
                "race_official" -> if (delegations.isNotEmpty()) {
                    SectionTitle("DELEGATION")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("None for now", SnowSoft, filled = delegation == null) { if (!busy) delegation = null }
                        delegations.forEach { g -> Chip(g.name, Gold, filled = delegation == g.id) { if (!busy) delegation = g.id } }
                    }
                }
            }
            Text(
                "${u.displayName} is told by email. " + if (u.role == "user") "They get the chats and pages of the new role at once." else "Their chats and pages change to the new role at once.",
                style = MaterialTheme.typography.labelSmall,
                color = SnowFaint,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Cancel", enabled = !busy, onClick = onCancel)
                GoldButton(
                    if (busy) "Saving…" else "Make ${ROLE_LABELS[role] ?: role}",
                    enabled = !busy && role.isNotBlank() && (!needsTeam || team.isNotBlank()),
                ) { onSave(role, if (role == "team_manager" || ((role == "racer" || role == "crew") && !iAmTeamManager)) team.trim() else "", delegation.takeIf { role == "race_official" }) }
            }
        }
    }
}

/**
 * A person's race categories: a racer's classes ("Races in", from their team's entries), a race official's ("Looks
 * after"), or what the team of crew or a team manager runs. Their manager, a coordinator or an admin taps to change a
 * racer's or an official's.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RaceCategoriesPanel(d: UserResponse) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val u = d.user
    val all = d.raceCategories
    val assignable = u.role == "racer"
    var ids by remember(u.id, d.categoryIds) { mutableStateOf(d.categoryIds) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    if (all.isEmpty() || (!assignable && d.teamCategoryIds.isEmpty())) return
    val offered = if (u.role == "racer" && d.teamCategoryIds.isNotEmpty()) all.filter { it.id in d.teamCategoryIds } else all
    val title = when (u.role) { "racer" -> "RACES IN"; "race_official" -> "LOOKS AFTER"; else -> "TEAM RACES IN" }
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(title)
            ErrorText(error)
            when {
                !assignable -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    all.filter { it.id in d.teamCategoryIds }.forEach { CategoryTag(it) }
                }
                d.canSetCategories -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        offered.forEach { c ->
                            Chip(c.code, categoryColor(c), filled = c.id in ids) {
                                if (busy) return@Chip
                                val before = ids
                                val next = if (c.id in ids) ids - c.id else ids + c.id
                                ids = next
                                busy = true
                                error = null
                                scope.launch {
                                    try {
                                        ids = app.api.put("/api/users/${u.id}/categories", CategoryIdsResponse.serializer()) {
                                            put("categoryIds", buildJsonArray { next.forEach { add(JsonPrimitive(it)) } })
                                        }.categoryIds
                                    } catch (e: Exception) {
                                        ids = before
                                        error = e.message ?: "Could not save."
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        if (u.role == "racer") "Tap the classes they race in. None ticked: their team's categories are used." else "None ticked: all categories.",
                        style = MaterialTheme.typography.labelSmall,
                        color = SnowFaint,
                    )
                }
                ids.isNotEmpty() -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    all.filter { it.id in ids }.forEach { CategoryTag(it) }
                }
                else -> Text(
                    if (u.role == "racer") "Not set — their team's categories are used." else "All categories.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SnowFaint,
                )
            }
        }
    }
}

/** For a developer on another admin's page: make them a developer (they answer support), or take it back after asking. */
@Composable
private fun DeletePanel(name: String, dueAt: String?, busy: Boolean, onDelete: () -> Unit, onCancel: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    if (dueAt != null) {
        val day = runCatching {
            java.time.Instant.parse(dueAt).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy"))
        }.getOrDefault(dueAt.take(10))
        MenuRow("Cancel deletion", "Will be deleted on $day, unless they sign in before then", icon = rememberVectorPainter(Icons.Outlined.Delete), danger = true, arrow = false) { if (!busy) onCancel() }
    } else {
        MenuRow("Delete account", "Only when they ask for it: an email and 7 days to change their mind", icon = rememberVectorPainter(Icons.Outlined.Delete), danger = true, arrow = false) { if (!busy) asking = true }
    }
    if (asking) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { asking = false },
            containerColor = NightPanel,
            title = { Text("Delete $name's account?", color = Snow) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DELETION_POINTS.forEach { Text("•  $it", style = MaterialTheme.typography.bodySmall, color = SnowSoft) }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { asking = false; onDelete() }) { Text("Delete", color = Danger) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { asking = false }) { Text("Cancel", color = SnowSoft) } },
        )
    }
}

@Composable
private fun DeveloperPanel(isDev: Boolean, busy: Boolean, onChange: (Boolean) -> Unit) {
    var asking by remember { mutableStateOf(false) }
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("DEVELOPER")
            Text(
                if (isDev) "A developer: an admin who also answers support tickets. Shown as Developer here, and only as Support to people who ask for help."
                else "Make this admin a developer: they also answer support tickets, and show as Developer.",
                style = MaterialTheme.typography.bodySmall,
                color = SnowFaint,
            )
            if (isDev) GhostButton("Remove developer", enabled = !busy) { asking = true }
            else GoldButton("Make developer", Modifier.fillMaxWidth(), enabled = !busy) { onChange(true) }
        }
    }
    if (asking) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { asking = false },
            containerColor = NightPanel,
            title = { Text("Remove developer?", color = Snow) },
            text = { Text("They stay an admin, and no longer see support tickets.", color = SnowSoft) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { asking = false; onChange(false) }) { Text("Remove", color = Gold) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { asking = false }) { Text("Cancel", color = SnowSoft) } },
        )
    }
}

/** A quick action under a person's banner: an icon over a word, on a soft tile. */
@Composable
fun QuickAction(icon: Painter, label: String, modifier: Modifier = Modifier, enabled: Boolean = true, highlight: Boolean = false, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(NightHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Gold, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (highlight) Gold else Snow)
    }
}
