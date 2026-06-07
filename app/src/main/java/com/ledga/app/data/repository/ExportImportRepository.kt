package com.ledga.app.data.repository

import android.content.Context
import android.net.Uri
import com.ledga.app.data.db.dao.CategoryDao
import com.ledga.app.data.db.dao.TransactionDao
import com.ledga.app.data.db.entity.TransactionEntity
import com.ledga.app.data.parser.TransactionDirection
import com.ledga.app.data.parser.TransactionType
import com.ledga.app.util.CurrencyFormatter
import com.ledga.app.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ExportTransaction(
    val transactionCode: String,
    val type: String,
    val amount: Double,
    val transactionCost: Double,
    val recipientName: String?,
    val recipientPhone: String?,
    val accountNumber: String?,
    val destinationCountry: String?,
    val balance: Double,
    val direction: String,
    val categoryId: Long?,
    val fulizaAmount: Double?,
    val fulizaOutstanding: Double?,
    val fulizaLimit: Double? = null,
    val reversedTransactionCode: String?,
    val rawSms: String,
    val timestamp: Long
)

@Serializable
data class ExportData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val transactions: List<ExportTransaction>
)

data class ExportResult(val success: Boolean, val count: Int)
data class ImportFromFileResult(val total: Int, val imported: Int, val skipped: Int)

@Singleton
class ExportImportRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun exportToZip(context: Context, uri: Uri): ExportResult = withContext(Dispatchers.IO) {
        val transactions = transactionDao.getRecentTransactions(10000).first()
        val categories = categoryDao.getAllCategories().first()
        val categoryMap = categories.associateBy { it.id }

        context.contentResolver.openOutputStream(uri)?.use { os ->
            ZipOutputStream(os).use { zip ->
                // CSV
                zip.putNextEntry(ZipEntry("transactions.csv"))
                val csvHeader = "Date,Time,Code,Type,Amount,Fee,Recipient,Phone,Account,Balance,Category\n"
                zip.write(csvHeader.toByteArray())
                transactions.forEach { twc ->
                    val t = twc.transaction
                    val cat = categoryMap[t.categoryId]?.name ?: ""
                    val line = "${DateUtils.formatDate(t.timestamp)},${DateUtils.formatTime(t.timestamp)},${t.transactionCode},${t.type},${t.amount},${t.transactionCost},${csvEscape(t.recipientName ?: "")},${t.recipientPhone ?: ""},${t.accountNumber ?: ""},${t.balance},${csvEscape(cat)}\n"
                    zip.write(line.toByteArray())
                }
                zip.closeEntry()

                // JSON
                zip.putNextEntry(ZipEntry("data.json"))
                val exportData = ExportData(
                    transactions = transactions.map { it.transaction.toExport() }
                )
                zip.write(json.encodeToString(exportData).toByteArray())
                zip.closeEntry()
            }
        }

        ExportResult(success = true, count = transactions.size)
    }

    suspend fun importFromZip(context: Context, uri: Uri): ImportFromFileResult = withContext(Dispatchers.IO) {
        var total = 0
        var imported = 0
        var skipped = 0

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "data.json") {
                        val reader = BufferedReader(InputStreamReader(zip))
                        val content = reader.readText()
                        val exportData = json.decodeFromString<ExportData>(content)
                        total = exportData.transactions.size

                        exportData.transactions.forEach { et ->
                            val entity = et.toEntity()
                            val id = transactionDao.insert(entity)
                            if (id != -1L) imported++ else skipped++
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        ImportFromFileResult(total = total, imported = imported, skipped = skipped)
    }

    private fun TransactionEntity.toExport() = ExportTransaction(
        transactionCode = transactionCode, type = type.name, amount = amount,
        transactionCost = transactionCost, recipientName = recipientName,
        recipientPhone = recipientPhone, accountNumber = accountNumber,
        destinationCountry = destinationCountry, balance = balance,
        direction = direction.name, categoryId = categoryId,
        fulizaAmount = fulizaAmount, fulizaOutstanding = fulizaOutstanding,
        fulizaLimit = fulizaLimit,
        reversedTransactionCode = reversedTransactionCode, rawSms = rawSms,
        timestamp = timestamp
    )

    private fun ExportTransaction.toEntity() = TransactionEntity(
        transactionCode = transactionCode,
        type = try { TransactionType.valueOf(type) } catch (e: Exception) { TransactionType.UNKNOWN },
        amount = amount, transactionCost = transactionCost,
        recipientName = recipientName, recipientPhone = recipientPhone,
        accountNumber = accountNumber, destinationCountry = destinationCountry,
        balance = balance,
        direction = try { TransactionDirection.valueOf(direction) } catch (e: Exception) { TransactionDirection.OUTFLOW },
        categoryId = categoryId, fulizaAmount = fulizaAmount,
        fulizaOutstanding = fulizaOutstanding, fulizaLimit = fulizaLimit,
        reversedTransactionCode = reversedTransactionCode,
        rawSms = rawSms, timestamp = timestamp
    )

    private fun csvEscape(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
    }
}
