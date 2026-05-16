package com.ledga.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ledga.app.data.db.entity.Goal
import com.ledga.app.data.db.entity.GoalContribution
import kotlinx.coroutines.flow.Flow

data class GoalProgress(
    val goalId: Long,
    val contributedAmount: Double,
)

@Dao
interface GoalDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(goal: Goal): Long

    @Update
    suspend fun update(goal: Goal)

    @Delete
    suspend fun delete(goal: Goal)

    @Query("SELECT * FROM goals ORDER BY completedAt IS NULL DESC, createdAt DESC")
    fun observeAll(): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): Goal?

    @Query("UPDATE goals SET completedAt = :completedAt WHERE id = :id")
    suspend fun markComplete(id: Long, completedAt: Long)

    // ---- Contributions (manual marks) ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContribution(contribution: GoalContribution)

    @Query("DELETE FROM goal_contributions WHERE goalId = :goalId AND transactionId = :transactionId")
    suspend fun removeContribution(goalId: Long, transactionId: Long)

    @Query("""
        SELECT goalId, COALESCE(SUM(t.amount), 0.0) AS contributedAmount
        FROM goal_contributions gc
        INNER JOIN transactions t ON t.id = gc.transactionId
        WHERE goalId = :goalId
        GROUP BY goalId
    """)
    fun observeManualProgress(goalId: Long): Flow<GoalProgress?>

    @Query("SELECT transactionId FROM goal_contributions WHERE goalId = :goalId")
    fun observeManualContributionIds(goalId: Long): Flow<List<Long>>

    @Query("SELECT goalId FROM goal_contributions WHERE transactionId = :transactionId")
    fun observeGoalIdsForTransaction(transactionId: Long): Flow<List<Long>>
}
