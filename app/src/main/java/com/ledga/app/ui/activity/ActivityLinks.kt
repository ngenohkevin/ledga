package com.ledga.app.ui.activity

import com.ledga.app.data.derive.TransactionFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Where another screen wants Activity to open (R61). */
sealed interface ActivityLink {
    /** Transactions narrowed to [filter]; [focusSearch] puts the cursor in the search field (Home's search button). */
    data class Transactions(val filter: TransactionFilter = TransactionFilter(), val focusSearch: Boolean = false) : ActivityLink

    /** Spending, at the current month (Home's spending card, R57). */
    data object Spending : ActivityLink
}

/**
 * The hand-off from Home or Tracker detail to Activity (R61). Activity's route stays a plain tab (one copy, its state
 * kept), and its ViewModel may not exist yet when the hop starts, so the newest request waits here until Activity
 * takes it, once.
 */
@Singleton
class ActivityLinks @Inject constructor() {
    private val pending = MutableStateFlow<ActivityLink?>(null)
    val requests: StateFlow<ActivityLink?> = pending

    private val spendingOpens = MutableStateFlow(0)

    /** Counts Spending hops: Spending's own ViewModel returns to the current month on each (R57). */
    val spendingHops: StateFlow<Int> = spendingOpens

    fun open(link: ActivityLink) {
        if (link == ActivityLink.Spending) spendingOpens.update { it + 1 }
        pending.value = link
    }

    /** Activity has applied [link]; a newer request stays. */
    fun taken(link: ActivityLink) {
        pending.compareAndSet(link, null)
    }
}
