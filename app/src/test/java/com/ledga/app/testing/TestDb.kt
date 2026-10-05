package com.ledga.app.testing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ledga.app.data.room.LedgaDatabase

object TestDb {
    val context: Context get() = ApplicationProvider.getApplicationContext()

    /** A fresh, seeded in-memory v6 database. */
    fun inMemory(): LedgaDatabase = LedgaDatabase.inMemory(context).allowMainThreadQueries().build()
}
