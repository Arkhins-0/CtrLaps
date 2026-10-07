package com.arkhins.ctrlaps.ui.screens

import com.arkhins.ctrlaps.ui.components.Snack
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.LocalApp
import com.arkhins.ctrlaps.data.Ok
import com.arkhins.ctrlaps.ui.components.ErrorText
import com.arkhins.ctrlaps.ui.components.Field
import com.arkhins.ctrlaps.ui.components.GhostButton
import com.arkhins.ctrlaps.ui.components.Panel
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import com.arkhins.ctrlaps.ui.theme.SnowSoft
import kotlinx.coroutines.launch
import kotlinx.serialization.json.put

/** What deleting does, in a few lines (the Privacy Policy has the rest); also on a developer's confirm. */
val DELETION_POINTS = listOf(
    "You are signed out everywhere now. The account is deleted after 7 days; signing in again before then cancels it.",
    "Your profile, photo, email choices, follows, votes and support tickets are erased.",
    "Messages, photos and documents you sent in chats and groups stay for the people you sent them to, from \"Deleted user\".",
    "Published race results keep the name as it was published.",
)

/** Settings → Delete account: the password and DELETE typed out, then signed out with this phone's copies cleared. */
@Composable
fun DeleteAccountScreen(onDeleted: () -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Delete your account and the details we hold about you.", style = MaterialTheme.typography.titleSmall, color = Snow)
                DELETION_POINTS.forEach { Text("•  $it", style = MaterialTheme.typography.bodySmall, color = SnowSoft) }
            }
        }
        ErrorText(error)
        Field(password, { password = it }, "Password", password = true)
        Field(confirm, { confirm = it }, "Type DELETE to confirm")
        GhostButton(
            if (busy) "Deleting…" else "Delete my account",
            Modifier.fillMaxWidth(),
            enabled = !busy && password.isNotEmpty() && confirm.trim().equals("DELETE", ignoreCase = true),
            danger = true,
        ) {
            busy = true
            error = null
            scope.launch {
                try {
                    app.api.post("/api/me/delete", Ok.serializer()) {
                        put("password", password)
                        put("confirm", confirm)
                    }
                    Snack.show("Your account will be deleted in 7 days. Sign in before then to keep it.")
                    onDeleted()
                } catch (e: Exception) {
                    error = e.message ?: "Could not delete."
                    busy = false
                }
            }
        }
        Text("Copies already on other people's phones or in emails they received are theirs; we can't reach them.", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
    }
}
