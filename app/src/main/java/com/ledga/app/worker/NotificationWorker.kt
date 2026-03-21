package com.ledga.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ledga.app.data.db.dao.TransactionDao
import com.ledga.app.util.CurrencyFormatter
import com.ledga.app.util.DateUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class DailySummaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val transactionDao: TransactionDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val start = DateUtils.getStartOfDay()
        val end = System.currentTimeMillis()

        val totalSpent = transactionDao.getTotalSpending(start, end).first()
        val totalFees = transactionDao.getTotalFees(start, end).first()

        if (totalSpent > 0) {
            val body = buildString {
                append("You spent ${CurrencyFormatter.formatKsh(totalSpent)} today")
                if (totalFees > 0) {
                    append(" (${CurrencyFormatter.formatKsh(totalFees)} in fees)")
                }
                append(".")
            }

            NotificationHelper.showNotification(
                context = applicationContext,
                channelId = NotificationHelper.CHANNEL_SUMMARIES,
                notificationId = 1001,
                title = "Daily Spending Summary",
                body = body
            )
        }

        return Result.success()
    }
}

@HiltWorker
class WeeklySummaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val transactionDao: TransactionDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val start = DateUtils.getStartOfWeek()
        val end = System.currentTimeMillis()

        val totalSpent = transactionDao.getTotalSpending(start, end).first()
        val totalFees = transactionDao.getTotalFees(start, end).first()

        if (totalSpent > 0) {
            val body = buildString {
                append("This week you spent ${CurrencyFormatter.formatKsh(totalSpent)}")
                if (totalFees > 0) {
                    append(" (${CurrencyFormatter.formatKsh(totalFees)} in fees)")
                }
                append(".")
            }

            NotificationHelper.showNotification(
                context = applicationContext,
                channelId = NotificationHelper.CHANNEL_SUMMARIES,
                notificationId = 1002,
                title = "Weekly Spending Summary",
                body = body
            )
        }

        return Result.success()
    }
}
