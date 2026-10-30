package com.watchhrept.wear.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.system.Os
import android.util.Log
import androidx.core.app.NotificationCompat
import com.watchhrept.wear.MainActivity
import com.watchhrept.wear.R
import com.watchhrept.wear.data.SettingsStore
import java.io.File

/**
 * Runs the Zalo server (the bundled src/ of this repo) in embedded Node.js.
 *
 * Lives in its own ":node" process: Node can only start once per process, so stopping the service
 * ends the process and the next start gets a fresh one. It runs as a foreground service so the
 * Zalo connection, and with it the 1:1 message history, keeps going while the app is closed.
 */
class NodeService : Service() {
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!started) {
            started = true
            // Node needs a bigger stack than Android's default thread stack.
            Thread(null, Runnable { runNode() }, "node", NODE_STACK_BYTES).start()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Process.killProcess(Process.myPid())
    }

    private fun runNode() {
        try {
            val script = File(filesDir, "server/server.mjs")
            script.parentFile?.mkdirs()
            // Always refresh: the bundle changes with every app update and is only ~2 MB.
            assets.open("server/server.mjs").use { input -> script.outputStream().use { input.copyTo(it) } }

            val dataDir = File(filesDir, "zalo").apply { mkdirs() }
            Os.setenv("HOST", "127.0.0.1", true)
            Os.setenv("PORT", LocalServer.PORT.toString(), true)
            Os.setenv("ACCESS_TOKEN", SettingsStore(this).localToken(), true)
            Os.setenv("DATA_DIR", dataDir.absolutePath, true)
            Os.setenv("HOME", filesDir.absolutePath, true)
            Os.setenv("TMPDIR", cacheDir.absolutePath, true)

            val code = NodeRuntime.startNode(arrayOf("node", "--max-old-space-size=$HEAP_MB", script.absolutePath))
            Log.w(TAG, "Node exited with code $code")
        } catch (e: Throwable) {
            Log.e(TAG, "Could not run the Zalo server", e)
        }
        stopSelf()
        Process.killProcess(Process.myPid())
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Zalo connection", NotificationManager.IMPORTANCE_MIN),
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Zalo Watch")
            .setContentText("Connected to Zalo")
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private companion object {
        const val TAG = "ZaloWatchNode"
        const val CHANNEL_ID = "server"
        const val NOTIFICATION_ID = 1
        const val NODE_STACK_BYTES = 8L * 1024 * 1024
        const val HEAP_MB = 192
    }
}
