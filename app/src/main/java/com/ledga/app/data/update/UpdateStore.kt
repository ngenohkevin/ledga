package com.ledga.app.data.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ledga.core.update.AppVersion
import com.ledga.core.update.UpdateChannel
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** What the update service keeps between runs (R133). The defaults are "nothing yet". */
data class UpdatePrefs(
    /** The last release list received, as it came (it is parsed again when read). */
    val releasesJson: String? = null,
    /** Where that list came from: its ETag is sent only to the same address. */
    val releasesUrl: String? = null,
    val etag: String? = null,
    val checkedAt: Instant? = null,
    val failure: CheckFailure? = null,
    /** The person's choice; null until they make one (the service then follows the installed build, R145). */
    val channel: UpdateChannel? = null,
    val skipped: AppVersion? = null,
    val snoozedUntil: Instant? = null,
    /** The versionName whose What's new has been seen (R141). */
    val seenVersion: String? = null,
)

/** R133: the update service's own DataStore file, apart from v1's settings; per-phone, never backed up. */
class UpdateStore(private val store: DataStore<Preferences>) {

    /** An unreadable file reads as nothing cached. */
    val prefs: Flow<UpdatePrefs> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::read)

    suspend fun current(): UpdatePrefs = prefs.first()

    /** A fresh list, kept with its address and ETag; any earlier failure is over. */
    suspend fun saveReleases(json: String, url: String, etag: String?, at: Instant) = edit {
        it[RELEASES] = json
        it[URL] = url
        if (etag == null) it.remove(ETAG) else it[ETAG] = etag
        it[CHECKED] = at.toEpochMilli()
        it.remove(FAILURE)
    }

    /** A 304: the cached list is current as of [at]. */
    suspend fun checkedUnchanged(at: Instant) = edit {
        it[CHECKED] = at.toEpochMilli()
        it.remove(FAILURE)
    }

    /** No answer: the cached list stays as it was (R147). */
    suspend fun checkFailed(reason: CheckFailure) = edit { it[FAILURE] = reason.name }

    suspend fun setChannel(channel: UpdateChannel) = edit { it[CHANNEL] = channel.name }

    suspend fun skip(version: AppVersion) = edit { it[SKIPPED] = version.toString() }

    suspend fun snoozeUntil(at: Instant) = edit { it[SNOOZED] = at.toEpochMilli() }

    suspend fun setSeen(versionName: String) = edit { it[SEEN] = versionName }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        store.edit { block(it) }
    }

    companion object {
        const val FILE_NAME = "ledga_updates"

        private val RELEASES = stringPreferencesKey("releases_json")
        private val URL = stringPreferencesKey("releases_url")
        private val ETAG = stringPreferencesKey("etag")
        private val CHECKED = longPreferencesKey("checked_at")
        private val FAILURE = stringPreferencesKey("failure")
        private val CHANNEL = stringPreferencesKey("channel")
        private val SKIPPED = stringPreferencesKey("skipped")
        private val SNOOZED = longPreferencesKey("snoozed_until")
        private val SEEN = stringPreferencesKey("seen_version")

        fun read(p: Preferences): UpdatePrefs = UpdatePrefs(
            releasesJson = p[RELEASES],
            releasesUrl = p[URL],
            etag = p[ETAG],
            checkedAt = p[CHECKED]?.let(Instant::ofEpochMilli),
            failure = p[FAILURE]?.let { v -> CheckFailure.entries.firstOrNull { it.name == v } },
            channel = p[CHANNEL]?.let { v -> UpdateChannel.entries.firstOrNull { it.name == v } },
            skipped = p[SKIPPED]?.let(AppVersion::parse),
            snoozedUntil = p[SNOOZED]?.let(Instant::ofEpochMilli),
            seenVersion = p[SEEN],
        )
    }
}
