package com.ledga.app.data.update

import com.ledga.app.testing.FullDiskPrefsStore
import com.ledga.app.testing.FakePrefsStore
import com.ledga.core.update.AppVersion
import com.ledga.core.update.NotesSection
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** R141: a build's notes, once. Synthetic notes. */
class WhatsNewTest {
    private val store = UpdateStore(FakePrefsStore())
    private val notes = listOf(NotesSection("What's new", listOf("A new Home")))

    private fun whatsNew(bundled: List<NotesSection> = notes, version: String = "2.0.0-beta.2") =
        WhatsNew(store, { bundled }, AppVersion.parse(version)!!)

    @Test
    fun `a new version's notes wait until they are seen`() = runTest {
        val w = whatsNew()
        assertEquals(notes, w.pending.first())
        w.seen()
        assertNull(w.pending.first())
    }

    @Test
    fun `the next version's notes show again`() = runTest {
        whatsNew(version = "2.0.0-beta.1").seen()
        assertEquals(notes, whatsNew(version = "2.0.0-beta.2").pending.first())
    }

    @Test
    fun `a build without notes shows nothing`() = runTest {
        assertNull(whatsNew(bundled = emptyList()).pending.first())
    }

    @Test
    fun `it names the version without Ledga dev's suffix`() {
        assertEquals("2.0.0-beta.2", whatsNew().version)
    }

    @Test
    fun `on a full disk the notes stay pending, without a crash (R155)`() = runTest {
        val w = WhatsNew(UpdateStore(FullDiskPrefsStore()), { notes }, AppVersion.parse("2.0.0-beta.2")!!)
        w.seen()
        assertEquals(notes, w.pending.first())
    }
}
