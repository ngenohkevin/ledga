package com.ledga.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.ledga.app.notify.NotificationIntents
import com.ledga.app.notify.UpdateIntents
import com.ledga.app.time.LiveClock
import com.ledga.app.ui.app.AppViewModel
import com.ledga.app.ui.app.LedgaRoot
import com.ledga.app.ui.app.NotificationOpens
import com.ledga.app.work.BackgroundWork
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val app: AppViewModel by viewModels()

    /** R34: a resume re-reads the date, so TODAY and this month move on after the phone slept through midnight. */
    @Inject lateinit var liveClock: LiveClock

    /** R100: a tapped notification's screen. */
    @Inject lateinit var opens: NotificationOpens

    /** Spec §12.1, R130: a snapshot job when Ledga leaves the screen. */
    @Inject lateinit var work: BackgroundWork

    override fun onCreate(savedInstanceState: Bundle?) {
        // The splash stays until the database is open and the appearance is known (no light flash in dark mode).
        installSplashScreen().setKeepOnScreenCondition { !app.ready.value }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LedgaRoot(app) }
        if (savedInstanceState == null) openFrom(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFrom(intent)
    }

    /** R100: once; the extras are cleared, so a rotation or a restart never opens it again. */
    private fun openFrom(intent: Intent?) {
        if (UpdateIntents.read(intent)) {
            intent?.let(UpdateIntents::clear)
            opens.openUpdates()
            return
        }
        val opened = NotificationIntents.read(intent) ?: return
        intent?.let(NotificationIntents::clear)
        lifecycleScope.launch { opens.open(opened) }
    }

    override fun onStop() {
        super.onStop()
        // A rotation stops and recreates the activity; Ledga hasn't left the screen.
        if (!isChangingConfigurations) work.snapshotSoon()
    }

    override fun onResume() {
        super.onResume()
        liveClock.onResume()
    }
}
