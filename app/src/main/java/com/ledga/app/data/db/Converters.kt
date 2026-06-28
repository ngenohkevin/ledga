package com.ledga.app.data.db

import androidx.room.TypeConverter
import com.ledga.app.data.db.entity.CarTag
import com.ledga.app.data.db.entity.InsightSeverity
import com.ledga.app.data.db.entity.InsightType
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

    @TypeConverter
    fun fromInsightType(value: InsightType): String = value.name

    @TypeConverter
    fun toInsightType(value: String): InsightType = InsightType.valueOf(value)

    @TypeConverter
    fun fromInsightSeverity(value: InsightSeverity): String = value.name

    @TypeConverter
    fun toInsightSeverity(value: String): InsightSeverity = InsightSeverity.valueOf(value)

    // Nullable: a transaction with no car tag stores SQL NULL.
    @TypeConverter
    fun fromCarTag(value: CarTag?): String? = value?.name

    @TypeConverter
    fun toCarTag(value: String?): CarTag? = value?.let { CarTag.valueOf(it) }
}
