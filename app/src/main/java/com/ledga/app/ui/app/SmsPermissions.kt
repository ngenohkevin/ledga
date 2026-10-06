package com.ledga.app.ui.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri

object SmsPermissions {
    /** SMS to read and receive M-Pesa messages. Phone state only names lines and resolves single-SIM messages (R33). */
    val ALL = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS, Manifest.permission.READ_PHONE_STATE)
}

/** The Activity behind a Compose context (permission rationale needs one). */
fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Ledga's page in Android's Settings, where a permission denied for good can still be granted. */
fun Context.openAppSettings() {
    startActivity(
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/** Android's notification settings for Ledga (R59): where a notification permission refused for good can still be granted. */
fun Context.openNotificationSettings() {
    startActivity(
        Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
