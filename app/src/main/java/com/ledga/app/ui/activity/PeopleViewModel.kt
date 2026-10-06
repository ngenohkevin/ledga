package com.ledga.app.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.data.derive.PeopleDirection
import com.ledga.app.time.LiveClock
import com.ledga.app.ui.design.format.NameFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** One person in People (R42), named as people write it (R44). [key] is their `counterpartyKey`. */
data class PersonRowUi(val key: String, val name: String, val phone: String?, val count: Int, val totalCents: Long, val lastAt: Instant)

/** Activity › People (spec §10.4): sent to / received from, a search, a minimum total. */
data class PeopleUi(
    val loaded: Boolean = false,
    val direction: PeopleDirection = PeopleDirection.SENT,
    val query: String = "",
    val minCents: Long = 0,
    /** The biggest total: the minimum slider's end. */
    val maxCents: Long = 0,
    val rows: List<PersonRowUi> = emptyList(),
    val today: LocalDate? = null,
)

@HiltViewModel
class PeopleViewModel @Inject constructor(private val ledger: LedgerQueries, live: LiveClock) : ViewModel() {
    private val direction = MutableStateFlow(PeopleDirection.SENT)
    private val query = MutableStateFlow("")
    private val minCents = MutableStateFlow(0L)

    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<PeopleUi> = combine(
        direction.flatMapLatest { d -> ledger.people(d).map { d to it } },
        query,
        minCents,
        live.today,
    ) { (d, people), q, min, today ->
        val all = people.map {
            PersonRowUi(it.counterpartyKey, it.name?.let(NameFormat::display) ?: it.phone ?: "Unknown", it.phone, it.count, it.totalCents, it.lastAt)
        }
        val max = all.maxOfOrNull { it.totalCents } ?: 0L
        val floor = min.coerceIn(0L, max)
        PeopleUi(true, d, q, floor, max, all.filter { it.totalCents >= floor && it.matches(q) }, today)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeopleUi())

    /** Each direction has its own totals, so its own minimum: switching starts from "any total". */
    fun setDirection(d: PeopleDirection) {
        direction.value = d
        minCents.value = 0
    }

    fun setQuery(text: String) {
        query.value = text
    }

    fun setMinimum(cents: Long) {
        minCents.value = cents.coerceAtLeast(0)
    }
}

/** A name (as shown) containing the search, or a phone holding at least three of its digits. */
internal fun PersonRowUi.matches(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    val digits = q.filter(Char::isDigit)
    return name.contains(q, ignoreCase = true) || (digits.length >= 3 && phone?.filter(Char::isDigit)?.contains(digits) == true)
}
