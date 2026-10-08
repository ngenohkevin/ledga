package com.ledga.app.testing

import android.content.Context
import com.ledga.core.derive.LegacyAutoCategorizer
import java.io.File
import java.time.Instant
import java.util.zip.ZipFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** One row of v1.6's export (`data.json` → `transactions`): what the upgrade tests need. Real rows are never printed. */
data class V1ExportTx(
    val code: String,
    val type: String,
    val direction: String,
    val amount: Double,
    val categoryId: Long?,
    val recipientName: String?,
    val accountNumber: String?,
    val reversedCode: String?,
    val rawSms: String,
    val timestamp: Instant,
)

/**
 * v1.6's Export, a zip of `transactions.csv` + `data.json` or that `data.json` alone, for the opt-in upgrade tests
 * (R160). It has transactions only: no rules, custom category names, notes, car tags or lines.
 */
object V1Export {
    /** Any failure is rethrown without its cause: a parser's message can quote the file, and the file is real data. */
    fun read(file: File): List<V1ExportTx> {
        val text = try {
            if (file.name.endsWith(".zip", ignoreCase = true)) {
                ZipFile(file).use { zip ->
                    val entry = zip.getEntry("data.json") ?: throw IllegalStateException("the zip has no data.json")
                    zip.getInputStream(entry).bufferedReader().readText()
                }
            } else {
                file.readText()
            }
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("could not open the export (${e.javaClass.simpleName})")
        }
        return try {
            Json.parseToJsonElement(text).jsonObject["transactions"]!!.jsonArray.map { row(it.jsonObject) }
        } catch (e: Exception) {
            throw IllegalStateException("could not read the export's transactions (${e.javaClass.simpleName})")
        }
    }

    private fun row(o: JsonObject): V1ExportTx {
        fun s(key: String) = o[key]?.jsonPrimitive?.contentOrNull
        return V1ExportTx(
            code = s("transactionCode")!!,
            type = s("type")!!,
            direction = s("direction")!!,
            amount = o["amount"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            categoryId = o["categoryId"]?.jsonPrimitive?.longOrNull,
            recipientName = s("recipientName"),
            accountNumber = s("accountNumber"),
            reversedCode = s("reversedTransactionCode"),
            rawSms = s("rawSms")!!,
            timestamp = Instant.ofEpochMilli(o["timestamp"]!!.jsonPrimitive.longOrNull!!),
        )
    }

    /**
     * [rows] as a v1.6 (schema 5) database named [name]: v1's default categories and rules, a placeholder "Custom <id>"
     * for each custom category the rows use (the export doesn't carry their names), then the rows.
     */
    fun writeSchema5(context: Context, name: String, rows: List<V1ExportTx>) {
        SchemaFixture.create(context, name, 5).use { helper ->
            val db = helper.writableDatabase
            val w = LegacyDbWriter(db, 5)
            db.beginTransaction()
            try {
                w.defaultCategories()
                rows.mapNotNull { it.categoryId }.filter { it > LegacyAutoCategorizer.MY_ACCOUNTS }.toSortedSet()
                    .forEach { w.category(it, "Custom $it", isDefault = false) }
                w.defaultRules()
                rows.forEachIndexed { i, r ->
                    w.tx(
                        id = i + 1L, code = r.code, type = r.type, direction = r.direction, rawSms = r.rawSms, timestamp = r.timestamp,
                        categoryId = r.categoryId, recipientName = r.recipientName, accountNumber = r.accountNumber,
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }
}
