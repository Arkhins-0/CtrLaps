package com.arkhins.ctrlaps.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** One successful check on this phone: who, as they were then, and when. */
@Serializable
data class RecentCheck(val person: Verified, val at: Long)

/**
 * The people this phone has checked on Verify, newest first, and the ones starred to keep at hand. Kept on the
 * phone only. Starred people are never dropped; the rest keep the last [KEEP].
 */
class VerifyHistory(context: Context) {
    private val prefs = context.getSharedPreferences("ctrlaps_verify_history", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _checks = MutableStateFlow(load())
    private val _starred = MutableStateFlow(loadStarred())

    val checks: StateFlow<List<RecentCheck>> = _checks
    val starred: StateFlow<Set<String>> = _starred

    fun add(person: Verified) {
        val rest = _checks.value.filterNot { it.person.id == person.id }
        val all = listOf(RecentCheck(person, System.currentTimeMillis())) + rest
        var unstarred = 0
        _checks.value = all.filter { it.person.id in _starred.value || ++unstarred <= KEEP }
        save()
    }

    fun toggleStar(id: String) {
        _starred.value = if (id in _starred.value) _starred.value - id else _starred.value + id
        prefs.edit().putString(STARRED, json.encodeToString(SetSerializer(String.serializer()), _starred.value)).apply()
    }

    /** Forget the checks that are not starred. */
    fun clearUnstarred() {
        _checks.value = _checks.value.filter { it.person.id in _starred.value }
        save()
    }

    private fun save() = prefs.edit().putString(CHECKS, json.encodeToString(ListSerializer(RecentCheck.serializer()), _checks.value)).apply()

    private fun load(): List<RecentCheck> =
        prefs.getString(CHECKS, null)?.let { runCatching { json.decodeFromString(ListSerializer(RecentCheck.serializer()), it) }.getOrNull() } ?: emptyList()

    private fun loadStarred(): Set<String> =
        prefs.getString(STARRED, null)?.let { runCatching { json.decodeFromString(SetSerializer(String.serializer()), it) }.getOrNull() } ?: emptySet()

    private companion object {
        const val CHECKS = "checks"
        const val STARRED = "starred"
        const val KEEP = 30
    }
}
