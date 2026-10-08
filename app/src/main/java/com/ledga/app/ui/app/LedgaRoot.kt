package com.ledga.app.ui.app

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.ledga.app.data.settings.Settings
import com.ledga.app.data.settings.TextSize
import com.ledga.app.startup.StartupState
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.home.HomeNav
import com.ledga.app.ui.you.YouNav
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The whole app: theme and text size from settings, then startup's verdict (spec §8, §10.4). */
@Composable
fun LedgaRoot(app: AppViewModel) {
    val settings by app.settings.collectAsStateWithLifecycle()
    val startup by app.startupState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val s = settings ?: Settings()
    LedgaTheme(appearance = s.appearance) {
        SystemBarsFollowTheme()
        WithTextSize(s.textSize) {
            LedgaRootContent(
                startup = startup,
                onShare = { file -> context.startActivity(RecoveryShare.intent(context, file)) },
                onRetry = app::retry,
            ) { onboarded -> LedgaNavHost(onboarded, opens = app.notificationOpens, onOpened = app::opened) }
        }
    }
}

/** Startup → blank canvas (the splash is up), the recovery screen, or [app]. Split out so tests can drive it. */
@Composable
fun LedgaRootContent(
    startup: StartupState,
    onShare: (File) -> Unit,
    onRetry: () -> Unit,
    app: @Composable (onboarded: Boolean) -> Unit,
) {
    when (startup) {
        StartupState.Opening -> Box(Modifier.fillMaxSize().background(LedgaTheme.colors.canvas))
        is StartupState.Failed -> RecoveryScreen(
            reason = startup.reason,
            canShare = startup.snapshot != null,
            onShare = { startup.snapshot?.let(onShare) },
            onRetry = onRetry,
        )
        is StartupState.Ready -> app(startup.onboarded)
    }
}

private val NO_OPENS = MutableStateFlow<OpenDestination?>(null)

/** The app once started (spec §10.4): onboarding until it's done, then four tabs; a tapped notification's screen (R100). */
@Composable
fun LedgaNavHost(
    onboarded: Boolean,
    screens: LedgaScreens = AppScreens,
    opens: StateFlow<OpenDestination?> = NO_OPENS,
    onOpened: (OpenDestination) -> Unit = {},
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val start: Any = remember { if (onboarded) HomeRoute else OnboardingRoute }
    // A hop into Activity from a screen in another tab (R61): Back returns to that tab, to the screen it left. A tab
    // tapped meanwhile ends it.
    var backTo by rememberSaveable { mutableStateOf<Tab?>(null) }
    fun hop(from: Tab) {
        backTo = from
        nav.openTab(Tab.ACTIVITY)
    }
    // A category's page, once however fast the taps come (4e D1, D3).
    fun openCategory(key: String, month: String? = null) = nav.navigate(CategoryRoute(key, month)) { launchSingleTop = true }
    // A pushed screen, once however fast the taps come.
    fun push(route: Any) = nav.navigate(route) { launchSingleTop = true }
    val back: () -> Unit = { nav.popBackStack() }
    // R83: the tab the screen on top belongs to — the last tab root on the back stack, however deep the pushes go.
    fun owningTab(): Tab = nav.currentBackStack.value.asReversed().firstNotNullOfOrNull { Tab.of(it.destination) } ?: Tab.HOME
    ShellFrame(
        selected = Tab.of(entry?.destination),
        onSelect = {
            backTo = null
            nav.openTab(it)
        },
    ) {
        NavHost(nav, startDestination = start) {
            composable<OnboardingRoute> {
                screens.Onboarding(onDone = { nav.navigate(HomeRoute) { popUpTo<OnboardingRoute> { inclusive = true } } })
            }
            composable<HomeRoute> {
                screens.Home(
                    HomeNav(
                        openActivity = { hop(Tab.HOME) },
                        openTrackers = { nav.openTab(Tab.CATEGORIES) },
                        openCategory = { openCategory(it) },
                        openYou = { nav.openTab(Tab.YOU) },
                        openAlerts = { nav.navigate(AlertsRoute()) { launchSingleTop = true } },
                        openUpdates = { push(UpdatesRoute) },
                    ),
                )
            }
            composable<ActivityRoute> {
                BackHandler(enabled = backTo != null) {
                    val to = backTo ?: return@BackHandler
                    backTo = null
                    nav.openTab(to)
                }
                screens.Activity(openCategory = { key, month -> openCategory(key, month) })
            }
            composable<CategoriesRoute> { screens.Categories(onOpen = { openCategory(it) }) }
            composable<YouRoute> {
                screens.You(
                    YouNav(
                        openLines = { push(LinesRoute) },
                        openPeople = { hop(Tab.YOU) },
                        openNotifications = { push(NotificationsRoute) },
                        openAppearance = { push(AppearanceRoute) },
                        openUnreadable = { push(UnreadableRoute) },
                        openHistoryCheck = { push(HistoryCheckRoute) },
                        openLicences = { push(LicencesRoute) },
                        openBackup = { push(BackupRoute) },
                        openUpdates = { push(UpdatesRoute) },
                        openVersionHistory = { push(VersionHistoryRoute) },
                    ),
                )
            }
            composable<LinesRoute> { screens.Lines(onBack = back, onUnassigned = { push(UnassignedRoute) }) }
            composable<UnassignedRoute> { screens.Unassigned(onBack = back) }
            composable<BackupRoute> { screens.Backup(onBack = back) }
            composable<UpdatesRoute> { screens.Updates(onBack = back) }
            composable<VersionHistoryRoute> { screens.VersionHistory(onBack = back) }
            composable<CategoryRoute> {
                screens.Category(
                    onBack = back,
                    // R90: Activity is one screen on its stack, so a page opened inside it pops back to it; from any
                    // other tab the page hops to Activity, and Back returns to the page (R61, R83).
                    onSeeAll = { owningTab().let { tab -> if (tab == Tab.ACTIVITY) nav.popBackStack<ActivityRoute>(inclusive = false) else hop(tab) } },
                )
            }
            composable<NotificationsRoute> { screens.NotificationSettings(onBack = back) }
            composable<AppearanceRoute> { screens.AppearanceSettings(onBack = back) }
            composable<UnreadableRoute> { screens.Unreadable(onBack = back) }
            composable<HistoryCheckRoute> {
                screens.HistoryCheck(onBack = back, onOpenCategory = { openCategory(it) }, onUnassigned = { push(UnassignedRoute) })
            }
            composable<LicencesRoute> { screens.Licences(onBack = back, onOpen = { push(LicenceRoute(it)) }) }
            composable<LicenceRoute> { entry -> screens.Licence(entry.toRoute<LicenceRoute>().asset, onBack = back) }
            composable<AlertsRoute> { entry ->
                screens.Alerts(onBack = { nav.popBackStack() }, onOpenCategory = { openCategory(it) }, openCode = entry.toRoute<AlertsRoute>().openCode)
            }
        }
    }
    // R100: a tapped notification's screen, once. During onboarding there is no tab yet: it is taken and dropped.
    val open by opens.collectAsStateWithLifecycle()
    LaunchedEffect(open) {
        val destination = open ?: return@LaunchedEffect
        if (nav.currentBackStack.value.any { Tab.of(it.destination) != null }) {
            backTo = null
            when (destination) {
                // A second payment's tap replaces an Alerts already on top: Back still returns where you were.
                is OpenDestination.Alerts -> nav.navigate(AlertsRoute(destination.code)) { popUpTo<AlertsRoute> { inclusive = true } }
                // To the tab's own screen: re-selecting a tab restores what was pushed on it (final review I1).
                OpenDestination.Home -> {
                    nav.openTab(Tab.HOME)
                    nav.popBackStack<HomeRoute>(inclusive = false)
                }
                OpenDestination.Activity -> {
                    nav.openTab(Tab.ACTIVITY)
                    nav.popBackStack<ActivityRoute>(inclusive = false)
                }
                // R144: an update notice opens Updates over You; Back returns to You.
                OpenDestination.Updates -> {
                    nav.openTab(Tab.YOU)
                    nav.navigate(UpdatesRoute) { launchSingleTop = true }
                }
            }
        }
        onOpened(destination)
    }
}

/** Edge-to-edge (targetSdk 35): bar icons follow Ledga's appearance, not the phone's theme. */
@Composable
private fun SystemBarsFollowTheme() {
    val dark = LedgaTheme.colors.isDark
    val activity = LocalContext.current.findActivity() as? ComponentActivity ?: return
    DisposableEffect(dark) {
        val style = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }
}

/**
 * You → Appearance → Text size (R30). SYSTEM keeps Android's own scale, including its non-linear scaling. The content
 * keeps one place in the tree whatever the size: moving it would rebuild the navigation (back to Home), and the old
 * screens' ViewModels would live on unseen and take Activity's hand-offs (S26).
 */
@Composable
internal fun WithTextSize(size: TextSize, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    val density = size.scale?.let { Density(d.density, it) } ?: d
    CompositionLocalProvider(LocalDensity provides density, content = content)
}
