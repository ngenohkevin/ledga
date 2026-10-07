package com.ledga.app.ui.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.TxRow
import com.ledga.app.data.room.toDerived
import com.ledga.app.time.LiveClock
import com.ledga.app.ui.design.format.AmountFormat
import com.ledga.app.ui.tx.TxText
import com.ledga.app.work.BackgroundWork
import com.ledga.app.work.ImportProgress
import com.ledga.core.derive.BalanceChain
import com.ledga.core.money.Decimals
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One place a balance doesn't follow (spec §15.1): the payment, and what was expected against what M-Pesa said. */
data class ChainBreakUi(val tx: TxRow, val expectedCents: Long, val statedCents: Long)

data class LineCheckUi(val label: String, val checked: Int, val breaks: Int)

data class HistoryCheckUi(
    val loaded: Boolean = false,
    /** Balances compared with the one before them. */
    val checked: Int = 0,
    /** Newest first. */
    val breaks: List<ChainBreakUi> = emptyList(),
    /** Each line's result, with two or more lines (or payments not on a line beside one). */
    val lines: List<LineCheckUi> = emptyList(),
    val categories: Map<String, CategoryRow> = emptyMap(),
    val today: LocalDate? = null,
)

object HistoryText {
    /** "Expected Ksh 3,500.00 · M-Pesa said Ksh 3,000.00". */
    fun breakLine(b: ChainBreakUi): String =
        "Expected ${AmountFormat.CURRENCY} ${AmountFormat.plain(b.expectedCents, Decimals.ALWAYS)} · M-Pesa said ${AmountFormat.CURRENCY} ${AmountFormat.plain(b.statedCents, Decimals.ALWAYS)}"
}

/**
 * History check (spec §15.1, R79): `BalanceChain` over every transaction, hidden ones included (the wallet moved).
 * It runs when the screen opens, and again from [check].
 */
@HiltViewModel
class HistoryCheckViewModel @Inject constructor(
    private val db: LedgaDatabase,
    private val live: LiveClock,
    private val work: BackgroundWork,
    private val edits: TransactionEdits,
) : ViewModel() {
    private val result = MutableStateFlow<HistoryCheckUi?>(null)

    /** A Rescan tapped here, until its scan has been seen running and then finishing: the check runs again then. */
    private var rescanning = false
    private var sawRunning = false

    init {
        check()
        viewModelScope.launch {
            work.inboxImport.collect { p ->
                if (!rescanning) return@collect
                when (p) {
                    is ImportProgress.Running -> sawRunning = true
                    is ImportProgress.Done, ImportProgress.Failed -> if (sawRunning) {
                        rescanning = false
                        check()
                    }
                    ImportProgress.Idle -> Unit
                }
            }
        }
    }

    fun check() {
        viewModelScope.launch {
            val txs = db.transactionsDao().all()
            val report = withContext(Dispatchers.Default) { BalanceChain.check(txs.map { it.toDerived() }) }
            val byCode = txs.associateBy { it.code }
            val lines = db.linesDao().all().associateBy { it.id }
            result.value = HistoryCheckUi(
                loaded = true,
                checked = report.checked,
                breaks = report.breaks.sortedByDescending { it.occurredAt }
                    .mapNotNull { b -> byCode[b.code]?.let { ChainBreakUi(it, b.expected.cents, b.actual.cents) } },
                lines = if (report.lines.size < 2) emptyList() else report.lines.map { l ->
                    LineCheckUi(l.lineId?.let { lines[it] }?.let(TxText::lineLabel) ?: "Not on a line", l.checked, l.breaks.size)
                },
            )
        }
    }

    val ui: StateFlow<HistoryCheckUi> = combine(result.filterNotNull(), db.categoriesDao().observeAll(), live.today) { r, cats, today ->
        r.copy(categories = cats.associateBy { it.key }, today = today)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryCheckUi())

    /** A break is most often a message Ledga never saw: the whole inbox again (spec §9.1), then the check again. */
    fun rescan() {
        rescanning = true
        sawRunning = false
        work.importInbox()
    }

    fun undoHide(code: String): Job = viewModelScope.launch { edits.setHidden(code, false) }
}
