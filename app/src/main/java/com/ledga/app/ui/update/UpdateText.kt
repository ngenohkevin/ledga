package com.ledga.app.ui.update

import com.ledga.app.data.update.CheckFailure
import com.ledga.app.data.update.UpdateState
import com.ledga.app.ui.design.format.DateLabels
import com.ledga.app.work.DownloadProgress
import com.ledga.core.update.UpdateChannel
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/** Everything the update screens and You's row say (R135, R142, R145, R147, R149). */
object UpdateText {
    private val ELLIPSIS = Char(0x2026)

    const val DEV_ONLY = "Ledga dev doesn't install updates. Version history still shows every release."
    const val ALLOW_INSTALLS = "To install updates, let Ledga install apps in Android's settings."
    const val NO_NOTES = "No notes were written for this version."
    const val BETA_ON = "You get new versions early. They may have rough edges."
    const val BETA_OFF = "Try new versions early. They may have rough edges."

    /** You → About → Updates (R149). */
    fun youLine(s: UpdateState): String {
        val running = s.download as? DownloadProgress.Running
        val newest = s.newest?.version
        return when {
            !s.offersUpdates -> "v${s.installed} · updates are off in Ledga dev"
            running != null -> running.fraction?.let { "Downloading ${percent(it)}%" } ?: "Downloading$ELLIPSIS"
            newest != null && s.ready -> "$newest ready to install"
            newest != null && s.skipped -> "v${s.installed} · $newest skipped"
            newest != null -> "$newest available"
            s.failure != null -> "v${s.installed} · couldn't check"
            s.checkedAt == null -> "v${s.installed}"
            else -> "v${s.installed} · up to date"
        }
    }

    /** "Checked today, 9:12 AM", "Checked yesterday, 9:00 AM", "Checked 2 Oct", "Checked 2 Oct 2025"; "Not checked yet". */
    fun checkedLine(at: Instant?, today: LocalDate): String {
        at ?: return "Not checked yet"
        val day = DateLabels.nairobiDate(at)
        return "Checked " + when (day) {
            today -> "today, ${DateLabels.clock(at)}"
            today.minusDays(1) -> "yesterday, ${DateLabels.clock(at)}"
            else -> if (day.year == today.year) DateLabels.dayMonth(day) else DateLabels.date(day)
        }
    }

    /** R147: why the last check got no answer; the list from before still shows. */
    fun failureLine(failure: CheckFailure): String = when (failure) {
        CheckFailure.OFFLINE -> "Couldn't check: no connection."
        CheckFailure.RATE_LIMITED -> "Couldn't check: GitHub asked Ledga to wait. It tries again later."
        CheckFailure.SERVER -> "Couldn't check: GitHub didn't answer properly. It tries again later."
    }

    /** "9.0 MB", "850 KB"; null when GitHub didn't say. */
    fun size(bytes: Long): String? = when {
        bytes <= 0 -> null
        bytes < 1_000_000 -> "${(bytes / 1_000.0).roundToInt()} KB"
        else -> String.format(Locale.ENGLISH, "%.1f MB", bytes / 1_000_000.0)
    }

    /** R145: what the Beta updates switch does from here. */
    fun betaLine(s: UpdateState): String = when {
        s.channel == UpdateChannel.BETA -> BETA_ON
        s.installed.isBeta -> "You'll stay on ${s.installed} until the next full release."
        else -> BETA_OFF
    }

    fun percent(fraction: Float): Int = (fraction * 100).roundToInt().coerceIn(0, 100)
}
