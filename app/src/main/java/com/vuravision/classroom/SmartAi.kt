package com.vuravision.classroom

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.util.Base64
import android.util.LruCache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Which service answers board questions (explain, check, steps, quiz…). Separate from the voice assistant's realtime
 * model, but it uses the same saved key for each service, so a teacher enters a key only once.
 */
data class AiSettings(val provider:String,val model:String,val dailyLimit:Int,val grade:String){
    companion object {
        val defaults=mapOf("gemini" to "gemini-2.5-flash","openai" to "gpt-4o-mini","openrouter" to "google/gemini-2.5-flash","avalai" to "gpt-4o-mini")
        val grades=listOf("primary","middle","high")
        fun load(c:Context):AiSettings{
            val p=c.getSharedPreferences("vura",0)
            val provider=p.getString("aiProvider",null)?.takeIf{it in VoiceSettings.providers}?:VoiceSettings.load(c).provider
            val model=p.getString("aiModel_$provider",null)?.takeIf{VoiceSettings.validCompatModel(it)}?:defaults.getValue(provider)
            return AiSettings(provider,model,p.getInt("aiDailyLimit",150).coerceIn(10,2000),p.getString("aiGrade","middle")?.takeIf{it in grades}?:"middle")
        }
    }
    fun save(c:Context){
        require(provider in VoiceSettings.providers && VoiceSettings.validCompatModel(model) && grade in grades)
        c.getSharedPreferences("vura",0).edit().putString("aiProvider",provider).putString("aiModel_$provider",model)
            .putInt("aiDailyLimit",dailyLimit.coerceIn(10,2000)).putString("aiGrade",grade).apply()
    }
}

/** Reasons are string keys so the UI can show a helpful sentence in either language. */
class AiException(val reason:String,message:String=reason):Exception(message)

/** The network call, replaceable in tests. */
fun interface AiTransport { fun post(url:String,headers:Map<String,String>,body:String):String }

class HttpAiTransport:AiTransport {
    private val client=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(90,TimeUnit.SECONDS).callTimeout(120,TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    override fun post(url:String,headers:Map<String,String>,body:String):String{
        val request=Request.Builder().url(url).post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
        headers.forEach{(k,v)->request.header(k,v)}
        val response=try{client.newCall(request.build()).execute()}catch(e:java.io.IOException){throw AiException("offline",e.message?:"offline")}
        response.use{r->
            if(!r.isSuccessful)throw AiException(when(r.code){401,403->"key";402,429->"quota";400,404,422->"model";else->"service"},"HTTP ${r.code}")
            val text=r.body?.string()?:throw AiException("format")
            if(text.length>4_000_000)throw AiException("format")
            return text
        }
    }
}

/**
 * One way to ask a model about the board, for every service. Only what the teacher selected is sent (a cropped
 * picture, at most 1024 px), answers are cached for the session, and a daily cap keeps a forgotten loop from
 * spending the school's credit.
 */
class SmartAi(private val context:Context,keySource:(String)->String,transportSource:AiTransport?=null) {
    private val key:(String)->String=testKey?:keySource
    private val transport:AiTransport=transportSource?:testTransport?:HttpAiTransport()
    companion object {
        /** Test seams: a fake service and key, so board actions can be exercised without a network or keystore. */
        var testTransport:AiTransport?=null
        var testKey:((String)->String)?=null
        private val cache=LruCache<String,String>(64)
        fun clearCache()=cache.evictAll()
        fun base(provider:String,c:Context)=when(provider){
            "openai"->"https://api.openai.com/v1";"openrouter"->RouterVoiceProtocol.BASE
            else->VoiceSettings.load(c).compatBase
        }
        /** The JSON object or array inside a reply, without Markdown fences or chatter around it. */
        fun jsonOf(text:String):String{
            val clean=text.replace(Regex("```(json)?",RegexOption.IGNORE_CASE),"").trim()
            val start=clean.indexOfFirst{it=='{'||it=='['};val end=clean.indexOfLast{it=='}'||it==']'}
            if(start<0||end<=start)throw AiException("format",text.take(200))
            return clean.substring(start,end+1)
        }
    }
    val settings get()=AiSettings.load(context)
    fun configured()=try{key(settings.provider).isNotBlank()}catch(_:Exception){false}
    /** No network at all means a clear "offline" message at once, instead of a long timeout. */
    private fun online():Boolean{
        val cm=context.getSystemService(ConnectivityManager::class.java)?:return true
        return cm.activeNetwork!=null
    }
    private fun today()=SimpleDateFormat("yyyyMMdd",Locale.US).format(Date())
    fun usedToday()=context.getSharedPreferences("vura",0).getInt("aiCount_${today()}",0)
    /** Blocking: call from a worker thread. */
    fun ask(system:String,prompt:String,image:Bitmap?=null,json:Boolean=false):String{
        val s=settings
        val secret=try{key(s.provider)}catch(_:Exception){""}
        if(secret.isBlank())throw AiException("no_key")
        val jpeg=image?.let{b->ByteArrayOutputStream().also{b.compress(Bitmap.CompressFormat.JPEG,85,it)}.toByteArray()}
        val id=MessageDigest.getInstance("SHA-256").run{update("${s.provider}|${s.model}|$system|$prompt|$json".toByteArray());jpeg?.let{update(it)};digest()}.joinToString(""){"%02x".format(it)}
        cache.get(id)?.let{return it}
        if(!online())throw AiException("offline")
        val prefs=context.getSharedPreferences("vura",0);val count=usedToday()
        if(count>=s.dailyLimit)throw AiException("limit")
        prefs.edit().putInt("aiCount_${today()}",count+1).apply()
        val image64=jpeg?.let{Base64.encodeToString(it,Base64.NO_WRAP)}
        val answer=if(s.provider=="gemini"){
            val parts=JSONArray().put(JSONObject().put("text",prompt))
            if(image64!=null)parts.put(JSONObject().put("inline_data",JSONObject().put("mime_type","image/jpeg").put("data",image64)))
            val body=JSONObject().put("system_instruction",JSONObject().put("parts",JSONArray().put(JSONObject().put("text",system))))
                .put("contents",JSONArray().put(JSONObject().put("role","user").put("parts",parts)))
                .put("generationConfig",JSONObject().put("temperature",.3).apply{if(json)put("responseMimeType","application/json")})
            val raw=transport.post("https://generativelanguage.googleapis.com/v1beta/models/${s.model}:generateContent",mapOf("x-goog-api-key" to secret),body.toString())
            try{
                val parts2=JSONObject(raw).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                (0 until parts2.length()).joinToString(""){parts2.getJSONObject(it).optString("text")}
            }catch(e:Exception){throw AiException("format",raw.take(200))}
        }else{
            val content=JSONArray().put(JSONObject().put("type","text").put("text",prompt))
            if(image64!=null)content.put(JSONObject().put("type","image_url").put("image_url",JSONObject().put("url","data:image/jpeg;base64,$image64")))
            val body=JSONObject().put("model",s.model).put("temperature",.3)
                .put("messages",JSONArray().put(JSONObject().put("role","system").put("content",system)).put(JSONObject().put("role","user").put("content",content)))
            val headers=mutableMapOf("Authorization" to "Bearer $secret");if(s.provider=="openrouter")headers["X-Title"]="VuraVision"
            val raw=transport.post(base(s.provider,context)+"/chat/completions",headers,body.toString())
            try{JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")}
            catch(e:Exception){throw AiException("format",raw.take(200))}
        }.trim()
        if(answer.isEmpty())throw AiException("format")
        cache.put(id,answer)
        return answer
    }
    fun askJson(system:String,prompt:String,image:Bitmap?=null):JSONObject{
        val text=ask(system,prompt,image,json=true)
        val body=jsonOf(text)
        return if(body.startsWith("["))JSONObject().put("items",JSONArray(body)) else JSONObject(body)
    }
}

/** What the teacher reads when something goes wrong: the cause and what to do, never a stack trace. */
object AiMessages {
    fun text(c:Context,reason:String)=when(reason){
        "no_key"->c.tr("Add a key for the AI service in Settings → Smart → AI service.","کلید سرویس را در تنظیمات ← هوشمند ← سرویس هوش وارد کنید.")
        "offline"->c.tr("No internet connection. Offline tools (to text, solve, plot) still work.","اینترنت وصل نیست. ابزارهای آفلاین (به متن، حل، نمودار) همچنان کار می‌کنند.")
        "key"->c.tr("The service rejected the key. Check it in Settings → Smart.","سرویس کلید را نپذیرفت. آن را در تنظیمات ← هوشمند بررسی کنید.")
        "quota"->c.tr("The service's credit or rate limit is used up. Try again later.","اعتبار یا سهمیهٔ سرویس تمام شده است. کمی بعد دوباره امتحان کنید.")
        "model"->c.tr("The service does not accept this model. Choose another in Settings → Smart.","سرویس این مدل را نمی‌پذیرد. مدل دیگری در تنظیمات ← هوشمند انتخاب کنید.")
        "limit"->c.tr("Today's request limit on this panel is reached (Settings → Smart).","سقف درخواست امروز این پنل پر شده است (تنظیمات ← هوشمند).")
        "format"->c.tr("The answer could not be read. Please try again.","پاسخ قابل خواندن نبود. دوباره امتحان کنید.")
        else->c.tr("The service did not answer. Please try again.","سرویس پاسخ نداد. دوباره امتحان کنید.")
    }
}
