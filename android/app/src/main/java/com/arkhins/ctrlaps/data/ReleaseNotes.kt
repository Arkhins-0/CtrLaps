package com.arkhins.ctrlaps.data

import kotlinx.serialization.Serializable

/** One line of release notes, and the roles it is for (empty: everyone). */
@Serializable
data class NoteItem(val text: String, val roles: List<String> = emptyList())

/** A heading of release notes (New, Improved, Fixed; "" before any heading) and its lines. */
@Serializable
data class NoteSection(val title: String = "", val items: List<NoteItem> = emptyList())

/**
 * Release notes as the repository writes them (release-notes/<version>.md, also each GitHub release's body): "## New"
 * starts a heading, "- [admin, coordinator] …" is a line for those roles only, "- …" one for everyone. The website
 * sends them already split (`sections`); this reads a release body straight from GitHub (the fallback).
 */
fun noteSections(body: String): List<NoteSection> {
    val out = mutableListOf<NoteSection>()
    body.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.contains("Full Changelog") }.forEach { line ->
        val heading = Regex("^#{1,6}\\s+(.*)$").find(line)
        if (heading != null) {
            out += NoteSection(heading.groupValues[1].trim())
            return@forEach
        }
        val text = line.removePrefix("-").removePrefix("*").trim().replace(Regex(";\\s*v\\d+(\\.\\d+)+\\s*$"), "")
        val tagged = Regex("^\\[([a-zA-Z_,\\s]+)]\\s*(.+)$").find(text)
        val item = if (tagged != null) {
            NoteItem(tagged.groupValues[2].trim(), tagged.groupValues[1].split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() })
        } else NoteItem(text)
        if (out.isEmpty()) out += NoteSection("")
        out[out.lastIndex] = out.last().copy(items = out.last().items + item)
    }
    return out.filter { it.items.isNotEmpty() }
}

/** The lines [role] should see: everyone's, and those tagged for their role. Headings left empty go. */
fun List<NoteSection>.forRole(role: String?): List<NoteSection> =
    map { s -> s.copy(items = s.items.filter { it.roles.isEmpty() || (role != null && role in it.roles) }) }.filter { it.items.isNotEmpty() }
