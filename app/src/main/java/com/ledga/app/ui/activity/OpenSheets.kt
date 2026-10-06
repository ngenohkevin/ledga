package com.ledga.app.ui.activity

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

/**
 * The sheets Activity has open over its screen (spec §10.4): a payment ([payment], its code), the category picker
 * over it ([picker]) and a person ([person], their `counterpartyKey`). The screen keeps only these keys; each sheet's
 * host owns its ViewModel.
 */
internal data class OpenSheets(val payment: String? = null, val picker: String? = null, val person: String? = null) {
    /**
     * After Hide (spec §10.4: with Undo). Every M3 sheet is its own dialog window above the screen, and the Undo
     * snackbar lives on the screen, so the person sheet under the payment's sheet closes too: left open, it hides Undo.
     */
    fun afterHide(): OpenSheets = copy(payment = null, person = null)

    companion object {
        val Saver: Saver<OpenSheets, Any> = listSaver(
            save = { listOf(it.payment, it.picker, it.person) },
            restore = { OpenSheets(it[0], it[1], it[2]) },
        )
    }
}
