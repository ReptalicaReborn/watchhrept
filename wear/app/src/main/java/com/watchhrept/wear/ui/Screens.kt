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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipColors
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
import com.watchhrept.wear.data.ServerSettings
import com.watchhrept.wear.data.SessionStatus
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.text.Normalizer

/** Standard round-screen list scaffold: time at the top, scroll indicator, vignette. */
@Composable
fun WatchList(
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
fun CenteredText(
    text: String,
    color: Color = MaterialTheme.colors.onBackground,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.body2,
) {
    Text(
        text = text,
        color = color,
        textAlign = TextAlign.Center,
        style = style,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    )
}

/** A full-width chip with a single label, the building block of most menus. */
@Composable
fun ActionChip(
    label: String,
    onClick: () -> Unit,
    secondary: String? = null,
    enabled: Boolean = true,
    colors: ChipColors = ChipDefaults.secondaryChipColors(),
) {
    Chip(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = if (secondary != null) {
            { Text(secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Destructive action: the first tap arms it, a second tap within a few seconds runs it. */
@Composable
fun ConfirmChip(label: String, onConfirm: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(3_000)
            armed = false
        }
    }
    ActionChip(
        label = if (armed) "Tap again to confirm" else label,
        onClick = { if (armed) onConfirm() else armed = true },
        colors = if (armed) ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.error) else ChipDefaults.secondaryChipColors(),
    )
}

@Composable
fun LoadingScreen(label: String? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (label == null) {
            CircularProgressIndicator()
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator()
                CenteredText(label, MaterialTheme.colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ConnectionErrorScreen(error: String, onRetry: () -> Unit, onSettings: () -> Unit) {
    WatchList {
        item { ListHeader { Text("Can't reach server") } }
        item { CenteredText(error, MaterialTheme.colors.error) }
        item { ActionChip("Retry", onRetry, colors = ChipDefaults.primaryChipColors()) }
        item { ActionChip("Settings", onSettings) }
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
            else -> item { ActionChip("Log in with QR", onLogin, colors = ChipDefaults.primaryChipColors()) }
        }
        status.error?.let { error -> item { CenteredText(error, MaterialTheme.colors.error) } }
        item { ActionChip("Server settings", onSettings) }
    }
}

@Composable
fun SettingsScreen(
    current: ServerSettings,
    loggedIn: Boolean,
    onSave: (ServerSettings) -> Unit,
    onRestartServer: () -> Unit,
    onLogout: () -> Unit,
) {
    var onWatch by rememberSaveable { mutableStateOf(current.onWatch) }
    var serverUrl by rememberSaveable { mutableStateOf(current.serverUrl) }
    var token by rememberSaveable { mutableStateOf(current.token) }
    val editUrl = rememberTextInput { serverUrl = it.trim() }
    val editToken = rememberTextInput { token = it.trim() }
    val edited = ServerSettings(onWatch, serverUrl, token)
    val changed = edited != current

    WatchList {
        item { ListHeader { Text("Settings") } }
        item {
            ActionChip(
                label = "Zalo runs on",
                secondary = if (onWatch) "This watch" else "Another device",
                onClick = { onWatch = !onWatch },
            )
        }
        if (!onWatch) {
            item {
                ActionChip(
                    label = "Address",
                    secondary = serverUrl.ifBlank { "Not set" },
                    onClick = { editUrl("Server address", listOf("http://10.0.2.2:3000")) },
                )
            }
            item {
                ActionChip(
                    label = "Access token",
                    secondary = if (token.isBlank()) "None" else "•".repeat(8),
                    onClick = { editToken("Access token", emptyList()) },
                )
            }
        }
        if (changed || !current.isConfigured) {
            item {
                ActionChip(
                    label = "Save",
                    enabled = edited.isConfigured,
                    onClick = { onSave(edited) },
                    colors = ChipDefaults.primaryChipColors(),
                )
            }
        } else {
            if (current.onWatch) item { ActionChip("Restart Zalo service", onRestartServer) }
            if (loggedIn) item { ConfirmChip("Log out of Zalo", onLogout) }
        }
        item {
            CenteredText(
                if (onWatch) {
                    "Zalo stays connected on the watch, even with the app closed. Uses more battery."
                } else {
                    "Tip: choose “type on phone” in the input screen to paste the token."
                },
                MaterialTheme.colors.onSurfaceVariant,
                MaterialTheme.typography.caption3,
            )
        }
    }
}

fun initial(name: String): String = name.trim().firstOrNull()?.uppercase() ?: "?"

fun formatTime(ts: Long): String =
    DateUtils.formatSameDayTime(ts, System.currentTimeMillis(), DateFormat.SHORT, DateFormat.SHORT).toString()

/** "Active now" / "Active 5 minutes ago" from Zalo's last-online timestamp (seconds or ms). */
fun formatPresence(lastOnline: Long, now: Long = System.currentTimeMillis()): String {
    val ms = if (lastOnline < 100_000_000_000L) lastOnline * 1000 else lastOnline
    if (now - ms < 5 * DateUtils.MINUTE_IN_MILLIS) return "Active now"
    return "Active " + DateUtils.getRelativeTimeSpanString(ms, now, DateUtils.MINUTE_IN_MILLIS)
}

/** Lowercase and strip Vietnamese diacritics so "duc" finds "Đức". */
fun foldForSearch(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace('đ', 'd')

private fun decodeDataUrl(dataUrl: String) = runCatching {
    val bytes = Base64.decode(dataUrl.substringAfter(','), Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()
