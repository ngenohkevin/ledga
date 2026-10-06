package com.ledga.app.ui.tx

import org.junit.Test
import kotlin.test.assertEquals

/** Spec §10.4: Hide comes with Undo, and Undo's snackbar is on the screen, under any sheet still open. */
class OpenSheetsTest {
    @Test
    fun `hiding a payment opened from a person closes the person sheet too, so Undo shows`() {
        assertEquals(OpenSheets(), OpenSheets(payment = "TJK4AB12FA", person = "JANE TESTER|0712111").afterHide())
    }

    @Test
    fun `hiding a payment opened from the list closes only its sheet`() {
        assertEquals(OpenSheets(), OpenSheets(payment = "TJK4AB12FA").afterHide())
    }
}
