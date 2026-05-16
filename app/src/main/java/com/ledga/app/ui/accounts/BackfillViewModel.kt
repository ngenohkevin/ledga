package com.ledga.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.db.entity.MpesaAccount
import com.ledga.app.data.repository.AccountsRepository
import com.ledga.app.data.repository.BackfillResult
import com.ledga.app.data.repository.HistoricalBackfillRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackfillUiState(
    val accounts: List<MpesaAccount> = emptyList(),
    val isAutoRunning: Boolean = false,
    val autoProgress: Pair<Int, Int>? = null,
    val autoResult: BackfillResult? = null,
    val dateRangeResult: Int? = null,
    val dateRangeRunning: Boolean = false,
)

@HiltViewModel
class BackfillViewModel @Inject constructor(
    private val accountsRepository: AccountsRepository,
    private val historicalBackfillRepository: HistoricalBackfillRepository,
) : ViewModel() {

    private val _autoRunning = MutableStateFlow(false)
    private val _autoProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    private val _autoResult = MutableStateFlow<BackfillResult?>(null)
    private val _dateRangeResult = MutableStateFlow<Int?>(null)
    private val _dateRangeRunning = MutableStateFlow(false)

    val uiState: StateFlow<BackfillUiState> = combine(
        accountsRepository.observeAll(),
        _autoRunning,
        _autoProgress,
        _autoResult,
        combine(_dateRangeRunning, _dateRangeResult) { r, res -> r to res },
    ) { accounts, autoRunning, progress, result, (dateRunning, dateResult) ->
        BackfillUiState(
            accounts = accounts,
            isAutoRunning = autoRunning,
            autoProgress = progress,
            autoResult = result,
            dateRangeResult = dateResult,
            dateRangeRunning = dateRunning,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BackfillUiState())

    fun runAuto() {
        if (_autoRunning.value) return
        _autoRunning.value = true
        _autoResult.value = null
        viewModelScope.launch {
            try {
                val result = historicalBackfillRepository.runAutoBackfill { scanned, total ->
                    _autoProgress.value = scanned to total
                }
                _autoResult.value = result
            } finally {
                _autoRunning.value = false
                _autoProgress.value = null
            }
        }
    }

    fun runDateRange(accountId: Long, startTime: Long, endTime: Long) {
        if (_dateRangeRunning.value) return
        _dateRangeRunning.value = true
        _dateRangeResult.value = null
        viewModelScope.launch {
            try {
                val res = historicalBackfillRepository.runDateRangeBackfill(
                    accountId = accountId,
                    startTime = startTime,
                    endTime = endTime,
                )
                _dateRangeResult.value = res.updated
            } finally {
                _dateRangeRunning.value = false
            }
        }
    }
}
