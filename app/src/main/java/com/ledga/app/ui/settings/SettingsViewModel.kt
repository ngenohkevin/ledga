package com.ledga.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.ledga.app.data.repository.BackupRepository
import com.ledga.app.data.repository.ExportImportRepository
import com.ledga.app.data.repository.ExportResult
import com.ledga.app.data.repository.FontScale
import com.ledga.app.data.repository.ImportFromFileResult
import com.ledga.app.data.repository.ImportResult
import com.ledga.app.data.repository.ReparseResult
import com.ledga.app.data.repository.SettingsRepository
import com.ledga.app.data.repository.SmsImporter
import com.ledga.app.data.repository.TransactionRepository
import com.ledga.app.ui.theme.ThemeMode
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val smsImporter: SmsImporter,
    private val exportImportRepository: ExportImportRepository,
    private val backupRepository: BackupRepository
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.getThemeMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val unparsedCount: StateFlow<Int> = transactionRepository.getUnparsedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val fontScale: StateFlow<FontScale> = settingsRepository.getFontScale()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FontScale.SYSTEM)

    val dailySummaryEnabled: StateFlow<Boolean> = settingsRepository.getDailySummaryEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val weeklySummaryEnabled: StateFlow<Boolean> = settingsRepository.getWeeklySummaryEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val budgetAlertsEnabled: StateFlow<Boolean> = settingsRepository.getBudgetAlertsEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val largeTxnAlertEnabled: StateFlow<Boolean> = settingsRepository.getLargeTransactionAlertEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val largeTxnThreshold: StateFlow<Double> = settingsRepository.getLargeTransactionThreshold()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5000.0)

    private val _importStatus = MutableStateFlow<ImportResult?>(null)
    val importStatus: StateFlow<ImportResult?> = _importStatus

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setFontScale(scale: FontScale) {
        viewModelScope.launch {
            settingsRepository.setFontScale(scale)
        }
    }

    fun setDailySummaryEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDailySummaryEnabled(enabled) }
    }

    fun setWeeklySummaryEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setWeeklySummaryEnabled(enabled) }
    }

    fun setBudgetAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBudgetAlertsEnabled(enabled) }
    }

    fun setLargeTxnAlertEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setLargeTransactionAlertEnabled(enabled) }
    }

    fun setLargeTxnThreshold(amount: Double) {
        viewModelScope.launch { settingsRepository.setLargeTransactionThreshold(amount) }
    }

    fun importSmsHistory() {
        viewModelScope.launch {
            val result = smsImporter.importHistory()
            _importStatus.value = result
        }
    }

    private val _reparseResult = MutableStateFlow<ReparseResult?>(null)
    val reparseResult: StateFlow<ReparseResult?> = _reparseResult

    fun reparseUnknown() {
        viewModelScope.launch {
            _reparseResult.value = transactionRepository.reparseUnknownTransactions()
        }
    }

    private val _reparseAllResult = MutableStateFlow<ReparseResult?>(null)
    val reparseAllResult: StateFlow<ReparseResult?> = _reparseAllResult

    private val _reparseAllRunning = MutableStateFlow(false)
    val reparseAllRunning: StateFlow<Boolean> = _reparseAllRunning

    /** Re-runs MpesaSmsParser over every stored row to pick up parser fixes. */
    fun reparseAll() {
        if (_reparseAllRunning.value) return
        _reparseAllRunning.value = true
        _reparseAllResult.value = null
        viewModelScope.launch {
            try {
                _reparseAllResult.value = transactionRepository.reparseAllTransactions()
            } finally {
                _reparseAllRunning.value = false
            }
        }
    }

    private val _exportResult = MutableStateFlow<ExportResult?>(null)
    val exportResult: StateFlow<ExportResult?> = _exportResult

    private val _fileImportResult = MutableStateFlow<ImportFromFileResult?>(null)
    val fileImportResult: StateFlow<ImportFromFileResult?> = _fileImportResult

    fun exportData(uri: Uri) {
        viewModelScope.launch {
            _exportResult.value = exportImportRepository.exportToZip(appContext, uri)
        }
    }

    fun importData(uri: Uri) {
        viewModelScope.launch {
            _fileImportResult.value = exportImportRepository.importFromZip(appContext, uri)
        }
    }

    // --- Google Drive Backup ---

    fun isSignedIn(): Boolean = backupRepository.isSignedIn(appContext)
    fun getAccountEmail(): String? = backupRepository.getAccountEmail(appContext)
    fun getSignInIntent() = backupRepository.getSignInIntent(appContext)

    private val _backupStatus = MutableStateFlow<String?>(null)
    val backupStatus: StateFlow<String?> = _backupStatus

    fun backupNow() {
        viewModelScope.launch {
            _backupStatus.value = "Backing up..."
            val success = backupRepository.performBackup(appContext)
            _backupStatus.value = if (success) "Backup complete" else "Backup failed — sign in first"
        }
    }

    fun restoreBackup() {
        viewModelScope.launch {
            _backupStatus.value = "Restoring..."
            val count = backupRepository.restoreBackup(appContext)
            _backupStatus.value = "Restored $count transactions"
        }
    }
}
