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

@Serializable
data class Quote(
    val msgId: String,
    val fromId: String = "",
    val fromName: String = "",
    val text: String = "",
)

@Serializable
data class Mention(
    val uid: String,
    val pos: Int,
    val len: Int,
)

@Serializable
data class ReactionSummary(
    val key: String,
    val emoji: String,
    val count: Int,
    val mine: Boolean = false,
)

/** Mirrors `ChatMessage` in the server's src/store.ts. */
@Serializable
data class ChatMessage(
    val id: String,
    val cliMsgId: String = "",
    val threadId: String,
    val threadKind: String,
    val fromId: String = "",
    val fromName: String = "",
    val isSelf: Boolean = false,
    val ts: Long,
    val msgType: String = "",
    val text: String,
    val attachment: Attachment? = null,
    val quote: Quote? = null,
    val mentions: List<Mention> = emptyList(),
    val recalled: Boolean = false,
    val reactions: List<ReactionSummary> = emptyList(),
    val mentionsMe: Boolean = false,
    /** "sent", "delivered" or "seen"; only on our own messages. */
    val status: String? = null,
    /** Group chats only: how many members have seen it. */
    val seenCount: Int? = null,
) {
    /** Plain text messages ("webchat" in Zalo) are the only kind the server can forward. */
    val isText: Boolean get() = !recalled && attachment == null && msgType == "webchat"
}

/** Mirrors `ThreadSummary` in the server's src/store.ts. */
@Serializable
data class ThreadSummary(
    val id: String,
    val kind: String,
    val name: String,
    val avatar: String = "",
    val lastMessage: ChatMessage? = null,
    val unread: Int = 0,
    val pinned: Boolean = false,
    /** Epoch ms the mute ends, -1 for "until turned off", null when not muted. */
    val mutedUntil: Long? = null,
) {
    val key: String get() = "$kind:$id"
    val isMuted: Boolean get() = mutedUntil != null
}

@Serializable
data class ThreadSummaryInfo(
    val id: String,
    val kind: String,
    val name: String,
)

@Serializable
data class ThreadMessages(
    val thread: ThreadSummaryInfo? = null,
    val messages: List<ChatMessage>,
)

@Serializable
data class GroupMember(
    val id: String,
    val name: String,
)

/** `GET /threads/:kind/:id/info`: last-online for users, members for groups. */
@Serializable
data class ThreadDetails(
    val lastOnline: Long? = null,
    val members: List<GroupMember>? = null,
)

data class ThreadRef(val kind: String, val id: String) {
    val key: String get() = "$kind:$id"
}

/** Zalo's six quick reactions, in the order the official app shows them. */
val QUICK_REACTIONS = listOf(
    "heart" to "❤️",
    "like" to "👍",
    "haha" to "😆",
    "wow" to "😮",
    "cry" to "😭",
    "angry" to "😡",
)

/** Values accepted by `PUT /threads/:kind/:id/mute`. */
enum class MuteOption(val apiValue: String, val label: String) {
    ONE_HOUR("1h", "For 1 hour"),
    FOUR_HOURS("4h", "For 4 hours"),
    UNTIL_8AM("8am", "Until 8 AM"),
    FOREVER("forever", "Until I turn it back on"),
}

sealed interface ServerEvent {
    data class Status(val status: SessionStatus) : ServerEvent
    data class Message(val message: ChatMessage) : ServerEvent
    data class MessageUpdated(val message: ChatMessage) : ServerEvent
    data class MessageDeleted(val thread: ThreadRef, val id: String) : ServerEvent
    data class Typing(val thread: ThreadRef, val uid: String, val name: String) : ServerEvent
    data object ThreadsChanged : ServerEvent
}
