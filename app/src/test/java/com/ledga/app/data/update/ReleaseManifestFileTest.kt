package com.ledga.app.data.update

import com.ledga.core.update.AppVersion
import com.ledga.core.update.ManifestCheck
import com.ledga.core.update.Sha256
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * R146: the manifest CI publishes, read back with the app's own parser and checks. It reads `app/build/ledga-release/`,
 * which `scripts/release_files.py` writes. It is skipped when that folder doesn't exist, except in the release workflow,
 * which passes `-Pledga.releaseFiles=required`.
 */
class ReleaseManifestFileTest {
    private val dir = File("build/ledga-release")
    private val manifestFile = File(dir, "ledga-release.json")

    @Test
    fun `the published manifest describes the APK beside it, and the app accepts it`() {
        if (System.getProperty("ledga.releaseFiles") == "required") {
            assertTrue(manifestFile.isFile, "the release workflow wrote no ${manifestFile.path}")
        } else {
            assumeTrue("no release files here: scripts/release_files.py writes them", manifestFile.isFile)
        }
        val manifest = GitHubJson.manifest(manifestFile.readText())
        val version = assertNotNull(AppVersion.parse(manifest.version))
        val apk = File(dir, manifest.apk)
        assertTrue(apk.isFile, "the APK the manifest names is beside it")
        assertEquals("ledga-$version.apk", apk.name)
        assertNull(ManifestCheck.check(manifest, version, apk.name, sdk = manifest.minSdk))
        assertEquals(manifest.sha256, Sha256.of(apk))
    }
}
