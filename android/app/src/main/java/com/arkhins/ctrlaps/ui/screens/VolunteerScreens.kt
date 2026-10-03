package com.arkhins.ctrlaps.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.LocalApp
import com.arkhins.ctrlaps.data.GroupResponse
import com.arkhins.ctrlaps.data.IdResponse
import com.arkhins.ctrlaps.data.NamedRef
import com.arkhins.ctrlaps.data.VolunteerGroupDetail
import com.arkhins.ctrlaps.data.VolunteerGroupDetailResponse
import com.arkhins.ctrlaps.data.VolunteerGroupsResponse
import com.arkhins.ctrlaps.ui.AppViewModel
import com.arkhins.ctrlaps.ui.components.Avatar
import com.arkhins.ctrlaps.ui.components.Chip
import com.arkhins.ctrlaps.ui.components.Empty
import com.arkhins.ctrlaps.ui.components.ErrorText
import com.arkhins.ctrlaps.ui.components.Field
import com.arkhins.ctrlaps.ui.components.GoldButton
import com.arkhins.ctrlaps.ui.components.IconAction
import com.arkhins.ctrlaps.ui.components.Loading
import com.arkhins.ctrlaps.ui.components.Panel
import com.arkhins.ctrlaps.ui.components.SectionTitle
import com.arkhins.ctrlaps.ui.components.UnreadBadge
import com.arkhins.ctrlaps.ui.theme.Danger
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.NightPanel
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import com.arkhins.ctrlaps.ui.theme.SnowSoft
import com.arkhins.ctrlaps.ui.whenLabel
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/*
 * Volunteer groups in the app. Both screens read the phone's copy first (the background download keeps
 * /api/volunteer-groups and each managed group's page) and refresh from the server behind it, like every other page.
 */

/** What a volunteer may do in the chat, as the menu names it. */
private val PERMISSIONS = listOf("full" to "Can chat", "no_messages" to "Can't send messages", "read_only" to "Read only")
private fun permissionLabel(p: String) = when (p) { "no_messages" -> "Can't send"; "read_only" -> "Read only"; else -> null }

/**
 * The Chats tab's Volunteers page: each volunteer group's chat this person sees (admins all, a coordinator the groups
 * they lead, a volunteer their own). Admins and coordinators get a + to make a group and Manage on the ones they lead.
 */
@Composable
fun VolunteersScreen(vm: AppViewModel, onOpenChat: (String) -> Unit, onManage: (String) -> Unit, onMade: (String) -> Unit = onManage) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<VolunteerGroupsResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val isAdmin = vm.me?.isAdmin == true

    LaunchedEffect(vm.refreshTick, vm.chatTick) {
        try {
            val fresh = app.store.get("/api/volunteer-groups", VolunteerGroupsResponse.serializer()) { if (data == null) data = it }
            data = fresh
            error = null
            // Behind the list: each group's manage page and chat, so they open already current.
            fresh.groups.forEach { g ->
                launch {
                    if (g.canManage) runCatching { app.store.fetch("/api/volunteer-groups/${g.id}", VolunteerGroupDetailResponse.serializer()) }
                    runCatching { app.chatCache.sync(g.conversationId, markRead = false) }
                }
            }
        } catch (e: Exception) {
            if (data == null) error = e.message
        }
    }

    val d = data
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("VOLUNTEER GROUPS", Modifier.weight(1f))
                if (d?.canCreate == true) IconAction(Icons.Outlined.Add, "New volunteer group", Gold) { creating = true }
            }
        }
        when {
            error != null && d == null -> item { Box(Modifier.padding(16.dp)) { ErrorText(error) } }
            d == null -> item { Loading() }
            d.groups.isEmpty() -> item { Box(Modifier.padding(16.dp)) { Empty(if (d.canCreate) "No volunteer groups yet. Tap + to make the first one." else "You're not in a volunteer group yet.") } }
            else -> items(d.groups, key = { it.id }) { g ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenChat(g.conversationId) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.width(48.dp).height(48.dp).background(if (g.open) Gold.copy(alpha = 0.15f) else NightPanel, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text("🦺", style = MaterialTheme.typography.titleMedium) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(g.name, style = MaterialTheme.typography.titleMedium, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            g.lastMessage ?: "No messages yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (g.unread > 0) Snow else SnowFaint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(
                                g.coordinator?.name ?: "No coordinator",
                                "${g.volunteers} volunteer${if (g.volunteers == 1) "" else "s"}",
                                if (g.open) null else "chat closed",
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = SnowFaint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        g.lastMessageAt?.let { Text(whenLabel(it), style = MaterialTheme.typography.labelSmall, color = if (g.unread > 0) Gold else SnowFaint) }
                        if (g.unread > 0) {
                            Spacer(Modifier.height(4.dp))
                            UnreadBadge(g.unread, muted = false)
                        }
                        if (g.canManage) {
                            Spacer(Modifier.height(4.dp))
                            Text("Manage", style = MaterialTheme.typography.labelSmall, color = Gold, modifier = Modifier.clickable { onManage(g.conversationId) }.padding(2.dp))
                        }
                    }
                }
                Box(Modifier.padding(start = 76.dp)) { HorizontalDivider(color = SnowFaint.copy(alpha = 0.15f)) }
            }
        }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        var lead by remember { mutableStateOf<NamedRef?>(null) }
        var busy by remember { mutableStateOf(false) }
        var err by remember { mutableStateOf<String?>(null) }
        var leadMenu by remember { mutableStateOf(false) }
        val coordinators = d?.coordinators.orEmpty()
        AlertDialog(
            onDismissRequest = { if (!busy) creating = false },
            containerColor = NightPanel,
            title = { Text("New volunteer group", color = Snow) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ErrorText(err)
                    Field(name, { name = it }, "Name", placeholder = "Marshals", enabled = !busy)
                    if (isAdmin) {
                        Box {
                            Row(
                                Modifier.fillMaxWidth().clickable(enabled = !busy) { leadMenu = true }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Led by", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                                    Text(lead?.name ?: "Pick a coordinator", style = MaterialTheme.typography.bodyMedium, color = if (lead == null) SnowFaint else Snow)
                                }
                                Text("▾", color = Gold)
                            }
                            DropdownMenu(expanded = leadMenu, onDismissRequest = { leadMenu = false }, containerColor = NightPanel) {
                                coordinators.forEach { c -> DropdownMenuItem(text = { Text(c.name, color = if (c.id == lead?.id) Gold else Snow) }, onClick = { lead = c; leadMenu = false }) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && name.trim().length >= 2 && (!isAdmin || lead != null), onClick = {
                    busy = true
                    err = null
                    scope.launch {
                        try {
                            val r = app.api.post("/api/volunteer-groups", IdResponse.serializer()) {
                                put("name", name.trim())
                                lead?.let { put("coordinatorId", it.id) }
                            }
                            creating = false
                            onMade(r.id)
                        } catch (e: Exception) {
                            err = e.message ?: "Could not make the group."
                            busy = false
                        }
                    }
                }) { Text(if (busy) "Making…" else "Make group", color = Gold) }
            },
            dismissButton = { TextButton(onClick = { creating = false }, enabled = !busy) { Text("Cancel", color = SnowFaint) } },
        )
    }
}

/**
 * A volunteer group's one page for whoever manages it (its coordinator or an admin; the chat's info page shows this
 * for them). One card — picture (tap to change), name, who leads it, how many, whether the chat is open, Open chat
 * and a ⋮ menu (rename, close or reopen, hand over, move volunteers) — then who can send, the staff in the chat, and
 * the volunteers as one list, each with a ⋮ menu for what they may do and where they go.
 */
@Composable
fun VolunteerGroupScreen(groupId: String, onOpenChat: (String) -> Unit, onGone: () -> Unit, onTitle: (String) -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val path = "/api/volunteer-groups/$groupId"
    var g by remember { mutableStateOf<VolunteerGroupDetail?>(null) }
    // The chat's own info: picture, who may send, who the staff are.
    var info by remember { mutableStateOf<com.arkhins.ctrlaps.data.GroupInfo?>(null) }
    var photoVersion by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var handing by remember { mutableStateOf(false) }
    var confirmHand by remember { mutableStateOf<NamedRef?>(null) }
    var adding by remember { mutableStateOf(false) }
    // The move popup: open with one volunteer ticked, or none ("").
    var moving by remember { mutableStateOf<String?>(null) }

    fun show(r: VolunteerGroupDetailResponse) {
        g = r.group ?: throw IllegalStateException("No longer yours to manage.")
        onTitle(r.group.name)
    }
    suspend fun loadInfo(conv: String) {
        runCatching { app.store.get("/api/groups/$conv", com.arkhins.ctrlaps.data.GroupResponse.serializer()) { if (info == null) info = it.group } }.onSuccess { info = it.group }
    }
    LaunchedEffect(g?.conversationId) { g?.conversationId?.let { loadInfo(it) } }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val conv = g?.conversationId ?: return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            try {
                val bmp = withContext(Dispatchers.IO) { loadShrunk(context, uri) } ?: throw IllegalStateException("Could not read that picture.")
                val file = withContext(Dispatchers.IO) { File(context.cacheDir, "group-photo.jpg").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) } } }
                app.api.postForm("/api/groups/$conv/photo", emptyMap(), "photo" to file, "image/jpeg", com.arkhins.ctrlaps.data.Ok.serializer())
                file.delete()
                photoVersion++
                loadInfo(conv)
            } catch (e: Exception) {
                error = e.message ?: "Could not change the picture."
            } finally {
                busy = false
            }
        }
    }
    LaunchedEffect(groupId) {
        try {
            show(app.store.get(path, VolunteerGroupDetailResponse.serializer()) { if (g == null) runCatching { show(it) } })
            error = null
        } catch (e: Exception) {
            if (g == null) error = e.message
        }
    }
    fun run(block: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try {
                block()
                runCatching { show(app.store.fetch(path, VolunteerGroupDetailResponse.serializer())) }.onFailure { onGone() }
                g?.conversationId?.let { loadInfo(it) }
            } catch (e: Exception) {
                error = e.message ?: "Could not save."
            } finally {
                busy = false
            }
        }
    }
    fun patch(vararg fields: Pair<String, Any>) = run { app.api.patch(path, VolunteerGroupDetailResponse.serializer()) { fields.forEach { (k, v) -> if (v is Boolean) put(k, v) else put(k, v.toString()) } } }

    val d = g
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (d == null) {
            item { if (error != null) ErrorText(error) else Loading() }
            return@LazyColumn
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clickable(enabled = !busy) { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Avatar(info?.photoUrl?.let { app.api.absolute("$it?v=$photoVersion") }, d.name, 56)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.name, style = MaterialTheme.typography.titleLarge, color = Snow)
                        Text(
                            listOfNotNull(
                                d.coordinator?.name ?: "No coordinator",
                                "${d.volunteers.size} volunteer${if (d.volunteers.size == 1) "" else "s"}",
                                if (d.open) null else "chat closed",
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (d.open) SnowSoft else Danger,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    GoldButton("Open chat", enabled = !busy) { onOpenChat(d.conversationId) }
                    Box {
                        IconAction(Icons.Outlined.MoreVert, "More", SnowSoft, enabled = !busy) { menu = true }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = NightPanel) {
                            DropdownMenuItem(text = { Text("Rename", color = Snow) }, onClick = { menu = false; renaming = d.name })
                            DropdownMenuItem(text = { Text(if (d.open) "Close the chat" else "Reopen the chat", color = Snow) }, onClick = { menu = false; patch("open" to !d.open) })
                            DropdownMenuItem(text = { Text("Hand over to another coordinator", color = Snow) }, onClick = { menu = false; handing = true })
                            if (d.volunteers.isNotEmpty()) DropdownMenuItem(text = { Text("Move volunteers…", color = Snow) }, onClick = { menu = false; moving = "" })
                        }
                    }
                }
            }
        }
        item { ErrorText(error) }
        info?.let { gi ->
            item {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Who can send", style = MaterialTheme.typography.titleSmall, color = Snow, modifier = Modifier.weight(1f))
                        listOf("everyone" to "Everyone", "admins" to "Staff only").forEach { (key, label) ->
                            Spacer(Modifier.width(6.dp))
                            Chip(label, Gold, filled = gi.sendPolicy == key) {
                                if (gi.sendPolicy != key && !busy) run { app.api.patch("/api/groups/${d.conversationId}", com.arkhins.ctrlaps.data.GroupResponse.serializer()) { put("sendPolicy", key) } }
                            }
                        }
                    }
                }
            }
            val staff = gi.members.filter { it.userRole != "volunteer" }
            if (staff.isNotEmpty()) {
                item { SectionTitle("STAFF · ${staff.size}") }
                item {
                    Panel(padding = PaddingValues(6.dp)) {
                        Column {
                            staff.forEachIndexed { i, m ->
                                if (i > 0) HorizontalDivider(color = SnowFaint.copy(alpha = 0.15f))
                                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(app.api.absolute(m.photoUrl), m.name, 40)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(m.name, style = MaterialTheme.typography.titleSmall, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(if (m.id == d.coordinator?.id) "Lead coordinator" else m.roleLabel, style = MaterialTheme.typography.bodySmall, color = if (m.id == d.coordinator?.id) Gold else SnowFaint)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("VOLUNTEERS · ${d.volunteers.size}", Modifier.weight(1f))
                if (d.unassigned.isNotEmpty()) IconAction(Icons.Outlined.Add, "Add a volunteer", Gold, enabled = !busy) { adding = true }
            }
        }
        item {
            Panel(padding = PaddingValues(6.dp)) {
                Column {
                    if (d.volunteers.isEmpty()) Text("No volunteers in this group yet.", style = MaterialTheme.typography.bodySmall, color = SnowFaint, modifier = Modifier.padding(10.dp))
                    d.volunteers.forEachIndexed { i, v ->
                        if (i > 0) HorizontalDivider(color = SnowFaint.copy(alpha = 0.15f))
                        var rowMenu by remember(v.id) { mutableStateOf(false) }
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(app.api.absolute(v.photoUrl), v.name, 40)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(v.name, style = MaterialTheme.typography.titleSmall, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val limit = permissionLabel(v.permission)
                                Text(
                                    listOfNotNull(if (v.status != "active") v.status.replaceFirstChar { it.uppercase() } else null, limit).joinToString(" · ").ifEmpty { "Can chat" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (limit != null) Danger else SnowFaint,
                                )
                            }
                            Box {
                                IconAction(Icons.Outlined.MoreVert, "More for ${v.name}", SnowSoft, enabled = !busy) { rowMenu = true }
                                DropdownMenu(expanded = rowMenu, onDismissRequest = { rowMenu = false }, containerColor = NightPanel) {
                                    PERMISSIONS.forEach { (key, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label, color = if (v.permission == key) Gold else Snow) },
                                            onClick = {
                                                rowMenu = false
                                                if (v.permission != key) run { app.api.patch("/api/groups/${d.conversationId}/members/${v.id}", GroupResponse.serializer()) { put("permission", key) } }
                                            },
                                        )
                                    }
                                    HorizontalDivider(color = SnowFaint.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 4.dp))
                                    DropdownMenuItem(text = { Text("Move to another group…", color = Snow) }, onClick = { rowMenu = false; moving = v.id })
                                    DropdownMenuItem(
                                        text = { Text("Remove from the group", color = Danger) },
                                        onClick = { rowMenu = false; run { app.api.delete("/api/volunteer-groups/$groupId/volunteers?userId=${v.id}") } },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    renaming?.let { current ->
        var value by remember(current) { mutableStateOf(current) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = NightPanel,
            title = { Text("Group name", color = Snow) },
            text = { Field(value, { value = it }, "Name") },
            confirmButton = { TextButton(enabled = value.trim().length >= 2, onClick = { renaming = null; patch("name" to value.trim()) }) { Text("Save", color = Gold) } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel", color = SnowFaint) } },
        )
    }
    if (handing && d != null) {
        AlertDialog(
            onDismissRequest = { handing = false },
            containerColor = NightPanel,
            title = { Text("Hand over to", color = Snow) },
            text = {
                Column {
                    Text("The group's volunteers and chat go with it.", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
                    Spacer(Modifier.height(8.dp))
                    d.coordinators.filter { it.id != d.coordinator?.id }.forEach { c ->
                        Text(
                            c.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Snow,
                            modifier = Modifier.fillMaxWidth().clickable { handing = false; confirmHand = c }.padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { handing = false }) { Text("Cancel", color = SnowFaint) } },
        )
    }
    confirmHand?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmHand = null },
            containerColor = NightPanel,
            title = { Text("Hand the group to ${c.name}?", color = Snow) },
            text = { Text("You will no longer see this group or its chat.", color = SnowSoft) },
            confirmButton = { TextButton(onClick = { confirmHand = null; patch("coordinatorId" to c.id) }) { Text("Hand over", color = Gold) } },
            dismissButton = { TextButton(onClick = { confirmHand = null }) { Text("Cancel", color = SnowFaint) } },
        )
    }
    moving?.let { pick ->
        if (d != null) MoveVolunteersSheet(d, preselect = pick.takeIf { it.isNotEmpty() }, onDismiss = { moving = null }) { ids, toId ->
            moving = null
            run {
                app.api.post("/api/volunteer-groups/$groupId/volunteers/move", VolunteerGroupDetailResponse.serializer()) {
                    putJsonArray("userIds") { ids.forEach { add(it) } }
                    if (toId != null) put("toGroupId", toId)
                }
            }
        }
    }
    if (adding && d != null) {
        AlertDialog(
            onDismissRequest = { adding = false },
            containerColor = NightPanel,
            title = { Text("Add a volunteer", color = Snow) },
            text = {
                Column {
                    Text("Volunteers in no group yet.", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
                    Spacer(Modifier.height(8.dp))
                    d.unassigned.forEach { u ->
                        Text(
                            u.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Snow,
                            modifier = Modifier.fillMaxWidth().clickable {
                                adding = false
                                run { app.api.post("/api/volunteer-groups/$groupId/volunteers", VolunteerGroupDetailResponse.serializer()) { put("userId", u.id) } }
                            }.padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel", color = SnowFaint) } },
        )
    }
}

/**
 * The move popup: the group's volunteers with their pictures and a checkbox each (search, All / None), then the group
 * they go to (search by name or coordinator; "No group" last) and one confirm. Opened from the group's ⋮ or from a
 * volunteer's ⋮ with that one already ticked.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MoveVolunteersSheet(d: VolunteerGroupDetail, preselect: String?, onDismiss: () -> Unit, onMove: (List<String>, String?) -> Unit) {
    val app = LocalApp.current
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(setOfNotNull(preselect)) }
    var choosing by remember { mutableStateOf(false) }
    var groupQuery by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<Pair<String?, String>?>(null) }
    val shown = d.volunteers.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    val allShown = shown.isNotEmpty() && shown.all { it.id in picked }
    val checkColors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = Gold, checkmarkColor = com.arkhins.ctrlaps.ui.theme.OnGold, uncheckedColor = SnowFaint)

    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = NightPanel) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            if (!choosing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Move volunteers", style = MaterialTheme.typography.titleMedium, color = Snow, modifier = Modifier.weight(1f))
                    Text(
                        if (allShown) "None" else "All",
                        style = MaterialTheme.typography.labelLarge,
                        color = Gold,
                        modifier = Modifier.clickable(enabled = shown.isNotEmpty()) { picked = if (allShown) picked - shown.map { it.id }.toSet() else picked + shown.map { it.id } }.padding(8.dp),
                    )
                }
                Field(query, { query = it }, "Search volunteers")
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    if (shown.isEmpty()) item { Text(if (query.isBlank()) "No volunteers in this group." else "Nobody matches.", style = MaterialTheme.typography.bodySmall, color = SnowFaint, modifier = Modifier.padding(vertical = 10.dp)) }
                    items(shown, key = { it.id }) { v ->
                        val on = v.id in picked
                        Row(
                            Modifier.fillMaxWidth().clickable { picked = if (on) picked - v.id else picked + v.id }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.Checkbox(checked = on, onCheckedChange = null, colors = checkColors)
                            Spacer(Modifier.width(8.dp))
                            Avatar(app.api.absolute(v.photoUrl), v.name, 40)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(v.name, style = MaterialTheme.typography.titleSmall, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(permissionLabel(v.permission) ?: "Can chat", style = MaterialTheme.typography.bodySmall, color = if (permissionLabel(v.permission) != null) Danger else SnowFaint)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (picked.isEmpty()) "Tick who to move" else "${picked.size} selected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (picked.isEmpty()) SnowFaint else Snow,
                        modifier = Modifier.weight(1f),
                    )
                    GoldButton("Choose group", enabled = picked.isNotEmpty()) { choosing = true }
                }
            } else {
                val groups = d.otherGroups.filter { groupQuery.isBlank() || it.name.contains(groupQuery.trim(), true) || (it.coordinator ?: "").contains(groupQuery.trim(), true) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", style = MaterialTheme.typography.titleLarge, color = Gold, modifier = Modifier.clickable { choosing = false }.padding(end = 10.dp))
                    Text("Move ${picked.size} to", style = MaterialTheme.typography.titleMedium, color = Snow)
                }
                Spacer(Modifier.height(8.dp))
                Field(groupQuery, { groupQuery = it }, "Search groups", placeholder = "Group or coordinator")
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(groups, key = { it.id }) { o ->
                        Row(Modifier.fillMaxWidth().clickable { confirm = o.id to o.name }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(40.dp).height(40.dp).background(Gold.copy(alpha = 0.15f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Text("🦺") }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(o.name, style = MaterialTheme.typography.titleSmall, color = Snow)
                                Text(o.coordinator ?: "No coordinator", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
                            }
                        }
                    }
                    if (groups.isEmpty()) item { Text("No group matches.", style = MaterialTheme.typography.bodySmall, color = SnowFaint, modifier = Modifier.padding(vertical = 10.dp)) }
                    item {
                        HorizontalDivider(color = SnowFaint.copy(alpha = 0.15f))
                        Row(Modifier.fillMaxWidth().clickable { confirm = null to "no group" }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("No group", style = MaterialTheme.typography.titleSmall, color = Danger, modifier = Modifier.weight(1f))
                            Text("Taken out, in none", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
                        }
                    }
                }
            }
        }
    }
    confirm?.let { (toId, toName) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = NightPanel,
            title = { Text("Move ${picked.size} volunteer${if (picked.size == 1) "" else "s"} to $toName?", color = Snow) },
            text = { Text(if (toId != null) "Their chat moves with them, and they are told." else "They leave this group's chat and belong to no group.", color = SnowSoft) },
            confirmButton = { TextButton(onClick = { confirm = null; onMove(picked.toList(), toId) }) { Text("Move", color = Gold) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel", color = SnowFaint) } },
        )
    }
}

