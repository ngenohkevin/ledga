package com.ledga.app.ui.home

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledga.app.ui.app.SmsPermissions
import com.ledga.app.ui.app.TakeRequest
import com.ledga.app.ui.app.openAppSettings
import com.ledga.app.ui.app.openNotificationSettings
import com.ledga.app.ui.app.showsRationale
import com.ledga.app.ui.design.components.Banner
import com.ledga.app.ui.design.components.BannerTone
import com.ledga.app.ui.design.components.EmptyState
import com.ledga.app.ui.design.components.LedgaModalSheet
import com.ledga.app.ui.design.components.Skeleton
import com.ledga.app.ui.design.icons.Ph
import com.ledga.app.ui.design.tokens.Radii
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.tx.CategoryPickerHost
import com.ledga.app.ui.tx.TransactionSheetHost
import com.ledga.app.ui.update.WhatsNewContent
import com.ledga.core.time.PeriodType
import kotlinx.coroutines.launch

internal const val LEGACY_IMPORT_FAILED_TEXT =
    "Your notes and categories from the old Ledga couldn't be moved yet. Ledga tries again each time it opens."

/** What Home's taps do (spec §10.4). Every default does nothing, for screenshots and tests. */
data class HomeActions(
    val onSearch: () -> Unit = {},
    val onProfile: () -> Unit = {},
    val onAllowSms: () -> Unit = {},
    val onTurnOnNotifications: () -> Unit = {},
    val onNotNow: () -> Unit = {},
    val onLine: (Long?) -> Unit = {},
    val onFuliza: () -> Unit = {},
    val onPeriod: (PeriodType) -> Unit = {},
    val onSpending: () -> Unit = {},
    val onTracker: (String) -> Unit = {},
    val onAllTrackers: () -> Unit = {},
    val onOpenTx: (String) -> Unit = {},
    val onPickCategory: (String) -> Unit = {},
    val onAllRecent: () -> Unit = {},
    val onAlerts: () -> Unit = {},
    val onUpdate: () -> Unit = {},
    val onInstall: () -> Unit = {},
    val onUpdateLater: () -> Unit = {},
    val onOpenUpdates: () -> Unit = {},
    val onMergeLines: () -> Unit = {},
    val onNotSameLines: () -> Unit = {},
)

/**
 * Home (spec §10.4, mockup `home`), a scrolling column, top to bottom:
 * - the header;
 * - the banners;
 * - the balance card with the line chip and the Fuliza strip;
 * - the spending card;
 * - the trackers strip;
 * - Recent.
 * With no history yet, an empty state takes the cards' place.
 */
@Composable
fun HomeContent(ui: HomeUi, actions: HomeActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).verticalScroll(rememberScrollState())) {
        HomeHeader(ui, actions)
        Column(Modifier.padding(horizontal = Spacing.screen).padding(bottom = Spacing.xxl), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            HomeBanners(ui, actions)
            when {
                !ui.loaded -> HomeSkeleton()
                !ui.hasHistory && !ui.smsGranted -> EmptyState(
                    "fluent_incoming_envelope",
                    "Ledga can't see your M-Pesa messages",
                    "Allow SMS access and every payment shows up here on its own.",
                    actionLabel = "Allow SMS access",
                    onAction = actions.onAllowSms,
                )
                !ui.hasHistory && ui.history != null -> HomeSkeleton()
                !ui.hasHistory -> EmptyState("fluent_magnifying_glass_tilted_left", "No M-Pesa payments yet", "New payments appear here as their messages arrive.")
                else -> {
                    BalanceCard(ui, actions)
                    SpendingCard(ui.spending, actions)
                    if (ui.trackers.isNotEmpty()) TrackersStrip(ui.trackers, actions)
                    RecentCard(ui, actions)
                }
            }
        }
    }
}

/** Spec §10.4 item 6, above the balance card. The update banner comes first (R148). */
@Composable
private fun HomeBanners(ui: HomeUi, actions: HomeActions) {
    when (val u = ui.update) {
        is HomeUpdate.Available -> Banner(
            "Ledga ${u.version} is available", BannerTone.Info, icon = Ph.DownloadSimple,
            actionLabel = "Update", onAction = actions.onUpdate, secondaryLabel = "Later", onSecondary = actions.onUpdateLater,
            onClick = actions.onOpenUpdates,
        )
        is HomeUpdate.Downloading -> Banner("Downloading Ledga ${u.version}$ELLIPSIS", BannerTone.Progress, progress = u.fraction, onClick = actions.onOpenUpdates)
        is HomeUpdate.Ready -> Banner(
            "Ledga ${u.version} is ready to install", BannerTone.Info, icon = Ph.DownloadSimple,
            actionLabel = "Install", onAction = actions.onInstall, secondaryLabel = "Later", onSecondary = actions.onUpdateLater,
            onClick = actions.onOpenUpdates,
        )
        // Final review I4: why it didn't work, with Updates (Try again, the release page) one tap away.
        is HomeUpdate.Failed -> Banner(
            u.message, BannerTone.Danger,
            actionLabel = "Open Updates", onAction = actions.onOpenUpdates, secondaryLabel = "Later", onSecondary = actions.onUpdateLater,
            onClick = actions.onOpenUpdates,
        )
        null -> Unit
    }
    // R177: one number on two lines; nothing moves without a tap.
    ui.lineMerge?.let {
        Banner(it.text, BannerTone.Info, actionLabel = "Merge", onAction = actions.onMergeLines, secondaryLabel = "Not the same", onSecondary = actions.onNotSameLines)
    }
    ui.history?.let { Banner("Updating your history…", BannerTone.Progress, progress = it.fraction) }
    if (ui.legacyImportFailed) Banner(LEGACY_IMPORT_FAILED_TEXT, BannerTone.Warning)
    if (ui.hasHistory && !ui.smsGranted) Banner("Ledga can't read new M-Pesa messages", BannerTone.Warning, actionLabel = "Allow", onAction = actions.onAllowSms)
    if (ui.notificationsNudge) {
        Banner(
            "Turn on notifications to hear about Fuliza and big payments",
            BannerTone.Info,
            icon = Ph.Bell,
            actionLabel = "Turn on",
            onAction = actions.onTurnOnNotifications,
            secondaryLabel = "Not now",
            onSecondary = actions.onNotNow,
        )
    }
}

@Composable
private fun HomeSkeleton() {
    repeat(3) { Skeleton(Modifier.fillMaxWidth().height(140.dp), RoundedCornerShape(Radii.card)) }
}

/**
 * The sheets Home has open (spec §10.4): a payment, the picker over it, and the Fuliza sheet. Every M3 sheet is its own
 * dialog window above Home's snackbar, so Hide closes the Fuliza sheet as well as the payment (Review Focus #4).
 */
internal data class HomeSheets(val payment: String? = null, val picker: String? = null, val fuliza: Boolean = false) {
    fun afterHide(): HomeSheets = copy(payment = null, fuliza = false)

    companion object {
        val Saver: Saver<HomeSheets, Any> = listSaver(
            save = { listOf(it.payment, it.picker, it.fuliza) },
            restore = { HomeSheets(it[0] as String?, it[1] as String?, it[2] as Boolean) },
        )
    }
}

/** Where Home's taps lead outside Home (wired in `LedgaNavHost`). */
data class HomeNav(
    val openActivity: () -> Unit = {},
    val openTrackers: () -> Unit = {},
    /** A category's page: a tracker tile, a payment's View (4e D3). */
    val openCategory: (String) -> Unit = {},
    val openYou: () -> Unit = {},
    val openAlerts: () -> Unit = {},
    val openUpdates: () -> Unit = {},
)

/** Home's tab (route): the ViewModel, the permissions (4a M3), the sheets, and Undo after Hide. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeRoute(nav: HomeNav, vm: HomeViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sheets by rememberSaveable(stateSaver = HomeSheets.Saver) { mutableStateOf(HomeSheets()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val askSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.READ_SMS] == true) vm.onSmsGranted() else vm.onSmsDenied(context.showsRationale(Manifest.permission.READ_SMS))
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.onNotificationsResult(granted, context.showsRationale(Manifest.permission.POST_NOTIFICATIONS))
    }
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    TakeRequest(vm.fulizaAsked) { request ->
        vm.fulizaShown(request)
        sheets = sheets.copy(fuliza = true)
    }
    val actions = HomeActions(
        onSearch = {
            vm.openSearch()
            nav.openActivity()
        },
        onProfile = nav.openYou,
        // 4a M3: the first tap asks; once Android won't ask again, the next tap opens Settings.
        onAllowSms = { if (ui.smsToSettings) context.openAppSettings() else askSms.launch(SmsPermissions.ALL) },
        // R109: Android's dialog while it can still ask; otherwise (a refusal for good, Android 8–12, notifications
        // switched off in Android) Android's notification settings.
        onTurnOnNotifications = {
            if (!ui.notificationsToSettings && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.openNotificationSettings()
            }
        },
        onNotNow = vm::dismissNotifications,
        onMergeLines = { vm.mergeLines() },
        onNotSameLines = { vm.notSameLines() },
        onLine = vm::selectLine,
        onFuliza = { sheets = sheets.copy(fuliza = true) },
        onPeriod = vm::setPeriod,
        onSpending = {
            vm.openSpending()
            nav.openActivity()
        },
        onTracker = nav.openCategory,
        onAllTrackers = nav.openTrackers,
        onOpenTx = { sheets = sheets.copy(payment = it) },
        onPickCategory = { sheets = sheets.copy(picker = it) },
        onAllRecent = {
            vm.openRecent()
            nav.openActivity()
        },
        onAlerts = nav.openAlerts,
        onUpdate = { vm.downloadUpdate() },
        onInstall = { vm.installUpdate(nav.openUpdates) },
        onUpdateLater = { vm.updateLater() },
        onOpenUpdates = nav.openUpdates,
    )
    Box(Modifier.fillMaxSize()) {
        HomeContent(ui, actions)
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(Spacing.l))
    }
    FulizaSheetHost(open = sheets.fuliza, onDismiss = { sheets = sheets.copy(fuliza = false) }, onOpenTx = { sheets = sheets.copy(payment = it) })
    TransactionSheetHost(
        code = sheets.payment,
        onDismiss = { sheets = sheets.copy(payment = null) },
        onHidden = { code ->
            sheets = sheets.afterHide()
            scope.launch {
                val result = snackbar.showSnackbar("Payment hidden", actionLabel = "Undo", duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) vm.undoHide(code)
            }
        },
        onChangeCategory = { sheets = sheets.copy(picker = it) },
        onViewCategory = nav.openCategory,
    )
    CategoryPickerHost(sheets.picker, onDismiss = { sheets = sheets.copy(picker = null) })
    val notes by vm.whatsNew.collectAsStateWithLifecycle()
    val pending = notes
    if (pending != null && sheets == HomeSheets()) {
        LedgaModalSheet(onDismiss = { vm.whatsNewSeen() }, title = "What's new in Ledga ${vm.whatsNewVersion}") {
            WhatsNewContent(pending, onDone = { vm.whatsNewSeen() })
        }
    }
}

private val ELLIPSIS = Char(0x2026)
