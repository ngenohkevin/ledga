package com.ledga.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.ledga.app.time.LiveClock
import com.ledga.app.ui.app.AppViewModel
import com.ledga.app.ui.app.LedgaRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val app: AppViewModel by viewModels()

    /** R34: a resume re-reads the date, so TODAY and this month move on after the phone slept through midnight. */
    @Inject lateinit var liveClock: LiveClock

    override fun onCreate(savedInstanceState: Bundle?) {
        // The splash stays until the database is open and the appearance is known (no light flash in dark mode).
        installSplashScreen().setKeepOnScreenCondition { !app.ready.value }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LedgaRoot(app) }
    }

    override fun onResume() {
        super.onResume()
        liveClock.onResume()
    }
}
