package com.arkhins.ctrlaps.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.LocalApp
import com.arkhins.ctrlaps.data.CategoryChannelResponse
import com.arkhins.ctrlaps.data.IdResponse
import com.arkhins.ctrlaps.ui.AppViewModel
import com.arkhins.ctrlaps.ui.components.Composer
import com.arkhins.ctrlaps.ui.components.Empty
import com.arkhins.ctrlaps.ui.components.ErrorText
import com.arkhins.ctrlaps.ui.components.FileView
import com.arkhins.ctrlaps.ui.components.Loading
import com.arkhins.ctrlaps.ui.components.MessageCard
import com.arkhins.ctrlaps.ui.components.photoRuns
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import com.arkhins.ctrlaps.ui.theme.SnowSoft
import kotlinx.coroutines.delay
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** A race category's channel ("ITC 2026"): its people read; admins, coordinators and its race officials post. */
@Composable
fun CategoryChannelScreen(vm: AppViewModel, categoryId: String, onView: (FileView) -> Unit) {
    val app = LocalApp.current
    var channel by remember { mutableStateOf<CategoryChannelResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(categoryId, reload, vm.refreshTick) {
        try {
            channel = app.store.get("/api/categories/$categoryId/channel", CategoryChannelResponse.serializer()) { if (channel == null) channel = it }
            error = null
        } catch (e: Exception) {
            if (channel == null) error = e.message
        }
    }
    LaunchedEffect(categoryId) {
        while (true) {
            delay(20_000)
            reload++
        }
    }

    val c = channel
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            error != null && c == null -> item { ErrorText(error) }
            c == null -> item { Loading() }
            else -> {
                item {
                    val color = runCatching { Color(android.graphics.Color.parseColor(c.category.color)) }.getOrDefault(SnowSoft)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(44.dp).height(44.dp).background(color.copy(alpha = 0.15f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Text(c.category.code.take(5), style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.category.name, style = MaterialTheme.typography.titleMedium, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Category channel · ${c.category.seasonName}", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                        }
                    }
                }
                if (c.canPost) {
                    item {
                        Composer(placeholder = "Post to everyone in ${c.category.code}", sendLabel = "Post", voiceNoteSends = false) { d ->
                            app.api.post("/api/categories/$categoryId/channel", IdResponse.serializer()) {
                                put("body", d.body)
                                d.link?.let { put("linkUrl", it.url) }
                                putJsonArray("fileIds") { d.fileIds.forEach { add(it) } }
                                put("urgent", d.urgent)
                            }
                            reload++
                        }
                    }
                }
                if (!c.open) item {
                    Text("This channel is closed: its season is archived.", style = MaterialTheme.typography.labelSmall, color = SnowFaint, modifier = Modifier.padding(vertical = 2.dp))
                }
                if (c.messages.isEmpty()) item { Empty("No posts yet.") }
                // Photos posted one after another show as one grid; the newest post on top.
                else items(photoRuns(c.messages).asReversed(), key = { it.first().id }) { run -> MessageCard(run, onView) }
            }
        }
    }
}
