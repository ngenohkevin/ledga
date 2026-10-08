package com.ledga.app.data.update

import com.ledga.core.update.AppVersion
import com.ledga.core.update.Release
import com.ledga.core.update.ReleaseManifest
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** Spec §13.4: GitHub's release list and `ledga-release.json`, as Ledga reads them. Synthetic JSON in GitHub's shape. */
class GitHubJsonTest {
    private val list = """
        [
          {
            "id": 1, "tag_name": "v2.0.1-beta.1", "name": "Ledga 2.0.1-beta.1", "draft": false, "prerelease": true,
            "published_at": "2026-10-05T06:00:00Z", "html_url": "https://example.test/releases/v2.0.1-beta.1",
            "body": "## What's new\n- One thing", "author": { "login": "someone" },
            "assets": [
              { "name": "ledga-2.0.1-beta.1.apk", "browser_download_url": "https://example.test/a.apk", "size": 9000000, "content_type": "application/vnd.android.package-archive" },
              { "name": "ledga-release.json", "browser_download_url": "https://example.test/m.json", "size": 210 }
            ]
          },
          {
            "tag_name": "v1.6.0", "draft": false, "prerelease": false, "published_at": null, "body": null,
            "assets": [ { "name": "ledga-v1.6.0.apk", "browser_download_url": "https://example.test/old.apk", "size": 4000000 } ]
          }
        ]
    """.trimIndent()

    @Test
    fun `a release list becomes Ledga's releases`() {
        val (beta, v1) = GitHubJson.releases(list)
        assertEquals("v2.0.1-beta.1", beta.tag)
        assertEquals(AppVersion(2, 0, 1, 1), beta.version)
        assertTrue(beta.prerelease)
        assertTrue(beta.installable)
        assertEquals(Instant.parse("2026-10-05T06:00:00Z"), beta.publishedAt)
        assertEquals("https://example.test/a.apk", beta.apk?.url)
        assertEquals(9_000_000L, beta.apk?.size)
        assertEquals("https://example.test/releases/v2.0.1-beta.1", beta.pageUrl)
        assertEquals("## What's new\n- One thing", beta.notes)
        assertFalse(v1.installable)
        assertNull(v1.publishedAt)
        assertEquals("", v1.notes)
    }

    @Test
    fun `an empty list is no releases`() {
        assertEquals(emptyList<Release>(), GitHubJson.releases("[]"))
    }

    @Test
    fun `an HTML page is not a release list`() {
        assertFailsWith<IllegalArgumentException> { GitHubJson.releases("<html><body>Bad gateway</body></html>") }
        assertFailsWith<IllegalArgumentException> { GitHubJson.releases("""{"message":"Not Found"}""") }
    }

    @Test
    fun `a manifest reads field for field, ignoring extras`() {
        val sha = "b".repeat(64)
        val text = """{"version":"2.0.1-beta.1","versionCode":2000101,"apk":"ledga-2.0.1-beta.1.apk","sha256":"$sha","minSdk":26,"channel":"beta","extra":1}"""
        assertEquals(ReleaseManifest("2.0.1-beta.1", 2_000_101, "ledga-2.0.1-beta.1.apk", sha, 26, "beta"), GitHubJson.manifest(text))
    }
}
