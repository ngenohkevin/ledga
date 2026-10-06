package com.ledga.app.data.lines

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

/** One active SIM as Android describes it. [displayName] and [number] are null when the phone won't say. */
data class Sim(val subscriptionId: Int, val displayName: String?, val number: String?)

/** The phone's active SIMs. Empty without READ_PHONE_STATE (spec §9.2: lines are then "Line N"). */
interface SimDirectory {
    fun active(): List<Sim>
    fun find(subscriptionId: Int): Sim?
}

@SuppressLint("MissingPermission") // every call is behind manager(), which checks READ_PHONE_STATE
class AndroidSimDirectory(private val context: Context) : SimDirectory {

    override fun active(): List<Sim> = try {
        manager()?.activeSubscriptionInfoList.orEmpty().map(::toSim)
    } catch (e: SecurityException) {
        emptyList()
    }

    override fun find(subscriptionId: Int): Sim? = try {
        manager()?.getActiveSubscriptionInfo(subscriptionId)?.let(::toSim)
    } catch (e: SecurityException) {
        null
    }

    private fun manager(): SubscriptionManager? =
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            null
        } else {
            context.getSystemService(SubscriptionManager::class.java)
        }

    @Suppress("DEPRECATION") // SubscriptionInfo.number: getPhoneNumber() needs API 33 and a stronger permission
    private fun toSim(info: SubscriptionInfo) = Sim(
        subscriptionId = info.subscriptionId,
        displayName = info.displayName?.toString()?.takeIf { it.isNotBlank() },
        number = try {
            info.number?.takeIf { it.isNotBlank() }
        } catch (e: SecurityException) {
            null
        },
    )
}
