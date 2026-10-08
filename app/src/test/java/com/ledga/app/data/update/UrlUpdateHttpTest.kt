package com.ledga.app.data.update

import com.ledga.app.testing.TinyHttpServer
import com.ledga.app.testing.TinyRequest
import com.ledga.app.testing.TinyResponse
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Spec §13.4, R147: the HTTP client against a real local server answering as GitHub does. */
class UrlUpdateHttpTest {
    @get:Rule val tmp = TemporaryFolder()

    private val payload = ByteArray(200_000) { (it % 251).toByte() }
    private val server = TinyHttpServer { r -> answer(r) }
    private val http = UrlUpdateHttp("Ledga/test", timeoutMs = 2_000)

    private fun answer(r: TinyRequest): TinyResponse = when (r.path) {
        "/releases" -> if (r.headers["if-none-match"] == "\"v1\"") {
            TinyResponse(304)
        } else {
            TinyResponse(200, "[]".toByteArray(), mapOf("ETag" to "\"v1\""))
        }
        "/limited" -> TinyResponse(403, headers = mapOf("x-ratelimit-remaining" to "0"))
        "/forbidden" -> TinyResponse(403)
        "/many" -> TinyResponse(429)
        "/broken" -> TinyResponse(500)
        "/asset" -> TinyResponse(302, headers = mapOf("Location" to "${server.base}/file"))
        "/file" -> TinyResponse(200, payload)
        "/short" -> TinyResponse(200, payload.copyOf(10), declaredLength = 1_000)
        "/manifest" -> TinyResponse(200, "{\"a\":1}".toByteArray())
        else -> TinyResponse(404)
    }

    @After fun stop() = server.close()

    @Test
    fun `a fresh list comes back with its ETag, asked for the way GitHub wants`() = runTest {
        assertEquals(ReleasesResponse.Fresh("[]", "\"v1\""), http.releases("${server.base}/releases", etag = null))
        val sent = server.requests.single().headers
        assertEquals("Ledga/test", sent["user-agent"])
        assertEquals("application/vnd.github+json", sent["accept"])
        assertEquals("2022-11-28", sent["x-github-api-version"])
        assertNull(sent["if-none-match"])
    }

    @Test
    fun `an unchanged list is a 304 when its ETag is sent`() = runTest {
        assertEquals(ReleasesResponse.NotModified, http.releases("${server.base}/releases", etag = "\"v1\""))
    }

    @Test
    fun `GitHub's hourly limit is told apart from other refusals`() = runTest {
        assertEquals(ReleasesResponse.Failed(CheckFailure.RATE_LIMITED), http.releases("${server.base}/limited", null))
        assertEquals(ReleasesResponse.Failed(CheckFailure.RATE_LIMITED), http.releases("${server.base}/many", null))
        assertEquals(ReleasesResponse.Failed(CheckFailure.SERVER), http.releases("${server.base}/forbidden", null))
        assertEquals(ReleasesResponse.Failed(CheckFailure.SERVER), http.releases("${server.base}/broken", null))
    }

    @Test
    fun `no server answering is offline`() = runTest {
        val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        assertEquals(ReleasesResponse.Failed(CheckFailure.OFFLINE), http.releases("http://127.0.0.1:$port/releases", null))
    }

    @Test
    fun `a download follows GitHub's redirect, writes every byte and reports progress`() = runTest {
        val to = tmp.newFile("x.apk.part")
        val progress = mutableListOf<Pair<Long, Long>>()
        http.download("${server.base}/asset", to) { done, total -> progress += done to total }
        assertTrue(payload.contentEquals(to.readBytes()))
        assertEquals(200_000L to 200_000L, progress.last())
        assertTrue(progress.size > 1)
    }

    @Test
    fun `a download that isn't there, or ends early, is an error`() = runTest {
        assertFailsWith<IOException> { http.download("${server.base}/missing", tmp.newFile("a.part")) { _, _ -> } }
        assertFailsWith<IOException> { http.download("${server.base}/short", tmp.newFile("b.part")) { _, _ -> } }
    }

    @Test
    fun `a small text file is read whole`() = runTest {
        assertEquals("{\"a\":1}", http.text("${server.base}/manifest"))
        assertFailsWith<IOException> { http.text("${server.base}/missing") }
    }
}
