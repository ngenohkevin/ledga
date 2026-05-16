package com.ledga.app.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.db.entity.Insight
import com.ledga.app.data.repository.InsightsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InsightsUiState(
    val insights: List<Insight> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val insightsRepository: InsightsRepository,
) : ViewModel() {

    init {
        // Generate on screen entry so first-time users see something immediately
        // even before the daily WorkManager job has run.
        viewModelScope.launch { insightsRepository.generateAll() }
    }

    val uiState: StateFlow<InsightsUiState> = insightsRepository
        .observeActive()
        .map { InsightsUiState(insights = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InsightsUiState())

    fun dismiss(id: Long) {
        viewModelScope.launch { insightsRepository.dismiss(id) }
    }

    fun snooze(id: Long) {
        viewModelScope.launch { insightsRepository.snooze(id, days = 30) }
    }
}
