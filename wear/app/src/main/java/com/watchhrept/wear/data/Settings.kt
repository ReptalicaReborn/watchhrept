package com.watchhrept.wear.data

import android.content.Context
import java.security.SecureRandom

data class ServerSettings(
    /** Run the Zalo server inside this app instead of connecting to one elsewhere. */
    val onWatch: Boolean,
    /** Remote server address; unused when [onWatch]. */
    val serverUrl: String,
    /** Remote server access token; unused when [onWatch]. */
    val token: String,
) {
    val isConfigured: Boolean get() = onWatch || ApiClient.normalizeServerUrl(serverUrl) != null
}

/** Server settings, kept in app-private storage shared by the UI and the on-watch server process. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): ServerSettings {
        val serverUrl = prefs.getString(KEY_URL, "").orEmpty()
        return ServerSettings(
            // New installs default to the on-watch server; existing remote setups keep working.
            onWatch = prefs.getBoolean(KEY_ON_WATCH, serverUrl.isBlank()),
            serverUrl = serverUrl,
            token = prefs.getString(KEY_TOKEN, "").orEmpty(),
        )
    }

    fun save(settings: ServerSettings) {
        prefs.edit()
            .putBoolean(KEY_ON_WATCH, settings.onWatch)
            .putString(KEY_URL, settings.serverUrl.trim())
            .putString(KEY_TOKEN, settings.token.trim())
            .commit()
    }

    /**
     * Secret shared between the UI and the on-watch server, so other apps on the watch can't use
     * the server's localhost port. Created on first use.
     */
    fun localToken(): String {
        prefs.getString(KEY_LOCAL_TOKEN, null)?.let { return it }
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        // commit, not apply: the server process reads this from disk right after.
        prefs.edit().putString(KEY_LOCAL_TOKEN, token).commit()
        return token
    }

    private companion object {
        const val KEY_ON_WATCH = "on_watch"
        const val KEY_URL = "server_url"
        const val KEY_TOKEN = "token"
        const val KEY_LOCAL_TOKEN = "local_token"
    }
}
