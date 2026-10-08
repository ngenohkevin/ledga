package com.ledga.app.ui.home

import com.ledga.app.data.update.GitHubJson
import com.ledga.app.data.update.UpdateState
import com.ledga.app.testing.ghList
import com.ledga.app.testing.ghRelease
import com.ledga.app.work.DownloadProgress
import com.ledga.core.update.AppVersion
import com.ledga.core.update.UpdateChannel
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/** Spec §10.4 item 6, R135, R136, R148: which update banner Home shows. Synthetic releases. */
class HomeUpdateTest {
    private val beta2 = GitHubJson.releases(ghList(ghRelease("v2.0.0-beta.2"))).single()
    private val base = UpdateState(installed = AppVersion.parse("2.0.0-beta.1")!!, channel = UpdateChannel.BETA, newest = beta2, offered = true)

    @Test
    fun `an offered update is available, and ready once downloaded`() {
        assertEquals(HomeUpdate.Available("2.0.0-beta.2"), HomeUpdate.of(base))
        assertEquals(HomeUpdate.Ready("2.0.0-beta.2"), HomeUpdate.of(base.copy(ready = true)))
    }

    @Test
    fun `a running download shows its progress`() {
        assertEquals(
            HomeUpdate.Downloading("2.0.0-beta.2", 0.5f),
            HomeUpdate.of(base.copy(download = DownloadProgress.Running("2.0.0-beta.2", 50, 100, user = false))),
        )
    }

    @Test
    fun `a skipped or snoozed update, or none, shows nothing (R135)`() {
        assertNull(HomeUpdate.of(base.copy(offered = false)))
        assertNull(HomeUpdate.of(base.copy(offered = false, ready = true)))
        assertNull(HomeUpdate.of(base.copy(newest = null, offered = false)))
    }
}
