package com.ledga.app.testing

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * Android's SMS provider as different phones present it: [columns] decides which subscription column exists
 * (`sub_id`, `sim_id` or none), [denied] stands for a missing READ_SMS. Register with
 * `Robolectric.setupContentProvider(FakeSmsProvider::class.java, "sms")`.
 */
class FakeSmsProvider : ContentProvider() {
    companion object {
        var rows: List<Map<String, Any?>> = emptyList()
        var columns: Set<String> = setOf("address", "body", "date", "sub_id")
        var denied = false

        fun reset() {
            rows = emptyList()
            columns = setOf("address", "body", "date", "sub_id")
            denied = false
        }

        fun row(address: String, body: String, date: Long, sub: Int? = 1, sim: Int? = null): Map<String, Any?> =
            mapOf("address" to address, "body" to body, "date" to date, "sub_id" to sub, "sim_id" to sim)
    }

    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor {
        if (denied) throw SecurityException("READ_SMS not granted")
        val cols = projection!!
        cols.firstOrNull { it !in columns }?.let { throw IllegalArgumentException("no such column: $it") }
        val args = selectionArgs!!.toList()
        val since = if (selection!!.contains("date >")) args.last().toLong() else null
        val senders = if (since != null) args.dropLast(1) else args
        val cursor = MatrixCursor(cols)
        rows.filter { it["address"] in senders && (since == null || (it["date"] as Long) > since) }
            .sortedByDescending { it["date"] as Long }
            .forEach { r -> cursor.addRow(cols.map { r[it] }.toTypedArray()) }
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0
}
