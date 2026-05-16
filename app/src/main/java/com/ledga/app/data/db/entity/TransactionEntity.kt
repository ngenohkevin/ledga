package com.ledga.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ledga.app.data.parser.TransactionDirection
import com.ledga.app.data.parser.TransactionType
// TransactionType includes: FULIZA_AUTO_PAY (auto-deduction from balance)

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["transactionCode"], unique = true),
        Index(value = ["categoryId"]),
        Index(value = ["accountId"]),
        Index(value = ["timestamp"]),
        Index(value = ["type"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = MpesaAccount::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val transactionCode: String,
    val type: TransactionType,
    val amount: Double,
    val transactionCost: Double,
    val recipientName: String?,
    val recipientPhone: String?,
    val accountNumber: String?,
    val destinationCountry: String?,
    val balance: Double,
    val direction: TransactionDirection,
    val categoryId: Long?,
    val fulizaAmount: Double?,
    val fulizaOutstanding: Double?,
    val reversedTransactionCode: String?,
    val rawSms: String,
    val timestamp: Long,
    val createdAt: Long = System.currentTimeMillis(),
    // v2 additions (DB v2)
    val accountId: Long? = null,
    val note: String? = null,
)
