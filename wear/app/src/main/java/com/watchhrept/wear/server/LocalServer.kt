package com.watchhrept.wear.server

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Starts and stops the Zalo server that runs on the watch itself. */
object LocalServer {
    /** Loopback port the on-watch server listens on. */
    const val PORT = 38123
    const val URL = "http://127.0.0.1:$PORT"

    fun start(context: Context) {
        ContextCompat.startForegroundService(context, Intent(context, NodeService::class.java))
    }

    /** Stops the server; its process exits, since Node can only start once per process. */
    fun stop(context: Context) {
        context.stopService(Intent(context, NodeService::class.java))
    }
}
