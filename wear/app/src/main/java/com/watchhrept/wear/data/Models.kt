package com.watchhrept.wear.data

import kotlinx.serialization.Serializable

/** Mirrors `SessionStatus` in the server's src/session.ts. */
@Serializable
data class SessionStatus(
    val state: String,
    val qrImage: String? = null,
    val scannedBy: String? = null,
    val ownId: String? = null,
    val ownName: String? = null,
    val error: String? = null,
) {
    val isLoggedIn: Boolean get() = state == "ready" || state == "disconnected"
}

@Serializable
data class Attachment(
    val title: String? = null,
    val href: String? = null,
    val thumb: String? = null,
)

/** Mirrors `ChatMessage` in the server's src/store.ts. */
@Serializable
data class ChatMessage(
    val id: String,
    val threadId: String,
    val threadKind: String,
    val fromId: String = "",
    val fromName: String = "",
    val isSelf: Boolean = false,
    val ts: Long,
    val msgType: String = "",
    val text: String,
    val attachment: Attachment? = null,
)

/** Mirrors `ThreadSummary` in the server's src/store.ts. */
@Serializable
data class ThreadSummary(
    val id: String,
    val kind: String,
    val name: String,
    val avatar: String = "",
    val lastMessage: ChatMessage? = null,
    val unread: Int = 0,
)

@Serializable
data class ThreadMessages(
    val thread: ThreadSummaryInfo? = null,
    val messages: List<ChatMessage>,
)

@Serializable
data class ThreadSummaryInfo(
    val id: String,
    val kind: String,
    val name: String,
)

sealed interface ServerEvent {
    data class Status(val status: SessionStatus) : ServerEvent
    data class Message(val message: ChatMessage) : ServerEvent
    data object ThreadsChanged : ServerEvent
}
