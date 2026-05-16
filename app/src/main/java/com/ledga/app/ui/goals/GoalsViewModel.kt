package com.ledga.app.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.db.entity.ContributionRule
import com.ledga.app.data.db.entity.Goal
import com.ledga.app.data.repository.GoalsRepository
import com.ledga.app.data.repository.GoalWithProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GoalsUiState(
    val goals: List<GoalWithProgress> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goalsRepository: GoalsRepository,
) : ViewModel() {

    val uiState: StateFlow<GoalsUiState> = goalsRepository
        .observeGoalsWithProgress()
        .let { flow ->
            kotlinx.coroutines.flow.flow {
                emit(GoalsUiState(isLoading = true))
                flow.collect { emit(GoalsUiState(goals = it, isLoading = false)) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GoalsUiState())

    fun create(
        name: String,
        targetAmount: Double,
        targetDate: Long?,
        rule: ContributionRule,
        colorHex: String,
    ) {
        viewModelScope.launch {
            goalsRepository.create(name, targetAmount, targetDate, rule, colorHex)
        }
    }

    fun markComplete(goalId: Long) {
        viewModelScope.launch { goalsRepository.markComplete(goalId) }
    }

    fun delete(goal: Goal) {
        viewModelScope.launch { goalsRepository.delete(goal) }
    }
}
