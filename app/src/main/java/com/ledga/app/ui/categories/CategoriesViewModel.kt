package com.ledga.app.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.edit.CategoryRules
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.core.model.CategoryGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row of Categories & rules: the category and how many rules file into it, switched on and off (R67). */
data class CategoryListRow(val category: CategoryRow, val rulesOn: Int, val rulesOff: Int)

data class CategoryGroupUi(val group: CategoryGroup, val rows: List<CategoryListRow>)

data class CategoriesUi(
    val loaded: Boolean = false,
    /** Every group, in the picker's order, with its categories that aren't archived. */
    val groups: List<CategoryGroupUi> = emptyList(),
    /** R72: archived categories, listed once under "Archived". */
    val archived: List<CategoryListRow> = emptyList(),
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(db: LedgaDatabase, private val edits: TransactionEdits) : ViewModel() {
    val ui: StateFlow<CategoriesUi> = combine(db.categoriesDao().observeAll(), db.rulesDao().observeAll()) { categories, rules ->
        fun row(c: CategoryRow): CategoryListRow {
            val mine = CategoryRules.forCategory(rules, c.key)
            return CategoryListRow(c, mine.count { it.enabled }, mine.count { !it.enabled })
        }
        CategoriesUi(
            loaded = true,
            groups = CategoryGroup.entries.map { g -> CategoryGroupUi(g, categories.filter { it.groupKey == g && !it.archived }.map(::row)) },
            archived = categories.filter { it.archived }.map(::row),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUi())

    /** "+ New category" (R43, R67): [onCreated] gets its key (an existing or archived one of that name comes back, R72). Blank makes nothing. */
    fun create(name: String, group: CategoryGroup, onCreated: (String) -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch { onCreated(edits.createCategory(name, group)) }
    }
}
