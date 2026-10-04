package com.vuravision.classroom

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.Button
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="en-land-mdpi")
class VoiceProvidersTest {
    private val context get()=RuntimeEnvironment.getApplication()
    @Before fun clean(){context.getSharedPreferences("vura",0).edit().clear().commit()}
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private class Worker:AbstractExecutorService(){
        val jobs=java.util.ArrayDeque<Runnable>();var closed=false
        override fun execute(r:Runnable){check(!closed);jobs.add(r)}
        fun run(){while(jobs.isNotEmpty())jobs.removeFirst().run()}
        override fun shutdown(){closed=true}
        override fun shutdownNow():MutableList<Runnable>{closed=true;val result=jobs.toMutableList();jobs.clear();return result}
        override fun isShutdown()=closed
        override fun isTerminated()=closed && jobs.isEmpty()
        override fun awaitTermination(timeout:Long,unit:TimeUnit)=isTerminated
    }
    private class Audio:VoiceAudioPort{
        var input:((ByteArray,Int)->Unit)?=null;var closed=false;val played=mutableListOf<ByteArray>()
        override fun start(input:(ByteArray,Int)->Unit,failure:()->Unit){this.input=input}
        override fun enqueue(pcm:ByteArray){played.add(pcm)}
        override fun interrupt(){played.clear()}
        override fun close(){closed=true}
    }
    private class Service:RouterVoiceService {
        var closed=false;var answers=0;val spoken=mutableListOf<String>();var request=""
        var response="""{"choices":[{"message":{"content":"{\"understood\":false,\"user_text\":\"wrong guess\",\"reply\":\"echo wrong guess\"}"}}]}"""
        override fun validate(settings:VoiceSettings){}
        override fun answer(body:String):String{answers++;request=body;return response}
        override fun speak(body:String,chunk:(ByteArray)->Unit){spoken.add(body);chunk(byteArrayOf(1,2))}
        override fun close(){closed=true}
    }
    private fun frame(value:Int)=ByteBuffer.allocate(1280).order(ByteOrder.LITTLE_ENDIAN).apply{repeat(640){putShort(value.toShort())}}.array()
    @Test fun hiddenDefaultAndDisableCancelPendingAndActiveSession(){
        assertFalse(VoiceSettings.enabled(context))
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        try{
            val a=controller.get();val button=a.findViewById<Button>(R.id.voice_assistant_button)
            assertEquals(View.GONE,button.visibility)
            val settings=MainActivity::class.java.getDeclaredMethod("settings").apply{isAccessible=true}
            settings.invoke(a);val hiddenMenu=ShadowDialog.getLatestDialog()
            val hiddenList=(hiddenMenu as androidx.appcompat.app.AlertDialog).listView.adapter
            assertFalse((0 until hiddenList.count).map{hiddenList.getItem(it).toString()}.contains(a.s("voice_assistant")));hiddenMenu.dismiss()
            VoiceSettings.setEnabled(a,true);idle();assertEquals(View.VISIBLE,button.visibility)
            settings.invoke(a);val enabledMenu=ShadowDialog.getLatestDialog()
            val enabledList=(enabledMenu as androidx.appcompat.app.AlertDialog).listView.adapter
            assertTrue((0 until enabledList.count).map{enabledList.getItem(it).toString()}.contains(a.s("voice_assistant")));enabledMenu.dismiss()
            var ended=false
            val session=object:VoiceConversation{
                override var active=true;override val ready=true
                override fun start(){}
                override fun sendImage(jpeg:ByteArray)=true
                override fun stop(reason:String?){active=false;ended=true}
            }
            MainActivity::class.java.getDeclaredField("voiceAssistant").apply{isAccessible=true}.set(a,session)
            MainActivity::class.java.getDeclaredField("voiceStarting").apply{isAccessible=true}.setBoolean(a,true)
            VoiceSettings.setEnabled(a,false);idle()
            assertTrue(ended);assertEquals(View.GONE,button.visibility)
            assertFalse(MainActivity::class.java.getDeclaredField("voiceStarting").apply{isAccessible=true}.getBoolean(a))
            // A permission callback arriving after disabling cannot expose or start the assistant.
            a.onRequestPermissionsResult(7410,arrayOf(android.Manifest.permission.RECORD_AUDIO),intArrayOf(0))
            assertEquals(View.GONE,button.visibility)
        }finally{controller.pause().stop().destroy()}
    }
    @Test fun connectionsPersistIndependentlyAndBehaviorSaveDoesNotEnable(){
        val settings=VoiceSettings(provider="openrouter",openaiModel="gpt-realtime-2",openaiVoice="cedar",routerModel="qwen/qwen3.8-omni-flash",routerVoice="Aoede")
        settings.save(context);assertEquals(settings,VoiceSettings.load(context));assertFalse(VoiceSettings.enabled(context))
        assertFalse(VoiceSettings.validOpenaiModel("gpt-realtime?key=secret"));assertFalse(VoiceSettings.validRouterModel("https://evil.test/model"))
        assertFalse(VoiceSettings.validRouterModel("google/gemini-3.5-flash:batch"))
        val keys=VoiceKeys(context)
        assertSame(keys.forProvider("gemini"),keys.forProvider("gemini"))
        assertNotSame(keys.forProvider("gemini"),keys.forProvider("openai"));assertNotSame(keys.forProvider("openai"),keys.forProvider("openrouter"))
    }
    @Test fun openaiUsesGaEventsCorrectRatesAndImageDoesNotTriggerAnswer(){
        val setup=JSONObject(OpenaiVoiceCodec.setup(VoiceSettings(provider="openai"),"guide",null)).getJSONObject("session")
        assertEquals("realtime",setup.getString("type"));assertEquals(24000,setup.getJSONObject("audio").getJSONObject("input").getJSONObject("format").getInt("rate"))
        assertEquals("marin",setup.getJSONObject("audio").getJSONObject("output").getString("voice"))
        assertTrue(setup.getJSONObject("audio").getJSONObject("input").getJSONObject("turn_detection").getBoolean("interrupt_response"))
        assertEquals("input_audio_buffer.append",JSONObject(OpenaiVoiceCodec.audio(byteArrayOf(1,2,3,4),2)).getString("type"))
        val image=JSONObject(OpenaiVoiceCodec.image(byteArrayOf(1,2)))
        assertEquals("conversation.item.create",image.getString("type"))
        assertEquals("input_image",image.getJSONObject("item").getJSONArray("content").getJSONObject(0).getString("type"))
        assertFalse(OpenaiVoiceCodec.parse("""{"type":"session.created"}""").ready)
        assertTrue(OpenaiVoiceCodec.parse("""{"type":"session.updated"}""").ready)
        assertArrayEquals(byteArrayOf(1,2),OpenaiVoiceCodec.parse("""{"type":"response.output_audio.delta","delta":"AQI="}""").audio.single())
        assertTrue(OpenaiVoiceCodec.parse("""{"type":"input_audio_buffer.speech_started"}""").interrupted)
        assertTrue(OpenaiVoiceCodec.parse("""{"type":"response.done","response":{"status":"completed"}}""").complete)
    }
    @Test fun routerRequiresAudioAndImagesAndSeparateSpeechCapability(){
        val catalog="""{"data":[{"id":"test/vision","architecture":{"input_modalities":["text","image"],"output_modalities":["text"]}},{"id":"test/audio","architecture":{"input_modalities":["text","audio"],"output_modalities":["text","audio"]}},{"id":"test/omni","architecture":{"input_modalities":["text","audio","image"],"output_modalities":["text"]}},{"id":"test/tts","architecture":{"input_modalities":["text"],"output_modalities":["speech"]}}]}"""
        assertFalse(RouterVoiceProtocol.compatible(catalog,"test/vision"));assertFalse(RouterVoiceProtocol.compatible(catalog,"test/audio"))
        assertTrue(RouterVoiceProtocol.compatible(catalog,"test/omni"));assertTrue(RouterVoiceProtocol.compatible(catalog,"test/tts",true))
        assertFalse(RouterVoiceProtocol.compatible(catalog,"test/missing"))
        val wav=RouterVoiceProtocol.wav(byteArrayOf(1,2));val b=ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF",String(wav,0,4));assertEquals(16000,b.getInt(24));assertEquals(2,b.getInt(40))
        val request=JSONObject(RouterVoiceProtocol.request(VoiceSettings(provider="openrouter"),"guide",emptyList(),byteArrayOf(1,2),byteArrayOf(3,4)))
        val content=request.getJSONArray("messages").getJSONObject(1).getJSONArray("content")
        assertEquals(listOf("text","image_url","input_audio"),(0 until content.length()).map{content.getJSONObject(it).getString("type")})
        assertFalse(request.has("tools"));assertEquals("wav",content.getJSONObject(2).getJSONObject("input_audio").getString("format"))
    }
    @Test fun silenceDoesNotSubmitAndSpeechHasBoundedEndpoint(){
        val turns=RouterVoiceTurns();repeat(100){assertNull(turns.push(frame(0),1280))}
        var result:ByteArray?=null
        repeat(10){assertNull(turns.push(frame(1500),1280))}
        repeat(18){turns.push(frame(0),1280)?.let{result=it}}
        assertNotNull(result);assertTrue(result!!.size<=640000)
        val sustained=RouterVoiceTurns();var submitted=0
        repeat(1000){sustained.push(frame(2000),1280)?.let{submitted++;assertTrue(it.size<=640000)}}
        assertTrue(submitted>=1)
    }
    @Test fun pcmStreamPreservesBytesAcrossOddNetworkChunksAndRejectsHeaders(){
        val bytes=byteArrayOf(1,2,3,4,5,6,7,8,9,10)
        val stream=object:java.io.ByteArrayInputStream(bytes){override fun read(b:ByteArray,off:Int,len:Int)=super.read(b,off,minOf(len,3))}
        val chunks=mutableListOf<ByteArray>();RouterVoiceProtocol.readPcm(stream){chunks.add(it);assertEquals(0,it.size%2)}
        assertArrayEquals(bytes,chunks.fold(byteArrayOf()){a,b->a+b})
        for(invalid in listOf("RIFFnoise","{\"error\":1}","OggSaudio")){
            var played=false
            try{RouterVoiceProtocol.readPcm(invalid.byteInputStream()){played=true};fail("Unexpected container played as PCM")}catch(e:RouterVoiceException){assertEquals("voice_protocol_error",e.reason)}
            assertFalse(played)
        }
    }
    @Test fun routerRejectsUncertainGuessGreetsOnceAndStopsEveryResource(){
        val audio=Audio();val service=Service();val worker=Worker();var end:String?="not ended"
        val session=RouterVoiceAssistant(VoiceSettings(provider="openrouter"),"guide",audio,service,{}, {end=it},worker=worker)
        session.start();assertFalse(session.ready);worker.run();idle();worker.run();idle()
        assertTrue(session.ready);assertEquals(1,service.spoken.size)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1,TimeUnit.SECONDS)
        session.sendImage(byteArrayOf(3,4))
        repeat(10){audio.input!!(frame(1600),1280)};repeat(18){audio.input!!(frame(0),1280)}
        worker.run();idle()
        assertEquals(1,service.answers);assertTrue(service.request.contains("image_url"))
        val spoken=JSONObject(service.spoken.last()).getString("input")
        assertEquals("متوجه نشدم، لطفاً دوباره بگید.",spoken);assertFalse(spoken.contains("wrong guess"))
        // Incoming speech while preparing/speaking is not submitted again.
        repeat(30){audio.input!!(frame(2000),1280)};repeat(18){audio.input!!(frame(0),1280)}
        worker.run();assertEquals(1,service.answers)
        session.stop();assertTrue(service.closed);assertTrue(audio.closed);assertTrue(worker.closed);assertNull(end)
        assertFalse(session.sendImage(byteArrayOf(1,2)))
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(2,TimeUnit.SECONDS);assertFalse(session.active)
    }
    @Test fun stoppingBeforeValidationCompletesCannotStartMicrophone(){
        val audio=Audio();val service=Service();val worker=Worker()
        val session=RouterVoiceAssistant(VoiceSettings(provider="openrouter"),"guide",audio,service,{}, {},worker=worker)
        session.start();session.stop();worker.run();idle();assertNull(audio.input);assertTrue(service.closed)
    }
}
