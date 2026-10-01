package com.arkhins.ctrlaps.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.LocalApp
import com.arkhins.ctrlaps.data.SessionResultsResponse
import com.arkhins.ctrlaps.data.StandingsResponse
import com.arkhins.ctrlaps.ui.AppViewModel
import com.arkhins.ctrlaps.ui.components.Chip
import com.arkhins.ctrlaps.ui.components.Divider
import com.arkhins.ctrlaps.ui.components.Empty
import com.arkhins.ctrlaps.ui.components.ErrorText
import com.arkhins.ctrlaps.ui.components.Loading
import com.arkhins.ctrlaps.ui.components.Panel
import com.arkhins.ctrlaps.ui.components.SectionTitle
import com.arkhins.ctrlaps.ui.theme.Snow
import com.arkhins.ctrlaps.ui.theme.SnowFaint
import com.arkhins.ctrlaps.ui.theme.SnowSoft
import com.arkhins.ctrlaps.ui.whenLabel

private fun pts(p: Double): String = com.arkhins.ctrlaps.data.pointsText(p)

/**
 * A season's standings (the current one, or [startSeason]; archived seasons keep theirs), one race category at a
 * time: drivers, teams, and the sessions with results.
 */
@Composable
fun StandingsScreen(vm: AppViewModel, onOpenResults: (String) -> Unit, startSeason: String? = null) {
    val app = LocalApp.current
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    var season by rememberSaveable { mutableStateOf(startSeason) }
    var data by remember { mutableStateOf<StandingsResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(chosen, season, vm.refreshTick) {
        val query = listOfNotNull(season?.let { "season=$it" }, chosen?.let { "category=$it" }).joinToString("&")
        val path = "/api/standings" + if (query.isEmpty()) "" else "?$query"
        try {
            data = app.store.get(path, StandingsResponse.serializer()) { if (data?.categoryId != it.categoryId || data?.seasonId != it.seasonId || data == null) data = it }
            error = null
        } catch (e: Exception) {
            if (data == null) error = e.message
        }
    }

    val d = data
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            error != null && d == null -> item { ErrorText(error) }
            d == null -> item { Loading() }
            else -> {
                // Past seasons keep their standings.
                if (d.seasons.size > 1) item {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        d.seasons.forEach { s -> Chip(s.name, SnowSoft, filled = s.id == d.seasonId) { if (s.id != d.seasonId) { season = s.id; chosen = null } } }
                    }
                }
                if (d.categories.isEmpty()) item { Empty("This season has no race categories yet.") }
                else item {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        d.categories.forEach { c -> Chip(c.code, categoryColor(c), filled = c.id == d.categoryId) { chosen = c.id } }
                    }
                }
                val category = d.categories.firstOrNull { it.id == d.categoryId }
                if (category != null) item {
                    Panel {
                        Column {
                            SectionTitle("${category.name} · DRIVERS".uppercase())
                            if (d.drivers.isEmpty()) Text("No results yet.", style = MaterialTheme.typography.bodySmall, color = SnowFaint, modifier = Modifier.padding(top = 8.dp))
                            d.drivers.forEachIndexed { i, s ->
                                if (i > 0) Divider()
                                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = SnowFaint, modifier = Modifier.width(28.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            (if (s.carNumber.isNotBlank()) "#${s.carNumber}  " else "") + s.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = Snow,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        val sub = listOfNotNull(s.teamName, if (s.wins > 0) "${s.wins} win" + if (s.wins == 1) "" else "s" else null).joinToString(" · ")
                                        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall, color = SnowFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(pts(s.points), style = MaterialTheme.typography.titleMedium, color = Snow, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
                if (d.teams.isNotEmpty()) item {
                    Panel {
                        Column {
                            SectionTitle("${category?.name ?: ""} · TEAMS".uppercase())
                            d.teams.forEachIndexed { i, t ->
                                if (i > 0) Divider()
                                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = SnowFaint, modifier = Modifier.width(28.dp))
                                    Text(t.name, style = MaterialTheme.typography.titleSmall, color = Snow, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(pts(t.points), style = MaterialTheme.typography.titleMedium, color = Snow, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
                if (d.sessions.isNotEmpty()) item {
                    Panel {
                        Column {
                            SectionTitle("RESULTS")
                            d.sessions.forEachIndexed { i, s ->
                                if (i > 0) Divider()
                                Row(Modifier.fillMaxWidth().clickable { onOpenResults(s.id) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(s.name, style = MaterialTheme.typography.titleSmall, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${s.weekendName} · ${whenLabel(s.startsAt)}", style = MaterialTheme.typography.labelSmall, color = SnowFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text("${s.rows} drivers ›", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One session's results; admins, coordinators and the category's race officials enter or edit them here. */
@Composable
fun ResultsScreen(vm: AppViewModel, sessionId: String) {
    val app = LocalApp.current
    var data by remember { mutableStateOf<SessionResultsResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(sessionId, vm.refreshTick) {
        try {
            data = app.store.get("/api/sessions/$sessionId/results", SessionResultsResponse.serializer()) { if (data == null) data = it }
            error = null
        } catch (e: Exception) {
            if (data == null) error = e.message
        }
    }
    val d = data
    if (editing && d != null) {
        ResultsEditor(sessionId, d, onSaved = { data = it; editing = false }, onCancel = { editing = false })
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            error != null && d == null -> item { ErrorText(error) }
            d == null -> item { Loading() }
            else -> {
                if (d.canEdit && d.category != null) item {
                    com.arkhins.ctrlaps.ui.components.GoldButton(if (d.results.isEmpty()) "Enter results" else "Edit results", Modifier.fillMaxWidth()) { editing = true }
                }
                item {
                    Column {
                        Text(d.session.name, style = MaterialTheme.typography.titleLarge, color = Snow)
                        Text(
                            listOfNotNull(d.category?.code, d.session.weekendName, whenLabel(d.session.startsAt)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = SnowSoft,
                        )
                    }
                }
                if (d.results.isEmpty()) item { Empty("No results yet.") }
                else item {
                    Panel {
                        Column {
                            d.results.forEachIndexed { i, r ->
                                if (i > 0) Divider()
                                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (r.status == "finished") (r.position?.toString() ?: "–") else r.status.uppercase(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SnowFaint,
                                        modifier = Modifier.width(40.dp),
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            (if (r.carNumber.isNotBlank()) "#${r.carNumber}  " else "") + r.driverName,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = Snow,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        val sub = listOfNotNull(r.teamName, r.bestLap.takeIf { it.isNotBlank() }?.let { "Best $it" }, if (r.pole) "Pole" else null, if (r.fastestLap) "Fastest lap" else null).joinToString(" · ")
                                        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Default), color = SnowFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (r.points > 0) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(pts(r.points), style = MaterialTheme.typography.titleMedium, color = Snow, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
