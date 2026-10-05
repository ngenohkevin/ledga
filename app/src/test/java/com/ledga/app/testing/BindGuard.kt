package com.ledga.app.testing

import androidx.room.RoomDatabase
import java.util.concurrent.Executor

/**
 * Records the largest number of bound variables any statement used. Robolectric's SQLite allows 32,766, but API 26-29
 * devices allow only 999, so an unchunked `IN (:list)` passes every test and crashes on the phone. Attach this to a
 * builder and assert [max] <= [API26_LIMIT] after exercising a large batch.
 */
class BindGuard {
    @Volatile var max = 0
        private set

    @Volatile var maxSql = ""
        private set

    fun <T : RoomDatabase> attach(builder: RoomDatabase.Builder<T>): RoomDatabase.Builder<T> =
        builder.setQueryCallback(
            { sql, args ->
                if (args.size > max) {
                    max = args.size
                    maxSql = sql.take(120)
                }
            },
            Executor { it.run() },
        )

    companion object {
        const val API26_LIMIT = 999
    }
}
