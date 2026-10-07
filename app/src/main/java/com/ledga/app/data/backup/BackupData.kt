package com.ledga.app.data.backup

import com.ledga.app.data.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Spec §12, R118: a snapshot's whole content and an export's `data.json`. Every choice the person made travels: messages,
 * overrides, their rules and the built-in ones they switched off, their categories and the built-in ones they changed,
 * lines, and the settings that belong to the person (R114). Times are epoch milliseconds; enums are stored by name.
 */
@Serializable
data class BackupData(
    val format: Int = FORMAT,
    val writtenAt: Long,
    val appVersion: String,
    /** R115: [AndroidDeviceId.fingerprint] of the phone that wrote it; null when unknown. Never Android's id itself. */
    val device: String? = null,
    val counts: BackupCounts,
    val sms: List<SmsEntry> = emptyList(),
    val overrides: List<OverrideEntry> = emptyList(),
    /** The person's rules (origin USER). */
    val rules: List<RuleEntry> = emptyList(),
    /** Built-in rules the person switched off, by what they do (ids can differ between versions). */
    val systemRulesOff: List<RuleKey> = emptyList(),
    /** The person's categories, and the built-in ones they renamed, re-iconed, re-coloured or (un)tracked. */
    val categories: List<CategoryEntry> = emptyList(),
    val lines: List<LineEntry> = emptyList(),
    /** Null in a v1 export. */
    val settings: PortableSettings? = null,
) {
    companion object {
        const val FORMAT = 1
    }
}

@Serializable
data class BackupCounts(val sms: Int, val payments: Int)

/** One stored message; [line] is a [LineEntry.id] of the same backup. */
@Serializable
data class SmsEntry(
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val subscriptionId: Int? = null,
    val line: Long? = null,
    val source: String,
)

@Serializable
data class OverrideEntry(
    val code: String,
    val categoryKey: String? = null,
    val note: String? = null,
    val line: Long? = null,
    val ownAccount: Boolean? = null,
    val hidden: Boolean = false,
    val updatedAt: Long,
)

@Serializable
data class RuleEntry(
    val field: String,
    val pattern: String,
    val action: String,
    val categoryKey: String? = null,
    val priority: Int = 0,
    val createdAt: Long,
    val enabled: Boolean = true,
)

@Serializable
data class RuleKey(val field: String, val pattern: String, val action: String, val categoryKey: String? = null)

@Serializable
data class CategoryEntry(
    val key: String,
    val name: String,
    val group: String,
    val icon3d: String,
    val color: String? = null,
    val colorDark: String? = null,
    val tracked: Boolean,
    val sortOrder: Int,
    val origin: String,
    val archived: Boolean = false,
)

@Serializable
data class LineEntry(
    val id: Long,
    val subscriptionId: Int? = null,
    val phoneNumber: String? = null,
    val displayName: String,
    val color: String,
    val isPrimary: Boolean,
    val createdAt: Long,
)

/** R114: the settings that belong to the person; the phone's own state never travels. */
@Serializable
data class PortableSettings(
    val appearance: String,
    val textSize: String,
    val displayName: String? = null,
    val notifyDaily: Boolean,
    val dailySummaryMinute: Int,
    val notifyWeekly: Boolean,
    val notifyLarge: Boolean,
    val largeThresholdCents: Long,
    val notifyFuliza: Boolean,
) {
    companion object {
        fun of(s: Settings) = PortableSettings(
            s.appearance.name, s.textSize.name, s.displayName, s.notifyDaily, s.dailySummaryMinute, s.notifyWeekly,
            s.notifyLarge, s.largeThresholdCents, s.notifyFuliza,
        )
    }
}

/** An export's `manifest.json` (spec §12.2). */
@Serializable
data class ExportManifest(val format: Int, val appVersion: String, val exportedAt: Long, val counts: BackupCounts)

object BackupJson {
    /** Unknown keys are skipped, so a later format 1 file with extra fields still reads. */
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}

/** Why a file can't be restored; [message] is what the screen says. */
sealed class BackupFileError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotLedga : BackupFileError("This isn't a Ledga backup file.")

    class Newer : BackupFileError("This backup was made by a newer Ledga. Update Ledga, then try again.")

    class Damaged(cause: Throwable?) : BackupFileError("This backup file is damaged and can't be read.", cause)
}
