package com.arkhins.ctrlaps.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** One notification the phone showed, kept so it can be found again after it was swiped away. */
@Serializable
data class LoggedNotification(
    /** Firebase's message id, or one made up for the phone's own (a reminder): the same one is never kept twice. */
    val key: String,
    /** "chats", "announcements", "channels", "results", "reminders", "support" or "other". */
    val kind: String,
    val title: String,
    val body: String,
    /** Where a tap goes, as the notification's own link. */
    val link: String,
    /** When it arrived, epoch milliseconds. */
    val at: Long,
    /** The sender's photo, as the server's relative URL. */
    val photo: String = "",
)

/**
 * Every notification the phone has shown, newest first, in a file of its own (at most [MAX]). The Notifications
 * page reads it; anything newer than the last look there counts as [unread], for the bell's badge.
 */
class NotificationLog(context: Context) {
    private val file = File(context.filesDir, "notifications.json")
    private val prefs = context.getSharedPreferences("ctrlaps_notifications", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val _items = MutableStateFlow(read())
    private val _unread = MutableStateFlow(countUnread(_items.value))

    val items: StateFlow<List<LoggedNotification>> = _items
    val unread: StateFlow<Int> = _unread

    private fun read(): List<LoggedNotification> =
        runCatching { json.decodeFromString(ListSerializer(LoggedNotification.serializer()), file.readText()) }.getOrDefault(emptyList())

    private fun countUnread(list: List<LoggedNotification>): Int {
        val seen = prefs.getLong(SEEN, 0L)
        return list.count { it.at > seen }
    }

    @Synchronized
    fun add(entry: LoggedNotification) {
        val current = _items.value
        if (current.any { it.key == entry.key }) return
        val next = (listOf(entry) + current).take(MAX)
        save(next)
    }

    /** The Notifications page was opened: the badge goes. */
    @Synchronized
    fun markSeen() {
        prefs.edit().putLong(SEEN, System.currentTimeMillis()).apply()
        _unread.value = 0
    }

    @Synchronized
    fun clear() = save(emptyList())

    /** Undo of a clear: the list as it was. */
    @Synchronized
    fun restore(list: List<LoggedNotification>) = save(list)

    private fun save(list: List<LoggedNotification>) {
        _items.value = list
        _unread.value = countUnread(list)
        runCatching { file.writeText(json.encodeToString(ListSerializer(LoggedNotification.serializer()), list)) }
    }

    private companion object {
        const val MAX = 3000
        const val SEEN = "seen_at"
    }
}
