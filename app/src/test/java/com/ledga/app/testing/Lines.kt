package com.ledga.app.testing

import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.lines.SelectedLine
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.LineRow
import com.ledga.app.data.settings.SettingsStore
import java.time.Instant

/** Two synthetic lines (R47): "Personal ··11" and "Business ··78". */
val PERSONAL = LineRow(id = 1, subscriptionId = 1, phoneNumber = "0712345111", displayName = "Personal", color = "#0E9F6E", isPrimary = true, createdAt = Instant.EPOCH)
val BUSINESS = LineRow(id = 2, subscriptionId = 2, phoneNumber = "0722345678", displayName = "Business", color = "#1E7FD8", isPrimary = false, createdAt = Instant.EPOCH)

suspend fun twoLines(db: LedgaDatabase) {
    db.linesDao().insert(PERSONAL)
    db.linesDao().insert(BUSINESS)
}

/** The line choice over [db]'s lines and an in-memory settings file. */
fun selectedLine(db: LedgaDatabase, prefs: FakePrefsStore = FakePrefsStore()) =
    SelectedLine(LinesRepository(db.linesDao(), FakeSims()), SettingsStore(prefs))
