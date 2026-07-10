package com.github.pushpavel.autocp.submit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLDecoder

/**
 * Encoding/decoding for the subset of Engine.IO v4 + Socket.IO v4 (websocket transport,
 * default namespace) that the cph-ng "CPH-NG Submit" browser extension uses.
 */
object SocketIoProtocol {
    const val PING_INTERVAL_MILLIS = 25_000L
    const val PING_TIMEOUT_MILLIS = 20_000L
    const val MAX_PAYLOAD_BYTES = 1_000_000

    const val PING = "2"

    private val json = Json { ignoreUnknownKeys = true }

    /** Engine.IO OPEN packet sent right after the websocket handshake. */
    fun openPacket(sid: String) = "0" + buildJsonObject {
        put("sid", sid)
        put("upgrades", JsonArray(emptyList()))
        put("pingInterval", PING_INTERVAL_MILLIS)
        put("pingTimeout", PING_TIMEOUT_MILLIS)
        put("maxPayload", MAX_PAYLOAD_BYTES)
    }

    /** Socket.IO CONNECT ack for the default namespace, answering the client's "40". */
    fun connectAckPacket(sid: String) = "40" + buildJsonObject { put("sid", sid) }

    /** Socket.IO EVENT packet: `42["name",payload]`. */
    fun eventPacket(name: String, payload: JsonElement? = null) = "42" + buildJsonArray {
        add(JsonPrimitive(name))
        if (payload != null) add(payload)
    }

    /** Parses a Socket.IO EVENT packet into (event name, first argument), or null for other packets. */
    fun parseEvent(message: String): Pair<String, JsonElement?>? {
        if (!message.startsWith("42")) return null
        var body = message.substring(2)
        // optional namespace ("/nsp,") then optional ack id digits precede the json array
        if (body.startsWith("/")) body = body.substringAfter(',', "")
        body = body.dropWhile { it.isDigit() }
        val array = runCatching { json.parseToJsonElement(body) as? JsonArray }.getOrNull() ?: return null
        val name = (array.getOrNull(0) as? JsonPrimitive)?.content ?: return null
        return name to array.getOrNull(1)
    }

    /** Parses the query string of a websocket handshake like `/ws/?EIO=4&transport=websocket&type=browser`. */
    fun parseQuery(resourceDescriptor: String): Map<String, String> {
        val query = resourceDescriptor.substringAfter('?', "")
        return query.split('&')
            .filter { it.isNotBlank() }
            .associate {
                val key = URLDecoder.decode(it.substringBefore('='), Charsets.UTF_8)
                val value = URLDecoder.decode(it.substringAfter('=', ""), Charsets.UTF_8)
                key to value
            }
    }
}
