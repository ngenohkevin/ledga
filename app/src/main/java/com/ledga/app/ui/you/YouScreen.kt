package com.ledga.app.ui.you

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledga.app.BuildConfig
import com.ledga.app.data.repository.FontScale
import com.ledga.app.ui.components.v2.Avatar
import com.ledga.app.ui.components.v2.BentoCard
import com.ledga.app.ui.components.v2.LedgaTopBar
import com.ledga.app.ui.settings.SettingsViewModel
import com.ledga.app.ui.theme.LedgaAccent
import com.ledga.app.ui.theme.LedgaAccentDeep
import com.ledga.app.ui.theme.LedgaText
import com.ledga.app.ui.theme.Space
import com.ledga.app.ui.theme.ThemeMode

/**
 * You — profile + consolidated settings (LEDGA_REDESIGN.md §4.7).
 *
 * Reuses the existing [SettingsViewModel] (data + repo are unchanged in
 * Phase B) but renders everything with v2 bento cards.
 */
@Composable
fun YouScreen(
    onNavigateToUnparsed: () -> Unit,
    onNavigateToBudgets: () -> Unit,
    onNavigateToGoals: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToUpdate: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val fontScale by viewModel.fontScale.collectAsState()
    val dailySummary by viewModel.dailySummaryEnabled.collectAsState()
    val weeklySummary by viewModel.weeklySummaryEnabled.collectAsState()
    val budgetAlerts by viewModel.budgetAlertsEnabled.collectAsState()
    val largeTxnAlert by viewModel.largeTxnAlertEnabled.collectAsState()
    val unparsedCount by viewModel.unparsedCount.collectAsState()
    val importStatus by viewModel.importStatus.collectAsState()
    val reparseResult by viewModel.reparseResult.collectAsState()
    val reparseAllResult by viewModel.reparseAllResult.collectAsState()
    val reparseAllRunning by viewModel.reparseAllRunning.collectAsState()
    val exportResult by viewModel.exportResult.collectAsState()
    val fileImportResult by viewModel.fileImportResult.collectAsState()
    val backupStatus by viewModel.backupStatus.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let { viewModel.exportData(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importData(it) } }

    Column(modifier = Modifier.fillMaxSize()) {
        LedgaTopBar(title = "You")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.Screen)
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(Space.Section),
        ) {
            // ---- Profile card ----
            BentoCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.s5),
                ) {
                    Avatar(initials = "KN", color = LedgaAccent, size = 48.dp)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "Your tracker",
                            style = LedgaText.TitleM,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "All data stays on this device",
                            style = LedgaText.BodyM,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ---- Personal tools ----
            BentoCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                NavRow(
                    icon = Icons.Filled.Flag,
                    iconTint = LedgaAccentDeep,
                    label = "Goals",
                    trailing = "—",
                    onClick = onNavigateToGoals,
                )
                CardDivider()
                NavRow(
                    icon = Icons.Filled.Wallet,
                    iconTint = LedgaAccentDeep,
                    label = "Budgets",
                    onClick = onNavigateToBudgets,
                )
                CardDivider()
                NavRow(
                    icon = Icons.Filled.SimCard,
                    iconTint = LedgaAccentDeep,
                    label = "Accounts",
                    trailing = "1 line",
                    onClick = onNavigateToAccounts,
                )
            }

            // ---- Appearance ----
            BentoCard(title = "Appearance", icon = Icons.Filled.Palette) {
                Text(
                    text = "Theme".uppercase(),
                    style = LedgaText.Overline,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ThemeMode.entries.forEach { mode ->
                    RadioRow(
                        label = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                        selected = themeMode == mode,
                        onSelect = { viewModel.setThemeMode(mode) },
                    )
                }
                Text(
                    text = "Font size".uppercase(),
                    style = LedgaText.Overline,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.s3),
                )
                FontScale.entries.forEach { scale ->
                    RadioRow(
                        label = scale.label,
                        selected = fontScale == scale,
                        onSelect = { viewModel.setFontScale(scale) },
                    )
                }
            }

            // ---- Notifications ----
            BentoCard(title = "Notifications", icon = Icons.Filled.Notifications) {
                ToggleRow("Daily summary", dailySummary) { viewModel.setDailySummaryEnabled(it) }
                ToggleRow("Weekly summary", weeklySummary) { viewModel.setWeeklySummaryEnabled(it) }
                ToggleRow("Budget alerts", budgetAlerts) { viewModel.setBudgetAlertsEnabled(it) }
                ToggleRow("Large-transaction alert", largeTxnAlert) { viewModel.setLargeTxnAlertEnabled(it) }
            }

            // ---- Data ----
            BentoCard(title = "Data", icon = Icons.Filled.Storage) {
                TextButtonRow("Import SMS history") { viewModel.importSmsHistory() }
                importStatus?.let {
                    StatusLine("Imported ${it.imported} of ${it.total} messages")
                }
                TextButtonRow("Export (ZIP — CSV + JSON)") {
                    exportLauncher.launch("ledga-export.zip")
                }
                exportResult?.let { StatusLine("Exported ${it.count} transactions") }
                TextButtonRow("Restore from ZIP") {
                    importLauncher.launch(arrayOf("application/zip"))
                }
                fileImportResult?.let {
                    StatusLine(
                        "Imported ${it.imported} of ${it.total} (${it.skipped} duplicates)"
                    )
                }
                if (unparsedCount > 0) {
                    TextButtonRow("Re-parse $unparsedCount unparsed messages") {
                        viewModel.reparseUnknown()
                    }
                    reparseResult?.let {
                        StatusLine(
                            "Fixed ${it.fixed} of ${it.total} (${it.stillUnknown} still unknown)"
                        )
                    }
                    TextButtonRow("View unparsed messages", onNavigateToUnparsed)
                }
                if (reparseAllRunning) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = LedgaAccent,
                        )
                        Text(
                            text = "Re-parsing all transactions…",
                            style = LedgaText.BodyL,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                } else {
                    TextButtonRow("Re-parse ALL transactions") {
                        viewModel.reparseAll()
                    }
                }
                reparseAllResult?.let {
                    StatusLine(
                        if (it.total == 0) "No transactions yet — try Import SMS history first."
                        else "Re-parsed ${it.fixed} of ${it.total} (${it.stillUnknown} skipped)"
                    )
                }
            }

            // ---- Backup ----
            BentoCard(title = "Backup", icon = Icons.Filled.Backup) {
                val signedIn = viewModel.isSignedIn()
                val email = viewModel.getAccountEmail()
                if (signedIn && email != null) {
                    StatusLine("Google account: $email")
                }
                TextButtonRow("Back up now") { viewModel.backupNow() }
                TextButtonRow("Restore from backup") { viewModel.restoreBackup() }
                backupStatus?.let { StatusLine(it) }
            }

            // ---- About ----
            BentoCard(title = "About", icon = Icons.Filled.Info) {
                Text(
                    text = "Ledga v${BuildConfig.VERSION_NAME}",
                    style = LedgaText.BodyM,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "All data stays on your device.",
                    style = LedgaText.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButtonRow("Check for updates · What's new", onNavigateToUpdate)
            }

            Box(modifier = Modifier.padding(bottom = Space.s7))
        }
    }
}

// ---- Local helpers ----

@Composable
private fun NavRow(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    label: String,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = Space.Card, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = LedgaText.BodyL,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = LedgaText.BodyM,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun CardDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(horizontal = Space.Card),
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(
            text = label,
            style = LedgaText.BodyL,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = LedgaText.BodyL,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedTrackColor = LedgaAccent,
            ),
        )
    }
}

@Composable
private fun TextButtonRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = LedgaText.BodyL,
        color = LedgaAccentDeep,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
    )
}

@Composable
private fun StatusLine(text: String) {
    Text(
        text = text,
        style = LedgaText.Caption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

