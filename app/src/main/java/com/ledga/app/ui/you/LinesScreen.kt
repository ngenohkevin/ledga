package com.ledga.app.ui.you

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.ui.app.DetailFrame
import com.ledga.app.ui.app.grouped
import com.ledga.app.ui.app.openAppSettings
import com.ledga.app.ui.app.showsRationale
import com.ledga.app.ui.design.components.Banner
import com.ledga.app.ui.design.components.BannerTone
import com.ledga.app.ui.design.components.EmptyState
import com.ledga.app.ui.design.components.LedgaCard
import com.ledga.app.ui.design.components.LedgaModalSheet
import com.ledga.app.ui.design.components.ListRow
import com.ledga.app.ui.design.components.PrimaryPill
import com.ledga.app.ui.design.components.RowDivider
import com.ledga.app.ui.design.icons.Ph
import com.ledga.app.ui.design.tokens.Spacing

data class LinesActions(
    val onBack: () -> Unit = {},
    val onRename: (LineUi) -> Unit = {},
    val onAllowPhone: () -> Unit = {},
    val onUnassigned: () -> Unit = {},
)

private const val PHONE_ACCESS_TEXT = "Allow phone access so Ledga can read your SIMs' numbers and file messages that arrive without a SIM tag."

/** You → M-Pesa lines (R65). */
@Composable
fun LinesContent(ui: LinesUi, actions: LinesActions, modifier: Modifier = Modifier) {
    DetailFrame("M-Pesa lines", onBack = actions.onBack, modifier = modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            if (ui.loaded && !ui.phoneAccess) Banner(PHONE_ACCESS_TEXT, BannerTone.Info, icon = Ph.SimCard, actionLabel = "Allow", onAction = actions.onAllowPhone)
            when {
                !ui.loaded -> Unit
                ui.lines.isEmpty() -> EmptyState("fluent_mobile_phone_with_arrow", "No lines yet", "A line appears when Ledga sees M-Pesa messages from a SIM.")
                else -> LedgaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
                    ui.lines.forEachIndexed { i, l ->
                        if (i > 0) RowDivider(Modifier.padding(horizontal = Spacing.m))
                        ListRow(
                            l.label,
                            subtitle = "${grouped(l.payments)} ${if (l.payments == 1) "payment" else "payments"}",
                            iconKey = "fluent_mobile_phone_with_arrow",
                            onClick = { actions.onRename(l) },
                        )
                    }
                }
            }
            if (ui.unattributed > 0 && ui.lines.isNotEmpty()) {
                LedgaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
                    ListRow(
                        "Not on a line",
                        subtitle = "${grouped(ui.unattributed)} ${if (ui.unattributed == 1) "payment" else "payments"} · Ledga couldn't tell which SIM",
                        iconKey = "fluent_label",
                        onClick = actions.onUnassigned,
                    )
                }
            }
        }
    }
}

/** The line's name: 1–24 characters (R65). */
@Composable
fun LineNameContent(name: String, refused: Boolean, onName: (String) -> Unit, onSave: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        OutlinedTextField(
            name,
            { onName(it.take(LinesRepository.NAME_MAX)) },
            Modifier.fillMaxWidth(),
            label = { Text("Line name") },
            singleLine = true,
            isError = refused,
            supportingText = if (refused) ({ Text("A line needs a name.") }) else null,
        )
        PrimaryPill("Save", onSave, Modifier.fillMaxWidth().padding(top = Spacing.m))
    }
}

/** You → M-Pesa lines (route): the rename sheet, and phone access (4a M3: ask first, Settings once Android won't ask). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinesScreen(onBack: () -> Unit, onUnassigned: () -> Unit, vm: LinesViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var renaming by rememberSaveable { mutableStateOf<Long?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.onPhoneResult(granted, context.showsRationale(Manifest.permission.READ_PHONE_STATE))
    }
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    LinesContent(
        ui,
        LinesActions(
            onBack = onBack,
            onRename = { renaming = it.line.id },
            onAllowPhone = { if (ui.toSettings) context.openAppSettings() else ask.launch(Manifest.permission.READ_PHONE_STATE) },
            onUnassigned = onUnassigned,
        ),
    )
    val line = ui.lines.firstOrNull { it.line.id == renaming } ?: return
    var name by rememberSaveable(line.line.id) { mutableStateOf(line.line.displayName) }
    var refused by rememberSaveable(line.line.id) { mutableStateOf(false) }
    LedgaModalSheet(onDismiss = { renaming = null }, title = "Rename ${line.label}") {
        LineNameContent(
            name, refused,
            onName = {
                name = it
                refused = false
            },
            onSave = { vm.rename(line.line.id, name) { ok -> if (ok) renaming = null else refused = true } },
        )
    }
}
