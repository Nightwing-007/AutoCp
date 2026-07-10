package com.github.pushpavel.autocp.submit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SocketIoProtocolTest {

    @Test
    fun `open packet is engine io type 0 with handshake json`() {
        val packet = SocketIoProtocol.openPacket("abc")
        assertTrue(packet.startsWith("0{"))
        val obj = Json.parseToJsonElement(packet.drop(1)).jsonObject
        assertEquals("abc", obj["sid"]!!.jsonPrimitive.content)
        assertEquals(0, obj["upgrades"]!!.jsonArray.size)
    }

    @Test
    fun `connect ack packet answers the default namespace`() {
        val packet = SocketIoProtocol.connectAckPacket("xyz")
        assertTrue(packet.startsWith("40{"))
        val obj = Json.parseToJsonElement(packet.drop(2)).jsonObject
        assertEquals("xyz", obj["sid"]!!.jsonPrimitive.content)
    }

    @Test
    fun `event packet round trips through parseEvent`() {
        val payload = buildJsonObject {
            put("url", JsonPrimitive("https://codeforces.com/contest/1234/problem/A"))
            put("sourceCode", JsonPrimitive("int main() {}\n"))
        }
        val packet = SocketIoProtocol.eventPacket("submitRequest", payload)
        assertTrue(packet.startsWith("42["))

        val (name, parsed) = SocketIoProtocol.parseEvent(packet)!!
        assertEquals("submitRequest", name)
        assertEquals(payload, parsed)
    }

    @Test
    fun `parses event without payload and with ack id`() {
        val (name, payload) = SocketIoProtocol.parseEvent("""421["setActive"]""")!!
        assertEquals("setActive", name)
        assertNull(payload)
    }

    @Test
    fun `non event messages are not parsed as events`() {
        assertNull(SocketIoProtocol.parseEvent("3"))
        assertNull(SocketIoProtocol.parseEvent("40"))
        assertNull(SocketIoProtocol.parseEvent("42 not json"))
    }

    @Test
    fun `parses handshake query params`() {
        val query = SocketIoProtocol.parseQuery("/ws/?EIO=4&transport=websocket&type=browser&t=PLxzKKq")
        assertEquals("browser", query["type"])
        assertEquals("4", query["EIO"])
        assertEquals("websocket", query["transport"])
    }
}
