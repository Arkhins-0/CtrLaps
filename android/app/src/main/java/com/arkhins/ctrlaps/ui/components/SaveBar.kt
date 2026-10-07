package com.arkhins.ctrlaps.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.NightLine
import com.arkhins.ctrlaps.ui.theme.NightPanel
import com.arkhins.ctrlaps.ui.theme.SnowSoft

/** How far a save of several changes has got: [done] of [total]. */
data class SaveProgress(val done: Int, val total: Int)

/**
 * The bar for changes waiting to be saved, docked at the bottom of a screen: "3 changes" with Discard and Save, then
 * "Saving 2 of 3…" with a line filling as they go. It slides away when nothing waits.
 */
@Composable
fun SaveBar(count: Int, progress: SaveProgress?, onSave: () -> Unit, onDiscard: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = count > 0 || progress != null,
        modifier = modifier,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
    ) {
        Column(Modifier.fillMaxWidth().background(NightPanel).navigationBarsPadding()) {
            if (progress != null && progress.total > 0) {
                LinearProgressIndicator(
                    progress = { progress.done.toFloat() / progress.total },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = Gold,
                    trackColor = NightLine,
                    drawStopIndicator = {},
                )
            } else {
                Row(Modifier.fillMaxWidth().height(3.dp).background(Gold)) {}
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    buildAnnotatedString {
                        if (progress != null) {
                            append("Saving ")
                            withStyle(SpanStyle(color = Gold, fontWeight = FontWeight.SemiBold)) { append("${minOf(progress.done + 1, progress.total)}") }
                            append(" of ${progress.total}…")
                        } else {
                            withStyle(SpanStyle(color = Gold, fontWeight = FontWeight.Bold)) { append("$count") }
                            append(if (count == 1) " change" else " changes")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = SnowSoft,
                    modifier = Modifier.weight(1f),
                )
                GhostButton("Discard", enabled = progress == null, onClick = onDiscard)
                GoldButton(if (progress != null) "Saving…" else "Save", enabled = progress == null && count > 0, onClick = onSave)
            }
        }
    }
}
