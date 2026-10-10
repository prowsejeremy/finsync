package com.jpd.hz.platform.jellyfin.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInStatusTest {

    private val status = SignInStatus()
    private val withOld = JellyfinClient.buildAuthHeader("device-1", token = "old")
    private val withNew = JellyfinClient.buildAuthHeader("device-1", token = "new")

    @Test
    fun `a 401 on a request with a token records that token`() {
        status.record(401, withOld)

        assertEquals("old", status.refusedToken.value)
    }

    @Test
    fun `a 401 without a token, such as a wrong password, records nothing`() {
        status.record(401, JellyfinClient.buildAuthHeader("device-1"))
        status.record(401, null)

        assertNull(status.refusedToken.value)
    }

    @Test
    fun `a success with the refused token clears it`() {
        status.record(401, withOld)

        status.record(200, withOld)

        assertNull(status.refusedToken.value)
    }

    @Test
    fun `a success with another token leaves the refusal`() {
        status.record(401, withOld)

        status.record(200, withNew)

        assertEquals("old", status.refusedToken.value)
    }

    @Test
    fun `refused only while the refused token is the saved sign-in's`() {
        assertTrue(SignInStatus.isRefused("old", "old"))
        assertFalse(SignInStatus.isRefused("old", "new"))
        assertFalse(SignInStatus.isRefused(null, "new"))
        assertFalse(SignInStatus.isRefused("old", null))
    }
}
