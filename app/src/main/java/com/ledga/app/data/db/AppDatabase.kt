package com.ledga.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ledga.app.data.db.dao.BudgetDao
import com.ledga.app.data.db.dao.CategoryDao
import com.ledga.app.data.db.dao.CategoryRuleDao
import com.ledga.app.data.db.dao.TransactionDao
import com.ledga.app.data.db.entity.Budget
import com.ledga.app.data.db.entity.Category
import com.ledga.app.data.db.entity.CategoryRule
import com.ledga.app.data.db.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        Category::class,
        Budget::class,
        CategoryRule::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun categoryRuleDao(): CategoryRuleDao
    abstract fun budgetDao(): BudgetDao
}
