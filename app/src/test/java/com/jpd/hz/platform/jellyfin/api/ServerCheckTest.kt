package com.jpd.hz.platform.jellyfin.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerCheckTest {

    @Test
    fun `a success is connected`() {
        assertEquals(ServerCheck.CONNECTED, ServerCheck.from(200))
        assertEquals(ServerCheck.CONNECTED, ServerCheck.from(204))
    }

    @Test
    fun `a 401 is a refused sign-in on a reachable server`() {
        val check = ServerCheck.from(401)

        assertEquals(ServerCheck.REFUSED, check)
        assertTrue(check.reachable)
    }

    @Test
    fun `anything else, or no response, is offline`() {
        listOf(null, 404, 500, 503).forEach { code ->
            assertEquals(ServerCheck.OFFLINE, ServerCheck.from(code))
        }
        assertFalse(ServerCheck.OFFLINE.reachable)
    }
}
