package com.ledga.app.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.ledga.app.testing.ghList
import com.ledga.app.testing.ghRelease
import com.ledga.app.data.update.GitHubJson
import com.ledga.core.update.AppVersion
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R134, R136: the update jobs WorkManager is given. Nothing runs here: only what is queued, and how. */
@RunWith(RobolectricTestRunner::class)
class UpdateWorkTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var wm: WorkManager
    private lateinit var work: WorkManagerUpdateWork
    private val release = GitHubJson.releases(ghList(ghRelease("v2.0.1"))).single()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        wm = WorkManager.getInstance(context)
        work = WorkManagerUpdateWork(wm)
    }

    private suspend fun downloads(): List<WorkInfo> = wm.getWorkInfosForUniqueWorkFlow(DownloadWorker.UNIQUE_NAME).first()

    @Test
    fun `a quiet download waits for an unmetered network, and a second request keeps it`() = runTest {
        work.download(release, user = false)
        work.download(release, user = false)
        val info = downloads().single()
        assertTrue(DownloadWorker.TAG_QUIET in info.tags)
        assertEquals("2.0.1", DownloadWorker.versionOf(info.tags))
        assertEquals(NetworkType.UNMETERED, info.constraints.requiredNetworkType)
    }

    @Test
    fun `a person's download replaces a quiet one still waiting, on any network`() = runTest {
        work.download(release, user = false)
        work.download(release, user = true)
        val open = downloads().filter { !it.state.isFinished }.single()
        assertTrue(DownloadWorker.TAG_USER in open.tags)
        assertEquals(NetworkType.CONNECTED, open.constraints.requiredNetworkType)
    }

    @Test
    fun `the daily check and a start's check are each queued once`() = runTest {
        work.keepChecking()
        work.keepChecking()
        work.checkSoon()
        assertEquals(1, wm.getWorkInfosForUniqueWorkFlow(UpdateCheckWorker.DAILY_NAME).first().size)
        assertEquals(NetworkType.CONNECTED, wm.getWorkInfosForUniqueWorkFlow(UpdateCheckWorker.DAILY_NAME).first().single().constraints.requiredNetworkType)
        assertEquals(1, wm.getWorkInfosForUniqueWorkFlow(UpdateCheckWorker.UNIQUE_NAME).first().size)
    }

    private fun info(state: WorkInfo.State, user: Boolean, progress: Data = Data.EMPTY) = WorkInfo(
        UUID.randomUUID(),
        state,
        setOf(if (user) DownloadWorker.TAG_USER else DownloadWorker.TAG_QUIET, DownloadWorker.VERSION_TAG + "2.0.1"),
        progress = progress,
    )

    @Test
    fun `a quiet download still waiting isn't shown, a person's is, and a running one shows its bytes`() {
        assertEquals(DownloadProgress.Idle, DownloadProgress.of(emptyList()))
        assertEquals(DownloadProgress.Idle, DownloadProgress.of(listOf(info(WorkInfo.State.ENQUEUED, user = false))))
        assertEquals(DownloadProgress.Running("2.0.1", 0L, -1L, user = true), DownloadProgress.of(listOf(info(WorkInfo.State.ENQUEUED, user = true))))
        val running = info(WorkInfo.State.RUNNING, user = false, DownloadWorker.progress(AppVersion.parse("2.0.1")!!, 40L, 100L, user = false))
        assertEquals(DownloadProgress.Running("2.0.1", 40L, 100L, user = false), DownloadProgress.of(listOf(running)))
    }
}
