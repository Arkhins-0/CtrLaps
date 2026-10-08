package com.arkhins.ctrlaps.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.LocalApp
import com.arkhins.ctrlaps.data.EmailSettings
import com.arkhins.ctrlaps.ui.components.Chip
import com.arkhins.ctrlaps.ui.components.ErrorText
import com.arkhins.ctrlaps.ui.components.Loading
import com.arkhins.ctrlaps.ui.components.Panel
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.OnGold
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import com.arkhins.ctrlaps.ui.theme.SnowSoft
import kotlinx.coroutines.launch
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Settings → Email: which emails this account gets — a switch per kind, and the race categories whose results come
 * (your own to start). Each change saves at once; account and security mails, and the organisers' Email page, always come.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmailSettingsScreen() {
    val haptics = com.arkhins.ctrlaps.ui.components.rememberHaptics()
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var s by remember { mutableStateOf<EmailSettings?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            s = app.store.get("/api/me/email-settings", EmailSettings.serializer()) { if (s == null) s = it }
        } catch (e: Exception) {
            if (s == null) error = e.message
        }
    }
    fun save(kind: String? = null, on: Boolean, category: String? = null) {
        error = null
        scope.launch {
            try {
                s = app.api.put("/api/me/email-settings", EmailSettings.serializer()) {
                    if (kind != null) putJsonObject("kinds") { put(kind, on) }
                    if (category != null) putJsonObject("results") { put(category, on) }
                }
            } catch (e: Exception) {
                error = e.message ?: "Could not save."
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Choose what comes by email. Everything still shows in the app.", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
        ErrorText(error)
        val d = s
        when {
            d == null && error == null -> Loading()
            d != null && !d.automatic -> Panel { Text("Your role gets no automatic email: your coordinator forwards what matters.", style = MaterialTheme.typography.bodyMedium, color = SnowSoft) }
            d != null -> {
                Panel {
                    Column {
                        d.kinds.forEachIndexed { i, k ->
                            if (i > 0) HorizontalDivider(color = SnowFaint.copy(alpha = 0.15f))
                            Column(Modifier.padding(vertical = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(k.label, style = MaterialTheme.typography.titleSmall, color = Snow)
                                        Text(k.hint, style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                                    }
                                    Switch(
                                        checked = k.on,
                                        onCheckedChange = { haptics.toggle(it); save(kind = k.key, on = it) },
                                        colors = SwitchDefaults.colors(checkedThumbColor = OnGold, checkedTrackColor = Gold),
                                    )
                                }
                                // Results: which categories (your own are on to start).
                                if (k.key == "results" && k.on && d.results.isNotEmpty()) {
                                    FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        d.results.forEach { c ->
                                            Chip(c.code, categoryColor(c.color), filled = c.on) { save(on = !c.on, category = c.id) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Text("Always sent: account and security emails, and notices from the organisers' Email page.", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
            }
        }
    }
}

/** A category colour from its hex. */
private fun categoryColor(hex: String) = runCatching { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Gold)
