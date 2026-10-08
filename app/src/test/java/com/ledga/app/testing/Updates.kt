package com.ledga.app.testing

import com.ledga.app.data.update.CheckFailure
import com.ledga.app.data.update.ReleasesResponse
import com.ledga.app.data.update.UpdateHttp
import com.ledga.app.data.update.UpdateNotices
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** GitHub as a test sets it: queued answers to checks (the last repeats), manifests by URL, and APK bytes by URL. */
class FakeUpdateHttp : UpdateHttp {
    val answers = ArrayDeque<ReleasesResponse>()

    /** Each check's address and the ETag it sent. */
    val checks = mutableListOf<Pair<String, String?>>()
    val texts = mutableMapOf<String, String>()
    val files = mutableMapOf<String, ByteArray>()
    val downloads = mutableListOf<String>()

    /** Downloads write half the file, then the connection drops. */
    var failDownloads = false

    override suspend fun releases(url: String, etag: String?): ReleasesResponse {
        checks += url to etag
        return if (answers.size > 1) answers.removeFirst() else answers.firstOrNull() ?: ReleasesResponse.Failed(CheckFailure.OFFLINE)
    }

    override suspend fun text(url: String): String = texts[url] ?: throw IOException("nothing at $url")

    override suspend fun download(url: String, to: File, onProgress: suspend (done: Long, total: Long) -> Unit) {
        downloads += url
        val bytes = files[url] ?: throw IOException("nothing at $url")
        if (failDownloads) {
            to.writeBytes(bytes.copyOf(bytes.size / 2))
            throw IOException("connection reset")
        }
        to.writeBytes(bytes)
        onProgress(bytes.size / 2L, bytes.size.toLong())
        onProgress(bytes.size.toLong(), bytes.size.toLong())
    }
}

/** The Updates notifications, as a list of what was asked: "progress 2.0.1 50", "ready 2.0.1", "clearProgress"… */
class FakeUpdateNotices : UpdateNotices {
    val events = mutableListOf<String>()

    override fun progress(version: String, percent: Int?) {
        events += "progress $version $percent"
    }

    override fun clearProgress() {
        events += "clearProgress"
    }

    override fun ready(version: String) {
        events += "ready $version"
    }

    override fun clearReady() {
        events += "clearReady"
    }
}

fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
