package com.jpd.hz.adapter.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val KURAGE = "jellyfin:3f2a"
private const val HOME = "plex:77b0"
private const val NAS = "nas:music"

/**
 * The sync service's queue: one run at a time, in the order asked, never twice (adapter harness
 * spec, "Running connections" and Testing 4).
 */
class SyncQueueTest {

    private val queue = SyncQueue()

    @Test
    fun `a connection is queued once, whether it's waiting or running`() {
        assertTrue(queue.add(KURAGE))
        assertFalse(queue.add(KURAGE))

        assertEquals(KURAGE, queue.next())
        assertFalse(queue.add(KURAGE))
        assertNull(queue.next())
    }

    @Test
    fun `runs start in the order they were asked, and the started one is running`() {
        assertNull(queue.running)
        queue.add(KURAGE)
        queue.add(HOME)
        queue.add(NAS)

        assertEquals(KURAGE, queue.next())
        assertEquals(KURAGE, queue.running)
        assertEquals(HOME, queue.next())
        assertEquals(HOME, queue.running)
        assertEquals(NAS, queue.next())
        assertNull(queue.next())
        assertNull(queue.running)
    }

    @Test
    fun `finishing the running connection frees it, and finishing another changes nothing`() {
        queue.add(KURAGE)
        queue.next()

        queue.finish(HOME)
        assertEquals(KURAGE, queue.running)

        queue.finish(KURAGE)
        assertNull(queue.running)
        assertTrue(queue.add(KURAGE))
    }

    @Test
    fun `a waiting connection can be taken off the queue, and a running one can't`() {
        queue.add(KURAGE)
        queue.add(HOME)
        queue.next()

        assertFalse(queue.remove(KURAGE))
        assertTrue(queue.remove(HOME))
        assertFalse(queue.remove(HOME))
        assertNull(queue.next())
    }

    @Test
    fun `clearing returns the waiting connections in order and leaves the running one`() {
        queue.add(KURAGE)
        queue.add(HOME)
        queue.add(NAS)
        queue.next()

        assertEquals(listOf(HOME, NAS), queue.clear())
        assertEquals(KURAGE, queue.running)
        assertNull(queue.next())
        assertEquals(emptyList<String>(), queue.clear())
    }
}
