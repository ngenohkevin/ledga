package com.ledga.app.data.repository

import com.ledga.app.data.db.dao.GoalDao
import com.ledga.app.data.db.dao.TransactionDao
import com.ledga.app.data.db.entity.ContributionRule
import com.ledga.app.data.db.entity.Goal
import com.ledga.app.data.db.entity.GoalContribution
import com.ledga.app.data.db.entity.TransactionWithCategory
import com.ledga.app.data.parser.TransactionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

/**
 * A goal plus its live progress, ETA and status. Built by [GoalsRepository]
 * by combining the goal row with whichever progress flow matches its rule.
 */
data class GoalWithProgress(
    val goal: Goal,
    val currentAmount: Double,
    val percent: Float,
    val eta: Long?,
    val status: GoalStatus,
)

enum class GoalStatus { OnTrack, Behind, Ahead, Completed }

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class GoalsRepository @Inject constructor(
    private val goalDao: GoalDao,
    private val transactionDao: TransactionDao,
) {

    // ---- CRUD ----

    suspend fun create(
        name: String,
        targetAmount: Double,
        targetDate: Long?,
        rule: ContributionRule,
        colorHex: String,
    ): Long = goalDao.insert(
        Goal(
            name = name.trim(),
            targetAmount = targetAmount,
            targetDate = targetDate,
            contributionRule = rule.encode(),
            colorHex = colorHex,
        )
    )

    suspend fun update(goal: Goal) = goalDao.update(goal)
    suspend fun delete(goal: Goal) = goalDao.delete(goal)
    suspend fun findById(id: Long): Goal? = goalDao.findById(id)

    suspend fun markComplete(id: Long) {
        goalDao.markComplete(id, System.currentTimeMillis())
    }

    // ---- Manual contributions ----

    suspend fun addManualContribution(goalId: Long, transactionId: Long) {
        goalDao.upsertContribution(GoalContribution(goalId = goalId, transactionId = transactionId))
    }

    suspend fun removeManualContribution(goalId: Long, transactionId: Long) {
        goalDao.removeContribution(goalId, transactionId)
    }

    fun observeManualContributionIds(goalId: Long): Flow<List<Long>> =
        goalDao.observeManualContributionIds(goalId)

    /** Which goals contain this transaction as a manual contribution. */
    fun observeGoalIdsForTransaction(transactionId: Long): Flow<List<Long>> =
        goalDao.observeGoalIdsForTransaction(transactionId)

    /** All goals with the Manual rule — what the detail sheet shows. */
    fun observeManualGoals(): Flow<List<Goal>> =
        goalDao.observeAll().map { all ->
            all.filter { ContributionRule.decode(it.contributionRule) is ContributionRule.Manual }
        }

    // ---- Progress observation ----

    /**
     * Stream of all goals with progress computed live. Updates on any
     * transaction insert that affects an active rule.
     */
    fun observeGoalsWithProgress(): Flow<List<GoalWithProgress>> =
        goalDao.observeAll().flatMapLatest { goals ->
            if (goals.isEmpty()) return@flatMapLatest flowOf(emptyList())
            // Combine each goal with its own progress flow.
            val progressFlows = goals.map { goal -> progressFlow(goal).map { goal to it } }
            combine(progressFlows) { pairs ->
                pairs.map { (goal, amount) -> goal.toWithProgress(amount) }
            }
        }

    /** Live progress for one goal — handy for the detail screen. */
    fun observeProgress(goalId: Long): Flow<GoalWithProgress?> =
        flow { emit(goalDao.findById(goalId)) }
            .flatMapLatest { goal ->
                if (goal == null) flowOf(null)
                else progressFlow(goal).map { goal.toWithProgress(it) }
            }

    /** Contributing transactions for a goal — used by the detail screen list. */
    fun observeContributingTransactions(goal: Goal): Flow<List<TransactionWithCategory>> =
        when (val rule = ContributionRule.decode(goal.contributionRule)) {
            ContributionRule.AllSavingsDeposits ->
                transactionDao.observeSavingsTransactionsSince(goal.createdAt)
            is ContributionRule.ToRecipient ->
                transactionDao.observeRecipientTransactionsSince(rule.recipientName, goal.createdAt)
            ContributionRule.Manual ->
                flowOf(emptyList())
        }

    // ---- Internal: per-rule progress flow ----

    private fun progressFlow(goal: Goal): Flow<Double> =
        when (val rule = ContributionRule.decode(goal.contributionRule)) {
            ContributionRule.AllSavingsDeposits ->
                transactionDao.observeSavingsContributionsSince(goal.createdAt)
            is ContributionRule.ToRecipient ->
                transactionDao.observeRecipientContributionsSince(rule.recipientName, goal.createdAt)
            ContributionRule.Manual ->
                goalDao.observeManualProgress(goal.id).map { it?.contributedAmount ?: 0.0 }
        }

    /**
     * The progress→status mapping.
     *
     * - `Completed` when target reached.
     * - For dated goals: project expected progress at "now" given a linear
     *   pace from createdAt → targetDate. Within ±10% = OnTrack, otherwise
     *   Behind / Ahead.
     * - For undated goals: just OnTrack until target is reached. We don't
     *   have signal to call it "Behind".
     */
    private fun Goal.toWithProgress(currentAmount: Double): GoalWithProgress {
        val pct = (currentAmount / targetAmount).coerceAtLeast(0.0)
        val completed = pct >= 1.0
        val now = System.currentTimeMillis()

        val eta = if (completed || currentAmount <= 0.0) null
        else {
            val elapsed = (now - createdAt).coerceAtLeast(DAY_MS)
            val rate = currentAmount / elapsed.toDouble()
            if (rate <= 0.0) null
            else {
                val remaining = targetAmount - currentAmount
                val msToTarget = (remaining / rate).roundToLong()
                now + msToTarget
            }
        }

        val status = when {
            completed -> GoalStatus.Completed
            targetDate == null -> GoalStatus.OnTrack
            else -> {
                val totalSpan = (targetDate - createdAt).coerceAtLeast(DAY_MS)
                val elapsed = (now - createdAt).coerceAtLeast(0L)
                val expectedPct = (elapsed.toDouble() / totalSpan.toDouble()).coerceIn(0.0, 1.0)
                val ratio = pct / expectedPct.coerceAtLeast(0.0001)
                when {
                    ratio < 0.9 -> GoalStatus.Behind
                    ratio > 1.1 -> GoalStatus.Ahead
                    else -> GoalStatus.OnTrack
                }
            }
        }

        return GoalWithProgress(
            goal = this,
            currentAmount = currentAmount,
            percent = pct.toFloat(),
            eta = eta,
            status = status,
        )
    }

    companion object {
        const val DAY_MS = 86_400_000L

        /** Used by SmsReceiver for auto-attribution on insert. */
        fun matches(rule: ContributionRule, type: TransactionType, recipient: String?): Boolean =
            when (rule) {
                ContributionRule.AllSavingsDeposits ->
                    type == TransactionType.MSHWARI || type == TransactionType.KCB_MPESA
                is ContributionRule.ToRecipient ->
                    recipient?.contains(rule.recipientName, ignoreCase = true) == true
                ContributionRule.Manual -> false
            }
    }
}
