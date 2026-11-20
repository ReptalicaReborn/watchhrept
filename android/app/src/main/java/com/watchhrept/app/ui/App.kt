package com.watchhrept.app.ui

import android.widget.Toast
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

private object Routes {
    const val HOME = "home"
    const val NEW_CHAT = "new_chat"
    const val CHAT = "chat"
    const val FORWARD = "forward"
    const val SETTINGS = "settings"
}

@Composable
fun App(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        snackbar.showSnackbar(error)
        vm.clearError()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            if (!state.settings.isConfigured) {
                SettingsScreen(
                    current = state.settings,
                    appearance = state.appearance,
                    profileName = null,
                    profileId = null,
                    loggedIn = false,
                    canGoBack = false,
                    onBack = {},
                    onSave = vm::saveSettings,
                    onAppearance = vm::setAppearance,
                    onRestartServer = vm::restartLocalServer,
                    onLogout = vm::logout,
                )
            } else {
                AppNav(vm)
            }
            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = 72.dp),
            )
        }
    }
}

@Composable
private fun AppNav(vm: AppViewModel) {
    val nav = rememberNavController()
    val context = LocalContext.current

    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = { slideInHorizontally { it / 4 } + fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 4 } + fadeOut() },
    ) {
        // Each destination collects state itself so it never renders a stale capture.
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
                status == null -> LoadingScreen(if (state.settings.onDevice) "Starting Zalo…" else "Connecting…")
                !status.isLoggedIn -> LoginScreen(
                    status = status,
                    onLogin = vm::login,
                    onRetry = vm::retryLogin,
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                )
                else -> ThreadListScreen(
                    state = state,
                    actions = ThreadListActions(
                        onOpen = { thread ->
                            vm.openChat(thread)
                            nav.navigate(Routes.CHAT)
                        },
                        onNewChat = { nav.navigate(Routes.NEW_CHAT) },
                        onReconnect = vm::reconnectZalo,
                        onSettings = { nav.navigate(Routes.SETTINGS) },
                        onMarkAllRead = vm::markAllRead,
                        onPin = { thread, pinned -> vm.setPinned(thread.ref, pinned) },
                        onMute = { thread, option -> vm.setMuted(thread.ref, option) },
                        onMarkRead = { vm.markThreadRead(it.ref) },
                        onMarkUnread = { vm.markUnread(it.ref) },
                        onDelete = { vm.deleteConversation(it.ref) },
                    ),
                )
            }
        }
        composable(Routes.NEW_CHAT) {
            val state by vm.state.collectAsStateWithLifecycle()
            ContactsScreen(
                title = "New message",
                threads = state.threads,
                onBack = { nav.popBackStack() },
                onPick = { thread ->
                    vm.openChat(thread)
                    nav.popBackStack()
                    nav.navigate(Routes.CHAT)
                },
            )
        }
        composable(Routes.CHAT) { entry ->
            val state by vm.state.collectAsStateWithLifecycle()
            // Close the chat once this screen leaves the back stack (not when covered by "Forward").
            LaunchedEffect(entry) {
                val ref = vm.state.value.chat?.ref
                entry.lifecycle.addObserver(LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_DESTROY) vm.closeChat(ref)
                })
            }
            val chat = state.chat
            if (chat == null) {
                LaunchedEffect(Unit) { nav.popBackStack(Routes.HOME, inclusive = false) }
            } else {
                ChatScreen(
                    chat = chat,
                    thread = state.threads.firstOrNull { it.ref == chat.ref },
                    typing = state.typing[chat.ref.key],
                    actions = ChatActions(
                        onBack = { nav.popBackStack() },
                        onSend = { text, quote, mentioned, onSent -> vm.send(text, quote, mentioned, onSent) },
                        onStartTyping = vm::notifyTyping,
                        onLoadMembers = vm::loadMembers,
                        onLoadEarlier = vm::loadEarlier,
                        onReact = vm::toggleReaction,
                        onRecall = { vm.recall(it) },
                        onDelete = { vm.deleteForMe(it) },
                        onForward = { msg ->
                            vm.select(msg)
                            nav.navigate(Routes.FORWARD)
                        },
                        onPin = { vm.setPinned(chat.ref, it) },
                        onMute = { vm.setMuted(chat.ref, it) },
                        onMarkUnread = {
                            vm.markUnread(chat.ref)
                            nav.popBackStack(Routes.HOME, inclusive = false)
                        },
                        onDeleteConversation = { vm.deleteConversation(chat.ref) },
                    ),
                )
            }
        }
        composable(Routes.FORWARD) {
            val state by vm.state.collectAsStateWithLifecycle()
            val msg = state.selected
            ContactsScreen(
                title = "Forward to",
                threads = state.threads,
                onBack = { nav.popBackStack() },
                onPick = { target ->
                    if (msg != null) {
                        vm.forward(msg, target, onDone = {
                            Toast.makeText(context, "Forwarded to ${target.name}", Toast.LENGTH_SHORT).show()
                        })
                    }
                    nav.popBackToChat()
                },
            )
        }
        composable(Routes.SETTINGS) {
            val state by vm.state.collectAsStateWithLifecycle()
            SettingsScreen(
                current = state.settings,
                appearance = state.appearance,
                profileName = state.status?.ownName,
                profileId = state.status?.ownId,
                loggedIn = state.status?.isLoggedIn == true,
                canGoBack = true,
                onBack = { nav.popBackStack() },
                onSave = {
                    vm.saveSettings(it)
                    nav.popBackStack()
                },
                onAppearance = vm::setAppearance,
                onRestartServer = {
                    vm.restartLocalServer()
                    nav.popBackStack()
                },
                onLogout = {
                    vm.logout()
                    nav.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }
    }
}

private fun NavHostController.popBackToChat() {
    if (!popBackStack(Routes.CHAT, inclusive = false)) popBackStack()
}
