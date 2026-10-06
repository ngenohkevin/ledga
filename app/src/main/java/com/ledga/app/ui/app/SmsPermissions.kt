package com.ledga.app.ui.app

import android.Manifest

object SmsPermissions {
    /** SMS to read and receive M-Pesa messages. Phone state only names lines and resolves single-SIM messages (R33). */
    val ALL = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS, Manifest.permission.READ_PHONE_STATE)
}
