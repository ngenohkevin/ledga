package com.ledga.app.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionInfo
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

        // A SIM that moves slots or gets re-provisioned (eSIM) is assigned a
        // NEW subscription id, but the line itself — phone number, history —
        // is the same. Re-link the existing account instead of spawning a
        // duplicate "Line 2" that splits the user's analytics in half.
        if (phone != null) {
            accountDao.getAll().firstOrNull { it.phoneNumber == phone }?.let { existing ->
                val relinked = existing.copy(subscriptionId = subscriptionId)
                accountDao.update(relinked)
                return relinked
            }
        }

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
     * Make an SMS's subscription id usable for attribution. Some OEMs omit
     * the subscription extra on the SMS broadcast (and the sub_id column in
     * the SMS provider). When that happens and exactly ONE SIM is active,
     * the message can only have come from that SIM — attribute it there.
     * With two active SIMs we never guess; the transaction stays unassigned
     * rather than being silently mis-attributed.
     */
    fun resolveSubscriptionId(rawSubscriptionId: Int): Int {
        if (rawSubscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) return rawSubscriptionId
        return activeSubscriptions().singleOrNull()?.subscriptionId
            ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
    }

    /**
     * Reconcile stored accounts with the SIMs currently in the device.
     * Run on app start so detection stays accurate as hardware changes:
     * - re-link accounts whose SIM got a new subscription id (slot moved,
     *   SIM swapped out and back, eSIM re-provisioned) by phone number
     * - backfill phone numbers that weren't readable at account creation
     * Never renames, recolors, or deletes — user-set fields are sacred and
     * accounts for removed SIMs keep their history.
     */
    suspend fun syncActiveSubscriptions() {
        val active = activeSubscriptions()
        if (active.isEmpty()) return
        val accounts = accountDao.getAll()
        for (info in active) {
            val phone = readNumber(info)
            val byId = accounts.firstOrNull { it.subscriptionId == info.subscriptionId }
            if (byId != null) {
                if (byId.phoneNumber == null && phone != null) {
                    accountDao.update(byId.copy(phoneNumber = phone))
                }
                continue
            }
            // No account under this sub id — same line known under an old one?
            if (phone != null) {
                accounts.firstOrNull { it.phoneNumber == phone }?.let {
                    accountDao.update(it.copy(subscriptionId = info.subscriptionId))
                }
            }
        }
    }

    private fun activeSubscriptions(): List<SubscriptionInfo> {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }
        return try {
            val mgr = context.getSystemService(SubscriptionManager::class.java)
                ?: return emptyList()
            @Suppress("MissingPermission")
            mgr.activeSubscriptionInfoList.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    private fun readNumber(info: SubscriptionInfo): String? = try {
        @Suppress("MissingPermission", "DEPRECATION")
        info.number?.takeIf { it.isNotBlank() }
    } catch (_: SecurityException) {
        null
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
