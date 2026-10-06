package com.ledga.app.ui.home

import kotlin.test.assertEquals
import org.junit.Test

/** Review Focus #4: after Hide, nothing covers Home's Undo snackbar. */
class HomeSheetsTest {
    @Test
    fun `hiding a payment opened from the Fuliza sheet closes that sheet too`() {
        assertEquals(HomeSheets(), HomeSheets(payment = "TJK4AB12EA", fuliza = true).afterHide())
    }
}
