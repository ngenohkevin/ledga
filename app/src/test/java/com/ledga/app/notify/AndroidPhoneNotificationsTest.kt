package com.ledga.app.notify

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** R102, R109, R110: what Android is handed. */
@RunWith(RobolectricTestRunner::class)
class AndroidPhoneNotificationsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val phone = AndroidPhoneNotifications(app)

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotifyChannels.ensure(app)
    }

    @Test
    fun `a notification shows its title and text on its channel, and its tap carries the alert`() {
        val opened = OpenedNotification("large:TJK4AB12FA", NotificationTap.Payment("TJK4AB12FA"))
        phone.post(PhoneNotification(7, NotifyChannels.LARGE, "Ksh 1,000 to KPLC Prepaid", "Large payment · Electricity", opened))
        val n = shadowOf(manager).allNotifications.single()
        assertEquals(NotifyChannels.LARGE, n.channelId)
        assertEquals("Ksh 1,000 to KPLC Prepaid", n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertEquals("Large payment · Electricity", n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        assertTrue(n.flags and Notification.FLAG_AUTO_CANCEL != 0, "a tap clears it")
        assertEquals(opened, NotificationIntents.read(shadowOf(n.contentIntent).savedIntent))
    }

    @Test
    fun `nothing may show while Android blocks Ledga, without the permission, or on a channel switched off (R109)`() {
        assertTrue(phone.allowed(NotifyChannels.FULIZA))
        shadowOf(manager).setNotificationsEnabled(false)
        assertFalse(phone.allowed(NotifyChannels.FULIZA))
        shadowOf(manager).setNotificationsEnabled(true)
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(phone.allowed(NotifyChannels.FULIZA))
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        manager.createNotificationChannel(NotificationChannel("muted", "Muted", NotificationManager.IMPORTANCE_NONE))
        assertFalse(phone.allowed("muted"))
    }

    @Test
    fun `Ledga's channels exist with v1's ids kept, and v1's budget channel is gone (R102)`() {
        manager.createNotificationChannel(NotificationChannel(NotifyChannels.V1_BUDGET, "Budget Alerts", NotificationManager.IMPORTANCE_HIGH))
        NotifyChannels.ensure(app)
        assertEquals("Summaries", manager.getNotificationChannel("spending_summaries")?.name?.toString())
        assertEquals("Large payments", manager.getNotificationChannel("large_transactions")?.name?.toString())
        assertEquals("Fuliza", manager.getNotificationChannel("fuliza")?.name?.toString())
        assertNull(manager.getNotificationChannel(NotifyChannels.V1_BUDGET))
    }
}
