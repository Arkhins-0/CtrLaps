package com.arkhins.ctrlaps.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.arkhins.ctrlaps.ui.screens.AboutSheet
import com.arkhins.ctrlaps.ui.theme.Danger
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.NightHigh
import com.arkhins.ctrlaps.ui.theme.NightPanel
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import com.arkhins.ctrlaps.ui.theme.SnowSoft

/**
 * A photo pulled up: the name, the photo large (square, or 16:9 for a weekend), and Close. Those who may change it
 * also get Change (the gallery) and Remove (with Undo on the message bar) under it.
 */
@Composable
fun PhotoPreviewHost() {
    val view = PhotoPreview.shown.value ?: return
    val close = { PhotoPreview.shown.value = null }
    AboutSheet(view.name, onClose = close, expanded = true) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            AsyncImage(
                model = view.url,
                contentDescription = "${view.name}'s photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(if (view.wide) 16f / 9f else 1f).clip(RoundedCornerShape(24.dp)),
            )
            if (view.onChange != null || view.onRemove != null) {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    view.onChange?.let { change ->
                        PhotoAction(Icons.Outlined.Edit, "Change", Gold, Modifier.weight(1f)) {
                            close()
                            change()
                        }
                    }
                    view.onRemove?.let { remove ->
                        // Removed when the message bar goes, unless Undo is tapped.
                        PhotoAction(Icons.Outlined.Delete, "Remove", Danger, Modifier.weight(1f)) {
                            close()
                            Snack.undo("Photo removed") { remove() }
                        }
                    }
                }
            }
        }
    }
}

/** An icon over its word, on a soft tile: Change, Remove. */
@Composable
private fun PhotoAction(icon: ImageVector, label: String, tone: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(NightHigh).clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (tone == Danger) Danger else Snow)
    }
}
