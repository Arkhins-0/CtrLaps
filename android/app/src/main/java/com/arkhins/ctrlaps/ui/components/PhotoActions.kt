package com.arkhins.ctrlaps.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.arkhins.ctrlaps.LocalApp
import com.arkhins.ctrlaps.R
import com.arkhins.ctrlaps.data.FileInfo
import com.arkhins.ctrlaps.data.Message
import com.arkhins.ctrlaps.data.attachments
import com.arkhins.ctrlaps.data.forwardMessages
import com.arkhins.ctrlaps.ui.instant
import com.arkhins.ctrlaps.ui.theme.Danger
import com.arkhins.ctrlaps.ui.theme.NightPanel
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import com.arkhins.ctrlaps.ui.theme.SnowSoft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.add
import kotlinx.serialization.json.putJsonArray

/** A message may be deleted for everyone within 2 hours of sending (as the server allows). */
private const val REMOVE_WINDOW_MS = 2 * 60 * 60 * 1000L

/**
 * The full-screen photo's header actions, as WhatsApp has them (only what CTR[L]APS does): Save, Forward, and a menu
 * with Share, Show in chat and — your own, within 2 hours — Delete. [message] is the one the photo came in; without it
 * (an older screen) only Save and Share show.
 */
@Composable
fun PhotoViewerActions(
    file: FileInfo,
    message: Message?,
    sentAt: String?,
    /** People with chats may forward; users (no role yet) have none. */
    canForward: Boolean,
    /** Back to the chat the photo is in; null when it is not in a chat (an announcement or a channel post). */
    onShowInChat: (() -> Unit)?,
    /** The photo was deleted: close the viewer and let the screens behind ask again. */
    onDeleted: () -> Unit,
) {
    val app = LocalApp.current
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var forwarding by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val sent = message != null && !message.id.startsWith("local-")
    val deletable = sent && message!!.mine && System.currentTimeMillis() - instant(message.createdAt).toEpochMilli() < REMOVE_WINDOW_MS
    // A message carrying several photos loses just this one; a photo that is its own message goes whole.
    val onlyFile = message != null && message.attachments.size > 1

    val share = {
        app.appScope.launch {
            val shared = runCatching {
                val f = app.chatMedia.fetch(file)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", f)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = file.mime.ifBlank { "image/*" }
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                withContext(Dispatchers.Main) { context.startActivity(Intent.createChooser(send, "Share photo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            if (shared.isFailure) withContext(Dispatchers.Main) { Toast.makeText(context, "Could not share the photo.", Toast.LENGTH_SHORT).show() }
        }
    }

    Row {
        SaveButton { saveAll(context, app, listOf(file to sentAt)) }
        if (sent && canForward) {
            IconButton(onClick = { forwarding = true }) {
                Icon(painterResource(R.drawable.ic_forward), contentDescription = "Forward", tint = Snow, modifier = Modifier.size(24.dp))
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More", tint = Snow) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = NightPanel) {
                DropdownMenuItem(
                    text = { Text("Share", color = Snow) },
                    leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null, tint = SnowSoft) },
                    onClick = { menu = false; share() },
                )
                if (onShowInChat != null) {
                    DropdownMenuItem(
                        text = { Text("Show in chat", color = Snow) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_tab_chat), contentDescription = null, tint = SnowSoft, modifier = Modifier.size(22.dp)) },
                        onClick = { menu = false; onShowInChat() },
                    )
                }
                if (deletable) {
                    DropdownMenuItem(
                        text = { Text("Delete", color = Danger) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = Danger) },
                        onClick = { menu = false; confirmDelete = true },
                    )
                }
            }
        }
    }

    if (confirmDelete && message != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = NightPanel,
            title = { Text("Delete photo?", color = Snow) },
            text = { Text(if (onlyFile) "It will be taken out of the message for everyone." else "It will be deleted for everyone.", color = SnowSoft) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    app.appScope.launch {
                        val ok = runCatching {
                            if (onlyFile) {
                                app.api.delete("/api/messages/${message.id}/files") { putJsonArray("fileIds") { add(file.id) } }
                            } else {
                                message.conversationId?.let { app.chatCache.deleteLocally(it, setOf(message.id)) }
                                app.api.delete("/api/messages/${message.id}")
                                app.chatCache.deleteDone(setOf(message.id))
                            }
                        }.isSuccess
                        message.conversationId?.let { c -> runCatching { app.chatCache.sync(c, markRead = false) } }
                        withContext(Dispatchers.Main) {
                            if (ok) onDeleted() else Toast.makeText(context, "Could not delete the photo.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = SnowFaint) } },
        )
    }

    if (forwarding && message != null) {
        ForwardSheet(1, onDismiss = { forwarding = false }, what = "photo") { targets ->
            forwarding = false
            Toast.makeText(context, if (targets.size == 1) "Forwarding to ${targets[0].other.name}" else "Forwarding to ${targets.size} chats", Toast.LENGTH_SHORT).show()
            app.appScope.launch {
                val failed = forwardMessages(app.chatCache, app.api, listOf(message), targets.map { it.id }, onlyFiles = if (onlyFile) listOf(file) else null)
                withContext(Dispatchers.Main) {
                    targets.filter { it.id in failed }.forEach { c -> Toast.makeText(context, "Could not forward to ${c.other.name}", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }
}
