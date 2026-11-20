package com.watchhrept.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.watchhrept.app.data.ApiClient
import com.watchhrept.app.data.Appearance
import com.watchhrept.app.data.ChatMessage
import com.watchhrept.app.data.GroupMember
import com.watchhrept.app.data.Mention
import com.watchhrept.app.data.MuteOption
import com.watchhrept.app.data.ServerEvent
import com.watchhrept.app.data.ServerSettings
import com.watchhrept.app.data.SessionStatus
import com.watchhrept.app.data.SettingsStore
import com.watchhrept.app.data.ThreadRef
import com.watchhrept.app.data.ThreadSummary
import com.watchhrept.app.server.LocalServer
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
    /** The server may hold older messages than the ones shown. */
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val sending: Boolean = false,
    /** 1:1 chats: epoch ms the other person was last online, if they share it. */
    val lastOnline: Long? = null,
    /** Group chats: members, loaded on demand for @mentions. */
    val members: List<GroupMember>? = null,
    /** First message that was unread when the chat was opened, for the "unread messages" divider. */
    val firstUnreadId: String? = null,
) {
    val ref: ThreadRef get() = ThreadRef(kind, id)
}

data class TypingInfo(val name: String, val at: Long)

data class UiState(
    val settings: ServerSettings,
    val appearance: Appearance = Appearance(),
    val status: SessionStatus? = null,
    /** Set while the server cannot be reached; the UI keeps showing cached data. */
    val connectionError: String? = null,
    val threads: List<ThreadSummary> = emptyList(),
    val chat: ChatState? = null,
    /** Message the message-actions screen is showing. */
    val selected: ChatMessage? = null,
    /** Who is typing, by thread key. Entries expire after [TYPING_TIMEOUT_MS]. */
    val typing: Map<String, TypingInfo> = emptyMap(),
    /** One-off error to show as a toast-like message. */
    val error: String? = null,
)

const val TYPING_TIMEOUT_MS = 6_000L

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val settingsStore = SettingsStore(app)
    private val _state = MutableStateFlow(UiState(settings = settingsStore.load(), appearance = settingsStore.loadAppearance()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var client: ApiClient? = null
    private var liveJob: Job? = null
    private var visible = false
    private var lastTypingSent = 0L
    /** When the live loop last (re)started the on-phone server; errors are expected while it boots. */
    private var localStartedAt = 0L

    init {
        rebuildClient()
    }

    fun setAppearance(appearance: Appearance) {
        settingsStore.saveAppearance(appearance)
        _state.update { it.copy(appearance = appearance) }
    }

    fun saveSettings(settings: ServerSettings) {
        val wasOnWatch = _state.value.settings.onDevice
        settingsStore.save(settings)
        if (wasOnWatch && !settings.onDevice) LocalServer.stop(getApplication())
        _state.value = UiState(settings = settingsStore.load(), appearance = _state.value.appearance)
        rebuildClient()
        if (visible) startLiveUpdates()
    }

    /** Restarts the on-phone server, e.g. if it got stuck. */
    fun restartLocalServer() {
        if (!_state.value.settings.onDevice) return
        LocalServer.stop(getApplication())
        _state.update { it.copy(status = null, connectionError = null) }
        viewModelScope.launch {
            // Let the old process exit before starting a new one.
            delay(1_500)
            if (visible) startLiveUpdates()
        }
    }

    fun logout() = launchApi { api ->
        val status = api.logout()
        _state.update { it.copy(status = status, threads = emptyList(), chat = null) }
    }

    /** Hold the WebSocket only while the app is visible, to save battery. */
    fun startLiveUpdates() {
        visible = true
        liveJob?.cancel()
        val api = client ?: return
        val onDevice = _state.value.settings.onDevice
        if (onDevice) {
            LocalServer.start(getApplication())
            localStartedAt = System.currentTimeMillis()
        }
        liveJob = viewModelScope.launch {
            while (true) {
                var booting = onDevice && System.currentTimeMillis() - localStartedAt < LOCAL_BOOT_GRACE_MS
                try {
                    refreshAll(api)
                    api.events().collect { onEvent(api, it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The on-phone server says why it stopped; no point waiting out the start-up time.
                    val failure = if (onDevice) LocalServer.failure(getApplication(), since = localStartedAt) else null
                    if (failure != null) booting = false
                    // While the on-phone server boots, keep showing "starting" rather than an error.
                    val error = when {
                        failure != null -> "The Zalo service stopped: $failure"
                        booting -> null
                        else -> e.message ?: "Cannot reach server"
                    }
                    _state.update { it.copy(connectionError = error) }
                }
                delay(if (booting) LOCAL_BOOT_RETRY_MS else RECONNECT_DELAY_MS)
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

    /** Retry a saved session that couldn't reach Zalo. */
    fun retryLogin() = launchApi { api ->
        val status = api.retry()
        _state.update { it.copy(status = status) }
    }

    fun reconnectZalo() = launchApi { api ->
        val status = api.reconnect()
        _state.update { it.copy(status = status) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    // ---- Chat ----

    fun openChat(thread: ThreadSummary) {
        _state.update { it.copy(chat = ChatState(thread.kind, thread.id, thread.name)) }
        launchApi { api ->
            val res = api.messages(thread.kind, thread.id, PAGE_SIZE)
            val unread = _state.value.threads.firstOrNull { it.ref == thread.ref }?.unread ?: thread.unread
            val firstUnread = if (unread > 0) res.messages.filter { !it.isSelf }.takeLast(unread).firstOrNull()?.id else null
            updateChat(thread.ref) {
                it.copy(
                    messages = merge(it.messages, res.messages),
                    loading = false,
                    hasMore = res.messages.size >= PAGE_SIZE,
                    firstUnreadId = firstUnread,
                )
            }
            markRead(api, thread.ref)
            val details = runCatching { api.details(thread.kind, thread.id) }.getOrNull()
            if (details?.lastOnline != null) updateChat(thread.ref) { it.copy(lastOnline = details.lastOnline) }
            if (details?.members != null) updateChat(thread.ref) { it.copy(members = details.members) }
        }
    }

    /** Closes the open chat, unless another one was opened since [ref] was. */
    fun closeChat(ref: ThreadRef? = null) {
        _state.update { if (ref == null || it.chat?.ref == ref) it.copy(chat = null) else it }
    }

    fun loadEarlier() {
        val chat = _state.value.chat ?: return
        val oldest = chat.messages.firstOrNull() ?: return
        if (chat.loadingMore) return
        updateChat(chat.ref) { it.copy(loadingMore = true) }
        launchApi(onDone = { updateChat(chat.ref) { it.copy(loadingMore = false) } }) { api ->
            val res = api.messages(chat.kind, chat.id, PAGE_SIZE, before = oldest.ts)
            updateChat(chat.ref) {
                it.copy(messages = merge(it.messages, res.messages), hasMore = res.messages.size >= PAGE_SIZE)
            }
        }
    }

    /**
     * Sends [text], optionally as a reply to [quote]. Each of [mentioned] whose "@Name" still appears in
     * the text becomes an @mention (groups only). [onSent] runs once the server accepted it, so the
     * input keeps the draft if sending fails.
     */
    fun send(text: String, quote: ChatMessage? = null, mentioned: List<GroupMember> = emptyList(), onSent: () -> Unit = {}) {
        val chat = _state.value.chat ?: return
        val body = text.trim()
        if (body.isEmpty()) return
        val mentions = if (chat.kind == "group") findMentions(body, mentioned) else emptyList()

        updateChat(chat.ref) { it.copy(sending = true) }
        launchApi(onDone = { updateChat(chat.ref) { it.copy(sending = false) } }) { api ->
            // The sent message comes back over the WebSocket like any other.
            api.send(chat.kind, chat.id, body, quoteId = quote?.id, mentions = mentions)
            onSent()
        }
    }

    /** Called when the reply input opens, so the other side sees "typing…". */
    fun notifyTyping() {
        val chat = _state.value.chat ?: return
        val now = System.currentTimeMillis()
        if (now - lastTypingSent < 3_000) return
        lastTypingSent = now
        viewModelScope.launch { runCatching { client?.typing(chat.kind, chat.id) } }
    }

    fun loadMembers() {
        val chat = _state.value.chat ?: return
        if (chat.kind != "group" || chat.members != null) return
        launchApi { api ->
            val members = api.details(chat.kind, chat.id).members.orEmpty()
            updateChat(chat.ref) { it.copy(members = members) }
        }
    }

    // ---- Message actions ----

    fun select(message: ChatMessage) {
        _state.update { it.copy(selected = message) }
    }

    /** Tapping the reaction we already gave removes it, as in the official app. */
    fun toggleReaction(message: ChatMessage, key: String) {
        val mine = message.reactions.any { it.mine && it.key == key }
        launchApi { api -> api.react(message.threadKind, message.threadId, message.id, if (mine) null else key) }
    }

    fun recall(message: ChatMessage) = launchApi { api ->
        api.recall(message.threadKind, message.threadId, message.id)
    }

    fun deleteForMe(message: ChatMessage) = launchApi { api ->
        api.deleteForMe(message.threadKind, message.threadId, message.id)
    }

    fun forward(message: ChatMessage, target: ThreadSummary, onDone: () -> Unit) = launchApi { api ->
        api.forward(message.threadKind, message.threadId, message.id, listOf(target.ref))
        onDone()
    }

    // ---- Conversation actions ----

    // These work on any conversation (chat list long-press) as well as the open one;
    // the server pushes the resulting thread-list changes.

    fun setPinned(ref: ThreadRef, pinned: Boolean) = launchApi { api -> api.setPinned(ref.kind, ref.id, pinned) }

    fun setMuted(ref: ThreadRef, option: MuteOption?) = launchApi { api -> api.setMuted(ref.kind, ref.id, option) }

    fun markUnread(ref: ThreadRef) = launchApi { api -> api.markUnread(ref.kind, ref.id) }

    fun markThreadRead(ref: ThreadRef) = launchApi { api -> markRead(api, ref) }

    fun markAllRead() = launchApi { api ->
        _state.value.threads.filter { it.unread > 0 }.forEach { markRead(api, it.ref) }
    }

    fun deleteConversation(ref: ThreadRef) = launchApi { api ->
        api.deleteConversation(ref.kind, ref.id)
        updateChat(ref) { it.copy(messages = emptyList(), hasMore = false) }
    }

    // ---- Internals ----

    private fun rebuildClient() {
        val settings = _state.value.settings
        client = when {
            settings.onDevice -> ApiClient(LocalServer.URL, settingsStore.localToken())
            settings.isConfigured -> ApiClient(settings.serverUrl, settings.token.ifBlank { null })
            else -> null
        }
    }

    private suspend fun refreshAll(api: ApiClient) {
        val status = api.status()
        val threads = if (status.isLoggedIn) api.threads() else emptyList()
        _state.update { it.copy(status = status, threads = threads, connectionError = null) }
        _state.value.chat?.let { chat ->
            val res = api.messages(chat.kind, chat.id, PAGE_SIZE)
            updateChat(chat.ref) { it.copy(messages = merge(it.messages, res.messages), loading = false) }
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
            is ServerEvent.MessageUpdated -> onMessageUpdated(event.message)
            is ServerEvent.MessageDeleted -> {
                updateChat(event.thread) { it.copy(messages = it.messages.filterNot { m -> m.id == event.id }) }
                if (_state.value.selected?.id == event.id) _state.update { it.copy(selected = null) }
                refreshThreads(api)
            }
            is ServerEvent.Typing -> onTyping(event)
        }
    }

    private suspend fun onMessage(api: ApiClient, msg: ChatMessage) {
        val ref = ThreadRef(msg.threadKind, msg.threadId)
        val inOpenChat = _state.value.chat?.ref == ref
        updateChat(ref) { it.copy(messages = merge(it.messages, listOf(msg))) }
        // A new message from someone ends their typing indicator.
        if (!msg.isSelf) _state.update { it.copy(typing = it.typing - ref.key) }

        val known = _state.value.threads.any { it.ref == ref }
        if (!known) {
            refreshThreads(api)
        } else {
            _state.update { s ->
                val threads = s.threads.map { t ->
                    if (t.ref != ref) return@map t
                    val unread = if (msg.isSelf || inOpenChat) 0 else t.unread + 1
                    t.copy(lastMessage = msg, unread = unread)
                }
                s.copy(threads = sortThreads(threads))
            }
        }
        if (inOpenChat && !msg.isSelf) markRead(api, ref)
    }

    private fun onMessageUpdated(msg: ChatMessage) {
        val ref = ThreadRef(msg.threadKind, msg.threadId)
        updateChat(ref) { chat -> chat.copy(messages = chat.messages.map { if (it.id == msg.id) msg else it }) }
        _state.update { s ->
            s.copy(
                selected = if (s.selected?.id == msg.id) msg else s.selected,
                threads = s.threads.map { t -> if (t.lastMessage?.id == msg.id) t.copy(lastMessage = msg) else t },
            )
        }
    }

    private fun onTyping(event: ServerEvent.Typing) {
        val at = System.currentTimeMillis()
        val key = event.thread.key
        _state.update { it.copy(typing = it.typing + (key to TypingInfo(event.name, at))) }
        viewModelScope.launch {
            delay(TYPING_TIMEOUT_MS)
            _state.update { s -> if (s.typing[key]?.at == at) s.copy(typing = s.typing - key) else s }
        }
    }

    private suspend fun refreshThreads(api: ApiClient) {
        val threads = api.threads()
        _state.update { it.copy(threads = threads) }
    }

    private suspend fun markRead(api: ApiClient, ref: ThreadRef) {
        runCatching { api.markRead(ref.kind, ref.id) }
        _state.update { s -> s.copy(threads = s.threads.map { if (it.ref == ref) it.copy(unread = 0) else it }) }
    }

    private fun updateChat(ref: ThreadRef, fn: (ChatState) -> ChatState) {
        _state.update { s ->
            val chat = s.chat
            if (chat != null && chat.ref == ref) s.copy(chat = fn(chat)) else s
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

    companion object {
        private const val RECONNECT_DELAY_MS = 5_000L
        private const val LOCAL_BOOT_GRACE_MS = 45_000L
        private const val LOCAL_BOOT_RETRY_MS = 1_000L
        private const val PAGE_SIZE = 30
        private const val MAX_CHAT_MESSAGES = 300

        /** Newer copies of a message (reactions, recalls, receipts) replace older ones. */
        private fun merge(current: List<ChatMessage>, incoming: List<ChatMessage>): List<ChatMessage> =
            (incoming + current).distinctBy { it.id }.sortedBy { it.ts }.takeLast(MAX_CHAT_MESSAGES)

        /** Positions of "@Name" for each picked member, in text order, without overlaps. */
        fun findMentions(text: String, members: List<GroupMember>): List<Mention> {
            val found = mutableListOf<Mention>()
            for (m in members.distinctBy { it.id }.sortedByDescending { it.name.length }) {
                val tag = "@${m.name}"
                var from = 0
                while (true) {
                    val pos = text.indexOf(tag, from)
                    if (pos < 0) break
                    from = pos + tag.length
                    if (found.none { pos < it.pos + it.len && it.pos < pos + tag.length }) {
                        found += Mention(uid = m.id, pos = pos, len = tag.length)
                    }
                }
            }
            return found.sortedBy { it.pos }
        }

        fun sortThreads(threads: List<ThreadSummary>): List<ThreadSummary> =
            threads.sortedWith(compareByDescending<ThreadSummary> { it.pinned }.thenByDescending { it.lastMessage?.ts ?: 0 })
    }
}

val ThreadSummary.ref: ThreadRef get() = ThreadRef(kind, id)
