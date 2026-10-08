package com.ledga.app.ui.update

import com.ledga.app.data.update.CheckFailure
import com.ledga.app.data.update.GitHubJson
import com.ledga.app.data.update.UpdateState
import com.ledga.app.testing.ghList
import com.ledga.app.testing.ghRelease
import com.ledga.app.work.DownloadProgress
import com.ledga.core.update.AppVersion
import com.ledga.core.update.NotesSection
import com.ledga.core.update.UpdateChannel
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** Spec §13.4, R135, R136: what the Updates screen shows for each state. Synthetic releases. */
class UpdatesUiTest {
    private val today = LocalDate.parse("2026-10-07")
    private val beta2 = GitHubJson.releases(ghList(ghRelease("v2.0.0-beta.2"))).single()
    private val base = UpdateState(installed = AppVersion.parse("2.0.0-beta.1")!!, channel = UpdateChannel.BETA)

    private fun ui(s: UpdateState, canInstall: Boolean = true) = UpdatesUi.of(s, today, canInstall)

    @Test
    fun `nothing newer is up to date, with no notes`() {
        val ui = ui(base.copy(failure = CheckFailure.OFFLINE))
        assertEquals(UpdateStatus.UpToDate, ui.status)
        assertTrue(ui.notes.isEmpty())
        assertEquals(UpdateText.failureLine(CheckFailure.OFFLINE), ui.failureLine)
    }

    @Test
    fun `a newer release is available, with its size, notes and page`() {
        val ui = ui(base.copy(newest = beta2))
        assertEquals(UpdateStatus.Available("2.0.0-beta.2", "9.0 MB", skipped = false), ui.status)
        assertEquals(listOf(NotesSection("What's new", listOf("Something new in 2.0.0-beta.2"))), ui.notes)
        assertEquals("https://example.test/releases/v2.0.0-beta.2", ui.pageUrl)
    }

    @Test
    fun `a skipped release is still shown, marked skipped (R135)`() {
        assertEquals(UpdateStatus.Available("2.0.0-beta.2", "9.0 MB", skipped = true), ui(base.copy(newest = beta2, skipped = true)).status)
    }

    @Test
    fun `a running download shows its share, a downloaded one is ready`() {
        assertEquals(
            UpdateStatus.Downloading("2.0.0-beta.2", 0.25f),
            ui(base.copy(newest = beta2, download = DownloadProgress.Running("2.0.0-beta.2", 25, 100, user = false))).status,
        )
        assertEquals(UpdateStatus.Ready("2.0.0-beta.2"), ui(base.copy(newest = beta2, ready = true)).status)
    }

    @Test
    fun `a person's failed download says why, a quiet one's offers the download again (R136)`() {
        val failed = DownloadProgress.Failed("2.0.0-beta.2", "The download stopped. Check your connection and try again.", user = true)
        assertEquals(UpdateStatus.Failed("2.0.0-beta.2", failed.message), ui(base.copy(newest = beta2, download = failed)).status)
        assertEquals(
            UpdateStatus.Available("2.0.0-beta.2", "9.0 MB", skipped = false),
            ui(base.copy(newest = beta2, download = failed.copy(user = false))).status,
        )
    }

    @Test
    fun `the beta switch follows the channel, and Ledga dev says it doesn't install`() {
        assertTrue(ui(base).beta)
        val dev = ui(base.copy(offersUpdates = false))
        assertEquals(false, dev.offersUpdates)
        assertNull(dev.pageUrl)
    }
}
