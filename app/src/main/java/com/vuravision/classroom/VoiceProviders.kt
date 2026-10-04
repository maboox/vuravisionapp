package com.vuravision.classroom

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

class VoiceKeys(private val context:Context) {
    private val stores=mutableMapOf<String,VoiceKeyStore>()
    fun forProvider(provider:String):VoiceKeyStore {
        require(provider in VoiceSettings.providers)
        return stores.getOrPut(provider){VoiceKeyStore(context,if(provider=="gemini")"vura_voice_secret" else "vura_voice_secret_$provider")}
    }
}

interface VoiceConversation {
    val active:Boolean
    val ready:Boolean
    fun start()
    fun sendImage(jpeg:ByteArray):Boolean
    fun stop(reason:String?=null)
}

interface LiveVoiceCodec {
    fun setup(settings:VoiceSettings,knowledge:String,resume:String?):String
    fun greeting():String
    fun audio(bytes:ByteArray,count:Int):String
    fun image(jpeg:ByteArray):String
    fun parse(message:String):VoiceProtocol.Packet
}

object GeminiVoiceCodec:LiveVoiceCodec {
    override fun setup(settings:VoiceSettings,knowledge:String,resume:String?)=VoiceProtocol.setup(settings,knowledge,resume)
    override fun greeting()=VoiceProtocol.greeting()
    override fun audio(bytes:ByteArray,count:Int)=VoiceProtocol.audio(bytes,count)
    override fun image(jpeg:ByteArray)=VoiceProtocol.image(jpeg)
    override fun parse(message:String)=VoiceProtocol.parse(message)
}

/** GA Realtime events; image updates add context without triggering a response. */
object OpenaiVoiceCodec:LiveVoiceCodec {
    override fun setup(settings:VoiceSettings,knowledge:String,resume:String?):String {
        val format=JSONObject().put("type","audio/pcm").put("rate",24000)
        val session=JSONObject().put("type","realtime").put("model",settings.openaiModel)
            .put("instructions",settings.instruction(knowledge)).put("output_modalities",JSONArray().put("audio"))
            .put("audio",JSONObject().put("input",JSONObject().put("format",format)
                .put("turn_detection",JSONObject().put("type","server_vad").put("silence_duration_ms",700)
                    .put("create_response",true).put("interrupt_response",true)))
                .put("output",JSONObject().put("format",format).put("voice",settings.openaiVoice)))
        return JSONObject().put("type","session.update").put("session",session).toString()
    }
    override fun greeting()=JSONObject().put("type","response.create").put("response",JSONObject()
        .put("instructions","با سلام کوتاه بگو دستیار آموزشی هستم و بپرس چطور می‌توانی کمک کنی؛ بعد منتظر سؤال بمان. نام برند، شرکت یا مدل را نگو.")).toString()
    override fun audio(bytes:ByteArray,count:Int)=JSONObject().put("type","input_audio_buffer.append")
        .put("audio",Base64.encodeToString(bytes,0,count,Base64.NO_WRAP)).toString()
    override fun image(jpeg:ByteArray)=JSONObject().put("type","conversation.item.create")
        .put("item",JSONObject().put("type","message").put("role","user")
            .put("content",JSONArray().put(JSONObject().put("type","input_image")
                .put("image_url","data:image/jpeg;base64,"+Base64.encodeToString(jpeg,Base64.NO_WRAP))))).toString()
    override fun parse(message:String):VoiceProtocol.Packet {
        val root=JSONObject(message)
        return when(root.optString("type")){
            "session.updated"->VoiceProtocol.Packet(ready=true)
            "input_audio_buffer.speech_started"->VoiceProtocol.Packet(interrupted=true)
            "response.output_audio.delta"->{
                val bytes=Base64.decode(root.getString("delta"),Base64.DEFAULT)
                require(bytes.size<=2_000_000 && bytes.size%2==0)
                VoiceProtocol.Packet(audio=listOf(bytes))
            }
            "response.done"->VoiceProtocol.Packet(complete=true,error=root.optJSONObject("response")?.optString("status")=="failed")
            "error"->VoiceProtocol.Packet(error=true,errorCode=root.optJSONObject("error")?.optString("code"))
            else->VoiceProtocol.Packet()
        }
    }
}
