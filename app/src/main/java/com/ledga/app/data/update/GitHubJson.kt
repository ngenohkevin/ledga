package com.ledga.app.data.update

import com.ledga.core.update.AppVersion
import com.ledga.core.update.Release
import com.ledga.core.update.ReleaseAsset
import com.ledga.core.update.ReleaseManifest
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private class GhAsset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
    val size: Long = 0,
)

@Serializable
private class GhRelease(
    @SerialName("tag_name") val tag: String,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val page: String? = null,
    val assets: List<GhAsset> = emptyList(),
)

@Serializable
private class GhManifest(
    val version: String,
    val versionCode: Int,
    val apk: String,
    val sha256: String,
    val minSdk: Int,
    val channel: String,
)

/** GitHub's releases API (spec §13.4) and `ledga-release.json`, read leniently: fields Ledga doesn't use are ignored. */
object GitHubJson {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /** Throws [IllegalArgumentException] (kotlinx's SerializationException) when [text] isn't a release list. */
    fun releases(text: String): List<Release> = json.decodeFromString<List<GhRelease>>(text).map { r ->
        Release(
            tag = r.tag,
            version = AppVersion.parse(r.tag),
            prerelease = r.prerelease,
            draft = r.draft,
            publishedAt = r.publishedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
            notes = r.body.orEmpty(),
            assets = r.assets.map { ReleaseAsset(it.name, it.url, it.size) },
            pageUrl = r.page,
        )
    }

    /** Throws [IllegalArgumentException] when [text] isn't a manifest. */
    fun manifest(text: String): ReleaseManifest = json.decodeFromString<GhManifest>(text).let {
        ReleaseManifest(it.version, it.versionCode, it.apk, it.sha256, it.minSdk, it.channel)
    }
}
