package com.ledga.app.ui.home

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.BuildConfig
import com.ledga.app.data.db.dao.CategorySpending
import com.ledga.app.data.db.entity.Category
import com.ledga.app.data.db.entity.Insight
import com.ledga.app.data.db.entity.MpesaAccount
import com.ledga.app.data.db.entity.TransactionWithCategory
import com.ledga.app.data.repository.AccountsRepository
import com.ledga.app.data.repository.CategoryRepository
import com.ledga.app.data.repository.InsightsRepository
import com.ledga.app.data.repository.SettingsRepository
import com.ledga.app.data.repository.TransactionRepository
import com.ledga.app.data.repository.UpdateRepository
import com.ledga.app.ui.components.DonutSegment
import com.ledga.app.ui.components.Period
import com.ledga.app.ui.components.parseColor
import com.ledga.app.util.DateUtils
import com.ledga.app.worker.GitHubRelease
import com.ledga.app.worker.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryBreakdownItem(
    val name: String,
    val color: String,
    val icon: String,
    val amount: Double
)

data class HomeUiState(
    val greeting: String = "",
    val balance: Double? = null,
    val totalSpending: Double = 0.0,
    val totalFees: Double = 0.0,
    val donutSegments: List<DonutSegment> = emptyList(),
    val categoryBreakdown: List<CategoryBreakdownItem> = emptyList(),
    val recentTransactions: List<TransactionWithCategory> = emptyList(),
    val selectedPeriod: Period = Period.TODAY,
    val monthLabel: String = "",
    val updateAvailable: GitHubRelease? = null,
    /** True when the background worker has already downloaded the APK. */
    val updatePrefetched: Boolean = false,
    val topInsight: Insight? = null,
    val accounts: List<MpesaAccount> = emptyList(),
    val selectedAccountId: Long? = null,
    /** Latest known Fuliza outstanding (owed) — null when never seen in SMS. */
    val fulizaOutstanding: Double? = null,
    /** Latest known available Fuliza limit (borrowable) — null when never seen. */
    val fulizaAvailable: Double? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val insightsRepository: InsightsRepository,
    private val accountsRepository: AccountsRepository,
    private val settingsRepository: SettingsRepository,
    private val updateRepository: UpdateRepository,
) : ViewModel() {

    fun selectAccount(id: Long?) {
        viewModelScope.launch { settingsRepository.setSelectedAccountId(id) }
    }

    private val _updateAvailable = MutableStateFlow<GitHubRelease?>(null)
    private val _updatePrefetched = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val release = UpdateChecker.checkForUpdate(
                context = appContext,
                owner = "ngenohkevin",
                repo = "ledga",
                currentVersion = BuildConfig.VERSION_NAME
            )
            // Respect the user's "Remind me later" snooze: hide the banner if
            // they dismissed this exact version. Newer versions still surface.
            val dismissed = settingsRepository.getDismissedUpdateVersion().first()
            val surfaced = release?.takeIf { it.tag_name != dismissed }
            _updateAvailable.value = surfaced
            // Did the background worker already pull the APK?
            _updatePrefetched.value = surfaced != null &&
                    updateRepository.findCachedApk(surfaced) != null
        }
    }

    private val _selectedPeriod = MutableStateFlow(Period.TODAY)

    private val timeRange = _selectedPeriod.flatMapLatest { period ->
        val now = System.currentTimeMillis()
        val start = when (period) {
            Period.TODAY -> DateUtils.getStartOfDay(now)
            Period.THIS_WEEK -> DateUtils.getStartOfWeek(now)
            Period.THIS_MONTH -> DateUtils.getStartOfMonth(now)
        }
        flowOf(start to now)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        _selectedPeriod,
        transactionRepository.getLatestTransactionWithBalance(),
        transactionRepository.getRecentTransactions(10),
        categoryRepository.getAllCategories()
    ) { period, latestTransaction, recentTransactions, categories ->
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
                val catMap = categories.associateBy { it.id }
                val segments = buildDonutSegments(categorySpending, categories)
                val breakdown = categorySpending
                    .sortedByDescending { it.totalAmount }
                    .mapNotNull { cs ->
                        val cat = catMap[cs.categoryId] ?: return@mapNotNull null
                        CategoryBreakdownItem(
                            name = cat.name,
                            color = cat.color,
                            icon = cat.icon,
                            amount = cs.totalAmount
                        )
                    }
                Triple(spending, fees, segments) to breakdown
            }
        }
    ) { state, (triple, breakdown) ->
        val (spending, fees, segments) = triple
        state.copy(
            totalSpending = spending,
            totalFees = fees,
            donutSegments = segments,
            categoryBreakdown = breakdown
        )
    }.combine(_updateAvailable) { state, update ->
        state.copy(updateAvailable = update)
    }.combine(_updatePrefetched) { state, prefetched ->
        state.copy(updatePrefetched = prefetched)
    }.combine(insightsRepository.observeTop()) { state, insight ->
        state.copy(topInsight = insight)
    }.combine(accountsRepository.observeAll()) { state, accounts ->
        state.copy(accounts = accounts)
    }.combine(settingsRepository.getSelectedAccountId()) { state, accountId ->
        state.copy(selectedAccountId = accountId)
    }.combine(transactionRepository.getLatestFulizaOutstanding()) { state, tx ->
        state.copy(fulizaOutstanding = tx?.fulizaOutstanding)
    }.combine(transactionRepository.getLatestFulizaLimit()) { state, tx ->
        state.copy(fulizaAvailable = tx?.fulizaLimit)
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
