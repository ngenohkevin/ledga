package com.ledga.app.ui.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.db.entity.TransactionWithCategory
import com.ledga.app.data.repository.GoalWithProgress
import com.ledga.app.data.repository.GoalsRepository
import com.ledga.app.ui.navigation.GoalDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.navigation.toRoute
import javax.inject.Inject

data class GoalDetailUiState(
    val goal: GoalWithProgress? = null,
    val contributions: List<TransactionWithCategory> = emptyList(),
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GoalDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val goalsRepository: GoalsRepository,
) : ViewModel() {

    private val goalId: Long = savedStateHandle.toRoute<GoalDetailRoute>().goalId

    val uiState: StateFlow<GoalDetailUiState> = goalsRepository.observeProgress(goalId)
        .flatMapLatest { withProgress ->
            if (withProgress == null) flowOf(GoalDetailUiState(isLoading = false))
            else goalsRepository.observeContributingTransactions(withProgress.goal)
                .map { contributions ->
                    GoalDetailUiState(
                        goal = withProgress,
                        contributions = contributions,
                        isLoading = false,
                    )
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GoalDetailUiState())

    fun markComplete() {
        viewModelScope.launch { goalsRepository.markComplete(goalId) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val g = goalsRepository.findById(goalId) ?: return@launch
            goalsRepository.delete(g)
            onDeleted()
        }
    }
}
