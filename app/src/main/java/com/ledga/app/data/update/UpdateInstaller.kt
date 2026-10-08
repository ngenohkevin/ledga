package com.ledga.app.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.ledga.app.receiver.InstallStatusReceiver
import java.io.File
import java.io.IOException

/** What [UpdateInstaller.install] did. Android's answer comes later, through `InstallStatusReceiver`. */
enum class InstallStart { STARTED, NEEDS_PERMISSION, FAILED }

/** R138: hands a verified APK to Android. Tests use a fake. */
interface UpdateInstaller {
    /** Android's "Install unknown apps" switch for Ledga. */
    fun allowed(): Boolean

    fun install(apk: File): InstallStart
}

/** Spec §13.4: a `PackageInstaller` session, whose status goes to `InstallStatusReceiver`. */
class PackageInstallerUpdates(private val context: Context, private val events: InstallEvents) : UpdateInstaller {

    override fun allowed(): Boolean = context.packageManager.canRequestPackageInstalls()

    override fun install(apk: File): InstallStart {
        if (!allowed()) return InstallStart.NEEDS_PERMISSION
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            // R138: once Ledga has installed itself, Android 12+ may update it without asking again.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        var id = NO_SESSION
        return try {
            id = installer.createSession(params)
            installer.openSession(id).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite(STREAM_NAME, 0, apk.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                session.commit(statusReceiver(id).intentSender)
            }
            events.failure.value = null
            InstallStart.STARTED
        } catch (e: IOException) {
            notStarted(installer, id)
        } catch (e: SecurityException) {
            notStarted(installer, id)
        }
    }

    private fun notStarted(installer: PackageInstaller, id: Int): InstallStart {
        if (id != NO_SESSION) runCatching { installer.abandonSession(id) }
        events.failure.value = UpdateMessages.INSTALL_NOT_STARTED
        return InstallStart.FAILED
    }

    /** Android fills in the status, so before Android 12 the intent is mutable by default and after it must say so. */
    private fun statusReceiver(id: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        id,
        Intent(context, InstallStatusReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0),
    )

    companion object {
        private const val NO_SESSION = -1
        private const val STREAM_NAME = "ledga.apk"

        /** Android's "Install unknown apps" switch, for Ledga alone. */
        fun settingsIntent(context: Context): Intent =
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
    }
}
