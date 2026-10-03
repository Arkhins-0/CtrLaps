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
import kotlinx.serialization.json.put

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
                            Text("Manage", style = MaterialTheme.typography.labelSmall, color = Gold, modifier = Modifier.clickable { onManage(g.id) }.padding(2.dp))
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

/**
 * Manage one volunteer group (its coordinator or an admin). One card for the group — name, who leads it, how many,
 * whether the chat is open, with Open chat and a ⋮ menu (rename, close or reopen, hand over) — then the volunteers as
 * one list, each with a ⋮ menu for what they may do and where they go.
 */
@Composable
fun VolunteerGroupScreen(groupId: String, onOpenChat: (String) -> Unit, onGone: () -> Unit, onTitle: (String) -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val path = "/api/volunteer-groups/$groupId"
    var g by remember { mutableStateOf<VolunteerGroupDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var handing by remember { mutableStateOf(false) }
    var confirmHand by remember { mutableStateOf<NamedRef?>(null) }
    var adding by remember { mutableStateOf(false) }

    fun show(r: VolunteerGroupDetailResponse) {
        g = r.group ?: throw IllegalStateException("No longer yours to manage.")
        onTitle(r.group.name)
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
                    Column(Modifier.weight(1f)) {
                        Text(d.name, style = MaterialTheme.typography.titleLarge, color = Snow)
                        Text(
                            listOf(
                                d.coordinator?.let { "Led by ${it.name}" } ?: "No coordinator",
                                "${d.volunteers.size} volunteer${if (d.volunteers.size == 1) "" else "s"}",
                                if (d.open) "chat open" else "chat closed",
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (d.open) SnowSoft else Danger,
                        )
                    }
                    GoldButton("Open chat", enabled = !busy) { onOpenChat(d.conversationId) }
                    Box {
                        IconAction(Icons.Outlined.MoreVert, "More", SnowSoft, enabled = !busy) { menu = true }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = NightPanel) {
                            DropdownMenuItem(text = { Text("Rename", color = Snow) }, onClick = { menu = false; renaming = d.name })
                            DropdownMenuItem(text = { Text(if (d.open) "Close the chat" else "Reopen the chat", color = Snow) }, onClick = { menu = false; patch("open" to !d.open) })
                            DropdownMenuItem(text = { Text("Hand over to another coordinator", color = Snow) }, onClick = { menu = false; handing = true })
                        }
                    }
                }
            }
        }
        item { ErrorText(error) }
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
                                    d.otherGroups.forEach { o ->
                                        DropdownMenuItem(
                                            text = { Text("Move to ${o.name}", color = Snow) },
                                            onClick = { rowMenu = false; run { app.api.post("/api/volunteer-groups/${o.id}/volunteers", VolunteerGroupDetailResponse.serializer()) { put("userId", v.id) } } },
                                        )
                                    }
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
