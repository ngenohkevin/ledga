package com.ledga.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.db.entity.Category
import com.ledga.app.data.db.entity.TransactionWithCategory
import com.ledga.app.data.parser.TransactionType
import com.ledga.app.data.repository.CategoryRepository
import com.ledga.app.data.repository.TransactionRepository
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
    val selectedTransaction: TransactionWithCategory? = null
)

@OptIn(FlowPreview::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _activeFilter = MutableStateFlow("All")

    companion object {
        val FILTERS = mapOf(
            "All" to emptyList<TransactionType>(),
            "Sent" to listOf(TransactionType.SEND, TransactionType.MPESA_GLOBAL),
            "Received" to listOf(TransactionType.RECEIVED),
            "Bills" to listOf(TransactionType.PAY_BILL),
            "Goods" to listOf(TransactionType.BUY_GOODS),
            "Withdraw" to listOf(TransactionType.WITHDRAW_AGENT, TransactionType.WITHDRAW_ATM),
        )
    }

    private val transactions = combine(
        _searchQuery.debounce(300),
        _activeFilter
    ) { query, filter ->
        Pair(query, filter)
    }.flatMapLatest { (query, filter) ->
        val types = FILTERS[filter] ?: emptyList()
        when {
            query.isNotBlank() -> transactionRepository.searchTransactions(query)
            types.isNotEmpty() -> transactionRepository.getTransactionsByType(types)
            else -> transactionRepository.getRecentTransactions(200)
        }
    }

    private val _selectedTransaction = MutableStateFlow<TransactionWithCategory?>(null)

    val uiState: StateFlow<TransactionsUiState> = combine(
        transactions,
        _searchQuery,
        _activeFilter,
        categoryRepository.getAllCategories(),
        _selectedTransaction
    ) { txns, query, filter, categories, selected ->
        val grouped = txns.groupBy { twc ->
            DateUtils.formatRelativeDate(twc.transaction.timestamp)
        }
        TransactionsUiState(
            groupedTransactions = grouped,
            searchQuery = query,
            activeFilter = filter,
            categories = categories,
            selectedTransaction = selected
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TransactionsUiState())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: String) {
        _activeFilter.value = filter
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
}
