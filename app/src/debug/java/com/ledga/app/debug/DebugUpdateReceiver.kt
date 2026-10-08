package com.ledga.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ledga.app.data.update.UpdateService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Ledga dev only (owner call A). Points the update check at `scripts/update-test-server.sh`, and checks at once:
 * `adb shell am broadcast -a com.ledga.app.DEBUG_UPDATES -n com.ledga.app.dev/com.ledga.app.debug.DebugUpdateReceiver --es base http://127.0.0.1:8765 --ez check true`
 * `--es base clear` goes back to GitHub, for Version history only (R140).
 */
@AndroidEntryPoint
class DebugUpdateReceiver : BroadcastReceiver() {
    @Inject lateinit var updates: UpdateService

    override fun onReceive(context: Context, intent: Intent) {
        val source = DebugUpdateSource(context)
        when (val base = intent.getStringExtra("base")) {
            null -> Unit
            "clear" -> source.set(null)
            else -> if (!source.set(base)) Log.w(TAG, "only http://127.0.0.1:<port> is allowed, not $base")
        }
        if (!intent.getBooleanExtra("check", false)) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                updates.check(force = true)
            } catch (e: Exception) {
                Log.w(TAG, "debug update check failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "Ledga"
    }
}
