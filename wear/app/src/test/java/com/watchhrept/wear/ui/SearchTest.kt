package com.watchhrept.wear.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTest {
    @Test
    fun foldForSearch_ignoresCaseAndVietnameseDiacritics() {
        assertEquals("nguyen van duc", foldForSearch("Nguyễn Văn Đức"))
        assertEquals("tran thi hoa", foldForSearch("TRẦN THỊ HOA"))
    }
}
