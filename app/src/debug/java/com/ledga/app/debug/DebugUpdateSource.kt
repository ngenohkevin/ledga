package com.ledga.app.debug

import android.content.Context
import com.ledga.app.data.update.UpdateEndpoint
import com.ledga.app.data.update.UpdateEndpoints

/**
 * Ledga dev only (owner call A, R140): the local release server that `scripts/update-test-server.sh` runs, set by adb
 * through `DebugUpdateReceiver`. Only `http://127.0.0.1:<port>` is accepted; the debug network config allows nothing else.
 */
class DebugUpdateSource(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** "http://127.0.0.1:<port>", or null for GitHub. */
    val base: String? get() = prefs.getString(BASE, null)

    /** False, and nothing changes, for anything but `http://127.0.0.1:<port>`. Null clears it. */
    fun set(base: String?): Boolean {
        if (base != null && !LOCAL.matches(base)) return false
        prefs.edit().apply { if (base == null) remove(BASE) else putString(BASE, base.trimEnd('/')) }.commit()
        return true
    }

    companion object {
        const val FILE = "ledga_debug_updates"
        private const val BASE = "base"
        private val LOCAL = Regex("""http://127\.0\.0\.1:\d{2,5}/?""")
    }
}

/** Unset: GitHub, read for Version history only, since Ledga dev never installs a real release. Set: the local server. */
class DebugUpdateEndpoints(private val source: DebugUpdateSource) : UpdateEndpoints {
    override fun current(): UpdateEndpoint =
        source.base?.let { UpdateEndpoint("$it/releases.json", offersUpdates = true) }
            ?: UpdateEndpoint(UpdateEndpoint.GITHUB, offersUpdates = false)
}
