package com.watchhrept.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.BreakIterator

/** Common emoji, roughly in the order people reach for them. */
private val EMOJI = """
    😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😙 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔
    🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🥵 🥶 🥴 😵 🤯 🤠 🥳 😎 🤓 🧐 😕
    😟 🙁 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 💩
    🤡 👻 👽 🤖 👋 🤚 ✋ 🖖 👌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 👐 🤲
    🙏 💪 ❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 🔥 ✨ ⭐ 🌟 💯 🎉 🎊 🎁 🎂 🍰
    ☕ 🍵 🍜 🍚 🍻 🥂 🌹 🌸 🌈 ☀️ 🌙 ⚡ 💤 💬 👀 🙈 🙉 🙊 🐶 🐱 🐼 🐷 🐸 🐵 ✅ ❌ ❓ ❗ 💰 📌
    📷 🎵 ⚽ 🏆 🚗 ✈️ 🏠 ⏰ 📞
""".trim().split(Regex("\\s+"))

/** Emoji grid shown in place of the keyboard. */
@Composable
fun EmojiPanel(onEmoji: (String) -> Unit, onBackspace: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier) {
        Column(Modifier.fillMaxWidth().height(288.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Emoji",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onBackspace) { Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = "Delete") }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(EMOJI) { emoji ->
                    Box(
                        Modifier.aspectRatio(1f).clip(CircleShape).clickable { onEmoji(emoji) },
                        contentAlignment = Alignment.Center,
                    ) { Text(emoji, fontSize = 26.sp) }
                }
            }
        }
    }
}

/** Index where the character (grapheme, so a whole emoji) before [cursor] starts. */
internal fun previousCharacterStart(text: String, cursor: Int): Int {
    if (cursor <= 0) return 0
    val it = BreakIterator.getCharacterInstance()
    it.setText(text)
    val start = it.preceding(cursor.coerceAtMost(text.length))
    return if (start == BreakIterator.DONE) cursor - 1 else start
}
