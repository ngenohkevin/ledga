package com.ledga.app.worker

import android.content.Context
import com.ledga.app.data.db.dao.BudgetDao
import com.ledga.app.data.db.dao.TransactionDao
import com.ledga.app.data.parser.ParsedTransaction
import com.ledga.app.util.CurrencyFormatter
import com.ledga.app.util.DateUtils
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionAlerts @Inject constructor(
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao
) {
    suspend fun checkAlerts(
        context: Context,
        transaction: ParsedTransaction,
        largeTransactionThreshold: Double,
        largeTransactionEnabled: Boolean,
        budgetAlertsEnabled: Boolean
    ) {
        // Large transaction alert
        if (largeTransactionEnabled && transaction.amount >= largeTransactionThreshold) {
            val name = transaction.recipientName ?: transaction.type.name.replace("_", " ")
            NotificationHelper.showNotification(
                context = context,
                channelId = NotificationHelper.CHANNEL_LARGE_TXN,
                notificationId = 2000 + (transaction.transactionCode.hashCode() and 0xFFFF),
                title = "Large Transaction",
                body = "${CurrencyFormatter.formatKsh(transaction.amount)} to $name"
            )
        }

        // Budget alerts
        if (budgetAlertsEnabled) {
            checkBudgetAlerts(context)
        }
    }

    private suspend fun checkBudgetAlerts(context: Context) {
        val start = DateUtils.getStartOfMonth()
        val end = System.currentTimeMillis()
        val totalSpent = transactionDao.getTotalSpending(start, end).first()

        val budgets = budgetDao.getActiveBudgets().first()
        val overallBudget = budgets.find { it.categoryId == null } ?: return

        val percentage = ((totalSpent / overallBudget.monthlyLimit) * 100).toInt()

        when {
            percentage >= 100 -> {
                NotificationHelper.showNotification(
                    context = context,
                    channelId = NotificationHelper.CHANNEL_BUDGET,
                    notificationId = 3001,
                    title = "Budget Exceeded!",
                    body = "You've spent ${CurrencyFormatter.formatKsh(totalSpent)} — ${percentage}% of your ${CurrencyFormatter.formatKsh(overallBudget.monthlyLimit)} monthly budget."
                )
            }
            percentage >= 80 -> {
                NotificationHelper.showNotification(
                    context = context,
                    channelId = NotificationHelper.CHANNEL_BUDGET,
                    notificationId = 3002,
                    title = "Budget Warning",
                    body = "You've used ${percentage}% of your monthly budget (${CurrencyFormatter.formatKsh(totalSpent)} of ${CurrencyFormatter.formatKsh(overallBudget.monthlyLimit)})."
                )
            }
        }
    }
}
