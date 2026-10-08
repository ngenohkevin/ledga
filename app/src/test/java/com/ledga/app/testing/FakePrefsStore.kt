package com.ledga.app.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/** An in-memory DataStore for ViewModel and startup tests (no files, no scopes to cancel). */
class FakePrefsStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}

/** A disk with no room left: reads work, and every write fails the way DataStore reports it (R155). */
class FullDiskPrefsStore(private val current: Preferences = emptyPreferences()) : DataStore<Preferences> {
    override val data: Flow<Preferences> = flowOf(current)

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        throw IOException("No space left on device")
}
