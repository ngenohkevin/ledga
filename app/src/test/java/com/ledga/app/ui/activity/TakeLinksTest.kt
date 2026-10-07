package com.ledga.app.ui.activity

import androidx.compose.ui.test.junit4.createComposeRule
import com.ledga.app.data.derive.TransactionFilter
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/** R93: Activity's screen takes each hand-off while it is shown, once. */
@RunWith(RobolectricTestRunner::class)
class TakeLinksTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `the screen showing takes a hand-off, once`() {
        val pending = MutableStateFlow<ActivityLink?>(null)
        val taken = mutableListOf<ActivityLink>()
        compose.setContent { TakeLinks(pending) { link -> taken += link; pending.value = null } }
        val link = ActivityLink.Transactions(TransactionFilter(query = "kplc"))
        compose.runOnIdle { pending.value = link }
        compose.waitForIdle()
        assertEquals(listOf<ActivityLink>(link), taken)
    }
}
