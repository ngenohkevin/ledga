package com.ledga.app.update

import com.ledga.app.ui.update.parseChangelog
import com.ledga.app.worker.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateLogicTest {

    // ---- Version comparison ----

    @Test
    fun `isNewerVersion catches simple bumps`() {
        assertTrue(UpdateChecker.isNewerVersion(latest = "1.1.0", current = "1.0.0"))
        assertTrue(UpdateChecker.isNewerVersion(latest = "1.0.1", current = "1.0.0"))
        assertTrue(UpdateChecker.isNewerVersion(latest = "2.0.0", current = "1.9.9"))
    }

    @Test
    fun `isNewerVersion handles double-digit segments correctly`() {
        // A naive lexicographic compare would say "1.10" < "1.9". We compare numerically.
        assertTrue(UpdateChecker.isNewerVersion(latest = "1.10", current = "1.9"))
        assertTrue(UpdateChecker.isNewerVersion(latest = "1.10.0", current = "1.9.5"))
    }

    @Test
    fun `isNewerVersion treats missing segments as zero`() {
        // 1.1 is the same as 1.1.0.
        assertFalse(UpdateChecker.isNewerVersion(latest = "1.1", current = "1.1.0"))
        assertFalse(UpdateChecker.isNewerVersion(latest = "1.1.0", current = "1.1"))
        // But 1.1.1 should beat 1.1.
        assertTrue(UpdateChecker.isNewerVersion(latest = "1.1.1", current = "1.1"))
    }

    @Test
    fun `isNewerVersion returns false on same or older`() {
        assertFalse(UpdateChecker.isNewerVersion(latest = "1.0.0", current = "1.0.0"))
        assertFalse(UpdateChecker.isNewerVersion(latest = "1.0.0", current = "1.1.0"))
        assertFalse(UpdateChecker.isNewerVersion(latest = "0.9.9", current = "1.0.0"))
    }

    // ---- Changelog parsing ----

    @Test
    fun `parseChangelog groups whats-new and fixes`() {
        val body = """
            ## What's new
            - Insights tab
            - Goals
            - Dual-SIM support

            ## Fixes
            - Faster startup on Android 8
            - Better Fuliza parsing
        """.trimIndent()
        val parsed = parseChangelog(body)
        assertEquals(3, parsed.whatsNew.size)
        assertTrue(parsed.whatsNew.contains("Insights tab"))
        assertEquals(2, parsed.fixes.size)
        assertTrue(parsed.fixes.contains("Faster startup on Android 8"))
        assertTrue(parsed.other.isEmpty())
    }

    @Test
    fun `parseChangelog strips bullet markers`() {
        val body = """
            ## What's new
            * One
            - Two
        """.trimIndent()
        val parsed = parseChangelog(body)
        assertEquals(listOf("One", "Two"), parsed.whatsNew)
    }

    @Test
    fun `parseChangelog falls through to other for non-bulleted lines`() {
        val body = """
            Release notes attached.
            See the GitHub page for the full story.
        """.trimIndent()
        val parsed = parseChangelog(body)
        assertTrue(parsed.whatsNew.isEmpty())
        assertTrue(parsed.fixes.isEmpty())
        assertEquals(2, parsed.other.size)
    }

    @Test
    fun `parseChangelog returns empty on blank input`() {
        val parsed = parseChangelog("")
        assertTrue(parsed.whatsNew.isEmpty())
        assertTrue(parsed.fixes.isEmpty())
        assertTrue(parsed.other.isEmpty())
    }
}
