package com.watchhrept.app.ui

import com.watchhrept.app.data.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatUiTest {
    private fun msg(
        text: String = "hi",
        isSelf: Boolean = true,
        kind: String = "user",
        status: String? = "sent",
        seenCount: Int? = null,
        recalled: Boolean = false,
        fromName: String = "An",
    ) = ChatMessage(
        id = "1",
        threadId = "t",
        threadKind = kind,
        fromName = fromName,
        isSelf = isSelf,
        ts = 0,
        text = text,
        status = status,
        seenCount = seenCount,
        recalled = recalled,
    )

    @Test
    fun deliveryState_followsReceipts() {
        assertEquals(Delivery.SENT, deliveryState(msg(status = "sent")))
        assertEquals(Delivery.DELIVERED, deliveryState(msg(status = "delivered")))
        assertEquals(Delivery.READ, deliveryState(msg(status = "seen")))
        assertEquals(Delivery.READ, deliveryState(msg(kind = "group", status = "delivered", seenCount = 2)))
        assertNull(deliveryState(msg(isSelf = false)))
        assertNull(deliveryState(msg(recalled = true)))
    }

    @Test
    fun preview_namesTheSenderOnlyInGroups() {
        assertEquals("hi", preview(msg(isSelf = true)))
        assertEquals("You: hi", preview(msg(isSelf = true, kind = "group")))
        assertEquals("An: hi", preview(msg(isSelf = false, kind = "group")))
        assertEquals("Message recalled", preview(msg(recalled = true)))
        assertEquals("", preview(null))
    }

    @Test
    fun previousCharacterStart_deletesWholeEmoji() {
        val text = "ok 👍"
        assertEquals(3, previousCharacterStart(text, text.length))
        assertEquals(1, previousCharacterStart("ab", 2))
        assertEquals(0, previousCharacterStart("", 0))
    }
}
