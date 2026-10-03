package com.arkhins.ctrlaps.ui.screens

import com.arkhins.ctrlaps.data.CategoryChannel
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
 * An admin names the people who manage a channel from here. Above them,
 * the channels of the race categories this person is in.
 */
@Composable
fun ChannelsScreen(vm: AppViewModel, onOpenWeekend: (String) -> Unit, onOpenCategory: (String) -> Unit = {}) {
    val app = LocalApp.current
    var data by remember { mutableStateOf<ChannelsResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    // Whose managers are being picked: (name, managers API).
    var managing by remember { mutableStateOf<Pair<String, String>?>(null) }
    // Admins pick the coordinators who manage a weekend's or a category's channel.
    val canManage = vm.me?.isAdmin == true

    LaunchedEffect(reload, vm.refreshTick) {
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
                items(categories, key = { "c-" + it.id }) { c -> CategoryChannelRow(c, onManagers = if (canManage) ({ managing = c.name to "/api/categories/${c.id}/managers" }) else null) { onOpenCategory(c.id) } }
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
                            if (canManage) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Managers",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Gold,
                                    modifier = Modifier.clickable { managing = w.name to "/api/weekends/${w.id}/managers" }.padding(2.dp),
                                )
                            }
                        }
                    }
                    Box(Modifier.padding(start = 76.dp)) { Divider() }
                }
              }
            }
        }
    }

    managing?.let { (name, url) ->
        ManagersSheet(name, url, onDismiss = { managing = null }) { ids ->
            managing = null
            app.appScope.launch {
                runCatching { app.api.put(url, ManagersResponse.serializer()) { putJsonArray("userIds") { ids.forEach { add(it) } } } }
                withContext(Dispatchers.Main) { reload++ }
            }
        }
    }
}

/** A race category's channel: its code in its colour, the last post, unread. */
@Composable
private fun CategoryChannelRow(c: CategoryChannel, onManagers: (() -> Unit)? = null, onOpen: () -> Unit) {
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
            if (onManagers != null) {
                Spacer(Modifier.height(4.dp))
                Text("Managers", style = MaterialTheme.typography.labelSmall, color = Gold, modifier = Modifier.clickable(onClick = onManagers).padding(2.dp))
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
    // The server gives the current managers and who may be picked (active coordinators).
    LaunchedEffect(url) {
        runCatching { app.api.get(url, ManagersResponse.serializer()) }
            .onSuccess { r ->
                picked = r.managers.map { it.id }.toSet()
                people = r.candidates
            }
            .onFailure { people = emptyList() }
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
                if (p == null) Loading() else PeoplePicker(p, picked, filter, avatar = 40) { picked = it }
            }
            Spacer(Modifier.height(12.dp))
            GoldButton("Save", Modifier.fillMaxWidth()) { onSave(picked.toList()) }
        }
    }
}
