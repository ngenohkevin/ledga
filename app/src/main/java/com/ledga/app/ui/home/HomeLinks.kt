package com.ledga.app.ui.home

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** R100: a Fuliza reminder's tap asks Home for its Fuliza sheet; Home's screen takes it while it is shown, once. */
@Singleton
class HomeLinks @Inject constructor() {
    private val fuliza = MutableStateFlow(false)
    val fulizaAsked: StateFlow<Boolean> = fuliza

    fun openFuliza() {
        fuliza.value = true
    }

    fun fulizaShown() {
        fuliza.value = false
    }
}
