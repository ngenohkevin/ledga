package com.ledga.app.ui.app

import android.util.Log
import com.ledga.app.data.derive.DateFilter
import com.ledga.app.data.derive.TransactionFilter
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.notify.NotificationTap
import com.ledga.app.notify.OpenedNotification
import com.ledga.app.ui.activity.ActivityLink
import com.ledga.app.ui.activity.ActivityLinks
import com.ledga.app.ui.home.HomeLinks
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Where a tapped notification sends Ledga (R100). */
sealed interface OpenDestination {
    /** Alerts, with [code]'s sheet over it while the payment shows (null: Alerts alone). */
    data class Alerts(val code: String?) : OpenDestination

    /** Home, which takes its Fuliza sheet from [HomeLinks]. */
    data object Home : OpenDestination

    /** Activity, which takes the waiting Transactions link from [ActivityLinks]. */
    data object Activity : OpenDestination
}

/**
 * R100: `MainActivity` hands a tapped notification here. The alert is marked read, Home or Activity gets its hand-off,
 * and the destination waits until `LedgaNavHost` has gone there ([taken]).
 */
@Singleton
class NotificationOpens @Inject constructor(
    private val db: LedgaDatabase,
    private val clock: Clock,
    private val activity: ActivityLinks,
    private val home: HomeLinks,
) {
    private val pending = MutableStateFlow<OpenDestination?>(null)
    val destination: StateFlow<OpenDestination?> = pending

    suspend fun open(opened: OpenedNotification) {
        readSafely { db.alertsDao().markRead(listOf(opened.alertKey), clock.instant()) }
        pending.value = when (val tap = opened.tap) {
            is NotificationTap.Payment -> OpenDestination.Alerts(tap.code.takeIf { readSafely { db.transactionsDao().get(it) }?.isHidden == false })
            NotificationTap.Fuliza -> {
                home.openFuliza()
                OpenDestination.Home
            }
            is NotificationTap.Spending -> {
                // Every flow: the day header's Out is then exactly the summary's Spent (R100).
                activity.open(ActivityLink.Transactions(TransactionFilter(dates = DateFilter.Custom(tap.from, tap.to))))
                OpenDestination.Activity
            }
        }
    }

    fun taken(destination: OpenDestination) {
        pending.compareAndSet(destination, null)
    }

    /**
     * A database that can't be read (the recovery screen is up) must not crash a tap (final review): null instead. Room
     * reports a closed database as a cancellation, so only this coroutine's own cancellation is passed on.
     */
    private suspend fun <T> readSafely(read: suspend () -> T): T? = try {
        read()
    } catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()
        Log.w(TAG, "a notification's tap couldn't read the database", e)
        null
    } catch (e: Exception) {
        Log.w(TAG, "a notification's tap couldn't read the database", e)
        null
    }

    private companion object {
        const val TAG = "Ledga"
    }
}
