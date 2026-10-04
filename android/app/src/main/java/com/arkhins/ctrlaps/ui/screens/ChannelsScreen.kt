package com.arkhins.ctrlaps.ui.screens

import com.arkhins.ctrlaps.data.CategoryChannel
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.arkhins.ctrlaps.R
import com.arkhins.ctrlaps.ui.components.IconAction
import com.arkhins.ctrlaps.ui.theme.OnGold
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.LocalApp
import com.arkhins.ctrlaps.data.ChannelWeekend
import com.arkhins.ctrlaps.data.ChannelsResponse
import com.arkhins.ctrlaps.data.ManagersResponse
import com.arkhins.ctrlaps.data.GroupMember
import com.arkhins.ctrlaps.data.PublicUser
import com.arkhins.ctrlaps.data.UsersResponse
import com.arkhins.ctrlaps.ui.AppViewModel
import com.arkhins.ctrlaps.ui.components.Chip
import com.arkhins.ctrlaps.ui.components.Divider
import com.arkhins.ctrlaps.ui.components.Empty
import com.arkhins.ctrlaps.ui.components.ErrorText
import com.arkhins.ctrlaps.ui.components.Field
import com.arkhins.ctrlaps.ui.components.GoldButton
import com.arkhins.ctrlaps.ui.components.Loading
import com.arkhins.ctrlaps.ui.components.MutedMark
import com.arkhins.ctrlaps.ui.components.UnreadBadge
import com.arkhins.ctrlaps.ui.components.SectionTitle
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.Night
import com.arkhins.ctrlaps.ui.theme.NightPanel
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import com.arkhins.ctrlaps.ui.theme.SnowSoft
import com.arkhins.ctrlaps.ui.whenLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.add
import kotlinx.serialization.json.putJsonArray

/**
 * The broadcast channels: one per race weekend, listed season by season
 * with the current season on top. Tapping a weekend opens its channel.
 * Above them, every race category's channel. Closing a channel and picking
 * its managers happen inside the channel, from its ⋮ menu ([ChannelMenu]).
 */
@Composable
fun ChannelsScreen(vm: AppViewModel, onOpenWeekend: (String) -> Unit, onOpenCategory: (String) -> Unit = {}) {
    val app = LocalApp.current
    var data by remember { mutableStateOf<ChannelsResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(vm.refreshTick) {
        try {
            data = app.store.get("/api/channels", ChannelsResponse.serializer()) { if (data == null) data = it }
            error = null
        } catch (e: Exception) {
            if (data == null) error = e.message
        }
    }

    val seasons = data?.seasons
    val categories = data?.categories.orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)) {
        when {
            error != null && seasons == null -> item { Box(Modifier.padding(16.dp)) { ErrorText(error) } }
            seasons == null -> item { Loading() }
            seasons.all { it.weekends.isEmpty() } && categories.isEmpty() -> item { Box(Modifier.padding(16.dp)) { Empty("No race weekends yet.") } }
            else -> {
              if (categories.isNotEmpty()) {
                item(key = "categories") { SectionTitle("CATEGORIES", Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                items(categories, key = { "c-" + it.id }) { c -> CategoryChannelRow(c) { onOpenCategory(c.id) } }
              }
              seasons.filter { it.weekends.isNotEmpty() }.forEach { season ->
                item(key = "season-${season.id}") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle(season.name.uppercase(), Modifier.weight(1f))
                        if (season.current) Chip("Current", Gold) else if (season.status == "archived") Chip("Archived", SnowFaint)
                    }
                }
                items(season.weekends, key = { it.id }) { w ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenWeekend(w.id) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.width(48.dp).height(48.dp).background(if (w.channelOpen) Gold.copy(alpha = 0.15f) else NightPanel, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Text("📣", style = MaterialTheme.typography.titleMedium) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(w.name, style = MaterialTheme.typography.titleMedium, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                if (w.muted) MutedMark()
                            }
                            Text(
                                w.lastMessage ?: "${w.startsOn} → ${w.endsOn}" + if (w.channelOpen) "" else " · closed",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (w.unread > 0) Snow else SnowFaint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (w.managers.isNotEmpty()) {
                                Text(
                                    "Managed by " + w.managers.joinToString { it.name },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SnowFaint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            w.lastMessageAt?.let { Text(whenLabel(it), style = MaterialTheme.typography.labelSmall, color = if (w.unread > 0 && !w.muted) Gold else SnowFaint) }
                            if (w.unread > 0) {
                                Spacer(Modifier.height(4.dp))
                                UnreadBadge(w.unread, w.muted)
                            }
                        }
                    }
                    Box(Modifier.padding(start = 76.dp)) { Divider() }
                }
              }
            }
        }
    }

}

/**
 * Admin: the ⋮ inside a channel (a weekend's or a category's) — close or reopen it, and pick the coordinators who
 * manage it. [locked]: its season is archived, so it can't be reopened from here.
 */
@Composable
internal fun ChannelMenu(name: String, open: Boolean, locked: Boolean, managersUrl: String, onToggle: () -> Unit) {
    val app = LocalApp.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    Box {
        IconAction(Icons.Outlined.MoreVert, "Channel options", SnowSoft) { menu = true }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = NightPanel) {
            if (open || !locked) {
                DropdownMenuItem(
                    text = { Text(if (open) "Close channel" else "Reopen channel", color = if (open) SnowSoft else Gold) },
                    leadingIcon = { Icon(painterResource(if (open) R.drawable.ic_archive else R.drawable.ic_unarchive), contentDescription = null, tint = if (open) SnowSoft else Gold) },
                    onClick = { menu = false; onToggle() },
                )
            }
            DropdownMenuItem(
                text = { Text("Managers", color = SnowSoft) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_tab_people), contentDescription = null, tint = SnowSoft, modifier = Modifier.size(24.dp)) },
                onClick = { menu = false; managing = true },
            )
        }
    }
    if (managing) {
        ManagersSheet(name, managersUrl, onDismiss = { managing = false }) { ids ->
            managing = false
            app.appScope.launch {
                val saved = runCatching { app.api.put(managersUrl, ManagersResponse.serializer()) { putJsonArray("userIds") { ids.forEach { add(it) } } } }
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, if (saved.isSuccess) "Managers saved" else "Couldn't save the managers. Try again.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

/** A race category's channel: its code in its colour, the last post, unread. */
@Composable
private fun CategoryChannelRow(c: CategoryChannel, onOpen: () -> Unit) {
    val color = runCatching { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(c.color)) }.getOrDefault(SnowSoft)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(48.dp).height(48.dp).background(color.copy(alpha = 0.15f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Text(c.code.take(5), style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, style = MaterialTheme.typography.titleMedium, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (c.muted) MutedMark()
            }
            Text(c.lastMessage ?: "No posts yet", style = MaterialTheme.typography.bodySmall, color = if (c.unread > 0) Snow else SnowFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            c.lastMessageAt?.let { Text(whenLabel(it), style = MaterialTheme.typography.labelSmall, color = if (c.unread > 0 && !c.muted) Gold else SnowFaint) }
            if (c.unread > 0) {
                Spacer(Modifier.height(4.dp))
                UnreadBadge(c.unread, c.muted)
            }
        }
    }
    Box(Modifier.padding(start = 76.dp)) { Divider() }
}

/** Admin: tick the coordinators who manage a weekend's or a category's channel (admins post everywhere already). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManagersSheet(name: String, url: String, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    val app = LocalApp.current
    var people by remember { mutableStateOf<List<PublicUser>?>(null) }
    var picked by remember { mutableStateOf(emptySet<String>()) }
    var filter by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    // The server gives the current managers and who may be picked (active coordinators), both as short cards.
    LaunchedEffect(url) {
        runCatching { app.api.get(url, ManagersResponse.serializer()) }
            .onSuccess { r ->
                // Someone already managing but no longer a coordinator stays tickable, so saving does not drop them unseen.
                val extra = r.managers.filter { m -> r.candidates.none { it.id == m.id } }
                picked = r.managers.map { it.id }.toSet()
                people = (extra + r.candidates).map { it.asPickRow() }
            }
            .onFailure { error = it.message ?: "Could not load people." }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = NightPanel) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Managers of $name", style = MaterialTheme.typography.titleMedium, color = Snow)
            Text("Coordinators who post in this channel. Admins post in every channel.", style = MaterialTheme.typography.bodySmall, color = SnowSoft)
            Spacer(Modifier.height(8.dp))
            Field(filter, { filter = it }, "Search people")
            Spacer(Modifier.height(6.dp))
            val p = people
            Box(Modifier.weight(1f, fill = false)) {
                when {
                    error != null -> ErrorText(error)
                    p == null -> Loading()
                    p.isEmpty() -> Empty("There are no coordinators to pick.")
                    else -> PeoplePicker(p, picked, filter, avatar = 40) { picked = it }
                }
            }
            Spacer(Modifier.height(12.dp))
            GoldButton("Save", Modifier.fillMaxWidth(), enabled = p != null) { onSave(picked.toList()) }
        }
    }
}

/** A manager's short card as a picker row: the picker shows only the name, role and photo. */
private fun GroupMember.asPickRow() = PublicUser(
    id = id, email = "", role = userRole, roleLabel = roleLabel, status = "active", statusLabel = "", name = name, photoUrl = photoUrl, verifyCode = "",
)
