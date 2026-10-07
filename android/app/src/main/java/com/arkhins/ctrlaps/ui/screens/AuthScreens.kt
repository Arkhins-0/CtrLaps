package com.arkhins.ctrlaps.ui.screens

import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.autofill.ContentType
import com.arkhins.ctrlaps.ui.theme.OnGold
import com.arkhins.ctrlaps.ui.theme.Night
import com.arkhins.ctrlaps.ui.theme.Gold
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.Config
import com.arkhins.ctrlaps.LocalApp
import com.arkhins.ctrlaps.data.ApiException
import com.arkhins.ctrlaps.R
import com.arkhins.ctrlaps.data.InviteInfo
import com.arkhins.ctrlaps.data.LoginResponse
import com.arkhins.ctrlaps.data.Ok
import com.arkhins.ctrlaps.data.ResetInfo
import com.arkhins.ctrlaps.ui.components.ErrorText
import com.arkhins.ctrlaps.ui.components.Field
import com.arkhins.ctrlaps.ui.components.GhostButton
import com.arkhins.ctrlaps.ui.components.GoldButton
import com.arkhins.ctrlaps.ui.components.Loading
import com.arkhins.ctrlaps.ui.components.Panel
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import com.arkhins.ctrlaps.ui.theme.SnowSoft
import kotlinx.coroutines.launch
import kotlinx.serialization.json.put

/** The frame for every signed-out screen: the mark, the name, one panel. */
@Composable
fun AuthFrame(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ctr_logo), contentDescription = null, modifier = Modifier.width(72.dp))
            Spacer(Modifier.width(12.dp))
            Text(Config.APP_NAME, style = MaterialTheme.typography.headlineMedium, color = Snow)
        }
        Spacer(Modifier.height(28.dp))
        Panel {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Snow)
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}

@Composable
fun LoginScreen(onSignedIn: (String) -> Unit, onForgot: () -> Unit, onRegister: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // The phone's password manager: it fills the two fields, and offers to save them once signing in works.
    val autofill = LocalAutofillManager.current

    AuthFrame("Sign in") {
        ErrorText(error)
        if (error != null) Spacer(Modifier.height(10.dp))
        Field(email, { email = it }, "Email", keyboard = KeyboardType.Email, enabled = !busy, autofill = ContentType.Username + ContentType.EmailAddress)
        Spacer(Modifier.height(10.dp))
        Field(password, { password = it }, "Password", password = true, enabled = !busy, autofill = ContentType.Password)
        Spacer(Modifier.height(16.dp))
        GoldButton(if (busy) "Signing in…" else "Sign in", Modifier.fillMaxWidth(), enabled = !busy && email.isNotBlank() && password.isNotBlank()) {
            busy = true
            error = null
            scope.launch {
                try {
                    val r = app.api.login(email.trim(), password)
                    val token = r.token ?: throw IllegalStateException("No session returned.")
                    // Signed in: the moment Android offers "Save password?".
                    autofill?.commit()
                    onSignedIn(token)
                } catch (e: Exception) {
                    // A banned account: nothing it left on this phone stays.
                    if ((e as? ApiException)?.reason == "banned") {
                        app.chatCache.wipe()
                        app.chatMedia.wipe()
                    }
                    error = e.message ?: "Could not sign in."
                    busy = false
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        GhostButton("Create an account", Modifier.fillMaxWidth(), enabled = !busy, onClick = onRegister)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Forgot password", style = MaterialTheme.typography.labelMedium, color = SnowFaint, modifier = Modifier.clickable { onForgot() })
            // Help before signing in: the FAQs and the support form, on the website.
            val context = androidx.compose.ui.platform.LocalContext.current
            Text(
                "Need help?",
                style = MaterialTheme.typography.labelMedium,
                color = SnowFaint,
                modifier = Modifier.clickable {
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("${com.arkhins.ctrlaps.Config.BASE_URL}/help"))) }
                },
            )
        }
    }
}

/** Registering, step one: the email, which gets a link to confirm it before the account is made. */
@Composable
fun RegisterScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AuthFrame("Create an account") {
        if (sent) {
            Text("We sent a link to ${email.trim()}. Open it to confirm your email and create your account. It works for 24 hours.", color = SnowSoft, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text("Nothing arrived? Check your spam folder, or try again in a few minutes.", color = SnowFaint, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(16.dp))
            GhostButton("Back to sign in", Modifier.fillMaxWidth(), onClick = onBack)
        } else {
            Text("Enter your email. We'll send a link to confirm it before your account is created.", color = SnowSoft, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            ErrorText(error)
            if (error != null) Spacer(Modifier.height(10.dp))
            Field(email, { email = it }, "Email", keyboard = KeyboardType.Email, enabled = !busy)
            Spacer(Modifier.height(16.dp))
            GoldButton(if (busy) "Sending…" else "Send the link", Modifier.fillMaxWidth(), enabled = !busy && email.isNotBlank()) {
                busy = true
                error = null
                scope.launch {
                    try {
                        app.api.post("/api/auth/register", Ok.serializer()) { put("email", email.trim()) }
                        sent = true
                    } catch (e: Exception) {
                        error = e.message ?: "Could not send the link."
                    }
                    busy = false
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Already have an account? Sign in", style = MaterialTheme.typography.labelMedium, color = SnowFaint, modifier = Modifier.clickable { onBack() })
        }
    }
}

@Composable
fun ForgotScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    AuthFrame("Forgot password") {
        if (sent) {
            Text("If that email has an account, a link to choose a new password is on its way. It works for 2 hours.", color = SnowSoft, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            GhostButton("Back to sign in", Modifier.fillMaxWidth(), onClick = onBack)
        } else {
            Field(email, { email = it }, "Email", keyboard = KeyboardType.Email, enabled = !busy)
            Spacer(Modifier.height(16.dp))
            GoldButton(if (busy) "Sending…" else "Email me a link", Modifier.fillMaxWidth(), enabled = !busy && email.isNotBlank()) {
                busy = true
                scope.launch {
                    runCatching { app.api.post("/api/auth/forgot", Ok.serializer()) { put("email", email.trim()) } }
                    sent = true
                    busy = false
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Back to sign in", style = MaterialTheme.typography.labelMedium, color = SnowFaint, modifier = Modifier.clickable { onBack() })
        }
    }
}

/** The invite and registration links (choose a first password, account made) and the reset link (choose a new one). */
@Composable
fun SetPasswordScreen(kind: String, token: String, onSignedIn: (String) -> Unit, onDone: () -> Unit, onLegal: (String) -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val invite = kind == "invite"
    val register = kind == "register"
    val firstPassword = invite || register
    var email by remember { mutableStateOf<String?>(null) }
    var roleLabel by remember { mutableStateOf("") }
    var loadError by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var agreed by remember { mutableStateOf(false) }

    LaunchedEffect(token) {
        try {
            if (invite) {
                val info = app.api.get("/api/auth/invite/$token", InviteInfo.serializer())
                email = info.email
                roleLabel = info.roleLabel
            } else if (register) {
                email = app.api.get("/api/auth/register/$token", ResetInfo.serializer()).email
            } else {
                email = app.api.get("/api/auth/reset/$token", ResetInfo.serializer()).email
            }
        } catch (e: Exception) {
            loadError = e.message ?: "This link is no longer valid."
        }
    }

    AuthFrame(if (invite) "Set up your account" else if (register) "Create your account" else "Choose a new password") {
        when {
            loadError != null -> {
                ErrorText(loadError)
                Spacer(Modifier.height(12.dp))
                GhostButton("Back", Modifier.fillMaxWidth(), onClick = onDone)
            }
            done -> {
                Text("Your password is changed. Sign in with it.", color = SnowSoft)
                Spacer(Modifier.height(12.dp))
                GoldButton("Sign in", Modifier.fillMaxWidth(), onClick = onDone)
            }
            email == null -> Loading()
            else -> {
                val autofill = LocalAutofillManager.current
                Text(if (invite) "$email · $roleLabel" else if (register) "$email is confirmed. Choose a password." else email!!, color = SnowSoft, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                ErrorText(error)
                if (error != null) Spacer(Modifier.height(10.dp))
                Field(password, { password = it }, "New password", password = true, enabled = !busy, autofill = ContentType.NewPassword)
                Text("At least 8 characters.", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                Spacer(Modifier.height(10.dp))
                Field(again, { again = it }, "Repeat password", password = true, enabled = !busy, autofill = ContentType.NewPassword)
                Spacer(Modifier.height(16.dp))
                if (firstPassword) {
                    Agreement(agreed, enabled = !busy, onLegal = onLegal) { agreed = it }
                    Spacer(Modifier.height(12.dp))
                }
                GoldButton(
                    if (busy) "Saving…" else if (firstPassword) "Create account" else "Save password",
                    Modifier.fillMaxWidth(),
                    enabled = !busy && password.length >= 8 && (!firstPassword || agreed),
                ) {
                    if (password != again) {
                        error = "The passwords do not match."
                        return@GoldButton
                    }
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            if (firstPassword) {
                                val r = app.api.post("/api/auth/$kind/$token", LoginResponse.serializer()) {
                                    put("password", password)
                                    put("platform", "android")
                                    put("acceptTerms", true)
                                }
                                val session = r.token ?: throw IllegalStateException("No session returned.")
                                autofill?.commit()
                                onSignedIn(session)
                            } else {
                                app.api.post("/api/auth/reset/$token", Ok.serializer()) { put("password", password) }
                                autofill?.commit()
                                done = true
                            }
                        } catch (e: Exception) {
                            error = e.message ?: "Could not save."
                            busy = false
                        }
                    }
                }
            }
        }
    }
}

/** "I agree to the Terms and Conditions and the Privacy Policy", one box, both documents linked. */
@Composable
private fun Agreement(checked: Boolean, enabled: Boolean, onLegal: (String) -> Unit, onChange: (Boolean) -> Unit) {
    val links = TextLinkStyles(SpanStyle(color = Gold, textDecoration = TextDecoration.Underline))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = Gold, checkmarkColor = OnGold, uncheckedColor = SnowFaint),
        )
        Text(
            buildAnnotatedString {
                append("I agree to the ")
                withLink(LinkAnnotation.Clickable("terms", links) { onLegal("terms") }) { append("Terms and Conditions") }
                append(" and the ")
                withLink(LinkAnnotation.Clickable("privacy", links) { onLegal("privacy") }) { append("Privacy Policy") }
                append(".")
            },
            style = MaterialTheme.typography.bodySmall,
            color = SnowSoft,
        )
    }
}
