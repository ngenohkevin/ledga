package com.ledga.app.ui.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledga.app.data.update.PackageInstallerUpdates
import com.ledga.app.ui.app.DetailFrame
import com.ledga.app.ui.app.GroupLabel
import com.ledga.app.ui.design.components.Banner
import com.ledga.app.ui.design.components.BannerTone
import com.ledga.app.ui.design.components.LedgaCard
import com.ledga.app.ui.design.components.LinkButton
import com.ledga.app.ui.design.components.ListRow
import com.ledga.app.ui.design.components.OutlinePill
import com.ledga.app.ui.design.components.PrimaryPill
import com.ledga.app.ui.design.components.RowTrailing
import com.ledga.app.ui.design.components.SkeletonRow
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType

/** What You → Updates' taps do. Every default does nothing, for screenshots and tests. */
data class UpdatesActions(
    val onBack: () -> Unit = {},
    val onCheck: () -> Unit = {},
    val onDownload: () -> Unit = {},
    val onInstall: () -> Unit = {},
    val onSkip: () -> Unit = {},
    val onBeta: (Boolean) -> Unit = {},
    val onAllowInstalls: () -> Unit = {},
    val onReleasePage: (String) -> Unit = {},
)

private val ELLIPSIS = Char(0x2026)

/** You → About → Updates (spec §13.4, mockup `you`): where the newest release stands, its notes, and the beta switch. */
@Composable
fun UpdatesContent(ui: UpdatesUi, actions: UpdatesActions, modifier: Modifier = Modifier) {
    DetailFrame("Updates", onBack = actions.onBack, modifier = modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            if (!ui.loaded) {
                repeat(3) { SkeletonRow() }
                return@Column
            }
            if (!ui.offersUpdates) Banner(UpdateText.DEV_ONLY, BannerTone.Info)
            ui.installFailure?.let { Banner(it, BannerTone.Danger) }
            if (ui.status is UpdateStatus.Ready && !ui.canInstall) {
                Banner(UpdateText.ALLOW_INSTALLS, BannerTone.Warning, actionLabel = "Allow", onAction = actions.onAllowInstalls)
            }
            LedgaCard(Modifier.fillMaxWidth()) { Status(ui, actions) }
            if (ui.status !is UpdateStatus.UpToDate) {
                // The notes carry their own "What's new" / "Fixes" headings.
                GroupLabel("Release notes")
                LedgaCard(Modifier.fillMaxWidth()) { NotesList(ui.notes) }
                ui.pageUrl?.let { url -> LinkButton("Open its release page", { actions.onReleasePage(url) }) }
            }
            GroupLabel("Channel")
            LedgaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
                ListRow("Beta updates", subtitle = ui.betaLine, iconKey = "fluent_sparkles", trailing = RowTrailing.Toggle(ui.beta, actions.onBeta))
            }
        }
    }
}

@Composable
private fun ColumnScope.Status(ui: UpdatesUi, actions: UpdatesActions) {
    val c = LedgaTheme.colors
    when (val s = ui.status) {
        UpdateStatus.UpToDate -> {
            Text("Ledga is up to date", style = LedgaType.section, color = c.ink)
            Text("You have ${ui.installed}", style = LedgaType.body, color = c.ink2)
        }
        is UpdateStatus.Available -> {
            Text("Ledga ${s.version} is available", style = LedgaType.section, color = c.ink)
            Text(listOfNotNull(s.size, "you have ${ui.installed}").joinToString(" · "), style = LedgaType.body, color = c.ink2)
            if (s.skipped) Text("You skipped this version", style = LedgaType.caption, color = c.muted)
            if (ui.offersUpdates) {
                Row(Modifier.fillMaxWidth().padding(top = Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    PrimaryPill("Download", actions.onDownload, Modifier.weight(1f))
                    if (!s.skipped) OutlinePill("Skip this version", actions.onSkip)
                }
            }
        }
        is UpdateStatus.Downloading -> {
            Text("Downloading Ledga ${s.version}$ELLIPSIS", style = LedgaType.section, color = c.ink)
            s.fraction?.let { f ->
                LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth().padding(top = Spacing.s), color = c.primary, trackColor = c.barTrack)
            }
            Text("You can leave this screen. Ledga says when it's ready.", Modifier.padding(top = Spacing.s), style = LedgaType.caption, color = c.muted)
        }
        is UpdateStatus.Ready -> {
            Text("Ledga ${s.version} is ready to install", style = LedgaType.section, color = c.ink)
            Text("Android may ask you to confirm.", style = LedgaType.body, color = c.ink2)
            PrimaryPill("Install", actions.onInstall, Modifier.fillMaxWidth().padding(top = Spacing.m))
        }
        is UpdateStatus.Failed -> {
            Text("Ledga ${s.version} didn't download", style = LedgaType.section, color = c.ink)
            Text(s.message, style = LedgaType.body, color = c.ink2)
            PrimaryPill("Try again", actions.onDownload, Modifier.fillMaxWidth().padding(top = Spacing.m))
        }
    }
    Text(ui.failureLine ?: ui.checkedLine, Modifier.padding(top = Spacing.m), style = LedgaType.caption, color = if (ui.failureLine != null) c.warning else c.muted)
    if (ui.checking) {
        Text("Checking for updates$ELLIPSIS", style = LedgaType.caption, color = c.muted)
    } else {
        LinkButton("Check now", actions.onCheck)
    }
}

/** You → About → Updates (route): Android's install switch and the release page leave Ledga. */
@Composable
fun UpdatesScreen(onBack: () -> Unit, vm: UpdatesViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    UpdatesContent(
        ui,
        UpdatesActions(
            onBack = onBack,
            onCheck = { vm.checkNow() },
            onDownload = { vm.download() },
            onInstall = { vm.install { context.open(PackageInstallerUpdates.settingsIntent(context)) } },
            onSkip = { vm.skip() },
            onBeta = { vm.setBeta(it) },
            onAllowInstalls = { context.open(PackageInstallerUpdates.settingsIntent(context)) },
            onReleasePage = { url -> context.open(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
        ),
    )
}

/** A screen outside Ledga; a phone with nothing to open it does nothing. */
private fun Context.open(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Unit
    }
}
