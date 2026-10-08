package com.ledga.app.data.update

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.ledga.app.notify.NotificationIntents
import com.ledga.app.notify.NotifyChannels
import com.ledga.app.notify.UpdateIntents
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Spec §11 "Updates", R144: the download's notifications, on their own quiet channel, opening Updates. */
@RunWith(RobolectricTestRunner::class)
class AndroidUpdateNoticesTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val notices = AndroidUpdateNotices(app)

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotifyChannels.ensure(app)
    }

    @Test
    fun `the Updates channel is quiet`() {
        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(NotifyChannels.UPDATES).importance)
    }

    @Test
    fun `progress is an ongoing bar, cleared when the download ends`() {
        notices.progress("2.0.1", 45)
        val n = shadowOf(manager).getNotification(AndroidUpdateNotices.PROGRESS_ID)
        assertEquals(NotifyChannels.UPDATES, n.channelId)
        assertEquals("Downloading Ledga 2.0.1", n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertEquals(45, n.extras.getInt(Notification.EXTRA_PROGRESS))
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        notices.clearProgress()
        assertNull(shadowOf(manager).getNotification(AndroidUpdateNotices.PROGRESS_ID))
    }

    @Test
    fun `a ready update says so, and its tap opens Updates, not an alert`() {
        notices.ready("2.0.1")
        val n = shadowOf(manager).getNotification(AndroidUpdateNotices.READY_ID)
        assertEquals("Update ready to install", n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertEquals("Ledga 2.0.1 has downloaded.", n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        val tap = shadowOf(n.contentIntent).savedIntent
        assertTrue(UpdateIntents.read(tap))
        assertNull(NotificationIntents.read(tap))
        UpdateIntents.clear(tap)
        assertTrue(!UpdateIntents.read(tap))
    }

    @Test
    fun `without the permission nothing is posted`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notices.ready("2.0.1")
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
}
