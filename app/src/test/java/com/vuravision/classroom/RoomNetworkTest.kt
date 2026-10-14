package com.vuravision.classroom

import com.google.gson.Gson
import com.vuravision.classroom.board.Item
import com.vuravision.classroom.board.Lesson
import com.vuravision.classroom.board.newId
import com.vuravision.classroom.room.DeviceProfile
import com.vuravision.classroom.room.RoomAddress
import com.vuravision.classroom.room.RoomCrypto
import com.vuravision.classroom.room.RoomDocuments
import com.vuravision.classroom.room.RoomEngine
import com.vuravision.classroom.room.RoomEnvelope
import com.vuravision.classroom.room.RoomFrame
import com.vuravision.classroom.room.RoomHttp
import com.vuravision.classroom.room.RoomJoin
import com.vuravision.classroom.room.RoomPoll
import com.vuravision.classroom.room.RoomRoles
import com.vuravision.classroom.room.RoomServer
import com.vuravision.classroom.room.RoomSocket
import com.vuravision.classroom.room.RoomWelcome
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoomNetworkTest {
    private lateinit var media: Media
    private lateinit var engine: RoomEngine
    private lateinit var server: RoomServer
    private lateinit var address: RoomAddress
    private val gson = Gson()
    private val profile = DeviceProfile(name = "Host")

    @Before fun start() {
        media = Media(RuntimeEnvironment.getApplication())
        engine = RoomEngine(Lesson(), profile, "Network room")
        server = RoomServer(engine, media)
        server.start(8000, false)
        address = RoomAddress("127.0.0.1", server.listeningPort, server.invite)
    }

    @After fun stop() {
        server.end()
        media.close()
    }
    private val keys = mutableMapOf<String, ByteArray>()
    private fun guest() = RoomHttp(address).join(DeviceProfile(name = "Guest")).let { (w, k) ->
        keys[w.peer] = k
        w
    }
    private fun client(w: RoomWelcome) = RoomHttp(address, w.peer, w.credential, keys.getValue(w.peer))
    private fun denied(action: () -> Unit) {
        try {
            action()
            fail("Request must be rejected")
        } catch (_: IllegalStateException) {}
    }

    /** A phone camera opening the invitation gets a page that opens the app, or says the app is not installed. */
    @Test fun invitationAddressServesTheJoinPage() {
        fun get(url: String) = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).run {
            connectTimeout = 3000
            readTimeout = 3000
            (responseCode to inputStream.bufferedReader().readText()).also { disconnect() }
        }
        val (code, page) = get(address.url)
        assertEquals(200, code)
        assertTrue(page.contains("intent://join?u=" + java.net.URLEncoder.encode(address.url, "UTF-8")))
        assertTrue(page.contains("Network room"))
        val (missingCode, missing) = get(address.url + "/noapp")
        assertEquals(200, missingCode)
        assertTrue(missing.contains("not installed"))
    }

    @Test fun pendingApprovalAndPrivateCredentialAreEnforcedOverHttp() {
        val p = guest()
        assertNull(gson.fromJson(client(p).json("/poll", RoomPoll()), RoomFrame::class.java).lesson)
        denied { RoomHttp(address, p.peer, RoomEngine.secret(), keys.getValue(p.peer)).json("/poll", RoomPoll()) }
        engine.setRole(p.peer, RoomRoles.VIEW)
        assertNotNull(gson.fromJson(client(p).json("/poll", RoomPoll()), RoomFrame::class.java).lesson)
        engine.remove(p.peer)
        denied { client(p).json("/poll", RoomPoll()) }
    }

    @Test fun imageAssetTransfersExactlyAndViewersCannotUpload() {
        val p = guest()
        engine.setRole(p.peer, RoomRoles.COLLAB)
        val bytes = ByteArray(10001) { (it % 251).toByte() }
        val source = java.io.File(media.context.cacheDir, "${newId()}.png").apply { writeBytes(bytes) }
        client(p).upload(source)
        val before = engine.snapshot()
        val after = before.copyDeep().apply { pages[0].items.add(Item(kind = "image", asset = source.name)) }
        val frame = gson.fromJson(client(p).json("/poll", RoomPoll(0, listOf(RoomDocuments.edit(before, after, 0)!!))), RoomFrame::class.java)
        assertTrue(frame.receipts.single().accepted)
        val destination = java.io.File(media.context.cacheDir, "${newId()}.png")
        client(p).download(source.name, destination)
        assertArrayEquals(bytes, destination.readBytes())
        engine.setRole(p.peer, RoomRoles.VIEW)
        denied { client(p).upload(source) }
        source.delete()
        destination.delete()
    }

    @Test fun downgradedImageOperationReceivesReadOnlyReceipt() {
        val p = guest()
        engine.setRole(p.peer, RoomRoles.VIEW)
        val before = engine.snapshot()
        val after = before.copyDeep().apply { pages[0].items.add(Item(kind = "image", asset = "${newId()}.png")) }
        val f = gson.fromJson(client(p).json("/poll", RoomPoll(0, listOf(RoomDocuments.edit(before, after, 0)!!))), RoomFrame::class.java)
        assertFalse(f.receipts.single().accepted)
        assertEquals("read_only", f.receipts.single().reason)
    }

    @Test fun onlyLiteralLocalNetworkInvitationsAreAccepted() {
        assertEquals(address.url, RoomAddress.parse(address.url).url)
        listOf("http://8.8.8.8:80/r/${server.invite}", "https://192.168.1.2:80/r/${server.invite}", "http://example.com:80/r/${server.invite}", address.url + "?x=1", address.url + "#x").forEach { url ->
            try {
                RoomAddress.parse(url)
                fail(url)
            } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun wrongInvitationCannotJoinRoom() {
        denied { RoomHttp(RoomAddress("127.0.0.1", server.listeningPort, RoomEngine.secret())).join(DeviceProfile(name = "Guest")) }
    }

    /** Item 84: everything after the key exchange is sealed; plain requests, other keys and old envelopes are refused. */
    @Test fun roomTrafficIsEncryptedAndCodesMatch() {
        val p = guest()
        engine.setRole(p.peer, RoomRoles.VIEW)
        assertEquals("both screens show the same code", RoomCrypto.code(keys.getValue(p.peer)), server.code(p.peer))
        denied { RoomHttp(address, p.peer, p.credential).json("/poll", RoomPoll()) }
        denied { RoomHttp(address, p.peer, p.credential, RoomCrypto.unhex("0".repeat(64))).json("/poll", RoomPoll()) }
        denied { RoomHttp(address).json("/join", RoomJoin(DeviceProfile(name = "Old version"))) }
        assertNotNull(gson.fromJson(client(p).json("/poll", RoomPoll()), RoomFrame::class.java).lesson)
        val key = keys.getValue(p.peer)
        val stale = RoomCrypto.seal(key, gson.toJson(RoomEnvelope(System.currentTimeMillis() - 600_000, p.credential, gson.toJson(RoomPoll()))).toByteArray(), "POST /poll")
        val c = java.net.URL(address.url + "/poll").openConnection() as java.net.HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("X-Vura-Peer", p.peer)
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(RoomCrypto.text(stale).toByteArray()) }
        assertEquals("a recorded request cannot be replayed later", 401, c.responseCode)
        val seal = RoomCrypto.seal(key, "lesson".toByteArray(), "x")
        assertFalse(String(seal, Charsets.ISO_8859_1).contains("lesson"))
        try {
            RoomCrypto.open(key, seal, "y")
            fail("a message cannot be moved to another endpoint")
        } catch (_: Exception) {}
    }

    /** The persistent socket answers sealed polls; a wrong credential gets the socket closed. */
    @Test fun webSocketCarriesSealedPolls() {
        val p = guest()
        engine.setRole(p.peer, RoomRoles.VIEW)
        val socket = RoomSocket(address, p.peer, p.credential, keys.getValue(p.peer))
        try {
            repeat(100) { if (!socket.open)Thread.sleep(20) }
            assertTrue("socket opened", socket.open)
            assertNotNull(gson.fromJson(socket.poll(RoomPoll()), RoomFrame::class.java).lesson)
            assertNotNull("several polls on the same socket", gson.fromJson(socket.poll(RoomPoll()), RoomFrame::class.java).lesson)
        } finally {
            socket.close()
        }
        val intruder = RoomSocket(address, p.peer, RoomEngine.secret(), keys.getValue(p.peer))
        try {
            repeat(100) { if (intruder.usable)Thread.sleep(20) }
            assertFalse("a wrong credential is closed", intruder.usable)
        } finally {
            intruder.close()
        }
    }
}
