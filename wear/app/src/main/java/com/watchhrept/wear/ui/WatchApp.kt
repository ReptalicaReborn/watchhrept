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
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import kotlinx.coroutines.delay

private object Routes {
    const val HOME = "home"
    const val CHAT = "chat"
    const val SETTINGS = "settings"
}

@Composable
fun WatchApp(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()

    MaterialTheme {
        Box(Modifier.fillMaxSize()) {
            if (!state.settings.isConfigured) {
                SettingsScreen(state.settings, onSave = vm::saveSettings)
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
                status == null -> LoadingScreen()
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
                    onReconnect = vm::reconnectZalo,
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
        }
        composable(Routes.CHAT) {
            val state by vm.state.collectAsStateWithLifecycle()
            DisposableEffect(Unit) { onDispose { vm.closeChat() } }
            state.chat?.let { ChatScreen(it, onSend = vm::send) }
        }
        composable(Routes.SETTINGS) {
            val state by vm.state.collectAsStateWithLifecycle()
            SettingsScreen(state.settings, onSave = {
                vm.saveSettings(it)
                nav.popBackStack()
            })
        }
    }
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
