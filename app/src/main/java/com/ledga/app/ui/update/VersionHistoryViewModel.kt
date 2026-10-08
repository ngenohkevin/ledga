package com.ledga.app.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.update.UpdateService
import com.ledga.app.data.update.UpdateState
import com.ledga.app.ui.design.format.DateLabels
import com.ledga.core.update.NotesSection
import com.ledga.core.update.ReleaseNotes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One release in Version history: "2.0.0-beta.2", "4 Oct 2026 · Beta". [tag] keys its open notes. */
data class HistoryRow(
    val tag: String,
    val title: String,
    val line: String,
    val installed: Boolean,
    val beta: Boolean,
    val notes: List<NotesSection>,
)

/** Version history (R142). */
data class VersionHistoryUi(
    val loaded: Boolean = false,
    val rows: List<HistoryRow> = emptyList(),
    val checking: Boolean = false,
    /** Why the list couldn't be fetched, for the empty state. */
    val failureLine: String? = null,
    /** The tags whose notes are open. */
    val expanded: Set<String> = emptySet(),
) {
    companion object {
        fun of(s: UpdateState, expanded: Set<String>): VersionHistoryUi = VersionHistoryUi(
            loaded = true,
            rows = s.history.mapNotNull { r ->
                val version = r.version ?: return@mapNotNull null
                val day = r.publishedAt?.let { DateLabels.date(DateLabels.nairobiDate(it)) }
                HistoryRow(
                    tag = r.tag,
                    title = version.toString(),
                    line = listOfNotNull(day, "Beta".takeIf { r.isBeta }).joinToString(" · "),
                    installed = version == s.installed,
                    beta = r.isBeta,
                    notes = ReleaseNotes.parse(r.notes),
                )
            },
            checking = s.checking,
            failureLine = s.failure?.let(UpdateText::failureLine),
            expanded = expanded,
        )
    }
}

/** You → About → Version history (spec §13.4, R142). */
@HiltViewModel
class VersionHistoryViewModel @Inject constructor(private val updates: UpdateService) : ViewModel() {
    private val expanded = MutableStateFlow<Set<String>>(emptySet())

    val ui: StateFlow<VersionHistoryUi> = combine(updates.state, expanded) { s, open -> VersionHistoryUi.of(s, open) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VersionHistoryUi())

    init {
        // R142: opening it fetches the list when a check is due.
        viewModelScope.launch { updates.check(force = false) }
    }

    fun toggle(tag: String) = expanded.update { if (tag in it) it - tag else it + tag }

    fun retry(): Job = viewModelScope.launch { updates.check(force = true) }
}
