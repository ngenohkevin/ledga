package com.ledga.app.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.db.dao.TopMerchant
import com.ledga.app.data.db.entity.TransactionWithCategory
import com.ledga.app.data.parser.TransactionType
import com.ledga.app.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Direction of money flow for the People view. */
enum class PeopleMode(val label: String, val types: List<TransactionType>) {
    SENT("Sent to", listOf(TransactionType.SEND, TransactionType.MPESA_GLOBAL)),
    RECEIVED("Received from", listOf(TransactionType.RECEIVED)),
}

data class PeopleUiState(
    val mode: PeopleMode = PeopleMode.SENT,
    val query: String = "",
    val minTotal: Double = 0.0,
    val people: List<TopMerchant> = emptyList(),
    /** When non-null, the drill-down sheet shows this person's transactions. */
    val selectedPerson: String? = null,
    val selectedPersonTransactions: List<TransactionWithCategory> = emptyList(),
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    private val _mode = MutableStateFlow(PeopleMode.SENT)
    private val _query = MutableStateFlow("")
    private val _minTotal = MutableStateFlow(0.0)
    private val _selectedPerson = MutableStateFlow<String?>(null)

    private val people = combine(
        _mode,
        _query.debounce(250),
        _minTotal,
    ) { mode, q, min -> Triple(mode, q, min) }
        .flatMapLatest { (mode, q, min) ->
            transactionRepository.getPeopleByTypes(mode.types, q, min)
        }

    private val selectedTransactions = combine(_selectedPerson, _mode) { name, mode -> name to mode }
        .flatMapLatest { (name, mode) ->
            if (name == null) flowOf(emptyList())
            else transactionRepository.getTransactionsForRecipient(name, mode.types)
        }

    val uiState: StateFlow<PeopleUiState> = combine(
        combine(_mode, _query, _minTotal) { mode, q, min -> Triple(mode, q, min) },
        people,
        _selectedPerson,
        selectedTransactions,
    ) { (mode, query, minTotal), people, selected, txns ->
        PeopleUiState(
            mode = mode,
            query = query,
            minTotal = minTotal,
            people = people,
            selectedPerson = selected,
            selectedPersonTransactions = txns,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PeopleUiState())

    fun setMode(mode: PeopleMode) { _mode.value = mode }
    fun setQuery(value: String) { _query.value = value }
    fun setMinTotal(value: Double) { _minTotal.value = value }
    fun selectPerson(name: String) { _selectedPerson.value = name }
    fun clearSelection() { _selectedPerson.value = null }
}
