package com.ledga.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ledga.app.testing.Sms
import com.ledga.core.parse.MpesaParser
import com.ledga.core.parse.ParseOutcome
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
class RobolectricSmokeTest {
    @Test
    fun `core parses inside an Android unit test`() {
        val outcome = MpesaParser.parse(Sms.SEND, Sms.at("2026-03-21T10:30:05Z"))
        assertIs<ParseOutcome.Parsed>(outcome)
        assertEquals("TJK4AB12FB", outcome.sms.code)
        assertNotNull(ApplicationProvider.getApplicationContext<Context>().filesDir)
    }
}
