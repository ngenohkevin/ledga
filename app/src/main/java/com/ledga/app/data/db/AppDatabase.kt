package com.ledga.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ledga.app.data.db.dao.BudgetDao
import com.ledga.app.data.db.dao.CategoryDao
import com.ledga.app.data.db.dao.CategoryRuleDao
import com.ledga.app.data.db.dao.GoalDao
import com.ledga.app.data.db.dao.InsightDao
import com.ledga.app.data.db.dao.MpesaAccountDao
import com.ledga.app.data.db.dao.TransactionDao
import com.ledga.app.data.db.entity.Budget
import com.ledga.app.data.db.entity.Category
import com.ledga.app.data.db.entity.CategoryRule
import com.ledga.app.data.db.entity.Goal
import com.ledga.app.data.db.entity.GoalContribution
import com.ledga.app.data.db.entity.Insight
import com.ledga.app.data.db.entity.MpesaAccount
import com.ledga.app.data.db.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        Category::class,
        Budget::class,
        CategoryRule::class,
        MpesaAccount::class,
        Goal::class,
        GoalContribution::class,
        Insight::class,
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun categoryRuleDao(): CategoryRuleDao
    abstract fun budgetDao(): BudgetDao
    abstract fun mpesaAccountDao(): MpesaAccountDao
    abstract fun goalDao(): GoalDao
    abstract fun insightDao(): InsightDao
}
