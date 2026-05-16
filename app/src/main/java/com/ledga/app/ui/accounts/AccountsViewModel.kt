package com.ledga.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.db.entity.MpesaAccount
import com.ledga.app.data.repository.AccountsRepository
import com.ledga.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountsUiState(
    val accounts: List<MpesaAccount> = emptyList(),
    val selectedAccountId: Long? = null,
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountsRepository: AccountsRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<AccountsUiState> = combine(
        accountsRepository.observeAll(),
        settingsRepository.getSelectedAccountId(),
    ) { accounts, selectedId ->
        AccountsUiState(accounts = accounts, selectedAccountId = selectedId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountsUiState())

    fun rename(id: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch { accountsRepository.rename(id, newName.trim()) }
    }

    fun recolor(id: Long, hex: String) {
        viewModelScope.launch { accountsRepository.recolor(id, hex) }
    }

    fun setPrimary(id: Long) {
        viewModelScope.launch { accountsRepository.setPrimary(id) }
    }
}
