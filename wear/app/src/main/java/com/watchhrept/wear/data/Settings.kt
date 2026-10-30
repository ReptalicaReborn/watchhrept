package com.watchhrept.wear.data

import android.content.Context

data class ServerSettings(val serverUrl: String, val token: String) {
    val isConfigured: Boolean get() = ApiClient.normalizeServerUrl(serverUrl) != null
}

/** Server address and access token, kept in app-private storage. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): ServerSettings = ServerSettings(
        serverUrl = prefs.getString(KEY_URL, "").orEmpty(),
        token = prefs.getString(KEY_TOKEN, "").orEmpty(),
    )

    fun save(settings: ServerSettings) {
        prefs.edit()
            .putString(KEY_URL, settings.serverUrl.trim())
            .putString(KEY_TOKEN, settings.token.trim())
            .apply()
    }

    private companion object {
        const val KEY_URL = "server_url"
        const val KEY_TOKEN = "token"
    }
}
