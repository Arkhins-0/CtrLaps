package com.arkhins.ctrlaps.ui.screens

import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import com.arkhins.ctrlaps.ui.components.Attachment
import com.arkhins.ctrlaps.ui.components.LinkCard
import com.arkhins.ctrlaps.ui.components.PhotoGrid
import com.arkhins.ctrlaps.ui.components.TextWithMeta
import com.arkhins.ctrlaps.ui.components.formatted
import com.arkhins.ctrlaps.ui.components.runPhotos
import com.arkhins.ctrlaps.ui.components.runText
import com.arkhins.ctrlaps.ui.components.stamped
import com.arkhins.ctrlaps.ui.components.textBesideCard
import com.arkhins.ctrlaps.ui.components.textOf
import com.arkhins.ctrlaps.ui.localTime
import com.arkhins.ctrlaps.data.attachments
import com.arkhins.ctrlaps.ui.components.isImage

import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Search
import com.arkhins.ctrlaps.ui.theme.Night
import androidx.compose.ui.unit.sp
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.LocalApp
import com.arkhins.ctrlaps.data.Faq
import com.arkhins.ctrlaps.data.FaqsResponse
import com.arkhins.ctrlaps.data.IdResponse
import com.arkhins.ctrlaps.data.Ok
import com.arkhins.ctrlaps.data.RaisedTicket
import com.arkhins.ctrlaps.data.SUPPORT_CATEGORIES
import com.arkhins.ctrlaps.data.Ticket
import com.arkhins.ctrlaps.data.TicketViewResponse
import com.arkhins.ctrlaps.data.TicketsResponse
import com.arkhins.ctrlaps.ui.AppViewModel
import com.arkhins.ctrlaps.ui.components.Chip
import com.arkhins.ctrlaps.ui.components.Composer
import com.arkhins.ctrlaps.ui.components.Divider
import com.arkhins.ctrlaps.ui.components.Empty
import com.arkhins.ctrlaps.ui.components.ErrorText
import com.arkhins.ctrlaps.ui.components.Field
import com.arkhins.ctrlaps.ui.components.FileView
import com.arkhins.ctrlaps.ui.components.GhostButton
import com.arkhins.ctrlaps.ui.components.GoldButton
import com.arkhins.ctrlaps.ui.components.Loading
import com.arkhins.ctrlaps.ui.components.MessageCard
import com.arkhins.ctrlaps.ui.components.Panel
import com.arkhins.ctrlaps.ui.components.SectionTitle
import com.arkhins.ctrlaps.ui.components.photoRuns
import com.arkhins.ctrlaps.ui.localDateTime
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.NightLine
import com.arkhins.ctrlaps.ui.theme.NightPanel
import com.arkhins.ctrlaps.ui.theme.OnGold
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import com.arkhins.ctrlaps.ui.theme.SnowSoft
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Account → About → Support: the FAQs, the support form, and tickets (a developer's are everyone's). */
@Composable
fun SupportScreen(vm: AppViewModel, onFaqs: () -> Unit, onForm: () -> Unit, onTickets: () -> Unit) {
    val dev = vm.me?.isDev == true
    val unread = vm.me?.unreadSupport ?: 0
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (dev) "You answer tickets as Support. People see your replies from Support, never your name." else "Find an answer, or ask Support. Replies come here and by email.",
            style = MaterialTheme.typography.bodySmall,
            color = SnowFaint,
        )
        Panel {
            Column {
                MenuRow("FAQs", if (dev) "Common questions; you can add and edit them" else "Answers to common questions", icon = rememberVectorPainter(Icons.Outlined.Search), onClick = onFaqs)
                MenuRow("Support form", "Raise a ticket", icon = rememberVectorPainter(Icons.Outlined.Create), onClick = onForm)
                MenuRow(
                    "Tickets",
                    when {
                        unread > 0 -> "$unread new ${if (unread == 1) "reply" else "replies"}"
                        dev -> "Everyone's tickets: open, closed and all"
                        else -> "Your tickets: open, closed and all"
                    },
                    icon = rememberVectorPainter(Icons.AutoMirrored.Outlined.List),
                    highlight = unread > 0,
                    onClick = onTickets,
                )
            }
        }
    }
}

/* ───────────────────────────── FAQs ──────────────────────────────── */

/** A search box, then the questions by category; a tap opens the answer and its steps. Developers add and edit them. */
@Composable
fun FaqScreen(vm: AppViewModel) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val dev = vm.me?.isDev == true
    var faqs by remember { mutableStateOf<List<Faq>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    // A question being edited, or "new" for one being added.
    var editing by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        try {
            faqs = app.store.get("/api/support/faqs", FaqsResponse.serializer()) { if (faqs == null) faqs = it.faqs }.faqs
            error = null
        } catch (e: Exception) {
            if (faqs == null) error = e.message
        }
    }
    fun act(block: suspend () -> Unit) = scope.launch {
        try {
            block()
            error = null
            reload++
        } catch (e: Exception) {
            error = e.message ?: "Could not save."
        }
    }

    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    val all = faqs.orEmpty()
    val shown = all.filter { f -> "${f.question} ${f.answer} ${f.steps.joinToString(" ")} ${f.category}".lowercase().let { t -> words.all { it in t } } }
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Field(query, { query = it }, "Search", placeholder = "Search the questions") }
        error?.let { e -> item { ErrorText(e) } }
        if (dev && editing == null) item { GhostButton("+ Add a question") { editing = "new" } }
        if (editing == "new") item {
            FaqEditor(null, onCancel = { editing = null }) { c, q, a, s ->
                act {
                    app.api.post("/api/support/faqs", Ok.serializer()) { put("category", c); put("question", q); put("answer", a); putJsonArray("steps") { s.forEach { add(it) } } }
                    editing = null
                }
            }
        }
        when {
            faqs == null && error == null -> item { Loading() }
            shown.isEmpty() -> item { Empty(if (all.isEmpty()) "No questions yet." else "Nothing matches. Try other words, or raise a ticket.") }
            else -> shown.map { it.category }.distinct().forEach { category ->
                item(key = "c-$category") { SectionTitle(category.uppercase(), Modifier.padding(top = 6.dp)) }
                items(shown.filter { it.category == category }, key = { it.id }) { f ->
                    if (editing == f.id) {
                        FaqEditor(f, onCancel = { editing = null }) { c, q, a, s ->
                            act {
                                app.api.patch("/api/support/faqs/${f.id}", Ok.serializer()) { put("category", c); put("question", q); put("answer", a); putJsonArray("steps") { s.forEach { add(it) } } }
                                editing = null
                            }
                        }
                    } else {
                        FaqItem(
                            f,
                            expanded = open == f.id,
                            onToggle = { open = if (open == f.id) null else f.id },
                            canEdit = dev,
                            onEdit = { editing = f.id },
                            onMove = { by ->
                                val i = all.indexOfFirst { it.id == f.id }
                                all.getOrNull(i + by)?.let { other ->
                                    act {
                                        app.api.patch("/api/support/faqs/${f.id}", Ok.serializer()) { put("position", other.position) }
                                        app.api.patch("/api/support/faqs/${other.id}", Ok.serializer()) { put("position", if (f.position == other.position) f.position + by else f.position) }
                                    }
                                }
                            },
                            onDelete = { act { app.api.delete("/api/support/faqs/${f.id}") } },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FaqItem(f: Faq, expanded: Boolean, onToggle: () -> Unit, canEdit: Boolean, onEdit: () -> Unit, onMove: (Int) -> Unit, onDelete: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    // A row, as in Arkhime's FAQ: a question mark, the question, an arrow; the answer opens in a sheet.
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).background(SnowSoft, CircleShape), contentAlignment = Alignment.Center) {
            Text("?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Night)
        }
        Spacer(Modifier.width(18.dp))
        Text(f.question, style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp), fontWeight = FontWeight.Bold, color = Snow, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Gold)
    }
    if (expanded) {
        ModalBottomSheet(onDismissRequest = onToggle, containerColor = Night) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(f.question, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Snow, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text(f.answer, style = MaterialTheme.typography.bodyLarge, color = SnowSoft)
                f.steps.forEachIndexed { i, step ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(22.dp).background(Gold, CircleShape), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = OnGold, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(step, style = MaterialTheme.typography.bodyLarge, color = SnowSoft, modifier = Modifier.weight(1f).padding(top = 1.dp))
                    }
                }
                if (canEdit) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("Edit", Gold) { onToggle(); onEdit() }
                        Chip("Up", SnowSoft) { onMove(-1) }
                        Chip("Down", SnowSoft) { onMove(1) }
                        Chip("Delete", MaterialTheme.colorScheme.error) { asking = true }
                    }
                }
                OutlinedButton(
                    onClick = onToggle,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.6f)),
                ) { Text("Close", color = Gold, fontWeight = FontWeight.Bold) }
            }
        }
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            containerColor = NightPanel,
            title = { Text("Delete this question?", color = Snow) },
            confirmButton = { TextButton(onClick = { asking = false; onDelete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("Cancel", color = SnowSoft) } },
        )
    }
}

/** Add or change a question: category, question, answer, and the steps one per line. */
@Composable
private fun FaqEditor(faq: Faq?, onCancel: () -> Unit, onSave: (String, String, String, List<String>) -> Unit) {
    var category by remember { mutableStateOf(faq?.category ?: SUPPORT_CATEGORIES.first()) }
    var question by remember { mutableStateOf(faq?.question ?: "") }
    var answer by remember { mutableStateOf(faq?.answer ?: "") }
    var steps by remember { mutableStateOf(faq?.steps?.joinToString("\n") ?: "") }
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(if (faq == null) "NEW QUESTION" else "EDIT QUESTION")
            CategoryPicker(category, (SUPPORT_CATEGORIES + category).distinct()) { category = it }
            Field(question, { question = it }, "Question", singleLine = false)
            Field(answer, { answer = it }, "Answer", singleLine = false)
            Field(steps, { steps = it }, "Steps, one per line (optional)", singleLine = false)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoldButton("Save", Modifier.weight(1f), enabled = question.isNotBlank() && answer.isNotBlank()) {
                    onSave(category, question.trim(), answer.trim(), steps.lines().map { it.trim() }.filter { it.isNotEmpty() })
                }
                GhostButton("Cancel", onClick = onCancel)
            }
        }
    }
}

/** A dropdown of the support categories. */
@Composable
private fun CategoryPicker(value: String, choices: List<String>, label: String = "Category", onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier.fillMaxWidth().background(NightPanel, RoundedCornerShape(12.dp)).clickable { open = true }.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = SnowFaint)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value.ifBlank { "Choose…" }, style = MaterialTheme.typography.bodyLarge, color = if (value.isBlank()) SnowFaint else Snow, modifier = Modifier.weight(1f))
                Text("⌄", color = Gold, style = MaterialTheme.typography.titleMedium)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = NightPanel) {
            choices.forEach { c -> DropdownMenuItem(text = { Text(c, color = Snow) }, onClick = { onPick(c); open = false }) }
        }
    }
}

/* ───────────────────────────── The form ──────────────────────────── */

/** Raise a ticket: name, email and contact (from the profile, editable), what it is about, a subject and the details. */
@Composable
fun TicketFormScreen(vm: AppViewModel, onRaised: (String) -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val u = vm.me?.user
    var name by rememberSaveable { mutableStateOf(u?.name ?: "") }
    var email by rememberSaveable { mutableStateOf(u?.email ?: "") }
    var phone by rememberSaveable { mutableStateOf(u?.phone ?: "") }
    var category by rememberSaveable { mutableStateOf("") }
    var subject by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var raised by remember { mutableStateOf<RaisedTicket?>(null) }
    // Screenshots and files going with it (up to ten; the server takes them from someone signed in).
    val context = androidx.compose.ui.platform.LocalContext.current
    val files = remember { androidx.compose.runtime.mutableStateListOf<TicketFile>() }
    var sending by remember { mutableStateOf<String?>(null) }
    // Come from the crash page: an app problem, the error in the details and the whole report attached (once).
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.arkhins.ctrlaps.data.CrashReporter.ticket.value?.let {
            category = "App problem"
            subject = "The app stopped"
            details = it
            com.arkhins.ctrlaps.data.CrashReporter.ticket.value = null
            com.arkhins.ctrlaps.data.CrashReporter.read(context)?.let { report ->
                val f = java.io.File(context.cacheDir, "support/${com.arkhins.ctrlaps.data.CrashReporter.REPORT_NAME}").apply { parentFile?.mkdirs(); writeText(report) }
                files.add(TicketFile(com.arkhins.ctrlaps.data.CrashReporter.REPORT_NAME, "text/plain", f.path))
            }
        }
    }
    fun add(uris: List<android.net.Uri>) {
        val room = MAX_TICKET_FILES - files.size
        if (uris.size > room) error = "Up to $MAX_TICKET_FILES files go with a ticket."
        scope.launch { uris.take(room.coerceAtLeast(0)).forEach { uri -> copyForTicket(context, uri)?.let { files.add(it) } } }
    }
    val pickPhotos = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(MAX_TICKET_FILES),
    ) { add(it) }
    val pickFiles = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments(),
    ) { add(it) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val r = raised
        if (r != null) {
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Ticket ${r.label} is open", style = MaterialTheme.typography.titleMedium, color = Snow)
                    Text("Support has your request. You'll be told here and by email when they reply.", style = MaterialTheme.typography.bodySmall, color = SnowSoft)
                    r.id?.let { id -> GoldButton("Open the ticket", Modifier.fillMaxWidth()) { onRaised(id) } }
                }
            }
            return@Column
        }
        Text("Tell Support what's wrong. You get a ticket number, and the replies come here and by email.", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
        Field(name, { name = it }, "Name", enabled = !busy)
        Field(email, { email = it }, "Email", keyboard = KeyboardType.Email, enabled = !busy)
        Field(phone, { phone = it }, "Contact number (optional)", keyboard = KeyboardType.Phone, enabled = !busy)
        CategoryPicker(category, SUPPORT_CATEGORIES, "What is it about?") { category = it }
        Field(subject, { subject = it }, "Subject", enabled = !busy, placeholder = "In a few words")
        Field(details, { details = it }, "Details", singleLine = false, enabled = !busy, placeholder = "What happened, what you expected, and on which phone")
        files.forEach { f ->
            Row(
                Modifier.fillMaxWidth().background(NightPanel, RoundedCornerShape(12.dp)).padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    androidx.compose.ui.res.painterResource(if (f.mime.startsWith("image/")) com.arkhins.ctrlaps.R.drawable.ic_gallery else com.arkhins.ctrlaps.R.drawable.ic_document),
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(f.name, style = MaterialTheme.typography.bodyMedium, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                androidx.compose.material3.IconButton(onClick = { files.remove(f) }, enabled = !busy) {
                    Icon(Icons.Outlined.Close, contentDescription = "Remove ${f.name}", tint = SnowFaint)
                }
            }
        }
        if (files.size < MAX_TICKET_FILES) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Add screenshots", Modifier.weight(1f), enabled = !busy) {
                    pickPhotos.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                GhostButton("Add files", Modifier.weight(1f), enabled = !busy) { pickFiles.launch(arrayOf("*/*")) }
            }
        }
        ErrorText(error)
        GoldButton(
            if (busy) sending ?: "Sending…" else "Raise ticket",
            Modifier.fillMaxWidth(),
            enabled = !busy && name.isNotBlank() && email.isNotBlank() && category.isNotBlank() && subject.isNotBlank() && details.isNotBlank(),
        ) {
            busy = true
            error = null
            scope.launch {
                try {
                    // The files go up first; a photo as a photo, anything else as a document.
                    val ids = files.mapIndexed { i, f ->
                        sending = "Uploading ${i + 1} of ${files.size}…"
                        com.arkhins.ctrlaps.data.uploadFile(app.api, app.documents, app.chatMedia, java.io.File(f.path), f.name, f.mime, asDocument = !f.mime.startsWith("image/")).id
                    }
                    sending = null
                    raised = app.api.post("/api/support/tickets", RaisedTicket.serializer()) {
                        put("name", name.trim()); put("email", email.trim()); put("phone", phone.trim())
                        put("category", category); put("subject", subject.trim()); put("details", details.trim())
                        if (ids.isNotEmpty()) putJsonArray("fileIds") { ids.forEach { add(it) } }
                    }
                    vm.changed()
                } catch (e: Exception) {
                    error = e.message ?: "Could not send it."
                } finally {
                    busy = false
                    sending = null
                }
            }
        }
    }
}

/** The most files a ticket takes (the server's limit). */
private const val MAX_TICKET_FILES = 10

/** A screenshot or file going with a ticket: a copy in the app's cache until it is sent. */
private data class TicketFile(val name: String, val mime: String, val path: String)

/** A picked file copied in (a picker's link may stop working before the ticket goes), with its name and type. */
private suspend fun copyForTicket(context: android.content.Context, uri: android.net.Uri): TicketFile? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "file"
        val out = java.io.File(context.cacheDir, "support/${System.nanoTime()}-$name").apply { parentFile?.mkdirs() }
        resolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
        TicketFile(name, resolver.getType(uri) ?: "application/octet-stream", out.path)
    }.getOrNull()
}

/* ───────────────────────────── Tickets ───────────────────────────── */

/** Open · Closed · All: your tickets, or (a developer) everyone's, with who raised each. */
@Composable
fun TicketsScreen(vm: AppViewModel, onOpen: (String) -> Unit) {
    val app = LocalApp.current
    var status by rememberSaveable { mutableStateOf("open") }
    var data by remember { mutableStateOf<TicketsResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(status, tick, vm.refreshTick) {
        try {
            data = app.store.get("/api/support/tickets?status=$status", TicketsResponse.serializer()) { if (data == null) data = it }
            error = null
        } catch (e: Exception) {
            if (data == null) error = e.message
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            tick++
        }
    }
    val dev = data?.isDev == true
    val q = query.trim().lowercase()
    val shown = data?.tickets.orEmpty().filter { t -> q.isEmpty() || "${t.label} ${t.subject} ${t.category} ${t.name} ${t.email}".lowercase().contains(q) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("open" to "Open", "closed" to "Closed", "all" to "All").forEach { (k, label) ->
                    Chip(label, Gold, filled = status == k) { if (status != k) { status = k; data = null } }
                }
            }
        }
        if (dev) item { Field(query, { query = it }, "Search", placeholder = "Number, subject, name or email") }
        when {
            error != null && data == null -> item { ErrorText(error) }
            data == null -> item { Loading() }
            shown.isEmpty() -> item { Empty(when (status) { "open" -> "No open tickets."; "closed" -> "No closed tickets."; else -> "No tickets yet." }) }
            else -> item {
                Panel(padding = PaddingValues(6.dp)) {
                    Column {
                        shown.forEachIndexed { i, t ->
                            if (i > 0) Divider()
                            TicketRow(t, dev) { onOpen(t.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketRow(t: Ticket, dev: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.label, style = MaterialTheme.typography.labelMedium, color = Gold, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(8.dp))
                Text(t.subject, style = MaterialTheme.typography.titleSmall, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                listOfNotNull(if (dev) t.name else null, t.category, t.lastMessage).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = SnowFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Chip(if (t.status == "open") "Open" else "Closed", if (t.status == "open") Gold else SnowFaint)
            if (t.unread > 0) {
                Box(Modifier.background(Gold, CircleShape).padding(horizontal = 6.dp, vertical = 1.dp)) {
                    Text("${t.unread}", style = MaterialTheme.typography.labelSmall, color = OnGold, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * A ticket: what was asked, then its chat with Support (oldest first) and the box to write in while it is open.
 * Its owner or a developer closes it; its owner reopens it within 2 days, a developer any time.
 */
@Composable
fun TicketScreen(vm: AppViewModel, ticketId: String, onView: (FileView) -> Unit, onTitle: (String) -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var v by remember { mutableStateOf<TicketViewResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    val list = rememberLazyListState()

    LaunchedEffect(ticketId, tick, vm.refreshTick) {
        try {
            v = app.store.get("/api/support/tickets/$ticketId", TicketViewResponse.serializer()) { if (v == null) v = it }
            v?.let { onTitle("Ticket ${it.ticket.label}") }
            error = null
        } catch (e: Exception) {
            if (v == null) error = e.message
        }
    }
    LaunchedEffect(ticketId) {
        while (true) {
            delay(10_000)
            tick++
        }
    }
    val count = v?.messages?.size ?: 0
    LaunchedEffect(count) { if (count > 0) list.animateScrollToItem(list.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1) }

    fun setStatus(status: String) {
        busy = true
        error = null
        scope.launch {
            try {
                v = app.api.patch("/api/support/tickets/$ticketId", TicketViewResponse.serializer()) { put("status", status) }
                vm.changed()
            } catch (e: Exception) {
                error = e.message ?: "Could not change it."
            } finally {
                busy = false
            }
        }
    }

    val d = v
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                error != null && d == null -> item { ErrorText(error) }
                d == null -> item { Loading() }
                else -> {
                    val t = d.ticket
                    // Flat, as the newer pages: what it is about large, then (for Support) who raised it as icon rows.
                    item {
                        Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(t.label, style = MaterialTheme.typography.labelLarge, color = Gold, fontFamily = FontFamily.Monospace)
                                Chip(if (t.status == "open") "Open" else "Closed", if (t.status == "open") Gold else SnowFaint, filled = t.status == "open")
                                Chip(t.category, SnowSoft)
                            }
                            Text(t.subject, style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, lineHeight = 30.sp), fontWeight = FontWeight.Bold, color = Snow)
                            Text("Raised ${localDateTime(t.createdAt)}", style = MaterialTheme.typography.bodySmall, color = SnowSoft.copy(alpha = 0.8f))
                        }
                    }
                    if (d.isDev) item {
                        Column {
                            DetailRow(Icons.Outlined.Person, "Raised by", t.name + if (t.userId == null) " · no account" else "")
                            DetailRow(Icons.Outlined.Email, "Email", t.email + if (t.userId == null) " · replies go here" else "")
                            t.phone?.takeIf { it.isNotBlank() }?.let { DetailRow(Icons.Outlined.Phone, "Contact", it) }
                        }
                    }
                    // The details open the chat below (with any files) when it was raised signed in: not twice.
                    if (d.messages.none { it.body.trim() == t.details.trim() }) item {
                        Text(t.details, style = MaterialTheme.typography.bodyMedium, color = SnowSoft, modifier = Modifier.padding(vertical = 4.dp))
                    }
                    item {
                        Column {
                            ErrorText(error)
                            if (d.canClose) MenuRow("Close ticket", if (d.isDev) "Tell them it is sorted" else "Sorted? You can reopen it within 2 days", icon = rememberVectorPainter(Icons.Outlined.CheckCircle), arrow = false) { if (!busy) closing = true }
                            if (d.canReopen) {
                                MenuRow(
                                    "Reopen ticket",
                                    if (!d.isDev) d.reopenUntil?.let { "Until ${localDateTime(it)}" } ?: "Write to Support again" else "Open it again",
                                    highlight = true,
                                    icon = rememberVectorPainter(Icons.Outlined.Refresh),
                                    arrow = false,
                                ) { if (!busy) setStatus("open") }
                            }
                            if (t.status == "closed" && !d.canReopen && !d.isDev) {
                                Text("Closed more than 2 days ago. Raise a new ticket if you still need help.", style = MaterialTheme.typography.bodySmall, color = SnowFaint, modifier = Modifier.padding(vertical = 8.dp))
                            }
                            HorizontalDivider(color = NightLine, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        }
                    }
                    if (d.messages.isEmpty()) item { Text(if (d.isDev) "No replies yet." else "Support will reply here.", style = MaterialTheme.typography.labelSmall, color = SnowFaint, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) }
                    // As a chat: yours on the right in the accent, Support's on the left; lines about the ticket as a
                    // pill; photos sent together as one grid.
                    val runs = photoRuns(d.messages.filter { it.event == null })
                    val byFirst = runs.associateBy { it.first().id }
                    items(d.messages.filter { it.event != null || it.id in byFirst }, key = { it.id }) { m ->
                        if (m.event != null) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    m.event,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SnowSoft,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.background(NightPanel.copy(alpha = 0.7f), RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
                                )
                            }
                        } else {
                            TicketBubble(byFirst.getValue(m.id), onView)
                        }
                    }
                }
            }
        }
        if (d?.canReply == true) {
            Box(Modifier.fillMaxWidth().background(NightLine.copy(alpha = 0.3f)).padding(horizontal = 8.dp, vertical = 6.dp)) {
                Composer(placeholder = if (d.isDev) "Reply as Support" else "Write to Support", urgentOption = false, voiceNoteSends = false, polls = false) { draft ->
                    app.api.post("/api/support/tickets/$ticketId", IdResponse.serializer()) {
                        put("body", draft.body)
                        draft.link?.let { put("linkUrl", it.url) }
                        putJsonArray("fileIds") { draft.fileIds.forEach { add(it) } }
                    }
                    tick++
                }
            }
        }
    }
    if (closing) {
        AlertDialog(
            onDismissRequest = { closing = false },
            containerColor = NightPanel,
            title = { Text("Close this ticket?", color = Snow) },
            text = { if (v?.isDev != true) Text("You can reopen it within 2 days.", color = SnowSoft) },
            confirmButton = { TextButton(onClick = { closing = false; setStatus("closed") }) { Text("Close", color = Gold) } },
            dismissButton = { TextButton(onClick = { closing = false }) { Text("Cancel", color = SnowSoft) } },
        )
    }
}

/**
 * One message in a ticket, as a chat bubble (the same look as Chats): yours in the accent on the right, Support's
 * (or, for Support, the person's) dark on the left with who wrote it; photos as a grid, files as cards, the time
 * tucked into the last line.
 */
@Composable
private fun TicketBubble(run: List<com.arkhins.ctrlaps.data.Message>, onView: (FileView) -> Unit) {
    val m = run.last()
    val mine = m.mine
    val view: (FileView) -> Unit = { onView(it.stamped(run)) }
    val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp)
    val photos = runPhotos(run)
    val files = run.flatMap { it.attachments }.filterNot { it.isImage }
    val text = if (run.size > 1) runText(run) else textBesideCard(textOf(m.body), m.linkPreview)
    val picture = photos.isNotEmpty() || m.linkPreview != null
    val inset = if (picture) Modifier.padding(horizontal = 9.dp) else Modifier
    val meta: @Composable () -> Unit = {
        Text(localTime(m.createdAt), style = MaterialTheme.typography.labelSmall, color = if (mine) OnGold.copy(alpha = 0.6f) else SnowFaint)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .then(if (picture) Modifier.width(IntrinsicSize.Max) else Modifier)
                .clip(shape)
                .background(if (mine) Gold else NightPanel)
                .border(1.dp, if (mine) Gold else NightLine, shape)
                .then(if (picture) Modifier.padding(start = 3.dp, end = 3.dp, top = 3.dp, bottom = 6.dp) else Modifier.padding(horizontal = 12.dp, vertical = 8.dp)),
        ) {
            if (!mine) {
                Text(m.sender?.name ?: "Support", style = MaterialTheme.typography.labelMedium, color = Gold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = inset)
                Spacer(Modifier.height(2.dp))
            }
            PhotoGrid(photos, view, fill = true)
            files.forEachIndexed { i, f ->
                if (i > 0 || photos.isNotEmpty()) Spacer(Modifier.height(6.dp))
                Box(inset) { Attachment(f, view, onDark = !mine) }
            }
            m.linkPreview?.let { card ->
                if (files.isNotEmpty() || photos.isNotEmpty()) Spacer(Modifier.height(6.dp))
                LinkCard(card, onDark = !mine)
            }
            if (text.isNotBlank()) {
                if (files.isNotEmpty() || photos.isNotEmpty() || m.linkPreview != null) Spacer(Modifier.height(6.dp))
                val words = remember(text) { formatted(text) }
                TextWithMeta(words, style = MaterialTheme.typography.bodyMedium.copy(color = if (mine) OnGold else Snow), meta = meta, modifier = inset)
            } else {
                Spacer(Modifier.height(2.dp))
                Row(Modifier.align(Alignment.End).then(inset)) { meta() }
            }
        }
    }
}
