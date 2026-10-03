package com.arkhins.ctrlaps.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import coil.imageLoader
import coil.request.ImageRequest
import com.arkhins.ctrlaps.CtrlapsApplication
import com.arkhins.ctrlaps.ui.screens.refreshHomeSnapshot
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.KSerializer
import java.util.concurrent.TimeUnit

/**
 * Everything the app shows, brought onto the phone in the background, so
 * any page opens at once and works with no signal: the account, the next
 * race, seasons, race weekends and their channels, the announcements,
 * people and each person's page, every chat and group with all their
 * attachments and profile photos, the channels list and each race
 * category's channel, standings (every category, this season and archived
 * ones) and each session's results, teams and categories, the FAQs and
 * support tickets, archived seasons, What's new and the legal pages. It
 * runs when the app starts, whenever
 * the network comes back, and every 15 minutes through WorkManager even
 * while the app is closed. Nothing fetched here is marked read.
 */
class Prefetch(private val app: CtrlapsApplication) {
    private val lock = Mutex()
    @Volatile private var lastRun = 0L

    /** Bring everything down. Skipped while signed out, while a run is going, or within a minute of the last one (unless [force]). */
    suspend fun run(force: Boolean = false) {
        if (!app.session.signedIn) return
        if (!force && System.currentTimeMillis() - lastRun < MIN_GAP_MS) return
        if (!lock.tryLock()) return
        try {
            lastRun = System.currentTimeMillis()
            everything()
        } finally {
            lock.unlock()
        }
    }

    private suspend fun everything() = coroutineScope {
        val store = app.store
        val photos = mutableSetOf<String?>()
        suspend fun <T> keep(path: String, serializer: KSerializer<T>, key: String = path): T? =
            runCatching { store.fetch(path, serializer, key) }.getOrNull()

        // The pages that stand alone.
        listOf(
            async { keep("/api/me", Me.serializer())?.let { photos += it.user.photoUrl } },
            async { keep("/api/next-race", NextRace.serializer()) },
            async { keep("/api/messages", MessagesResponse.serializer())?.messages?.forEach { photos += it.sender?.photoUrl } },
            // The channels list, and each race category's channel in it (read=0, kept where its page looks).
            async {
                keep("/api/channels", ChannelsResponse.serializer())?.categories?.forEach { c ->
                    keep("/api/categories/${c.id}/channel?read=0", CategoryChannelResponse.serializer(), key = "/api/categories/${c.id}/channel")
                        ?.messages?.forEach { photos += it.sender?.photoUrl }
                }
            },
            async { keep("/api/app-version/releases", ChangelogResponse.serializer()) },
            async { keep("/api/legal/privacy", LegalDoc.serializer()) },
            async { keep("/api/legal/terms", LegalDoc.serializer()) },
            async { keep("/api/users?chat=1", UsersResponse.serializer())?.users?.forEach { photos += it.photoUrl } },
            async { keep("/api/users?group=1", UsersResponse.serializer()) },
            // Upcoming events, and their reminders set again (alarms don't outlive a restart of the phone).
            async { keep("/api/events/upcoming", UpcomingEventsResponse.serializer())?.let { EventReminders.sync(app, it.events) } },
        ).awaitAll()

        // Seasons, and each archived one's read-only record and standings.
        val seasons = keep("/api/seasons", SeasonsResponse.serializer())?.seasons.orEmpty()
        seasons.filter { it.status == "archived" }.map { s ->
            async { keep("/api/seasons/${s.id}?archive=1", SeasonArchive.serializer()) }
        }.awaitAll()

        // Standings: the page as it first opens, every category of this season, and archived seasons' (the trophy on
        // an archived season); and each session with results. Paths as StandingsScreen and ResultsScreen ask them.
        val sessions = mutableSetOf<String>()
        suspend fun standings(path: String) = keep(path, StandingsResponse.serializer())?.also { r -> r.sessions.forEach { sessions += it.id } }
        val first = standings("/api/standings")
        first?.categories?.map { c -> async { standings("/api/standings?category=${c.id}") } }?.awaitAll()
        seasons.filter { it.status == "archived" }.map { s ->
            async {
                standings("/api/standings?season=${s.id}")?.categories?.forEach { c -> standings("/api/standings?season=${s.id}&category=${c.id}") }
            }
        }.awaitAll()
        sessions.map { id -> async { keep("/api/sessions/$id/results", SessionResultsResponse.serializer()) } }.awaitAll()

        // Teams and the season's categories (People → Teams / Categories; refused for those who can't see them).
        keep("/api/teams", TeamsResponse.serializer())

        // Support: the FAQs, your tickets (each tab), and each ticket's chat (asked with read=0, kept where the ticket
        // page looks for it).
        keep("/api/support/faqs", FaqsResponse.serializer())
        keep("/api/me/email-settings", EmailSettings.serializer())
        val tickets = listOf("open", "closed", "all").map { s -> async { keep("/api/support/tickets?status=$s", TicketsResponse.serializer()) } }.awaitAll()
        tickets.lastOrNull()?.tickets?.map { t ->
            async { keep("/api/support/tickets/${t.id}?read=0", TicketViewResponse.serializer(), key = "/api/support/tickets/${t.id}") }
        }?.awaitAll()

        // Volunteer groups (admins, coordinators, volunteers): the list, each managed group's page, and each chat; and
        // the admin's or coordinator's Today card.
        keep("/api/me/dashboard", DashboardResponse.serializer())
        keep("/api/volunteer-groups", VolunteerGroupsResponse.serializer())?.groups?.map { g ->
            async {
                if (g.canManage) keep("/api/volunteer-groups/${g.id}", VolunteerGroupDetailResponse.serializer())
                runCatching { app.chatCache.sync(g.conversationId, markRead = false) }.getOrNull()?.messages?.forEach { photos += it.sender?.photoUrl }
                keep("/api/groups/${g.conversationId}", GroupResponse.serializer())?.group?.members?.forEach { photos += it.photoUrl }
            }
        }?.awaitAll()

        // Race weekends and their channels (asked with read=0, kept where the weekend page looks for them).
        keep("/api/weekends", WeekendsResponse.serializer())?.weekends?.map { w ->
            async {
                keep("/api/weekends/${w.id}", WeekendResponse.serializer())
                keep("/api/weekends/${w.id}/channel?read=0", ChannelResponse.serializer(), key = "/api/weekends/${w.id}/channel")
                    ?.messages?.forEach { photos += it.sender?.photoUrl }
            }
        }?.awaitAll()

        // People below, and each one's page.
        keep("/api/users", UsersResponse.serializer())?.users?.map { u ->
            photos += u.photoUrl
            async { keep("/api/users/${u.id}", UserResponse.serializer()) }
        }?.awaitAll()

        // Every chat and group: the list, the messages (not marked read), who is on the other side, and every attachment.
        runCatching { app.api.get("/api/conversations", ConversationsResponse.serializer()).conversations }.getOrNull()?.let { list ->
            app.chatCache.saveList(list.filter { it.lastMessageAt != null })
            list.map { c ->
                photos += c.other.photoUrl
                async {
                    val chat = runCatching { app.chatCache.sync(c.id, markRead = false) }.getOrNull()
                    if (c.kind == "group") keep("/api/groups/${c.id}", GroupResponse.serializer())?.group?.members?.forEach { photos += it.photoUrl }
                    else keep("/api/conversations/${c.id}/profile", Verified.serializer())
                    chat?.messages?.forEach { m ->
                        photos += m.sender?.photoUrl
                        m.attachments.filter { app.chatMedia.wanted(it) && app.chatMedia.local(it) == null }.forEach { f -> runCatching { app.chatMedia.fetch(f) } }
                    }
                }
            }.awaitAll()
        }

        // Home, rebuilt from all of the above.
        runCatching { refreshHomeSnapshot(app) }

        // Profile and group photos into the image cache, so faces show offline too.
        photos.filterNotNull().forEach { url ->
            app.imageLoader.enqueue(ImageRequest.Builder(app).data(app.api.absolute(url)).build())
        }
    }

    companion object {
        private const val MIN_GAP_MS = 60_000L
        private const val WORK = "ctrlaps-prefetch"

        /** The every-15-minutes run, whenever there is a network, app open or not. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PrefetchWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** WorkManager's hook for [Prefetch]: the run while the app is closed. */
class PrefetchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as CtrlapsApplication).prefetch.run(force = true)
        return Result.success()
    }
}
