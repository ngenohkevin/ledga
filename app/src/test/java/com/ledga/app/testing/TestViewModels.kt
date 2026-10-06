package com.ledga.app.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * The ViewModels a test made, stopped before it closes its database. A query a ViewModel still has running when
 * `db.close()` lands fails on Room's executor, and that failure surfaces in a later test (`UncaughtExceptionsBeforeTest`).
 */
class TestViewModels {
    private val all = mutableListOf<ViewModel>()

    fun <T : ViewModel> track(vm: T): T = vm.also { all += it }

    /** Cancels every tracked ViewModel's work and waits until it has stopped. */
    fun stopAll() = runBlocking {
        withTimeout(10_000) { all.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() } }
    }
}
