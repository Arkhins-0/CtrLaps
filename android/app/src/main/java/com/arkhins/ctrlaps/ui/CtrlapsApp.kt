package com.arkhins.ctrlaps.ui

import com.arkhins.ctrlaps.ui.components.HeaderTabs
import com.arkhins.ctrlaps.ui.screens.PeopleTab
import com.arkhins.ctrlaps.ui.screens.peoplePages
import androidx.compose.runtime.CompositionLocalProvider
import com.arkhins.ctrlaps.ui.screens.LocalOpen
import com.arkhins.ctrlaps.ui.components.PhotoViewerActions
import com.arkhins.ctrlaps.ui.screens.StandingsScreen
import com.arkhins.ctrlaps.ui.screens.ResultsScreen
import com.arkhins.ctrlaps.ui.screens.CategoryChannelScreen
import androidx.compose.ui.res.painterResource
import com.arkhins.ctrlaps.R
import com.arkhins.ctrlaps.ui.components.IconAction
import com.arkhins.ctrlaps.ui.theme.Gold
import com.arkhins.ctrlaps.data.Saver
import com.arkhins.ctrlaps.ui.components.SaveButton
import com.arkhins.ctrlaps.ui.components.saveAll
import com.arkhins.ctrlaps.ui.components.report
import com.arkhins.ctrlaps.ui.screens.LegalScreen
import com.arkhins.ctrlaps.ui.screens.ChangelogScreen
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.arkhins.ctrlaps.LocalApp
import com.arkhins.ctrlaps.data.FileInfo
import com.arkhins.ctrlaps.data.OtherUser
import com.arkhins.ctrlaps.data.SavedDocument
import com.arkhins.ctrlaps.ui.components.FileView
import com.arkhins.ctrlaps.ui.components.Avatar
import com.arkhins.ctrlaps.ui.components.BottomNav
import com.arkhins.ctrlaps.ui.components.PopupBubble
import com.arkhins.ctrlaps.ui.components.SelectionBar
import com.arkhins.ctrlaps.ui.components.SelectionTopBar
import com.arkhins.ctrlaps.ui.components.TopBar
import com.arkhins.ctrlaps.ui.components.ChatsHeader
import com.arkhins.ctrlaps.ui.components.UpdateAvailableDialog
import com.arkhins.ctrlaps.ui.components.WhatsNewDialog
import com.arkhins.ctrlaps.ui.screens.AccountScreen
import com.arkhins.ctrlaps.ui.screens.ArchiveScreen
import com.arkhins.ctrlaps.ui.screens.SeasonArchiveScreen
import com.arkhins.ctrlaps.ui.screens.ChatScreen
import com.arkhins.ctrlaps.ui.screens.ChatProfileScreen
import com.arkhins.ctrlaps.ui.screens.GroupScreen
import com.arkhins.ctrlaps.ui.screens.SettingsScreen
import com.arkhins.ctrlaps.ui.screens.PermissionsScreen
import com.arkhins.ctrlaps.ui.screens.ThemeScreen
import com.arkhins.ctrlaps.ui.screens.AboutScreen
import com.arkhins.ctrlaps.ui.screens.ActivityScreen
import com.arkhins.ctrlaps.ui.screens.DeleteAccountScreen
import com.arkhins.ctrlaps.ui.screens.EmailSettingsScreen
import com.arkhins.ctrlaps.ui.screens.FaqScreen
import com.arkhins.ctrlaps.ui.screens.LicenseScreen
import com.arkhins.ctrlaps.ui.screens.SupportScreen
import com.arkhins.ctrlaps.ui.screens.TicketFormScreen
import com.arkhins.ctrlaps.ui.screens.TicketScreen
import com.arkhins.ctrlaps.ui.screens.TicketsScreen
import com.arkhins.ctrlaps.ui.screens.AccountDetailsScreen
import com.arkhins.ctrlaps.ui.screens.StorageScreen
import com.arkhins.ctrlaps.ui.screens.NewGroupScreen
import com.arkhins.ctrlaps.ui.screens.ChatsScreen
import com.arkhins.ctrlaps.ui.screens.ComposeScreen
import com.arkhins.ctrlaps.ui.screens.EmailScreen
import com.arkhins.ctrlaps.ui.screens.ForgotScreen
import com.arkhins.ctrlaps.ui.screens.HomeScreen
import com.arkhins.ctrlaps.ui.screens.ImageScreen
import com.arkhins.ctrlaps.ui.screens.GalleryScreen
import com.arkhins.ctrlaps.ui.screens.LoginScreen
import com.arkhins.ctrlaps.ui.screens.RegisterScreen
import com.arkhins.ctrlaps.ui.screens.NewChatScreen
import com.arkhins.ctrlaps.ui.screens.NewPersonScreen
import com.arkhins.ctrlaps.ui.screens.OnboardingScreen
import com.arkhins.ctrlaps.ui.screens.PdfScreen
import com.arkhins.ctrlaps.ui.screens.PeopleScreen
import com.arkhins.ctrlaps.ui.screens.PermissionScreen
import com.arkhins.ctrlaps.ui.screens.PersonScreen
import com.arkhins.ctrlaps.ui.screens.ScannerScreen
import com.arkhins.ctrlaps.ui.screens.ScheduleScreen
import com.arkhins.ctrlaps.ui.screens.SetPasswordScreen
import com.arkhins.ctrlaps.ui.screens.WeekendScreen
import com.arkhins.ctrlaps.ui.screens.allGranted
import com.arkhins.ctrlaps.ui.theme.Night
import kotlinx.coroutines.launch

/**
 * The whole app: the permission gate first, then sign-in or the signed-in
 * screens, with the update popup over whatever is showing.
 */
@Composable
fun CtrlapsApp() {
    val app = LocalApp.current
    val context = LocalContext.current
    val vm = rememberViewModel { AppViewModel(app) }
    val uri = LocalUriHandler.current
    var granted by remember { mutableStateOf(allGranted(context)) }

    // Coming back from settings (permissions, or "allow installs"): pick up where we left off.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = allGranted(context)
                if (vm.updateStage is UpdateStage.NeedsPermission) vm.install()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Phones that installed before the battery step existed get the dialog once.
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    LaunchedEffect(vm.gate, granted) {
        if (granted && vm.gate == Gate.Ready && !app.session.batteryAsked) {
            app.session.markBatteryAsked()
            if (!Battery.isExempt(context)) runCatching { batteryLauncher.launch(Battery.requestExemption(context)) }
        }
    }

    Box(Modifier.fillMaxSize().background(Night)) {
        if (!granted) {
            PermissionScreen { granted = true }
        } else {
            when (vm.gate) {
                Gate.Loading -> Unit
                Gate.SignedOut -> AuthNav(vm)
                Gate.Onboarding -> OnboardingScreen { vm.refreshMe() }
                Gate.Ready -> MainNav(vm)
            }
        }

        // The first open after an update shows what changed; a newer release, if any, waits until that is closed.
        val whatsNew = vm.whatsNew
        if (whatsNew != null) {
            WhatsNewDialog(info = whatsNew, onDismiss = vm::dismissWhatsNew, role = vm.me?.user?.role)
        }
        val update = vm.updateInfo
        if (whatsNew == null && update != null && !vm.updateDismissed) {
            UpdateAvailableDialog(
                role = vm.me?.user?.role,
                info = update,
                stage = vm.updateStage,
                onUpdate = vm::downloadAndInstall,
                onInstall = vm::install,
                onOpenSettings = vm::openInstallSettings,
                onOpenReleasePage = {
                    uri.openSafely(update.releaseUrl)
                    vm.dismissUpdate()
                },
                onDismiss = vm::dismissUpdate,
            )
        }
    }
}

/** Signed out: sign in, register, forgot password, and the invite/register/reset links. */
@Composable
private fun AuthNav(vm: AppViewModel) {
    val nav = rememberNavController()
    val pending by Links.pending.collectAsStateWithLifecycle()

    LaunchedEffect(pending) {
        val link = pending ?: return@LaunchedEffect
        val route = Links.route(link)
        if (route != null && route.startsWith("setpassword/")) {
            Links.pending.value = null
            nav.navigate(route)
        }
        // Any other link waits for sign-in; MainNav picks it up.
    }

    NavHost(nav, startDestination = "login", enterTransition = { fadeIn(tween(120)) }, exitTransition = { fadeOut(tween(90)) }) {
        composable("login") { LoginScreen(onSignedIn = vm::signedIn, onForgot = { nav.navigate("forgot") }, onRegister = { nav.navigate("register") }) }
        composable("register") { RegisterScreen(onBack = { nav.popBackStack() }) }
        composable("forgot") { ForgotScreen(onBack = { nav.popBackStack() }) }
        composable("setpassword/{kind}/{token}") { entry ->
            SetPasswordScreen(
                kind = entry.arguments?.getString("kind") ?: "invite",
                token = entry.arguments?.getString("token") ?: "",
                onSignedIn = vm::signedIn,
                onDone = { nav.navigate("login") { popUpTo("login") { inclusive = true } } },
                onLegal = { nav.navigate("legal/$it") },
            )
        }
        composable("legal/{doc}") { e ->
            LegalScreen(e.arguments?.getString("doc") ?: "privacy", onOpen = { nav.navigate("legal/$it") }, onBack = { nav.popBackStack() })
        }
    }
}

/** Signed in and set up: the five tabs and everything they open. */
@Composable
private fun MainNav(vm: AppViewModel) {
    val app = LocalApp.current
    val nav = rememberNavController()
    // The chats tab: 0 is the chat list, 1 the channels; the header switch and the swipe both move it.
    var chatsPage by remember { mutableIntStateOf(0) }
    var peoplePage by remember { mutableIntStateOf(0) }
    var pdf by remember { mutableStateOf<SavedDocument?>(null) }
    var image by remember { mutableStateOf<FileInfo?>(null) }
    var imageMessage by remember { mutableStateOf<com.arkhins.ctrlaps.data.Message?>(null) }
    // When the photo or document being viewed was sent: its saved copy is named after it.
    var viewSentAt by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    // A grid's photos, opened from it; while some are picked there the header is the selection bar.
    var gallery by remember { mutableStateOf<FileView.Gallery?>(null) }
    var gallerySelection by remember { mutableStateOf<SelectionBar?>(null) }

    val back: () -> Unit = { nav.popBackStack() }
    // The header's arrow goes back the way the system Back does, so a screen that asks first ("Discard changes?") can.
    val backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val headerBack: () -> Unit = { backDispatcher?.onBackPressed() ?: back() }
    /** A chat opens on top of what is showing; from inside a chat (a forward), it takes that chat's place. */
    val openChat: (String) -> Unit = { id ->
        val top = nav.currentBackStackEntry
        val inChat = top?.destination?.route == "chat/{id}"
        if (!(inChat && top?.arguments?.getString("id") == id)) {
            nav.navigate("chat/$id") { if (inChat) popUpTo("chat/{id}") { inclusive = true } }
        }
    }
    /** Go where a link or a saved route says. */
    val go: (String) -> Unit = { r -> if (r.startsWith("chat/")) openChat(r.removePrefix("chat/")) else runCatching { nav.navigate(r) } }
    val pending by Links.pending.collectAsStateWithLifecycle()
    // Opened by a notification or link: that decides the screen, not the last one seen.
    val openedByLink = remember { Links.pending.value != null }

    LaunchedEffect(pending) {
        val link = pending ?: return@LaunchedEffect
        Links.pending.value = null
        Links.route(link)?.let { r -> if (!r.startsWith("setpassword/")) go(r) }
    }

    // The system may kill the app while the phone is locked and start it
    // again from scratch. The last screen is kept in DataStore, so a restart
    // within a few hours goes back to it (unless a link or notification says
    // where to go instead).
    val restoreScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val saved = app.session.lastRoute
        val fresh = System.currentTimeMillis() - app.session.lastRouteAt < 6 * 60 * 60 * 1000L
        if (!openedByLink && Links.pending.value == null && saved != null && saved != "home" && fresh) {
            runCatching { nav.navigate(saved) { launchSingleTop = true } }
        }
    }
    DisposableEffect(nav) {
        val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, destination, arguments ->
            val pattern = destination.route ?: return@OnDestinationChangedListener
            // Screens that make sense to come back to; not viewers or forms.
            val concrete = when {
                pattern in setOf("home", "schedule", "chats", "people", "account") -> pattern
                pattern.startsWith("weekend/") || pattern.startsWith("category/") || pattern.startsWith("person/") || pattern.startsWith("chat/") ->
                    pattern.replace("{id}", arguments?.getString("id") ?: return@OnDestinationChangedListener)
                else -> return@OnDestinationChangedListener
            }
            restoreScope.launch { app.session.saveRoute(concrete) }
        }
        nav.addOnDestinationChangedListener(listener)
        onDispose { nav.removeOnDestinationChangedListener(listener) }
    }

    val openWeekend: (String) -> Unit = { nav.open("weekend/$it") }
    val openRoute: (String) -> Unit = { nav.open(it) }
    val view: (FileView) -> Unit = {
        when (it) {
            is FileView.Pdf -> { pdf = it.doc; viewSentAt = it.sentAt; nav.open("pdf") }
            is FileView.Image -> { image = it.file; imageMessage = it.message; viewSentAt = it.sentAt; nav.open("image") }
            is FileView.Gallery -> { gallery = it; gallerySelection = null; nav.open("gallery") }
        }
    }

    /** A tab: its header, its page, and the footer. */
    @Composable
    fun Tab(current: String, title: String, center: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) =
        Screen(title, onBack = null, onOpenWeekend = openWeekend, center = center, footer = {
            // A tab always shows its own page: everything above Home is
            // dropped first, nothing is restored (a chat opened from a popup
            // would otherwise come back on top of Home).
            BottomNav(
                current = current,
                unreadHome = vm.unreadHome,
                unreadChats = vm.unreadChats,
                photoUrl = app.api.absolute(vm.me?.user?.photoUrl),
                name = vm.me?.user?.displayName ?: "?",
                showChats = vm.me?.user?.role != "user",
            ) { dest ->
                nav.navigate(dest) {
                    popUpTo("home") { inclusive = dest == "home" }
                    launchSingleTop = true
                }
            }
        }, content = { CompositionLocalProvider(LocalOpen provides openRoute) { content() } })

    /** A screen opened on top: its own header with a back arrow, no footer. */
    @Composable
    fun Pushed(title: String, showCountdown: Boolean = true, header: (@Composable () -> Unit)? = null, action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) =
        Screen(title, onBack = headerBack, onOpenWeekend = openWeekend, showCountdown = showCountdown, header = header, action = action, content = {
            CompositionLocalProvider(LocalOpen provides openRoute) { content() }
        })

    Box(Modifier.fillMaxSize()) {
        // Tabs swap in place, footer still. Anything else flies in from the right, header and all, over
        // the screen it was opened from, which stays exactly as it was and is there again on the way back.
        NavHost(
            nav,
            startDestination = "home",
            modifier = Modifier.fillMaxSize(),
            enterTransition = { if (!targetState.isTab()) slideInHorizontally(tween(220)) { it } else if (initialState.isTab()) EnterTransition.None else fadeIn(tween(120)) },
            exitTransition = { if (!targetState.isTab()) ExitTransition.KeepUntilTransitionsFinished else if (initialState.isTab()) ExitTransition.None else fadeOut(tween(90)) },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { if (initialState.isTab()) ExitTransition.None else slideOutHorizontally(tween(200)) { it } },
        ) {
            composable("home") { Tab("home", "CTR[L]APS") { HomeScreen(vm, highlight = null, onOpenWeekend = openWeekend, onOpenChat = openChat, onAllChats = { nav.navigate("chats") { popUpTo("home"); launchSingleTop = true } }, onCompose = { nav.open("compose") }, onView = view) } }
            composable("home?m={m}") { e -> Tab("home", "CTR[L]APS") { HomeScreen(vm, highlight = e.arguments?.getString("m"), onOpenWeekend = openWeekend, onOpenChat = openChat, onAllChats = { nav.navigate("chats") { popUpTo("home"); launchSingleTop = true } }, onCompose = { nav.open("compose") }, onView = view) } }
            composable("schedule") { Tab("schedule", "Schedule") { ScheduleScreen(isAdmin = vm.me?.isAdmin == true, onOpenWeekend = openWeekend, onArchive = { nav.open("archive") }, mine = vm.me?.categoryIds, onStandings = { nav.open("standings") }, editTimes = vm.me?.user?.role == "coordinator") } }
            composable("chats") { Tab("chats", "Chats", center = { ChatsHeader(chatsPage) { chatsPage = it } }) { ChatsScreen(vm, page = chatsPage, onPage = { chatsPage = it }, onOpen = openChat, onNewChat = { nav.open("newchat") }, onOpenWeekend = openWeekend, onOpenCategory = { nav.open("category/$it") }) } }
            composable("people") {
                val pages = peoplePages(vm.me?.user?.role)
                Tab("people", "People", center = if (pages.size > 1) ({ HeaderTabs(pages, peoplePage) { peoplePage = it } }) else null) {
                    PeopleTab(vm.me, peoplePage, onPage = { peoplePage = it }, onOpen = { nav.open("person/$it") }, onAdd = { nav.open("newperson") }, onEmail = { g -> nav.open(if (g == null) "email" else "email?group=$g") })
                }
            }
            composable("account") {
                Tab("account", "Account") {
                    AccountScreen(
                        vm,
                        onScan = { nav.open("scanner") },
                        onArchive = { nav.open("archive") },
                        onDetails = { nav.open("details") },
                        onStorage = { nav.open("storage") },
                        onSettings = { nav.open("settings") },
                        onAbout = { nav.open("about") },
                        onActivity = { nav.open("activity") },
                    )
                }
            }

            composable("chat/{id}") { e ->
                val id = e.arguments?.getString("id") ?: ""
                var chatWith by remember { mutableStateOf<OtherUser?>(null) }
                // Messages long-pressed in a chat: the header turns into the selection bar.
                var selection by remember { mutableStateOf<SelectionBar?>(null) }
                // The chat header's ⋮ menu: a search bar in the chat, or an export of it.
                var chatSearch by remember { mutableStateOf(false) }
                var chatExport by remember { mutableIntStateOf(0) }
                var chatCanExport by remember { mutableStateOf(true) }
                // The name and photo are there from the first frame: from the phone's copy of the chat, or its row in the list.
                val who = chatWith ?: remember(id) {
                    app.chatCache.peek(id)?.let { it.other ?: it.group?.asOther() } ?: app.chatCache.peekList()?.find { it.id == id }?.other
                }
                Pushed(who?.name ?: "", header = {
                    val bar = selection
                    if (bar != null) SelectionTopBar(bar) else TopBar(
                        title = who?.name ?: "",
                        onBack = back,
                        onOpenWeekend = openWeekend,
                        photo = who?.let { w -> { Avatar(app.api.absolute(w.photoUrl), w.name, 36) } },
                        onTitleClick = { nav.open(if (who?.role == "group") "group/$id" else "chatprofile/$id") },
                        menu = listOf("Search messages" to { chatSearch = true }) + (if (chatCanExport) listOf("Export chat" to { chatExport++ }) else emptyList()),
                    )
                }) {
                    ChatScreen(vm, id, view, onSelection = { selection = it }, searchOpen = chatSearch, onSearchClose = { chatSearch = false }, exportTick = chatExport, onCanExport = { chatCanExport = it }, onOpenChat = openChat) { chatWith = it }
                }
            }
            // Made or chosen from a form: back to the chats tab, with the chat on top of it.
            composable("newchat") { Pushed("New chat") { NewChatScreen(onNewGroup = { nav.open("newgroup") }) { id -> nav.popBackStack("chats", false); openChat(id) } } }
            composable("newgroup") { Pushed("New group") { NewGroupScreen { id -> nav.popBackStack("chats", false); openChat(id) } } }
            composable("group/{id}") { e ->
                var t by remember { mutableStateOf("") }
                Pushed(t) { GroupScreen(vm, e.arguments?.getString("id") ?: "", onOpenChat = { nav.popBackStack("chats", false); openChat(it) }, onLeft = { nav.navigate("chats") { popUpTo("home") } }) { t = it } }
            }
            composable("chatprofile/{id}") { e ->
                var t by remember { mutableStateOf("") }
                Pushed(t) { ChatProfileScreen(e.arguments?.getString("id") ?: "") { t = it } }
            }
            composable("compose") { Pushed("New message") { ComposeScreen { nav.popBackStack(); vm.changed() } } }
            composable("weekend/{id}") { e -> Pushed("Race weekend") { WeekendScreen(vm, e.arguments?.getString("id") ?: "", view) } }
            composable("teams") { LaunchedEffect(Unit) { peoplePage = 1; nav.navigate("people") { popUpTo("home"); launchSingleTop = true } } }
            composable("categories") { LaunchedEffect(Unit) { peoplePage = 2; nav.navigate("people") { popUpTo("home"); launchSingleTop = true } } }
            composable("standings") { Pushed("Standings") { StandingsScreen(vm, onOpenResults = { nav.open("results/$it") }) } }
            composable("standings?season={season}") { e -> Pushed("Standings") { StandingsScreen(vm, onOpenResults = { nav.open("results/$it") }, startSeason = e.arguments?.getString("season")) } }
            composable("results/{id}") { e -> Pushed("Results") { ResultsScreen(vm, e.arguments?.getString("id") ?: "") } }
            composable("results/{id}/edit") { e -> Pushed("Results") { ResultsScreen(vm, e.arguments?.getString("id") ?: "", startEditing = true) } }
            composable("category/{id}") { e -> Pushed("Category channel") { CategoryChannelScreen(vm, e.arguments?.getString("id") ?: "", view) } }
            composable("person/{id}") { e ->
                var t by remember { mutableStateOf("") }
                Pushed(t) { PersonScreen(vm.me, e.arguments?.getString("id") ?: "", onOpenChat = openChat) { t = it } }
            }
            composable("newperson") { Pushed("Add or promote") { NewPersonScreen(vm.me) { id -> nav.navigate("person/$id") { popUpTo("people") } } } }
            composable("email") { Pushed("Email") { EmailScreen(null) { nav.popBackStack() } } }
            composable("email?group={group}") { e -> Pushed("Email") { EmailScreen(e.arguments?.getString("group")) { nav.popBackStack() } } }

            composable("details") { Pushed("Account") { AccountDetailsScreen(vm) } }
            composable("storage") { Pushed("Storage") { StorageScreen() } }
            composable("settings") { Pushed("Settings") { SettingsScreen(onPermissions = { nav.open("permissions") }, onTheme = { nav.open("theme") }, onEmail = { nav.open("email-settings") }, onDelete = { nav.open("delete-account") }) } }
            composable("email-settings") { Pushed("Email") { EmailSettingsScreen() } }
            composable("activity") { Pushed("Activity log") { ActivityScreen() } }
            composable("delete-account") { Pushed("Delete account") { DeleteAccountScreen(onDeleted = { vm.accountDeleted() }) } }
            composable("theme") { Pushed("Theme") { ThemeScreen() } }
            composable("permissions") { Pushed("Permissions") { PermissionsScreen() } }
            composable("about") { Pushed("About") { AboutScreen(vm, onChangelog = { nav.open("changelog") }, onLegal = { nav.open("legal/$it") }, onSupport = { nav.open("support") }, onLicense = { nav.open("license") }) } }
            composable("license") { Pushed("License") { LicenseScreen() } }
            composable("support") { Pushed("Support") { SupportScreen(vm, onFaqs = { nav.open("support/faqs") }, onForm = { nav.open("support/new") }, onTickets = { nav.open("support/tickets") }) } }
            composable("support/faqs") { Pushed("FAQs") { FaqScreen(vm) } }
            composable("support/new") { Pushed("Support form") { TicketFormScreen(vm) { id -> nav.navigate("support/ticket/$id") { popUpTo("support") } } } }
            composable("support/tickets") { Pushed("Tickets") { TicketsScreen(vm) { nav.open("support/ticket/$it") } } }
            composable("support/ticket/{id}") { e ->
                var t by remember { mutableStateOf("Ticket") }
                Pushed(t) { TicketScreen(vm, e.arguments?.getString("id") ?: "", view) { t = it } }
            }
            composable("changelog") { Pushed("What's new") { ChangelogScreen(vm.me?.user?.role) } }
            composable("legal/{doc}") { e ->
                var t by remember { mutableStateOf("") }
                Pushed(t) { LegalScreen(e.arguments?.getString("doc") ?: "privacy", onOpen = { nav.open("legal/$it") }, onTitle = { t = it }) }
            }
            composable("archive") { Pushed("Archive") { ArchiveScreen { nav.open("archive/$it") } } }
            composable("archive/{id}") { e ->
                var t by remember { mutableStateOf("Season") }
                Pushed(t) { SeasonArchiveScreen(vm, e.arguments?.getString("id") ?: "", onView = view, onDeleted = back) { t = it } }
            }
            composable("scanner") {
                var typing by remember { mutableStateOf(false) }
                Pushed("Verify", action = { if (typing) IconAction(painterResource(R.drawable.ic_scan), "Scan a QR code", Gold) { typing = false } }) {
                    ScannerScreen(typing = typing, onTyping = { typing = it }, onOpenChat = openChat)
                }
            }
            composable("verify/{token}") { e ->
                var typing by remember { mutableStateOf(false) }
                Pushed("Verify", action = { if (typing) IconAction(painterResource(R.drawable.ic_scan), "Scan a QR code", Gold) { typing = false } }) {
                    ScannerScreen(initialToken = e.arguments?.getString("token"), typing = typing, onTyping = { typing = it }, onOpenChat = openChat)
                }
            }

            composable("pdf") {
                val at = viewSentAt
                Pushed(pdf?.name?.replace(Regex("^[0-9a-f]{8}-"), "") ?: "Document", showCountdown = false, action = {
                    SaveButton { pdf?.let { d -> app.appScope.launch { report(context, listOf(runCatching { Saver.save(context, d, at) })) } } }
                }) { pdf?.let { PdfScreen(it) } }
            }
            composable("image") {
                val at = viewSentAt
                val m = imageMessage
                // Who sent it and when, as WhatsApp heads a photo: "You · 12:26 am".
                val heading = m?.let { "${if (it.mine) "You" else it.sender?.name ?: "CTR[L]APS"} · ${localTime(it.createdAt)}" } ?: image?.name ?: "Photo"
                val chatId = m?.conversationId?.takeIf { m.kind == "direct" || m.kind == "group" }
                Pushed(heading, showCountdown = false, action = {
                    image?.let { f ->
                        PhotoViewerActions(
                            f,
                            m,
                            at,
                            canForward = vm.me?.user?.role != "user",
                            // Back to the chat it came from, or open it.
                            onShowInChat = chatId?.let { id -> { if (!nav.popBackStack("chat/{id}", inclusive = false)) openChat(id) } },
                            onDeleted = {
                                vm.changed()
                                if (nav.currentDestination?.route == "image") nav.popBackStack()
                            },
                        )
                    }
                }) {
                    image?.let { ImageScreen(it) }
                }
            }
            composable("gallery") {
                // Who sent the photos and when, as WhatsApp heads them: "You · 8:16 pm".
                val heading = gallery?.message?.let { m -> "${if (m.mine) "You" else m.sender?.name ?: "CTR[L]APS"} · ${localTime(m.createdAt)}" } ?: "Photos"
                Pushed(heading, showCountdown = false, header = gallerySelection?.let { bar -> { SelectionTopBar(bar) } }) {
                    gallery?.let { g ->
                        GalleryScreen(
                            g,
                            onView = view,
                            onSelection = { gallerySelection = it },
                            onChanged = vm::changed,
                            // Only if the gallery is still what is showing: the delete may finish after a back press.
                            onDone = { if (nav.currentDestination?.route == "gallery") nav.popBackStack() },
                        )
                    }
                }
            }
        }

        vm.popup?.let { event ->
            Box(Modifier.align(Alignment.TopCenter)) {
                PopupBubble(event, onOpen = { link -> vm.dismissPopup(); Links.route(link)?.let(go) }, onDismiss = vm::dismissPopup)
            }
        }
    }
}

/**
 * Open a screen from a tap. Taps while a screen is still sliding in are ignored (the screen they were
 * made on is no longer the settled one), and a screen already on top is never stacked on itself, so
 * tapping Settings three times, or the countdown on the race weekend page, opens it once.
 */
private fun NavController.open(route: String) {
    val top = currentBackStackEntry
    if (top != null && !top.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
    // The exact screen on top ("weekend/42", not just any weekend): Terms can still open Privacy.
    val showing = top?.destination?.route?.let { pattern ->
        Regex("\\{(\\w+)\\}").replace(pattern) { m -> top.arguments?.getString(m.groupValues[1]) ?: "" }
    }
    if (showing == route) return
    navigate(route)
}

/** Open a link, or do nothing if no browser is installed. */
fun UriHandler.openSafely(url: String) {
    runCatching { openUri(url) }
}

private val TABS = setOf("home", "home?m={m}", "schedule", "chats", "people", "account")

private fun NavBackStackEntry.isTab() = destination.route in TABS

/**
 * One screen, whole: its header, its page and (on a tab) the footer, on a
 * solid background so nothing underneath shows through while it slides.
 */
@Composable
private fun Screen(
    title: String,
    onBack: (() -> Unit)?,
    onOpenWeekend: (String) -> Unit,
    showCountdown: Boolean = true,
    center: (@Composable () -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Night)) {
        if (header != null) header()
        else TopBar(title = title, onBack = onBack, onOpenWeekend = onOpenWeekend, showCountdown = showCountdown, center = center, action = action)
        Box(Modifier.weight(1f)) { content() }
        footer?.invoke()
    }
}
