package com.ledga.app.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.db.dao.CategorySpending
import com.ledga.app.data.db.dao.DailySpending
import com.ledga.app.data.db.dao.TopMerchant
import com.ledga.app.data.repository.CategoryRepository
import com.ledga.app.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

enum class TrendsPeriod(val label: String, val days: Int) {
    WEEK("7D", 7),
    MONTH("30D", 30),
    QUARTER("90D", 90),
    YEAR("1Y", 365)
}

data class TrendsUiState(
    val selectedPeriod: TrendsPeriod = TrendsPeriod.MONTH,
    val dailySpending: List<DailySpending> = emptyList(),
    val totalSpending: Double = 0.0,
    val totalFees: Double = 0.0,
    val topMerchants: List<TopMerchant> = emptyList(),
    val categorySpending: List<CategorySpendingWithName> = emptyList()
)

data class CategorySpendingWithName(
    val name: String,
    val color: String,
    val totalAmount: Double
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class TrendsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(TrendsPeriod.MONTH)

    private val timeRange = _selectedPeriod.flatMapLatest { period ->
        val now = System.currentTimeMillis()
        val start = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -period.days)
        }.timeInMillis
        flowOf(start to now)
    }

    val uiState: StateFlow<TrendsUiState> = combine(
        _selectedPeriod,
        timeRange.flatMapLatest { (s, e) -> transactionRepository.getDailySpending(s, e) },
        timeRange.flatMapLatest { (s, e) -> transactionRepository.getTotalSpending(s, e) },
        timeRange.flatMapLatest { (s, e) -> transactionRepository.getTotalFees(s, e) },
        timeRange.flatMapLatest { (s, e) -> transactionRepository.getTopMerchants(s, e) },
    ) { period, daily, spending, fees, merchants ->
        TrendsUiState(
            selectedPeriod = period,
            dailySpending = daily,
            totalSpending = spending,
            totalFees = fees,
            topMerchants = merchants,
        )
    }.combine(
        combine(
            timeRange.flatMapLatest { (s, e) -> transactionRepository.getSpendingByCategory(s, e) },
            categoryRepository.getAllCategories()
        ) { spending, categories ->
            val catMap = categories.associateBy { it.id }
            spending.map { cs ->
                val cat = catMap[cs.categoryId]
                CategorySpendingWithName(
                    name = cat?.name ?: "Other",
                    color = cat?.color ?: "#9E9E9E",
                    totalAmount = cs.totalAmount
                )
            }.sortedByDescending { it.totalAmount }
        }
    ) { state, categorySpending ->
        state.copy(categorySpending = categorySpending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrendsUiState())

    fun selectPeriod(period: TrendsPeriod) {
        _selectedPeriod.value = period
    }
}
