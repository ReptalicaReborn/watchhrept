package com.watchhrept.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.watchhrept.app.R
import com.watchhrept.app.data.MuteOption
import com.watchhrept.app.data.ThreadSummary

/** Everything the chat list can ask for. */
class ThreadListActions(
    val onOpen: (ThreadSummary) -> Unit,
    val onNewChat: () -> Unit,
    val onReconnect: () -> Unit,
    val onSettings: () -> Unit,
    val onMarkAllRead: () -> Unit,
    val onPin: (ThreadSummary, Boolean) -> Unit,
    val onMute: (ThreadSummary, MuteOption?) -> Unit,
    val onMarkRead: (ThreadSummary) -> Unit,
    val onMarkUnread: (ThreadSummary) -> Unit,
    val onDelete: (ThreadSummary) -> Unit,
)

private enum class RowAction { PIN, UNPIN, MUTE, UNMUTE, READ, UNREAD, DELETE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadListScreen(state: UiState, actions: ThreadListActions) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var muteFor by remember { mutableStateOf<ThreadSummary?>(null) }
    var deleteFor by remember { mutableStateOf<ThreadSummary?>(null) }
    val closeSearch: () -> Unit = {
        searching = false
        query = ""
    }
    BackHandler(enabled = searching) { closeSearch() }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    // The chat list shows conversations; contacts you never talked to are under search and "New chat".
    val chats = remember(state.threads) { state.threads.filter { it.lastMessage != null || it.pinned || it.unread > 0 } }
    val folded = foldForSearch(query.trim())
    val matches = remember(state.threads, folded) {
        if (folded.isEmpty()) emptyList() else state.threads.filter { foldForSearch(it.name).contains(folded) }
    }

    val onRowAction: (ThreadSummary, RowAction) -> Unit = { thread, action ->
        menuFor = null
        when (action) {
            RowAction.PIN -> actions.onPin(thread, true)
            RowAction.UNPIN -> actions.onPin(thread, false)
            RowAction.MUTE -> muteFor = thread
            RowAction.UNMUTE -> actions.onMute(thread, null)
            RowAction.READ -> actions.onMarkRead(thread)
            RowAction.UNREAD -> actions.onMarkUnread(thread)
            RowAction.DELETE -> deleteFor = thread
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (searching) {
                SearchTopBar(query = query, onQuery = { query = it }, onClose = closeSearch)
            } else {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = actions.onSettings) {
                            Avatar(
                                name = state.status?.ownName.orEmpty(),
                                url = "",
                                key = state.status?.ownId ?: "me",
                                isGroup = false,
                                size = 32.dp,
                            )
                        }
                    },
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        IconButton(onClick = { searching = true }) { Icon(Icons.Outlined.Search, contentDescription = "Search") }
                        OverflowMenu(
                            hasUnread = chats.any { it.unread > 0 },
                            onMarkAllRead = actions.onMarkAllRead,
                            onSettings = actions.onSettings,
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        floatingActionButton = {
            if (!searching) {
                FloatingActionButton(
                    onClick = actions.onNewChat,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) { Icon(Icons.Outlined.Edit, contentDescription = "New chat") }
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (searching && folded.isNotEmpty()) {
                val chatHits = matches.filter { it.lastMessage != null }
                val contactHits = matches.filter { it.lastMessage == null && it.kind == "user" }.sortedBy { foldForSearch(it.name) }
                val groupHits = matches.filter { it.lastMessage == null && it.kind == "group" }.sortedBy { foldForSearch(it.name) }
                if (matches.isEmpty()) {
                    item(key = "no-results") {
                        Text(
                            "No results for “${query.trim()}”",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                if (chatHits.isNotEmpty()) {
                    item(key = "h-chat-hits") { SectionHeader("Chats") }
                    items(chatHits, key = { it.key }) { thread ->
                        ConversationRow(
                            thread = thread,
                            typing = state.typing[thread.key],
                            menuOpen = false,
                            onClick = { actions.onOpen(thread) },
                            onLongClick = {},
                            onDismissMenu = {},
                            onAction = {},
                        )
                    }
                }
                if (contactHits.isNotEmpty()) {
                    item(key = "h-contact-hits") { SectionHeader("Contacts") }
                    items(contactHits.take(MAX_RESULTS), key = { it.key }) { ContactRow(it, onClick = { actions.onOpen(it) }) }
                }
                if (groupHits.isNotEmpty()) {
                    item(key = "h-group-hits") { SectionHeader("Groups") }
                    items(groupHits.take(MAX_RESULTS), key = { it.key }) { ContactRow(it, onClick = { actions.onOpen(it) }) }
                }
            } else {
                val status = state.status
                if (status?.state == "disconnected" || state.connectionError != null) {
                    item(key = "banner") {
                        StatusBanner(
                            text = state.connectionError ?: status?.error ?: "Disconnected from Zalo",
                            action = if (status?.state == "disconnected") "Reconnect" else null,
                            onAction = actions.onReconnect,
                        )
                    }
                }
                if (chats.isEmpty()) item(key = "empty") { EmptyChats() }
                val pinned = chats.filter { it.pinned }
                val others = chats.filterNot { it.pinned }
                if (pinned.isNotEmpty()) {
                    item(key = "h-pinned") { SectionHeader("Pinned") }
                    items(pinned, key = { it.key }) { thread ->
                        ConversationRow(
                            thread = thread,
                            typing = state.typing[thread.key],
                            menuOpen = menuFor == thread.key,
                            onClick = { actions.onOpen(thread) },
                            onLongClick = { menuFor = thread.key },
                            onDismissMenu = { menuFor = null },
                            onAction = { onRowAction(thread, it) },
                        )
                    }
                    if (others.isNotEmpty()) item(key = "h-chats") { SectionHeader("Chats") }
                }
                items(others, key = { it.key }) { thread ->
                    ConversationRow(
                        thread = thread,
                        typing = state.typing[thread.key],
                        menuOpen = menuFor == thread.key,
                        onClick = { actions.onOpen(thread) },
                        onLongClick = { menuFor = thread.key },
                        onDismissMenu = { menuFor = null },
                        onAction = { onRowAction(thread, it) },
                    )
                }
            }
        }
    }

    muteFor?.let { thread ->
        MuteDialog(
            onDismiss = { muteFor = null },
            onPick = {
                actions.onMute(thread, it)
                muteFor = null
            },
        )
    }
    deleteFor?.let { thread ->
        DeleteConversationDialog(
            onDismiss = { deleteFor = null },
            onConfirm = {
                actions.onDelete(thread)
                deleteFor = null
            },
        )
    }
}

private const val MAX_RESULTS = 100

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search") }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text("Search") },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, contentDescription = "Clear") }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
    )
}

@Composable
private fun OverflowMenu(hasUnread: Boolean, onMarkAllRead: () -> Unit, onSettings: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More options") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Mark all read") },
                enabled = hasUnread,
                onClick = {
                    open = false
                    onMarkAllRead()
                },
            )
            DropdownMenuItem(
                text = { Text("Settings") },
                onClick = {
                    open = false
                    onSettings()
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    thread: ThreadSummary,
    typing: TypingInfo?,
    menuOpen: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismissMenu: () -> Unit,
    onAction: (RowAction) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val unread = thread.unread > 0
    val last = thread.lastMessage
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(thread.name, thread.avatar, key = thread.key, isGroup = thread.kind == "group", size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            thread.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (thread.isMuted) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Outlined.NotificationsOff,
                                contentDescription = "Muted",
                                modifier = Modifier.size(16.dp),
                                tint = colors.onSurfaceVariant,
                            )
                        }
                    }
                    if (last != null) {
                        Spacer(Modifier.width(8.dp))
                        if (typing == null && last.isSelf) {
                            DeliveryIcon(deliveryState(last), tint = colors.onSurfaceVariant, background = colors.background)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            formatListTime(last.ts),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (unread) colors.primary else colors.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val mentioned = last?.mentionsMe == true && unread
                    Text(
                        text = when {
                            typing != null -> if (thread.kind == "group" && typing.name.isNotBlank()) "${typing.name} is typing…" else "typing…"
                            else -> preview(last)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            typing != null -> colors.primary
                            unread -> colors.onSurface
                            else -> colors.onSurfaceVariant
                        },
                        fontStyle = if (typing == null && last?.recalled == true) FontStyle.Italic else FontStyle.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (mentioned) {
                        Spacer(Modifier.width(8.dp))
                        CountBadge("@", colors.primary, colors.onPrimary)
                    }
                    if (unread) {
                        Spacer(Modifier.width(6.dp))
                        CountBadge(
                            text = if (thread.unread > 99) "99+" else thread.unread.toString(),
                            color = if (thread.isMuted) colors.outline else colors.primary,
                            contentColor = if (thread.isMuted) colors.surface else colors.onPrimary,
                        )
                    }
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu, offset = DpOffset(72.dp, 0.dp)) {
            MenuEntry(
                if (thread.pinned) "Unpin" else "Pin",
                if (thread.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
            ) { onAction(if (thread.pinned) RowAction.UNPIN else RowAction.PIN) }
            if (thread.isMuted) {
                MenuEntry("Unmute", Icons.Outlined.Notifications) { onAction(RowAction.UNMUTE) }
            } else {
                MenuEntry("Mute…", Icons.Outlined.NotificationsOff) { onAction(RowAction.MUTE) }
            }
            if (unread) {
                MenuEntry("Mark as read", Icons.Outlined.Drafts) { onAction(RowAction.READ) }
            } else {
                MenuEntry("Mark as unread", Icons.Outlined.MarkChatUnread) { onAction(RowAction.UNREAD) }
            }
            HorizontalDivider()
            MenuEntry("Delete", Icons.Outlined.Delete, destructive = true) { onAction(RowAction.DELETE) }
        }
    }
}

@Composable
fun MenuEntry(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(label, color = color) },
        leadingIcon = { Icon(icon, null, tint = color) },
        onClick = onClick,
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
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                Icons.AutoMirrored.Outlined.Chat,
                null,
                Modifier.size(56.dp).padding(bottom = 8.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("No chats yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "New messages appear here. Tap the pencil to write to a friend or group.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}
