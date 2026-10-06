package com.ledga.app.ui.tx

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.edit.ApplyCounts
import com.ledga.app.data.edit.ApplyTo
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.ui.design.format.NameFormat
import com.ledga.core.model.CategoryGroup
import com.ledga.core.model.FlowKind
import com.ledga.core.model.TxKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One category in the picker's grid. */
data class PickerItem(val key: String, val name: String, val icon3d: String, val tracked: Boolean)

data class PickerGroup(val group: CategoryGroup, val items: List<PickerItem>)

/** The category picker (spec §10.4, mockup `picker`): the categories that fit, by group, and "apply to all" (R36). */
data class PickerState(
    val loaded: Boolean = false,
    /** The display name "apply to all" names (R44); null when the SMS names no one. */
    val txName: String? = null,
    /** A paybill's account: "only this account number" (R35). */
    val account: String? = null,
    val groups: List<PickerGroup> = emptyList(),
    val selected: String? = null,
    val query: String = "",
    val counts: ApplyCounts = ApplyCounts(0, null),
    val applyAll: Boolean = false,
    val accountOnly: Boolean = false,
    /** The group whose "+ New category" editor is open. */
    val newCategoryIn: CategoryGroup? = null,
) {
    /** The groups and categories whose name contains the search, in order. */
    val visibleGroups: List<PickerGroup>
        get() {
            val q = query.trim()
            if (q.isEmpty()) return groups
            return groups.mapNotNull { g ->
                g.items.filter { it.name.contains(q, ignoreCase = true) }.takeIf { it.isNotEmpty() }?.let { g.copy(items = it) }
            }
        }

    /** Spec §7.4, R36: offered when a rule on this name would label this payment; on by default when it would label more. */
    val showApplyAll: Boolean get() = txName != null && counts.fromName >= 1

    /** "Only this account number" (R35): a paybill with an account, while "apply to all" is on. */
    val showAccountOnly: Boolean get() = showApplyAll && applyAll && account != null && counts.forAccount != null

    val applyCount: Int get() = if (accountOnly) counts.forAccount ?: counts.fromName else counts.fromName

    val applyTo: ApplyTo
        get() = when {
            !showApplyAll || !applyAll -> ApplyTo.THIS_ONE
            accountOnly && counts.forAccount != null -> ApplyTo.THIS_ACCOUNT
            else -> ApplyTo.ALL_FROM_NAME
        }
}

/** The picker's state and save. One instance per screen; [open] loads a payment afresh (a save may have changed it). */
@HiltViewModel
class CategoryPickerViewModel @Inject constructor(
    private val db: LedgaDatabase,
    private val edits: TransactionEdits,
    private val deriver: Deriver,
) : ViewModel() {
    private val _state = MutableStateFlow(PickerState())
    val state: StateFlow<PickerState> = _state

    private var code: String? = null
    private var applyAllTouched = false

    /** The category the payment had when the picker opened: saving it unchanged changes nothing (no rule, no override). */
    private var initial: String? = null

    fun open(code: String) {
        this.code = code
        applyAllTouched = false
        initial = null
        _state.value = PickerState()
        viewModelScope.launch {
            val tx = db.transactionsDao().get(code) ?: return@launch
            val groups = groupsFor(tx.flow)
            val selected = tx.categoryKey.takeIf { key -> groups.any { g -> g.items.any { it.key == key } } }
            initial = selected
            _state.value = PickerState(
                loaded = true,
                txName = tx.counterpartyName?.let(NameFormat::display),
                account = tx.counterpartyAccount?.takeIf { tx.kind == TxKind.PAYBILL },
                groups = groups,
                selected = selected,
            )
            selected?.let { recount(it) }
        }
    }

    fun select(key: String) {
        _state.update { it.copy(selected = key) }
        viewModelScope.launch { recount(key) }
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun setApplyAll(on: Boolean) {
        applyAllTouched = true
        _state.update { it.copy(applyAll = on) }
    }

    fun setAccountOnly(on: Boolean) = _state.update { it.copy(accountOnly = on) }

    fun startNewCategory(group: CategoryGroup) = _state.update { it.copy(newCategoryIn = group) }

    fun cancelNewCategory() = _state.update { it.copy(newCategoryIn = null) }

    /** R43: a new category in the group being added to; it is selected at once. */
    fun createCategory(name: String) {
        val group = _state.value.newCategoryIn ?: return
        val c = code ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            val key = edits.createCategory(name, group)
            val tx = db.transactionsDao().get(c) ?: return@launch
            val groups = groupsFor(tx.flow)
            _state.update { it.copy(groups = groups, newCategoryIn = null, query = "") }
            select(key)
        }
    }

    fun save(onDone: () -> Unit) {
        val c = code ?: return
        val s = _state.value
        val key = s.selected ?: return
        // Nothing changed: an unchanged Save must not pin the category or file the name's other payments.
        if (key == initial && s.applyTo == ApplyTo.THIS_ONE) {
            onDone()
            return
        }
        viewModelScope.launch {
            edits.setCategory(c, key, s.applyTo)
            onDone()
        }
    }

    private suspend fun recount(key: String) {
        val c = code ?: return
        val counts = edits.categoryCounts(c, key)
        _state.update { s ->
            // "Apply to all" defaults on only for a change (spec §7.4): the current category starts off.
            val byDefault = counts.fromName > 1 && key != initial
            if (s.selected != key) s else s.copy(counts = counts, applyAll = if (applyAllTouched) s.applyAll else byDefault)
        }
    }

    /** Only categories whose group fits the payment's flow (owner decision B1): a category never changes what counts as spending. */
    private suspend fun groupsFor(flow: FlowKind): List<PickerGroup> {
        val engine = deriver.ruleEngine()
        val fitting = db.categoriesDao().all().filter { !it.archived && engine.fits(it.key, flow) }
        return CategoryGroup.entries.mapNotNull { g ->
            fitting.filter { it.groupKey == g }.takeIf { it.isNotEmpty() }?.let { cats ->
                PickerGroup(g, cats.map { PickerItem(it.key, it.name, it.icon3d, it.tracked) })
            }
        }
    }
}
