package com.jpd.hz.adapter.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The read-only rule: reads always go out, and a POST only to a platform's own sign-in paths
 * (adapter harness spec, "The contract").
 */
class ReadOnlyInterceptorTest {

    // Jellyfin's three, as its sign-in client passes them; written out here because the harness's
    // tests may not import a platform.
    private val signInPosts = listOf(
        "Users/AuthenticateByName",
        "QuickConnect/Initiate",
        "Users/AuthenticateWithQuickConnect"
    )
    private val signInRequests = signInPosts.map { "/$it" }
    private val bothClients = listOf(signInPosts, emptyList())

    @Test
    fun `reads go out on both clients`() {
        for (allowedPosts in bothClients) {
            assertTrue(ReadOnlyInterceptor.allows("GET", "/Items", allowedPosts))
            assertTrue(ReadOnlyInterceptor.allows("HEAD", "/Items", allowedPosts))
        }
    }

    @Test
    fun `the sign-in client may post the three sign-in requests`() {
        for (path in signInRequests) {
            assertTrue(path, ReadOnlyInterceptor.allows("POST", path, signInPosts))
        }
    }

    @Test
    fun `the read client may post none of them`() {
        for (path in signInRequests) {
            assertFalse(path, ReadOnlyInterceptor.allows("POST", path, emptyList()))
        }
    }

    @Test
    fun `approving a code and other writes are blocked on both clients`() {
        val blocked = listOf(
            "POST" to "/QuickConnect/Authorize",
            "POST" to "/Items/abc",
            "DELETE" to "/Items/abc"
        )
        for (allowedPosts in bothClients) {
            for ((method, path) in blocked) {
                assertFalse(path, ReadOnlyInterceptor.allows(method, path, allowedPosts))
            }
        }
    }

    @Test
    fun `methods and paths match whatever their case`() {
        assertTrue(ReadOnlyInterceptor.allows("post", "/quickconnect/initiate", signInPosts))
        assertTrue(ReadOnlyInterceptor.allows("get", "/items", emptyList()))
    }
}
