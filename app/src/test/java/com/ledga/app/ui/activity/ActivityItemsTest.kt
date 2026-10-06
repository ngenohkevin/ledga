package com.ledga.app.ui.activity

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import com.ledga.app.testing.txRow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals

/** R40: a day card is its header, its rows and an end cap; days are Nairobi days. */
class ActivityItemsTest {
    private val complete = LoadStates(LoadState.NotLoading(false), LoadState.NotLoading(true), LoadState.NotLoading(true))

    @Test
    fun `payments fall into Nairobi days, newest first, each day a card with a header and an end`() = runTest {
        val midnight = txRow(code = "TJK4AB12HB", at = Instant.parse("2026-03-21T21:00:00Z")) // 00:00 on 22 March, Nairobi
        val lateNight = txRow(code = "TJK4AB12HA", at = Instant.parse("2026-03-21T20:59:00Z")) // 23:59 on 21 March
        val afternoon = txRow(code = "TJK4AB12FA", at = Instant.parse("2026-03-21T12:00:00Z"))
        val items = flowOf(PagingData.from(listOf(midnight, lateNight, afternoon), complete).toActivityItems()).asSnapshot()
        assertEquals(
            listOf(
                ActivityItem.Day(LocalDate.parse("2026-03-22"), closesPrevious = false),
                ActivityItem.Tx(midnight),
                ActivityItem.Day(LocalDate.parse("2026-03-21"), closesPrevious = true),
                ActivityItem.Tx(lateNight),
                ActivityItem.Tx(afternoon),
                ActivityItem.End,
            ),
            items,
        )
    }
}
