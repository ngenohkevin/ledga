package com.ledga.app.ui.you

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledga.app.ui.app.GroupLabel
import com.ledga.app.ui.app.ScreenTitle
import com.ledga.app.ui.app.SmsPermissions
import com.ledga.app.ui.app.openAppSettings
import com.ledga.app.ui.app.showsRationale
import com.ledga.app.ui.design.components.InitialAvatar
import com.ledga.app.ui.design.components.LedgaCard
import com.ledga.app.ui.design.components.LedgaModalSheet
import com.ledga.app.ui.design.components.ListRow
import com.ledga.app.ui.design.components.PrimaryPill
import com.ledga.app.ui.design.components.RowDivider
import com.ledga.app.ui.design.components.RowTrailing
import com.ledga.app.ui.design.components.SkeletonRow
import com.ledga.app.ui.design.components.WellSize
import com.ledga.app.ui.design.icons.Ph
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Sizes
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType

/** Where You's rows lead (wired in `LedgaNavHost`). Every default does nothing. */
data class YouNav(
    val openLines: () -> Unit = {},
    val openPeople: () -> Unit = {},
    val openNotifications: () -> Unit = {},
    val openAppearance: () -> Unit = {},
    val openUnreadable: () -> Unit = {},
    val openHistoryCheck: () -> Unit = {},
    val openLicences: () -> Unit = {},
    val openBackup: () -> Unit = {},
)

/** What You's taps do. Every default does nothing, for screenshots and tests. */
data class YouActions(
    val onProfile: () -> Unit = {},
    val onLines: () -> Unit = {},
    val onPeople: () -> Unit = {},
    val onNotifications: () -> Unit = {},
    val onAppearance: () -> Unit = {},
    val onRescan: () -> Unit = {},
    val onUnreadable: () -> Unit = {},
    val onHistoryCheck: () -> Unit = {},
    val onLicences: () -> Unit = {},
    val onBackup: () -> Unit = {},
)

const val RESCAN_TEXT = "Ledga reads every M-Pesa message on this phone again and adds any it missed. Your categories, notes and rules stay as they are."

/** You (spec §10.4, mockup `you`): the profile, then Money, App, Data and About, each a card of rows. */
@Composable
fun YouContent(ui: YouUi, actions: YouActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).verticalScroll(rememberScrollState())) {
        ScreenTitle("You")
        Column(Modifier.padding(horizontal = Spacing.screen).padding(bottom = Spacing.xxl)) {
            if (!ui.loaded) {
                repeat(6) { SkeletonRow() }
                return@Column
            }
            Profile(ui, actions.onProfile)
            Group("Money") {
                ListRow("M-Pesa lines", subtitle = YouText.linesLine(ui.lines), iconKey = "fluent_mobile_phone_with_arrow", onClick = actions.onLines)
                Divider()
                ListRow("People", subtitle = "Who you send to and receive from", iconKey = "fluent_busts_in_silhouette", onClick = actions.onPeople)
            }
            Group("App") {
                ListRow("Notifications", subtitle = ui.notifications, iconKey = "fluent_bell", onClick = actions.onNotifications)
                Divider()
                ListRow("Appearance", subtitle = ui.appearance, iconKey = "fluent_artist_palette", onClick = actions.onAppearance)
            }
            Group("Data") {
                ListRow("Export & restore", subtitle = "Full backup file (.ledga) + spreadsheet", iconKey = "fluent_floppy_disk", onClick = actions.onBackup)
                Divider()
                ListRow("Android backup", subtitle = ui.backup, iconKey = "fluent_cloud", onClick = actions.onBackup)
                Divider()
                ListRow("Rescan SMS inbox", subtitle = YouText.rescanLine(ui.rescan), iconKey = "fluent_incoming_envelope", onClick = actions.onRescan)
                Divider()
                ListRow("Messages Ledga couldn't read", subtitle = YouText.unreadableLine(ui.unreadable), iconKey = "fluent_memo", onClick = actions.onUnreadable)
                Divider()
                ListRow("History check", subtitle = "Checks that your balances add up", iconKey = "fluent_check_mark_button", onClick = actions.onHistoryCheck)
            }
            // R66: Updates and Version history join About in Phase 6.
            Group("About") {
                ListRow("Open-source licences", subtitle = "The fonts, icons and libraries Ledga uses", iconKey = "fluent_sparkles", onClick = actions.onLicences)
                Divider()
                ListRow("Version", iconKey = "fluent_rocket", trailing = RowTrailing.Value(ui.version))
            }
        }
    }
}

@Composable
private fun Profile(ui: YouUi, onClick: () -> Unit) {
    val c = LedgaTheme.colors
    LedgaCard(Modifier.fillMaxWidth().padding(top = Spacing.s), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            val name = ui.name
            if (name != null) {
                InitialAvatar(name, inflow = true, size = WellSize.Large)
            } else {
                Box(Modifier.size(WellSize.Large.box).clip(RoundedCornerShape(WellSize.Large.radius)).background(c.plate), contentAlignment = Alignment.Center) {
                    Icon(Ph.UserCircle, contentDescription = null, tint = c.ink2, modifier = Modifier.size(Sizes.icon))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(name ?: "Add your name", style = LedgaType.section, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(YouText.profileLine(ui.payments, ui.since), style = LedgaType.caption, color = c.muted)
            }
        }
    }
}

@Composable
private fun Group(title: String, rows: @Composable ColumnScope.() -> Unit) {
    GroupLabel(title)
    LedgaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs), content = rows)
}

@Composable
private fun Divider() = RowDivider(Modifier.padding(horizontal = Spacing.m))

/** R81: the optional name Home greets; blank clears it. */
@Composable
fun NameContent(name: String, onName: (String) -> Unit, onSave: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        OutlinedTextField(
            name,
            { onName(it.take(YouViewModel.NAME_MAX)) },
            Modifier.fillMaxWidth(),
            label = { Text("Your name") },
            singleLine = true,
            supportingText = { Text("Ledga greets you with it. It stays on this phone.") },
        )
        PrimaryPill("Save", onSave, Modifier.fillMaxWidth().padding(top = Spacing.m))
    }
}

/** R77: what a rescan does, before it does it. */
@Composable
fun RescanContent(onStart: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text(RESCAN_TEXT, style = LedgaType.body, color = LedgaTheme.colors.ink2)
        PrimaryPill("Rescan", onStart, Modifier.fillMaxWidth().padding(top = Spacing.l))
    }
}

private enum class YouSheet { NAME, RESCAN }

/** You's tab (route): the name and rescan sheets, and SMS access for a rescan (4a M3). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouScreen(nav: YouNav, vm: YouViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sheet by rememberSaveable { mutableStateOf<YouSheet?>(null) }
    val askSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        vm.onSmsResult(result[Manifest.permission.READ_SMS] == true, context.showsRationale(Manifest.permission.READ_SMS))
    }
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    YouContent(
        ui,
        YouActions(
            onProfile = { sheet = YouSheet.NAME },
            onLines = nav.openLines,
            onPeople = {
                vm.openPeople()
                nav.openPeople()
            },
            onNotifications = nav.openNotifications,
            onAppearance = nav.openAppearance,
            onRescan = { sheet = YouSheet.RESCAN },
            onUnreadable = nav.openUnreadable,
            onHistoryCheck = nav.openHistoryCheck,
            onLicences = nav.openLicences,
            onBackup = nav.openBackup,
        ),
    )
    when (sheet) {
        YouSheet.NAME -> {
            var name by rememberSaveable { mutableStateOf(ui.name.orEmpty()) }
            LedgaModalSheet(onDismiss = { sheet = null }, title = "Your name") {
                NameContent(name, onName = { name = it }, onSave = {
                    vm.setName(name)
                    sheet = null
                })
            }
        }
        YouSheet.RESCAN -> LedgaModalSheet(onDismiss = { sheet = null }, title = "Rescan SMS inbox") {
            RescanContent(onStart = {
                sheet = null
                if (!vm.rescan()) {
                    if (ui.smsToSettings) context.openAppSettings() else askSms.launch(SmsPermissions.ALL)
                }
            })
        }
        null -> Unit
    }
}
