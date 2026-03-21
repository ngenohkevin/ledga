package com.ledga.app.data.repository

import android.content.ContentResolver
import android.net.Uri
import com.ledga.app.data.parser.MpesaSmsParser
import com.ledga.app.data.parser.ParseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ImportResult(
    val total: Int,
    val imported: Int,
    val failed: Int
)

@Singleton
class SmsImporter @Inject constructor(
    private val contentResolver: ContentResolver,
    private val transactionRepository: TransactionRepository
) {
    suspend fun importHistory(
        onProgress: (imported: Int, total: Int) -> Unit = { _, _ -> }
    ): ImportResult = withContext(Dispatchers.IO) {
        val smsList = readMpesaSms()
        val total = smsList.size
        var imported = 0
        var failed = 0

        smsList.forEachIndexed { index, sms ->
            val result = MpesaSmsParser.parse(sms.body, sms.date)
            when (result) {
                is ParseResult.Success -> {
                    val id = transactionRepository.insertTransaction(result.transaction)
                    if (id != -1L) imported++
                }
                is ParseResult.Failure -> failed++
            }
            onProgress(index + 1, total)
        }

        ImportResult(total = total, imported = imported, failed = failed)
    }

    private fun readMpesaSms(): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()
        val uri = Uri.parse("content://sms/inbox")
        val cursor = contentResolver.query(
            uri,
            arrayOf("body", "date", "address"),
            "address = ?",
            arrayOf("MPESA"),
            "date DESC"
        )

        cursor?.use {
            val bodyIndex = it.getColumnIndexOrThrow("body")
            val dateIndex = it.getColumnIndexOrThrow("date")
            while (it.moveToNext()) {
                messages.add(
                    SmsMessage(
                        body = it.getString(bodyIndex),
                        date = it.getLong(dateIndex)
                    )
                )
            }
        }

        return messages
    }

    private data class SmsMessage(val body: String, val date: Long)
}
