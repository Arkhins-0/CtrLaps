package com.arkhins.ctrlaps.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.arkhins.ctrlaps.CtrlapsApplication
import com.arkhins.ctrlaps.data.Message
import com.arkhins.ctrlaps.data.Queued
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.util.UUID

/**
 * A chat notification's buttons, without opening the app. Reply goes
 * through the outbox like a message typed in the chat (so with no
 * connection it waits and goes later), then marks the chat read; Mark read
 * fetches the chat as read and takes the notification away.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION) ?: return
        val app = context.applicationContext as CtrlapsApplication
        if (!app.session.signedIn) {
            Notifications.cancelChat(context, conversationId)
            return
        }
        val pending = goAsync()
        app.appScope.launch {
            try {
                when (intent.action) {
                    REPLY -> {
                        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()?.trim()
                        if (text.isNullOrEmpty()) return@launch
                        val local = Message(
                            id = "local-" + UUID.randomUUID(),
                            conversationId = conversationId,
                            kind = "direct",
                            body = text,
                            createdAt = Instant.now().toString(),
                            mine = true,
                            status = "pending",
                        )
                        // The answer shows at once; the outbox keeps it until the server has it.
                        Notifications.replied(context, conversationId, text)
                        app.outbox.send(Queued(conversationId, local))
                        withTimeoutOrNull(8_000) { app.outbox.items.first { list -> list.none { it.message.id == local.id } } }
                        runCatching { app.chatCache.sync(conversationId, markRead = true) }
                    }
                    MARK_READ -> {
                        Notifications.cancelChat(context, conversationId)
                        runCatching { app.chatCache.sync(conversationId, markRead = true) }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val REPLY = "com.arkhins.ctrlaps.REPLY"
        const val MARK_READ = "com.arkhins.ctrlaps.MARK_READ"
        const val EXTRA_CONVERSATION = "conversation"
        const val KEY_REPLY = "reply"
    }
}
