package com.jpd.hz.adapter.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// Long enough for a loopback request; a cancel that doesn't reach the call never returns.
private const val TEST_TIMEOUT_MS = 10_000L

/**
 * A download still waiting for the server's reply ends when its sync is stopped: cancelling the
 * caller cancels the call. Downloads have no read timeout, so a server that never answers would
 * otherwise hold the run, and its folder lock, until the app is killed.
 */
class CancellableCallTest {

    // As downloads are: they wait as long as the server takes (JellyfinRepository.downloadAudio).
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.SECONDS).build()
    private val acceptor = Executors.newSingleThreadExecutor()

    @After
    fun tearDown() {
        acceptor.shutdownNow()
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun `cancelling the caller ends the wait for a server that never answers`() {
        ServerSocket(0).use { server ->
            val connection = acceptor.submit<Socket> { server.accept() }
            val call = client.newCall(requestTo(server))

            runBlocking {
                val download = launch(Dispatchers.IO) { call.executeCancellable() }
                // Connected, and never answered.
                connection.get().use { download.cancelAndJoin() }
            }

            assertTrue(call.isCanceled())
        }
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun `an answer comes back as the response`() {
        ServerSocket(0).use { server ->
            acceptor.submit { server.accept().use { answer(it, "ok") } }

            val response = runBlocking { client.newCall(requestTo(server)).executeCancellable() }

            response.use { assertEquals("ok", it.body?.string()) }
        }
    }

    private fun requestTo(server: ServerSocket) =
        Request.Builder().url("http://127.0.0.1:${server.localPort}/Audio").build()

    // Reads the request's headers first: closing with them unread could reset the connection.
    private fun answer(socket: Socket, body: String) {
        val request = socket.getInputStream().bufferedReader()
        while (request.readLine().orEmpty().isNotEmpty()) Unit
        val reply = "HTTP/1.1 200 OK\r\nContent-Length: ${body.length}\r\n" +
            "Connection: close\r\n\r\n$body"
        socket.getOutputStream().apply {
            write(reply.toByteArray())
            flush()
        }
    }
}
