package com.arkhins.ctrlaps.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.LocalApp
import com.arkhins.ctrlaps.data.IdResponse
import com.arkhins.ctrlaps.data.VolunteerGroupDetail
import com.arkhins.ctrlaps.data.VolunteerGroupDetailResponse
import com.arkhins.ctrlaps.data.VolunteerGroupsResponse
import com.arkhins.ctrlaps.ui.AppViewModel
import com.arkhins.ctrlaps.ui.components.Avatar
import com.arkhins.ctrlaps.ui.components.Chip
import com.arkhins.ctrlaps.ui.components.Empty
import com.arkhins.ctrlaps.ui.components.ErrorText
import com.arkhins.ctrlaps.ui.components.Field
import com.arkhins.ctrlaps.ui.components.GhostButton
import com.arkhins.ctrlaps.ui.components.GoldButton
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
import kotlinx.serialization.json.put

/**
 * The Chats tab's Volunteers page: each volunteer group's chat this person sees (admins all, a coordinator the groups
 * they lead, a volunteer their own), and for admins and coordinators a new group and Manage.
 */
@Composable
fun VolunteersScreen(vm: AppViewModel, onOpenChat: (String) -> Unit, onManage: (String) -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<VolunteerGroupsResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val isAdmin = vm.me?.isAdmin == true

    LaunchedEffect(vm.refreshTick, vm.chatTick) {
        try {
            data = app.store.get("/api/volunteer-groups", VolunteerGroupsResponse.serializer()) { if (data == null) data = it }
            error = null
        } catch (e: Exception) {
            if (data == null) error = e.message
        }
    }

    val d = data
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("VOLUNTEER GROUPS", Modifier.weight(1f))
                if (d?.canCreate == true) GoldButton("New group") { creating = true }
            }
        }
        when {
            error != null && d == null -> item { Box(Modifier.padding(16.dp)) { ErrorText(error) } }
            d == null -> item { Loading() }
            d.groups.isEmpty() -> item { Box(Modifier.padding(16.dp)) { Empty(if (d.canCreate) "No volunteer groups yet. Make the first one." else "You're not in a volunteer group yet.") } }
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
                            g.lastMessage ?: "${g.volunteers} volunteer${if (g.volunteers == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (g.unread > 0) Snow else SnowFaint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            (g.coordinator?.let { "Led by ${it.name}" } ?: "No coordinator") + " · ${g.volunteers} volunteer${if (g.volunteers == 1) "" else "s"}" + if (g.open) "" else " · chat closed",
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
                            Text("Manage", style = MaterialTheme.typography.labelSmall, color = Gold, modifier = Modifier.clickable { onManage(g.id) }.padding(2.dp))
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        var lead by remember { mutableStateOf<String?>(null) }
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
                    Field(name, { name = it }, "Name", placeholder = "Marshals")
                    if (isAdmin) {
                        Box {
                            GhostButton(coordinators.firstOrNull { it.id == lead }?.name ?: "Led by…", Modifier.fillMaxWidth()) { leadMenu = true }
                            DropdownMenu(expanded = leadMenu, onDismissRequest = { leadMenu = false }, containerColor = NightPanel) {
                                coordinators.forEach { c -> DropdownMenuItem(text = { Text(c.name, color = if (c.id == lead) Gold else Snow) }, onClick = { lead = c.id; leadMenu = false }) }
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
                                lead?.let { put("coordinatorId", it) }
                            }
                            creating = false
                            onManage(r.id)
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

private val PERMISSIONS = listOf("full" to "Can chat", "no_messages" to "Can't send", "read_only" to "Read only")

/** Manage one volunteer group (its coordinator or an admin): name, coordinator, open or closed, and its volunteers. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VolunteerGroupScreen(groupId: String, onOpenChat: (String) -> Unit, onGone: () -> Unit, onTitle: (String) -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var g by remember { mutableStateOf<VolunteerGroupDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var handing by remember { mutableStateOf<Pair<String, String>?>(null) }

    suspend fun reload() {
        g = app.api.get("/api/volunteer-groups/$groupId", VolunteerGroupDetailResponse.serializer()).group ?: throw IllegalStateException("No longer yours to manage.")
        g?.let { onTitle(it.name) }
    }
    fun run(done: String? = null, block: suspend () -> Unit) {
        busy = true
        error = null
        note = null
        scope.launch {
            try {
                block()
                runCatching { reload() }.onFailure { onGone() }
                note = done
            } catch (e: Exception) {
                error = e.message ?: "Could not save."
            } finally {
                busy = false
            }
        }
    }
    LaunchedEffect(groupId) { runCatching { reload() }.onFailure { error = it.message } }

    val d = g
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (d == null) {
            item { if (error != null) ErrorText(error) else Loading() }
            return@LazyColumn
        }
        item {
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.name, style = MaterialTheme.typography.titleLarge, color = Snow)
                            Text(d.coordinator?.let { "Led by ${it.name}" } ?: "No coordinator", style = MaterialTheme.typography.bodySmall, color = SnowSoft)
                        }
                        GhostButton("Rename", enabled = !busy) { renaming = d.name }
                    }
                    Text(
                        if (d.open) "The chat is open." else "The chat is closed: everyone can read it, nobody can send.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SnowFaint,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GoldButton("Open chat", enabled = !busy) { onOpenChat(d.conversationId) }
                        GhostButton(if (d.open) "Close chat" else "Reopen chat", enabled = !busy) {
                            run(if (d.open) "Chat closed." else "Chat opened.") { app.api.patch("/api/volunteer-groups/$groupId", VolunteerGroupDetailResponse.serializer()) { put("open", !d.open) } }
                        }
                    }
                }
            }
        }
        item { ErrorText(error) }
        if (note != null) item { Text(note!!, style = MaterialTheme.typography.bodySmall, color = Gold) }
        item {
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionTitle("HAND OVER")
                    Text("Give the group to another coordinator: its volunteers and chat go with it.", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        d.coordinators.forEach { c ->
                            Chip(c.name, Gold, filled = c.id == d.coordinator?.id) { if (c.id != d.coordinator?.id && !busy) handing = c.id to c.name }
                        }
                    }
                }
            }
        }
        item { SectionTitle("VOLUNTEERS · ${d.volunteers.size}") }
        if (d.volunteers.isEmpty()) item { Text("No volunteers in this group yet.", style = MaterialTheme.typography.bodySmall, color = SnowFaint) }
        items(d.volunteers, key = { it.id }) { v ->
            var moveMenu by remember { mutableStateOf(false) }
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(app.api.absolute(v.photoUrl), v.name, 36)
                        Spacer(Modifier.width(10.dp))
                        Text(v.name + if (v.status != "active") " · ${v.status}" else "", style = MaterialTheme.typography.titleSmall, color = Snow, modifier = Modifier.weight(1f))
                        Box {
                            Text("Move", style = MaterialTheme.typography.labelMedium, color = Gold, modifier = Modifier.clickable(enabled = !busy) { moveMenu = true }.padding(6.dp))
                            DropdownMenu(expanded = moveMenu, onDismissRequest = { moveMenu = false }, containerColor = NightPanel) {
                                d.otherGroups.forEach { o ->
                                    DropdownMenuItem(
                                        text = { Text(o.name + (o.coordinator?.let { " ($it)" } ?: ""), color = Snow) },
                                        onClick = {
                                            moveMenu = false
                                            run("Moved.") { app.api.post("/api/volunteer-groups/${o.id}/volunteers", VolunteerGroupDetailResponse.serializer()) { put("userId", v.id) } }
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("No group", color = Danger) },
                                    onClick = {
                                        moveMenu = false
                                        run("Taken out of the group.") { app.api.delete("/api/volunteer-groups/$groupId/volunteers?userId=${v.id}") }
                                    },
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PERMISSIONS.forEach { (key, label) ->
                            Chip(label, if (key == "full") Gold else Danger, filled = v.permission == key) {
                                if (v.permission != key && !busy) run { app.api.patch("/api/groups/${d.conversationId}/members/${v.id}", com.arkhins.ctrlaps.data.GroupResponse.serializer()) { put("permission", key) } }
                            }
                        }
                    }
                }
            }
        }
        if (d.unassigned.isNotEmpty()) {
            item { SectionTitle("IN NO GROUP · ${d.unassigned.size}") }
            items(d.unassigned, key = { "u-" + it.id }) { u ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(u.name, style = MaterialTheme.typography.bodyMedium, color = SnowSoft, modifier = Modifier.weight(1f))
                    Text("Add", style = MaterialTheme.typography.labelMedium, color = Gold, modifier = Modifier.clickable(enabled = !busy) {
                        run("Added.") { app.api.post("/api/volunteer-groups/$groupId/volunteers", VolunteerGroupDetailResponse.serializer()) { put("userId", u.id) } }
                    }.padding(8.dp))
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
            confirmButton = {
                TextButton(onClick = {
                    renaming = null
                    run("Renamed.") { app.api.patch("/api/volunteer-groups/$groupId", VolunteerGroupDetailResponse.serializer()) { put("name", value.trim()) } }
                }) { Text("Save", color = Gold) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel", color = SnowFaint) } },
        )
    }
    handing?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { handing = null },
            containerColor = NightPanel,
            title = { Text("Hand the group to $name?", color = Snow) },
            text = { Text("Its volunteers and its chat go with it.", color = SnowSoft) },
            confirmButton = {
                TextButton(onClick = {
                    handing = null
                    run("Handed over.") { app.api.patch("/api/volunteer-groups/$groupId", VolunteerGroupDetailResponse.serializer()) { put("coordinatorId", id) } }
                }) { Text("Hand over", color = Gold) }
            },
            dismissButton = { TextButton(onClick = { handing = null }) { Text("Cancel", color = SnowFaint) } },
        )
    }
}
