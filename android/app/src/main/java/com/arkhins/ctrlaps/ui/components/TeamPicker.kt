package com.arkhins.ctrlaps.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.LocalApp
import com.arkhins.ctrlaps.data.TeamsResponse
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.NightLine
import com.arkhins.ctrlaps.ui.theme.NightPanel
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint

/**
 * The team box when giving someone a role: type to search the teams there are and pick one. A name that matches none
 * says a new team of that name will be made, so a typo doesn't quietly make a second "Ahura Racing".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamPicker(value: String, onChange: (String) -> Unit, label: String, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    var teams by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        teams = runCatching { app.store.get("/api/teams", TeamsResponse.serializer()) { teams = it.teams.map { t -> t.name } }.teams.map { it.name } }.getOrDefault(teams)
    }
    var open by remember { mutableStateOf(false) }
    val typed = value.trim()
    val matches = teams.filter { typed.isEmpty() || it.contains(typed, ignoreCase = true) }.take(8)
    val known = teams.any { it.equals(typed, ignoreCase = true) }

    Column(modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(expanded = open && matches.isNotEmpty() && enabled, onExpandedChange = { if (enabled) open = it }) {
            OutlinedTextField(
                value = value,
                onValueChange = { onChange(it); open = true },
                label = { Text(label) },
                singleLine = true,
                enabled = enabled,
                placeholder = { Text("Search or type a new team", color = SnowFaint) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable, enabled),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, unfocusedBorderColor = NightLine, focusedLabelColor = Gold, unfocusedLabelColor = SnowFaint, cursorColor = Gold),
            )
            ExposedDropdownMenu(expanded = open && matches.isNotEmpty() && enabled, onDismissRequest = { open = false }, containerColor = NightPanel) {
                matches.forEach { name ->
                    DropdownMenuItem(text = { Text(name, color = Snow) }, onClick = { onChange(name); open = false })
                }
            }
        }
        // A name that isn't a team yet: say so before it is saved.
        if (typed.isNotEmpty() && !known && teams.isNotEmpty()) {
            Text(
                "No team called “$typed” yet: saving makes a new team with that name. If that's not right, pick one from the list.",
                style = MaterialTheme.typography.bodySmall,
                color = Gold,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }
    }
}
