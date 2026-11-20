package com.watchhrept.app.server

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import java.io.File

/** Starts and stops the Zalo server that runs on the phone itself, and reads what it reported. */
object LocalServer {
    /** Loopback port the on-phone server listens on. */
    const val PORT = 38123
    const val URL = "http://127.0.0.1:$PORT"

    fun start(context: Context) {
        ContextCompat.startForegroundService(context, Intent(context, NodeService::class.java))
    }

    /** Stops the server; its process exits, since Node can only start once per process. */
    fun stop(context: Context) {
        context.stopService(Intent(context, NodeService::class.java))
    }

    /** Node's output plus the service's own notes, rewritten on each start. */
    fun logFile(context: Context): File = File(context.filesDir, "server.log")

    private fun statusFile(context: Context): File = File(context.filesDir, "server.status")

    /** Called by the service process: "starting", "failed" or "exited", with a reason. */
    fun writeStatus(context: Context, state: String, detail: String = "") {
        runCatching {
            statusFile(context).writeText("$state|${System.currentTimeMillis()}|${detail.replace('\n', ' ')}")
        }
    }

    /** Why the server stopped, if it stopped after [since] (epoch ms); null while it starts or runs. */
    fun failure(context: Context, since: Long): String? {
        val parts = runCatching { statusFile(context).readText() }.getOrNull()?.split('|', limit = 3) ?: return null
        if (parts.size < 3) return null
        val at = parts[1].toLongOrNull() ?: return null
        if (at < since) return null
        return when (parts[0]) {
            "failed", "exited" -> parts[2].ifBlank { parts[0] }
            else -> null
        }
    }

    /** The end of the log, for showing in the app. */
    fun readLog(context: Context, maxChars: Int = 30_000): String = runCatching {
        val text = logFile(context).readText()
        if (text.length > maxChars) "…\n" + text.takeLast(maxChars) else text
    }.getOrDefault("")
}
