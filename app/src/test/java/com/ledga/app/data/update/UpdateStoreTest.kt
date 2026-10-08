package com.ledga.app.data.update

import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ledga.app.testing.FakePrefsStore
import com.ledga.core.update.AppVersion
import com.ledga.core.update.UpdateChannel
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** R133: what the update service keeps between runs. */
class UpdateStoreTest {
    private val store = UpdateStore(FakePrefsStore())
    private val at = Instant.parse("2026-10-07T06:00:00Z")

    @Test
    fun `nothing is cached at first, and no channel is chosen`() = runTest {
        assertEquals(UpdatePrefs(), store.current())
    }

    @Test
    fun `a fresh list is kept with its address and ETag, and ends a failure`() = runTest {
        store.checkFailed(CheckFailure.OFFLINE)
        store.saveReleases("[]", "https://example.test/releases", "\"e1\"", at)
        assertEquals(UpdatePrefs(releasesJson = "[]", releasesUrl = "https://example.test/releases", etag = "\"e1\"", checkedAt = at), store.current())
    }

    @Test
    fun `a failure keeps the cached list (R147)`() = runTest {
        store.saveReleases("[]", "https://example.test/releases", null, at)
        store.checkFailed(CheckFailure.RATE_LIMITED)
        val p = store.current()
        assertEquals("[]", p.releasesJson)
        assertEquals(at, p.checkedAt)
        assertEquals(CheckFailure.RATE_LIMITED, p.failure)
    }

    @Test
    fun `an unchanged list moves the check time on and keeps its ETag`() = runTest {
        store.saveReleases("[]", "https://example.test/releases", "\"e1\"", at)
        store.checkFailed(CheckFailure.OFFLINE)
        store.checkedUnchanged(at.plusSeconds(60))
        val p = store.current()
        assertEquals(at.plusSeconds(60), p.checkedAt)
        assertNull(p.failure)
        assertEquals("\"e1\"", p.etag)
    }

    @Test
    fun `channel, skip, snooze and the seen version are kept`() = runTest {
        store.setChannel(UpdateChannel.BETA)
        store.skip(AppVersion.parse("2.0.1")!!)
        store.snoozeUntil(at)
        store.setSeen("2.0.0-beta.1")
        assertEquals(
            UpdatePrefs(channel = UpdateChannel.BETA, skipped = AppVersion.parse("2.0.1"), snoozedUntil = at, seenVersion = "2.0.0-beta.1"),
            store.current(),
        )
    }

    @Test
    fun `values it can't read fall back to the defaults`() = runTest {
        val raw = FakePrefsStore(
            preferencesOf(
                stringPreferencesKey("channel") to "NIGHTLY",
                stringPreferencesKey("skipped") to "soon",
                stringPreferencesKey("failure") to "?",
            ),
        )
        assertEquals(UpdatePrefs(), UpdateStore(raw).current())
    }
}
