package com.ledga.app.ui.tx

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.LineRow
import com.ledga.app.data.room.TxRow
import com.ledga.app.time.LiveClock
import com.ledga.core.model.FlowKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** "Treat all 6 from Example Bank as your own account?" (R37): asked when more than one payment would change. */
data class PendingOwn(val own: Boolean, val count: Int, val name: String)

/** One payment and everything the sheet shows about it (spec §10.4 "Transaction sheet"). */
data class TxSheetState(
    val tx: TxRow? = null,
    val category: CategoryRow? = null,
    val lines: List<LineRow> = emptyList(),
    val sms: List<String> = emptyList(),
    val today: LocalDate? = null,
    val pendingOwn: PendingOwn? = null,
) {
    val line: LineRow? get() = tx?.lineId?.let { id -> lines.firstOrNull { it.id == id } }

    /** Only kinds that can move money between your own accounts get the switch (spec §7.4, R37). */
    val canBeOwnAccount: Boolean get() = tx?.kind?.ownAccountFlow != null
    val isOwnAccount: Boolean get() = tx?.flow == FlowKind.OWN_OUT || tx?.flow == FlowKind.OWN_IN
    val categoryName: String get() = category?.name ?: "Other"

    /** R46: a payment moves to another line only when there is another line. */
    val canMoveLine: Boolean get() = lines.size >= 2
}

/** The transaction sheet's state and edits. One instance per screen; [open] switches it to another payment. */
@HiltViewModel
class TxSheetViewModel @Inject constructor(
    private val db: LedgaDatabase,
    private val edits: TransactionEdits,
    lines: LinesRepository,
    live: LiveClock,
) : ViewModel() {
    private val code = MutableStateFlow<String?>(null)
    private val pending = MutableStateFlow<PendingOwn?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TxSheetState> = code
        .flatMapLatest { c ->
            if (c == null) {
                flowOf(TxSheetState())
            } else {
                combine(
                    db.transactionsDao().observe(c),
                    db.categoriesDao().observeAll(),
                    lines.observe(),
                    db.smsDao().bodiesFor(c),
                    live.today,
                ) { tx, categories, ls, sms, today ->
                    TxSheetState(tx, categories.firstOrNull { it.key == tx?.categoryKey }, ls, sms, today)
                }
            }
        }
        .combine(pending) { s, p -> s.copy(pendingOwn = p) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TxSheetState())

    private var session: Long? = null

    /**
     * Opens [code] for the host's [session] (a saveable id, R62). A rotation re-runs the host's effect with the same
     * session and changes nothing, so a pending own-account question survives; a new opening starts clean.
     */
    fun open(code: String, session: Long = System.nanoTime()) {
        if (this.session == session && this.code.value == code) return
        this.session = session
        pending.value = null
        this.code.value = code
    }

    fun setNote(note: String) = edit { edits.setNote(it, note) }

    fun setHidden(hidden: Boolean) = edit { edits.setHidden(it, hidden) }

    fun moveToLine(lineId: Long) = edit { edits.setLine(it, lineId) }

    /** R37: a single payment changes at once; when more than one would, the sheet asks "all from <name>" or "only this". */
    fun setOwnAccount(own: Boolean) = edit { c ->
        val tx = db.transactionsDao().get(c) ?: return@edit
        val count = edits.ownAccountCount(c, own)
        if (count > 1 && tx.counterpartyName != null) {
            pending.value = PendingOwn(own, count, TxText.title(tx))
        } else {
            edits.setOwnAccount(c, own, allFromName = false)
        }
    }

    fun confirmOwn(allFromName: Boolean) = edit { c ->
        val p = pending.value ?: return@edit
        pending.value = null
        edits.setOwnAccount(c, p.own, allFromName)
    }

    fun cancelOwn() {
        pending.value = null
    }

    private fun edit(block: suspend (String) -> Unit) {
        val c = code.value ?: return
        viewModelScope.launch { block(c) }
    }
}
