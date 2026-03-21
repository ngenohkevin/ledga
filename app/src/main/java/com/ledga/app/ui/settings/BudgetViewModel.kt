package com.ledga.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.db.dao.BudgetDao
import com.ledga.app.data.db.entity.Budget
import com.ledga.app.data.repository.CategoryRepository
import com.ledga.app.data.repository.TransactionRepository
import com.ledga.app.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryBudgetItem(
    val categoryName: String,
    val spent: Double,
    val limit: Double
)

data class BudgetUiState(
    val overallBudget: Double? = null,
    val totalSpentThisMonth: Double = 0.0,
    val categoryBudgets: List<CategoryBudgetItem> = emptyList()
)

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetDao: BudgetDao,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val monthStart = DateUtils.getStartOfMonth()
    private val now = System.currentTimeMillis()

    val uiState: StateFlow<BudgetUiState> = combine(
        budgetDao.getActiveBudgets(),
        transactionRepository.getTotalSpending(monthStart, now),
        transactionRepository.getSpendingByCategory(monthStart, now),
        categoryRepository.getAllCategories()
    ) { budgets, totalSpent, categorySpending, categories ->
        val overallBudget = budgets.find { it.categoryId == null }?.monthlyLimit
        val catMap = categories.associateBy { it.id }
        val spendingMap = categorySpending.associate { it.categoryId to it.totalAmount }

        val categoryBudgets = budgets
            .filter { it.categoryId != null }
            .mapNotNull { budget ->
                val cat = catMap[budget.categoryId] ?: return@mapNotNull null
                CategoryBudgetItem(
                    categoryName = cat.name,
                    spent = spendingMap[budget.categoryId] ?: 0.0,
                    limit = budget.monthlyLimit
                )
            }

        BudgetUiState(
            overallBudget = overallBudget,
            totalSpentThisMonth = totalSpent,
            categoryBudgets = categoryBudgets
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetUiState())

    fun setOverallBudget(amount: Double) {
        viewModelScope.launch {
            budgetDao.insert(Budget(categoryId = null, monthlyLimit = amount, isActive = true))
        }
    }
}
