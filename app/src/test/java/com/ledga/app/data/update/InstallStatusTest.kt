package com.ledga.app.data.update

import android.content.Intent
import android.content.pm.PackageInstaller
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec §13.4, R138: Android's answer to an install, in words a person understands. */
@RunWith(RobolectricTestRunner::class)
class InstallStatusTest {
    private val events = InstallEvents()
    private val confirms = mutableListOf<Intent>()
    private var damaged = 0

    private fun answer(status: Int, extra: Intent? = null) {
        val intent = Intent().putExtra(PackageInstaller.EXTRA_STATUS, status).apply { extra?.let { putExtra(Intent.EXTRA_INTENT, it) } }
        InstallStatus.handle(intent, events, confirm = { confirms += it }, damaged = { damaged++ })
    }

    @Test
    fun `when Android asks the person, its confirm screen is shown`() {
        answer(PackageInstaller.STATUS_PENDING_USER_ACTION, Intent("android.content.pm.action.CONFIRM_INSTALL"))
        assertEquals("android.content.pm.action.CONFIRM_INSTALL", confirms.single().action)
        assertTrue(confirms.single().flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertNull(events.failure.value)
    }

    @Test
    fun `success and a cancelled confirm leave nothing to explain`() {
        events.failure.value = "an earlier failure"
        answer(PackageInstaller.STATUS_SUCCESS)
        assertNull(events.failure.value)
        events.failure.value = "an earlier failure"
        answer(PackageInstaller.STATUS_FAILURE_ABORTED)
        assertNull(events.failure.value)
    }

    @Test
    fun `a signing conflict is explained, with the release page as the way round`() {
        answer(PackageInstaller.STATUS_FAILURE_CONFLICT)
        assertEquals(UpdateMessages.install(PackageInstaller.STATUS_FAILURE_CONFLICT), events.failure.value)
        assertTrue(events.failure.value!!.contains("release page"))
        assertEquals(0, damaged)
    }

    @Test
    fun `a damaged file is deleted and says to download it again`() {
        answer(PackageInstaller.STATUS_FAILURE_INVALID)
        assertEquals(1, damaged)
        assertTrue(events.failure.value!!.contains("Download it again"))
    }

    @Test
    fun `every other failure has its own words`() {
        val statuses = listOf(
            PackageInstaller.STATUS_FAILURE, PackageInstaller.STATUS_FAILURE_BLOCKED, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_STORAGE,
        )
        val messages = statuses.map { UpdateMessages.install(it) }
        assertEquals(statuses.size, messages.toSet().size)
    }
}
