package com.watchhrept.wear.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesServerMessageWithParityFields() {
        // Shape produced by ChatStore.toPublic on the server.
        val msg = json.decodeFromString<ChatMessage>(
            """
            {"id":"10","cliMsgId":"c10","threadId":"g1","threadKind":"group","fromId":"me","fromName":"Me",
             "isSelf":true,"ts":1700000000000,"msgType":"webchat","text":"@Bob ok",
             "quote":{"msgId":"9","fromId":"200","fromName":"Bob","text":"lunch?"},
             "mentions":[{"uid":"200","pos":0,"len":4}],
             "reactions":[{"key":"heart","emoji":"❤️","count":2,"mine":true}],
             "mentionsMe":false,"status":"seen","seenCount":3,"futureField":1}
            """.trimIndent(),
        )
        assertEquals("lunch?", msg.quote?.text)
        assertEquals(Mention("200", 0, 4), msg.mentions.single())
        assertTrue(msg.reactions.single().mine)
        assertEquals("seen", msg.status)
        assertEquals(3, msg.seenCount)
        assertTrue(msg.isText)
    }

    @Test
    fun olderPayloadsStillDecode() {
        val msg = json.decodeFromString<ChatMessage>(
            """{"id":"1","threadId":"100","threadKind":"user","ts":1,"text":"[Photo]","attachment":{"href":"https://x"}}""",
        )
        assertTrue(msg.reactions.isEmpty())
        assertFalse(msg.recalled)
        assertNull(msg.status)
        assertFalse("attachments can't be forwarded", msg.isText)
    }

    @Test
    fun decodesThreadSummaryFlags() {
        val t = json.decodeFromString<ThreadSummary>(
            """{"id":"g1","kind":"group","name":"Team","unread":2,"pinned":true,"mutedUntil":-1}""",
        )
        assertTrue(t.pinned)
        assertTrue(t.isMuted)
        assertEquals("group:g1", t.key)
        assertFalse(json.decodeFromString<ThreadSummary>("""{"id":"1","kind":"user","name":"A"}""").isMuted)
    }
}
