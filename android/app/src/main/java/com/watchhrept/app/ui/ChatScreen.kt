package com.watchhrept.app.ui

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchhrept.app.data.ChatMessage
import com.watchhrept.app.data.GroupMember
import com.watchhrept.app.data.MuteOption
import com.watchhrept.app.data.QUICK_REACTIONS
import com.watchhrept.app.data.ThreadSummary
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

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

private enum class MessageAction { REPLY, COPY, FORWARD, RECALL, DELETE }

/** Messages from the same person this close together are drawn as one group of bubbles. */
private const val GROUP_GAP_MS = 5 * 60_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chat: ChatState, thread: ThreadSummary?, typing: TypingInfo?, actions: ChatActions) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val colors = LocalChatColors.current
    var menuFor by remember { mutableStateOf<String?>(null) }
    var replyTo by remember { mutableStateOf<ChatMessage?>(null) }
    var confirm by remember { mutableStateOf<Pair<MessageAction, ChatMessage>?>(null) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val isGroup = chat.kind == "group"

    val onAction: (ChatMessage, MessageAction) -> Unit = { msg, action ->
        menuFor = null
        when (action) {
            MessageAction.REPLY -> replyTo = msg
            MessageAction.COPY -> {
                clipboard.setText(AnnotatedString(msg.text))
                // Android 13+ confirms copies itself.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }
            }
            MessageAction.FORWARD -> actions.onForward(msg)
            MessageAction.RECALL, MessageAction.DELETE -> confirm = action to msg
        }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(chat.name, thread?.avatar.orEmpty(), key = chat.ref.key, isGroup = isGroup, size = 36.dp)
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
                actions = {
                    ChatMenu(
                        thread = thread,
                        onPin = actions.onPin,
                        onMute = { dialog = "mute" },
                        onUnmute = { actions.onMute(null) },
                        onMarkUnread = actions.onMarkUnread,
                        onDelete = { dialog = "delete" },
                    )
                },
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
            MessageList(
                chat = chat,
                typing = typing,
                menuFor = menuFor,
                onOpenMenu = { menuFor = it.id },
                onDismissMenu = { menuFor = null },
                onAction = onAction,
                onReact = { msg, key ->
                    menuFor = null
                    actions.onReact(msg, key)
                },
                onSwipeReply = { replyTo = it },
                onLoadEarlier = actions.onLoadEarlier,
            )
            if (chat.loading && chat.messages.isEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }

    confirm?.let { (action, msg) ->
        val recall = action == MessageAction.RECALL
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (recall) "Recall this message?" else "Delete this message?") },
            text = { Text(if (recall) "It is removed for everyone in the chat." else "It is removed only for you.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    if (recall) actions.onRecall(msg) else actions.onDelete(msg)
                }) { Text(if (recall) "Recall" else "Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
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
        "delete" -> DeleteConversationDialog(
            onDismiss = { dialog = null },
            onConfirm = {
                dialog = null
                actions.onDeleteConversation()
            },
        )
    }
}

@Composable
private fun ChatMenu(
    thread: ThreadSummary?,
    onPin: (Boolean) -> Unit,
    onMute: () -> Unit,
    onUnmute: () -> Unit,
    onMarkUnread: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Chat options") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val pinned = thread?.pinned == true
            MenuEntry(if (pinned) "Unpin" else "Pin to top", if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin) {
                open = false
                onPin(!pinned)
            }
            if (thread?.isMuted == true) {
                MenuEntry("Unmute", Icons.Outlined.Notifications) {
                    open = false
                    onUnmute()
                }
            } else {
                MenuEntry("Mute…", Icons.Outlined.NotificationsOff) {
                    open = false
                    onMute()
                }
            }
            MenuEntry("Mark as unread", Icons.Outlined.MarkChatUnread) {
                open = false
                onMarkUnread()
            }
            HorizontalDivider()
            MenuEntry("Delete conversation", Icons.Outlined.Delete, destructive = true) {
                open = false
                onDelete()
            }
        }
    }
}

@Composable
private fun MessageList(
    chat: ChatState,
    typing: TypingInfo?,
    menuFor: String?,
    onOpenMenu: (ChatMessage) -> Unit,
    onDismissMenu: () -> Unit,
    onAction: (ChatMessage, MessageAction) -> Unit,
    onReact: (ChatMessage, String) -> Unit,
    onSwipeReply: (ChatMessage) -> Unit,
    onLoadEarlier: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Newest at the bottom: reverseLayout keeps the view pinned to the latest message.
    val reversed = remember(chat.messages) { chat.messages.asReversed() }
    val isGroup = chat.kind == "group"
    val unreadCount = remember(chat.messages, chat.firstUnreadId) {
        val start = chat.messages.indexOfFirst { it.id == chat.firstUnreadId }
        if (start < 0) 0 else chat.messages.drop(start).count { !it.isSelf }
    }

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
    // Follow new messages (and "typing…") when already at the bottom.
    LaunchedEffect(chat.messages.lastOrNull()?.id, typing != null) {
        if (listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
    }
    val showJump by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (typing != null) {
                item(key = "typing") { TypingBubble(isGroup, typing) }
            }
            items(reversed.size, key = { reversed[it].id }) { i ->
                val msg = reversed[i]
                val older = reversed.getOrNull(i + 1)
                val newer = reversed.getOrNull(i - 1)
                val firstOfRun = older == null || older.fromId != msg.fromId || !sameDay(older.ts, msg.ts) ||
                    msg.ts - older.ts > GROUP_GAP_MS || msg.id == chat.firstUnreadId
                val lastOfRun = newer == null || newer.fromId != msg.fromId || !sameDay(newer.ts, msg.ts) ||
                    newer.ts - msg.ts > GROUP_GAP_MS || newer.id == chat.firstUnreadId
                Column {
                    if (older == null || !sameDay(older.ts, msg.ts)) DayHeader(msg.ts)
                    if (msg.id == chat.firstUnreadId && unreadCount > 0) UnreadDivider(unreadCount)
                    MessageRow(
                        msg = msg,
                        isGroup = isGroup,
                        firstOfRun = firstOfRun,
                        lastOfRun = lastOfRun,
                        menuOpen = menuFor == msg.id,
                        onLongPress = { onOpenMenu(msg) },
                        onDismissMenu = onDismissMenu,
                        onAction = { onAction(msg, it) },
                        onReact = { onReact(msg, it) },
                        onSwipeReply = { onSwipeReply(msg) },
                    )
                }
            }
            if (chat.loadingMore) {
                item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
            }
        }
        AnimatedVisibility(
            visible = showJump,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            SmallFloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
            ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Jump to latest") }
        }
    }
}

@Composable
private fun DayHeader(ts: Long) {
    Box(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            formatDay(ts),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UnreadDivider(count: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Text(
            if (count == 1) "1 unread message" else "$count unread messages",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth(),
        )
    }
}

@Composable
private fun MessageRow(
    msg: ChatMessage,
    isGroup: Boolean,
    firstOfRun: Boolean,
    lastOfRun: Boolean,
    menuOpen: Boolean,
    onLongPress: () -> Unit,
    onDismissMenu: () -> Unit,
    onAction: (MessageAction) -> Unit,
    onReact: (String) -> Unit,
    onSwipeReply: () -> Unit,
) {
    val self = msg.isSelf
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    val maxBubble = (LocalConfiguration.current.screenWidthDp * 0.78f).dp

    Box(Modifier.fillMaxWidth().padding(top = if (firstOfRun) 8.dp else 2.dp)) {
        // Revealed while swiping a message to the right to reply to it.
        if (offsetX.value > 0f) {
            Icon(
                Icons.AutoMirrored.Filled.Reply,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .graphicsLayer {
                        val progress = (offsetX.value / threshold).coerceIn(0f, 1f)
                        alpha = progress
                        scaleX = 0.6f + 0.4f * progress
                        scaleY = 0.6f + 0.4f * progress
                    },
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(msg.id, msg.recalled) {
                    if (msg.recalled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX.value >= threshold) onSwipeReply()
                            scope.launch { offsetX.animateTo(0f) }
                        },
                        onDragCancel = { scope.launch { offsetX.animateTo(0f) } },
                        onHorizontalDrag = { change, delta ->
                            val next = (offsetX.value + delta).coerceIn(0f, threshold * 1.3f)
                            if (offsetX.value < threshold && next >= threshold) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            scope.launch { offsetX.snapTo(next) }
                            change.consume()
                        },
                    )
                },
            horizontalArrangement = if (self) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
        ) {
            if (isGroup && !self) {
                Box(Modifier.width(36.dp)) {
                    if (lastOfRun) Avatar(msg.fromName, "", key = msg.fromId, isGroup = false, size = 28.dp)
                }
            }
            Column(
                horizontalAlignment = if (self) Alignment.End else Alignment.Start,
                modifier = Modifier.widthIn(max = maxBubble),
            ) {
                Box {
                    Bubble(msg, isGroup, firstOfRun, lastOfRun, onLongPress)
                    MessageMenu(expanded = menuOpen, msg = msg, onDismiss = onDismissMenu, onAction = onAction, onReact = onReact)
                }
                if (msg.reactions.isNotEmpty()) {
                    Reactions(msg, Modifier.offset(y = (-6).dp).padding(horizontal = 10.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(msg: ChatMessage, isGroup: Boolean, firstOfRun: Boolean, lastOfRun: Boolean, onLongPress: () -> Unit) {
    val self = msg.isSelf
    val colors = LocalChatColors.current
    val big = 18.dp
    val small = 4.dp
    val shape = if (self) {
        RoundedCornerShape(topStart = big, topEnd = if (firstOfRun) big else small, bottomEnd = if (lastOfRun) big else small, bottomStart = big)
    } else {
        RoundedCornerShape(topStart = if (firstOfRun) big else small, topEnd = big, bottomEnd = big, bottomStart = if (lastOfRun) big else small)
    }
    val container = if (self) colors.outgoing else colors.incoming
    val content = if (self) colors.onOutgoing else colors.onIncoming
    val meta = content.copy(alpha = 0.75f)

    Surface(
        shape = shape,
        color = container,
        contentColor = content,
        modifier = Modifier.clip(shape).combinedClickable(onClick = {}, onLongClick = onLongPress),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (isGroup && !self && firstOfRun && msg.fromName.isNotBlank()) {
                Text(
                    msg.fromName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = senderNameColor(msg.fromId),
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            msg.quote?.let { q -> QuoteBlock(q.fromName, q.text, onOutgoing = self) }
            val footer: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatClock(msg.ts), style = MaterialTheme.typography.labelSmall, color = meta)
                    if (self && !msg.recalled) {
                        Spacer(Modifier.width(4.dp))
                        DeliveryIcon(deliveryState(msg), tint = meta, background = container)
                    }
                }
            }
            if (msg.recalled) {
                TextWithFooter(
                    text = AnnotatedString("Message recalled"),
                    style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                    color = meta,
                    footer = footer,
                )
            } else {
                TextWithFooter(text = messageText(msg), style = MaterialTheme.typography.bodyLarge, color = content, footer = footer)
            }
        }
    }
}

/** Message text with @mentions emphasized and attachments named. */
private fun messageText(msg: ChatMessage): AnnotatedString = buildAnnotatedString {
    val text = msg.text
    append(text)
    for (m in msg.mentions) {
        val start = m.pos.coerceIn(0, text.length)
        val end = (m.pos + m.len).coerceIn(start, text.length)
        if (end > start) addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
    }
    msg.attachment?.let { a ->
        val label = a.title?.takeIf { it.isNotBlank() } ?: a.href ?: "Attachment"
        if (text.isNotBlank()) append("\n")
        append("📎 $label")
    }
}

/**
 * Text followed by the time and check marks: on the last line when they fit there, as chat apps do,
 * otherwise on a line of their own.
 */
@Composable
private fun TextWithFooter(text: AnnotatedString, style: TextStyle, color: Color, footer: @Composable () -> Unit) {
    val textLayout = remember { arrayOfNulls<TextLayoutResult>(1) }
    Layout(
        content = {
            Text(text, style = style, color = color, onTextLayout = { textLayout[0] = it })
            footer()
        },
    ) { measurables, constraints ->
        val textPlaceable = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val footerPlaceable = measurables[1].measure(Constraints())
        val gap = 8.dp.roundToPx()
        val result = textLayout[0]
        val lastLineWidth = if (result != null && result.lineCount > 0) {
            val last = result.lineCount - 1
            ceil(result.getLineRight(last) - result.getLineLeft(last)).toInt()
        } else {
            textPlaceable.width
        }
        val empty = text.isEmpty()
        val inline = !empty && lastLineWidth + gap + footerPlaceable.width <= constraints.maxWidth
        val width: Int
        val height: Int
        when {
            empty -> {
                width = footerPlaceable.width
                height = footerPlaceable.height
            }
            inline -> {
                width = maxOf(textPlaceable.width, lastLineWidth + gap + footerPlaceable.width)
                height = maxOf(textPlaceable.height, footerPlaceable.height)
            }
            else -> {
                width = maxOf(textPlaceable.width, footerPlaceable.width)
                height = textPlaceable.height + footerPlaceable.height
            }
        }
        layout(width, height) {
            if (!empty) textPlaceable.place(0, 0)
            footerPlaceable.place(width - footerPlaceable.width, height - footerPlaceable.height)
        }
    }
}

@Composable
private fun QuoteBlock(from: String, text: String, onOutgoing: Boolean) {
    val colors = LocalChatColors.current
    val bar = if (onOutgoing) colors.onOutgoing else MaterialTheme.colorScheme.primary
    val background = if (onOutgoing) Color.White.copy(alpha = 0.18f) else colors.background.copy(alpha = 0.65f)
    Row(
        Modifier
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .height(IntrinsicSize.Min),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(bar))
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                from.ifBlank { "Message" },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Reactions(msg: ChatMessage, modifier: Modifier = Modifier) {
    val colors = LocalChatColors.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        msg.reactions.forEach { r ->
            Surface(
                shape = CircleShape,
                color = if (r.mine) MaterialTheme.colorScheme.secondaryContainer else colors.incoming,
                contentColor = colors.onIncoming,
                border = BorderStroke(2.dp, colors.background),
            ) {
                Text(
                    if (r.count > 1) "${r.emoji} ${r.count}" else r.emoji,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/** Long-press menu: a row of reactions above the message actions. */
@Composable
private fun MessageMenu(
    expanded: Boolean,
    msg: ChatMessage,
    onDismiss: () -> Unit,
    onAction: (MessageAction) -> Unit,
    onReact: (String) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        if (!msg.recalled) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                QUICK_REACTIONS.forEach { (key, emoji) ->
                    val mine = msg.reactions.any { it.mine && it.key == key }
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (mine) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .clickable { onReact(key) },
                        contentAlignment = Alignment.Center,
                    ) { Text(emoji, fontSize = 24.sp) }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            MenuEntry("Reply", Icons.AutoMirrored.Outlined.Reply) { onAction(MessageAction.REPLY) }
            if (msg.text.isNotBlank()) MenuEntry("Copy text", Icons.Outlined.ContentCopy) { onAction(MessageAction.COPY) }
            if (msg.isText) MenuEntry("Forward", Icons.AutoMirrored.Outlined.Forward) { onAction(MessageAction.FORWARD) }
            if (msg.isSelf) MenuEntry("Recall for everyone", Icons.AutoMirrored.Outlined.Undo) { onAction(MessageAction.RECALL) }
        }
        MenuEntry("Delete for me", Icons.Outlined.DeleteOutline, destructive = true) { onAction(MessageAction.DELETE) }
    }
}

/** "…" bubble while the other side is typing. */
@Composable
private fun TypingBubble(isGroup: Boolean, typing: TypingInfo) {
    val colors = LocalChatColors.current
    val transition = rememberInfiniteTransition(label = "typing")
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
        if (isGroup) {
            Box(Modifier.width(36.dp)) { Avatar(typing.name, "", key = typing.name, isGroup = false, size = 28.dp) }
        }
        Surface(shape = RoundedCornerShape(18.dp), color = colors.incoming) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                repeat(3) { i ->
                    val dotAlpha by transition.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(durationMillis = 450, delayMillis = i * 150), RepeatMode.Reverse),
                        label = "dot$i",
                    )
                    Box(
                        Modifier
                            .size(8.dp)
                            .graphicsLayer { alpha = dotAlpha }
                            .background(colors.onIncoming.copy(alpha = 0.7f), CircleShape),
                    )
                }
            }
        }
    }
}

/** Message input: a rounded field with an emoji button, a reply preview, and a send button once there is text. */
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
    val colors = LocalChatColors.current
    var value by remember { mutableStateOf(TextFieldValue("")) }
    val mentioned = remember { mutableStateListOf<GroupMember>() }
    var showEmoji by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    val imeVisible by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
    // The keyboard and the emoji panel take turns.
    LaunchedEffect(imeVisible) { if (imeVisible) showEmoji = false }
    BackHandler(enabled = showEmoji) { showEmoji = false }

    // The "@query" being typed right before the cursor, if any.
    val mentionQuery = remember(value, isGroup) { if (isGroup) mentionQueryAt(value) else null }
    LaunchedEffect(mentionQuery != null) { if (mentionQuery != null) onLoadMembers() }
    val suggestions = remember(mentionQuery, members) {
        val q = mentionQuery?.let { foldForSearch(it.second) }
        if (q == null) emptyList() else members.orEmpty().filter { foldForSearch(it.name).contains(q) }.take(6)
    }

    fun replaceSelection(insert: String) {
        val start = value.selection.min
        value = TextFieldValue(value.text.replaceRange(start, value.selection.max, insert), TextRange(start + insert.length))
    }

    fun insertMention(member: GroupMember) {
        val (start, _) = mentionQuery ?: return
        val end = value.selection.start
        val tag = "@${member.name} "
        value = TextFieldValue(value.text.replaceRange(start, end, tag), TextRange(start + tag.length))
        mentioned += member
    }

    fun backspace() {
        if (!value.selection.collapsed) {
            replaceSelection("")
            return
        }
        val cursor = value.selection.start
        if (cursor == 0) return
        val start = previousCharacterStart(value.text, cursor)
        value = TextFieldValue(value.text.removeRange(start, cursor), TextRange(start))
    }

    Column(Modifier.background(colors.background).navigationBarsPadding().imePadding()) {
        if (suggestions.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    suggestions.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().clickable { insertMention(m) }.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(m.name, "", key = m.id, isGroup = false, size = 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(m.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
        Row(
            Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f),
            ) {
                Column {
                    replyTo?.let { ReplyPreview(it, onCancelReply) }
                    Row(verticalAlignment = Alignment.Bottom) {
                        IconButton(onClick = {
                            if (showEmoji) {
                                showEmoji = false
                                runCatching { focus.requestFocus() }
                                keyboard?.show()
                            } else {
                                keyboard?.hide()
                                showEmoji = true
                            }
                        }) {
                            Icon(
                                if (showEmoji) Icons.Outlined.Keyboard else Icons.Outlined.EmojiEmotions,
                                contentDescription = if (showEmoji) "Show keyboard" else "Emoji",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        BasicTextField(
                            value = value,
                            onValueChange = {
                                if (it.text != value.text && it.text.isNotEmpty()) onStartTyping()
                                value = it
                            },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            maxLines = 6,
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 13.dp, bottom = 13.dp, end = 16.dp)
                                .focusRequester(focus),
                            decorationBox = { inner ->
                                Box {
                                    if (value.text.isEmpty()) {
                                        Text(
                                            "Message",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = value.text.isNotBlank(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                FilledIconButton(
                    onClick = {
                        onSend(value.text, mentioned.toList()) {
                            value = TextFieldValue("")
                            mentioned.clear()
                        }
                    },
                    enabled = !sending,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = colors.outgoing,
                        contentColor = colors.onOutgoing,
                    ),
                    modifier = Modifier.padding(start = 8.dp).size(48.dp),
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
            }
        }
        if (showEmoji) {
            EmojiPanel(
                onEmoji = { emoji ->
                    replaceSelection(emoji)
                    onStartTyping()
                },
                onBackspace = { backspace() },
            )
        }
    }
}

@Composable
private fun ReplyPreview(msg: ChatMessage, onCancel: () -> Unit) {
    Row(
        Modifier
            .padding(start = 8.dp, end = 8.dp, top = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                if (msg.isSelf) "You" else msg.fromName.ifBlank { "Message" },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                if (msg.recalled) "Message recalled" else msg.text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onCancel) { Icon(Icons.Outlined.Close, contentDescription = "Cancel reply") }
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
