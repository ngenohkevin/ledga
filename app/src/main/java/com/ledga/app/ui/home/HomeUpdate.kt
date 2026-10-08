package com.ledga.app.ui.home

import com.ledga.app.data.update.UpdateState
import com.ledga.app.work.DownloadProgress

/** Spec §10.4 item 6, R148: Home's update banner. */
sealed interface HomeUpdate {
    val version: String

    /** "Ledga 2.0.1 is available", with Later and Update. */
    data class Available(override val version: String) : HomeUpdate

    /** A download running, the person's or a quiet one (R136). */
    data class Downloading(override val version: String, val fraction: Float?) : HomeUpdate

    /** "Ledga 2.0.1 is ready to install", with Later and Install. */
    data class Ready(override val version: String) : HomeUpdate

    /** Final review I4: Android refused the install, or the person's download stopped; [message] says why. */
    data class Failed(override val version: String, val message: String) : HomeUpdate

    companion object {
        /**
         * Null with nothing newer, or while it is skipped or snoozed (R135). A running download always shows; a refused
         * install or a person's stopped download says why (final review I4).
         */
        fun of(s: UpdateState): HomeUpdate? {
            val version = s.newest?.version?.toString() ?: return null
            val running = s.download as? DownloadProgress.Running
            val stopped = (s.download as? DownloadProgress.Failed)?.takeIf { it.user && it.version == version }
            return when {
                running != null && running.version == version -> Downloading(version, running.fraction)
                !s.offered -> null
                s.installFailure != null -> Failed(version, s.installFailure)
                stopped != null -> Failed(version, stopped.message)
                s.ready -> Ready(version)
                else -> Available(version)
            }
        }
    }
}
