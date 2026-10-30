package com.watchhrept.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.watchhrept.wear.data.ChatMessage
import com.watchhrept.wear.data.GroupMember
import com.watchhrept.wear.data.QUICK_REACTIONS

private val QUICK_REPLIES = listOf("OK", "👍", "Đang bận, nói sau nhé", "Đang trên đường", "Cảm ơn!")
private val OWN_BUBBLE = Color(0xFF1E3A5F)

@Composable
fun ChatScreen(
    chat: ChatState,
    typing: TypingInfo?,
    onSelect: (ChatMessage) -> Unit,
    onSend: (String) -> Unit,
    onStartTyping: () -> Unit,
    onMention: () -> Unit,
    onLoadEarlier: () -> Unit,
    onOptions: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    val openInput = rememberTextInput(onResult = onSend)
    val lastOwnId = chat.messages.lastOrNull { it.isSelf }?.id
    val subtitle = when {
        chat.kind == "user" && chat.lastOnline != null -> formatPresence(chat.lastOnline)
        chat.kind == "group" && chat.members != null -> "${chat.members.size + 1} members"
        else -> null
    }
    // Items before the first message: header, optional subtitle, optional "load earlier"/spinner.
    val leadingItems = 1 + (if (subtitle != null) 1 else 0) +
        (if (chat.hasMore) 1 else 0) + (if (chat.loading && chat.messages.isEmpty()) 1 else 0)

    // Follow new messages at the bottom, but stay put when older ones are loaded above.
    LaunchedEffect(chat.messages.lastOrNull()?.id) {
        if (chat.messages.isNotEmpty()) listState.scrollToItem(leadingItems + chat.messages.size - 1)
    }

    WatchList(listState) {
        item { ListHeader { Text(chat.name, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        if (subtitle != null) {
            item { CenteredText(subtitle, MaterialTheme.colors.onSurfaceVariant, MaterialTheme.typography.caption3) }
        }
        if (chat.hasMore) {
            item {
                ActionChip(if (chat.loadingMore) "Loading…" else "Load earlier", onLoadEarlier, enabled = !chat.loadingMore)
            }
        }
        if (chat.loading && chat.messages.isEmpty()) item { CircularProgressIndicator() }
        items(chat.messages, key = { it.id }) { msg ->
            MessageBubble(
                msg = msg,
                showSender = chat.kind == "group",
                showStatus = msg.id == lastOwnId,
                onClick = { onSelect(msg) },
            )
        }
        if (typing != null) {
            item {
                CenteredText(
                    if (chat.kind == "group" && typing.name.isNotBlank()) "${typing.name} is typing…" else "typing…",
                    MaterialTheme.colors.primary,
                    MaterialTheme.typography.caption2,
                )
            }
        }
        item {
            ActionChip(
                label = if (chat.sending) "Sending…" else "Reply",
                enabled = !chat.sending,
                colors = ChipDefaults.primaryChipColors(),
                onClick = {
                    onStartTyping()
                    openInput("Message ${chat.name}", QUICK_REPLIES)
                },
            )
        }
        if (chat.kind == "group") item { ActionChip("@ Mention someone", onMention) }
        item { ActionChip("Chat options", onOptions) }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage, showSender: Boolean, showStatus: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (msg.isSelf) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(if (msg.isSelf) OWN_BUBBLE else MaterialTheme.colors.surface, shape)
                .then(if (msg.mentionsMe) Modifier.border(1.dp, MaterialTheme.colors.primary, shape) else Modifier)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            if (showSender && !msg.isSelf) {
                Text(
                    msg.fromName,
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            msg.quote?.let { q ->
                Text(
                    "↩ ${q.fromName}: ${q.text}",
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                msg.text,
                style = MaterialTheme.typography.body2,
                fontStyle = if (msg.recalled) FontStyle.Italic else FontStyle.Normal,
                color = if (msg.recalled) MaterialTheme.colors.onSurfaceVariant else MaterialTheme.colors.onSurface,
            )
            if (msg.reactions.isNotEmpty()) {
                Text(
                    msg.reactions.joinToString(" ") { if (it.count > 1) "${it.emoji}${it.count}" else it.emoji },
                    style = MaterialTheme.typography.caption2,
                )
            }
            Text(
                listOfNotNull(formatTime(msg.ts), if (showStatus) statusLabel(msg) else null).joinToString(" · "),
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
fun MessageActionsScreen(
    msg: ChatMessage,
    onReact: (String) -> Unit,
    onStartTyping: () -> Unit,
    onReply: (String) -> Unit,
    onForward: () -> Unit,
    onRecall: () -> Unit,
    onDelete: () -> Unit,
) {
    val reply = rememberTextInput(onResult = onReply)

    WatchList {
        item {
            ListHeader {
                Text(if (msg.isSelf) "You" else msg.fromName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item { CenteredText(msg.text) }
        item {
            CenteredText(
                listOfNotNull(formatTime(msg.ts), statusLabel(msg)).joinToString(" · "),
                MaterialTheme.colors.onSurfaceVariant,
                MaterialTheme.typography.caption3,
            )
        }
        if (!msg.recalled) {
            // Two rows of three so the buttons fit inside a round screen.
            QUICK_REACTIONS.chunked(3).forEach { row ->
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        row.forEach { (key, emoji) ->
                            val mine = msg.reactions.any { it.mine && it.key == key }
                            Button(
                                onClick = { onReact(key) },
                                modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
                                colors = if (mine) ButtonDefaults.primaryButtonColors() else ButtonDefaults.secondaryButtonColors(),
                            ) {
                                Text(emoji)
                            }
                        }
                    }
                }
            }
            item {
                ActionChip(
                    label = "Reply",
                    colors = ChipDefaults.primaryChipColors(),
                    onClick = {
                        onStartTyping()
                        reply("Reply to ${if (msg.isSelf) "yourself" else msg.fromName}", QUICK_REPLIES)
                    },
                )
            }
            if (msg.isText) item { ActionChip("Forward", onForward) }
            if (msg.isSelf) item { ConfirmChip("Recall for everyone", onRecall) }
        }
        item { ConfirmChip("Delete for me", onDelete) }
    }
}

/** Pick a group member to @mention, then type the message. */
@Composable
fun MentionScreen(
    members: List<GroupMember>?,
    onStartTyping: () -> Unit,
    onSend: (GroupMember, String) -> Unit,
) {
    var chosen by remember { mutableStateOf<GroupMember?>(null) }
    val input = rememberTextInput { text -> chosen?.let { onSend(it, text) } }

    WatchList {
        item { ListHeader { Text("Mention") } }
        when {
            members == null -> item { CircularProgressIndicator() }
            members.isEmpty() -> item { CenteredText("No members found") }
            else -> items(members, key = { it.id }) { member ->
                ActionChip(member.name, onClick = {
                    chosen = member
                    onStartTyping()
                    input("Message @${member.name}", emptyList())
                })
            }
        }
    }
}

private fun statusLabel(msg: ChatMessage): String? = when {
    !msg.isSelf || msg.recalled -> null
    msg.threadKind == "group" && (msg.seenCount ?: 0) > 0 -> "Seen by ${msg.seenCount}"
    msg.status == "seen" -> "Seen"
    msg.status == "delivered" -> "Delivered"
    msg.status == "sent" -> "Sent"
    else -> null
}
