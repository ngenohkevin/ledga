package com.ledga.app.data.room

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ledga.core.derive.Derivation
import com.ledga.core.derive.RuleEngine
import com.ledga.core.model.Categories
import com.ledga.core.parse.MpesaParser
import java.time.Instant

/** Seed SQL shared by a fresh install ([SeedOnCreate]) and MIGRATION_5_6. Idempotent (INSERT OR IGNORE / REPLACE). */
object V6Seed {
    fun categories(db: SupportSQLiteDatabase) = Categories.SEED.forEach { c ->
        db.execSQL(
            "INSERT OR IGNORE INTO `categories` (`key`, `name`, `groupKey`, `icon3d`, `color`, `colorDark`, `tracked`, `sortOrder`, `origin`, `archived`) " +
                "VALUES (?, ?, ?, ?, NULL, NULL, ?, ?, 'SYSTEM', 0)",
            arrayOf<Any?>(c.key, c.name, c.group.name, c.icon3d, if (c.tracked) 1 else 0, c.sortOrder),
        )
    }

    fun systemRules(db: SupportSQLiteDatabase, now: Instant) = RuleEngine.systemRules(now).forEach { r ->
        db.execSQL(
            "INSERT OR IGNORE INTO `rules` (`id`, `field`, `pattern`, `action`, `categoryKey`, `origin`, `priority`, `createdAt`, `enabled`) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)",
            arrayOf<Any?>(r.id, r.field.name, r.pattern, r.action.name, r.categoryKey, r.origin.name, r.priority, r.createdAt.toEpochMilli()),
        )
    }

    /** 0/0 after a migration forces a rebuild; the current versions on a fresh (empty) install. */
    fun versions(db: SupportSQLiteDatabase, parserVersion: Int, derivationVersion: Int) {
        db.execSQL("INSERT OR REPLACE INTO `meta` (`key`, `value`) VALUES (?, ?)", arrayOf<Any?>(MetaKeys.PARSER_VERSION, parserVersion.toString()))
        db.execSQL("INSERT OR REPLACE INTO `meta` (`key`, `value`) VALUES (?, ?)", arrayOf<Any?>(MetaKeys.DERIVATION_VERSION, derivationVersion.toString()))
    }
}

/** Fresh installs only (Room never calls onCreate after a migration). Synchronous: no second database instance. */
object SeedOnCreate : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        V6Seed.categories(db)
        V6Seed.systemRules(db, Instant.now())
        V6Seed.versions(db, MpesaParser.VERSION, Derivation.VERSION)
    }
}
