package com.arkhins.ctrlaps.ui.screens

import androidx.compose.runtime.collectAsState
import kotlinx.serialization.Serializable
import com.arkhins.ctrlaps.ui.theme.NightHigh
import com.arkhins.ctrlaps.ui.theme.Night
import com.arkhins.ctrlaps.ui.openSafely
import com.arkhins.ctrlaps.ui.components.Loading
import com.arkhins.ctrlaps.ui.components.ErrorText
import com.arkhins.ctrlaps.ui.components.Avatar
import com.arkhins.ctrlaps.LocalApp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import com.arkhins.ctrlaps.R
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.BuildConfig
import com.arkhins.ctrlaps.Config
import com.arkhins.ctrlaps.ui.AppViewModel
import com.arkhins.ctrlaps.ui.components.GoldButton
import com.arkhins.ctrlaps.ui.components.Panel
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import com.arkhins.ctrlaps.ui.theme.SnowSoft

/** One person behind the app (About → Developers / Helpers), set only in the database. */
@Serializable
data class Credit(val id: String, val name: String, val subtext: String = "", val link: String? = null, val photoUrl: String? = null)

@Serializable
data class CreditsResponse(val credits: List<Credit> = emptyList())

/** The About page's own top: only a back arrow, as the page's big title is the heading. */
@Composable
fun BackOnlyBar(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Snow) }
    }
}

/**
 * About, after Arkhime's: a big title, then the update check, what's new, the people behind the app, the Terms, the
 * Privacy Policy and the License (all but the first two in sheets). Help (FAQs, support) is its own row on the
 * Account tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(vm: AppViewModel, onChangelog: () -> Unit, onLegal: (String) -> Unit, onSupport: () -> Unit, onLicense: () -> Unit, onFaqs: () -> Unit = onSupport) {
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    // Debug builds: `--es sheet people|terms|privacy` opens that sheet over adb (see DebugHooks).
    if (BuildConfig.DEBUG) {
        val asked by com.arkhins.ctrlaps.ui.DebugHooks.sheet.collectAsState()
        LaunchedEffect(asked) {
            if (asked in setOf("people", "terms", "privacy", "license")) { sheet = asked; com.arkhins.ctrlaps.ui.DebugHooks.sheet.value = null }
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        val info = vm.updateInfo
        MenuRow(
            "Check for updates",
            when {
                vm.checkingUpdate -> "Checking…"
                vm.updateCheckError != null -> vm.updateCheckError!!
                info != null -> "v${info.version} is available: tap to update"
                vm.checkedOnce && vm.noReleaseYet -> "No release published yet"
                vm.checkedOnce -> "v${BuildConfig.VERSION_NAME} is up to date"
                else -> "You have v${BuildConfig.VERSION_NAME}. Tap to check"
            },
            highlight = info != null,
            icon = rememberVectorPainter(Icons.Outlined.Refresh),
            arrow = false,
        ) { if (info != null) vm.showUpdate() else if (!vm.checkingUpdate) vm.checkForUpdate(force = true) }
        MenuRow("What's new", "Changes in each version", icon = rememberVectorPainter(Icons.Outlined.Star), onClick = onChangelog)
        MenuRow("Developers / Helpers", "The people behind CTR[L]APS", icon = painterResource(R.drawable.ic_tab_people), arrow = false) { sheet = "people" }
        MenuRow("Terms and conditions", "The rules for using CTR[L]APS", icon = painterResource(R.drawable.ic_document), arrow = false) { sheet = "terms" }
        MenuRow("Privacy Policy", "What CTR[L]APS keeps and why", icon = rememberVectorPainter(Icons.Outlined.Lock), arrow = false) { sheet = "privacy" }
        MenuRow(
            "License",
            listOfNotNull("Apache License 2.0", Config.POWERED_BY_NAME.takeIf { it.isNotBlank() }?.let { "© 2026 $it" }).joinToString(" · "),
            icon = rememberVectorPainter(Icons.Outlined.Info),
            arrow = false,
        ) { sheet = "license" }
        // A debug build says so, so it can't be mistaken for the release (same name, icon and version).
        Text(
            "CTR[L]APS v${BuildConfig.VERSION_NAME}${if (BuildConfig.DEBUG) " · Debug" else ""}",
            style = MaterialTheme.typography.labelSmall,
            color = SnowFaint,
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            textAlign = TextAlign.Center,
        )
    }

    when (val s = sheet) {
        "people" -> AboutSheet("Developers / Helpers", onClose = { sheet = null }) { PeopleList() }
        "terms", "privacy" -> AboutSheet(if (s == "privacy") "Privacy Policy" else "Terms and conditions", onClose = { sheet = null }, tall = true) {
            // A link inside one opens the other here, in the same sheet.
            LegalScreen(s, onOpen = { doc -> sheet = doc })
        }
        "license" -> AboutSheet("License", onClose = { sheet = null }, tall = true) { LicenseScreen() }
        else -> Unit
    }
}

/** A sheet from About: its title, what it holds, and Close. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(title: String, onClose: () -> Unit, tall: Boolean = false, content: @Composable () -> Unit) {
    // A long one opens halfway and, scrolled, rises all the way to the top before its text scrolls.
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Night, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.fillMaxWidth().then(if (tall) Modifier.fillMaxHeight() else Modifier).navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Snow, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
            Box(Modifier.fillMaxWidth().weight(1f, fill = tall)) { content() }
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(52.dp),
                border = BorderStroke(1.dp, Gold.copy(alpha = 0.6f)),
            ) { Text("Close", color = Gold, fontWeight = FontWeight.Bold) }
        }
    }
}

/** The people behind the app, as cards: photo, name in the accent, what they do; a tap opens their link. */
@Composable
private fun PeopleList() {
    val app = LocalApp.current
    val uri = LocalUriHandler.current
    var people by remember { mutableStateOf<List<Credit>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            people = app.store.get("/api/credits", CreditsResponse.serializer()) { people = it.credits }.credits
        } catch (e: Exception) {
            if (people == null) error = e.message ?: "Could not load this."
        }
    }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val list = people
        when {
            list == null && error != null -> ErrorText(error)
            list == null -> Loading()
            list.isEmpty() -> Text("Nobody listed yet.", style = MaterialTheme.typography.bodyMedium, color = SnowFaint)
            else -> list.forEach { c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(NightHigh, RoundedCornerShape(16.dp))
                        .clickable(enabled = c.link != null) { c.link?.let { uri.openSafely(it) } }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(c.photoUrl, c.name, 56)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.titleSmall.copy(fontSize = 17.sp), fontWeight = FontWeight.Bold, color = Gold)
                        if (c.subtext.isNotBlank()) Text(c.subtext, style = MaterialTheme.typography.bodyMedium, color = Snow)
                    }
                }
            }
        }
    }
}

/** The app's license text, from the repository's LICENSE (copied into the build: res/raw). */
@Composable
private fun legalText(raw: Int): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember(raw) {
        runCatching { context.resources.openRawResource(raw).bufferedReader().use { it.readText() } }.getOrDefault("")
    }
}

/** Who made it and the domain, from the build's settings; blank ones are left out. */
@Composable
private fun OwnerPanel(summary: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("CTR[L]APS", style = MaterialTheme.typography.titleMedium, color = Snow)
            if (Config.POWERED_BY_NAME.isNotBlank()) Text("Copyright 2026 ${Config.POWERED_BY_NAME}", style = MaterialTheme.typography.bodyMedium, color = Snow)
            if (Config.POWERED_BY_DOMAIN.isNotBlank()) {
                Text(
                    Config.POWERED_BY_DOMAIN,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gold,
                    modifier = Modifier.clickable {
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://${Config.POWERED_BY_DOMAIN}"))) }
                    },
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(summary, style = MaterialTheme.typography.bodySmall, color = SnowFaint)
        }
    }
}

/** The app's license: who holds the copyright, and the Apache License 2.0 (the repository's LICENSE). */
@Composable
fun LicenseScreen() {
    val text = legalText(com.arkhins.ctrlaps.R.raw.license)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OwnerPanel("Licensed under the Apache License, Version 2.0. You may use, copy, change and share it under the terms below; it comes with no warranty. The libraries it uses keep their own licenses.")
        // The file is wrapped for a wide screen: each paragraph is joined into one line so it flows to the phone's width.
        // Only the terms: the appendix after them is a template for source files, with blanks to fill in.
        val paragraphs = androidx.compose.runtime.remember(text) {
            val end = "END OF TERMS AND CONDITIONS"
            val terms = text.indexOf(end).let { if (it >= 0) text.substring(0, it + end.length) else text }
            terms.trim().split(Regex("\\n\\s*\\n")).map { p -> p.lines().joinToString(" ") { it.trim() } }.filter { it.isNotBlank() }
        }
        paragraphs.forEach { p ->
            val heading = p.length < 70 && !p.endsWith(".")
            Text(
                p,
                style = if (heading) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
                color = if (heading) Snow else SnowSoft,
            )
        }
    }
}
