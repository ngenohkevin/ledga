package com.ledga.app.ui.home

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import com.ledga.app.BuildConfig
import com.ledga.app.data.db.dao.CategorySpending
import com.ledga.app.data.db.entity.Category
import com.ledga.app.data.db.entity.TransactionWithCategory
import com.ledga.app.data.repository.CategoryRepository
import com.ledga.app.data.repository.TransactionRepository
import com.ledga.app.ui.components.DonutSegment
import com.ledga.app.ui.components.Period
import com.ledga.app.ui.components.parseColor
import com.ledga.app.util.DateUtils
import com.ledga.app.worker.GitHubRelease
import com.ledga.app.worker.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.content.Context
import javax.inject.Inject

data class HomeUiState(
    val greeting: String = "",
    val balance: Double? = null,
    val totalSpending: Double = 0.0,
    val totalFees: Double = 0.0,
    val donutSegments: List<DonutSegment> = emptyList(),
    val recentTransactions: List<TransactionWithCategory> = emptyList(),
    val selectedPeriod: Period = Period.THIS_MONTH,
    val monthLabel: String = "",
    val updateAvailable: GitHubRelease? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _updateAvailable = MutableStateFlow<GitHubRelease?>(null)

    init {
        viewModelScope.launch {
            val release = UpdateChecker.checkForUpdate(
                context = appContext,
                owner = "ngenohkevin",
                repo = "ledga",
                currentVersion = BuildConfig.VERSION_NAME
            )
            _updateAvailable.value = release
        }
    }

    private val _selectedPeriod = MutableStateFlow(Period.THIS_MONTH)

    private val timeRange = _selectedPeriod.flatMapLatest { period ->
        val now = System.currentTimeMillis()
        val start = when (period) {
            Period.TODAY -> DateUtils.getStartOfDay(now)
            Period.THIS_WEEK -> DateUtils.getStartOfWeek(now)
            Period.THIS_MONTH -> DateUtils.getStartOfMonth(now)
        }
        kotlinx.coroutines.flow.flowOf(start to now)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        _selectedPeriod,
        timeRange,
        transactionRepository.getLatestTransaction(),
        transactionRepository.getRecentTransactions(10),
        categoryRepository.getAllCategories()
    ) { period, (start, end), latestTransaction, recentTransactions, categories ->
        val categoryMap = categories.associateBy { it.id }

        HomeUiState(
            greeting = DateUtils.greeting(),
            balance = latestTransaction?.balance,
            recentTransactions = recentTransactions,
            selectedPeriod = period,
            monthLabel = DateUtils.formatMonthYear(System.currentTimeMillis())
        )
    }.combine(
        timeRange.flatMapLatest { (start, end) ->
            combine(
                transactionRepository.getTotalSpending(start, end),
                transactionRepository.getTotalFees(start, end),
                transactionRepository.getSpendingByCategory(start, end),
                categoryRepository.getAllCategories()
            ) { spending, fees, categorySpending, categories ->
                Triple(spending, fees, buildDonutSegments(categorySpending, categories))
            }
        }
    ) { state, (spending, fees, segments) ->
        state.copy(
            totalSpending = spending,
            totalFees = fees,
            donutSegments = segments
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun selectPeriod(period: Period) {
        _selectedPeriod.value = period
    }

    private fun buildDonutSegments(
        spending: List<CategorySpending>,
        categories: List<Category>
    ): List<DonutSegment> {
        val categoryMap = categories.associateBy { it.id }
        return spending
            .sortedByDescending { it.totalAmount }
            .take(6)
            .map { cs ->
                val cat = categoryMap[cs.categoryId]
                DonutSegment(
                    label = cat?.name ?: "Other",
                    value = cs.totalAmount.toFloat(),
                    color = cat?.color?.let { parseColor(it) } ?: Color.Gray
                )
            }
    }
}
