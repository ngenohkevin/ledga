package com.ledga.app.ui.car

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.db.entity.CarTag
import com.ledga.app.data.db.entity.TransactionEntity
import com.ledga.app.data.db.entity.TransactionWithCategory
import com.ledga.app.data.repository.TransactionRepository
import com.ledga.app.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the Car expenses screen. All figures are account-agnostic (summed
 * across every M-PESA line) — a car costs the same no matter which SIM paid,
 * so the repository's car queries deliberately ignore the account switcher.
 */
data class CarUiState(
    val selectedTag: CarTag = CarTag.FUEL,
    /** All-time totals per tag — drive the hero headline + the toggle subtitles. */
    val fuelAllTime: Double = 0.0,
    val serviceAllTime: Double = 0.0,
    val totalAllTime: Double = 0.0,
    /** For the currently-selected tag. */
    val week: Double = 0.0,
    val month: Double = 0.0,
    val allTime: Double = 0.0,
    val transactions: List<TransactionWithCategory> = emptyList(),
    /** The row the user tapped, shown in the re-tag sheet (null = closed). */
    val selected: TransactionEntity? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CarExpensesViewModel @Inject constructor(
    private val repository: TransactionRepository,
) : ViewModel() {

    private val _selectedTag = MutableStateFlow(CarTag.FUEL)
    private val _selected = MutableStateFlow<TransactionEntity?>(null)

    // Captured once when the screen opens — "this week"/"this month" boundaries.
    // Open intervals (start .. MAX) so a payment made seconds ago still counts.
    private val weekStart = DateUtils.getStartOfWeek()
    private val monthStart = DateUtils.getStartOfMonth()

    private data class PerTag(
        val tag: CarTag,
        val week: Double,
        val month: Double,
        val allTime: Double,
        val transactions: List<TransactionWithCategory>,
    )

    private val perTag = _selectedTag.flatMapLatest { tag ->
        combine(
            repository.getCarSpending(tag, weekStart, Long.MAX_VALUE),
            repository.getCarSpending(tag, monthStart, Long.MAX_VALUE),
            repository.getCarSpending(tag, 0L, Long.MAX_VALUE),
            repository.getCarTransactions(tag),
        ) { week, month, all, txns -> PerTag(tag, week, month, all, txns) }
    }

    val uiState: StateFlow<CarUiState> = combine(
        repository.getCarSpending(CarTag.FUEL, 0L, Long.MAX_VALUE),
        repository.getCarSpending(CarTag.SERVICE, 0L, Long.MAX_VALUE),
        perTag,
        _selected,
    ) { fuelAll, serviceAll, pt, selected ->
        CarUiState(
            selectedTag = pt.tag,
            fuelAllTime = fuelAll,
            serviceAllTime = serviceAll,
            totalAllTime = fuelAll + serviceAll,
            week = pt.week,
            month = pt.month,
            allTime = pt.allTime,
            transactions = pt.transactions,
            selected = selected,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CarUiState())

    fun selectTag(tag: CarTag) {
        _selectedTag.value = tag
    }

    fun selectTransaction(transaction: TransactionEntity) {
        _selected.value = transaction
    }

    fun clearSelection() {
        _selected.value = null
    }

    /** Re-tag or untag (tag = null) the tapped transaction; closes the sheet. */
    fun setTag(transactionId: Long, tag: CarTag?) {
        viewModelScope.launch {
            repository.updateCarTag(transactionId, tag)
            _selected.value = null
        }
    }
}
