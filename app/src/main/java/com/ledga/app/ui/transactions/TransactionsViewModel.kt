package com.ledga.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.db.entity.Category
import com.ledga.app.data.db.entity.Goal
import com.ledga.app.data.db.entity.MpesaAccount
import com.ledga.app.data.db.entity.TransactionWithCategory
import com.ledga.app.data.parser.TransactionType
import com.ledga.app.data.repository.AccountsRepository
import com.ledga.app.data.repository.CategoryRepository
import com.ledga.app.data.repository.GoalsRepository
import com.ledga.app.data.repository.SettingsRepository
import com.ledga.app.data.repository.TransactionRepository
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import com.ledga.app.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class TransactionsUiState(
    val groupedTransactions: Map<String, List<TransactionWithCategory>> = emptyMap(),
    val searchQuery: String = "",
    val activeFilter: String = "All",
    val categories: List<Category> = emptyList(),
    val accounts: List<MpesaAccount> = emptyList(),
    val manualGoals: List<Goal> = emptyList(),
    /** Goal IDs the currently-selected transaction is already manually attributed to. */
    val selectedTxGoalIds: List<Long> = emptyList(),
    /** Recipient fragments marked as the user's own accounts (transfers). */
    val ownAccountFragments: List<String> = emptyList(),
    val selectedTransaction: TransactionWithCategory? = null
) {
    fun isOwnAccount(recipientName: String?): Boolean {
        val name = recipientName ?: return false
        return ownAccountFragments.any { name.contains(it, ignoreCase = true) }
    }
}

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountsRepository: AccountsRepository,
    private val goalsRepository: GoalsRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _activeFilter = MutableStateFlow("All")

    companion object {
        /** Amount-based filter (not a transaction type) — handled specially. */
        const val LARGE_FILTER = "Large"

        val FILTERS = mapOf(
            "All" to emptyList<TransactionType>(),
            "Sent" to listOf(TransactionType.SEND, TransactionType.MPESA_GLOBAL),
            "Received" to listOf(TransactionType.RECEIVED),
            "Bills" to listOf(TransactionType.PAY_BILL),
            "Goods" to listOf(TransactionType.BUY_GOODS),
            "Withdraw" to listOf(TransactionType.WITHDRAW_AGENT, TransactionType.WITHDRAW_ATM),
            // Amount threshold, not a type — resolved against the user's
            // large-transaction setting (default Ksh5,000, configurable).
            LARGE_FILTER to emptyList(),
        )
    }

    private val transactions = combine(
        _searchQuery.debounce(300),
        _activeFilter,
        settingsRepository.getLargeTransactionThreshold(),
    ) { query, filter, threshold ->
        Triple(query, filter, threshold)
    }.flatMapLatest { (query, filter, threshold) ->
        when {
            query.isNotBlank() -> transactionRepository.searchTransactions(query)
            filter == LARGE_FILTER -> transactionRepository.getLargeTransactions(threshold)
            else -> {
                val types = FILTERS[filter] ?: emptyList()
                if (types.isNotEmpty()) transactionRepository.getTransactionsByType(types)
                else transactionRepository.getRecentTransactions(200)
            }
        }
    }

    private val _selectedTransaction = MutableStateFlow<TransactionWithCategory?>(null)

    private val selectedTxGoalIds = _selectedTransaction.flatMapLatest { selected ->
        if (selected == null) flowOf(emptyList())
        else goalsRepository.observeGoalIdsForTransaction(selected.transaction.id)
    }

    val uiState: StateFlow<TransactionsUiState> = combine(
        transactions,
        _searchQuery,
        _activeFilter,
        categoryRepository.getAllCategories(),
        combine(
            _selectedTransaction,
            accountsRepository.observeAll(),
            goalsRepository.observeManualGoals(),
            selectedTxGoalIds,
            transactionRepository.getOwnAccountFragments(),
        ) { sel, accts, manualGoals, txGoals, ownFragments ->
            GoalsBundle(
                selected = sel, accounts = accts, manualGoals = manualGoals,
                txGoals = txGoals, ownFragments = ownFragments,
            )
        },
    ) { txns, query, filter, categories, bundle ->
        val grouped = txns.groupBy { twc ->
            DateUtils.formatRelativeDate(twc.transaction.timestamp)
        }
        TransactionsUiState(
            groupedTransactions = grouped,
            searchQuery = query,
            activeFilter = filter,
            categories = categories,
            accounts = bundle.accounts,
            manualGoals = bundle.manualGoals,
            selectedTxGoalIds = bundle.txGoals,
            ownAccountFragments = bundle.ownFragments,
            selectedTransaction = bundle.selected
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TransactionsUiState())

    private data class GoalsBundle(
        val selected: TransactionWithCategory?,
        val accounts: List<MpesaAccount>,
        val manualGoals: List<Goal>,
        val txGoals: List<Long>,
        val ownFragments: List<String>,
    )

    /** Current "large" threshold (shared with the large-transaction alert). */
    val largeThreshold: StateFlow<Double> = settingsRepository.getLargeTransactionThreshold()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5000.0)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: String) {
        _activeFilter.value = filter
    }

    fun setLargeThreshold(amount: Double) {
        viewModelScope.launch { settingsRepository.setLargeTransactionThreshold(amount) }
    }

    fun selectTransaction(twc: TransactionWithCategory) {
        _selectedTransaction.value = twc
    }

    fun clearSelection() {
        _selectedTransaction.value = null
    }

    fun changeCategory(transactionId: Long, categoryId: Long) {
        viewModelScope.launch {
            transactionRepository.updateCategory(transactionId, categoryId)
            _selectedTransaction.value = null
        }
    }

    fun changeAccount(transactionId: Long, accountId: Long?) {
        viewModelScope.launch {
            transactionRepository.updateAccount(transactionId, accountId)
            _selectedTransaction.value = null
        }
    }

    /** Toggle membership of the selected transaction in a Manual-rule goal. */
    fun toggleGoalContribution(transactionId: Long, goalId: Long, currentlyIn: Boolean) {
        viewModelScope.launch {
            if (currentlyIn) goalsRepository.removeManualContribution(goalId, transactionId)
            else goalsRepository.addManualContribution(goalId, transactionId)
        }
    }

    /** Mark/unmark the selected transaction's recipient as the user's own account. */
    fun toggleOwnAccount(recipientName: String, currentlyMarked: Boolean) {
        viewModelScope.launch {
            if (currentlyMarked) transactionRepository.unmarkRecipientAsOwnAccount(recipientName)
            else transactionRepository.markRecipientAsOwnAccount(recipientName)
        }
    }
}
