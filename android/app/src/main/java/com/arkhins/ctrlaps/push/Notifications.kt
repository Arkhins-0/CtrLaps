package com.arkhins.ctrlaps.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.transform.CircleCropTransformation
import com.arkhins.ctrlaps.Config
import com.arkhins.ctrlaps.CtrlapsApplication
import com.arkhins.ctrlaps.MainActivity
import com.arkhins.ctrlaps.R
import com.arkhins.ctrlaps.data.LoggedNotification
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** A message that arrived while the app was open: the in-app popup shows it. */
data class PushEvent(
    val title: String,
    val body: String,
    val link: String,
    /** "chat", "group", "channel" or "announcement"; blank from an older server. */
    val kind: String = "",
    val senderName: String = "",
    val senderRole: String = "",
    val senderPhoto: String = "",
    /** The words typed with it, if any. */
    val text: String = "",
    /** "image", "audio", "document", "location" or blank. */
    val attach: String = "",
    /** The race weekend, for a channel message. */
    val place: String = "",
)

/** The server's silent nudge: this chat, the announcements ("home") or this weekend's channel changed. */
data class SyncSignal(val scope: String, val id: String)

/** A push as the server sends it: data only, so the app builds the notification itself. */
data class Incoming(
    /** Firebase's id for the message: the Notifications page keeps each one once. */
    val key: String,
    val title: String,
    val body: String,
    val link: String,
    val tag: String,
    val kind: String,
    val senderId: String,
    val senderName: String,
    val senderPhoto: String,
    val text: String,
    /** The group's name for a group chat, the weekend or category for a channel post. */
    val place: String,
    /** A photo's file id: shown large. */
    val image: String,
) {
    companion object {
        fun from(data: Map<String, String>, title: String, body: String, key: String) = Incoming(
            key = key,
            title = title,
            body = body,
            link = data["link"] ?: "/home",
            tag = data["tag"].orEmpty(),
            kind = data["kind"].orEmpty(),
            senderId = data["senderId"].orEmpty(),
            senderName = data["senderName"].orEmpty(),
            senderPhoto = data["senderPhoto"].orEmpty(),
            text = data["text"].orEmpty(),
            place = data["place"].orEmpty(),
            image = data["image"].orEmpty(),
        )
    }
}

/**
 * Every notification the app shows. Three channels, so each can be set
 * apart in the phone's settings: chats (each conversation one notification
 * with its last messages, the sender's photo, Reply and Mark read), posts
 * (announcements and channels, a photo shown large) and everything else.
 */
object Notifications {
    const val CHANNEL_CHATS = "chats"
    const val CHANNEL_POSTS = "posts"
    const val CHANNEL_OTHER = "other"
    /** The one channel every notification used before; removed so it doesn't linger in the phone's settings. */
    private const val OLD_CHANNEL = "ctrlaps_alerts"
    const val EXTRA_LINK = "link"

    private const val ID = 1
    private const val SUMMARY_TAG = "chats-summary"
    private const val GROUP_CHATS = "com.arkhins.ctrlaps.CHATS"
    /** A chat notification keeps this many of its latest messages. */
    private const val KEEP = 8

    /** The app is on screen (MainActivity between onResume and onPause). */
    @Volatile var foreground = false
    /** The private chat open on screen, if any: its messages need no notification or popup. */
    @Volatile var openChat: String? = null

    /** A message for the chat the person is looking at right now. */
    fun isOpenChat(link: String): Boolean = foreground && openChat != null && link == "/chats/$openChat"

    /** Foreground pushes, for the in-app popup. */
    val events = MutableSharedFlow<PushEvent>(extraBufferCapacity = 8)

    /** Nudges, after the phone's copy has been brought up to date, so open screens show it. */
    val syncs = MutableSharedFlow<SyncSignal>(extraBufferCapacity = 16)

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        fun channel(id: String, name: String, about: String) = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            description = about
            enableVibration(true)
            setShowBadge(true)
        }
        manager.createNotificationChannels(
            listOf(
                channel(CHANNEL_CHATS, "Chats", "Private and group messages"),
                channel(CHANNEL_POSTS, "Announcements and channels", "Announcements, race-weekend and category channels"),
                channel(CHANNEL_OTHER, "Results and reminders", "Results, event reminders, support replies and app updates"),
            ),
        )
        manager.deleteNotificationChannel(OLD_CHANNEL)
    }

    private fun chatTag(conversationId: String) = "chat:$conversationId"

    /** A push from the server. Runs on Firebase's own thread, so fetching photos may wait a moment. */
    fun show(context: Context, n: Incoming) {
        log(context, n.key, kindOf(n.kind, n.link, n.tag), n.title, n.text.ifBlank { n.body }, n.link, n.senderPhoto)
        val chat = n.link.removePrefix("/chats/").takeIf { n.link.startsWith("/chats/") && it.isNotBlank() && !it.contains('/') }
        when {
            chat != null -> showChat(context, n, chat)
            n.kind == "announcement" || n.kind == "channel" -> showPost(context, n)
            else -> show(context, n.title, n.body, n.link, n.tag.ifBlank { null }, logged = true)
        }
    }

    /**
     * Account → Notifications → Send a test: one notification on each channel (a chat, an announcement, a result), so a
     * phone can be checked without waiting for a real one. Not kept in the history.
     */
    fun sendTest(context: Context) {
        listOf(
            Triple(CHANNEL_CHATS, "Test · Chats", "This is how a chat message arrives."),
            Triple(CHANNEL_POSTS, "Test · Announcements and channels", "This is how an announcement or a channel post arrives."),
            Triple(CHANNEL_OTHER, "Test · Results and reminders", "This is how results, reminders and support replies arrive."),
        ).forEach { (channel, title, body) ->
            val builder = base(context, channel, "/home", "test:$channel")
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            post(context, "test:$channel", builder)
        }
    }

    /** A plain notification: results, reminders, support. Results also get a Standings button. */
    fun show(context: Context, title: String, body: String, link: String, tag: String?, logged: Boolean = false) {
        if (!logged) log(context, "${tag ?: link}@${System.currentTimeMillis()}", kindOf("", link, tag), title, body, link, "")
        val builder = base(context, CHANNEL_OTHER, link, tag ?: link)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        if (link.startsWith("/results/")) builder.addAction(0, "Standings", open(context, "/standings", 31 * (tag ?: link).hashCode() + 3))
        post(context, tag ?: link, builder)
    }

    private fun showChat(context: Context, n: Incoming, conversationId: String) {
        val tag = chatTag(conversationId)
        val style = previousStyle(context, tag) ?: NotificationCompat.MessagingStyle(me())
        if (n.kind == "group") {
            style.conversationTitle = n.place.ifBlank { null }
            style.isGroupConversation = true
        }
        val sender = Person.Builder()
            .setKey(n.senderId.ifBlank { n.senderName.ifBlank { n.title } })
            .setName(n.senderName.ifBlank { n.title })
            .apply { bitmap(context, n.senderPhoto, 192, circle = true)?.let { setIcon(IconCompat.createWithBitmap(it)) } }
            .build()
        style.addMessage(n.text.ifBlank { n.body }, System.currentTimeMillis(), sender)
        while (style.messages.size > KEEP) style.messages.removeAt(0)
        val builder = base(context, CHANNEL_CHATS, n.link, tag)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(GROUP_CHATS)
            .addAction(replyAction(context, conversationId, tag))
            .addAction(
                NotificationCompat.Action.Builder(0, "Mark read", action(context, NotificationActionReceiver.MARK_READ, conversationId, 31 * tag.hashCode() + 2, mutable = false))
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                    .setShowsUserInterface(false)
                    .build(),
            )
        post(context, tag, builder)
        postSummary(context)
    }

    private fun showPost(context: Context, n: Incoming) {
        val text = n.text.ifBlank { n.body }
        val tag = n.tag.ifBlank { n.link }
        val builder = base(context, CHANNEL_POSTS, n.link, tag)
            .setContentTitle(n.title)
            .setContentText(text)
            .setSubText(n.place.ifBlank { null })
        bitmap(context, n.senderPhoto, 192, circle = true)?.let { builder.setLargeIcon(it) }
        val photo = n.image.takeIf { it.isNotBlank() }?.let { bitmap(context, "/api/files/$it?go=view", 1080, circle = false) }
        builder.setStyle(
            if (photo != null) NotificationCompat.BigPictureStyle().bigPicture(photo).setSummaryText(text)
            else NotificationCompat.BigTextStyle().bigText(text),
        )
        post(context, tag, builder)
    }

    /** After Reply: the chat's notification shows the answer, quietly, then clears itself. */
    fun replied(context: Context, conversationId: String, text: String) {
        val tag = chatTag(conversationId)
        val style = previousStyle(context, tag) ?: NotificationCompat.MessagingStyle(me())
        style.addMessage(text, System.currentTimeMillis(), null as Person?)
        val builder = base(context, CHANNEL_CHATS, "/chats/$conversationId", tag)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(GROUP_CHATS)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(5_000)
        post(context, tag, builder)
    }

    /** The chat was read (Mark read, or opened in the app): its notification goes. */
    fun cancelChat(context: Context, conversationId: String) {
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(chatTag(conversationId), ID)
        val left = active(context).count { it.notification.group == GROUP_CHATS && it.tag != SUMMARY_TAG }
        if (left == 0) manager.cancel(SUMMARY_TAG, ID)
    }

    /** After the app updated itself: a tap opens it again, where the "What's new" popup is waiting. */
    fun showUpdated(context: Context) {
        val builder = base(context, CHANNEL_OTHER, "/home", "updated")
            .setContentTitle("${Config.APP_NAME} was updated")
            .setContentText("Tap to see what's new")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        post(context, "updated", builder)
    }

    /** The background check found a newer release: a tap opens the app, where the update popup waits. */
    fun showUpdateAvailable(context: Context, version: String) {
        val builder = base(context, CHANNEL_OTHER, "/home", "update-available")
            .setContentTitle("${Config.APP_NAME} v$version is out")
            .setContentText("Tap to update")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        post(context, "update-available", builder)
    }

    /* ─────────────────────────────── Pieces ─────────────────────────────── */

    /** What the Notifications page files it under. */
    private fun kindOf(kind: String, link: String, tag: String?) = when {
        link.startsWith("/chats/") -> "chats"
        kind == "announcement" -> "announcements"
        kind == "channel" -> "channels"
        link.startsWith("/results/") -> "results"
        link.startsWith("/support") -> "support"
        tag?.startsWith("event:") == true -> "reminders"
        else -> "other"
    }

    private fun log(context: Context, key: String, kind: String, title: String, body: String, link: String, photo: String) {
        val app = context.applicationContext as? CtrlapsApplication ?: return
        runCatching { app.notificationLog.add(LoggedNotification(key, kind, title, body, link, System.currentTimeMillis(), photo)) }
    }

    private fun me() = Person.Builder().setKey("me").setName("You").build()

    private fun active(context: Context) =
        runCatching { context.getSystemService(NotificationManager::class.java).activeNotifications.toList() }.getOrDefault(emptyList())

    /** The chat's notification still showing, as a style to add the next message to. */
    private fun previousStyle(context: Context, tag: String): NotificationCompat.MessagingStyle? =
        active(context).firstOrNull { it.tag == tag && it.id == ID }?.notification?.let {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it)
        }

    /** Bundles the chat notifications into one stack. */
    private fun postSummary(context: Context) {
        val chats = active(context).count { it.notification.group == GROUP_CHATS && it.tag != SUMMARY_TAG }
        val builder = NotificationCompat.Builder(context, CHANNEL_CHATS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.gold))
            .setContentTitle("New messages")
            .setStyle(NotificationCompat.InboxStyle().setSummaryText(if (chats > 1) "$chats chats" else null))
            .setGroup(GROUP_CHATS)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .setContentIntent(open(context, "/chats", SUMMARY_TAG.hashCode()))
        post(context, SUMMARY_TAG, builder)
    }

    private fun base(context: Context, channel: String, link: String, tag: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_notification)
        .setColor(context.getColor(R.color.gold))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setDefaults(NotificationCompat.DEFAULT_ALL)
        .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        .setAutoCancel(true)
        .setContentIntent(open(context, link, 31 * tag.hashCode()))

    private fun post(context: Context, tag: String, builder: NotificationCompat.Builder) {
        runCatching { NotificationManagerCompat.from(context).notify(tag, ID, builder.build()) }
    }

    private fun open(context: Context, link: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_LINK, link)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** A button handled in the background ([NotificationActionReceiver]); each its own request code, so none replaces another. */
    private fun action(context: Context, what: String, conversationId: String, requestCode: Int, mutable: Boolean): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(what)
            .putExtra(NotificationActionReceiver.EXTRA_CONVERSATION, conversationId)
        // Reply's box writes its text into the intent, which needs a mutable one.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (mutable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else if (mutable) 0 else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    private fun replyAction(context: Context, conversationId: String, tag: String): NotificationCompat.Action {
        val input = RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY).setLabel("Reply").build()
        return NotificationCompat.Action.Builder(0, "Reply", action(context, NotificationActionReceiver.REPLY, conversationId, 31 * tag.hashCode() + 1, mutable = true))
            .addRemoteInput(input)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
    }

    /** A photo for a notification, fetched with the app's own sign-in; null if it can't come in a few seconds. */
    private fun bitmap(context: Context, path: String, size: Int, circle: Boolean): Bitmap? {
        if (path.isBlank()) return null
        val app = context.applicationContext as CtrlapsApplication
        val request = ImageRequest.Builder(context)
            .data(app.api.absolute(path))
            .size(size)
            .allowHardware(false)
            .apply { if (circle) transformations(CircleCropTransformation()) }
            .build()
        return runCatching {
            runBlocking { withTimeoutOrNull(8_000) { (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap() } }
        }.getOrNull()
    }
}
