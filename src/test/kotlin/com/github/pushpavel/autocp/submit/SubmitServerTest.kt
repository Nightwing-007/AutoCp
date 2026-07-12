package com.github.pushpavel.autocp.submit

import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Verifies [SubmitServer] against a real Socket.IO client configured exactly like the
 * "CPH-NG Submit" browser extension (path /ws, websocket transport, query type=browser).
 */
class SubmitServerTest {

    private var server: SubmitServer? = null
    private var socket: Socket? = null

    @AfterEach
    fun tearDown() {
        socket?.disconnect()
        socket?.close()
        server?.stop()
    }

    @Test
    fun `browser client connects and receives submitRequest`() {
        val port = ServerSocket(0).use { it.localPort }
        val server = SubmitServer(port).also { server = it }
        assertTrue(server.startAsync().get(5, TimeUnit.SECONDS))
        assertFalse(server.isBrowserConnected)
        assertFalse(server.submit(SubmitData("https://example.com", "code")))

        val options = IO.Options().apply {
            path = "/ws"
            transports = arrayOf("websocket")
            query = "type=browser"
        }
        val connected = CountDownLatch(1)
        val received = CompletableFuture<JSONObject>()
        val socket = IO.socket(URI("http://127.0.0.1:$port"), options).also { socket = it }
        socket.on(Socket.EVENT_CONNECT) { connected.countDown() }
        socket.on("submitRequest") { args -> received.complete(args[0] as JSONObject) }
        socket.connect()

        assertTrue(connected.await(5, TimeUnit.SECONDS), "client should complete the socket.io handshake")
        waitFor("server should register the browser client") { server.isBrowserConnected }

        val data = SubmitData("https://codeforces.com/contest/1234/problem/A", "int main() {}\n")
        assertTrue(server.submit(data))

        val payload = received.get(5, TimeUnit.SECONDS)
        assertEquals(data.url, payload.getString("url"))
        assertEquals(data.sourceCode, payload.getString("sourceCode"))
    }

    @Test
    fun `browser disconnect is tracked`() {
        val port = ServerSocket(0).use { it.localPort }
        val server = SubmitServer(port).also { server = it }
        assertTrue(server.startAsync().get(5, TimeUnit.SECONDS))

        val options = IO.Options().apply {
            path = "/ws"
            transports = arrayOf("websocket")
            query = "type=browser"
        }
        val socket = IO.socket(URI("http://127.0.0.1:$port"), options).also { socket = it }
        socket.connect()
        waitFor("browser should connect") { server.isBrowserConnected }

        socket.disconnect()
        waitFor("browser should disconnect") { !server.isBrowserConnected }
        assertFalse(server.submit(SubmitData("https://example.com", "code")))
    }

    private fun waitFor(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        assertTrue(condition(), message)
    }
}
