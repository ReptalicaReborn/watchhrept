package com.watchhrept.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.watchhrept.wear.data.MuteOption
import com.watchhrept.wear.data.ThreadSummary

private const val MAX_SEARCH_RESULTS = 100

@Composable
fun ThreadListScreen(
    state: UiState,
    onOpen: (ThreadSummary) -> Unit,
    onNewChat: () -> Unit,
    onReconnect: () -> Unit,
    onSettings: () -> Unit,
) {
    // Contacts we have never talked to live under "New chat", as in the official app.
    val threads = state.threads.filter { it.lastMessage != null }

    WatchList {
        item { ListHeader { Text(state.status?.ownName ?: "Zalo") } }
        if (state.status?.state == "disconnected") {
            item {
                ActionChip(
                    label = "Disconnected",
                    secondary = "Tap to reconnect",
                    onClick = onReconnect,
                    colors = ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.error),
                )
            }
        }
        state.connectionError?.let { error -> item { CenteredText("Offline: $error", MaterialTheme.colors.error) } }
        item { ActionChip("New chat", onNewChat, colors = ChipDefaults.primaryChipColors()) }
        if (threads.isEmpty()) item { CenteredText("No chats yet") }
        items(threads, key = { it.key }) { thread ->
            ThreadChip(thread, typing = state.typing[thread.key], onClick = { onOpen(thread) })
        }
        item { ActionChip("Settings", onSettings) }
    }
}

@Composable
private fun ThreadChip(thread: ThreadSummary, typing: TypingInfo?, onClick: () -> Unit) {
    val last = thread.lastMessage
    val preview = when {
        typing != null -> if (thread.kind == "group" && typing.name.isNotBlank()) "${typing.name} is typing…" else "typing…"
        last == null -> ""
        last.isSelf -> "You: ${last.text}"
        thread.kind == "group" -> "${last.fromName}: ${last.text}"
        else -> last.text
    }
    val hasUnread = thread.unread > 0
    val badges = buildString {
        if (thread.pinned) append("📌")
        if (thread.isMuted) append("🔕")
        if (hasUnread && last?.mentionsMe == true) append("@")
    }

    Chip(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        // Muted chats keep a quiet look even with unread messages.
        colors = if (hasUnread && !thread.isMuted) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
        icon = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(ChipDefaults.IconSize)
                    .background(MaterialTheme.colors.surface, CircleShape),
            ) {
                Text(
                    text = if (hasUnread) thread.unread.coerceAtMost(99).toString() else initial(thread.name),
                    style = MaterialTheme.typography.caption2,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onSurface,
                )
            }
        },
        label = {
            Text(
                text = listOf(badges, if (thread.kind == "group") "👥" else "", thread.name)
                    .filter { it.isNotEmpty() }
                    .joinToString(" "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        secondaryLabel = {
            Text(
                preview,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (typing != null) MaterialTheme.colors.primary else MaterialTheme.colors.onSurfaceVariant,
            )
        },
    )
}

/** Pick any friend or group: starting a new chat, or choosing where to forward a message. */
@Composable
fun ContactsScreen(title: String, threads: List<ThreadSummary>, onPick: (ThreadSummary) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val search = rememberTextInput { query = it.trim() }
    val results = remember(threads, query) {
        val q = foldForSearch(query)
        threads
            .filter { q.isEmpty() || foldForSearch(it.name).contains(q) }
            .sortedBy { foldForSearch(it.name) }
            .take(MAX_SEARCH_RESULTS)
    }

    WatchList {
        item { ListHeader { Text(title) } }
        item {
            ActionChip(
                label = if (query.isEmpty()) "Search" else "“$query”",
                secondary = if (query.isEmpty()) "Speak or type a name" else "Tap to change",
                onClick = { search("Search contacts", emptyList()) },
                colors = ChipDefaults.primaryChipColors(),
            )
        }
        if (query.isNotEmpty()) item { ActionChip("Clear search", { query = "" }) }
        if (results.isEmpty()) item { CenteredText("No matches") }
        items(results, key = { it.key }) { t ->
            ActionChip(
                label = (if (t.kind == "group") "👥 " else "") + t.name,
                onClick = { onPick(t) },
            )
        }
    }
}

@Composable
fun ChatOptionsScreen(
    chat: ChatState,
    thread: ThreadSummary?,
    onPin: (Boolean) -> Unit,
    onMute: () -> Unit,
    onUnmute: () -> Unit,
    onMarkUnread: () -> Unit,
    onDelete: () -> Unit,
) {
    val pinned = thread?.pinned == true
    WatchList {
        item { ListHeader { Text(chat.name, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        item { ActionChip(if (pinned) "Unpin chat" else "Pin chat", { onPin(!pinned) }) }
        item {
            if (thread != null && thread.isMuted) {
                ActionChip("Unmute", onUnmute, secondary = muteLabel(thread.mutedUntil))
            } else {
                ActionChip("Mute notifications", onMute)
            }
        }
        item { ActionChip("Mark as unread", onMarkUnread) }
        item { ConfirmChip("Delete conversation", onDelete) }
    }
}

@Composable
fun MuteScreen(onPick: (MuteOption) -> Unit) {
    WatchList {
        item { ListHeader { Text("Mute") } }
        MuteOption.entries.forEach { option -> item { ActionChip(option.label, { onPick(option) }) } }
    }
}

private fun muteLabel(mutedUntil: Long?): String = when {
    mutedUntil == null -> ""
    mutedUntil == -1L -> "Muted until turned on"
    else -> "Muted until ${formatTime(mutedUntil)}"
}
