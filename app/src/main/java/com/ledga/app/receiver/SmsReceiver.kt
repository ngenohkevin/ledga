package com.ledga.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import com.ledga.app.data.parser.MpesaSmsParser
import com.ledga.app.data.parser.ParseResult
import com.ledga.app.data.repository.AccountsRepository
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

    @Inject
    lateinit var accountsRepository: AccountsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Group message parts by sender (multi-part SMS).
                // SubscriptionId is per-message but identical across parts of the
                // same SMS — keep the first valid one we see per sender.
                val partsBySender = messages.groupBy { it.displayOriginatingAddress }

                // Some OEMs ship the broadcast without the subscription extra;
                // resolveSubscriptionId falls back to the sole active SIM.
                val subscriptionId =
                    accountsRepository.resolveSubscriptionId(subscriptionIdFromIntent(intent))

                for ((sender, parts) in partsBySender) {
                    if (!MpesaSmsParser.isMpesaMessage(sender ?: "")) continue

                    val body = parts.joinToString("") { it.displayMessageBody }
                    val account = accountsRepository.getOrCreateForSubscription(subscriptionId)

                    when (val result = MpesaSmsParser.parse(body, System.currentTimeMillis())) {
                        is ParseResult.Success -> {
                            transactionRepository.insertTransaction(
                                parsed = result.transaction,
                                accountId = account?.id,
                            )
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
                            // Filtered (balance check etc) — quietly drop.
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Reads the subscription id off the SMS_RECEIVED intent.
     *
     * Android writes the subscription as an extra with key "subscription"
     * (older builds) or [SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX]
     * (modern). We check both so we work on the broad device population
     * Ledga targets (SDK 26+).
     */
    private fun subscriptionIdFromIntent(intent: Intent): Int {
        val invalid = SubscriptionManager.INVALID_SUBSCRIPTION_ID
        val modern = intent.getIntExtra(
            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
            invalid
        )
        if (modern != invalid) return modern
        return intent.getIntExtra("subscription", invalid)
    }
}
