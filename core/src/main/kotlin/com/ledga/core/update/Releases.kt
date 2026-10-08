package com.ledga.core.update

import java.time.Duration
import java.time.Instant

/** Spec §13.4: which releases a phone follows. Beta follows both kinds; stable, full releases only. */
enum class UpdateChannel { STABLE, BETA }

/** A file attached to a release. [size] is in bytes. */
data class ReleaseAsset(val name: String, val url: String, val size: Long)

/**
 * One GitHub release as Ledga reads it (spec §13.4). [version] is null for a tag that isn't a Ledga version; [pageUrl]
 * is its page on GitHub, where the APK can be downloaded by hand (spec §17, R147).
 */
data class Release(
    val tag: String,
    val version: AppVersion?,
    val prerelease: Boolean,
    val draft: Boolean,
    val publishedAt: Instant?,
    val notes: String,
    val assets: List<ReleaseAsset>,
    val pageUrl: String? = null,
) {
    /** The first `.apk` asset: v1's updater takes the same one (spec §13.5). */
    val apk: ReleaseAsset? get() = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

    val manifest: ReleaseAsset? get() = assets.firstOrNull { it.name == MANIFEST_NAME }

    /** v2 releases carry an APK and a manifest; v1.x releases have no manifest and show in Version history only. */
    val installable: Boolean get() = apk != null && manifest != null

    /** R145: a pre-release, or a beta version published by mistake as a full release. */
    val isBeta: Boolean get() = prerelease || version?.isBeta == true

    companion object {
        const val MANIFEST_NAME = "ledga-release.json"
    }
}

/** Spec §13.4's rules, apart from the network and storage so they are tested on their own. */
object UpdatePolicy {
    /** App start checks at most this often (R134). */
    val CHECK_EVERY: Duration = Duration.ofHours(6)

    /** "Later" on Home's banner (spec §13.4). */
    val SNOOZE: Duration = Duration.ofDays(3)

    /** True when [lastChecked] is unknown, 6 hours old, or in the future (the phone's clock was set back). */
    fun checkDue(lastChecked: Instant?, now: Instant): Boolean =
        lastChecked == null || now.isBefore(lastChecked) || !now.isBefore(lastChecked.plus(CHECK_EVERY))

    fun onChannel(release: Release, channel: UpdateChannel): Boolean =
        !release.draft && release.version != null && (channel == UpdateChannel.BETA || !release.isBeta)

    /** Version history (spec §13.4): every release on the channel, newest first. */
    fun history(releases: List<Release>, channel: UpdateChannel): List<Release> =
        releases.filter { onChannel(it, channel) }.sortedByDescending { it.version }

    /**
     * The newest installable release on the channel above [installed] (spec §13.4). Leaving beta never downgrades: on
     * stable, a beta build waits for the next full release above it.
     */
    fun newest(releases: List<Release>, installed: AppVersion, channel: UpdateChannel): Release? =
        history(releases, channel).firstOrNull { r ->
            val v = r.version
            r.installable && v != null && v > installed
        }

    /** R135: what Home's banner, the quiet download and the "ready" notice may offer: not a skipped version, not while snoozed. */
    fun offered(newest: Release?, skipped: AppVersion?, snoozedUntil: Instant?, now: Instant): Boolean =
        newest != null && newest.version != skipped && (snoozedUntil == null || !now.isBefore(snoozedUntil))
}
