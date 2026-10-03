package com.arkhins.ctrlaps.ui.screens

import com.arkhins.ctrlaps.ui.theme.OnGold
import com.arkhins.ctrlaps.data.UpcomingEvent
import com.arkhins.ctrlaps.data.UpcomingEventsResponse
import com.arkhins.ctrlaps.data.EventReminders
import com.arkhins.ctrlaps.ui.components.UpcomingEventsCard
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import com.arkhins.ctrlaps.CtrlapsApplication
import com.arkhins.ctrlaps.data.ChannelResponse
import com.arkhins.ctrlaps.data.Conversation
import com.arkhins.ctrlaps.data.ConversationsResponse
import com.arkhins.ctrlaps.data.Message
import com.arkhins.ctrlaps.data.attachments
import com.arkhins.ctrlaps.ui.components.filesLabel
import com.arkhins.ctrlaps.data.MessagesResponse
import com.arkhins.ctrlaps.data.NextRace
import com.arkhins.ctrlaps.data.Ok
import com.arkhins.ctrlaps.data.Weekend
import com.arkhins.ctrlaps.data.WeekendsResponse
import com.arkhins.ctrlaps.ui.AppViewModel
import com.arkhins.ctrlaps.ui.components.Avatar
import com.arkhins.ctrlaps.ui.components.PreviewLine
import com.arkhins.ctrlaps.ui.components.Divider
import com.arkhins.ctrlaps.ui.components.Empty
import com.arkhins.ctrlaps.ui.components.ErrorText
import com.arkhins.ctrlaps.ui.components.FileView
import com.arkhins.ctrlaps.ui.components.GoldButton
import com.arkhins.ctrlaps.ui.components.Loading
import com.arkhins.ctrlaps.ui.components.MessageCard
import com.arkhins.ctrlaps.ui.components.photoRuns
import com.arkhins.ctrlaps.ui.components.Panel
import com.arkhins.ctrlaps.ui.components.DashboardCard
import com.arkhins.ctrlaps.ui.components.DayHeader
import com.arkhins.ctrlaps.ui.components.FeedRow
import com.arkhins.ctrlaps.ui.components.NewLine
import com.arkhins.ctrlaps.ui.components.feedRows
import com.arkhins.ctrlaps.ui.components.lastNewRun
import com.arkhins.ctrlaps.ui.components.rememberNewIds
import com.arkhins.ctrlaps.ui.localDateTime
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.Night
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import com.arkhins.ctrlaps.ui.theme.SnowSoft
import com.arkhins.ctrlaps.ui.trackDateTime
import com.arkhins.ctrlaps.ui.whenLabel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.add
import kotlinx.serialization.json.putJsonArray
import java.time.LocalDate

/** A weekend that is still on, with its latest channel post. */
@Serializable
private data class ChannelSummary(val weekend: Weekend, val latest: Message? = null)

/** Home as last seen, kept on the phone so it shows at once. */
@Serializable
private data class HomeSnapshot(
    val next: NextRace? = null,
    val chats: List<Conversation> = emptyList(),
    val channels: List<ChannelSummary> = emptyList(),
    val messages: List<Message> = emptyList(),
    val events: List<UpcomingEvent> = emptyList(),
)

private const val HOME_KEY = "snapshot:home"

/**
 * Home rebuilt in the background from what [com.arkhins.ctrlaps.data.Prefetch]
 * has just kept, so Home opens current even with no signal. Marks nothing read.
 */
suspend fun refreshHomeSnapshot(app: CtrlapsApplication) {
    val next = app.store.read("/api/next-race", NextRace.serializer())
    val chats = app.chatCache.loadList().orEmpty().sortedByDescending { it.lastMessageAt ?: "" }.take(3)
    val today = LocalDate.now().toString()
    val weekends = app.store.read("/api/weekends", WeekendsResponse.serializer())?.weekends.orEmpty()
    val channels = weekends.filter { it.endsOn >= today }.sortedBy { it.startsOn }.map { w ->
        ChannelSummary(w, app.store.read("/api/weekends/${w.id}/channel", ChannelResponse.serializer())?.messages?.lastOrNull())
    }
    val events = app.store.read("/api/events/upcoming", UpcomingEventsResponse.serializer())?.events.orEmpty()
    val messages = app.store.read("/api/messages", MessagesResponse.serializer())?.messages ?: return
    app.store.put(HOME_KEY, HomeSnapshot(next, chats, channels, messages.filter { it.kind == "broadcast" }, events), HomeSnapshot.serializer())
}

/**
 * Home: the next race, the last three private chats, the latest post of
 * each race weekend still on, then the announcements sent to this person.
 */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun HomeScreen(
    vm: AppViewModel,
    highlight: String?,
    onOpenWeekend: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onAllChats: () -> Unit,
    onCompose: () -> Unit,
    onView: (FileView) -> Unit,
) {
    val app = LocalApp.current
    var messages by remember { mutableStateOf<List<Message>?>(null) }
    // The announcements unread while this page is open: the NEW line goes under the last of them.
    val newIds = rememberNewIds(messages)
    var chats by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var channels by remember { mutableStateOf<List<ChannelSummary>>(emptyList()) }
    var next by remember { mutableStateOf<NextRace?>(null) }
    var events by remember { mutableStateOf<List<UpcomingEvent>>(emptyList()) }
    // Admins and coordinators: today's sessions and the people to nudge.
    var board by remember { mutableStateOf<com.arkhins.ctrlaps.data.Dashboard?>(null) }
    val openRoute = LocalOpen.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()
    // Last lines from the phone's own copy, as in the chats list: a send or a delete shows here at once.
    val shownChats = rememberPhoneLast(chats).orEmpty()
    // The chats shown here, read in and their rows made ready, so tapping one opens it drawn.
    LaunchedEffect(chats) {
        chats.forEach { c -> (app.chatCache.peek(c.id) ?: runCatching { app.chatCache.load(c.id) }.getOrNull())?.let { warmChatRows(c.id, it.messages) } }
    }

    LaunchedEffect(Unit) {
        if (messages == null) {
            app.store.read(HOME_KEY, HomeSnapshot.serializer())?.let { s ->
                if (messages == null) {
                    next = s.next
                    chats = s.chats
                    channels = s.channels
                    messages = s.messages
                    events = s.events
                }
            }
        }
    }
    LaunchedEffect(vm.refreshTick, vm.me?.user?.role) {
        if (vm.me?.user?.role == "admin" || vm.me?.user?.role == "coordinator") {
            runCatching { app.store.get("/api/me/dashboard", com.arkhins.ctrlaps.data.DashboardResponse.serializer()) { c -> if (board == null) board = c.dashboard } }
                .onSuccess { board = it.dashboard }
        } else board = null
    }
    LaunchedEffect(vm.refreshTick) {
        try {
            coroutineScope {
                val nextJob = async { runCatching { app.api.get("/api/next-race", NextRace.serializer()) }.getOrNull() }
                val chatsJob = async { runCatching { app.api.get("/api/conversations", ConversationsResponse.serializer()).conversations }.getOrDefault(emptyList()) }
                val weekendsJob = async { runCatching { app.api.get("/api/weekends", WeekendsResponse.serializer()).weekends }.getOrDefault(emptyList()) }
                val inboxJob = async { app.store.fetch("/api/messages", MessagesResponse.serializer()) }
                val eventsJob = async { runCatching { app.store.fetch("/api/events/upcoming", UpcomingEventsResponse.serializer()).events }.getOrNull() }

                next = nextJob.await()
                eventsJob.await()?.let {
                    events = it
                    EventReminders.sync(app, it)
                }
                // Only chats something has been said in, newest first.
                chats = chatsJob.await().filter { it.lastMessageAt != null }.take(3)
                val today = LocalDate.now().toString()
                val active = weekendsJob.await().filter { it.endsOn >= today }.sortedBy { it.startsOn }
                channels = active.map { w ->
                    async { ChannelSummary(w, runCatching { app.store.fetch("/api/weekends/${w.id}/channel", ChannelResponse.serializer()).messages.lastOrNull() }.getOrNull()) }
                }.map { it.await() }

                val r = inboxJob.await()
                // Announcements only: chats and channels have their own sections above.
                messages = r.messages.filter { it.kind == "broadcast" }
                error = null
                app.store.put(HOME_KEY, HomeSnapshot(next, chats, channels, messages.orEmpty(), events), HomeSnapshot.serializer())
                val unread = r.messages.filter { it.readAt == null && !it.mine && it.kind != "direct" }.map { it.id }
                if (unread.isNotEmpty()) {
                    runCatching { app.api.post("/api/messages/read", Ok.serializer()) { putJsonArray("ids") { unread.forEach { add(it) } } } }
                    vm.homeRead()
                }
            }
            if (highlight != null) {
                // Counted in cards, as the list shows them: a run of photos is one.
                val idx = messages?.let { ms -> feedRows(photoRuns(ms.asReversed()).asReversed(), newIds).indexOfFirst { r -> r is FeedRow.Run && r.run.any { it.id == highlight } } } ?: -1
                if (idx >= 0) list.animateScrollToItem(idx + 4)
            }
        } catch (e: Exception) {
            if (messages == null) error = e.message
        }
    }

    // Admins and coordinators send announcements, to anyone.
    val canSend = vm.me?.canAnnounce == true

    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            // The admin's or coordinator's card rides in this first row, so the rows counted below stay where they are.
            board?.let { b ->
                Column {
                    DashboardCard(b, onWeekend = onOpenWeekend, onPerson = { openRoute("person/$it") })
                    Spacer(Modifier.height(10.dp))
                }
            }
            val n = next
            if (n != null && n.state != "none" && n.weekend != null && n.session != null) {
                Panel(Modifier.clickable { onOpenWeekend(n.weekend.id) }) {
                    Column {
                        Text(if (n.state == "live") "LIVE NOW" else "NEXT UP", style = MaterialTheme.typography.labelMedium, color = Gold)
                        Spacer(Modifier.height(4.dp))
                        Text(n.weekend.name, style = MaterialTheme.typography.titleLarge, color = Snow)
                        Text("${n.session.name} · ${localDateTime(n.session.startsAt)}", style = MaterialTheme.typography.bodyMedium, color = SnowSoft)
                        Text("${trackDateTime(n.session.startsAt, n.weekend.timezone)} track time", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                        if (n.weekend.place.isNotBlank()) Text(n.weekend.place, style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                    }
                }
            }
        }

        if (events.isNotEmpty()) {
            item { SectionHeader("Upcoming events") }
            item {
                UpcomingEventsCard(events) { e ->
                    if (e.conversationId != null) onOpenChat(e.conversationId)
                    else scope.launch {
                        // An announcement: down to its card below.
                        val idx = messages?.let { ms -> feedRows(photoRuns(ms.asReversed()).asReversed(), newIds).indexOfFirst { r -> r is FeedRow.Run && r.run.any { it.id == e.messageId } } } ?: -1
                        if (idx >= 0) list.animateScrollToItem(idx + 5 + (if (chats.isNotEmpty()) 2 else 0) + (if (channels.isNotEmpty()) 1 + channels.size else 0))
                    }
                }
            }
        }

        if (chats.isNotEmpty()) {
            item { SectionHeader("Chats", "All chats", onAllChats) }
            item {
                Panel(padding = PaddingValues(6.dp)) {
                    Column {
                        shownChats.forEachIndexed { i, chat ->
                            if (i > 0) Divider()
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenChat(chat.id) }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(app.api.absolute(chat.other.photoUrl), chat.other.name, 44)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(chat.other.name, style = MaterialTheme.typography.titleSmall, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    PreviewLine(chat.lastMessage ?: chat.other.roleLabel, color = if (chat.unread > 0) Snow else SnowFaint, style = MaterialTheme.typography.bodySmall)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    chat.lastMessageAt?.let { Text(whenLabel(it), style = MaterialTheme.typography.labelSmall, color = if (chat.unread > 0) Gold else SnowFaint) }
                                    if (chat.unread > 0) {
                                        Spacer(Modifier.height(4.dp))
                                        Box(Modifier.background(Gold, RoundedCornerShape(999.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                                            Text("${chat.unread}", style = MaterialTheme.typography.labelSmall, color = OnGold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (channels.isNotEmpty()) {
            item { SectionHeader("Weekend channels") }
            items(channels, key = { it.weekend.id }) { c ->
                Panel(Modifier.clickable { onOpenWeekend(c.weekend.id) }) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.weekend.name, style = MaterialTheme.typography.titleSmall, color = Snow, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            c.latest?.let { Text(whenLabel(it.createdAt), style = MaterialTheme.typography.labelSmall, color = SnowFaint) }
                        }
                        Spacer(Modifier.height(4.dp))
                        val m = c.latest
                        if (m == null) {
                            Text("No posts yet.", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
                        } else {
                            Text(
                                buildString {
                                    append(if (m.mine) "You" else m.sender?.name ?: "CTR[L]APS")
                                    append(": ")
                                    // Several files are counted ("📷 3 photos"); one keeps its old wording.
                                    val files = m.attachments
                                    append(m.body.ifBlank { if (files.size > 1) filesLabel(files) else files.firstOrNull()?.let { "Document: ${it.name}" } ?: "" })
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (m.readAt == null && !m.mine) Snow else SnowSoft,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text("Announcements", style = MaterialTheme.typography.titleMedium, color = Snow, modifier = Modifier.weight(1f))
                if (canSend) GoldButton("New message", onClick = onCompose)
            }
        }
        val m = messages
        when {
            error != null && m == null -> item { ErrorText(error) }
            m == null -> item { Loading() }
            m.isEmpty() -> item { Empty("Nothing yet. Messages sent to you appear here.") }
            // Newest first: photos sent one after another are gathered in the order sent, then turned back round; a NEW line under the last unread.
            else -> {
                feedRows(photoRuns(m.asReversed()).asReversed(), newIds).forEach { row ->
                    when (row) {
                        is FeedRow.Day -> stickyHeader(key = row.key) { DayHeader(row.label) }
                        is FeedRow.Run -> item(key = row.key) { MessageCard(row.run, onView, highlight = row.run.any { it.id == highlight }) }
                        FeedRow.New -> item(key = row.key) { NewLine() }
                    }
                }
            }
        }
        item { Spacer(Modifier.fillMaxWidth().height(8.dp)) }
    }
}

@Composable
private fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Snow, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, style = MaterialTheme.typography.labelMedium, color = Gold, modifier = Modifier.clickable(onClick = onAction).padding(4.dp))
        }
    }
}
