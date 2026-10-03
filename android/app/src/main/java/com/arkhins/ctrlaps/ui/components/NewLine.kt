package com.arkhins.ctrlaps.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.data.Message
import com.arkhins.ctrlaps.ui.theme.Danger

/**
 * The line under the last unread post in a feed shown newest first: a thin red rule with NEW at its end, so the
 * reader knows where the new posts stop. Where it sits is decided once, when the page first has posts, and stays
 * put while the page is open (the posts are marked read as soon as they are fetched).
 */
@Composable
fun NewLine() {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(Danger))
        Box(Modifier.background(Danger, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
            Text("NEW", style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
    }
}

/**
 * Every post seen unread while this page is open (the phone's copy first, then the server's): the set only grows, so
 * the line stays where it is once the server has marked the posts read, and takes in a post that arrives meanwhile.
 */
@Composable
fun rememberNewIds(messages: List<Message>?): Set<String> {
    var ids by remember { mutableStateOf<Set<String>>(emptySet()) }
    val unread = messages.orEmpty().filter { it.readAt == null && !it.mine }.map { it.id }
    if (unread.any { it !in ids }) ids = ids + unread
    return ids
}

/** In a list of runs shown newest first: the index of the last run that holds a new post, where the line goes under. */
fun lastNewRun(runs: List<List<Message>>, newIds: Set<String>): Int = runs.indexOfLast { run -> run.any { it.id in newIds } }

/** A row of a feed shown newest first: a day's heading (stuck to the top while its posts scroll), a post, or the NEW line. */
sealed interface FeedRow {
    val key: String
    data class Day(val label: String) : FeedRow { override val key get() = "day-$label" }
    data class Run(val run: List<Message>) : FeedRow { override val key get() = run.first().id }
    data object New : FeedRow { override val key get() = "new-line" }
}

/** The runs of a feed (newest first) with a heading at each new day and the NEW line under the last unread post. */
fun feedRows(runs: List<List<Message>>, newIds: Set<String>): List<FeedRow> {
    val lastNew = lastNewRun(runs, newIds)
    val rows = ArrayList<FeedRow>(runs.size + 4)
    var day: String? = null
    runs.forEachIndexed { i, run ->
        val d = com.arkhins.ctrlaps.ui.dayHeading(run.first().createdAt)
        if (d != day) {
            day = d
            rows += FeedRow.Day(d)
        }
        rows += FeedRow.Run(run)
        if (i == lastNew) rows += FeedRow.New
    }
    return rows
}

/** The day's heading in a feed: a pill on a band of the page's colour, so the posts slide under it as it sticks. */
@Composable
fun DayHeader(label: String) {
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .background(com.arkhins.ctrlaps.ui.theme.NightPanel, RoundedCornerShape(999.dp))
                .border(1.dp, com.arkhins.ctrlaps.ui.theme.NightLine, RoundedCornerShape(999.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) { Text(label, style = MaterialTheme.typography.labelSmall, color = com.arkhins.ctrlaps.ui.theme.SnowFaint) }
    }
}

