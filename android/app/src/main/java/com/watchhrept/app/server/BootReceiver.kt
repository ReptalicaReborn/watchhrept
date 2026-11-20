package com.watchhrept.app.server

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.watchhrept.app.data.SettingsStore

/** Reconnects to Zalo after the phone restarts, so messages keep arriving into the history. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && SettingsStore(context).load().onDevice) {
            LocalServer.start(context)
        }
    }
}
