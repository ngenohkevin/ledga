package com.ledga.app.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.alerts.AlertType
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.time.LiveClock
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row of Alerts (R71). [code] is set only while its payment exists and isn't hidden. */
data class AlertUi(
    val key: String,
    val type: AlertType,
    val title: String,
    val body: String,
    val at: Instant,
    val isNew: Boolean,
    val code: String?,
)

data class AlertsUi(val loaded: Boolean = false, val alerts: List<AlertUi> = emptyList(), val today: LocalDate? = null)

/**
 * Alerts (R71). Opening it marks every alert read, so Home's badge clears at once; the ones that were unread stay "New"
 * for this visit. One that arrives during the visit is new too, and stays unread, so the bell still counts it.
 */
@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val db: LedgaDatabase,
    private val live: LiveClock,
    private val edits: TransactionEdits,
) : ViewModel() {
    /** What was unread when Alerts opened. */
    private val opened = MutableStateFlow<Set<String>?>(null)

    init {
        viewModelScope.launch {
            val unread = db.alertsDao().unreadKeys()
            opened.value = unread.toSet()
            val now = live.now()
            unread.chunked(Deriver.CHUNK).forEach { db.alertsDao().markRead(it, now) }
        }
    }

    val ui: StateFlow<AlertsUi> = combine(db.alertsDao().observeWithTx(), opened.filterNotNull(), live.today) { rows, fresh, today ->
        AlertsUi(
            loaded = true,
            alerts = rows.map { r ->
                val a = r.alert
                AlertUi(a.key, AlertType.of(a.type), a.title, a.body, a.createdAt, isNew = a.key in fresh || a.readAt == null, code = r.txCode)
            },
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertsUi())

    /** The snackbar's Undo after Hide (spec §10.4). */
    fun undoHide(code: String): Job = viewModelScope.launch { edits.setHidden(code, false) }
}
