package com.ledga.app.data.update

import com.ledga.core.update.AppVersion
import java.io.File

/** Downloaded updates (R139): `noBackupFilesDir/updates/`, out of Android backup (spec §12.1). */
class UpdateFiles(private val dir: File) {
    fun apk(version: AppVersion): File = File(dir, "ledga-$version.apk")

    fun partial(version: AppVersion): File = File(dir, "ledga-$version.apk.part")

    /** [version]'s verified APK, or null. Only a download that passed every check is given this name. */
    fun ready(version: AppVersion): File? = apk(version).takeIf { it.isFile && it.length() > 0 }

    /** The folder, created when needed. */
    fun dir(): File = dir.apply { mkdirs() }

    /** Deletes every file but [keep]'s APK and its download in progress: older, skipped or dropped versions, strays. */
    fun keepOnly(keep: AppVersion?) {
        val kept = keep?.let { setOf(apk(it).name, partial(it).name) }.orEmpty()
        dir.listFiles()?.filter { it.name !in kept }?.forEach { it.delete() }
    }

    /** R154: downloads of the installed version or an older one are of no use, the one just installed among them. */
    fun dropThrough(installed: AppVersion) {
        dir.listFiles()?.filter { f -> versionOf(f.name)?.let { it <= installed } == true }?.forEach { it.delete() }
    }

    private fun versionOf(name: String): AppVersion? =
        if (name.startsWith("ledga-")) AppVersion.parse(name.removePrefix("ledga-").removeSuffix(".part").removeSuffix(".apk")) else null
}
