package com.vuravision.classroom

import com.google.gson.Gson
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class RoomNetworkTest {
    private lateinit var media:Media
    private lateinit var engine:RoomEngine
    private lateinit var server:RoomServer
    private lateinit var address:RoomAddress
    private val gson=Gson()
    private val profile=DeviceProfile(name="Host")
    @Before fun start(){media=Media(RuntimeEnvironment.getApplication());engine=RoomEngine(Lesson(),profile,"Network room");server=RoomServer(engine,media);server.start(8000,false);address=RoomAddress("127.0.0.1",server.listeningPort,server.invite)}
    @After fun stop(){server.end();media.close()}
    private fun guest()=gson.fromJson(RoomHttp(address).json("/join",RoomJoin(DeviceProfile(name="Guest"))),RoomWelcome::class.java)
    private fun client(w:RoomWelcome)=RoomHttp(address,w.peer,w.credential)
    private fun denied(action:()->Unit){try{action();fail("Request must be rejected")}catch(_:IllegalStateException){}}
    @Test fun pendingApprovalAndPrivateCredentialAreEnforcedOverHttp(){val p=guest();assertNull(gson.fromJson(client(p).json("/poll",RoomPoll()),RoomFrame::class.java).lesson)
        denied{RoomHttp(address,p.peer,RoomEngine.secret()).json("/poll",RoomPoll())};engine.setRole(p.peer,RoomRoles.VIEW);assertNotNull(gson.fromJson(client(p).json("/poll",RoomPoll()),RoomFrame::class.java).lesson);engine.remove(p.peer);denied{client(p).json("/poll",RoomPoll())}}
    @Test fun imageAssetTransfersExactlyAndViewersCannotUpload(){val p=guest();engine.setRole(p.peer,RoomRoles.COLLAB);val bytes=ByteArray(10001){(it%251).toByte()};val source=java.io.File(media.context.cacheDir,"${newId()}.png").apply{writeBytes(bytes)};client(p).upload(source)
        val before=engine.snapshot();val after=before.copyDeep().apply{pages[0].items.add(Item(kind="image",asset=source.name))};val frame=gson.fromJson(client(p).json("/poll",RoomPoll(0,listOf(RoomDocuments.edit(before,after,0)!!))),RoomFrame::class.java);assertTrue(frame.receipts.single().accepted)
        val destination=java.io.File(media.context.cacheDir,"${newId()}.png");client(p).download(source.name,destination);assertArrayEquals(bytes,destination.readBytes());engine.setRole(p.peer,RoomRoles.VIEW);denied{client(p).upload(source)};source.delete();destination.delete()}
    @Test fun downgradedImageOperationReceivesReadOnlyReceipt(){val p=guest();engine.setRole(p.peer,RoomRoles.VIEW);val before=engine.snapshot();val after=before.copyDeep().apply{pages[0].items.add(Item(kind="image",asset="${newId()}.png"))}
        val f=gson.fromJson(client(p).json("/poll",RoomPoll(0,listOf(RoomDocuments.edit(before,after,0)!!))),RoomFrame::class.java);assertFalse(f.receipts.single().accepted);assertEquals("read_only",f.receipts.single().reason)}
    @Test fun onlyLiteralLocalNetworkInvitationsAreAccepted(){assertEquals(address.url,RoomAddress.parse(address.url).url)
        listOf("http://8.8.8.8:80/r/${server.invite}","https://192.168.1.2:80/r/${server.invite}","http://example.com:80/r/${server.invite}",address.url+"?x=1",address.url+"#x").forEach{url->try{RoomAddress.parse(url);fail(url)}catch(_:IllegalArgumentException){}}
    }
    @Test fun wrongInvitationCannotJoinRoom(){denied{RoomHttp(RoomAddress("127.0.0.1",server.listeningPort,RoomEngine.secret())).json("/join",RoomJoin(DeviceProfile(name="Guest")))}}
}
