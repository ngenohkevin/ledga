package com.ledga.app.data.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ledga.app.BuildConfig
import com.ledga.core.update.AppVersion
import com.ledga.core.update.ReleaseNotes
import java.io.File
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec §13.1–13.2, R132, R143: the build's version comes from version.properties, and its notes ship in the APK. */
@RunWith(RobolectricTestRunner::class)
class VersioningTest {
    // Unit tests run in the app module's folder.
    private val name: String = Properties().apply { File("../version.properties").inputStream().use(::load) }.getProperty("VERSION_NAME")
    private val notesFile = File("../release-notes/$name.md")

    @Test
    fun `the build's version comes from the properties file, with Ledga dev's suffix`() {
        assertEquals("$name${AppVersion.DEV_SUFFIX}", BuildConfig.VERSION_NAME)
    }

    @Test
    fun `the versionCode follows the spec's formula`() {
        assertEquals(AppVersion.ofBuild(BuildConfig.VERSION_NAME)!!.code, BuildConfig.VERSION_CODE)
    }

    @Test
    fun `this version has release notes with a What's new section`() {
        val notes = ReleaseNotes.parse(notesFile.readText())
        assertTrue(notes.any { it.title == "What's new" && it.items.isNotEmpty() }, notes.toString())
    }

    @Test
    fun `the APK carries this version's notes for What's new`() {
        val assets = ApplicationProvider.getApplicationContext<Context>().assets
        assertEquals(ReleaseNotes.parse(notesFile.readText()), AssetNotes(assets).read())
    }
}
