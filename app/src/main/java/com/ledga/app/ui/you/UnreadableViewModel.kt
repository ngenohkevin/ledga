package com.ledga.app.ui.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.ui.design.format.DateLabels
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class UnreadableMessage(val id: Long, val sender: String, val body: String, val receivedAt: Instant)

data class UnreadableUi(val loaded: Boolean = false, val messages: List<UnreadableMessage> = emptyList())

/** R78: what Share sends. */
object UnreadableText {
    const val NOTE = "Ledga keeps these and reads them again when it learns their shape. Sharing one sends the message exactly as it is."

    /** "…, received Mon 5 Oct 2026, 2:15 PM:" then a blank line and the message as M-Pesa sent it. */
    fun share(m: UnreadableMessage): String = "M-Pesa message Ledga couldn't read, received ${DateLabels.dateTime(m.receivedAt)}:\n\n${m.body}"
}

/** Messages Ledga couldn't read (spec §9.3, R78). */
@HiltViewModel
class UnreadableViewModel @Inject constructor(db: LedgaDatabase) : ViewModel() {
    val ui: StateFlow<UnreadableUi> = db.smsDao().observeUnreadable()
        .map { rows -> UnreadableUi(true, rows.map { UnreadableMessage(it.id, it.sender, it.body, it.receivedAt) }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UnreadableUi())
}
