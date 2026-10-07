package com.arkhins.ctrlaps.ui.screens

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

/** The app's version and updates, what's new, the Terms, Privacy Policy and License, and Support. */
@Composable
fun AboutScreen(vm: AppViewModel, onChangelog: () -> Unit, onLegal: (String) -> Unit, onSupport: () -> Unit, onLicense: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UpdatePanel(vm)
        Panel {
            Column {
                MenuRow("What's new", "Changes in each version", icon = rememberVectorPainter(Icons.Outlined.Star), onClick = onChangelog)
                HorizontalDivider(color = SnowFaint.copy(alpha = 0.15f))
                MenuRow("Terms and conditions", "The rules for using CTR[L]APS", icon = painterResource(R.drawable.ic_document)) { onLegal("terms") }
                HorizontalDivider(color = SnowFaint.copy(alpha = 0.15f))
                MenuRow("Privacy Policy", "What CTR[L]APS keeps and why", icon = rememberVectorPainter(Icons.Outlined.Lock)) { onLegal("privacy") }
                HorizontalDivider(color = SnowFaint.copy(alpha = 0.15f))
                MenuRow("License", listOfNotNull("Apache License 2.0", Config.POWERED_BY_NAME.takeIf { it.isNotBlank() }?.let { "© 2026 $it" }).joinToString(" · "), icon = rememberVectorPainter(Icons.Outlined.Info), onClick = onLicense)
            }
        }
        // Support: FAQs, the support form and tickets.
        Panel {
            val unread = vm.me?.unreadSupport ?: 0
            MenuRow(
                "Support",
                if (unread > 0) "$unread new ${if (unread == 1) "reply" else "replies"}" else if (vm.me?.isDev == true) "Tickets and FAQs" else "FAQs, the support form and your tickets",
                highlight = unread > 0,
                icon = rememberVectorPainter(Icons.Outlined.Call),
                onClick = onSupport,
            )
        }
        // A debug build says so, so it can't be mistaken for the release (same name, icon and version).
        Text("CTR[L]APS v${BuildConfig.VERSION_NAME}${if (BuildConfig.DEBUG) " · Debug" else ""}", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
    }
}

/** The version and the in-app update: tap to check again. */
@Composable
private fun UpdatePanel(vm: AppViewModel) {
    Panel(Modifier.clickable(enabled = !vm.checkingUpdate) { vm.checkForUpdate(force = true) }) {
        Column(Modifier.fillMaxWidth()) {
            Text("App version", style = MaterialTheme.typography.titleMedium, color = Snow)
            Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.headlineSmall, color = Snow)
            Spacer(Modifier.height(4.dp))
            val info = vm.updateInfo
            Text(
                when {
                    vm.checkingUpdate -> "Checking for updates…"
                    vm.updateCheckError != null -> vm.updateCheckError!!
                    info != null -> "v${info.version} is available."
                    vm.checkedOnce && vm.noReleaseYet -> "No release has been published yet. Tap to check again."
                    vm.checkedOnce -> "Up to date. Tap to check again."
                    else -> "Tap to check for updates."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (info != null) Gold else SnowFaint,
            )
            if (info != null && !vm.checkingUpdate) {
                Spacer(Modifier.height(10.dp))
                GoldButton("Update app") { vm.showUpdate() }
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
