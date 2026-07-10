package com.github.pushpavel.autocp.submit

import com.intellij.openapi.diagnostic.logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Local server speaking the cph-ng router's Socket.IO endpoint (default port 27121, path `/ws`)
 * so the "CPH-NG Submit" browser extension connects unchanged as a `type=browser` client and
 * receives `submitRequest` events.
 *
 * Binds both IPv4 and IPv6 loopback, like `openServerSocketsAsync` in gather/base/localServer.kt.
 */
class SubmitServer(private val port: Int) {

    private class Session(
        val sid: String,
        val type: String?,
        @Volatile var connected: Boolean = false,
    )

    private val log = logger<SubmitServer>()
    private val json = Json { ignoreUnknownKeys = true }
    private val sessions = ConcurrentHashMap<WebSocket, Session>()
    private val endpoints = listOf(Endpoint("127.0.0.1"), Endpoint("::1"))
    private var pinger: ScheduledExecutorService? = null

    @Volatile
    private var activeBrowser: WebSocket? = null

    val isBrowserConnected
        get() = sessions.any { (conn, session) ->
            session.type == "browser" && session.connected && conn.isOpen
        }

    /** Completes with true if at least one loopback endpoint bound successfully. */
    fun startAsync(): CompletableFuture<Boolean> {
        endpoints.forEach { it.start() }
        return CompletableFuture.allOf(*endpoints.map { it.bound }.toTypedArray())
            .thenApply { endpoints.any { it.bound.get() } }
            .whenComplete { ok, _ -> if (ok == true) startPinger() }
    }

    fun stop() {
        pinger?.shutdownNow()
        pinger = null
        endpoints.forEach { runCatching { it.stop(1000) } }
        sessions.clear()
        activeBrowser = null
    }

    /** Sends the submission to the active browser client. Returns false when no browser is connected. */
    fun submit(data: SubmitData): Boolean {
        val target = activeBrowser?.takeIf { it.isOpen && sessions[it]?.connected == true }
            ?: sessions.entries.firstOrNull { (conn, session) ->
                session.type == "browser" && session.connected && conn.isOpen
            }?.key
            ?: return false
        return runCatching {
            target.send(SocketIoProtocol.eventPacket("submitRequest", json.encodeToJsonElement(data)))
        }.isSuccess
    }

    private fun onSocketOpen(conn: WebSocket, handshake: ClientHandshake) {
        val query = SocketIoProtocol.parseQuery(handshake.resourceDescriptor ?: "")
        val session = Session(sid = UUID.randomUUID().toString(), type = query["type"])
        sessions[conn] = session
        conn.send(SocketIoProtocol.openPacket(session.sid))
    }

    private fun onSocketMessage(conn: WebSocket, message: String) {
        val session = sessions[conn] ?: return
        when {
            message == "2probe" -> conn.send("3probe")
            message.startsWith("3") -> {} // pong
            message.startsWith("2") -> conn.send("3" + message.drop(1))
            message == "1" || message.startsWith("41") -> conn.close()
            message.startsWith("40") -> {
                session.connected = true
                conn.send(SocketIoProtocol.connectAckPacket(UUID.randomUUID().toString()))
                if (session.type == "browser") registerBrowser(conn)
            }
            message.startsWith("42") -> {
                val (name, _) = SocketIoProtocol.parseEvent(message) ?: return
                when (name) {
                    "setActive" -> setActiveBrowser(conn)
                }
            }
        }
    }

    @Synchronized
    private fun onSocketClose(conn: WebSocket) {
        sessions.remove(conn)
        if (conn == activeBrowser) {
            activeBrowser = sessions.entries.firstOrNull { (c, session) ->
                session.type == "browser" && session.connected && c.isOpen
            }?.key
            sendStatus()
        }
    }

    @Synchronized
    private fun registerBrowser(conn: WebSocket) {
        if (activeBrowser?.isOpen != true) activeBrowser = conn
        sendStatus()
    }

    @Synchronized
    private fun setActiveBrowser(conn: WebSocket) {
        activeBrowser = conn
        sendStatus()
    }

    private fun sendStatus() {
        for ((conn, session) in sessions) {
            if (session.type != "browser" || !session.connected || !conn.isOpen) continue
            val payload = buildJsonObject { put("isActive", conn == activeBrowser) }
            runCatching { conn.send(SocketIoProtocol.eventPacket("status", payload)) }
        }
    }

    private fun startPinger() {
        if (pinger != null) return
        pinger = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "AutoCp Submit Server Pinger").apply { isDaemon = true }
        }.also {
            it.scheduleAtFixedRate({
                for ((conn, _) in sessions)
                    runCatching { if (conn.isOpen) conn.send(SocketIoProtocol.PING) }
            }, SocketIoProtocol.PING_INTERVAL_MILLIS, SocketIoProtocol.PING_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
        }
    }

    private inner class Endpoint(host: String) : WebSocketServer(InetSocketAddress(host, port)) {
        val bound = CompletableFuture<Boolean>()

        init {
            isReuseAddr = true
        }

        override fun onStart() {
            bound.complete(true)
        }

        override fun onError(conn: WebSocket?, ex: Exception) {
            if (conn == null) {
                // binding failed (or the accept loop died)
                if (bound.complete(false)) log.warn("could not bind submit server on $address", ex)
            } else {
                log.warn("submit server connection error", ex)
            }
        }

        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) = onSocketOpen(conn, handshake)

        override fun onClose(conn: WebSocket, code: Int, reason: String?, remote: Boolean) = onSocketClose(conn)

        override fun onMessage(conn: WebSocket, message: String) = onSocketMessage(conn, message)
    }
}
