package com.ledga.app.testing

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Builds a database exactly as Room created it at an old schema version, from the exported JSON on disk
 * (unit tests run with the module directory as the working directory). Replaces MigrationTestHelper,
 * whose asset lookup Robolectric does not reliably serve (refinement R14).
 */
object SchemaFixture {
    val DIR = File("schemas/com.ledga.app.data.room.LedgaDatabase")
    const val TABLE = "\${TABLE_NAME}"
    const val VIEW = "\${VIEW_NAME}"

    fun json(version: Int): JsonObject =
        Json.parseToJsonElement(File(DIR, "$version.json").readText()).jsonObject["database"]!!.jsonObject

    /** CREATE TABLE / INDEX / VIEW statements plus Room's setup queries (room_master_table + identity hash). */
    fun statements(version: Int): List<String> {
        val db = json(version)
        val out = mutableListOf<String>()
        db["entities"]!!.jsonArray.forEach { e ->
            val o = e.jsonObject
            val table = o["tableName"]!!.jsonPrimitive.content
            out += o["createSql"]!!.jsonPrimitive.content.replace(TABLE, table)
            o["indices"]?.jsonArray?.forEach { i -> out += i.jsonObject["createSql"]!!.jsonPrimitive.content.replace(TABLE, table) }
        }
        db["views"]?.jsonArray?.forEach { v ->
            val o = v.jsonObject
            out += o["createSql"]!!.jsonPrimitive.content.replace(VIEW, o["viewName"]!!.jsonPrimitive.content)
        }
        db["setupQueries"]!!.jsonArray.forEach { out += it.jsonPrimitive.content }
        return out
    }

    /** A fresh file database [name] at [version]. Write rows through `writableDatabase`, then close it. */
    fun create(context: Context, name: String, version: Int): SupportSQLiteOpenHelper {
        context.deleteDatabase(name)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) = statements(version).forEach(db::execSQL)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                    error("fixtures are created, never upgraded")
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
    }
}
