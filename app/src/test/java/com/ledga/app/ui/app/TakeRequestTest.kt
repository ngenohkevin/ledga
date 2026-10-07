package com.ledga.app.ui.app

import androidx.compose.ui.test.junit4.createComposeRule
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R100: Home's screen takes a Fuliza reminder's request while it is shown, once. */
@RunWith(RobolectricTestRunner::class)
class TakeRequestTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `the screen showing takes a request, once`() {
        val requested = MutableStateFlow(false)
        var taken = 0
        compose.setContent { TakeRequest(requested) { taken++; requested.value = false } }
        compose.runOnIdle { requested.value = true }
        compose.waitForIdle()
        assertEquals(1, taken)
    }
}
