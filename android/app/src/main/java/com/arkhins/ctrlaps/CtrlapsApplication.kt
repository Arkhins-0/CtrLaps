package com.arkhins.ctrlaps

import kotlinx.coroutines.launch
import android.net.Network
import android.net.ConnectivityManager
import android.app.Application
import androidx.compose.runtime.staticCompositionLocalOf
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.arkhins.ctrlaps.data.Prefetch
import com.arkhins.ctrlaps.data.Outbox
import com.arkhins.ctrlaps.data.AppUpdater
import com.arkhins.ctrlaps.data.ChatCache
import com.arkhins.ctrlaps.data.ChatMedia
import com.arkhins.ctrlaps.data.LocalStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import com.arkhins.ctrlaps.data.Documents
import com.arkhins.ctrlaps.data.SessionStore
import com.arkhins.ctrlaps.data.UpdateChecker
import com.arkhins.ctrlaps.data.WhatsNewStore
import com.arkhins.ctrlaps.data.CtrlapsApi
import com.arkhins.ctrlaps.push.Notifications

/** One place for the objects that live as long as the process. */
class CtrlapsApplication : Application(), ImageLoaderFactory {
    /** Bumped after volunteers are moved, so the group page showing refreshes. */
    val moveTick = androidx.compose.runtime.mutableIntStateOf(0)

    /** Who this device is signed in as. */
    val session: SessionStore by lazy { SessionStore(this) }

    /** The server. */
    val api: CtrlapsApi by lazy { CtrlapsApi(session) }

    /** Opens documents from the app's own folder (Android/data/…/files/CTRLAPS_Documents). */
    val documents: Documents by lazy { Documents(this, api, chatMedia) }

    /** Work that outlives a screen: fetching chat pictures and voice notes, background syncs. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Attachments from private chats, kept on the phone. */
    val chatMedia: ChatMedia by lazy { ChatMedia(this, api) }

    /** Who this phone has checked on Verify, and who is starred. */
    val verifyHistory: com.arkhins.ctrlaps.data.VerifyHistory by lazy { com.arkhins.ctrlaps.data.VerifyHistory(this) }

    /** The phone's own copy of every private chat. */
    val chatCache: ChatCache by lazy { ChatCache(this, api, chatMedia, appScope) }

    /** Messages written offline (or not yet answered), and files still going up, sent the moment the network is back. */
    val outbox: Outbox by lazy { Outbox(this, api, chatCache, chatMedia, documents, appScope) }

    /** Everything the app shows, brought onto the phone in the background. */
    val prefetch: Prefetch by lazy { Prefetch(this) }

    /** The phone's copy of every other page: announcements, channels, schedule, people, the account. */
    val store: LocalStore by lazy { LocalStore(this, api, chatMedia, appScope) }

    /** Asks the server (or GitHub) what the latest release is. */
    val updates: UpdateChecker by lazy { UpdateChecker() }

    /** Downloads a release APK and installs it through the package installer. */
    val updater: AppUpdater by lazy { AppUpdater(this) }

    /**
     * Why the last install failed, from [com.arkhins.ctrlaps.data.UpdateInstallReceiver],
     * for the update dialog to show. Cleared by whoever shows it.
     */
    val installFailure = MutableStateFlow<String?>(null)

    /** Which version the user has seen, and the notes of an update about to install. */
    val whatsNew: WhatsNewStore by lazy { WhatsNewStore(this) }

    /** Who is signed in, once /api/me has answered; screens outside the view model read it here. */
    @Volatile
    var currentUserId: String? = null

    override fun onCreate() {
        super.onCreate()
        session.load()
        Notifications.createChannel(this)
        runCatching {
            getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) {
                    api.online.value = false
                }

                override fun onAvailable(network: Network) {
                    appScope.launch { runCatching { api.getText("/api/health") } }
                }
            })
        }
        // Whatever was left queued when the app last closed goes out as soon as it can.
        appScope.launch { outbox }
        // Everything onto the phone: now, each time the network is back, and every 15 minutes even while closed.
        Prefetch.schedule(this)
        appScope.launch { api.online.collect { if (it) prefetch.run() } }
    }

    /** Event reminders set again from the upcoming list (after an answer, or on Home). */
    fun refreshEventReminders() {
        appScope.launch {
            runCatching { store.fetch("/api/events/upcoming", com.arkhins.ctrlaps.data.UpcomingEventsResponse.serializer()) }
                .getOrNull()?.let { com.arkhins.ctrlaps.data.EventReminders.sync(this@CtrlapsApplication, it.events) }
        }
    }

    /** Profile photos come from our API, so Coil's client must carry the session. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this).okHttpClient { api.http }.respectCacheHeaders(false).crossfade(true).build()
}

val LocalApp = staticCompositionLocalOf<CtrlapsApplication> { error("CtrlapsApplication is not provided") }
