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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
    /** Headroom still borrowable now = ceiling − current outstanding. */
    val fulizaAvailable: Double? = null,
    /** Total Fuliza ceiling (limit), independent of what's currently drawn. */
    val fulizaCeiling: Double? = null,
    /** Timestamp of the SMS the outstanding figure came from — drives the "as of" caption. */
    val fulizaOutstandingAt: Long? = null,
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

    /** Latest Fuliza figures for one line, plus the cross-line merge result. */
    private data class FulizaFacts(
        val outstanding: Double? = null,
        val outstandingAt: Long? = null,
        val ceiling: Double? = null,
        val available: Double? = null,
    )

    private val accountContext = combine(
        settingsRepository.getSelectedAccountId(),
        accountsRepository.observeAll(),
    ) { selected, accounts -> selected to accounts }

    /**
     * Balance for the Home card. Each SIM's wallet is independent, so the
     * Combined view must show the SUM of every line's latest balance — the
     * balance on the most recent SMS alone is just one line's wallet and
     * understates a dual-SIM user's real position.
     */
    private val balanceFlow: Flow<Double?> = accountContext.flatMapLatest { (selected, accounts) ->
        if (selected == null && accounts.size > 1) {
            combine(
                transactionRepository.getCombinedLatestBalance(),
                transactionRepository.getLatestTransactionWithBalance(),
            ) { combined, latest ->
                // While history is still unattributed the per-line sum is
                // NULL — fall back to the single-figure behavior.
                combined ?: latest?.balance
            }
        } else {
            transactionRepository.getLatestTransactionWithBalance().map { it?.balance }
        }
    }

    /**
     * Fuliza is per-line. With a line selected, show that line's figures;
     * on the Combined view with 2+ lines, sum each line's latest facts so
     * line A's limit never gets crossed with line B's outstanding.
     */
    private val fulizaFlow: Flow<FulizaFacts> = accountContext.flatMapLatest { (selected, accounts) ->
        val lines: List<Long?> = when {
            selected != null -> listOf(selected)
            accounts.size > 1 -> accounts.map { it.id }
            else -> listOf(null) // single/no line: unfiltered covers unattributed rows
        }
        val perLine = lines.map { id ->
            combine(
                transactionRepository.getLatestFulizaOutstandingFor(id),
                transactionRepository.getLatestFulizaLimitFor(id),
            ) { outTx, limTx ->
                // M-PESA's "Available Fuliza limit" is the headroom remaining
                // AT THAT SMS (ceiling − outstanding then). So ceiling = that
                // reading + the outstanding it was reported alongside. The
                // amount borrowable NOW = ceiling − current outstanding.
                val outstanding = outTx?.fulizaOutstanding
                val ceiling = limTx?.let { (it.fulizaLimit ?: 0.0) + (it.fulizaOutstanding ?: 0.0) }
                val available = ceiling?.let { (it - (outstanding ?: 0.0)).coerceAtLeast(0.0) }
                FulizaFacts(
                    outstanding = outstanding,
                    outstandingAt = outTx?.timestamp,
                    ceiling = ceiling,
                    available = available,
                )
            }
        }
        combine(perLine) { facts ->
            FulizaFacts(
                outstanding = facts.mapNotNull { it.outstanding }.takeIf { it.isNotEmpty() }?.sum(),
                outstandingAt = facts.mapNotNull { it.outstandingAt }.maxOrNull(),
                ceiling = facts.mapNotNull { it.ceiling }.takeIf { it.isNotEmpty() }?.sum(),
                available = facts.mapNotNull { it.available }.takeIf { it.isNotEmpty() }?.sum(),
            )
        }
    }

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
        balanceFlow,
        transactionRepository.getRecentTransactions(10),
        categoryRepository.getAllCategories()
    ) { period, balance, recentTransactions, categories ->
        HomeUiState(
            greeting = DateUtils.greeting(),
            balance = balance,
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
    }.combine(fulizaFlow) { state, fuliza ->
        state.copy(
            fulizaOutstanding = fuliza.outstanding,
            fulizaOutstandingAt = fuliza.outstandingAt,
            fulizaAvailable = fuliza.available,
            fulizaCeiling = fuliza.ceiling,
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
