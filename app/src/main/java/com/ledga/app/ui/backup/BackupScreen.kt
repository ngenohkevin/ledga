package com.ledga.app.ui.backup

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledga.app.data.backup.Exporter
import com.ledga.app.data.backup.RestoreMode
import com.ledga.app.data.backup.RestoreSource
import com.ledga.app.ui.app.DetailFrame
import com.ledga.app.ui.app.GroupLabel
import com.ledga.app.ui.design.components.Banner
import com.ledga.app.ui.design.components.BannerTone
import com.ledga.app.ui.design.components.LedgaCard
import com.ledga.app.ui.design.components.LedgaModalSheet
import com.ledga.app.ui.design.components.ListRow
import com.ledga.app.ui.design.components.OutlinePill
import com.ledga.app.ui.design.components.PrimaryPill
import com.ledga.app.ui.design.components.RowDivider
import com.ledga.app.ui.design.components.RowTrailing
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import com.ledga.app.work.RestoreProgress

data class BackupActions(
    val onBack: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onSource: (RestoreSource) -> Unit = {},
    val onChooseFile: () -> Unit = {},
    val onDismissError: () -> Unit = {},
)

/** You → Export & restore (spec §12, R124, R125): export, Android backup, restore. Stateless. */
@Composable
fun BackupContent(ui: BackupUi, actions: BackupActions, modifier: Modifier = Modifier) {
    val c = LedgaTheme.colors
    val restoring = ui.restore is RestoreProgress.Running
    DetailFrame("Export & restore", onBack = actions.onBack, modifier = modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            when (val r = ui.restore) {
                is RestoreProgress.Running -> Banner("Restoring your history$ELLIPSIS", BannerTone.Progress, progress = r.fraction)
                is RestoreProgress.Done -> Banner(BackupText.restoredLine(r), BannerTone.Info)
                is RestoreProgress.Failed -> Banner(r.message, BannerTone.Danger)
                RestoreProgress.Idle -> Unit
            }
            ui.error?.let { Banner(it, BannerTone.Danger, actionLabel = "OK", onAction = actions.onDismissError) }
            Section("Export") {
                Text(BackupText.EXPORT, style = LedgaType.body, color = c.ink2)
                Row(Modifier.fillMaxWidth().padding(top = Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    PrimaryPill("Save to a file", actions.onSave, Modifier.weight(1f), enabled = ui.export != ExportState.Working)
                    OutlinePill("Share", actions.onShare)
                }
                BackupText.exportLine(ui.export)?.let { Text(it, Modifier.padding(top = Spacing.s), style = LedgaType.caption, color = c.muted) }
                Text(BackupText.PRIVACY, Modifier.padding(top = Spacing.s), style = LedgaType.caption, color = c.muted)
            }
            Section("Android backup") {
                Text(ui.today?.let { BackupText.savedLine(ui.savedAt, it) }.orEmpty(), style = LedgaType.bodyStrong, color = c.ink)
                Text(BackupText.ANDROID_BACKUP, Modifier.padding(top = Spacing.xs), style = LedgaType.caption, color = c.muted)
            }
            GroupLabel("Restore")
            LedgaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
                ui.sources.forEach { s ->
                    ListRow(
                        BackupText.sourceTitle(s.kind),
                        subtitle = BackupText.sourceLine(s.info),
                        iconKey = "fluent_floppy_disk",
                        onClick = if (restoring || ui.reading) null else ({ actions.onSource(s) }),
                    )
                    RowDivider(Modifier.padding(horizontal = Spacing.m))
                }
                ListRow(
                    "Choose a file",
                    subtitle = "A .ledga file, or a Ledga 1 export (.zip)",
                    iconKey = "fluent_magnifying_glass_tilted_left",
                    trailing = if (restoring || ui.reading) RowTrailing.None else RowTrailing.Chevron,
                    onClick = if (restoring || ui.reading) null else actions.onChooseFile,
                )
            }
        }
    }
}

private val ELLIPSIS = Char(0x2026)

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    GroupLabel(title)
    LedgaCard(Modifier.fillMaxWidth(), content = content)
}

/** The route: the file pickers, the share sheet, phone access, the restore sheet and Replace's confirmation ("Replace" in
 * `danger`: `danger/surfaceSheet` is a listed text pair). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onBack: () -> Unit, vm: BackupViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmReplace by rememberSaveable { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Exporter.MIME)) { uri -> uri?.let(vm::saveTo) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::pick) }
    val askPhone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.onPhoneResult() }
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    val share = ui.export as? ExportState.Share
    LaunchedEffect(share) {
        val file = share?.file ?: return@LaunchedEffect
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).setType(Exporter.MIME).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share your Ledga backup"))
        vm.shared()
    }
    BackupContent(
        ui,
        BackupActions(
            onBack = onBack,
            onSave = { save.launch(vm.exportName()) },
            onShare = vm::share,
            onSource = vm::pickSource,
            onChooseFile = { open.launch(arrayOf("*/*")) },
            onDismissError = vm::clearError,
        ),
    )
    val draft = ui.draft ?: return
    LedgaModalSheet(onDismiss = vm::dismiss, title = "Restore") {
        RestoreDraftContent(
            draft,
            ui.phoneAccess,
            RestoreDraftActions(
                onMode = vm::setMode,
                onAnswer = vm::answer,
                onAllowPhone = { askPhone.launch(Manifest.permission.READ_PHONE_STATE) },
                onRestore = { if (draft.mode == RestoreMode.REPLACE) confirmReplace = true else vm.start() },
            ),
        )
    }
    if (confirmReplace) {
        val c = LedgaTheme.colors
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text(BackupText.REPLACE_TITLE, style = LedgaType.section, color = c.ink) },
            text = { Text(BackupText.REPLACE_BODY, style = LedgaType.body, color = c.ink2) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReplace = false
                    vm.start()
                }) { Text("Replace", style = LedgaType.label, color = c.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Cancel", style = LedgaType.label, color = c.primary) } },
            containerColor = c.surfaceSheet,
        )
    }
}
