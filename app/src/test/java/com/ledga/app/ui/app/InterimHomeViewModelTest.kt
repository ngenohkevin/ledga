package com.ledga.app.ui.app

import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.room.SmsSource
import com.ledga.app.testing.FakeBackgroundWork
import com.ledga.app.testing.MainDispatcherRule
import com.ledga.app.testing.Sms
import com.ledga.app.testing.TestDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class InterimHomeViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val db = TestDb.inMemory()
    private val work = FakeBackgroundWork()
    private var granted = true
    private val clock = Clock.fixed(Instant.parse("2026-03-25T09:00:00Z"), ZoneOffset.UTC)

    private fun vm() = InterimHomeViewModel(db, LedgerQueries(db), work, { granted }, clock)

    @Test
    fun `the interim home shows how much history arrived and this month's spending`() = runTest {
        SmsIngestor(db, Deriver(db)).ingestAll(
            listOf(
                RawSms("MPESA", Sms.SEND, Sms.at("2026-03-21T10:30:30Z"), null, null, SmsSource.INBOX),
                RawSms("MPESA", Sms.KPLC, Sms.at("2026-03-21T12:00:30Z"), null, null, SmsSource.INBOX),
            ),
        )
        val s = vm().state.first { it.count == 2 }
        assertEquals(150_700L, s.spentThisMonthCents) // 500 + 7 fee + 1,000
        assertEquals(Instant.parse("2026-03-21T10:30:00Z"), s.first)
        assertEquals(Instant.parse("2026-03-21T12:00:00Z"), s.last)
    }

    @Test
    fun `allowing SMS from Home starts the full import`() = runTest {
        granted = false
        val vm = vm()
        vm.state.first { !it.smsGranted }
        granted = true
        vm.onSmsGranted()
        vm.state.first { it.smsGranted }
        assertEquals(listOf("importInbox"), work.calls)
    }
}
