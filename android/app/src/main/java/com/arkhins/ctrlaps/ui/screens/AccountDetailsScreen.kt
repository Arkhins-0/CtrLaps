package com.arkhins.ctrlaps.ui.screens

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
    var showPassword by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Panel {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyValue("Name", u.displayName)
                KeyValue("Email", u.email)
                KeyValue("Contact", u.phone ?: "—")
                KeyValue("Date of birth", u.dob ?: "—")
                KeyValue("Role", u.roleLabel + (u.teamName?.let { " · $it" } ?: ""))
                me.parent?.let { KeyValue("Reports to", "${it.name} · ${it.roleLabel}") }
                if (u.role != "user" && u.role != "admin") {
                    Spacer(Modifier.height(4.dp))
                    Text("Profile details are locked. Your manager or an admin can change them.", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                }
            }
        }

        // Users (no role yet) and admins, who have no manager above them, edit their own details.
        if (u.role == "user" || u.role == "admin") {
            EditProfilePanel(vm)
            ChangeEmailPanel()
        }

        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Password", style = MaterialTheme.typography.titleMedium, color = Snow)
                if (showPassword) ChangePasswordForm { showPassword = false }
                else GhostButton("Change password") { showPassword = true }
            }
        }

        ForgotPasswordPanel(u.email)
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
private fun ForgotPasswordPanel(email: String) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Forgot password", style = MaterialTheme.typography.titleMedium, color = Snow)
            Text(
                if (sent) "A link to choose a new password is on its way to $email. It works for 2 hours."
                else "Don't know your current password? Get a link at $email to choose a new one.",
                style = MaterialTheme.typography.bodySmall,
                color = SnowFaint,
            )
            ErrorText(error)
            if (!sent) GhostButton(if (busy) "Sending…" else "Email me a link", enabled = !busy) {
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
}

/** Someone with no role yet changes their own name, date of birth, contact number and photo. */
@Composable
private fun EditProfilePanel(vm: AppViewModel) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val u = vm.me?.user ?: return
    var editing by remember { mutableStateOf(false) }
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
            Text("Your details", style = MaterialTheme.typography.titleMedium, color = Snow)
            if (!editing) {
                Text(if (u.role == "admin") "Your name, contact, date of birth and photo." else "You can change your details until an organiser gives you a role.", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
                GhostButton("Edit profile") { editing = true }
                return@Column
            }
            ErrorText(error)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(72.dp).clip(CircleShape).clickable(enabled = !busy) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    val bmp = photo
                    if (bmp != null) Image(bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp))
                    else Avatar(app.api.absolute(u.photoUrl), u.displayName, 72)
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
                        editing = false
                        photo = null
                        vm.refreshMe()
                    } catch (e: Exception) {
                        error = e.message ?: "Could not save."
                    } finally {
                        file?.delete()
                        busy = false
                    }
                }
            }
            GhostButton("Cancel", enabled = !busy) {
                editing = false
                photo = null
                error = null
                name = u.name ?: ""
                dob = u.dob ?: ""
                phone = u.phone ?: ""
            }
        }
    }
}

/** A new email for someone with no role yet: a link goes to it, and the change happens when it is opened. */
@Composable
private fun ChangeEmailPanel() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var sentTo by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Change email", style = MaterialTheme.typography.titleMedium, color = Snow)
            Text("We send a link to the new address. Your email changes only when you open it.", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
            sentTo?.let { Text("A link is on its way to $it. It works for 24 hours.", style = MaterialTheme.typography.bodySmall, color = SnowSoft) }
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
}
