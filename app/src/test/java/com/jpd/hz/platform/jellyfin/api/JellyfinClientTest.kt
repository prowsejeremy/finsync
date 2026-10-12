package com.jpd.hz.platform.jellyfin.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinClientTest {

    @Test
    fun `the header carries the given device ID and token`() {
        val header = JellyfinClient.buildAuthHeader("device-1", token = "abc123")

        assertTrue(header.contains("DeviceId=\"device-1\""))
        assertTrue(header.endsWith(", Token=\"abc123\""))
    }

    @Test
    fun `a header without a token has no token to read`() {
        val header = JellyfinClient.buildAuthHeader("device-1")

        assertFalse(header.contains("Token="))
        assertNull(JellyfinClient.tokenOf(header))
    }

    @Test
    fun `tokenOf reads the token back from a header`() {
        val header = JellyfinClient.buildAuthHeader("device-1", token = "abc123")

        assertEquals("abc123", JellyfinClient.tokenOf(header))
    }
}
