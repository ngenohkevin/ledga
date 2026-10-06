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

    @Test
    fun `Send me the database shares the pre-v6 copy through Ledga's file provider`() {
        val copy = File(context.filesDir, "pre-v6/ledga.db").apply { parentFile!!.mkdirs(); writeText("copy") }
        val chooser = RecoveryShare.intent(context, copy)
        val send = assertNotNull(IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java))
        assertEquals(Intent.ACTION_SEND, send.action)
        val uri = assertNotNull(IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java))
        assertEquals("${context.packageName}.fileprovider", uri.authority)
        assertTrue(uri.path!!.contains("pre-v6"), uri.toString())
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}
