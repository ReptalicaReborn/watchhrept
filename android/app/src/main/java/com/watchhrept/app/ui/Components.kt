package com.watchhrept.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.watchhrept.app.data.ChatMessage
import com.watchhrept.app.data.MuteOption

/**
 * Round avatar: the Zalo picture when there is one, else the first letter on a tinted circle.
 * [key] picks the tint, so a person keeps the same color everywhere.
 */
@Composable
fun Avatar(name: String, url: String, key: String, isGroup: Boolean, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val fallback = @Composable {
        val colors = avatarColors(key)
        Box(
            Modifier.size(size).clip(CircleShape).background(colors.container),
            contentAlignment = Alignment.Center,
        ) {
            if (isGroup && name.isBlank()) {
                Icon(Icons.Outlined.Groups, null, tint = colors.content, modifier = Modifier.size(size * 0.5f))
            } else {
                Text(
                    initial(name),
                    color = colors.content,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (size.value * 0.4f).sp,
                )
            }
        }
    }
    if (url.isBlank()) {
        Box(modifier) { fallback() }
    } else {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
            loading = { fallback() },
            error = { fallback() },
        )
    }
}

/** Delivery state of one of our own messages, shown as check marks in circles. */
enum class Delivery { SENT, DELIVERED, READ }

fun deliveryState(msg: ChatMessage): Delivery? = when {
    !msg.isSelf || msg.recalled -> null
    msg.status == "seen" || (msg.seenCount ?: 0) > 0 -> Delivery.READ
    msg.status == "delivered" -> Delivery.DELIVERED
    msg.status == "sent" -> Delivery.SENT
    else -> null
}

/**
 * One outlined circle with a check when sent, two when delivered, two filled when read.
 * [background] is what the icon sits on, used to cut the front circle out of the back one.
 */
@Composable
fun DeliveryIcon(state: Delivery?, tint: Color, background: Color, modifier: Modifier = Modifier) {
    if (state == null) return
    val double = state != Delivery.SENT
    Canvas(modifier.size(width = if (double) 18.dp else 12.dp, height = 12.dp)) {
        val stroke = 1.2.dp.toPx()
        val outer = size.height / 2
        val r = outer - stroke / 2
        val cy = size.height / 2
        val centers = if (double) listOf(outer, size.width - outer) else listOf(size.width / 2)
        for (cx in centers) {
            val center = Offset(cx, cy)
            if (state == Delivery.READ) {
                drawCircle(background, radius = outer + stroke, center = center)
                drawCircle(tint, radius = outer, center = center)
                drawCheck(center, r, background, stroke)
            } else {
                drawCircle(background, radius = outer + stroke * 0.5f, center = center)
                drawCircle(tint, radius = r, center = center, style = Stroke(stroke))
                drawCheck(center, r, tint, stroke)
            }
        }
    }
}

private fun DrawScope.drawCheck(center: Offset, r: Float, color: Color, stroke: Float) {
    val path = Path().apply {
        moveTo(center.x - r * 0.45f, center.y + r * 0.02f)
        lineTo(center.x - r * 0.12f, center.y + r * 0.36f)
        lineTo(center.x + r * 0.46f, center.y - r * 0.30f)
    }
    drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Pill with a number (or "@"), as on unread chats. */
@Composable
fun CountBadge(text: String, color: Color, contentColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
            .background(color, CircleShape)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = contentColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

/** Rounded search field used on the chat list and contact pickers. */
@Composable
fun SearchPill(
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = { Icon(Icons.Outlined.Search, null) },
) {
    TextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text(placeholder) },
        singleLine = true,
        leadingIcon = leading,
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, contentDescription = "Clear") }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
fun LoadingScreen(label: String? = null) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        if (label != null) {
            Spacer(Modifier.height(16.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun ConnectionErrorScreen(error: String, onRetry: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.CloudOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text("Can't reach the server", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onSettings) { Text("Settings") }
            Button(onClick = onRetry) { Text("Try again") }
        }
    }
}

@Composable
fun MuteDialog(onDismiss: () -> Unit, onPick: (MuteOption) -> Unit) {
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

/** "Delete conversation?" confirmation, shared by the chat list and the chat screen. */
@Composable
fun DeleteConversationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete conversation?") },
        text = { Text("The chat history is deleted for you. Others in the chat still see it.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
