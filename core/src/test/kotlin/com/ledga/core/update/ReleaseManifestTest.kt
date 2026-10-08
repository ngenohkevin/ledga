package com.ledga.core.update

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir

/** Spec §13.3–13.4: `ledga-release.json` must describe the release it came with. Synthetic values. */
class ReleaseManifestTest {
    @TempDir lateinit var dir: File

    private val version = AppVersion.parse("2.0.1-beta.2")!!
    private val apk = "ledga-2.0.1-beta.2.apk"
    private val good = ReleaseManifest("2.0.1-beta.2", 2_000_102, apk, "a".repeat(64), 26, "beta")

    @Test
    fun `a manifest that describes its release passes`() {
        assertNull(ManifestCheck.check(good, version, apk, sdk = 35))
    }

    @Test
    fun `any field that doesn't match the release is a mismatch`() {
        val wrong = listOf(
            good.copy(version = "2.0.1-beta.1"),
            good.copy(versionCode = 2_000_101),
            good.copy(apk = "ledga-2.0.1-beta.1.apk"),
            good.copy(sha256 = "abc"),
            good.copy(sha256 = "A".repeat(64)),
            good.copy(channel = "stable"),
        )
        for (m in wrong) assertEquals(ManifestProblem.Mismatch, ManifestCheck.check(m, version, apk, sdk = 35), m.toString())
        assertEquals(ManifestProblem.Mismatch, ManifestCheck.check(good, version, "ledga-other.apk", sdk = 35))
    }

    @Test
    fun `an update that needs a newer Android says which`() {
        assertEquals(ManifestProblem.NeedsAndroid(36), ManifestCheck.check(good.copy(minSdk = 36), version, apk, sdk = 35))
    }

    @Test
    fun `the channel is beta for a beta and stable for a full release`() {
        assertEquals("beta", ManifestCheck.channelOf(version))
        assertEquals("stable", ManifestCheck.channelOf(AppVersion.parse("2.0.1")!!))
    }

    @Test
    fun `SHA-256 is lower-case hex of the file's bytes`() {
        val file = File(dir, "abc.bin").apply { writeText("abc") }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.of(file))
    }
}
