package com.ledga.app.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.ledga.app.data.db.dao.MpesaAccountDao
import com.ledga.app.data.db.entity.MpesaAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountsRepository @Inject constructor(
    private val accountDao: MpesaAccountDao,
    @ApplicationContext private val context: Context,
) {

    /** Default color rotation when we auto-create accounts. */
    private val autoColors = listOf(
        "#10A37F", // accent
        "#3478F6", // transport blue
        "#A155F0", // airtime purple
        "#EC4072", // food pink
    )

    fun observeAll(): Flow<List<MpesaAccount>> = accountDao.observeAll()
    fun observeCount(): Flow<Int> = accountDao.count()
    suspend fun getAll(): List<MpesaAccount> = accountDao.getAll()
    suspend fun findById(id: Long): MpesaAccount? = accountDao.findById(id)

    /**
     * Look up an account by Android SubscriptionInfo id, creating one on
     * first sight. Carrier name / phone number is filled in from
     * SubscriptionManager when READ_PHONE_STATE is granted; otherwise we
     * fall back to "Line {n}".
     *
     * Returns null when [subscriptionId] is [SubscriptionManager.INVALID_SUBSCRIPTION_ID]
     * — single-SIM devices or pre-Lollipop intents.
     */
    suspend fun getOrCreateForSubscription(subscriptionId: Int): MpesaAccount? {
        if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return null

        accountDao.findBySubscriptionId(subscriptionId)?.let { return it }

        val (displayName, phone) = lookupSubscriptionInfo(subscriptionId)
        val existingCount = accountDao.getAll().size
        val nextNumber = existingCount + 1

        val account = MpesaAccount(
            subscriptionId = subscriptionId,
            phoneNumber = phone,
            displayName = displayName ?: "Line $nextNumber",
            colorHex = autoColors[existingCount % autoColors.size],
            isPrimary = existingCount == 0,
        )
        val id = accountDao.insert(account)
        // If insert returned -1 (race: another thread inserted) re-read.
        return if (id == -1L) {
            accountDao.findBySubscriptionId(subscriptionId)
        } else {
            account.copy(id = id)
        }
    }

    suspend fun rename(id: Long, name: String) {
        accountDao.findById(id)?.copy(displayName = name)?.let { accountDao.update(it) }
    }

    suspend fun recolor(id: Long, hex: String) {
        accountDao.findById(id)?.copy(colorHex = hex)?.let { accountDao.update(it) }
    }

    suspend fun setPrimary(id: Long) {
        accountDao.setPrimary(id)
    }

    suspend fun delete(id: Long) {
        accountDao.deleteById(id)
    }

    /**
     * Resolve display name + phone number for a subscription ID.
     * Returns nulls when READ_PHONE_STATE is denied (graceful degradation).
     */
    private fun lookupSubscriptionInfo(subscriptionId: Int): Pair<String?, String?> {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null to null
        }
        return try {
            val mgr = context.getSystemService(SubscriptionManager::class.java)
                ?: return null to null
            @Suppress("MissingPermission")
            val info = mgr.getActiveSubscriptionInfo(subscriptionId)
            val name = info?.displayName?.toString()?.takeIf { it.isNotBlank() }
            @Suppress("MissingPermission")
            val number = info?.number?.takeIf { it.isNotBlank() }
            name to number
        } catch (_: SecurityException) {
            null to null
        }
    }
}
