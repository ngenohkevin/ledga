package com.ledga.app.ui.lines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.lines.LinePlacements
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.room.LineRow
import com.ledga.app.time.LiveClock
import com.ledga.app.ui.tx.TxText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One line's part of the balance proposal: "Personal ··11 · 812 payments". */
data class PlacedShare(val label: String, val count: Int)

data class UnassignedUi(
    val loaded: Boolean = false,
    val unassigned: Int = 0,
    val lines: List<LineRow> = emptyList(),
    /** The balance proposal is being worked out. */
    val counting: Boolean = true,
    val shares: List<PlacedShare> = emptyList(),
    val placeable: Int = 0,
    val left: Int = 0,
    val rangeLine: Long? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val inRange: Int? = null,
    val today: LocalDate? = null,
    /** A placement is being written: the buttons wait. */
    val busy: Boolean = false,
)

/** What a placement moved, for its snackbar and Undo. */
data class Moved(val count: Int, val codes: List<String>, val byBalance: Boolean, val lineLabel: String? = null)

/** You → M-Pesa lines → Not on a line (R116, R128, R129). */
@HiltViewModel
class UnassignedViewModel @Inject constructor(
    private val placements: LinePlacements,
    private val edits: TransactionEdits,
    lines: LinesRepository,
    liveClock: LiveClock,
) : ViewModel() {
    private val state = MutableStateFlow(UnassignedUi())
    val ui: StateFlow<UnassignedUi> = state.asStateFlow()

    /** The proposal's own map: code → line. */
    private var placed: Map<String, Long> = emptyMap()

    init {
        viewModelScope.launch {
            combine(placements.observeUnassigned().distinctUntilChanged(), lines.observe(), liveClock.today) { n, ls, today -> Triple(n, ls, today) }
                .collect { (n, ls, today) ->
                    state.update { s ->
                        val first = ls.firstOrNull()?.id
                        s.copy(
                            loaded = true,
                            unassigned = n,
                            lines = ls,
                            today = today,
                            rangeLine = s.rangeLine?.takeIf { id -> ls.any { it.id == id } } ?: first,
                            from = s.from ?: today.withDayOfMonth(1),
                            to = s.to ?: today,
                        )
                    }
                    propose()
                    countRange()
                }
        }
    }

    private suspend fun propose() {
        state.update { it.copy(counting = true) }
        val p = placements.propose()
        placed = p.placed
        val labels = state.value.lines.associate { it.id to TxText.lineLabel(it) }
        state.update { s ->
            s.copy(
                counting = false,
                shares = s.lines.mapNotNull { l -> p.byLine[l.id]?.let { PlacedShare(labels.getValue(l.id), it) } },
                placeable = p.placed.size,
                left = p.left,
            )
        }
    }

    private suspend fun countRange() {
        val s = state.value
        val from = s.from ?: return
        val to = s.to ?: return
        val n = placements.inDates(from, to).size
        state.update { it.copy(inRange = n) }
    }

    /** R116: every payment the balances place, in one go. */
    fun placeByBalance(onDone: (Moved) -> Unit) = write(onDone) {
        val codes = edits.placeOnLines(placed)
        Moved(codes.size, codes, byBalance = true)
    }

    fun chooseLine(id: Long) = state.update { it.copy(rangeLine = id) }

    fun setFrom(day: LocalDate) {
        state.update { it.copy(from = day, inRange = null) }
        viewModelScope.launch { countRange() }
    }

    fun setTo(day: LocalDate) {
        state.update { it.copy(to = day, inRange = null) }
        viewModelScope.launch { countRange() }
    }

    /** R128: the range's payments not on a line, to the chosen line. */
    fun moveRange(onDone: (Moved) -> Unit) {
        val s = state.value
        val line = s.lines.firstOrNull { it.id == s.rangeLine } ?: return
        val from = s.from ?: return
        val to = s.to ?: return
        write(onDone) {
            val codes = edits.placeOnLines(placements.inDates(from, to).associateWith { line.id })
            Moved(codes.size, codes, byBalance = false, lineLabel = line.displayName)
        }
    }

    fun undo(moved: Moved) {
        viewModelScope.launch {
            edits.unplace(moved.codes)
            propose()
            countRange()
        }
    }

    private fun write(onDone: (Moved) -> Unit, block: suspend () -> Moved) {
        if (state.value.busy) return
        state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val moved = block()
                propose()
                countRange()
                onDone(moved)
            } finally {
                state.update { it.copy(busy = false) }
            }
        }
    }
}
