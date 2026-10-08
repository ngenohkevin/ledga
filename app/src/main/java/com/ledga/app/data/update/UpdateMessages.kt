package com.ledga.app.data.update

import com.ledga.core.update.ManifestProblem

/** What Ledga says when an update can't be downloaded (R136, R138). Task 6 adds the install messages. */
object UpdateMessages {
    const val NETWORK = "The download stopped. Check your connection and try again."
    const val MISMATCH = "The download didn't match the release, so it was deleted. Try again."
    const val NOT_THIS_APP = "That file isn't an update for this Ledga, so it wasn't kept."
    const val DOWNLOAD_FAILED = "The download didn't finish. Try again."

    fun needsAndroid(minSdk: Int): String = "This update needs Android ${androidName(minSdk)} or newer."

    fun of(problem: ManifestProblem): String = when (problem) {
        ManifestProblem.Mismatch -> MISMATCH
        is ManifestProblem.NeedsAndroid -> needsAndroid(problem.minSdk)
    }

    /** Android's version name for an API level ("13" for 33), or the level itself past the ones Ledga knows. */
    fun androidName(api: Int): String = when (api) {
        26 -> "8"
        27 -> "8.1"
        28 -> "9"
        29 -> "10"
        30 -> "11"
        31 -> "12"
        32 -> "12L"
        33 -> "13"
        34 -> "14"
        35 -> "15"
        36 -> "16"
        else -> "(API $api)"
    }
}
