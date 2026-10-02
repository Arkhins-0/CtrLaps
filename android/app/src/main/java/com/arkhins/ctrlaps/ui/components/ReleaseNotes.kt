package com.arkhins.ctrlaps.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.data.NoteSection
import com.arkhins.ctrlaps.data.forRole
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowSoft

/**
 * A version's notes as [role] should see them: each heading (New, Improved, Fixed) and its lines, leaving out lines
 * meant for other roles. [plain] is what an older server sent instead (lines, no headings), shown when there are no
 * sections; with neither, "Small fixes and improvements."
 */
@Composable
fun ReleaseNotes(sections: List<NoteSection>, plain: List<String>, role: String?, modifier: Modifier = Modifier) {
    val mine = sections.forRole(role)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            mine.isNotEmpty() -> mine.forEach { s ->
                if (s.title.isNotBlank()) Text(s.title, style = MaterialTheme.typography.titleSmall, color = Snow, modifier = Modifier.padding(top = 2.dp))
                s.items.forEach { Bullet(it.text) }
            }
            sections.isEmpty() && plain.isNotEmpty() -> plain.forEach { Bullet(it) }
            else -> Text("Small fixes and improvements.", style = MaterialTheme.typography.bodySmall, color = SnowSoft)
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row {
        Text("•", style = MaterialTheme.typography.bodySmall, color = Gold)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = SnowSoft)
    }
}
