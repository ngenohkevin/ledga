package com.ledga.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MatchType {
    RECIPIENT_NAME, PHONE, TILL, PAYBILL
}

@Entity(
    tableName = "category_rules",
    indices = [Index(value = ["categoryId"])],
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CategoryRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val categoryId: Long,
    val matchType: MatchType,
    val matchValue: String
)
