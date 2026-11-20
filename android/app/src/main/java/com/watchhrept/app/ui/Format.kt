package com.watchhrept.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.format.DateUtils
import android.util.Base64
import com.watchhrept.app.data.ChatMessage
import java.text.DateFormat
import java.text.Normalizer
import java.util.Calendar

fun initial(name: String): String = name.trim().firstOrNull()?.uppercase() ?: "?"

/** "14:05" today, "Mon" this week, "12/3/26" before that, like a chat list. */
fun formatListTime(ts: Long, now: Long = System.currentTimeMillis()): String {
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val sameDay = then.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
        then.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    return when {
        sameDay -> DateFormat.getTimeInstance(DateFormat.SHORT).format(ts)
        now - ts < 6 * DateUtils.DAY_IN_MILLIS -> java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()).format(ts)
        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(ts)
    }
}

fun formatTime(ts: Long): String =
    DateUtils.formatSameDayTime(ts, System.currentTimeMillis(), DateFormat.SHORT, DateFormat.SHORT).toString()

fun formatClock(ts: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(ts)

/** Header shown between messages from different days. */
fun formatDay(ts: Long, now: Long = System.currentTimeMillis()): String = when {
    DateUtils.isToday(ts) -> "Today"
    DateUtils.isToday(ts + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
    else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(ts)
}

fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

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

fun decodeDataUrl(dataUrl: String): Bitmap? = runCatching {
    val bytes = Base64.decode(dataUrl.substringAfter(','), Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

fun statusLabel(msg: ChatMessage): String? = when {
    !msg.isSelf || msg.recalled -> null
    msg.threadKind == "group" && (msg.seenCount ?: 0) > 0 -> "Seen by ${msg.seenCount}"
    msg.status == "seen" -> "Seen"
    msg.status == "delivered" -> "Delivered"
    msg.status == "sent" -> "Sent"
    else -> null
}

fun muteLabel(mutedUntil: Long?): String = when {
    mutedUntil == null -> ""
    mutedUntil == -1L -> "Muted until turned on"
    else -> "Muted until ${formatTime(mutedUntil)}"
}

/** Preview of a message for the chat list; group chats say who wrote it. */
fun preview(msg: ChatMessage?): String {
    if (msg == null) return ""
    if (msg.recalled) return "Message recalled"
    val body = msg.text.ifBlank { msg.attachment?.let { "📎 " + (it.title?.takeIf(String::isNotBlank) ?: "Attachment") }.orEmpty() }
    return when {
        msg.threadKind != "group" -> body
        msg.isSelf -> "You: $body"
        msg.fromName.isNotBlank() -> "${msg.fromName}: $body"
        else -> body
    }
}
