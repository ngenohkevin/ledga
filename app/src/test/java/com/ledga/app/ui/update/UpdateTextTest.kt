package com.ledga.app.ui.update

import com.ledga.app.data.update.CheckFailure
import com.ledga.app.data.update.GitHubJson
import com.ledga.app.data.update.UpdateState
import com.ledga.app.testing.ghList
import com.ledga.app.testing.ghRelease
import com.ledga.app.work.DownloadProgress
import com.ledga.core.update.AppVersion
import com.ledga.core.update.UpdateChannel
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/** R142, R145, R147, R149: the update screens' words. */
class UpdateTextTest {
    private val beta1 = AppVersion.parse("2.0.0-beta.1")!!
    private val beta2 = GitHubJson.releases(ghList(ghRelease("v2.0.0-beta.2"))).single()
    private val base = UpdateState(installed = beta1, channel = UpdateChannel.BETA, checkedAt = Instant.parse("2026-10-07T06:00:00Z"))

    @Test
    fun `You's row says where the update stands (R149)`() {
        assertEquals("v2.0.0-beta.1 · up to date", UpdateText.youLine(base))
        assertEquals("v2.0.0-beta.1", UpdateText.youLine(base.copy(checkedAt = null)))
        assertEquals("v2.0.0-beta.1 · couldn't check", UpdateText.youLine(base.copy(failure = CheckFailure.OFFLINE)))
        assertEquals("2.0.0-beta.2 available", UpdateText.youLine(base.copy(newest = beta2)))
        assertEquals("v2.0.0-beta.1 · 2.0.0-beta.2 skipped", UpdateText.youLine(base.copy(newest = beta2, skipped = true)))
        assertEquals("Downloading 45%", UpdateText.youLine(base.copy(newest = beta2, download = DownloadProgress.Running("2.0.0-beta.2", 45, 100, user = true))))
        assertEquals("2.0.0-beta.2 ready to install", UpdateText.youLine(base.copy(newest = beta2, ready = true)))
        assertEquals("v2.0.0-beta.1 · updates are off in Ledga dev", UpdateText.youLine(base.copy(offersUpdates = false)))
    }

    @Test
    fun `the check's time reads like Ledga's other times`() {
        val today = LocalDate.parse("2026-10-07")
        assertEquals("Checked today, 9:00 AM", UpdateText.checkedLine(Instant.parse("2026-10-07T06:00:00Z"), today))
        assertEquals("Checked yesterday, 9:00 AM", UpdateText.checkedLine(Instant.parse("2026-10-06T06:00:00Z"), today))
        assertEquals("Checked 2 Oct", UpdateText.checkedLine(Instant.parse("2026-10-02T06:00:00Z"), today))
        assertEquals("Not checked yet", UpdateText.checkedLine(null, today))
    }

    @Test
    fun `sizes read in KB and MB`() {
        assertEquals("9.0 MB", UpdateText.size(9_000_000))
        assertEquals("850 KB", UpdateText.size(850_000))
        assertNull(UpdateText.size(0))
    }

    @Test
    fun `each failure says why, and the beta switch says what it does (R145, R147)`() {
        assertEquals(3, CheckFailure.entries.map(UpdateText::failureLine).toSet().size)
        assertEquals(UpdateText.BETA_ON, UpdateText.betaLine(base))
        assertEquals("You'll stay on 2.0.0-beta.1 until the next full release.", UpdateText.betaLine(base.copy(channel = UpdateChannel.STABLE)))
        assertEquals(UpdateText.BETA_OFF, UpdateText.betaLine(base.copy(installed = AppVersion.parse("2.0.0")!!, channel = UpdateChannel.STABLE)))
    }
}
