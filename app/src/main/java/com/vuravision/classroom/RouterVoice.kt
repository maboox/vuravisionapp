package com.vuravision.classroom

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/** Bounded local endpoint detection, not speech recognition. No model runs on the panel. */
class RouterVoiceTurns {
    private val buffer=ByteArrayOutputStream()
    private val preRoll=java.util.ArrayDeque<ByteArray>()
    private var noise=90.0
    private var voiced=0
    private var quietBytes=0
    private var speechBytes=0
    private var recording=false
    @Synchronized fun reset(){buffer.reset();preRoll.clear();voiced=0;quietBytes=0;speechBytes=0;recording=false}
    @Synchronized fun push(bytes:ByteArray,count:Int):ByteArray? {
        require(count in 0..bytes.size && count%2==0)
        if(count==0)return null
        var power=0.0
        for(i in 0 until count step 2){val n=((bytes[i].toInt() and 255) or (bytes[i+1].toInt() shl 8)).toShort().toInt();power+=n.toDouble()*n}
        val rms=sqrt(power/(count/2))
        val speech=rms>maxOf(350.0,noise*3.0)
        if(!recording){
            if(!speech)noise=(noise*.97+rms.coerceAtMost(700.0)*.03)
            preRoll.addLast(bytes.copyOf(count));while(preRoll.size>8)preRoll.removeFirst()
            voiced=if(speech)voiced+count else 0
            if(voiced<2560)return null // 80 ms / PCM16 16 kHz.
            recording=true;preRoll.forEach{buffer.write(it)};preRoll.clear();speechBytes=voiced
        }else{buffer.write(bytes,0,count);if(speech)speechBytes+=count}
        quietBytes=if(speech)0 else quietBytes+count
        if(quietBytes>=22400 || buffer.size()>=640000){
            val result=if(speechBytes>=2560)buffer.toByteArray()else null
            reset();return result
        }
        return null
    }
}

object RouterVoiceProtocol {
    const val BASE="https://openrouter.ai/api/v1"
    const val GREETING="سلام، دستیار آموزشی هستم. چطور می‌تونم کمکتون کنم؟"
    data class Reply(val user:String,val text:String)
    fun wav(pcm:ByteArray):ByteArray {
        require(pcm.size%2==0 && pcm.size<=650000)
        return ByteBuffer.allocate(44+pcm.size).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36+pcm.size).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(1).putInt(16000).putInt(32000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(pcm.size).put(pcm).array()
    }
    fun compatible(catalog:String,id:String,speech:Boolean=false):Boolean {
        val models=JSONObject(catalog).getJSONArray("data")
        for(i in 0 until models.length()){
            val model=models.getJSONObject(i);if(model.optString("id")!=id)continue
            val architecture=model.optJSONObject("architecture")?:return false
            fun modalities(name:String)=architecture.optJSONArray(name)?.let{a->(0 until a.length()).map{a.optString(it)}}.orEmpty()
            return if(speech)"speech" in modalities("output_modalities")else
                modalities("input_modalities").containsAll(listOf("audio","image","text")) && "text" in modalities("output_modalities")
        }
        return false
    }
    fun request(settings:VoiceSettings,knowledge:String,history:List<Pair<String,String>>,pcm:ByteArray,jpeg:ByteArray?):String {
        val instruction=settings.instruction(knowledge)+"\nاین مسیر نوبتی است. در خروجی فقط یک شیء JSON با understood (boolean)، user_text و reply (string) بده. user_text فقط متن سؤال شنیده‌شده برای حافظهٔ مکالمه است، نه چیزی که باید بلند خوانده شود. اگر صدا یا منظور نامفهوم است understood=false، user_text خالی و reply دقیقاً «متوجه نشدم، لطفاً دوباره بگید.» باشد. هرگز برداشت نامطمئن را بازگو نکن. اگر سؤال مشخص است understood=true و reply پاسخ فارسی طبق قواعد آموزشی باشد. نام مدل، سرویس یا شرکت را در reply نگو."
        val messages=JSONArray().put(JSONObject().put("role","system").put("content",instruction))
        history.takeLast(6).forEach{(user,reply)->messages.put(JSONObject().put("role","user").put("content",user)).put(JSONObject().put("role","assistant").put("content",reply))}
        val parts=JSONArray().put(JSONObject().put("type","text").put("text",if(jpeg==null)"به سؤال در صدای پیوست پاسخ بده. تصویر در دسترس نیست؛ جزئیات تخته را حدس نزن."else"به سؤال در صدای پیوست پاسخ بده. تصویر پیوست آخرین نمای ثبت‌شدهٔ تخته و PDF است؛ فقط همان محتوای قابل مشاهده را می‌توانی ببینی."))
        jpeg?.let{parts.put(JSONObject().put("type","image_url").put("image_url",JSONObject().put("url","data:image/jpeg;base64,"+Base64.encodeToString(it,Base64.NO_WRAP))))}
        parts.put(JSONObject().put("type","input_audio").put("input_audio",JSONObject().put("data",Base64.encodeToString(wav(pcm),Base64.NO_WRAP)).put("format","wav")))
        messages.put(JSONObject().put("role","user").put("content",parts))
        return JSONObject().put("model",settings.routerModel).put("messages",messages).put("stream",false)
            .put("max_tokens",when(settings.length){"detailed"->1200;"medium"->600;else->350})
            .put("response_format",JSONObject().put("type","json_object")).toString()
    }
    fun reply(response:String):Reply {
        val message=JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        val raw=message.getString("content").trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val data=JSONObject(raw)
        if(!data.getBoolean("understood"))return Reply("","متوجه نشدم، لطفاً دوباره بگید.")
        val text=data.getString("reply").trim();require(text.isNotBlank() && text.length<=4500)
        return Reply(data.optString("user_text").take(1500),text)
    }
    fun speech(settings:VoiceSettings,text:String):String {
        val request=JSONObject().put("model",settings.routerSpeechModel).put("input",text).put("voice",settings.routerVoice).put("response_format","pcm")
        if(settings.routerSpeechModel.startsWith("google/gemini-3.8-"))request.put("provider",JSONObject().put("options",JSONObject().put("google-ai-studio",JSONObject()
            .put("speech_metadata",JSONObject().put("style",when(settings.tone){"formal"->"Polite, formal and clear. Speak Persian.";"calm"->"Calm, patient and measured. Speak Persian.";else->"Warm, friendly and respectful. Speak Persian."})))))
        return request.toString()
    }
    fun readPcm(stream:InputStream,chunk:(ByteArray)->Unit){
        val prefix=ByteArray(4);var filled=0
        while(filled<4){val n=stream.read(prefix,filled,4-filled);if(n<0)throw RouterVoiceException("voice_protocol_error");filled+=n}
        val magic=String(prefix,Charsets.US_ASCII)
        if(magic in listOf("RIFF","OggS","fLaC") || magic.startsWith("ID3") || prefix[0]=='{'.code.toByte())throw RouterVoiceException("voice_protocol_error")
        chunk(prefix)
        var total=4;var leftover:Byte?=null;val buffer=ByteArray(8192)
        while(true){
            val n=stream.read(buffer);if(n<0)break
            total+=n;if(total>8_640_000)throw RouterVoiceException("voice_protocol_error")
            val previous=leftover
            val joined=if(previous!=null)byteArrayOf(previous)+buffer.copyOf(n)else buffer.copyOf(n)
            val even=joined.size and -2
            leftover=if(joined.size%2==1)joined.last()else null
            if(even>0)chunk(joined.copyOf(even))
        }
        if(leftover!=null)throw RouterVoiceException("voice_protocol_error")
    }
}

interface RouterVoiceService {
    fun validate(settings:VoiceSettings)
    fun answer(body:String):String
    fun speak(body:String,chunk:(ByteArray)->Unit)
    fun close()
}
class RouterVoiceException(val reason:String):Exception(reason)

/** Only fixed HTTPS endpoints. Redirects and automatic retries cannot forward a credential. */
class RouterVoiceHttp(private val key:String):RouterVoiceService {
    private val client=OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS)
        .callTimeout(120,TimeUnit.SECONDS).retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
    private val lock=Any()
    private var closed=false
    private var call:Call?=null
    private fun execute(path:String,body:String?=null):Response {
        val request=Request.Builder().url(RouterVoiceProtocol.BASE+path).header("Authorization","Bearer $key").header("X-Title","VuraVision")
        if(body!=null)request.post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
        val next=synchronized(lock){if(closed)throw RouterVoiceException("voice_network_error");client.newCall(request.build()).also{call=it}}
        val response=next.execute()
        if(!response.isSuccessful){val code=response.code;response.close();throw RouterVoiceException(when(code){401,402,403,429->"voice_key_or_quota";400,404,422->"voice_model_error";else->"voice_service_error"})}
        return response
    }
    private fun readJson(path:String,body:String?=null):String=execute(path,body).use{r->
        val stream=r.body?.byteStream()?:throw RouterVoiceException("voice_protocol_error")
        val buffer=ByteArrayOutputStream();val bytes=ByteArray(8192)
        while(true){val n=stream.read(bytes);if(n<0)break;if(buffer.size()+n>4_000_000)throw RouterVoiceException("voice_protocol_error");buffer.write(bytes,0,n)}
        buffer.toString("UTF-8")
    }
    override fun validate(settings:VoiceSettings){
        if(!RouterVoiceProtocol.compatible(readJson("/models"),settings.routerModel))throw RouterVoiceException("voice_router_model_error")
        if(!RouterVoiceProtocol.compatible(readJson("/models?output_modalities=speech"),settings.routerSpeechModel,true))throw RouterVoiceException("voice_router_speech_error")
    }
    override fun answer(body:String)=readJson("/chat/completions",body)
    override fun speak(body:String,chunk:(ByteArray)->Unit){
        execute("/audio/speech",body).use{response->
            if(response.body?.contentType()?.let{it.type=="audio" && it.subtype=="pcm"}!=true)throw RouterVoiceException("voice_protocol_error")
            val stream=response.body?.byteStream()?:throw RouterVoiceException("voice_protocol_error")
            RouterVoiceProtocol.readPcm(stream,chunk)
        }
    }
    override fun close(){synchronized(lock){closed=true;call?.cancel();call=null};client.dispatcher.executorService.shutdown();client.connectionPool.evictAll()}
}

/** Half-duplex: bounded speech turn → audio/image understanding → speech, all off the UI thread. */
class RouterVoiceAssistant(
    private val settings:VoiceSettings,private val knowledge:String,private val audio:VoiceAudioPort,
    private val service:RouterVoiceService,private val state:(String)->Unit,private val stopped:(String?)->Unit,
    private val handler:Handler=Handler(Looper.getMainLooper()),
    private val worker:ExecutorService=Executors.newSingleThreadExecutor(),private val connected:()->Unit={},
):VoiceConversation {
    @Volatile override var active=false;private set
    @Volatile override var ready=false;private set
    @Volatile private var accepting=false
    @Volatile private var image:ByteArray?=null
    private val turns=RouterVoiceTurns()
    private val history=mutableListOf<Pair<String,String>>()
    private val timeout=Runnable{stop("voice_time_limit")}
    private fun ui(action:()->Unit){handler.post{if(active)action()}}
    private fun work(action:()->Unit){
        worker.execute{try{if(active)action()}catch(e:RouterVoiceException){ui{stop(e.reason)}}catch(_:InterruptedException){}catch(_:Exception){ui{stop("voice_service_error")}}}
    }
    override fun start(){
        check(Looper.myLooper()==handler.looper);check(!active);active=true;state("voice_connecting")
        handler.postDelayed(timeout,settings.sessionMinutes.coerceIn(1,60)*60_000L)
        work{
            service.validate(settings)
            ui{
                try{audio.start(::input,{ui{stop("voice_audio_error")}})}catch(_:Exception){stop("voice_audio_error");return@ui}
                ready=true;connected()
                work{speak(RouterVoiceProtocol.GREETING)}
            }
        }
    }
    private fun input(bytes:ByteArray,count:Int){
        if(!active || !accepting)return
        val turn=turns.push(bytes,count)?:return
        accepting=false
        ui{state("voice_thinking")}
        work{
            val reply=RouterVoiceProtocol.reply(service.answer(RouterVoiceProtocol.request(settings,knowledge,history,turn,image)))
            if(!active)return@work
            if(reply.user.isNotBlank()){history.add(reply.user to reply.text);while(history.size>6)history.removeAt(0)}
            speak(reply.text)
        }
    }
    private fun speak(text:String){
        ui{state("voice_speaking")}
        var until=SystemClock.uptimeMillis()
        service.speak(RouterVoiceProtocol.speech(settings,text)){bytes->
            if(!active)throw InterruptedException()
            while(active && audio.pendingOutputBytes()>240000)Thread.sleep(20)
            if(!active)throw InterruptedException()
            audio.enqueue(bytes);until=maxOf(until,SystemClock.uptimeMillis())+bytes.size*1000L/48000
        }
        handler.postDelayed({if(active){turns.reset();accepting=true;state("voice_listening")}},(until-SystemClock.uptimeMillis()).coerceAtLeast(0)+400)
    }
    @Synchronized override fun sendImage(jpeg:ByteArray):Boolean {
        if(!active || !ready || jpeg.size>60000)return false
        image=jpeg // Only the newest JPEG is retained; no frame/history uploads during silence.
        return true
    }
    @Synchronized override fun stop(reason:String?){
        if(!active)return
        active=false;ready=false;accepting=false;image=null
        handler.removeCallbacks(timeout);turns.reset();service.close();worker.shutdownNow();audio.close();stopped(reason)
    }
}
