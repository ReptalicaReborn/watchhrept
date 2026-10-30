package com.watchhrept.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import kotlinx.coroutines.delay

private object Routes {
    const val HOME = "home"
    const val NEW_CHAT = "new_chat"
    const val CHAT = "chat"
    const val MESSAGE = "message"
    const val FORWARD = "forward"
    const val MENTION = "mention"
    const val CHAT_OPTIONS = "chat_options"
    const val MUTE = "mute"
    const val SETTINGS = "settings"
}

@Composable
fun WatchApp(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()

    MaterialTheme {
        Box(Modifier.fillMaxSize()) {
            if (!state.settings.isConfigured) {
                SettingsScreen(
                    current = state.settings,
                    loggedIn = false,
                    onSave = vm::saveSettings,
                    onRestartServer = vm::restartLocalServer,
                    onLogout = vm::logout,
                )
            } else {
                AppNav(vm)
            }
            state.error?.let { ErrorToast(it, onDismiss = vm::clearError) }
        }
    }
}

@Composable
private fun AppNav(vm: AppViewModel) {
    val nav = rememberSwipeDismissableNavController()

    // Each destination collects state itself so it never renders a stale capture.
    SwipeDismissableNavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val state by vm.state.collectAsStateWithLifecycle()
            val status = state.status
            val connectionError = state.connectionError
            when {
                status == null && connectionError != null -> ConnectionErrorScreen(
                    error = connectionError,
                    onRetry = vm::startLiveUpdates,
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                )
                status == null -> LoadingScreen(if (state.settings.onWatch) "Starting Zalo…" else null)
                !status.isLoggedIn -> LoginScreen(
                    status = status,
                    onLogin = vm::login,
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                )
                else -> ThreadListScreen(
                    state = state,
                    onOpen = { thread ->
                        vm.openChat(thread)
                        nav.navigate(Routes.CHAT)
                    },
                    onNewChat = { nav.navigate(Routes.NEW_CHAT) },
                    onReconnect = vm::reconnectZalo,
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
        }
        composable(Routes.NEW_CHAT) {
            val state by vm.state.collectAsStateWithLifecycle()
            ContactsScreen("New chat", state.threads, onPick = { thread ->
                vm.openChat(thread)
                nav.popBackStack()
                nav.navigate(Routes.CHAT)
            })
        }
        composable(Routes.CHAT) {
            val state by vm.state.collectAsStateWithLifecycle()
            DisposableEffect(Unit) { onDispose { vm.closeChat() } }
            state.chat?.let { chat ->
                ChatScreen(
                    chat = chat,
                    typing = state.typing[chat.ref.key],
                    onSelect = { msg ->
                        vm.select(msg)
                        nav.navigate(Routes.MESSAGE)
                    },
                    onSend = { text -> vm.send(text) },
                    onStartTyping = vm::notifyTyping,
                    onMention = {
                        vm.loadMembers()
                        nav.navigate(Routes.MENTION)
                    },
                    onLoadEarlier = vm::loadEarlier,
                    onOptions = { nav.navigate(Routes.CHAT_OPTIONS) },
                )
            }
        }
        composable(Routes.MESSAGE) {
            val state by vm.state.collectAsStateWithLifecycle()
            val msg = state.selected
            if (msg == null) {
                // Deleted while open.
                LaunchedEffect(Unit) { nav.popBackStack() }
            } else {
                MessageActionsScreen(
                    msg = msg,
                    onReact = { key -> vm.toggleReaction(msg, key) },
                    onStartTyping = vm::notifyTyping,
                    onReply = { text ->
                        vm.send(text, quote = msg)
                        nav.popBackStack()
                    },
                    onForward = { nav.navigate(Routes.FORWARD) },
                    onRecall = {
                        vm.recall(msg)
                        nav.popBackStack()
                    },
                    onDelete = {
                        vm.deleteForMe(msg)
                        nav.popBackStack()
                    },
                )
            }
        }
        composable(Routes.FORWARD) {
            val state by vm.state.collectAsStateWithLifecycle()
            val msg = state.selected
            ContactsScreen("Forward to", state.threads, onPick = { target ->
                if (msg != null) vm.forward(msg, target, onDone = { nav.popBackToChat() })
            })
        }
        composable(Routes.MENTION) {
            val state by vm.state.collectAsStateWithLifecycle()
            MentionScreen(
                members = state.chat?.members,
                onStartTyping = vm::notifyTyping,
                onSend = { member, text ->
                    vm.send(text, mention = member)
                    nav.popBackStack()
                },
            )
        }
        composable(Routes.CHAT_OPTIONS) {
            val state by vm.state.collectAsStateWithLifecycle()
            val chat = state.chat
            if (chat == null) {
                LaunchedEffect(Unit) { nav.popBackStack() }
            } else {
                ChatOptionsScreen(
                    chat = chat,
                    thread = state.threads.firstOrNull { it.ref == chat.ref },
                    onPin = { pinned ->
                        vm.setPinned(pinned)
                        nav.popBackStack()
                    },
                    onMute = { nav.navigate(Routes.MUTE) },
                    onUnmute = {
                        vm.setMuted(null)
                        nav.popBackStack()
                    },
                    onMarkUnread = {
                        vm.markUnread()
                        nav.popBackStack(Routes.HOME, inclusive = false)
                    },
                    onDelete = {
                        vm.deleteConversation()
                        nav.popBackStack(Routes.HOME, inclusive = false)
                    },
                )
            }
        }
        composable(Routes.MUTE) {
            MuteScreen(onPick = { option ->
                vm.setMuted(option)
                nav.popBackToChat()
            })
        }
        composable(Routes.SETTINGS) {
            val state by vm.state.collectAsStateWithLifecycle()
            SettingsScreen(
                current = state.settings,
                loggedIn = state.status?.isLoggedIn == true,
                onSave = {
                    vm.saveSettings(it)
                    nav.popBackStack()
                },
                onRestartServer = {
                    vm.restartLocalServer()
                    nav.popBackStack()
                },
                onLogout = {
                    vm.logout()
                    nav.popBackStack()
                },
            )
        }
    }
}

private fun NavHostController.popBackToChat() {
    popBackStack(Routes.CHAT, inclusive = false)
}

@Composable
private fun ErrorToast(message: String, onDismiss: () -> Unit) {
    LaunchedEffect(message) {
        delay(3_000)
        onDismiss()
    }
    Box(Modifier.fillMaxSize().padding(bottom = 16.dp), contentAlignment = Alignment.BottomCenter) {
        Text(
            text = message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onError,
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .background(MaterialTheme.colors.error, RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
