package com.ledga.app.data.lines

import com.ledga.app.data.room.LineRow
import com.ledga.app.data.settings.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Which line the summaries show (R47, owner 2026-10-06): Home, Activity › Spending and People, Trackers and Tracker
 * detail all follow one choice, `Settings.selectedLineId`. It counts only on a phone with two or more lines and only
 * while that line exists. A v1 choice carried onto a one-line phone, or a line id that is gone, reads as all lines,
 * so it can never hide the payments Ledga couldn't attribute (spec §9.1).
 */
data class LineChoice(val lines: List<LineRow> = emptyList(), val selectedId: Long? = null) {
    /** The line every summary narrows to; null = all lines. */
    val lineId: Long? get() = selectedId?.takeIf { id -> lines.size >= 2 && lines.any { it.id == id } }

    val selected: LineRow? get() = lineId?.let { id -> lines.firstOrNull { it.id == id } }

    /** The line chip appears only where there is a choice to make. */
    val showChip: Boolean get() = lines.size >= 2
}

class SelectedLine(private val lines: LinesRepository, private val settings: SettingsStore) {
    val choice: Flow<LineChoice> =
        combine(lines.observe(), settings.settings.map { it.selectedLineId }) { ls, id -> LineChoice(ls, id) }.distinctUntilChanged()

    suspend fun select(lineId: Long?) = settings.setSelectedLine(lineId)
}
