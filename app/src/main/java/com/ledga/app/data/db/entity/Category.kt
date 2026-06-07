package com.ledga.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val icon: String,
    val color: String,
    val isDefault: Boolean = true,
    /**
     * Transfer categories hold movements between the user's own accounts
     * (bank, M-PESA card, …). Spending queries exclude them — moving your
     * own money is not spending.
     */
    @ColumnInfo(defaultValue = "0")
    val isTransfer: Boolean = false
)
