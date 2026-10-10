package com.jpd.hz.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadOnlyInterceptorTest {

    private val signInPaths = listOf(
        "/Users/AuthenticateByName",
        "/QuickConnect/Initiate",
        "/Users/AuthenticateWithQuickConnect"
    )

    @Test
    fun `reads go out on both clients`() {
        for (allowLoginPost in listOf(true, false)) {
            assertTrue(ReadOnlyInterceptor.allows("GET", "/Items", allowLoginPost))
            assertTrue(ReadOnlyInterceptor.allows("HEAD", "/Items", allowLoginPost))
        }
    }

    @Test
    fun `the sign-in client may post the three sign-in requests`() {
        for (path in signInPaths) {
            assertTrue(path, ReadOnlyInterceptor.allows("POST", path, allowLoginPost = true))
        }
    }

    @Test
    fun `the read client may post none of them`() {
        for (path in signInPaths) {
            assertFalse(path, ReadOnlyInterceptor.allows("POST", path, allowLoginPost = false))
        }
    }

    @Test
    fun `approving a code and other writes are blocked on both clients`() {
        val blocked = listOf(
            "POST" to "/QuickConnect/Authorize",
            "POST" to "/Items/abc",
            "DELETE" to "/Items/abc"
        )
        for (allowLoginPost in listOf(true, false)) {
            for ((method, path) in blocked) {
                assertFalse(path, ReadOnlyInterceptor.allows(method, path, allowLoginPost))
            }
        }
    }

    @Test
    fun `methods and paths match whatever their case`() {
        val path = "/quickconnect/initiate"

        assertTrue(ReadOnlyInterceptor.allows("post", path, allowLoginPost = true))
        assertTrue(ReadOnlyInterceptor.allows("get", "/items", allowLoginPost = false))
    }
}
