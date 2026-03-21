package com.ledga.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledga.app.data.repository.FontScale
import com.ledga.app.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    onNavigateToUnparsed: () -> Unit,
    onNavigateToBudgets: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
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
    val exportResult by viewModel.exportResult.collectAsState()
    val fileImportResult by viewModel.fileImportResult.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let { viewModel.exportData(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importData(it) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        // Appearance
        SettingsSection(title = "Appearance") {
            ThemeSelector(
                currentMode = themeMode,
                onSelect = { viewModel.setThemeMode(it) }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("Font Size", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FontScale.entries.forEach { scale ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setFontScale(scale) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = fontScale == scale,
                        onClick = { viewModel.setFontScale(scale) }
                    )
                    Text(text = scale.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        // Notifications
        SettingsSection(title = "Notifications") {
            SettingsToggle("Daily Summary", dailySummary) { viewModel.setDailySummaryEnabled(it) }
            SettingsToggle("Weekly Summary", weeklySummary) { viewModel.setWeeklySummaryEnabled(it) }
            SettingsToggle("Budget Alerts", budgetAlerts) { viewModel.setBudgetAlertsEnabled(it) }
            SettingsToggle("Large Transaction Alert", largeTxnAlert) { viewModel.setLargeTxnAlertEnabled(it) }
        }

        // Budgets
        SettingsSection(title = "Budgets") {
            Text(
                text = "Manage Budgets",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToBudgets)
                    .padding(vertical = 8.dp)
            )
        }

        // Data
        SettingsSection(title = "Data") {
            OutlinedButton(
                onClick = { viewModel.importSmsHistory() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import SMS History")
            }

            if (importStatus != null) {
                Text(
                    text = "Imported ${importStatus!!.imported} of ${importStatus!!.total} messages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { exportLauncher.launch("ledga-export.zip") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export Data (CSV + JSON)")
            }
            if (exportResult != null) {
                Text(
                    text = "Exported ${exportResult!!.count} transactions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/zip")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import Data from ZIP")
            }
            if (fileImportResult != null) {
                Text(
                    text = "Imported ${fileImportResult!!.imported} of ${fileImportResult!!.total} (${fileImportResult!!.skipped} duplicates skipped)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (unparsedCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.reparseUnknown() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Re-parse Unparsed Messages ($unparsedCount)")
                }
                if (reparseResult != null) {
                    Text(
                        text = "Fixed ${reparseResult!!.fixed} of ${reparseResult!!.total} (${reparseResult!!.stillUnknown} still unknown)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "View Unparsed Messages",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToUnparsed)
                        .padding(vertical = 8.dp)
                )
            }
        }

        // Backup
        SettingsSection(title = "Backup") {
            val backupStatus by viewModel.backupStatus.collectAsState()
            val signedIn = viewModel.isSignedIn()
            val email = viewModel.getAccountEmail()

            if (signedIn && email != null) {
                Text(
                    text = "Google Account: $email",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedButton(
                onClick = { viewModel.backupNow() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Back Up Now")
            }

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedButton(
                onClick = { viewModel.restoreBackup() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Restore from Backup")
            }

            if (backupStatus != null) {
                Text(
                    text = backupStatus!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // About
        SettingsSection(title = "About") {
            Text(
                text = "Ledga v1.0.0",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "All data stays on your device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ThemeSelector(
    currentMode: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    ThemeMode.entries.forEach { mode ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(mode) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = currentMode == mode,
                onClick = { onSelect(mode) }
            )
            Text(
                text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun SettingsToggle(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
