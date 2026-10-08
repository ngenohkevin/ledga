package com.ledga.core.update

import java.io.File
import java.security.MessageDigest

/** `ledga-release.json` (spec §13.3): written by `scripts/release_files.py` beside each v2 APK. */
data class ReleaseManifest(
    val version: String,
    val versionCode: Int,
    val apk: String,
    val sha256: String,
    val minSdk: Int,
    val channel: String,
)

/** Why a downloaded manifest can't be used. */
sealed interface ManifestProblem {
    /** It doesn't describe the release it came with: another version, code, file or channel, or no proper checksum. */
    data object Mismatch : ManifestProblem

    /** The update needs a newer Android: API [minSdk]. */
    data class NeedsAndroid(val minSdk: Int) : ManifestProblem
}

/** Spec §13.4, R138: a manifest is trusted only when every field agrees with the release it came with. */
object ManifestCheck {
    private val HEX64 = Regex("[0-9a-f]{64}")

    fun check(manifest: ReleaseManifest, version: AppVersion, apkName: String, sdk: Int): ManifestProblem? = when {
        manifest.version != version.toString() ||
            manifest.versionCode != version.code ||
            manifest.apk != apkName ||
            !HEX64.matches(manifest.sha256) ||
            manifest.channel != channelOf(version) -> ManifestProblem.Mismatch
        manifest.minSdk > sdk -> ManifestProblem.NeedsAndroid(manifest.minSdk)
        else -> null
    }

    fun channelOf(version: AppVersion): String = if (version.isBeta) "beta" else "stable"
}

/** Lower-case hex SHA-256 of a file, read in 64 KB blocks (spec §14). */
object Sha256 {
    fun of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
