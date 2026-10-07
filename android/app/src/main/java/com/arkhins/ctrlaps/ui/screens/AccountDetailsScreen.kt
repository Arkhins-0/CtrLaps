package com.arkhins.ctrlaps.ui.screens

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.AccountBox
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.LocalApp
import com.arkhins.ctrlaps.data.Ok
import com.arkhins.ctrlaps.data.UserResponse
import com.arkhins.ctrlaps.ui.components.Avatar
import com.arkhins.ctrlaps.ui.components.DateField
import com.arkhins.ctrlaps.ui.AppViewModel
import com.arkhins.ctrlaps.ui.components.ErrorText
import com.arkhins.ctrlaps.ui.components.Field
import com.arkhins.ctrlaps.ui.components.GhostButton
import com.arkhins.ctrlaps.ui.components.GoldButton
import com.arkhins.ctrlaps.ui.components.IconAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.components.KeyValue
import com.arkhins.ctrlaps.ui.components.Panel
import com.arkhins.ctrlaps.ui.components.SquareCropDialog
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import com.arkhins.ctrlaps.ui.theme.SnowSoft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.serialization.json.put

/** The account's details (editable by someone with no role yet, read-only after), changing the password, and a reset link for a forgotten one. */
@Composable
fun AccountDetailsScreen(vm: AppViewModel) {
    val me = vm.me ?: return
    val u = me.user
    var sheet by remember { mutableStateOf<String?>(null) }
    // Users (no role yet) and admins, who have no manager above them, edit their own details: the pencil opens the form.
    // Users (no role yet), admins and coordinators change their own details; everyone else asks their manager.
    val canEdit = u.role == "user" || u.role == "admin" || u.role == "coordinator"
    var editing by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        if (editing) EditProfilePanel(vm) { editing = false }
        else {
            // The details as rows: an icon, the value, and what it is in small letters above.
            DetailRow(Icons.Outlined.Person, "Name", u.displayName)
            DetailRow(Icons.Outlined.Email, "Email", u.email)
            DetailRow(Icons.Outlined.Phone, "Contact", u.phone ?: "—")
            DetailRow(Icons.Outlined.DateRange, "Date of birth", u.dob ?: "—")
            DetailRow(Icons.Outlined.AccountBox, "Role", u.roleLabel + (u.teamName?.let { " · $it" } ?: ""))
            me.parent?.let { DetailRow(Icons.Outlined.Face, "Reports to", "${it.name} · ${it.roleLabel}") }
            if (!canEdit) Text("Profile details are locked. Your manager or an admin can change them.", style = MaterialTheme.typography.bodySmall, color = SnowFaint, modifier = Modifier.padding(top = 4.dp))
        }

        if (!editing) {
            Spacer(Modifier.height(8.dp))
            if (canEdit) MenuRow("Edit details", "Name, photo, date of birth and contact", icon = rememberVectorPainter(Icons.Outlined.Edit)) { editing = true }
            if (canEdit) MenuRow("Change email", "A link goes to the new address", icon = rememberVectorPainter(Icons.Outlined.Email), arrow = false) { sheet = "email" }
            MenuRow("Change password", "Other devices are signed out", icon = rememberVectorPainter(Icons.Outlined.Lock), arrow = false) { sheet = "password" }
            MenuRow("Forgot password", "Get a link at ${u.email}", icon = rememberVectorPainter(Icons.Outlined.Refresh), arrow = false) { sheet = "forgot" }
        }
    }

    when (sheet) {
        "email" -> AboutSheet("Change email", onClose = { sheet = null }) { SheetBody { ChangeEmailForm() } }
        "password" -> AboutSheet("Change password", onClose = { sheet = null }) { SheetBody { ChangePasswordForm { sheet = null } } }
        "forgot" -> AboutSheet("Forgot password", onClose = { sheet = null }) { SheetBody { ForgotPasswordForm(u.email) } }
        else -> Unit
    }
}

/** A form inside a sheet: the page's side margins. */
@Composable
private fun SheetBody(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) { content() }
}

/** One detail: an accent icon, the value, and what it is in small letters above it. */
@Composable
fun DetailRow(icon: ImageVector, label: String, value: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Gold, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = SnowFaint)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Snow)
        }
        trailing?.invoke()
    }
}

@Composable
private fun ChangePasswordForm(onDone: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (done) {
            Text("Password changed. Other devices are signed out.", color = SnowSoft)
            GhostButton("Close", onClick = onDone)
            return@Column
        }
        ErrorText(error)
        Field(current, { current = it }, "Current password", password = true, enabled = !busy)
        Field(next, { next = it }, "New password", password = true, enabled = !busy)
        Field(again, { again = it }, "Repeat new password", password = true, enabled = !busy)
        GoldButton(if (busy) "Saving…" else "Change password", Modifier.fillMaxWidth(), enabled = !busy && next.length >= 8 && current.isNotBlank()) {
            if (next != again) {
                error = "The new passwords do not match."
                return@GoldButton
            }
            busy = true
            error = null
            scope.launch {
                try {
                    app.api.post("/api/auth/password", Ok.serializer()) {
                        put("current", current)
                        put("next", next)
                    }
                    done = true
                } catch (e: Exception) {
                    error = e.message ?: "Could not change."
                } finally {
                    busy = false
                }
            }
        }
        GhostButton("Cancel", enabled = !busy, onClick = onDone)
    }
}

/** Forgot the current password: the same reset link the sign-in page sends, to this account's email. */
@Composable
private fun ForgotPasswordForm(email: String) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (sent) "A link to choose a new password is on its way to $email. It works for 2 hours."
            else "Don't know your current password? Get a link at $email to choose a new one.",
            style = MaterialTheme.typography.bodyMedium,
            color = SnowSoft,
        )
        ErrorText(error)
        if (!sent) GoldButton(if (busy) "Sending…" else "Email me a link", Modifier.fillMaxWidth(), enabled = !busy) {
            busy = true
            error = null
            scope.launch {
                try {
                    app.api.post("/api/auth/forgot", Ok.serializer()) { put("email", email) }
                    sent = true
                } catch (e: Exception) {
                    error = e.message ?: "Could not send."
                } finally {
                    busy = false
                }
            }
        }
    }
}

/** Someone with no role yet, or an admin, changes their own name, date of birth, contact number and photo; [onDone] closes the form. */
@Composable
private fun EditProfilePanel(vm: AppViewModel, onDone: () -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val u = vm.me?.user ?: return
    var name by remember(u) { mutableStateOf(u.name ?: "") }
    var dob by remember(u) { mutableStateOf(u.dob ?: "") }
    var phone by remember(u) { mutableStateOf(u.phone ?: "") }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var cropping by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { cropping = withContext(Dispatchers.IO) { loadShrunk(context, uri, 1600) } }
    }

    cropping?.let { src -> SquareCropDialog(src, onCancel = { cropping = null }) { photo = it; cropping = null } }
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Edit profile", style = MaterialTheme.typography.titleMedium, color = Snow)
            if (u.role == "user") Text("You can change your details until an organiser gives you a role.", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
            ErrorText(error)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(72.dp).clip(CircleShape).clickable(enabled = !busy) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    val bmp = photo
                    if (bmp != null) Image(bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp))
                    else Avatar(app.api.absolute(u.photoUrl), u.displayName, 72, preview = false)
                }
                Spacer(Modifier.width(14.dp))
                GhostButton("Change photo", enabled = !busy) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            }
            Field(name, { name = it }, "Full name", enabled = !busy)
            DateField(dob, { dob = it }, "Date of birth", enabled = !busy, maxToday = true)
            Field(phone, { phone = it }, "Contact number", keyboard = KeyboardType.Phone, enabled = !busy)
            GoldButton(if (busy) "Saving…" else "Save", Modifier.fillMaxWidth(), enabled = !busy && name.isNotBlank() && dob.isNotBlank() && phone.isNotBlank()) {
                busy = true
                error = null
                scope.launch {
                    val bmp = photo
                    val file = if (bmp == null) null else withContext(Dispatchers.IO) {
                        File(context.cacheDir, "photo.jpg").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
                    }
                    try {
                        app.api.postForm(
                            "/api/me/details",
                            mapOf("name" to name.trim(), "dob" to dob.trim(), "phone" to phone.trim()),
                            file?.let { "photo" to it },
                            "image/jpeg",
                            UserResponse.serializer(),
                        )
                        photo = null
                        vm.refreshMe()
                        onDone()
                    } catch (e: Exception) {
                        error = e.message ?: "Could not save."
                    } finally {
                        file?.delete()
                        busy = false
                    }
                }
            }
            GhostButton("Cancel", enabled = !busy, onClick = onDone)
        }
    }
}

/** A new email: a link goes to it, and the change happens when it is opened. */
@Composable
private fun ChangeEmailForm() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var sentTo by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("We send a link to the new address. Your email changes only when you open it.", style = MaterialTheme.typography.bodyMedium, color = SnowSoft)
        sentTo?.let { Text("A link is on its way to $it. It works for 24 hours.", style = MaterialTheme.typography.bodyMedium, color = Gold) }
        ErrorText(error)
        Field(email, { email = it }, "New email", keyboard = KeyboardType.Email, enabled = !busy)
        GoldButton(if (busy) "Sending…" else "Send link", Modifier.fillMaxWidth(), enabled = !busy && email.isNotBlank()) {
            busy = true
            error = null
            scope.launch {
                try {
                    app.api.post("/api/me/email", Ok.serializer()) { put("email", email.trim()) }
                    sentTo = email.trim().lowercase()
                    email = ""
                } catch (e: Exception) {
                    error = e.message ?: "Could not send the link."
                } finally {
                    busy = false
                }
            }
        }
    }
}
