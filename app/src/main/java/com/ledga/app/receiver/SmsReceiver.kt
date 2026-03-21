package com.ledga.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.ledga.app.data.parser.MpesaSmsParser
import com.ledga.app.data.parser.ParseResult
import com.ledga.app.data.repository.SettingsRepository
import com.ledga.app.data.repository.TransactionRepository
import com.ledga.app.worker.TransactionAlerts
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var transactionRepository: TransactionRepository

    @Inject
    lateinit var transactionAlerts: TransactionAlerts

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Group message parts by sender (multi-part SMS)
                val fullMessages = messages
                    .groupBy { it.displayOriginatingAddress }
                    .mapValues { (_, parts) -> parts.joinToString("") { it.displayMessageBody } }

                for ((sender, body) in fullMessages) {
                    if (!MpesaSmsParser.isMpesaMessage(sender ?: "")) continue

                    when (val result = MpesaSmsParser.parse(body, System.currentTimeMillis())) {
                        is ParseResult.Success -> {
                            transactionRepository.insertTransaction(result.transaction)
                            // Check for alerts
                            val largeTxnEnabled = settingsRepository.getLargeTransactionAlertEnabled().first()
                            val largeTxnThreshold = settingsRepository.getLargeTransactionThreshold().first()
                            val budgetEnabled = settingsRepository.getBudgetAlertsEnabled().first()
                            transactionAlerts.checkAlerts(
                                context = context,
                                transaction = result.transaction,
                                largeTransactionThreshold = largeTxnThreshold,
                                largeTransactionEnabled = largeTxnEnabled,
                                budgetAlertsEnabled = budgetEnabled
                            )
                        }
                        is ParseResult.Failure -> {
                            // Store as UNKNOWN for later review
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
