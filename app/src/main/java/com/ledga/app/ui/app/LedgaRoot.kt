package com.ledga.app.ui.app

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ledga.app.data.settings.Settings
import com.ledga.app.data.settings.TextSize
import com.ledga.app.startup.StartupState
import com.ledga.app.ui.activity.ActivityTab
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.onboarding.OnboardingRoute as OnboardingScreenRoute
import java.io.File

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
            ) { onboarded -> LedgaNavHost(onboarded) }
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

/** The app once started (spec §10.4): onboarding until it's done, then four tabs. */
@Composable
fun LedgaNavHost(onboarded: Boolean) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val start: Any = remember { if (onboarded) HomeRoute else OnboardingRoute }
    ShellFrame(selected = Tab.of(entry?.destination), onSelect = { nav.openTab(it) }) {
        NavHost(nav, startDestination = start) {
            composable<OnboardingRoute> {
                OnboardingScreenRoute(onDone = { nav.navigate(HomeRoute) { popUpTo<OnboardingRoute> { inclusive = true } } })
            }
            composable<HomeRoute> { InterimHomeRoute() }
            composable<ActivityRoute> { ActivityTab() }
            composable<TrackersRoute> { ComingNext(Tab.TRACKERS) }
            composable<YouRoute> { ComingNext(Tab.YOU) }
        }
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

/** You → Appearance → Text size (R30). SYSTEM keeps Android's own scale, including its non-linear scaling. */
@Composable
private fun WithTextSize(size: TextSize, content: @Composable () -> Unit) {
    val scale = size.scale
    if (scale == null) {
        content()
    } else {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, scale), content = content)
    }
}
