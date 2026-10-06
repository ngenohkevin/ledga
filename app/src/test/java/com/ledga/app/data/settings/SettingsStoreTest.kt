package com.ledga.app.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.ui.design.theme.Appearance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `an upgraded phone reads its v1 settings through the v2 store`() = runTest {
        val file = File(tmp.root, "${SettingsStore.FILE_NAME}.preferences_pb")
        val v1Scope = CoroutineScope(Dispatchers.IO + Job())
        PreferenceDataStoreFactory.create(scope = v1Scope) { file }.edit {
            it[stringPreferencesKey("theme_mode")] = "DARK"
            it[booleanPreferencesKey("onboarding_completed")] = true
        }
        v1Scope.coroutineContext.job.cancelAndJoin() // one DataStore per file at a time

        val v2Scope = CoroutineScope(Dispatchers.IO + Job())
        val store = SettingsStore(PreferenceDataStoreFactory.create(migrations = listOf(V1SettingsMigration), scope = v2Scope) { file })
        val s = store.current()
        assertEquals(Appearance.DARK, s.appearance)
        assertTrue(s.onboarded)
        v2Scope.coroutineContext.job.cancelAndJoin()
    }

    @Test
    fun `setters round-trip and the watermark never moves backwards`() = runTest {
        val store = SettingsStore(FakePrefsStore())
        store.setDisplayName("  Jane ")
        store.setSelectedLine(3L)
        store.setTextSize(TextSize.EXTRA_LARGE)
        store.advanceWatermark(200L)
        store.advanceWatermark(100L)
        store.setOnboarded()
        val s = store.current()
        assertEquals("Jane", s.displayName)
        assertEquals(3L, s.selectedLineId)
        assertEquals(TextSize.EXTRA_LARGE, s.textSize)
        assertEquals(200L, s.smsWatermarkMillis)
        assertTrue(s.onboarded)
        store.setDisplayName(" ")
        store.setSelectedLine(null)
        assertNull(store.current().displayName)
        assertNull(store.current().selectedLineId)
    }
}
