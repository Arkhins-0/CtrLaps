package com.arkhins.ctrlaps.ui.screens

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

/** The app's version and updates, what's new, the Terms and Privacy Policy, and Support. */
@Composable
fun AboutScreen(vm: AppViewModel, onChangelog: () -> Unit, onLegal: (String) -> Unit, onSupport: () -> Unit) {
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
                MenuRow("What's new", "Changes in each version", onClick = onChangelog)
                HorizontalDivider(color = SnowFaint.copy(alpha = 0.15f))
                MenuRow("Terms and conditions", "The rules for using CTR[L]APS") { onLegal("terms") }
                HorizontalDivider(color = SnowFaint.copy(alpha = 0.15f))
                MenuRow("Privacy Policy", "What CTR[L]APS keeps and why") { onLegal("privacy") }
            }
        }
        // Support: FAQs, the support form and tickets.
        Panel {
            val unread = vm.me?.unreadSupport ?: 0
            MenuRow(
                "Support",
                if (unread > 0) "$unread new ${if (unread == 1) "reply" else "replies"}" else if (vm.me?.isDev == true) "Tickets and FAQs" else "FAQs, the support form and your tickets",
                highlight = unread > 0,
                onClick = onSupport,
            )
        }
        Text("CTR[L]APS v${BuildConfig.VERSION_NAME} · ${Config.POWERED_BY_NAME}", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
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
