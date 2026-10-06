package com.ledga.app.ui.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.lines.PhoneAccess
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.LineRow
import com.ledga.app.ui.tx.TxText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LineUi(val line: LineRow, val label: String, val payments: Int)

data class LinesUi(
    val loaded: Boolean = false,
    val lines: List<LineUi> = emptyList(),
    /** Payments Ledga couldn't put on a line (Phase 5 adds bulk assignment). */
    val unattributed: Int = 0,
    val phoneAccess: Boolean = true,
    /** Android won't ask again: "Allow" opens Ledga's settings page (4a M3). */
    val toSettings: Boolean = false,
)

/** You → M-Pesa lines (R65): rename a line; offer phone access when it was refused (R33). */
@HiltViewModel
class LinesViewModel @Inject constructor(private val lines: LinesRepository, db: LedgaDatabase, private val phone: PhoneAccess) : ViewModel() {
    private val granted = MutableStateFlow(phone.granted())
    private val blocked = MutableStateFlow(false)

    val ui: StateFlow<LinesUi> = combine(lines.observe(), db.transactionsDao().observeCountsByLine(), granted, blocked) { ls, counts, g, b ->
        val byLine = counts.associate { it.lineId to it.count }
        LinesUi(true, ls.map { LineUi(it, TxText.lineLabel(it), byLine[it.id] ?: 0) }, byLine[null] ?: 0, g, b)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LinesUi())

    /** [onResult] is false for a blank name (nothing saved). */
    fun rename(id: Long, name: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(lines.rename(id, name)) }
    }

    /** On resume and after the permission dialog: phone access that has just arrived re-reads the SIMs (numbers, moved SIMs). */
    fun refresh() {
        val now = phone.granted()
        if (now && !granted.value) viewModelScope.launch { lines.syncActive() }
        granted.value = now
        if (now) blocked.value = false
    }

    fun onPhoneResult(granted: Boolean, showRationale: Boolean) {
        refresh()
        if (!granted) blocked.value = !showRationale
    }
}
