package com.watchhrept.wear.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.watchhrept.wear.data.ApiClient
import com.watchhrept.wear.data.ChatMessage
import com.watchhrept.wear.data.ServerEvent
import com.watchhrept.wear.data.ServerSettings
import com.watchhrept.wear.data.SessionStatus
import com.watchhrept.wear.data.SettingsStore
import com.watchhrept.wear.data.ThreadSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatState(
    val kind: String,
    val id: String,
    val name: String,
    val messages: List<ChatMessage> = emptyList(),
    val loading: Boolean = true,
    val sending: Boolean = false,
)

data class UiState(
    val settings: ServerSettings,
    val status: SessionStatus? = null,
    /** Set while the server cannot be reached; the UI keeps showing cached data. */
    val connectionError: String? = null,
    val threads: List<ThreadSummary> = emptyList(),
    val chat: ChatState? = null,
    /** One-off error to show as a toast-like message. */
    val error: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val settingsStore = SettingsStore(app)
    private val _state = MutableStateFlow(UiState(settings = settingsStore.load()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var client: ApiClient? = null
    private var liveJob: Job? = null
    private var visible = false

    init {
        rebuildClient()
    }

    fun saveSettings(settings: ServerSettings) {
        settingsStore.save(settings)
        _state.value = UiState(settings = settingsStore.load())
        rebuildClient()
        if (visible) startLiveUpdates()
    }

    /** Hold the WebSocket only while the app is visible, to save battery. */
    fun startLiveUpdates() {
        visible = true
        liveJob?.cancel()
        val api = client ?: return
        liveJob = viewModelScope.launch {
            while (true) {
                try {
                    refreshAll(api)
                    api.events().collect { onEvent(api, it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(connectionError = e.message ?: "Cannot reach server") }
                }
                delay(RECONNECT_DELAY_MS)
            }
        }
    }

    fun stopLiveUpdates() {
        visible = false
        liveJob?.cancel()
        liveJob = null
    }

    fun login() = launchApi { api ->
        val status = api.login()
        _state.update { it.copy(status = status) }
    }

    fun reconnectZalo() = launchApi { api ->
        val status = api.reconnect()
        _state.update { it.copy(status = status) }
    }

    fun openChat(thread: ThreadSummary) {
        _state.update { it.copy(chat = ChatState(thread.kind, thread.id, thread.name)) }
        launchApi { api ->
            val res = api.messages(thread.kind, thread.id)
            _state.update { s ->
                val chat = s.chat?.takeIf { it.kind == thread.kind && it.id == thread.id } ?: return@update s
                s.copy(chat = chat.copy(messages = mergeMessages(chat.messages, res.messages), loading = false))
            }
            markRead(api, thread.kind, thread.id)
        }
    }

    fun closeChat() {
        _state.update { it.copy(chat = null) }
    }

    fun send(text: String) {
        val chat = _state.value.chat ?: return
        if (text.isBlank()) return
        _state.update { s -> s.copy(chat = s.chat?.copy(sending = true)) }
        launchApi(onDone = { _state.update { s -> s.copy(chat = s.chat?.copy(sending = false)) } }) { api ->
            // The sent message comes back over the WebSocket like any other.
            api.send(chat.kind, chat.id, text.trim())
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun rebuildClient() {
        val settings = _state.value.settings
        client = if (settings.isConfigured) ApiClient(settings.serverUrl, settings.token.ifBlank { null }) else null
    }

    private suspend fun refreshAll(api: ApiClient) {
        val status = api.status()
        val threads = if (status.isLoggedIn) api.threads() else emptyList()
        _state.update { it.copy(status = status, threads = threads, connectionError = null) }
        _state.value.chat?.let { chat ->
            val res = api.messages(chat.kind, chat.id)
            _state.update { s ->
                s.copy(chat = s.chat?.copy(messages = mergeMessages(s.chat.messages, res.messages), loading = false))
            }
        }
    }

    private suspend fun onEvent(api: ApiClient, event: ServerEvent) {
        when (event) {
            is ServerEvent.Status -> {
                val wasLoggedIn = _state.value.status?.isLoggedIn == true
                _state.update { it.copy(status = event.status, connectionError = null) }
                if (event.status.isLoggedIn && !wasLoggedIn) refreshThreads(api)
            }
            is ServerEvent.ThreadsChanged -> refreshThreads(api)
            is ServerEvent.Message -> onMessage(api, event.message)
        }
    }

    private suspend fun onMessage(api: ApiClient, msg: ChatMessage) {
        val chat = _state.value.chat
        val inOpenChat = chat != null && chat.kind == msg.threadKind && chat.id == msg.threadId
        if (inOpenChat) {
            _state.update { s -> s.copy(chat = s.chat?.copy(messages = mergeMessages(s.chat.messages, listOf(msg)))) }
        }

        val known = _state.value.threads.any { it.kind == msg.threadKind && it.id == msg.threadId }
        if (!known) {
            refreshThreads(api)
        } else {
            _state.update { s ->
                val threads = s.threads.map { t ->
                    if (t.kind != msg.threadKind || t.id != msg.threadId) return@map t
                    val unread = if (msg.isSelf || inOpenChat) 0 else t.unread + 1
                    t.copy(lastMessage = msg, unread = unread)
                }
                s.copy(threads = threads.sortedByDescending { it.lastMessage?.ts ?: 0 })
            }
        }
        if (inOpenChat && !msg.isSelf) markRead(api, msg.threadKind, msg.threadId)
    }

    private suspend fun refreshThreads(api: ApiClient) {
        val threads = api.threads()
        _state.update { it.copy(threads = threads) }
    }

    private suspend fun markRead(api: ApiClient, kind: String, id: String) {
        runCatching { api.markRead(kind, id) }
        _state.update { s ->
            s.copy(threads = s.threads.map { if (it.kind == kind && it.id == id) it.copy(unread = 0) else it })
        }
    }

    private fun launchApi(onDone: () -> Unit = {}, block: suspend (ApiClient) -> Unit) {
        val api = client ?: return
        viewModelScope.launch {
            try {
                block(api)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Something went wrong") }
            } finally {
                onDone()
            }
        }
    }

    private companion object {
        const val RECONNECT_DELAY_MS = 5_000L
        const val MAX_CHAT_MESSAGES = 60

        fun mergeMessages(current: List<ChatMessage>, incoming: List<ChatMessage>): List<ChatMessage> =
            (current + incoming).distinctBy { it.id }.sortedBy { it.ts }.takeLast(MAX_CHAT_MESSAGES)
    }
}
