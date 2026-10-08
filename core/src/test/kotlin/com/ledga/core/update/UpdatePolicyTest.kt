package com.ledga.core.update

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Spec §13.4: which releases a phone sees, and which one it is offered. Synthetic releases. */
class UpdatePolicyTest {
    private fun rel(tag: String, prerelease: Boolean = false, draft: Boolean = false, v2: Boolean = true) = Release(
        tag = tag,
        version = AppVersion.parse(tag),
        prerelease = prerelease,
        draft = draft,
        publishedAt = Instant.parse("2026-10-01T06:00:00Z"),
        notes = "",
        assets = buildList {
            add(ReleaseAsset("ledga-${tag.removePrefix("v")}.apk", "https://example.test/$tag.apk", 9_000_000))
            if (v2) add(ReleaseAsset(Release.MANIFEST_NAME, "https://example.test/$tag.json", 200))
        },
        pageUrl = "https://example.test/releases/$tag",
    )

    private val releases = listOf(
        rel("v1.6.0", v2 = false),
        rel("v2.0.0-beta.1", prerelease = true),
        rel("v2.0.0-beta.2", prerelease = true),
        rel("v2.0.0"),
        rel("v2.0.1-beta.1", prerelease = true),
        rel("v2.0.1-beta.2", prerelease = true, draft = true),
        rel("nightly", prerelease = true),
    )

    private fun v(text: String) = AppVersion.parse(text)!!

    private val now = Instant.parse("2026-10-07T09:00:00Z")

    @Test
    fun `beta follows betas and full releases, never drafts`() {
        assertEquals("v2.0.1-beta.1", UpdatePolicy.newest(releases, v("2.0.0-beta.1"), UpdateChannel.BETA)?.tag)
    }

    @Test
    fun `leaving beta waits for the next full release above the installed beta`() {
        assertEquals("v2.0.0", UpdatePolicy.newest(releases, v("2.0.0-beta.2"), UpdateChannel.STABLE)?.tag)
        assertNull(UpdatePolicy.newest(releases, v("2.0.0"), UpdateChannel.STABLE))
    }

    @Test
    fun `a v1 release is never offered, but it is in the history`() {
        val v1Only = listOf(rel("v1.6.0", v2 = false))
        assertNull(UpdatePolicy.newest(v1Only, v("1.5.0"), UpdateChannel.STABLE))
        assertEquals(listOf("v1.6.0"), UpdatePolicy.history(v1Only, UpdateChannel.STABLE).map { it.tag })
    }

    @Test
    fun `history is newest first, per channel, without drafts or other tags`() {
        assertEquals(listOf("v2.0.0", "v1.6.0"), UpdatePolicy.history(releases, UpdateChannel.STABLE).map { it.tag })
        assertEquals(
            listOf("v2.0.1-beta.1", "v2.0.0", "v2.0.0-beta.2", "v2.0.0-beta.1", "v1.6.0"),
            UpdatePolicy.history(releases, UpdateChannel.BETA).map { it.tag },
        )
    }

    @Test
    fun `a beta published by mistake as a full release is still a beta (R145)`() {
        val mislabeled = listOf(rel("v2.0.2-beta.1", prerelease = false))
        assertNull(UpdatePolicy.newest(mislabeled, v("2.0.0"), UpdateChannel.STABLE))
        assertEquals("v2.0.2-beta.1", UpdatePolicy.newest(mislabeled, v("2.0.0"), UpdateChannel.BETA)?.tag)
    }

    @Test
    fun `a skipped or snoozed version isn't offered, a newer one is (R135)`() {
        val newest = rel("v2.0.1")
        assertTrue(UpdatePolicy.offered(newest, skipped = null, snoozedUntil = null, now = now))
        assertFalse(UpdatePolicy.offered(newest, skipped = v("2.0.1"), snoozedUntil = null, now = now))
        assertTrue(UpdatePolicy.offered(newest, skipped = v("2.0.0"), snoozedUntil = null, now = now))
        assertFalse(UpdatePolicy.offered(newest, skipped = null, snoozedUntil = now.plusSeconds(60), now = now))
        assertTrue(UpdatePolicy.offered(newest, skipped = null, snoozedUntil = now, now = now))
        assertFalse(UpdatePolicy.offered(null, skipped = null, snoozedUntil = null, now = now))
    }

    @Test
    fun `a check is due every 6 hours`() {
        assertTrue(UpdatePolicy.checkDue(null, now))
        assertFalse(UpdatePolicy.checkDue(now.minus(Duration.ofHours(6)).plusSeconds(1), now))
        assertTrue(UpdatePolicy.checkDue(now.minus(Duration.ofHours(6)), now))
    }

    @Test
    fun `a clock set back makes a check due`() {
        assertTrue(UpdatePolicy.checkDue(now.plus(Duration.ofHours(2)), now))
    }

    @Test
    fun `a version is held while skipped or snoozed, and released when the snooze ends (final review I1)`() {
        assertTrue(UpdatePolicy.held(v("2.0.1"), skipped = v("2.0.1"), snoozedUntil = null, now = now))
        assertTrue(UpdatePolicy.held(v("2.0.1"), skipped = null, snoozedUntil = now.plusSeconds(1), now = now))
        assertFalse(UpdatePolicy.held(v("2.0.1"), skipped = v("2.0.0"), snoozedUntil = now, now = now))
    }
}
