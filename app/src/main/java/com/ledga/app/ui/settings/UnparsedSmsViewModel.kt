package com.ledga.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.db.entity.TransactionWithCategory
import com.ledga.app.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class UnparsedSmsViewModel @Inject constructor(
    transactionRepository: TransactionRepository
) : ViewModel() {

    val unparsedTransactions: StateFlow<List<TransactionWithCategory>> =
        transactionRepository.getUnparsedTransactions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
