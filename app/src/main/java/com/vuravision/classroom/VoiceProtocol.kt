package com.vuravision.classroom

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** Wire format is kept separate from audio, networking and document editing. */
object VoiceProtocol {
    const val ENDPOINT="wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    fun setup(settings:VoiceSettings,knowledge:String,resume:String?=null):String {
        val session=JSONObject();if(!resume.isNullOrBlank())session.put("handle",resume)
        val config=JSONObject().put("model","models/${settings.model}")
            .put("generationConfig",JSONObject().put("responseModalities",JSONArray().put("AUDIO"))
                .put("speechConfig",JSONObject().put("voiceConfig",JSONObject().put("prebuiltVoiceConfig",JSONObject().put("voiceName",settings.voice)))))
            .put("systemInstruction",JSONObject().put("parts",JSONArray().put(JSONObject().put("text",settings.instruction(knowledge)))))
            .put("sessionResumption",session)
            .put("contextWindowCompression",JSONObject().put("triggerTokens",16000).put("slidingWindow",JSONObject().put("targetTokens",8000)))
            .put("realtimeInputConfig",JSONObject().put("automaticActivityDetection",JSONObject().put("silenceDurationMs",700)))
        return JSONObject().put("setup",config).toString()
    }
    fun greeting()=JSONObject().put("clientContent",JSONObject().put("turns",JSONArray().put(JSONObject().put("role","user")
        .put("parts",JSONArray().put(JSONObject().put("text","با سلام کوتاه بگو دستیار آموزشی هستم و بپرس چطور می‌توانی کمک کنی. نام برند، شرکت یا مدل را نگو. سپس منتظر صحبت من بمان.")))))
        .put("turnComplete",true)).toString()
    fun audio(bytes:ByteArray,count:Int=bytes.size)=media("audio","audio/pcm;rate=16000",Base64.encodeToString(bytes,0,count,Base64.NO_WRAP))
    fun image(jpeg:ByteArray)=media("video","image/jpeg",Base64.encodeToString(jpeg,Base64.NO_WRAP))
    private fun media(kind:String,mime:String,data:String)=JSONObject().put("realtimeInput",JSONObject().put(kind,JSONObject().put("mimeType",mime).put("data",data))).toString()
    data class Packet(val ready:Boolean=false,val interrupted:Boolean=false,val complete:Boolean=false,
        val audio:List<ByteArray> = emptyList(),val resumeHandle:String?=null,val resumable:Boolean?=null,val goAway:Boolean=false,val error:Boolean=false,val errorCode:String?=null)
    fun parse(message:String):Packet {
        val root=JSONObject(message)
        val content=root.optJSONObject("serverContent")?:root.optJSONObject("server_content")
        val audio=mutableListOf<ByteArray>()
        val turn=content?.optJSONObject("modelTurn")?:content?.optJSONObject("model_turn")
        val parts=turn?.optJSONArray("parts")
        if(parts!=null)for(i in 0 until parts.length()){
            val part=parts.getJSONObject(i)
            val data=part.optJSONObject("inlineData")?:part.optJSONObject("inline_data")?:continue
            val mime=data.optString("mimeType",data.optString("mime_type"))
            if(mime.startsWith("audio/pcm") && data.has("data")){
                val rate=Regex("rate=(\\d+)").find(mime)?.groupValues?.get(1)?.toIntOrNull()
                require(rate==null || rate==24000){"Unsupported audio sample rate"}
                val bytes=Base64.decode(data.getString("data"),Base64.DEFAULT)
                require(bytes.size<=2_000_000 && bytes.size%2==0){"Invalid PCM payload"};audio.add(bytes)
            }
        }
        val resume=root.optJSONObject("sessionResumptionUpdate")?:root.optJSONObject("session_resumption_update")
        return Packet(root.has("setupComplete")||root.has("setup_complete"),content?.optBoolean("interrupted")==true,
            content?.optBoolean("turnComplete",content.optBoolean("turn_complete"))==true,audio,
            resume?.optString("newHandle",resume.optString("new_handle"))?.takeIf{it.isNotBlank()},
            resume?.takeIf{it.has("resumable")}?.optBoolean("resumable"),root.has("goAway")||root.has("go_away"),root.has("error"))
    }
}
