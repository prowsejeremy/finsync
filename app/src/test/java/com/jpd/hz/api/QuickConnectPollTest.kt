package com.jpd.hz.api

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickConnectPollTest {

    @Test
    fun `an approved code is approved`() {
        assertEquals(QuickConnectPoll.APPROVED, QuickConnectPoll.from(200, true))
    }

    @Test
    fun `an unapproved code, or an answer with no body, is still waiting`() {
        assertEquals(QuickConnectPoll.WAITING, QuickConnectPoll.from(200, false))
        assertEquals(QuickConnectPoll.WAITING, QuickConnectPoll.from(200, null))
    }

    @Test
    fun `a code the server has forgotten has expired`() {
        assertEquals(QuickConnectPoll.EXPIRED, QuickConnectPoll.from(404, null))
    }

    @Test
    fun `any other answer, or none, fails`() {
        assertEquals(QuickConnectPoll.FAILED, QuickConnectPoll.from(500, null))
        assertEquals(QuickConnectPoll.FAILED, QuickConnectPoll.from(401, null))
        assertEquals(QuickConnectPoll.FAILED, QuickConnectPoll.from(null, null))
    }
}
