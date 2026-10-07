package com.arkhins.ctrlaps.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkhins.ctrlaps.LocalApp
import com.arkhins.ctrlaps.R
import com.arkhins.ctrlaps.data.PhotoUrlResponse
import com.arkhins.ctrlaps.data.TeamPageResponse
import com.arkhins.ctrlaps.data.pointsText
import com.arkhins.ctrlaps.ui.components.Avatar
import com.arkhins.ctrlaps.ui.components.ErrorText
import com.arkhins.ctrlaps.ui.components.GroupTitle
import com.arkhins.ctrlaps.ui.components.Loading
import com.arkhins.ctrlaps.ui.components.SquareCropDialog
import com.arkhins.ctrlaps.ui.theme.Danger
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.NightLine
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import com.arkhins.ctrlaps.ui.theme.SnowSoft
import com.arkhins.ctrlaps.ui.whenLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val FINISH_SHORT = mapOf("dnf" to "DNF", "dns" to "DNS", "dsq" to "DSQ")

/**
 * Pick a photo from the phone, crop it square and save it as a team's photo. Returns what starts it for a team id;
 * [onSaved] gets the team and its new photo address, [onError] what went wrong.
 */
@Composable
fun rememberTeamPhotoPicker(onSaved: (teamId: String, photoUrl: String?) -> Unit, onError: (String) -> Unit): (String) -> Unit {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var team by remember { mutableStateOf<String?>(null) }
    var cropping by remember { mutableStateOf<Bitmap?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { cropping = withContext(Dispatchers.IO) { loadShrunk(context, uri, 1600) } }
    }
    cropping?.let { src ->
        SquareCropDialog(src, onCancel = { cropping = null }) { square ->
            cropping = null
            val id = team ?: return@SquareCropDialog
            scope.launch {
                val file = withContext(Dispatchers.IO) {
                    File(context.cacheDir, "team.jpg").also { f -> f.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
                }
                try {
                    onSaved(id, app.api.postForm("/api/teams/$id/photo", emptyMap(), "photo" to file, "image/jpeg", PhotoUrlResponse.serializer()).photoUrl)
                } catch (e: Exception) {
                    onError(e.message ?: "Could not save the photo.")
                } finally {
                    file.delete()
                }
            }
        }
    }
    return { id ->
        team = id
        pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}

/**
 * A team's page, for anyone signed in: its photo as a banner, where it stands in each category, its people (each
 * opening their own page where the viewer may), and its drivers' latest results. An admin, a coordinator or the
 * team's manager changes the photo here.
 */
@Composable
fun TeamScreen(teamId: String, onTitle: (String) -> Unit) {
    val app = LocalApp.current
    val open = LocalOpen.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<TeamPageResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(teamId, reload) {
        try {
            data = app.store.get("/api/teams/$teamId", TeamPageResponse.serializer()) { if (data == null) data = it; onTitle(it.team.name) }
                .also { onTitle(it.team.name) }
            error = null
        } catch (e: Exception) {
            if (data == null) error = e.message
        }
    }
    val changePhoto = rememberTeamPhotoPicker(onSaved = { _, _ -> reload++ }, onError = { error = it })

    val d = data
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)) {
        if (d == null) {
            item { if (error != null) ErrorText(error) else Loading() }
            return@LazyColumn
        }
        val codes = d.categories.joinToString(", ") { it.code }
        item {
            val people = d.people.size
            ProfileBanner(
                app.api.absolute(d.team.photoUrl),
                d.team.name,
                listOf(if (people == 1) "1 person" else "$people people", codes.takeIf { it.isNotBlank() }?.let { "races in $it" }).filterNotNull().joinToString(" · "),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { d.categories.take(5).forEach { CategoryTag(it) } }
            }
        }
        item { ErrorText(error) }
        if (d.canEdit) item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(painterResource(R.drawable.ic_gallery), if (d.team.photoUrl == null) "Add photo" else "Change photo", Modifier.weight(1f)) { changePhoto(d.team.id) }
                if (d.team.photoUrl != null) {
                    QuickAction(rememberVectorPainter(Icons.Outlined.Delete), "Remove photo", Modifier.weight(1f)) {
                        scope.launch {
                            try {
                                app.api.delete("/api/teams/${d.team.id}/photo")
                                reload++
                            } catch (e: Exception) {
                                error = e.message ?: "Could not remove the photo."
                            }
                        }
                    }
                }
            }
        }

        // Where it stands in each category.
        item { GroupTitle("Standings", modifier = Modifier.padding(top = 12.dp)) }
        if (d.standings.isEmpty()) item { Text("No results yet this season.", style = MaterialTheme.typography.bodyMedium, color = SnowFaint, modifier = Modifier.padding(vertical = 8.dp)) }
        items(d.standings, key = { "s-" + it.category.id }) { line ->
            val tone = categoryColor(line.category)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { open("standings") }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(tone.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("P${line.position}", style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), fontWeight = FontWeight.Bold, color = tone)
                        Text("of ${line.of}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = SnowSoft)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(line.category.name, style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp), fontWeight = FontWeight.Bold, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${line.wins} ${if (line.wins == 1) "win" else "wins"} · ${line.podiums} ${if (line.podiums == 1) "podium" else "podiums"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SnowSoft.copy(alpha = 0.8f),
                    )
                }
                Text("${pointsText(line.points)} pts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Snow)
            }
        }

        // Its people: the manager first, then racers and crew.
        item { GroupTitle("People", d.people.size, Modifier.padding(top = 12.dp)) }
        if (d.people.isEmpty()) item { Text("Nobody carries this team yet.", style = MaterialTheme.typography.bodyMedium, color = SnowFaint, modifier = Modifier.padding(vertical = 8.dp)) }
        items(d.people, key = { "p-" + it.id }) { p ->
            val canOpen = p.id in d.canOpen
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).then(if (canOpen) Modifier.clickable { open("person/${p.id}") } else Modifier).padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(app.api.absolute(p.photoUrl), p.name, size = 46)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp), fontWeight = FontWeight.Bold, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (p.pending) "Invite not accepted" else ROLE_LABELS[p.role] ?: p.role,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (p.pending) Gold else SnowSoft.copy(alpha = 0.8f),
                    )
                }
                if (canOpen) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Gold)
            }
        }

        // The latest results of its drivers.
        if (d.results.isNotEmpty()) {
            item { GroupTitle("Latest results", modifier = Modifier.padding(top = 12.dp)) }
            val byId = d.categories.associateBy { it.id }
            items(d.results.take(10), key = { "r-" + it.sessionId + it.driverName }) { r ->
                val finished = r.status == "finished"
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { open("results/${r.sessionId}") }.padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (finished) NightLine else Danger.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (finished) "P${r.position ?: "–"}" else FINISH_SHORT[r.status] ?: "–",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (!finished) Danger else if ((r.position ?: 99) <= 3) Gold else Snow,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            r.categoryId?.let { byId[it] }?.let {
                                CategoryTag(it)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(r.driverName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("${r.sessionName} · ${r.weekendName} · ${whenLabel(r.startsAt)}", style = MaterialTheme.typography.bodySmall, color = SnowSoft.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(pointsText(r.points), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Snow)
                }
            }
        }
    }
}
