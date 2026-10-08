package com.ledga.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ledga.app.data.update.InstallEvents
import com.ledga.app.data.update.InstallStatus
import com.ledga.app.data.update.UpdateFiles
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** R138: Android's answer to an update's install session. Not exported: only the session's PendingIntent reaches it. */
@AndroidEntryPoint
class InstallStatusReceiver : BroadcastReceiver() {
    @Inject lateinit var events: InstallEvents

    @Inject lateinit var files: UpdateFiles

    override fun onReceive(context: Context, intent: Intent) {
        InstallStatus.handle(intent, events, confirm = { context.startActivity(it) }, damaged = { files.keepOnly(null) })
    }
}
