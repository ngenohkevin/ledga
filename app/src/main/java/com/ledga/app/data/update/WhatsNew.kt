package com.ledga.app.data.update

import com.ledga.core.update.AppVersion
import com.ledga.core.update.NotesSection
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * R141: this build's notes, shown once on Home by an onboarded person (a v1 upgrader sees 2.0's). Onboarding files them
 * as seen without showing them, so a fresh install never opens to a list of changes it didn't live through.
 */
class WhatsNew(private val store: UpdateStore, private val notes: BundledNotes, installed: AppVersion) {
    /** "2.0.0-beta.1": the sheet's title names it. */
    val version: String = installed.toString()

    /** Done was tapped in this run (final review I1): the sheet closes even when the disk can't keep that. */
    private val dismissed = MutableStateFlow(false)

    /** The notes until they are seen; null once seen or closed, or for a build with none. */
    val pending: Flow<List<NotesSection>?> = combine(store.prefs.map { it.seenVersion }.distinctUntilChanged(), dismissed) { seen, closed ->
        if (closed || seen == version) null else notes.read().takeIf { it.isNotEmpty() }
    }.flowOn(Dispatchers.IO)

    /** R155: on a full disk the notes close for now but aren't filed as seen, so the next start shows them again. */
    suspend fun seen() {
        dismissed.value = true
        try {
            store.setSeen(version)
        } catch (e: IOException) {
            // nothing was saved
        }
    }
}
