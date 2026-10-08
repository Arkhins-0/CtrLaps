package com.arkhins.ctrlaps.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arkhins.ctrlaps.ui.theme.Danger
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.ui.theme.NightHigh
import com.arkhins.ctrlaps.ui.theme.NightLine
import com.arkhins.ctrlaps.ui.theme.Snow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The app's message bar (Arkhime's snackbar), in place of Android's grey toasts: a rounded bar just above the tab bar,
 * in the app's colours, red for something that went wrong. It stays a few seconds, closes on a tap, and a long press
 * copies its words (for a support ticket). It can carry one button: Undo, Retry, Open.
 *
 * [undo] does a change later: the change is made when the bar goes, unless Undo was tapped. The wait runs outside any
 * screen, so leaving the page doesn't lose the change; if the app is closed before then, nothing is changed.
 */
object Snack {
    enum class Tone { Info, Error }

    data class Message(val text: String, val tone: Tone, val action: String?, val onAction: (() -> Unit)?, val id: Long = System.nanoTime())

    val shown = mutableStateOf<Message?>(null)
    /** Set while the tab bar is on screen, so the bar sits above it. */
    val overTabBar = mutableStateOf(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timer: Job? = null
    /** The change waiting on an Undo bar: made at once when another message takes its place. */
    private var pending: (suspend () -> Unit)? = null

    fun show(text: String, tone: Tone = Tone.Info, action: String? = null, seconds: Long = if (action != null) 6 else 4, onAction: (() -> Unit)? = null) {
        flushPending()
        val m = Message(text, tone, action, onAction)
        shown.value = m
        timer?.cancel()
        timer = scope.launch {
            delay(seconds * 1000)
            if (shown.value?.id == m.id) shown.value = null
        }
    }

    fun error(text: String) = show(text, Tone.Error)

    /**
     * "[text] · Undo": [onUndo] puts the screen back at once; otherwise [commit] runs when the bar goes (after
     * [seconds], a tap on the bar, or the next message). [onFailed] gets what went wrong in [commit].
     */
    fun undo(text: String, seconds: Long = 5, onUndo: () -> Unit = {}, onFailed: (Throwable) -> Unit = { error(it.message ?: "Something went wrong.") }, commit: suspend () -> Unit) {
        flushPending()
        val m = Message(text, Tone.Info, "Undo", null)
        val job: suspend () -> Unit = { runCatching { commit() }.onFailure(onFailed) }
        pending = job
        shown.value = m.copy(onAction = {
            if (pending === job) pending = null
            onUndo()
        })
        timer?.cancel()
        timer = scope.launch {
            delay(seconds * 1000)
            if (shown.value?.id == m.id) shown.value = null
            if (pending === job) {
                pending = null
                job()
            }
        }
    }

    /** Close the bar now; a change waiting on Undo is made. */
    fun dismiss() {
        shown.value = null
        flushPending()
    }

    private fun flushPending() {
        val job = pending ?: return
        pending = null
        scope.launch { job() }
    }
}

/** Where [Snack]'s bar is drawn: once, over everything, at the bottom. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SnackHost() {
    val m = Snack.shown.value
    val clipboard = LocalClipboardManager.current
    val haptics = com.arkhins.ctrlaps.ui.components.rememberHaptics()
    Box(Modifier.fillMaxSize().imePadding().navigationBarsPadding(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = m != null,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut(),
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = if (Snack.overTabBar.value) 84.dp else 16.dp),
        ) {
            val shown = m ?: return@AnimatedVisibility
            val tone = if (shown.tone == Snack.Tone.Error) Danger else Gold
            Row(
                Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(NightHigh)
                    .border(1.dp, if (shown.tone == Snack.Tone.Error) Danger.copy(alpha = 0.6f) else NightLine, RoundedCornerShape(16.dp))
                    .combinedClickable(
                        onClick = { Snack.dismiss() },
                        onLongClick = {
                            clipboard.setText(AnnotatedString(shown.text))
                            haptics.longPress()
                        },
                    )
                    .padding(start = 0.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // A thin stripe in the tone: the accent, or red for a failure.
                Box(Modifier.width(4.dp).height(52.dp).background(tone))
                Spacer(Modifier.width(14.dp))
                Text(
                    shown.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Snow,
                    modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                )
                shown.action?.let { label ->
                    TextButton(onClick = {
                        shown.onAction?.invoke()
                        Snack.shown.value = null
                    }) { Text(label.uppercase(), color = tone, fontWeight = FontWeight.Bold) }
                }
                if (shown.action == null) Spacer(Modifier.width(10.dp))
            }
        }
    }
}
