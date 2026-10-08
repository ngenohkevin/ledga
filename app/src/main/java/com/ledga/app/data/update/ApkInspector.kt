package com.ledga.app.data.update

import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import java.io.File

/** What an APK says it is. */
data class ApkInfo(val packageName: String, val versionCode: Long)

/** R138: reads a downloaded APK before Android is asked to install it. Null when it can't be read. Tests use a fake. */
fun interface ApkInspector {
    fun inspect(apk: File): ApkInfo?
}

class AndroidApkInspector(private val packageManager: PackageManager) : ApkInspector {
    @Suppress("DEPRECATION") // the flags overload needs API 33; this one reads the same fields
    override fun inspect(apk: File): ApkInfo? =
        packageManager.getPackageArchiveInfo(apk.path, 0)?.let { ApkInfo(it.packageName, PackageInfoCompat.getLongVersionCode(it)) }
}
