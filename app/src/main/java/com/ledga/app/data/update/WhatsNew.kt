package com.ledga.app.data.update

import com.ledga.core.update.AppVersion
import com.ledga.core.update.NotesSection
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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

    /** The notes until they are seen; null once seen, or for a build with none. */
    val pending: Flow<List<NotesSection>?> = store.prefs
        .map { it.seenVersion }
        .distinctUntilChanged()
        .map { seen -> if (seen == version) null else notes.read().takeIf { it.isNotEmpty() } }
        .flowOn(Dispatchers.IO)

    /** R155: on a full disk the notes aren't filed as seen (they show again next time) rather than crash. */
    suspend fun seen() {
        try {
            store.setSeen(version)
        } catch (e: IOException) {
            // nothing was saved
        }
    }
}
