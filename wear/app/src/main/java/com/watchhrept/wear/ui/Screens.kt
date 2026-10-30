package com.watchhrept.wear.ui

import android.graphics.BitmapFactory
import android.text.format.DateUtils
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.watchhrept.wear.data.ApiClient
import com.watchhrept.wear.data.ChatMessage
import com.watchhrept.wear.data.ServerSettings
import com.watchhrept.wear.data.SessionStatus
import com.watchhrept.wear.data.ThreadSummary
import java.text.DateFormat

private val QUICK_REPLIES = listOf("OK", "👍", "Đang bận, nói sau nhé", "Đang trên đường", "Cảm ơn!")

/** Standard round-screen list scaffold: time at the top, scroll indicator, vignette. */
@Composable
private fun WatchList(
    listState: ScalingLazyListState = rememberScalingLazyListState(),
    content: ScalingLazyListScope.() -> Unit,
) {
    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

@Composable
private fun CenteredText(text: String, color: Color = MaterialTheme.colors.onBackground) {
    Text(
        text = text,
        color = color,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.body2,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    )
}

@Composable
fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun ConnectionErrorScreen(error: String, onRetry: () -> Unit, onSettings: () -> Unit) {
    WatchList {
        item { ListHeader { Text("Can't reach server") } }
        item { CenteredText(error, MaterialTheme.colors.error) }
        item { Chip(onClick = onRetry, label = { Text("Retry") }, modifier = Modifier.fillMaxWidth()) }
        item {
            Chip(
                onClick = onSettings,
                label = { Text("Settings") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun LoginScreen(status: SessionStatus, onLogin: () -> Unit, onSettings: () -> Unit) {
    val qr = remember(status.qrImage) { status.qrImage?.let(::decodeDataUrl) }

    WatchList {
        item { ListHeader { Text("Zalo login") } }
        when {
            qr != null -> {
                item {
                    Image(
                        bitmap = qr,
                        contentDescription = "Login QR code",
                        filterQuality = FilterQuality.None,
                        modifier = Modifier
                            .size(150.dp)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .padding(6.dp),
                    )
                }
                item {
                    CenteredText(
                        status.scannedBy?.let { "Scanned by $it. Confirm on your phone." }
                            ?: "Scan with the Zalo app on your phone",
                    )
                }
            }
            status.state == "logging_in" -> item { CircularProgressIndicator() }
            else -> item {
                Chip(onClick = onLogin, label = { Text("Log in with QR") }, modifier = Modifier.fillMaxWidth())
            }
        }
        status.error?.let { error -> item { CenteredText(error, MaterialTheme.colors.error) } }
        item {
            Chip(
                onClick = onSettings,
                label = { Text("Server settings") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun ThreadListScreen(
    state: UiState,
    onOpen: (ThreadSummary) -> Unit,
    onReconnect: () -> Unit,
    onSettings: () -> Unit,
) {
    // Contacts we have never talked to are only useful with search, which a watch doesn't have.
    val threads = state.threads.filter { it.lastMessage != null }

    WatchList {
        item { ListHeader { Text(state.status?.ownName ?: "Zalo") } }
        if (state.status?.state == "disconnected") {
            item {
                Chip(
                    onClick = onReconnect,
                    label = { Text("Disconnected") },
                    secondaryLabel = { Text("Tap to reconnect", maxLines = 1) },
                    colors = ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.error),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        state.connectionError?.let { error -> item { CenteredText("Offline: $error", MaterialTheme.colors.error) } }
        if (threads.isEmpty()) item { CenteredText("No chats yet") }
        items(threads, key = { "${it.kind}:${it.id}" }) { thread -> ThreadChip(thread, onClick = { onOpen(thread) }) }
        item {
            Chip(
                onClick = onSettings,
                label = { Text("Settings") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ThreadChip(thread: ThreadSummary, onClick: () -> Unit) {
    val last = thread.lastMessage
    val preview = when {
        last == null -> ""
        last.isSelf -> "You: ${last.text}"
        thread.kind == "group" -> "${last.fromName}: ${last.text}"
        else -> last.text
    }
    val hasUnread = thread.unread > 0

    Chip(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (hasUnread) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
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
                text = (if (thread.kind == "group") "👥 " else "") + thread.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        secondaryLabel = { Text(preview, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

@Composable
fun ChatScreen(chat: ChatState, onSend: (String) -> Unit) {
    val listState = rememberScalingLazyListState()
    val openInput = rememberTextInput(onResult = onSend)

    // Header is item 0, so the newest message is at index messages.size.
    LaunchedEffect(chat.messages.size) {
        if (chat.messages.isNotEmpty()) listState.scrollToItem(chat.messages.size)
    }

    WatchList(listState) {
        item {
            ListHeader {
                Text(chat.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (chat.loading && chat.messages.isEmpty()) item { CircularProgressIndicator() }
        items(chat.messages, key = { it.id }) { msg -> MessageBubble(msg, showSender = chat.kind == "group") }
        item {
            Chip(
                onClick = { openInput("Reply to ${chat.name}", QUICK_REPLIES) },
                enabled = !chat.sending,
                label = { Text(if (chat.sending) "Sending…" else "Reply") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage, showSender: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (msg.isSelf) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(
                    if (msg.isSelf) Color(0xFF1E3A5F) else MaterialTheme.colors.surface,
                    RoundedCornerShape(12.dp),
                )
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
            Text(msg.text, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.onSurface)
            Text(
                formatTime(msg.ts),
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
fun SettingsScreen(current: ServerSettings, onSave: (ServerSettings) -> Unit) {
    var serverUrl by rememberSaveable { mutableStateOf(current.serverUrl) }
    var token by rememberSaveable { mutableStateOf(current.token) }
    val editUrl = rememberTextInput { serverUrl = it.trim() }
    val editToken = rememberTextInput { token = it.trim() }
    val valid = ApiClient.normalizeServerUrl(serverUrl) != null

    WatchList {
        item { ListHeader { Text("Server") } }
        item {
            Chip(
                onClick = { editUrl("Server address", listOf("http://10.0.2.2:3000")) },
                label = { Text("Address") },
                secondaryLabel = { Text(serverUrl.ifBlank { "Not set" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Chip(
                onClick = { editToken("Access token", emptyList()) },
                label = { Text("Access token") },
                secondaryLabel = { Text(if (token.isBlank()) "None" else "•".repeat(8)) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Chip(
                onClick = { onSave(ServerSettings(serverUrl, token)) },
                enabled = valid,
                label = { Text("Save") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            CenteredText(
                "Tip: choose “type on phone” in the input screen to paste the token.",
                MaterialTheme.colors.onSurfaceVariant,
            )
        }
    }
}

private fun initial(name: String): String = name.trim().firstOrNull()?.uppercase() ?: "?"

private fun formatTime(ts: Long): String =
    DateUtils.formatSameDayTime(ts, System.currentTimeMillis(), DateFormat.SHORT, DateFormat.SHORT).toString()

private fun decodeDataUrl(dataUrl: String) = runCatching {
    val bytes = Base64.decode(dataUrl.substringAfter(','), Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()
