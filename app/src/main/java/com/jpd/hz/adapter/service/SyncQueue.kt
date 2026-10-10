package com.jpd.hz.adapter.service

/**
 * The connections the sync service runs, one at a time, in the order asked (spec "Running
 * connections"). A connection is never queued twice, or while it runs. Plain Kotlin, so its rules
 * are tested on the JVM; the service calls it from one thread at a time.
 */
class SyncQueue {

    private val waiting = ArrayDeque<String>()

    /** The connection whose run is going, or null. */
    var running: String? = null
        private set

    /** Queues [connectionId] unless it's running or waiting. True when it was queued. */
    fun add(connectionId: String): Boolean {
        if (connectionId == running || connectionId in waiting) return false
        waiting.addLast(connectionId)
        return true
    }

    /** Starts the next waiting connection, which becomes [running]; null when none waits. */
    fun next(): String? {
        running = waiting.removeFirstOrNull()
        return running
    }

    /** The running connection's run has ended. */
    fun finish(connectionId: String) {
        if (running == connectionId) running = null
    }

    /** Takes [connectionId] off the queue before it starts. True when it was waiting. */
    fun remove(connectionId: String): Boolean = waiting.remove(connectionId)

    /** Empties the queue, returning the connections that were waiting. */
    fun clear(): List<String> {
        val dropped = waiting.toList()
        waiting.clear()
        return dropped
    }
}
