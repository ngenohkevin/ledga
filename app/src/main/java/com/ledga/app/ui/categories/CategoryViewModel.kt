package com.ledga.app.ui.categories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.derive.TransactionFilter
import com.ledga.app.data.edit.CategoryLooks
import com.ledga.app.data.edit.CategoryRules
import com.ledga.app.data.edit.RemovedRule
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.ui.activity.ActivityLink
import com.ledga.app.ui.activity.ActivityLinks
import com.ledga.app.ui.rules.RuleDraft
import com.ledga.app.ui.rules.ShownPreview
import com.ledga.app.ui.trackers.TrackerText
import com.ledga.core.derive.RuleOrigin
import com.ledga.core.model.Categories
import com.ledga.core.model.CategoryGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One rule on a category's screen (R73). */
data class RuleUi(val id: Long, val label: String, val builtIn: Boolean, val enabled: Boolean)

/** A category's screen (R67). */
data class CategoryUi(
    val loaded: Boolean = false,
    val missing: Boolean = false,
    val category: CategoryRow? = null,
    val rules: List<RuleUi> = emptyList(),
    /** Its payments that show (R72's archive question). */
    val payments: Int = 0,
) {
    /** Icon, colour and archive are for the person's own categories (R68, R72, R74). */
    val own: Boolean get() = category?.origin == CategoryOrigin.USER

    /** R50: spending groups only, and not while archived. */
    val canTrack: Boolean get() = category != null && !category.archived && category.groupKey in TRACKABLE

    /** Own-account rules come from a payment's "My own account" switch (R37). */
    val canAddRule: Boolean get() = category != null && category.key != Categories.OWN_ACCOUNTS

    companion object {
        private val TRACKABLE = setOf(CategoryGroup.BILLS_UTILITIES, CategoryGroup.CAR, CategoryGroup.EVERYDAY, CategoryGroup.MONEY)
    }
}

@HiltViewModel
class CategoryViewModel @Inject constructor(
    handle: SavedStateHandle,
    db: LedgaDatabase,
    private val edits: TransactionEdits,
    private val links: ActivityLinks,
) : ViewModel() {
    /** `CategoryRoute.categoryKey`. */
    val categoryKey: String = checkNotNull(handle.get<String>("categoryKey")) { "CategoryRoute needs a categoryKey" }

    private val draft = RuleDraft(viewModelScope, edits, categoryKey)
    val preview: StateFlow<ShownPreview?> = draft.preview

    val ui: StateFlow<CategoryUi> = combine(
        db.categoriesDao().observe(categoryKey),
        db.rulesDao().observeAll(),
        db.transactionsDao().observeCountInCategory(categoryKey),
    ) { c, rules, payments ->
        if (c == null) {
            CategoryUi(loaded = true, missing = true)
        } else {
            val mine = CategoryRules.forCategory(rules, c.key).map { RuleUi(it.id, TrackerText.ruleLabel(it), it.origin == RuleOrigin.SYSTEM, it.enabled) }
            CategoryUi(loaded = true, category = c, rules = mine, payments = payments)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryUi())

    fun setRuleEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { edits.setRuleEnabled(id, enabled) }
    }

    /** R49/R73: deletes a rule of the person's own; [onDeleted] gets what Undo needs. */
    fun deleteRule(id: Long, onDeleted: (RemovedRule) -> Unit) {
        viewModelScope.launch { edits.removeRule(id)?.let(onDeleted) }
    }

    fun restoreRule(removed: RemovedRule) {
        viewModelScope.launch { edits.restoreRule(removed) }
    }

    fun previewRule(name: String, account: String) = draft.count(name, account)

    fun clearPreview() = draft.clear()

    fun addRule(name: String, account: String, onDone: () -> Unit) = draft.save(name, account, onDone)

    fun rename(name: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(edits.renameCategory(categoryKey, name)) }
    }

    fun setTracked(tracked: Boolean) {
        viewModelScope.launch { edits.setTracked(categoryKey, tracked) }
    }

    fun setIcon(icon: String) {
        viewModelScope.launch { edits.setCategoryIcon(categoryKey, icon) }
    }

    fun setColor(swatch: CategoryLooks.Swatch) {
        viewModelScope.launch { edits.setCategoryColor(categoryKey, swatch) }
    }

    fun setArchived(archived: Boolean) {
        viewModelScope.launch { edits.setArchived(categoryKey, archived) }
    }

    /** "See payments" (R61, R83): Transactions on this category, every line. */
    fun openPayments() = links.open(ActivityLink.Transactions(TransactionFilter(categoryKeys = setOf(categoryKey))))
}
