package com.ledga.app.ui.home

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A Fuliza reminder's tap (R100): Home's Fuliza sheet, on the reminder's line ([lineId]; null = not on a line). */
data class FulizaRequest(val lineId: Long?)

/** R100: a Fuliza reminder's tap asks Home for its Fuliza sheet; Home's screen takes it while it is shown, once. */
@Singleton
class HomeLinks @Inject constructor() {
    private val fuliza = MutableStateFlow<FulizaRequest?>(null)
    val fulizaAsked: StateFlow<FulizaRequest?> = fuliza

    fun openFuliza(lineId: Long?) {
        fuliza.value = FulizaRequest(lineId)
    }

    fun fulizaShown() {
        fuliza.value = null
    }
}
