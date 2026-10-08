package com.ledga.app.data.update

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Spec §13.4, R138: handing a verified APK to Android. */
@RunWith(RobolectricTestRunner::class)
class PackageInstallerUpdatesTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val events = InstallEvents()
    private val installer = PackageInstallerUpdates(context, events)
    private val apk by lazy { tmp.newFile("ledga-2.0.1.apk").apply { writeBytes(ByteArray(4_096) { it.toByte() }) } }

    @Test
    fun `without Android's permission to install, nothing starts`() {
        shadowOf(context.packageManager).setCanRequestPackageInstalls(false)
        assertEquals(InstallStart.NEEDS_PERMISSION, installer.install(apk))
        assertTrue(context.packageManager.packageInstaller.allSessions.isEmpty())
    }

    @Test
    fun `with it, a session for this very app is committed, and an earlier failure is cleared`() {
        shadowOf(context.packageManager).setCanRequestPackageInstalls(true)
        events.failure.value = "an earlier failure"
        assertEquals(InstallStart.STARTED, installer.install(apk))
        assertEquals(context.packageName, context.packageManager.packageInstaller.allSessions.single().appPackageName)
        assertNull(events.failure.value)
    }

    @Test
    fun `the settings link opens Ledga's own Install unknown apps switch`() {
        val intent = PackageInstallerUpdates.settingsIntent(context)
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
    }
}
