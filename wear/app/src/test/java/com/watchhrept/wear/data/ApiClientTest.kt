package com.watchhrept.wear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiClientTest {
    @Test
    fun normalizeServerUrl_addsSchemeAndTrimsSlash() {
        assertEquals("http://192.168.1.5:3000/", ApiClient.normalizeServerUrl(" 192.168.1.5:3000/ ").toString())
        assertEquals("https://zalo.example.com/", ApiClient.normalizeServerUrl("https://zalo.example.com").toString())
    }

    @Test
    fun normalizeServerUrl_rejectsBlankAndGarbage() {
        assertNull(ApiClient.normalizeServerUrl(""))
        assertNull(ApiClient.normalizeServerUrl("   "))
        assertNull(ApiClient.normalizeServerUrl("ftp://example.com"))
    }

    @Test
    fun serverSettings_onWatchNeedsNoAddress() {
        assertTrue(ServerSettings(onWatch = true, serverUrl = "", token = "").isConfigured)
        assertFalse(ServerSettings(onWatch = false, serverUrl = "", token = "").isConfigured)
        assertTrue(ServerSettings(onWatch = false, serverUrl = "192.168.1.5:3000", token = "").isConfigured)
    }
}
