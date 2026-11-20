package com.watchhrept.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.watchhrept.app.data.ChatMessage
import com.watchhrept.app.data.GroupMember
import com.watchhrept.app.data.MuteOption
import com.watchhrept.app.data.QUICK_REACTIONS
import com.watchhrept.app.data.ThreadSummary

/** Everything the chat screen can ask for. */
class ChatActions(
    val onBack: () -> Unit,
    val onSend: (text: String, quote: ChatMessage?, mentioned: List<GroupMember>, onSent: () -> Unit) -> Unit,
    val onStartTyping: () -> Unit,
    val onLoadMembers: () -> Unit,
    val onLoadEarlier: () -> Unit,
    val onReact: (ChatMessage, String) -> Unit,
    val onRecall: (ChatMessage) -> Unit,
    val onDelete: (ChatMessage) -> Unit,
    val onForward: (ChatMessage) -> Unit,
    val onPin: (Boolean) -> Unit,
    val onMute: (MuteOption?) -> Unit,
    val onMarkUnread: () -> Unit,
    val onDeleteConversation: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chat: ChatState, thread: ThreadSummary?, typing: TypingInfo?, actions: ChatActions) {
    var sheetFor by remember { mutableStateOf<String?>(null) }
    var replyTo by remember { mutableStateOf<ChatMessage?>(null) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val isGroup = chat.kind == "group"

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(chat.name, thread?.avatar.orEmpty(), isGroup = isGroup, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(chat.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            val subtitle = when {
                                typing != null -> if (isGroup && typing.name.isNotBlank()) "${typing.name} is typing…" else "typing…"
                                chat.lastOnline != null -> formatPresence(chat.lastOnline)
                                isGroup && chat.members != null -> "${chat.members.size + 1} members"
                                else -> null
                            }
                            subtitle?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (typing != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                },
                actions = { ChatMenu(thread, onPin = actions.onPin, onDialog = { dialog = it }, onUnmute = { actions.onMute(null) }, onMarkUnread = actions.onMarkUnread) },
            )
        },
        bottomBar = {
            Composer(
                isGroup = isGroup,
                members = chat.members,
                sending = chat.sending,
                replyTo = replyTo,
                onCancelReply = { replyTo = null },
                onStartTyping = actions.onStartTyping,
                onLoadMembers = actions.onLoadMembers,
                onSend = { text, mentioned, onSent ->
                    actions.onSend(text, replyTo, mentioned) {
                        replyTo = null
                        onSent()
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (chat.loading && chat.messages.isEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            MessageList(chat, onLongPress = { sheetFor = it.id }, onLoadEarlier = actions.onLoadEarlier)
        }
    }

    // Look the message up each time so reactions and receipts update while the sheet is open.
    val sheetMsg = sheetFor?.let { id -> chat.messages.firstOrNull { it.id == id } }
    if (sheetMsg != null) {
        MessageSheet(
            msg = sheetMsg,
            onDismiss = { sheetFor = null },
            onReact = { key ->
                actions.onReact(sheetMsg, key)
                sheetFor = null
            },
            onReply = {
                replyTo = sheetMsg
                sheetFor = null
            },
            onForward = {
                sheetFor = null
                actions.onForward(sheetMsg)
            },
            onRecall = {
                sheetFor = null
                actions.onRecall(sheetMsg)
            },
            onDelete = {
                sheetFor = null
                actions.onDelete(sheetMsg)
            },
        )
    }

    when (dialog) {
        "mute" -> MuteDialog(
            onDismiss = { dialog = null },
            onPick = {
                dialog = null
                actions.onMute(it)
            },
        )
        "delete" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete conversation?") },
            text = { Text("The chat history is deleted for you. Others in the chat still see it.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    actions.onDeleteConversation()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ChatMenu(
    thread: ThreadSummary?,
    onPin: (Boolean) -> Unit,
    onDialog: (String) -> Unit,
    onUnmute: () -> Unit,
    onMarkUnread: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Chat options") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val pinned = thread?.pinned == true
            DropdownMenuItem(
                text = { Text(if (pinned) "Unpin" else "Pin to top") },
                leadingIcon = { Icon(if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, null) },
                onClick = {
                    open = false
                    onPin(!pinned)
                },
            )
            if (thread?.isMuted == true) {
                DropdownMenuItem(
                    text = { Text("Unmute") },
                    leadingIcon = { Icon(Icons.Outlined.Notifications, null) },
                    onClick = {
                        open = false
                        onUnmute()
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Mute…") },
                    leadingIcon = { Icon(Icons.Outlined.NotificationsOff, null) },
                    onClick = {
                        open = false
                        onDialog("mute")
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Mark as unread") },
                leadingIcon = { Icon(Icons.Outlined.MarkChatUnread, null) },
                onClick = {
                    open = false
                    onMarkUnread()
                },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Delete conversation", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    open = false
                    onDialog("delete")
                },
            )
        }
    }
}

@Composable
private fun MessageList(chat: ChatState, onLongPress: (ChatMessage) -> Unit, onLoadEarlier: () -> Unit) {
    val listState = rememberLazyListState()
    // Newest at the bottom: reverseLayout keeps the view pinned to the latest message.
    val reversed = remember(chat.messages) { chat.messages.asReversed() }
    val lastSelfId = remember(chat.messages) { chat.messages.lastOrNull { it.isSelf && !it.recalled }?.id }
    val isGroup = chat.kind == "group"

    val nearTop by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }
    // Re-checked after each page, so it keeps loading while the top of the chat is in view.
    LaunchedEffect(listState, chat.hasMore, chat.messages.size) {
        snapshotFlow { nearTop }.collect { if (it && chat.hasMore) onLoadEarlier() }
    }
    // Follow new messages when already at the bottom.
    LaunchedEffect(chat.messages.lastOrNull()?.id) {
        if (listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
    }

    LazyColumn(
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(reversed.size, key = { reversed[it].id }) { i ->
            val msg = reversed[i]
            val older = reversed.getOrNull(i + 1)
            val newer = reversed.getOrNull(i - 1)
            val firstOfRun = older == null || older.fromId != msg.fromId || !sameDay(older.ts, msg.ts) ||
                msg.ts - older.ts > 10 * 60_000
            val lastOfRun = newer == null || newer.fromId != msg.fromId || !sameDay(newer.ts, msg.ts) ||
                newer.ts - msg.ts > 10 * 60_000
            Column {
                if (older == null || !sameDay(older.ts, msg.ts)) DayHeader(msg.ts)
                MessageBubble(
                    msg = msg,
                    isGroup = isGroup,
                    firstOfRun = firstOfRun,
                    lastOfRun = lastOfRun,
                    showStatus = msg.id == lastSelfId,
                    onLongPress = { onLongPress(msg) },
                )
            }
        }
        if (chat.loadingMore) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
        }
    }
}

@Composable
private fun DayHeader(ts: Long) {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(
                formatDay(ts),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    msg: ChatMessage,
    isGroup: Boolean,
    firstOfRun: Boolean,
    lastOfRun: Boolean,
    showStatus: Boolean,
    onLongPress: () -> Unit,
) {
    val self = msg.isSelf
    val big = 20.dp
    val small = 6.dp
    val shape = if (self) {
        RoundedCornerShape(big, if (firstOfRun) big else small, if (lastOfRun) big else small, big)
    } else {
        RoundedCornerShape(if (firstOfRun) big else small, big, big, if (lastOfRun) big else small)
    }
    val container = if (self) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (self) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Row(
        Modifier.fillMaxWidth().padding(top = if (firstOfRun) 6.dp else 1.dp),
        horizontalArrangement = if (self) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (isGroup && !self) {
            Box(Modifier.width(36.dp)) {
                if (lastOfRun) Avatar(msg.fromName, "", isGroup = false, size = 28.dp)
            }
        }
        Column(
            horizontalAlignment = if (self) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            if (isGroup && !self && firstOfRun && msg.fromName.isNotBlank()) {
                Text(
                    msg.fromName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp, bottom = 2.dp),
                )
            }
            Surface(
                shape = shape,
                color = container,
                contentColor = content,
                modifier = Modifier.clip(shape).combinedClickable(onClick = {}, onLongClick = onLongPress),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    msg.quote?.let { q -> QuoteBlock(q.fromName, q.text, self) }
                    when {
                        msg.recalled -> Text(
                            "Message recalled",
                            fontStyle = FontStyle.Italic,
                            color = content.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        else -> {
                            if (msg.text.isNotBlank()) Text(msg.text, style = MaterialTheme.typography.bodyLarge)
                            msg.attachment?.let { a ->
                                val label = a.title?.takeIf { it.isNotBlank() } ?: a.href ?: "Attachment"
                                Text(
                                    "📎 $label",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Text(
                        formatClock(msg.ts),
                        style = MaterialTheme.typography.labelSmall,
                        color = content.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
            if (msg.reactions.isNotEmpty()) {
                Row(
                    Modifier.padding(top = 2.dp, start = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    msg.reactions.forEach { r ->
                        Surface(
                            shape = CircleShape,
                            color = if (r.mine) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Text(
                                if (r.count > 1) "${r.emoji} ${r.count}" else r.emoji,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
            if (showStatus) {
                statusLabel(msg)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, end = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuoteBlock(from: String, text: String, onPrimary: Boolean) {
    val bar = if (onPrimary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    Row(Modifier.padding(bottom = 6.dp).height(IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(bar, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(8.dp))
        Column {
            if (from.isNotBlank()) Text(from, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageSheet(
    msg: ChatMessage,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onRecall: () -> Unit,
    onDelete: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var confirm by remember { mutableStateOf<String?>(null) }
    val itemColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            if (!msg.recalled) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    QUICK_REACTIONS.forEach { (key, emoji) ->
                        val mine = msg.reactions.any { it.mine && it.key == key }
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (mine) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
                                .clickable { onReact(key) },
                            contentAlignment = Alignment.Center,
                        ) { Text(emoji, style = MaterialTheme.typography.titleLarge) }
                    }
                }
                ListItem(
                    colors = itemColors,
                    modifier = Modifier.clickable(onClick = onReply),
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Reply, null) },
                    headlineContent = { Text("Reply") },
                )
                if (msg.text.isNotBlank()) {
                    ListItem(
                        colors = itemColors,
                        modifier = Modifier.clickable {
                            clipboard.setText(AnnotatedString(msg.text))
                            onDismiss()
                        },
                        leadingContent = { Icon(Icons.Outlined.ContentCopy, null) },
                        headlineContent = { Text("Copy text") },
                    )
                }
                if (msg.isText) {
                    ListItem(
                        colors = itemColors,
                        modifier = Modifier.clickable(onClick = onForward),
                        leadingContent = { Icon(Icons.AutoMirrored.Outlined.Forward, null) },
                        headlineContent = { Text("Forward") },
                    )
                }
                if (msg.isSelf) {
                    ListItem(
                        colors = itemColors,
                        modifier = Modifier.clickable { confirm = "recall" },
                        leadingContent = { Icon(Icons.AutoMirrored.Outlined.Undo, null) },
                        headlineContent = { Text("Recall for everyone") },
                    )
                }
            }
            ListItem(
                colors = itemColors,
                modifier = Modifier.clickable { confirm = "delete" },
                leadingContent = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                headlineContent = { Text("Delete for me", color = MaterialTheme.colorScheme.error) },
            )
        }
    }

    confirm?.let { what ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (what == "recall") "Recall this message?" else "Delete this message?") },
            text = {
                Text(
                    if (what == "recall") "It is removed for everyone in the chat." else "It is removed only on your devices.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    if (what == "recall") onRecall() else onDelete()
                }) { Text(if (what == "recall") "Recall" else "Delete") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MuteDialog(onDismiss: () -> Unit, onPick: (MuteOption) -> Unit) {
    var choice by rememberSaveable { mutableStateOf(MuteOption.ONE_HOUR) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mute notifications") },
        text = {
            Column {
                MuteOption.entries.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = choice == option, onClick = { choice = option })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == option, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(option.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(choice) }) { Text("Mute") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Text input with reply preview, @mention suggestions (groups) and a send button. */
@Composable
private fun Composer(
    isGroup: Boolean,
    members: List<GroupMember>?,
    sending: Boolean,
    replyTo: ChatMessage?,
    onCancelReply: () -> Unit,
    onStartTyping: () -> Unit,
    onLoadMembers: () -> Unit,
    onSend: (String, List<GroupMember>, onSent: () -> Unit) -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue("")) }
    val mentioned = remember { mutableStateListOf<GroupMember>() }
    // The "@query" being typed right before the cursor, if any.
    val mentionQuery = remember(value, isGroup) { if (isGroup) mentionQueryAt(value) else null }
    LaunchedEffect(mentionQuery != null) { if (mentionQuery != null) onLoadMembers() }
    val suggestions = remember(mentionQuery, members) {
        val q = mentionQuery?.let { foldForSearch(it.second) }
        if (q == null) emptyList() else members.orEmpty().filter { foldForSearch(it.name).contains(q) }.take(6)
    }

    fun insertMention(member: GroupMember) {
        val (start, _) = mentionQuery ?: return
        val end = value.selection.start
        val tag = "@${member.name} "
        val text = value.text.replaceRange(start, end, tag)
        value = TextFieldValue(text, TextRange(start + tag.length))
        mentioned += member
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
        Column(Modifier.navigationBarsPadding().imePadding()) {
            if (suggestions.isNotEmpty()) {
                Column(Modifier.heightIn(max = 240.dp)) {
                    suggestions.forEach { m ->
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier.clickable { insertMention(m) },
                            leadingContent = { Avatar(m.name, "", isGroup = false, size = 32.dp) },
                            headlineContent = { Text(m.name) },
                        )
                    }
                    HorizontalDivider()
                }
            }
            replyTo?.let { q ->
                Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Reply, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (q.isSelf) "Replying to yourself" else "Replying to ${q.fromName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(q.text, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onCancelReply) { Icon(Icons.Outlined.Close, contentDescription = "Cancel reply") }
                }
            }
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (isGroup) {
                    IconButton(onClick = {
                        val start = value.selection.min
                        val needsSpace = start > 0 && !value.text[start - 1].isWhitespace()
                        val insert = if (needsSpace) " @" else "@"
                        value = TextFieldValue(
                            value.text.replaceRange(start, value.selection.max, insert),
                            TextRange(start + insert.length),
                        )
                    }) { Icon(Icons.Outlined.AlternateEmail, contentDescription = "Mention someone") }
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        if (it.text != value.text && it.text.isNotEmpty()) onStartTyping()
                        value = it
                    },
                    placeholder = { Text("Message") },
                    maxLines = 6,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        onSend(value.text, mentioned.toList()) {
                            value = TextFieldValue("")
                            mentioned.clear()
                        }
                    },
                    enabled = value.text.isNotBlank() && !sending,
                    modifier = Modifier.size(52.dp),
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
            }
        }
    }
}

/** Start index of "@" and the text typed after it, when the cursor is inside an @mention being typed. */
internal fun mentionQueryAt(value: TextFieldValue): Pair<Int, String>? {
    val cursor = value.selection.start
    if (!value.selection.collapsed || cursor <= 0) return null
    val before = value.text.substring(0, cursor)
    val at = before.lastIndexOf('@')
    if (at < 0) return null
    if (at > 0 && !before[at - 1].isWhitespace()) return null
    val query = before.substring(at + 1)
    if (query.length > 30 || '\n' in query || query.startsWith(" ")) return null
    return at to query
}
