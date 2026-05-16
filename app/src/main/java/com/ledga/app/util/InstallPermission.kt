package com.ledga.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Helpers around `REQUEST_INSTALL_PACKAGES`.
 *
 * On Android 8+ (API 26) the user must explicitly enable "Install unknown
 * apps" for any app that calls the package installer. We check the runtime
 * grant before firing the install intent so the user gets a one-tap path
 * to Settings instead of seeing the install fail silently.
 */
object InstallPermission {

    /** True when the app can already trigger an install without further user setup. */
    fun canInstall(context: Context): Boolean {
        // API 26+ is our minSdk, but guard anyway for clarity.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    /**
     * Open Settings → "Install unknown apps" for this app. The user flips
     * the toggle for Ledga and returns to the app — at which point [canInstall]
     * will return true on the next check.
     */
    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
