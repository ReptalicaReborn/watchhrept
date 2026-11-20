package com.watchhrept.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.watchhrept.app.data.ThreadSummary
import androidx.compose.foundation.lazy.rememberLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadListScreen(
    state: UiState,
    onOpen: (ThreadSummary) -> Unit,
    onNewChat: () -> Unit,
    onSearch: () -> Unit,
    onReconnect: () -> Unit,
    onSettings: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val expandedFab by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    // The chat list shows conversations; contacts you never talked to are reachable through "New chat".
    val chats = remember(state.threads) { state.threads.filter { it.lastMessage != null || it.pinned } }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Chats")
                        state.status?.ownName?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, contentDescription = "Search") }
                    IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewChat,
                expanded = expandedFab,
                icon = { Icon(Icons.Outlined.Edit, null) },
                text = { Text("New chat") },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            val status = state.status
            if (status?.state == "disconnected" || state.connectionError != null) {
                item {
                    StatusBanner(
                        text = state.connectionError ?: status?.error ?: "Disconnected from Zalo",
                        action = if (status?.state == "disconnected") "Reconnect" else null,
                        onAction = onReconnect,
                    )
                }
            }
            if (chats.isEmpty()) {
                item { EmptyChats() }
            }
            items(chats, key = { it.key }) { thread ->
                ThreadRow(thread, typing = state.typing[thread.key], onClick = { onOpen(thread) })
            }
        }
    }
}

@Composable
private fun ThreadRow(thread: ThreadSummary, typing: TypingInfo?, onClick: () -> Unit) {
    val unread = thread.unread > 0
    val last = thread.lastMessage
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Avatar(thread.name, thread.avatar, isGroup = thread.kind == "group") },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    thread.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (thread.isMuted) {
                    Spacer(Modifier.size(4.dp))
                    Icon(
                        Icons.Outlined.NotificationsOff,
                        contentDescription = "Muted",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        supportingContent = {
            val mentioned = last?.mentionsMe == true && unread
            Text(
                when {
                    typing != null -> if (thread.kind == "group" && typing.name.isNotBlank()) "${typing.name} is typing…" else "typing…"
                    mentioned -> "@ ${preview(last)}"
                    else -> preview(last)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = when {
                    typing != null || mentioned -> MaterialTheme.colorScheme.primary
                    unread -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                last?.let {
                    Text(
                        formatListTime(it.ts),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (thread.pinned) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (unread) {
                        Spacer(Modifier.size(4.dp))
                        Badge(
                            containerColor = if (thread.isMuted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                        ) { Text(if (thread.unread > 99) "99+" else thread.unread.toString()) }
                    }
                }
            }
        },
    )
}

@Composable
fun StatusBanner(text: String, action: String?, onAction: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WifiOff, null, Modifier.size(20.dp))
            Spacer(Modifier.size(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (action != null) TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun EmptyChats() {
    Box(Modifier.fillMaxWidth().padding(top = 96.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.AutoMirrored.Outlined.Chat,
                null,
                Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text("No chats yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "New messages appear here. Tap “New chat” to write to a friend or group.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
            )
        }
    }
}
