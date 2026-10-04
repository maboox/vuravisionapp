package com.vuravision.classroom

import android.Manifest
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="en-land-mdpi")
class VoiceAssistantTest {
    private val context get()=RuntimeEnvironment.getApplication()
    @Before fun clean(){context.getSharedPreferences("vura",0).edit().clear().commit();Fonts.initialize(context)}
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private class Audio:VoiceAudioPort {
        var starts=0;var closes=0;var interrupts=0
        var input:((ByteArray,Int)->Unit)?=null
        val played=mutableListOf<ByteArray>()
        override fun start(input:(ByteArray,Int)->Unit,failure:()->Unit){starts++;this.input=input}
        override fun enqueue(pcm:ByteArray){played.add(pcm)}
        override fun interrupt(){interrupts++;played.clear()}
        override fun close(){closes++}
    }
    private class Wire:VoiceWire {
        lateinit var events:VoiceWire.Events
        var closed=false;var queue=0L
        val sent=mutableListOf<String>()
        override fun connect(key:String,events:VoiceWire.Events){this.events=events}
        override fun send(text:String):Boolean {sent.add(text);return true}
        override fun queuedBytes()=queue
        override fun close(){closed=true}
    }
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    @Test fun defaultPersonaAndAdministratorSettingsPersist(){
        val defaults=VoiceSettings.load(context)
        assertEquals("warm",defaults.tone);assertEquals("short",defaults.length)
        val prompt=defaults.instruction("guide test")
        listOf("دستیار آموزشی هستم","متوجه نشدم، لطفاً دوباره بگید.","همیشه به فارسی","هرگز عصبانی","جواب نهایی","هیچ ابزار تغییر","guide test").forEach{assertTrue(it,prompt.contains(it))}
        val updated=defaults.copy(tone="formal",length="detailed",extra="برای پایهٔ پنجم توضیح بده",panelNotes="پنل ۷۵ اینچ",frameSeconds=5,sessionMinutes=30)
        updated.save(context);assertEquals(updated,VoiceSettings.load(context))
        assertFalse(VoiceSettings.validModel("gemini-live?key=secret"))
        val knowledge=VoiceKnowledge.load(context)
        assertTrue(knowledge.contains("انتخاب فونت" ) || knowledge.contains("فونت"))
        assertTrue(knowledge.contains("PDF"));assertTrue(knowledge.contains("تعداد لمس هم‌زمان را ثابت نمی‌کنند"))
    }
    @Test fun setupIsAudioOnlyAndMediaMessagesFollowLiveSchema(){
        val setup=JSONObject(VoiceProtocol.setup(VoiceSettings(),"help","resume-handle")).getJSONObject("setup")
        assertEquals("models/"+VoiceSettings.DEFAULT_MODEL,setup.getString("model"))
        assertEquals("AUDIO",setup.getJSONObject("generationConfig").getJSONArray("responseModalities").getString(0))
        assertFalse(setup.has("tools"));assertFalse(setup.has("inputAudioTranscription"))
        assertEquals("resume-handle",setup.getJSONObject("sessionResumption").getString("handle"))
        assertTrue(setup.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text").contains("همیشه به فارسی"))
        val bytes=byteArrayOf(1,2,3,4)
        val audio=JSONObject(VoiceProtocol.audio(bytes,2)).getJSONObject("realtimeInput").getJSONObject("audio")
        assertEquals("audio/pcm;rate=16000",audio.getString("mimeType"));assertArrayEquals(bytes.copyOf(2),Base64.decode(audio.getString("data"),Base64.DEFAULT))
        val image=JSONObject(VoiceProtocol.image(bytes)).getJSONObject("realtimeInput").getJSONObject("video")
        assertEquals("image/jpeg",image.getString("mimeType"));assertArrayEquals(bytes,Base64.decode(image.getString("data"),Base64.DEFAULT))
    }
    @Test fun parserReadsAllAudioPartsAndHandlesInterruptionAndResumption(){
        val packet=VoiceProtocol.parse("""{"serverContent":{"modelTurn":{"parts":[{"text":"ignored"},{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"AQI="}},{"inline_data":{"mime_type":"audio/pcm;rate=24000","data":"AwQ="}}]},"turnComplete":true},"sessionResumptionUpdate":{"resumable":true,"newHandle":"h"}}""")
        assertEquals(2,packet.audio.size);assertArrayEquals(byteArrayOf(3,4),packet.audio[1]);assertTrue(packet.complete)
        assertEquals("h",packet.resumeHandle);assertEquals(true,packet.resumable)
        assertTrue(VoiceProtocol.parse("""{"server_content":{"interrupted":true}}""").interrupted)
        assertTrue(VoiceProtocol.parse("""{"setupComplete":{}}""").ready)
        assertTrue(VoiceProtocol.parse("""{"goAway":{"timeLeft":"10s"}}""").goAway)
    }
    @Test(expected=IllegalArgumentException::class) fun unsupportedAudioRateIsRejected(){
        VoiceProtocol.parse("""{"serverContent":{"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=16000","data":"AQI="}}]}}}""")
    }
    @Test fun connectionWaitsForSetupGreetsOnceAndStopsAllMedia(){
        val audio=Audio();val wire=Wire();var ended:String?="not-ended"
        val session=VoiceAssistant(VoiceSettings(),"fake-key","guide",audio,{}, {ended=it},{wire})
        session.start();assertFalse(session.ready);assertFalse(session.sendImage(byteArrayOf(1,2)))
        wire.events.opened();idle();assertEquals(1,wire.sent.size);assertTrue(JSONObject(wire.sent[0]).has("setup"));assertEquals(0,audio.starts)
        wire.events.message("""{"setupComplete":{}}""");idle()
        assertEquals(1,audio.starts);assertEquals(1,wire.sent.count{JSONObject(it).has("clientContent")})
        audio.input!!(byteArrayOf(0,1),2);assertTrue(JSONObject(wire.sent.last()).getJSONObject("realtimeInput").has("audio"))
        assertTrue(session.sendImage(byteArrayOf(4,5)))
        wire.events.message("""{"serverContent":{"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"AQI="}}]}}}""");idle();assertEquals(1,audio.played.size)
        wire.events.message("""{"serverContent":{"interrupted":true,"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"AQI="}}]}}}""");idle();assertTrue(audio.played.isEmpty())
        session.stop();assertFalse(session.active);assertTrue(wire.closed);assertEquals(1,audio.closes);assertNull(ended)
        val sent=wire.sent.size;audio.input!!(byteArrayOf(1,2),2);wire.events.message("""{"setupComplete":{}}""");idle();assertEquals(sent,wire.sent.size)
    }
    @Test fun sessionResumesWithoutRepeatedGreetingOrMicrophoneAndIgnoresOldSocket(){
        val audio=Audio();val wires=mutableListOf<Wire>();var screenRefreshes=0
        val session=VoiceAssistant(VoiceSettings(),"fake-key","guide",audio,{}, {},{Wire().also{wires.add(it)}},connected={screenRefreshes++})
        try{
            session.start();val first=wires[0];first.events.opened();idle();first.events.message("""{"setupComplete":{}}""");idle()
            first.events.message("""{"sessionResumptionUpdate":{"resumable":true,"newHandle":"handle1"}}""");idle()
            first.events.message("""{"goAway":{"timeLeft":"10s"}}""");idle();assertFalse(session.ready)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(1500,TimeUnit.MILLISECONDS)
            val next=wires[1];next.events.opened();idle()
            assertEquals("handle1",JSONObject(next.sent.single()).getJSONObject("setup").getJSONObject("sessionResumption").getString("handle"))
            next.events.message("""{"setupComplete":{}}""");idle();assertEquals(1,audio.starts);assertEquals(1,next.sent.size);assertEquals(2,screenRefreshes)
            first.events.message("""{"error":{"message":"old socket"}}""");idle();assertTrue(session.active)
        }finally{session.stop()}
    }
    @Test fun quotaAndSlowNetworkStopInsteadOfBufferingUnlimitedAudio(){
        val audio=Audio();val wire=Wire();var reason:String?=null
        val session=VoiceAssistant(VoiceSettings(),"fake-key","guide",audio,{}, {reason=it},{wire})
        session.start();wire.events.failed(429);idle();assertEquals("voice_key_or_quota",reason);assertFalse(session.active)
        val audio2=Audio();val wire2=Wire()
        val second=VoiceAssistant(VoiceSettings(),"fake-key","guide",audio2,{}, {reason=it},{wire2})
        second.start();wire2.events.opened();idle();wire2.events.message("""{"setupComplete":{}}""");idle()
        wire2.queue=129000;audio2.input!!(byteArrayOf(1,2),2);idle();assertEquals("voice_network_slow",reason);assertFalse(second.active)
    }
    @Test fun localDurationLimitEndsSessionAndStopCancelsPendingReconnect(){
        val wires=mutableListOf<Wire>();var reason:String?=null
        val session=VoiceAssistant(VoiceSettings(sessionMinutes=1),"fake-key","guide",Audio(),{}, {reason=it},{Wire().also{wires.add(it)}})
        session.start();wires[0].events.opened();idle();wires[0].events.message("""{"setupComplete":{}}""");idle()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1,TimeUnit.MINUTES)
        assertEquals("voice_time_limit",reason);assertFalse(session.active)
        val stoppedWires=mutableListOf<Wire>()
        val second=VoiceAssistant(VoiceSettings(),"fake-key","guide",Audio(),{}, {},{Wire().also{stoppedWires.add(it)}})
        second.start();stoppedWires[0].events.failed(null);idle();second.stop()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(2,TimeUnit.SECONDS);assertEquals(1,stoppedWires.size)
    }
    @Test fun independentButtonAndConnectionSettingsDoNotExposeHiddenBehavior(){
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        try{
            val a=controller.get();VoiceSettings.setEnabled(a,true);idle();val button=a.findViewById<Button>(R.id.voice_assistant_button)
            assertNotNull(button);assertEquals(a.s("voice_assistant"),button.text.toString())
            button.performClick();val dialog=ShadowDialog.getLatestDialog()
            val text=views(dialog.window!!.decorView).filterIsInstance<TextView>().joinToString("\n"){it.text.toString()}
            assertTrue(text.contains("API"));assertFalse(text.contains(a.s("voice_behavior")))
            dialog.dismiss()
            val hidden=VoiceSettingsUi(a);hidden.advanced{}
            val admin=ShadowDialog.getLatestDialog()
            assertTrue(views(admin.window!!.decorView).filterIsInstance<TextView>().any{it.text.toString().contains("Additional behavior")})
            admin.dismiss()
            assertEquals(PackageManager.PERMISSION_DENIED,a.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        }finally{controller.pause().stop().destroy()}
    }
}
