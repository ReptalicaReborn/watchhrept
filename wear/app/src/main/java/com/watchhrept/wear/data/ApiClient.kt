package com.watchhrept.wear.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(message: String) : IOException(message)

/** Talks to the watchhrept server's REST API and WebSocket. */
class ApiClient(serverUrl: String, private val token: String?) {
    private val baseUrl: HttpUrl = normalizeServerUrl(serverUrl)
        ?: throw IllegalArgumentException("Invalid server URL: $serverUrl")

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun status(): SessionStatus = get("status")

    suspend fun threads(): List<ThreadSummary> = get("threads")

    suspend fun messages(kind: String, id: String, limit: Int = 40): ThreadMessages =
        get("threads/$kind/$id/messages", "limit" to limit.toString())

    suspend fun login(): SessionStatus = post("login")

    suspend fun reconnect(): SessionStatus = post("reconnect")

    suspend fun send(kind: String, id: String, text: String) {
        post<Unit>("threads/$kind/$id/messages", buildJsonObject { put("text", text) }.toString())
    }

    suspend fun markRead(kind: String, id: String) {
        post<Unit>("threads/$kind/$id/read")
    }

    /** Live server events. Completes or throws when the socket closes; callers reconnect. */
    fun events(): Flow<ServerEvent> = callbackFlow {
        val url = baseUrl.newBuilder()
            .addPathSegment("ws")
            .apply { token?.let { addQueryParameter("token", it) } }
            .build()
        val request = Request.Builder()
            .url(url.toString().replaceFirst("http", "ws"))
            .build()

        val socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                parseEvent(text)?.let { trySend(it) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(if (response?.code == 401) ApiException("Wrong access token") else t)
            }
        })
        awaitClose { socket.close(1000, null) }
    }

    private fun parseEvent(text: String): ServerEvent? {
        val obj = json.parseToJsonElement(text).jsonObject
        return when (obj["type"]?.jsonPrimitive?.content) {
            "status" -> ServerEvent.Status(json.decodeFromJsonElement(SessionStatus.serializer(), obj.getValue("status")))
            "message" -> ServerEvent.Message(json.decodeFromJsonElement(ChatMessage.serializer(), obj.getValue("message")))
            "threads" -> ServerEvent.ThreadsChanged
            else -> null
        }
    }

    private suspend inline fun <reified T> get(path: String, vararg query: Pair<String, String>): T =
        execute(Request.Builder().url(url(path, *query)).get())

    private suspend inline fun <reified T> post(path: String, body: String = ""): T =
        execute(Request.Builder().url(url(path)).post(body.toRequestBody(JSON_MEDIA_TYPE)))

    private suspend inline fun <reified T> execute(builder: Request.Builder): T = withContext(Dispatchers.IO) {
        token?.let { builder.header("Authorization", "Bearer $it") }
        http.newCall(builder.build()).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val message = runCatching { json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content }
                    .getOrNull()
                throw ApiException(message ?: "HTTP ${res.code}")
            }
            if (T::class == Unit::class) Unit as T else json.decodeFromString<T>(body)
        }
    }

    private fun url(path: String, vararg query: Pair<String, String>): HttpUrl =
        baseUrl.newBuilder()
            .addPathSegment("api")
            .apply { path.split('/').forEach { addPathSegment(it) } }
            .apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        // Shared so the connection pool survives client rebuilds after settings changes.
        private val http = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()

        /** Accepts "192.168.1.5:3000" or "https://zalo.example.com/" and returns a base URL. */
        fun normalizeServerUrl(input: String): HttpUrl? {
            val trimmed = input.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
            return withScheme.toHttpUrlOrNull()
        }
    }
}
