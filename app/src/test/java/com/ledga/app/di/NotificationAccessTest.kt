package com.ledga.app.di

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** R109: "notifications allowed" is Android's own switch, not only Android 13's permission. */
@RunWith(RobolectricTestRunner::class)
class NotificationAccessTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(NotificationManager::class.java)

    @Test
    fun `notifications switched off in Android read as off, with no dialog to ask, and Android 13's refusal too`() {
        val access = AppModule.notificationAccess(app)
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(access.enabled())
        shadowOf(manager).setNotificationsEnabled(false)
        assertFalse(access.enabled())
        assertFalse(access.shouldAsk(), "nothing for Android's dialog to ask: Turn on opens the settings")
        shadowOf(manager).setNotificationsEnabled(true)
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(access.shouldAsk())
        assertFalse(access.enabled())
    }
}
