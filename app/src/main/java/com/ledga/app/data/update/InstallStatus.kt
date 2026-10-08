package com.ledga.app.data.update

import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat

/** Spec §13.4, R138: Android's answer to an install session. */
object InstallStatus {
    /**
     * - When Android asks the person, [confirm] shows its screen.
     * - Success, or a confirm the person cancelled, leaves nothing to explain.
     * - Any other answer becomes a message; when the file itself was the problem, [damaged] deletes it.
     */
    fun handle(intent: Intent, events: InstallEvents, confirm: (Intent) -> Unit, damaged: () -> Unit) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)?.let { confirm(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED -> events.failure.value = null
            else -> {
                if (status == PackageInstaller.STATUS_FAILURE_INVALID) damaged()
                events.failure.value = UpdateMessages.install(status)
            }
        }
    }
}
