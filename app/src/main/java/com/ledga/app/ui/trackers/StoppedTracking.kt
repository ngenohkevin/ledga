package com.ledga.app.ui.trackers

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.ledga.app.data.edit.TransactionEdits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import javax.inject.Inject
import javax.inject.Singleton

/** A tracker just stopped from its detail (R51). */
data class StoppedTracking(val categoryKey: String, val name: String)

/**
 * Tracker detail closes when tracking stops (R51), so the screen it returns to, Home or Trackers, says so and offers
 * Undo. The newest note waits here until one of them shows it, once.
 */
@Singleton
class StoppedTrackers @Inject constructor(private val edits: TransactionEdits) {
    private val pending = MutableStateFlow<StoppedTracking?>(null)
    val latest: StateFlow<StoppedTracking?> = pending

    fun post(note: StoppedTracking) {
        pending.value = note
    }

    /** Shown: a newer note stays. */
    fun taken(note: StoppedTracking) {
        pending.compareAndSet(note, null)
    }

    suspend fun undo(note: StoppedTracking) = edits.setTracked(note.categoryKey, true)
}

/** Shows each note once, "Stopped tracking <name>", with Undo. */
@Composable
fun StoppedTrackingUndo(notes: StateFlow<StoppedTracking?>, snackbar: SnackbarHostState, onShown: (StoppedTracking) -> Unit, onUndo: (StoppedTracking) -> Unit) {
    LaunchedEffect(notes) {
        notes.filterNotNull().collect { note ->
            onShown(note)
            val result = snackbar.showSnackbar("Stopped tracking ${note.name}", actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) onUndo(note)
        }
    }
}
