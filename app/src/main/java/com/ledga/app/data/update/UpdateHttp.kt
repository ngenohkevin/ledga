package com.ledga.app.data.update

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Why a check got no answer (R147). */
enum class CheckFailure { OFFLINE, RATE_LIMITED, SERVER }

/** GitHub's answer to a check. */
sealed interface ReleasesResponse {
    data class Fresh(val body: String, val etag: String?) : ReleasesResponse

    /** 304: the cached list is still current, and the request didn't count against GitHub's hourly limit. */
    data object NotModified : ReleasesResponse

    data class Failed(val reason: CheckFailure) : ReleasesResponse
}

/** The update service's network: GitHub's API and asset downloads, nothing else (spec §14). Tests use a fake or a local server. */
interface UpdateHttp {
    suspend fun releases(url: String, etag: String?): ReleasesResponse

    /** A small text file (the manifest). Throws IOException. */
    suspend fun text(url: String): String

    /** Streams [url] into [to]; [onProgress] gets the bytes done and the total (−1 when unknown). Throws IOException. */
    suspend fun download(url: String, to: File, onProgress: suspend (done: Long, total: Long) -> Unit)
}

/** [UpdateHttp] over `HttpURLConnection`, with 10 s timeouts (spec §13.4). It follows GitHub's asset redirects. */
class UrlUpdateHttp(private val userAgent: String, private val timeoutMs: Int = TIMEOUT_MS) : UpdateHttp {

    override suspend fun releases(url: String, etag: String?): ReleasesResponse = withContext(Dispatchers.IO) {
        var c: HttpURLConnection? = null
        try {
            val conn = open(url).apply {
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", GITHUB_API_VERSION)
                if (etag != null) setRequestProperty("If-None-Match", etag)
            }
            c = conn
            when (val code = conn.responseCode) {
                HttpURLConnection.HTTP_OK -> ReleasesResponse.Fresh(conn.inputStream.use { read(it, MAX_LIST_BYTES) }, conn.getHeaderField("ETag"))
                HttpURLConnection.HTTP_NOT_MODIFIED -> ReleasesResponse.NotModified
                HttpURLConnection.HTTP_FORBIDDEN, TOO_MANY_REQUESTS -> ReleasesResponse.Failed(
                    if (code == TOO_MANY_REQUESTS || conn.getHeaderField("x-ratelimit-remaining") == "0") CheckFailure.RATE_LIMITED else CheckFailure.SERVER,
                )
                else -> ReleasesResponse.Failed(CheckFailure.SERVER)
            }
        } catch (e: IOException) {
            ReleasesResponse.Failed(CheckFailure.OFFLINE)
        } finally {
            c?.disconnect()
        }
    }

    override suspend fun text(url: String): String = withContext(Dispatchers.IO) {
        val c = open(url)
        try {
            if (c.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${c.responseCode}")
            c.inputStream.use { read(it, MAX_TEXT_BYTES) }
        } finally {
            c.disconnect()
        }
    }

    override suspend fun download(url: String, to: File, onProgress: suspend (done: Long, total: Long) -> Unit): Unit = withContext(Dispatchers.IO) {
        val c = open(url)
        try {
            if (c.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${c.responseCode}")
            val total = c.contentLengthLong
            var done = 0L
            c.inputStream.use { input ->
                to.outputStream().use { output ->
                    val buffer = ByteArray(BLOCK)
                    while (true) {
                        ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        done += n
                        if (done > MAX_APK_BYTES) throw IOException("the download is larger than an APK can be")
                        output.write(buffer, 0, n)
                        onProgress(done, total)
                    }
                }
            }
            if (total >= 0 && done != total) throw IOException("the download ended early")
        } finally {
            c.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = timeoutMs
        readTimeout = timeoutMs
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", userAgent)
    }

    /** The whole stream as UTF-8 text, refusing anything over [max] bytes. */
    private fun read(input: InputStream, max: Int): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BLOCK)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > max) throw IOException("the answer is larger than expected")
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    companion object {
        const val TIMEOUT_MS = 10_000
        private const val GITHUB_API_VERSION = "2022-11-28"
        private const val TOO_MANY_REQUESTS = 429
        private const val BLOCK = 64 * 1024
        private const val MAX_LIST_BYTES = 4 * 1024 * 1024
        private const val MAX_TEXT_BYTES = 64 * 1024
        private const val MAX_APK_BYTES = 64L * 1024 * 1024
    }
}
