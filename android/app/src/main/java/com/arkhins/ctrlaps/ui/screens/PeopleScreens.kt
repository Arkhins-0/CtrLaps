package com.arkhins.ctrlaps.ui.screens

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
import com.arkhins.ctrlaps.ui.components.Avatar
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
val ROLE_LABELS = mapOf(
    "admin" to "Admin", "coordinator" to "Coordinator", "race_official" to "Race official", "team_manager" to "Team manager",
    "racer" to "Racer", "crew" to "Crew", "security_head" to "Security head", "security" to "Security", "volunteer" to "Volunteer", "user" to "User",
)

/** Everyone below the signed-in person, grouped by role. */
@Composable
fun PeopleScreen(me: Me?, onOpen: (String) -> Unit, onAdd: () -> Unit, onEmail: (String?) -> Unit) {
    val app = LocalApp.current
    val openRoute = LocalOpen.current
    var people by remember { mutableStateOf<List<PublicUser>?>(null) }
    var roster by remember { mutableStateOf<List<RosterCategory>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var mailMenu by remember { mutableStateOf(false) }
    var filterMenu by remember { mutableStateOf(false) }
    // Roles left out of the list; people who registered and have no role yet start hidden.
    var hiddenRoles by rememberSaveable { mutableStateOf(listOf("user")) }
    // Only the people starred (on their page, their card or Verify); off to start.
    var starredOnly by rememberSaveable { mutableStateOf(false) }
    val starred by app.verifyHistory.starred.collectAsState()
    // One race category (its people) and one team; blank is any.
    var category by rememberSaveable { mutableStateOf("") }
    var team by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
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
        u.role !in hiddenRoles && (!starredOnly || u.id in starred) &&
            (inCategory == null || u.id in inCategory) && (team.isBlank() || u.teamName?.trim().equals(team, ignoreCase = true)) && (query.isBlank() || "${u.displayName} ${u.roleLabel} ${u.teamName ?: ""}".contains(query.trim(), ignoreCase = true))
    }
    val presentRoles = ROLE_ORDER.filter { r -> people?.any { it.role == r } == true }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Field(query, { query = it }, "Search", modifier = Modifier.weight(1f), placeholder = "Name, designation or team")
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
                if (me?.user?.role == "admin" || me?.user?.role == "coordinator") {
                    Text(if (me.user.role == "admin") "Teams & categories" else "Teams", style = MaterialTheme.typography.labelLarge, color = Gold, modifier = Modifier.clickable { openRoute("teams") }.padding(horizontal = 8.dp, vertical = 10.dp))
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
        when {
            error != null && p == null -> item { ErrorText(error) }
            p == null -> item { Loading() }
            p.isEmpty() -> item {
                Empty(
                    when {
                        people?.isEmpty() == true -> if (canCreate) "Nobody yet. Add the first person." else "Nobody reports to you."
                        (category.isNotBlank() || team.isNotBlank()) && query.isBlank() -> "Nobody matches this category or team."
                        starredOnly && query.isBlank() -> "No starred people here. Star someone from their page, or turn off Starred only."
                        query.isBlank() -> "Everyone here is hidden by the filter. Tap the filter icon to show them."
                        else -> "No one matches."
                    },
                )
            }
            else -> {
                val groups = ROLE_ORDER.mapNotNull { r -> p.filter { it.role == r }.takeIf { it.isNotEmpty() }?.let { r to it } }
                items(groups, key = { it.first }) { (role, list) ->
                    Column {
                        SectionTitle("${ROLE_LABELS[role]}s · ${list.size}".uppercase())
                        Spacer(Modifier.height(6.dp))
                        Panel(padding = PaddingValues(6.dp)) {
                            Column {
                                list.forEachIndexed { i, u ->
                                    if (i > 0) Divider()
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpen(u.id) }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Avatar(app.api.absolute(u.photoUrl), u.displayName)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(u.displayName, style = MaterialTheme.typography.titleSmall, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                                if (u.id in starred) {
                                                    Spacer(Modifier.width(6.dp))
                                                    Icon(painterResource(R.drawable.ic_star), contentDescription = "Starred", tint = Gold, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                            Text(
                                                (if (u.name != null) u.email else "Invite not accepted") + (u.teamName?.let { " · $it" } ?: ""),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SnowFaint,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        StatusChip(u.status, u.statusLabel)
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
            item { if (error != null) ErrorText(error) else Loading() }
            return@LazyColumn
        }
        val u = d.user
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EditablePhoto(app.api.absolute(u.photoUrl), null, u.displayName, 64, editable = d.canEdit && !busy) {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(u.displayName, style = MaterialTheme.typography.titleLarge, color = Snow)
                        Text(u.roleLabel + (u.teamName?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyMedium, color = SnowSoft)
                        Spacer(Modifier.height(4.dp))
                        StatusChip(u.status, u.statusLabel)
                    }
                    // The same star as Verify's list: starred people are pinned there.
                    val starred by app.verifyHistory.starred.collectAsState()
                    val on = u.id in starred
                    IconButton(
                        onClick = {
                            app.verifyHistory.toggleStar(
                                Verified(u.id, u.name, u.role, u.roleLabel, u.teamName, u.status, u.statusLabel, u.verifyCode, u.photoUrl, u.profileComplete),
                            )
                        },
                        modifier = Modifier.align(Alignment.Top),
                    ) {
                        Icon(
                            painterResource(if (on) R.drawable.ic_star else R.drawable.ic_star_border),
                            contentDescription = if (on) "Unstar" else "Star",
                            tint = if (on) Gold else SnowFaint,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        KeyValue("Email", u.email)
                        KeyValue("Contact", u.phone ?: "—")
                        KeyValue("Date of birth", u.dob ?: "—")
                        KeyValue("Account code", u.verifyCode, mono = true, copyable = true)
                    }
                    // The person's own QR, the same one on their account page, so it can be scanned from here.
                    val qr = rememberQr(d.qrUrl)
                    if (qr != null) {
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(6.dp)) {
                            Image(qr.asImageBitmap(), contentDescription = "QR code", modifier = Modifier.size(140.dp))
                        }
                    }
                }
            }
        }
        item { ErrorText(error) }
        if (note != null) item { Text(note!!, style = MaterialTheme.typography.bodySmall, color = SnowSoft) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (u.id != me?.user?.id && u.status == "active" && u.role != "user" && me?.user?.role != "user") {
                    GoldButton("Open private chat", enabled = !busy) {
                        run(null) { onOpenChat(app.api.post("/api/conversations", IdResponse.serializer()) { put("memberId", u.id) }.id) }
                    }
                }
                if (u.status == "pending") GhostButton("Resend invite", enabled = !busy) { run("Invite sent again.") { app.api.post("/api/users/${u.id}/invite", Ok.serializer()) } }
                if (d.canEdit && u.profileComplete && !editing) GhostButton("Edit profile", enabled = !busy) { editing = true }
            }
        }
        // Promote someone who registered (or change the role of someone this person manages): the roles they may
        // give, and only the boxes that role needs. The server checks the same rules.
        val canPromote = u.id != me?.user?.id && me?.canCreate?.isNotEmpty() == true &&
            u.status != "banned" && u.status != "dismissed" && (u.role == "user" || d.canEdit)
        item { RaceCategoriesPanel(d) }
        if (canPromote && !promoting) {
            item { GoldButton(if (u.role == "user") "Promote" else "Change role", Modifier.fillMaxWidth(), enabled = !busy) { promoting = true } }
        }
        if (canPromote && promoting) {
            item {
                PromotePanel(me!!, u, busy = busy, onCancel = { promoting = false }) { role, team ->
                    run("Now a ${ROLE_LABELS[role] ?: role}.") {
                        app.api.post("/api/users", UserResponse.serializer()) {
                            put("email", u.email)
                            put("role", role)
                            put("teamName", team)
                        }
                        promoting = false
                    }
                }
            }
        }
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
            if (u.role == "team_manager") Field(team, { team = it }, "Team", enabled = !busy)
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
                        else Text("Photo", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        GhostButton(if (photo == null) "Add photo" else "Change photo", enabled = !busy && createdId == null) { choosePhoto() }
                        Text("Optional. They can add their own when they set up.", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
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

                if (role == "team_manager") Field(team, { team = it }, "Team", enabled = !busy && createdId == null)
                if (role == "racer" || role == "crew") {
                    if (iAmTeamManager) me?.user?.teamName?.let { Text("Team: $it", style = MaterialTheme.typography.labelSmall, color = SnowFaint) }
                    else Field(team, { team = it }, "Team (optional)", enabled = !busy && createdId == null)
                }

                Text(
                    "A new email gets a link to choose a password and fill in their profile. If the email already has an account, they are given this role and told by email.",
                    style = MaterialTheme.typography.labelSmall,
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

/** The coordinator's relay to volunteers/security, or the admin's mail to ticked roles. */
@Composable
fun EmailScreen(group: String?, onSent: () -> Unit) {
    val app = LocalApp.current
    var roles by remember { mutableStateOf(ROLE_ORDER.toSet()) }
    var sent by remember { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val s = sent
        if (s != null) {
            Panel {
                Column {
                    Text("Sent to $s ${if (s == 1) "person" else "people"}.", color = Snow)
                    Spacer(Modifier.height(10.dp))
                    GoldButton("Back to people", onClick = onSent)
                }
            }
            return@Column
        }
        Text(
            if (group != null) "Goes by email and as an urgent message to your $group." else "Tick the groups. Every active person in them gets the email and an urgent message.",
            style = MaterialTheme.typography.bodySmall,
            color = SnowSoft,
        )
        if (group == null) {
            Panel {
                Column {
                    ROLE_ORDER.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { roles = if (r in roles) roles - r else roles + r }) {
                            Checkbox(r in roles, { roles = if (it) roles + r else roles - r }, colors = CheckboxDefaults.colors(checkedColor = Gold))
                            Text(ROLE_LABELS[r] ?: r, style = MaterialTheme.typography.bodyMedium, color = Snow)
                        }
                    }
                }
            }
        }
        // One email, all the files attached to it.
        Composer(placeholder = "What everyone needs to know", urgentOption = false, sendLabel = "Send email", voiceNoteSends = false, oneMessagePerFile = false, linkPreviews = false) { d ->
            val r = if (group != null) {
                app.api.post("/api/email/relay", SentResponse.serializer()) {
                    put("group", group)
                    put("body", d.body)
                    putJsonArray("fileIds") { d.fileIds.forEach { add(it) } }
                }
            } else {
                app.api.post("/api/email/bulk", SentResponse.serializer()) {
                    putJsonArray("roles") { roles.forEach { add(it) } }
                    put("body", d.body)
                    putJsonArray("fileIds") { d.fileIds.forEach { add(it) } }
                }
            }
            sent = r.delivered
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
private fun PromotePanel(me: Me, u: PublicUser, busy: Boolean, onCancel: () -> Unit, onSave: (String, String) -> Unit) {
    val roles = me.canCreate.filter { it != u.role }
    var role by remember { mutableStateOf(roles.firstOrNull() ?: "") }
    var team by remember { mutableStateOf(u.teamName ?: "") }
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
                "team_manager" -> Field(team, { team = it }, "Team name", enabled = !busy)
                "racer", "crew" ->
                    if (iAmTeamManager) me.user.teamName?.let { Text("Team: $it", style = MaterialTheme.typography.bodySmall, color = SnowSoft) }
                    else Field(team, { team = it }, "Team (optional)", enabled = !busy)
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
                ) { onSave(role, if (role == "team_manager" || ((role == "racer" || role == "crew") && !iAmTeamManager)) team.trim() else "") }
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
    val assignable = u.role == "racer" || u.role == "race_official"
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
