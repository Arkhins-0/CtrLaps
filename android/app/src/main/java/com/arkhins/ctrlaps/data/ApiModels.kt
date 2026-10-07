package com.arkhins.ctrlaps.data

import kotlinx.serialization.Serializable

/*
 * What the server sends. Field names match the JSON of the website's API
 * (the TypeScript files under src/lib) exactly; unknown keys are ignored.
 */

@Serializable
data class PublicUser(
    val id: String,
    val email: String,
    val role: String,
    val roleLabel: String,
    val status: String,
    val statusLabel: String,
    val name: String? = null,
    val dob: String? = null,
    val phone: String? = null,
    val teamName: String? = null,
    /** The team as a record, for its page. */
    val teamId: String? = null,
    val parentId: String? = null,
    val photoUrl: String? = null,
    val verifyCode: String,
    val profileComplete: Boolean = false,
    /** From /api/users?group=1: "direct" (invite) or "request" (someone higher up, asked). */
    val groupMode: String? = null,
    /** A developer: an admin who also answers support, shown as "Developer" (People hides them to start). */
    val isDev: Boolean = false,
) {
    val displayName: String get() = name ?: email
}

@Serializable
data class ParentInfo(val id: String, val name: String, val roleLabel: String)

@Serializable
data class Me(
    val user: PublicUser,
    val qrUrl: String,
    val parent: ParentInfo? = null,
    val canCreate: List<String> = emptyList(),
    /** Sends announcements (admins and coordinators), to anyone. */
    val canAnnounce: Boolean = false,
    val canPostChannel: Boolean = false,
    val canRelay: Boolean = false,
    val canBulkEmail: Boolean = false,
    val isAdmin: Boolean = false,
    /** A developer: answers support tickets, and may make other admins developers. */
    val isDev: Boolean = false,
    val unread: Int = 0,
    val unreadChats: Int = 0,
    val unreadHome: Int = 0,
    /** Unread support replies (a developer: new messages on tickets). */
    val unreadSupport: Int = 0,
    val pushConfigured: Boolean = false,
    /** "My categories" this season; null = everything. */
    val categoryIds: List<String>? = null,
)

@Serializable
data class LoginResponse(val token: String? = null, val user: PublicUser)

@Serializable
data class InviteInfo(val email: String, val role: String = "", val roleLabel: String = "")

@Serializable
data class ResetInfo(val email: String)

@Serializable
data class Sender(val id: String, val name: String, val role: String, val roleLabel: String, val photoUrl: String? = null)

@Serializable
/** [document]: sent through "Document", so it shows, opens and saves as a document whatever its type. */
data class FileInfo(
    val id: String,
    val name: String,
    val mime: String,
    val size: Long = 0,
    val document: Boolean = false,
    /** A photo's tiny preview (base64 JPEG), shown blurred until the photo is on the phone. */
    val thumb: String? = null,
)

@Serializable
data class Message(
    val id: String,
    val conversationId: String? = null,
    val kind: String = "broadcast",
    val weekendId: String? = null,
    /** A category channel's category. */
    val categoryId: String? = null,
    val sender: Sender? = null,
    val body: String = "",
    /** The first attachment (all a server before multi-attachments sends). */
    val file: FileInfo? = null,
    /** Every attachment, in the order they were picked: photos, documents, audio. */
    val files: List<FileInfo> = emptyList(),
    val urgent: Boolean = false,
    val createdAt: String,
    val readAt: String? = null,
    val mine: Boolean = false,
    /** The message this one answers, as it read when fetched. */
    val replyTo: ReplyRef? = null,
    val editedAt: String? = null,
    val deleted: Boolean = false,
    /** Last edited, deleted, delivered or read; the phone asks for changes after the newest of these. */
    val changedAt: String? = null,
    /** Your own private message: "sent", "delivered" or "read" (one, two, three ticks). */
    val status: String? = null,
    /** Passed on from another chat. */
    val forwarded: Boolean = false,
    /** This message is a group invitation. */
    val groupInvite: GroupInvite? = null,
    /** A line in a group chat about the group itself (joined, left, …), not something someone said. */
    val event: String? = null,
    /** Yours: the id this phone gave it when sending (a `local-` id), so the server's copy replaces the phone's. */
    val clientId: String? = null,
    /** Photos sent or forwarded together share a batch (one grid), each with its place in it. */
    val batchId: String? = null,
    val batchPos: Int? = null,
    /** A poll on this message (groups and announcements), as this person sees it. */
    val poll: Poll? = null,
    /** An event on this message (groups and announcements), as this person sees it. */
    val calendarEvent: CalendarEvent? = null,
    /** The card for the first link in the text, unless the sender closed it. */
    val linkPreview: LinkPreview? = null,
)

/** A link's card: what the page says about itself; [image] is a path through the server (or null). */
@Serializable
data class LinkPreview(val url: String, val title: String = "", val description: String = "", val site: String = "", val image: String? = null)

@Serializable
data class LinkPreviewAnswer(val preview: LinkPreview? = null)

/** An event: when (and until when), where, the reminder, the going / not going counts and this person's answer. */
@Serializable
data class CalendarEvent(
    val id: String,
    val name: String,
    val description: String = "",
    val startsAt: String,
    val endsAt: String? = null,
    val location: String = "",
    val reminderMinutes: Int? = null,
    val going: Int = 0,
    val notGoing: Int = 0,
    /** "going", "not_going" or null. */
    val myAnswer: String? = null,
    /** Whether names come with the counts (a group's members; an announcement's sender). */
    val named: Boolean = false,
    val goingNames: List<String> = emptyList(),
    val notGoingNames: List<String> = emptyList(),
)

/** An event that hasn't ended, for Home's card and the reminders. */
@Serializable
data class UpcomingEvent(
    val id: String,
    val messageId: String,
    /** The group it was posted in; null for an announcement. */
    val conversationId: String? = null,
    /** The group's name, or "Announcement". */
    val place: String = "",
    val name: String,
    val startsAt: String,
    val endsAt: String? = null,
    val location: String = "",
    val reminderMinutes: Int? = null,
    val myAnswer: String? = null,
    val going: Int = 0,
)

@Serializable
data class UpcomingEventsResponse(val events: List<UpcomingEvent> = emptyList())

/** Who picked an option (shown where the poll names its voters). */
@Serializable
data class Voter(val id: String, val name: String)

@Serializable
data class PollOption(val id: String, val text: String, val votes: Int = 0, val mine: Boolean = false, val voters: List<Voter> = emptyList())

/** A poll: its question, whether several answers are allowed, how many answered, and each option's count. */
@Serializable
data class Poll(
    val id: String,
    val question: String,
    val multiple: Boolean = false,
    val voters: Int = 0,
    /** Whether names come with the counts (a group's members; an announcement's sender). */
    val named: Boolean = false,
    val options: List<PollOption> = emptyList(),
)

/** The vote route's answer: the message with its poll brought up to date. */
@Serializable
data class VoteAnswer(val message: Message? = null)

/** An invitation to a group, carried by a message in a private chat. */
@Serializable
data class GroupInvite(
    val id: String,
    val groupId: String,
    val groupName: String = "Group",
    /** "pending", "accepted", "declined", "expired" or "revoked". Good once, and for two days. */
    val status: String = "pending",
    /** Sent to someone higher up: a join request. */
    val upward: Boolean = false,
    val expiresAt: String? = null,
)

/** Who a message you sent has reached, and when. */
@Serializable
data class MessageInfo(val sentAt: String, val recipients: List<MessageRecipient> = emptyList())

@Serializable
data class MessageRecipient(
    val id: String,
    val name: String,
    val roleLabel: String = "",
    val photoUrl: String? = null,
    val deliveredAt: String? = null,
    val readAt: String? = null,
)

/** What creating a group or inviting to one answers: who could not be brought in. */
@Serializable
data class InviteResult(val id: String? = null, val added: Int = 0, val requested: Int = 0, val skipped: List<String> = emptyList())

@Serializable
data class GroupMember(
    val id: String,
    val name: String,
    val roleLabel: String = "",
    val photoUrl: String? = null,
    val groupRole: String = "member",
    /** In a volunteer group's chat: "full", "no_messages" or "read_only". */
    val permission: String = "full",
    val userRole: String = "",
)

@Serializable
data class GroupInfo(
    val id: String,
    val name: String,
    val photoUrl: String? = null,
    val sendPolicy: String = "everyone",
    val members: List<GroupMember> = emptyList(),
    val invited: List<GroupMember> = emptyList(),
    val myRole: String? = null,
    val canSend: Boolean = true,
    val createdBy: String? = null,
    /** A volunteer group's chat: its members follow the group (nobody is added, removed or leaves by hand). */
    val volunteerGroupId: String? = null,
    val closed: Boolean = false,
    val myPermission: String = "full",
    /** Why the box to write in is not shown. */
    val sendNote: String? = null,
    /** May set volunteers' permissions (its coordinator or an admin). */
    val canLimit: Boolean = false,
) {
    /** The group where a chat's other side would be: name, photo, and "Group · n members" for the designation. */
    fun asOther(): OtherUser = OtherUser(id, name, "group", "Group · ${members.size} member${if (members.size == 1) "" else "s"}", photoUrl)
}

@Serializable
data class GroupResponse(val group: GroupInfo)

/** The channels page: seasons, the current one first, each with its race weekends' channels. */
@Serializable
data class ChannelsResponse(val seasons: List<ChannelSeason> = emptyList(), val categories: List<CategoryChannel> = emptyList())

/** A race category's channel (this season; everyone sees them all). */
@Serializable
data class CategoryChannel(
    val id: String,
    val name: String,
    val code: String,
    val color: String,
    val unread: Int = 0,
    val lastMessageAt: String? = null,
    val lastMessage: String? = null,
    /** This person muted its notifications. */
    val muted: Boolean = false,
)

@Serializable
data class CategoryChannelInfo(val id: String, val name: String, val code: String, val color: String, val seasonName: String = "")

@Serializable
data class CategoryChannelResponse(
    val channelId: String,
    val category: CategoryChannelInfo,
    val open: Boolean,
    val canPost: Boolean,
    val messages: List<Message>,
    val muted: Boolean = false,
    /** Why it is closed: "archived" (its season is archived) or "admin" (an admin closed it). */
    val closedReason: String? = null,
)

/** The answer to closing or reopening a category's channel. */
@Serializable
data class OpenResponse(val open: Boolean = true)

/** The answer to muting or unmuting a channel. */
@Serializable
data class MuteResponse(val muted: Boolean = false)

@Serializable
data class ChannelSeason(val id: String, val name: String, val current: Boolean = false, val status: String = "active", val weekends: List<ChannelWeekend> = emptyList())

@Serializable
data class ChannelWeekend(
    val id: String,
    val name: String,
    val startsOn: String = "",
    val endsOn: String = "",
    val channelOpen: Boolean = true,
    val unread: Int = 0,
    val lastMessageAt: String? = null,
    val lastMessage: String? = null,
    val managers: List<GroupMember> = emptyList(),
    /** This person muted its notifications. */
    val muted: Boolean = false,
)

@Serializable
data class ManagersResponse(
    val managers: List<GroupMember> = emptyList(),
    /** For admins: who may be picked (active coordinators), as short cards like [managers]. */
    val candidates: List<GroupMember> = emptyList(),
)

@Serializable
data class InviteAnswer(val groupId: String, val accepted: Boolean = false)

/** The Privacy Policy or the Terms, as /api/legal/<doc> gives them. */
@Serializable
data class LegalDoc(val title: String, val updated: String = "", val intro: List<LegalBlock> = emptyList(), val sections: List<LegalSection> = emptyList())

@Serializable
data class LegalSection(val title: String, val blocks: List<LegalBlock> = emptyList())

/** A paragraph (p) or a bulleted list (ul). */
@Serializable
data class LegalBlock(val p: String? = null, val ul: List<String>? = null)

@Serializable
data class ChangelogEntry(val version: String, val date: String = "", val changes: List<String> = emptyList(), val sections: List<NoteSection> = emptyList())

@Serializable
data class ChangelogResponse(val releases: List<ChangelogEntry> = emptyList())

/** What sending to a chat answers: the new message, so the phone need not ask again. */
@Serializable
data class ChatSent(val id: String, val message: Message? = null)

@Serializable
data class ReplyRef(
    val id: String,
    val senderName: String = "",
    val mine: Boolean = false,
    val body: String = "",
    val fileName: String? = null,
    val fileMime: String? = null,
    /** The quoted file went through "Document": named as a document, not a photo or voice note. */
    val fileDocument: Boolean = false,
    val deleted: Boolean = false,
)

@Serializable
data class MessagesResponse(val messages: List<Message>)

@Serializable
data class UnseenResponse(val messages: List<Message>, val unread: Int, val unreadChats: Int = 0, val unreadHome: Int = 0, val now: String)

@Serializable
data class OtherUser(val id: String, val name: String, val role: String = "", val roleLabel: String = "", val photoUrl: String? = null, val status: String = "active")

@Serializable
data class Conversation(val id: String, val kind: String = "direct", val other: OtherUser, val iOpened: Boolean = false, val lastMessageAt: String? = null, val lastMessage: String? = null, val lastStatus: String? = null, val unread: Int = 0, val messages: Int = 0)

@Serializable
data class ConversationsResponse(val conversations: List<Conversation>)

@Serializable
data class ConversationDetail(
    val id: String,
    val iOpened: Boolean = false,
    val other: OtherUser? = null,
    /** Set for a group chat. */
    val group: GroupInfo? = null,
    val messages: List<Message>,
    val liveIds: List<String>? = null,
)

@Serializable
data class ChannelResponse(
    val channelId: String,
    val open: Boolean,
    val canPost: Boolean,
    val messages: List<Message>,
    val muted: Boolean = false,
    /** Why it is closed: "admin", "season" (closed when its season was archived) or "archived" (its season is archived now). */
    val closedReason: String? = null,
)

@Serializable
data class RaceSession(
    val id: String,
    val weekendId: String = "",
    val name: String,
    val startsAt: String,
    val endsAt: String,
    /** Its race category; null when it is for everyone. */
    val categoryId: String? = null,
)

/** A race category (class) of a season: "ITC", its colour and order. */
@Serializable
data class Category(
    val id: String,
    val seasonId: String,
    val name: String,
    val code: String,
    val color: String,
    val position: Int = 0,
    /** The points table; null when points are typed by hand. */
    val scoring: Scoring? = null,
)

/** A category's points table (src/lib/scoring.ts): points by finishing position, for DNF / DNS / DSQ, and bonuses. */
@Serializable
data class Scoring(
    val points: List<Double> = emptyList(),
    val dnf: Double = 0.0,
    val dns: Double = 0.0,
    val dsq: Double = 0.0,
    val pole: Double = 0.0,
    val fastestLap: Double = 0.0,
) {
    /** What one result scores: its position (or DNF / DNS / DSQ), plus pole and fastest lap. */
    fun pointsFor(status: String, position: Int?, pole: Boolean, fastestLap: Boolean): Double {
        val base = when (status) {
            "finished" -> position?.let { points.getOrNull(it - 1) } ?: 0.0
            "dnf" -> dnf
            "dns" -> dns
            "dsq" -> dsq
            else -> 0.0
        }
        return Math.round((base + (if (pole) this.pole else 0.0) + (if (fastestLap) this.fastestLap else 0.0)) * 100) / 100.0
    }

    /** "25, 18, 15… · DNF 0 · pole +1 · fastest lap +1" */
    fun summary(): String {
        val head = points.take(6).joinToString(", ") { pointsText(it) } + if (points.size > 6) "…" else ""
        val extras = listOfNotNull(
            dnf.takeIf { it > 0 }?.let { "DNF ${pointsText(it)}" },
            dns.takeIf { it > 0 }?.let { "DNS ${pointsText(it)}" },
            dsq.takeIf { it > 0 }?.let { "DSQ ${pointsText(it)}" },
            pole.takeIf { it > 0 }?.let { "pole +${pointsText(it)}" },
            fastestLap.takeIf { it > 0 }?.let { "fastest lap +${pointsText(it)}" },
        )
        return (listOf(head.ifBlank { "No points" }) + extras).joinToString(" · ")
    }
}

/** Points without a needless ".0". */
fun pointsText(p: Double): String = if (p % 1.0 == 0.0) p.toLong().toString() else p.toString()

@Serializable
data class Weekend(
    val id: String,
    val name: String,
    val venue: String = "",
    val city: String = "",
    val country: String = "",
    val timezone: String = "UTC",
    val startsOn: String,
    val endsOn: String,
    val channelOpen: Boolean = true,
    val seasonId: String? = null,
    val seasonName: String? = null,
    val seasonArchived: Boolean = false,
    /** The race categories running this round. */
    val categoryIds: List<String> = emptyList(),
    /** The weekend's photo (the track), versioned; null when it has none. */
    val photoUrl: String? = null,
    val sessions: List<RaceSession> = emptyList(),
) {
    val place: String get() = listOf(venue, city, country).filter { it.isNotBlank() }.joinToString(", ")
}

@Serializable
data class WeekendsResponse(val weekends: List<Weekend>, val categories: List<Category> = emptyList())

@Serializable
data class WeekendResponse(val weekend: Weekend, val categories: List<Category> = emptyList(), val channelId: String? = null, val canPost: Boolean = false)

@Serializable
data class NextRace(
    val state: String,
    val weekend: Weekend? = null,
    val session: RaceSession? = null,
    val later: List<RaceSession> = emptyList(),
    val now: String? = null,
)

@Serializable
data class UsersResponse(val users: List<PublicUser>, val categories: List<RosterCategory> = emptyList())

/** A category this season with the people tied to it (its racers, its teams' crew and managers, its race officials). */
@Serializable
data class RosterCategory(val id: String, val name: String, val code: String, val color: String, val memberIds: List<String> = emptyList())

@Serializable
data class UserResponse(
    val user: PublicUser,
    val qrUrl: String? = null,
    val canEdit: Boolean = false,
    /** The current season's race categories, the ones given to this person, their team's entries, and whether the viewer may change theirs. */
    val raceCategories: List<Category> = emptyList(),
    val categoryIds: List<String> = emptyList(),
    val teamCategoryIds: List<String> = emptyList(),
    val canSetCategories: Boolean = false,
    /** The viewer is a developer and this person an active admin: they may be made a developer, or stop being one. */
    val canSetDev: Boolean = false,
    /** The viewer is a developer: they may delete this account on the person's request; when one waits, its date. */
    val canDelete: Boolean = false,
    val deletionDueAt: String? = null,
)

@Serializable
data class CategoryIdsResponse(val categoryIds: List<String> = emptyList())

/** The categories a user (no role yet) may follow this season, and the ones they follow. */
@Serializable
data class FollowingResponse(val categories: List<Category> = emptyList(), val categoryIds: List<String> = emptyList(), val canFollow: Boolean = false)

@Serializable
data class IdResponse(val id: String)

@Serializable
data class SentResponse(val id: String, val delivered: Int = 0)

@Serializable
data class UploadSlot(val id: String, val uploadUrl: String, val direct: Boolean, val maxProxyBytes: Long = 0)

@Serializable
data class FileMeta(val id: String, val name: String, val mime: String, val size: Long = 0, val downloadUrl: String, val viewUrl: String)

@Serializable
data class Verified(
    val id: String,
    val name: String? = null,
    val role: String,
    val roleLabel: String,
    val teamName: String? = null,
    val status: String,
    val statusLabel: String,
    val verifyCode: String,
    val photoUrl: String? = null,
    val profileComplete: Boolean = false,
    val qrUrl: String? = null,
    /** Their race categories this season, shown as badges. */
    val categories: List<Category> = emptyList(),
)

@Serializable
data class Season(
    val id: String,
    val name: String,
    val startsOn: String,
    val endsOn: String? = null,
    val status: String = "active",
    val archivedAt: String? = null,
    val current: Boolean = false,
    val weekends: Int = 0,
)

@Serializable
data class SeasonsResponse(val seasons: List<Season>)

@Serializable
data class SeasonResponse(val season: Season)

@Serializable
data class ArchivedSender(val id: String, val name: String, val roleLabel: String = "")

@Serializable
data class ArchivedMessage(
    val id: String,
    val body: String = "",
    val urgent: Boolean = false,
    val createdAt: String,
    val sender: ArchivedSender? = null,
    val file: FileInfo? = null,
    val mine: Boolean = false,
)

@Serializable
data class ArchivedWeekend(
    val id: String,
    val name: String,
    val venue: String = "",
    val city: String = "",
    val country: String = "",
    val timezone: String = "UTC",
    val startsOn: String,
    val endsOn: String,
    val sessions: List<RaceSession> = emptyList(),
    val posts: List<ArchivedMessage> = emptyList(),
) {
    val place: String get() = listOf(venue, city, country).filter { it.isNotBlank() }.joinToString(", ")
}

@Serializable
data class ArchivedChat(val other: OtherUser, val messages: List<ArchivedMessage>)

@Serializable
data class SeasonArchive(
    val season: Season,
    val weekends: List<ArchivedWeekend> = emptyList(),
    val announcements: List<ArchivedMessage> = emptyList(),
    val chats: List<ArchivedChat> = emptyList(),
)

@Serializable
data class Ok(val ok: Boolean = true)

/** A message's attachments: all of them, or its one file from an older server. */
val Message.attachments: List<FileInfo> get() = files.ifEmpty { listOfNotNull(file) }

/** This season's standings for one race category (step 5 of the race-categories plan). */
@Serializable
data class StandingsResponse(
    /** The seasons to choose from (the current one first), and the one shown. */
    val seasons: List<StandingsSeason> = emptyList(),
    val seasonId: String? = null,
    val categories: List<Category> = emptyList(),
    /** How many sessions have results in each category, by its id: the number on its chip. */
    val counts: Map<String, Int> = emptyMap(),
    val categoryId: String? = null,
    val drivers: List<DriverStanding> = emptyList(),
    val teams: List<TeamStanding> = emptyList(),
    val sessions: List<StandingSession> = emptyList(),
)

@Serializable
data class DriverStanding(
    val key: String,
    val name: String,
    val userId: String? = null,
    val carNumber: String = "",
    val teamName: String? = null,
    /** Their latest team, for its page. */
    val teamId: String? = null,
    /** Their account's photo (none for a name typed in). */
    val photoUrl: String? = null,
    val points: Double = 0.0,
    val wins: Int = 0,
    val podiums: Int = 0,
    val starts: Int = 0,
    val best: Int? = null,
    /** Their result per session id (sessions they weren't in are left out). */
    val rounds: Map<String, DriverRound> = emptyMap(),
)

/** One driver's result in one session, for the standings grid. */
@Serializable
data class DriverRound(val position: Int? = null, val status: String = "finished", val points: Double = 0.0, val pole: Boolean = false, val fastestLap: Boolean = false)

@Serializable
data class StandingsSeason(val id: String, val name: String, val current: Boolean = false)

@Serializable
data class TeamStanding(val id: String, val name: String, val photoUrl: String? = null, val points: Double = 0.0, val wins: Int = 0, val podiums: Int = 0, val rounds: Map<String, Double> = emptyMap())

@Serializable
data class StandingSession(val id: String, val name: String, val startsAt: String, val weekendName: String = "", val rows: Int = 0)

/** One session's results. */
@Serializable
data class SessionResultsResponse(
    val session: ResultSessionInfo,
    val category: CategoryChannelInfo? = null,
    val results: List<SessionResult> = emptyList(),
    val canEdit: Boolean = false,
    /** For those who may enter results: the teams to pick from (older servers). */
    val teams: List<ResultTeam> = emptyList(),
    /** For those who may enter results: the teams (entered ones first) with their racers in this category. */
    val entrants: List<EntrantTeam> = emptyList(),
    /** The category's points table (null: points are typed), and whether this session scores from it. */
    val scoring: Scoring? = null,
    val scores: Boolean = true,
)

/** A team to pick when entering results, with its racers in the category ("" id: racers with no team). */
@Serializable
data class EntrantTeam(val id: String, val name: String, val entered: Boolean = false, val racers: List<Entrant> = emptyList())

/** A racer to pick: their account, name, and the car number of their last result in the category. */
@Serializable
data class Entrant(val id: String, val name: String, val carNumber: String = "")

@Serializable
data class ResultTeam(val id: String, val name: String, val entered: Boolean = false)

/** People → Teams: every team with this season's entries, and the season's categories. */
@Serializable
data class TeamsResponse(val teams: List<TeamRecord> = emptyList(), val categories: List<Category> = emptyList(), val seasonId: String = "")

@Serializable
data class TeamRecord(val id: String, val name: String, val categoryIds: List<String> = emptyList(), val members: Int = 0, val photoUrl: String? = null)

@Serializable
data class CategoriesResponse(val categories: List<Category> = emptyList())

@Serializable
data class ResultSessionInfo(
    val id: String,
    val name: String,
    val startsAt: String,
    val weekendId: String = "",
    val weekendName: String = "",
    val categoryId: String? = null,
    /** When its results were last sent to the category's followers; null when never. */
    val notifiedAt: String? = null,
)

/** Settings → Email: each kind of email on or off, and which categories' results come. */
@Serializable
data class EmailSettings(
    /** False for volunteers and security: no automatic email, so nothing to choose. */
    val automatic: Boolean = true,
    val kinds: List<EmailKindSetting> = emptyList(),
    val results: List<ResultsCategorySetting> = emptyList(),
)

@Serializable
data class EmailKindSetting(val key: String, val label: String, val hint: String = "", val on: Boolean = true)

@Serializable
data class ResultsCategorySetting(val id: String, val code: String, val name: String = "", val color: String = "#FFD100", val on: Boolean = false, val mine: Boolean = false)

@Serializable
data class SessionResult(
    val position: Int? = null,
    val status: String = "finished",
    val carNumber: String = "",
    val driverName: String,
    val userId: String? = null,
    val teamId: String? = null,
    val teamName: String? = null,
    val points: Double = 0.0,
    val bestLap: String = "",
    val pole: Boolean = false,
    val fastestLap: Boolean = false,
    /** Typed by hand: the table doesn't change it. */
    val manualPoints: Boolean = true,
)

/** The Activity log (developers only): a page of entries, newest first; `next` asks for older ones. */
@Serializable
data class ActivityPage(val entries: List<ActivityEntry> = emptyList(), val next: String? = null)

@Serializable
data class ActivityEntry(
    val id: String,
    val at: String,
    val action: String,
    val title: String,
    val detail: String? = null,
    val actor: ActivityActor? = null,
    val target: ActivityTarget? = null,
)

@Serializable
data class ActivityActor(val id: String, val name: String, val roleLabel: String = "")

/** Who or what it was done to: a person or a race weekend. */
@Serializable
data class ActivityTarget(val id: String, val name: String, val kind: String)

/** The Chats tab's Volunteers page. */
@Serializable
data class VolunteerGroupsResponse(
    val groups: List<VolunteerGroupRow> = emptyList(),
    val canCreate: Boolean = false,
    /** For an admin making a group: who can lead it. */
    val coordinators: List<NamedRef> = emptyList(),
)

@Serializable
data class NamedRef(val id: String, val name: String)

@Serializable
data class VolunteerGroupRow(
    val id: String,
    /** "volunteer" or "delegation". */
    val kind: String = "volunteer",
    val name: String,
    val open: Boolean = true,
    val conversationId: String,
    val coordinator: NamedRef? = null,
    val volunteers: Int = 0,
    val unread: Int = 0,
    val lastMessage: String? = null,
    val lastMessageAt: String? = null,
    val canManage: Boolean = false,
)

@Serializable
data class VolunteerGroupDetailResponse(val group: VolunteerGroupDetail? = null)

@Serializable
data class VolunteerGroupDetail(
    val id: String,
    /** "volunteer" or "delegation". */
    val kind: String = "volunteer",
    val name: String,
    val open: Boolean = true,
    val conversationId: String,
    val coordinator: NamedRef? = null,
    val volunteers: List<VolunteerInGroup> = emptyList(),
    val unassigned: List<NamedRef> = emptyList(),
    val otherGroups: List<OtherVolunteerGroup> = emptyList(),
    val coordinators: List<NamedRef> = emptyList(),
)

@Serializable
data class VolunteerInGroup(val id: String, val name: String, val photoUrl: String? = null, val permission: String = "full", val status: String = "active")

@Serializable
data class OtherVolunteerGroup(val id: String, val name: String, val coordinator: String? = null)

/** The admin's and coordinator's card on Home (null for anyone else). */
@Serializable
data class DashboardResponse(val dashboard: Dashboard? = null)

@Serializable
data class Dashboard(
    val sessions: List<DashboardSession> = emptyList(),
    val changes: List<String> = emptyList(),
    val pending: List<NamedRef> = emptyList(),
    val unfinished: List<NamedRef> = emptyList(),
    val suspended: List<NamedRef> = emptyList(),
)

@Serializable
data class DashboardSession(val id: String, val name: String, val weekendId: String, val weekendName: String, val startsAt: String, val track: String)

/** A team's page: its photo, categories, people, where it stands and its latest results. */
@Serializable
data class TeamPageResponse(
    val team: TeamInfo,
    val categories: List<Category> = emptyList(),
    val people: List<TeamMember> = emptyList(),
    val standings: List<TeamStandingLine> = emptyList(),
    val results: List<TeamResult> = emptyList(),
    /** May change the team's photo. */
    val canEdit: Boolean = false,
    /** The people whose own page this viewer may open. */
    val canOpen: List<String> = emptyList(),
)

@Serializable
data class TeamInfo(val id: String, val name: String, val photoUrl: String? = null)

@Serializable
data class TeamMember(val id: String, val name: String, val role: String, val photoUrl: String? = null, val pending: Boolean = false)

@Serializable
data class TeamStandingLine(val category: Category, val position: Int, val of: Int, val points: Double = 0.0, val wins: Int = 0, val podiums: Int = 0)

@Serializable
data class TeamResult(
    val sessionId: String,
    val sessionName: String,
    val weekendName: String = "",
    val categoryId: String? = null,
    val startsAt: String,
    val driverName: String,
    val position: Int? = null,
    val status: String = "finished",
    val points: Double = 0.0,
)

@Serializable
data class PhotoUrlResponse(val photoUrl: String? = null)

/** What the demo-data script printed (debug builds, a local test server only). */
@Serializable
data class DemoDataResponse(val output: String = "")

/** Which notifications you hear about (urgent ones always come). */
@Serializable
data class PushPreferencesResponse(val kinds: List<PushKindSetting> = emptyList())

@Serializable
data class PushKindSetting(val key: String, val label: String, val hint: String = "", val on: Boolean = true)
