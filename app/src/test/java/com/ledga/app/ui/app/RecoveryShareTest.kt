package com.ledga.app.ui.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RecoveryShareTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun copy(name: String) = File(context.filesDir, "pre-v6/$name").apply { parentFile!!.mkdirs(); writeText(name) }

    /**
     * One test on purpose: FileProvider caches its roots per authority in a static map, and Robolectric gives each test
     * a new data directory, so a second FileProvider test in the same JVM would resolve against a stale root.
     */
    @Test
    fun `Send me the database shares the pre-v6 copy with its WAL through Ledga's file provider`() {
        val db = copy("ledga.db")
        fun shared(): Intent = assertNotNull(IntentCompat.getParcelableExtra(RecoveryShare.intent(context, db), Intent.EXTRA_INTENT, Intent::class.java))
        fun names(send: Intent) = IntentCompat.getParcelableArrayListExtra(send, Intent.EXTRA_STREAM, Uri::class.java)!!.map { it.lastPathSegment }

        assertEquals(listOf("ledga.db"), names(shared()), "a copy without a WAL is shared on its own")

        copy("ledga.db-wal")
        copy("ledga.db-shm")
        val send = shared()
        assertEquals(Intent.ACTION_SEND_MULTIPLE, send.action)
        assertEquals(listOf("ledga.db", "ledga.db-wal", "ledga.db-shm"), names(send))
        val uris = IntentCompat.getParcelableArrayListExtra(send, Intent.EXTRA_STREAM, Uri::class.java)!!
        assertTrue(uris.all { it.authority == "${context.packageName}.fileprovider" && it.path!!.contains("pre-v6") }, uris.toString())
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}
