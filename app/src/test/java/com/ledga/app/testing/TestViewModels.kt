package com.ledga.app.testing

import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.robolectric.Shadows

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

    /**
     * [stopAll] for route tests, where `viewModelScope` runs on Robolectric's main looper rather than a test dispatcher:
     * blocking that thread to wait would stop the cancellation itself, so this idles the looper until the work ends.
     */
    fun stopAllOnMainLooper() {
        val jobs = all.map { it.viewModelScope.coroutineContext.job.apply { cancel() } }
        repeat(500) {
            if (jobs.all { it.isCompleted }) return
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        error("ViewModel work still running after cancel")
    }
}
