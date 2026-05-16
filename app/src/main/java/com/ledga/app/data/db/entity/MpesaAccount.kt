package com.ledga.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single M-Pesa line tied to a SIM subscription on this device.
 * Multiple accounts let dual-SIM users keep Personal / Business separate.
 *
 * subscriptionId is the Android SubscriptionInfo.getSubscriptionId() value
 * captured at SMS-receive time. It's stable per-SIM across reboots but
 * changes if the SIM is removed and reinserted in a different slot.
 */
@Entity(
    tableName = "mpesa_accounts",
    indices = [
        Index(value = ["subscriptionId"], unique = true),
    ]
)
data class MpesaAccount(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subscriptionId: Int,
    val phoneNumber: String?,
    val displayName: String,
    val colorHex: String,
    val isPrimary: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
