package com.ledga.app.data.db

import androidx.room.TypeConverter
import com.ledga.app.data.db.entity.MatchType
import com.ledga.app.data.parser.TransactionDirection
import com.ledga.app.data.parser.TransactionType

class Converters {

    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun fromTransactionDirection(value: TransactionDirection): String = value.name

    @TypeConverter
    fun toTransactionDirection(value: String): TransactionDirection = TransactionDirection.valueOf(value)

    @TypeConverter
    fun fromMatchType(value: MatchType): String = value.name

    @TypeConverter
    fun toMatchType(value: String): MatchType = MatchType.valueOf(value)
}
