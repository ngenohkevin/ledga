package com.ledga.app.ui.activity

import androidx.paging.PagingData
import androidx.paging.insertSeparators
import androidx.paging.map
import com.ledga.app.data.room.TxRow
import com.ledga.app.ui.design.format.DateLabels
import java.time.LocalDate

/**
 * One item of the paged Transactions list (R40). A day card is its [Day] header (Top), its [Tx] rows (Middle) and a
 * bottom cap, which is either the next [Day] ([Day.closesPrevious]) or [End]. Paging can't know a day's last row
 * until the next page arrives, so the cap is an item of its own.
 */
sealed interface ActivityItem {
    val key: String

    data class Day(val day: LocalDate, val closesPrevious: Boolean) : ActivityItem {
        override val key: String = "day-$day"
    }

    /** [load] numbers the filter and search the list was loaded for: a new one starts at the top (final review I1). */
    data class Tx(val row: TxRow, val load: Int = 0) : ActivityItem {
        override val key: String get() = row.code
    }

    data object End : ActivityItem {
        override val key: String = "end"
    }
}

/**
 * Day headers by Nairobi date (spec §6.4): before the first row, between days, and an end cap after the last. [load] is
 * stamped on every row ([ActivityItem.Tx.load]).
 */
fun PagingData<TxRow>.toActivityItems(load: Int = 0): PagingData<ActivityItem> =
    map<TxRow, ActivityItem> { ActivityItem.Tx(it, load) }.insertSeparators { before, after ->
        val b = (before as? ActivityItem.Tx)?.row
        val a = (after as? ActivityItem.Tx)?.row
        val dayAfter = a?.let { DateLabels.nairobiDate(it.occurredAt) }
        when {
            dayAfter == null -> if (b == null) null else ActivityItem.End
            b == null -> ActivityItem.Day(dayAfter, closesPrevious = false)
            dayAfter != DateLabels.nairobiDate(b.occurredAt) -> ActivityItem.Day(dayAfter, closesPrevious = true)
            else -> null
        }
    }
