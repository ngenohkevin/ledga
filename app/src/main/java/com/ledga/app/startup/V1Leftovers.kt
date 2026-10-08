package com.ledga.app.startup

import android.content.Context
import androidx.work.WorkManager
import java.io.File

/**
 * R31: v1 scheduled periodic jobs whose worker classes v2 deleted. An upgraded phone would fail them forever, so they
 * are cancelled right after the migration, along with v1's update-check preferences and the APKs v1's updater
 * downloaded (R158; v2 keeps its own in `noBackupFilesDir/updates`). Phase 5 must not reuse these job names.
 */
class V1Leftovers(private val context: Context, private val wm: WorkManager) : () -> Unit {
    override fun invoke() {
        JOBS.forEach(wm::cancelUniqueWork)
        context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        context.deleteSharedPreferences(UPDATE_PREFS)
        File(context.cacheDir, V1_DOWNLOADS).deleteRecursively()
    }

    companion object {
        val JOBS = listOf("insight_generation", "daily_summary", "weekly_summary", "update_check")
        private const val UPDATE_PREFS = "update_prefs"
        private const val V1_DOWNLOADS = "updates"
    }
}
