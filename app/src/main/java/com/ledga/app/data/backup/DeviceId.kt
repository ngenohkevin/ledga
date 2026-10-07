package com.ledga.app.data.backup

import android.annotation.SuppressLint
import android.content.Context
import java.security.MessageDigest

/** R115: tells "the same phone" from "another phone" in a backup, without carrying an identifier out of it. */
fun interface DeviceId {
    fun value(): String?
}

class AndroidDeviceId(private val context: Context) : DeviceId {
    // Android's per-app id (the same for this app on this phone until a factory reset), hashed before it goes anywhere.
    @SuppressLint("HardwareIds")
    override fun value(): String? =
        android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
            ?.let(::fingerprint)

    companion object {
        fun fingerprint(id: String): String = MessageDigest.getInstance("SHA-256")
            .digest("ledga-device:$id".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(32)
    }
}
