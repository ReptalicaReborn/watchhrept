package com.watchhrept.app.ui

import com.watchhrept.app.data.GroupMember
import com.watchhrept.app.data.Mention
import org.junit.Assert.assertEquals
import org.junit.Test

class MentionsTest {
    private val an = GroupMember("1", "An")
    private val anh = GroupMember("2", "An Nguyễn")

    @Test
    fun findMentions_locatesEachPickedMember() {
        val text = "hi @An and @An Nguyễn"
        assertEquals(
            listOf(Mention("1", 3, 3), Mention("2", 11, 10)),
            AppViewModel.findMentions(text, listOf(an, anh)),
        )
    }

    @Test
    fun findMentions_dropsMembersWhoseTagWasDeleted() {
        assertEquals(emptyList<Mention>(), AppViewModel.findMentions("hello", listOf(an)))
    }

    @Test
    fun findMentions_prefersTheLongerNameWhenTagsOverlap() {
        assertEquals(listOf(Mention("2", 0, 10)), AppViewModel.findMentions("@An Nguyễn hi", listOf(an, anh)))
    }
}
