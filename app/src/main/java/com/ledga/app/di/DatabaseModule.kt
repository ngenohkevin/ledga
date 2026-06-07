package com.ledga.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ledga.app.data.db.AppDatabase
import com.ledga.app.data.db.DefaultData
import com.ledga.app.data.db.Migrations
import com.ledga.app.data.db.dao.BudgetDao
import com.ledga.app.data.db.dao.CategoryDao
import com.ledga.app.data.db.dao.CategoryRuleDao
import com.ledga.app.data.db.dao.GoalDao
import com.ledga.app.data.db.dao.InsightDao
import com.ledga.app.data.db.dao.MpesaAccountDao
import com.ledga.app.data.db.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "ledga.db"
        )
            .addMigrations(
                Migrations.MIGRATION_1_2,
                Migrations.MIGRATION_2_3,
                Migrations.MIGRATION_3_4,
            )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    CoroutineScope(Dispatchers.IO).launch {
                        val database = Room.databaseBuilder(
                            context, AppDatabase::class.java, "ledga.db"
                        ).build()
                        database.categoryDao().insertAll(DefaultData.DEFAULT_CATEGORIES)
                        database.categoryRuleDao().insertAll(DefaultData.DEFAULT_RULES)
                    }
                }
            })
            .build()
    }

    @Provides
    fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideCategoryRuleDao(db: AppDatabase): CategoryRuleDao = db.categoryRuleDao()

    @Provides
    fun provideBudgetDao(db: AppDatabase): BudgetDao = db.budgetDao()

    @Provides
    fun provideMpesaAccountDao(db: AppDatabase): MpesaAccountDao = db.mpesaAccountDao()

    @Provides
    fun provideGoalDao(db: AppDatabase): GoalDao = db.goalDao()

    @Provides
    fun provideInsightDao(db: AppDatabase): InsightDao = db.insightDao()
}
