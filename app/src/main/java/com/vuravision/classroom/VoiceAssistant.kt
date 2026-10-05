package com.vuravision.classroom

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

interface VoiceWire {
    interface Events {
        fun opened()
        fun message(text:String)
        fun closed(code:Int)
        fun failed(httpCode:Int?)
    }
    fun connect(key:String,events:Events)
    fun send(text:String):Boolean
    fun queuedBytes():Long
    fun close()
}
class SocketVoiceWire(private val endpoint:String,private val authHeader:String):VoiceWire {
    private val client=OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(0,TimeUnit.SECONDS).pingInterval(20,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    private var socket:WebSocket?=null
    override fun connect(key:String,events:VoiceWire.Events){
        // Native clients can send the API key in a header; it never appears in a URL/log.
        val request=Request.Builder().url(endpoint).header(authHeader,if(authHeader=="Authorization")"Bearer $key"else key).build()
        socket=client.newWebSocket(request,object:WebSocketListener(){
            override fun onOpen(webSocket:WebSocket,response:Response){events.opened()}
            override fun onMessage(webSocket:WebSocket,text:String){events.message(text)}
            override fun onMessage(webSocket:WebSocket,bytes:ByteString){events.message(bytes.utf8())}
            override fun onClosing(webSocket:WebSocket,code:Int,reason:String){webSocket.close(code,null)}
            override fun onClosed(webSocket:WebSocket,code:Int,reason:String){events.closed(code)}
            override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?){events.failed(response?.code)}
        })
    }
    override fun send(text:String)=socket?.send(text)==true
    override fun queuedBytes()=socket?.queueSize()?:0
    override fun close(){socket?.cancel();socket=null;client.dispatcher.executorService.shutdown();client.connectionPool.evictAll()}
}
class GoogleVoiceWire:VoiceWire by SocketVoiceWire(VoiceProtocol.ENDPOINT,"x-goog-api-key")
class OpenaiVoiceWire(settings:VoiceSettings):VoiceWire by SocketVoiceWire("wss://api.openai.com/v1/realtime?model=${settings.openaiModel}","Authorization")

/** Owns a single foreground conversation. No Store/document mutator is exposed. */
class VoiceAssistant(
    private val settings:VoiceSettings,private val key:String,private val knowledge:String,
    private val audio:VoiceAudioPort,
    private val state:(String)->Unit,private val stopped:(String?)->Unit,
    private val wireFactory:()->VoiceWire={if(settings.provider=="openai")OpenaiVoiceWire(settings)else GoogleVoiceWire()},
    private val handler:Handler=Handler(Looper.getMainLooper()),
    private val connected:()->Unit={},
) :VoiceConversation {
    private val codec=if(settings.provider=="openai")OpenaiVoiceCodec else GeminiVoiceCodec
    @Volatile override var active=false;private set
    @Volatile override var ready=false;private set
    @Volatile private var wire:VoiceWire?=null
    @Volatile private var connection=0
    private var handle:String?=null
    private var failures=0
    private var greeted=false
    @Volatile private var micStarted=false
    private var reconnecting=false
    private var reconnectRequested=false
    private var lastState=""
    private val timeout=Runnable{stop("voice_time_limit")}
    private var connectTimeout:Runnable?=null
    @Volatile private var muted=false
    override fun setMuted(value:Boolean){
        if(muted==value)return
        muted=value;audio.setMuted(value)
        if(value && active && ready){val silence=ByteArray(if(settings.provider=="openai")38400 else 25600);wire?.send(codec.audio(silence,silence.size))}
    }
    override fun start(){
        check(Looper.myLooper()==handler.looper);check(!active)
        active=true
        handler.postDelayed(timeout,settings.sessionMinutes.coerceIn(1,60)*60_000L)
        connect()
    }
    private fun notifyState(value:String){if(value!=lastState){lastState=value;state(value)}}
    private fun connect(){
        if(!active)return
        ready=false;reconnecting=false;reconnectRequested=false
        notifyState("voice_connecting")
        val token=++connection
        val next=wireFactory();wire=next
        fun current()=active && token==connection
        fun ui(action:()->Unit){handler.post{if(current())action()}}
        connectTimeout?.let{handler.removeCallbacks(it)}
        connectTimeout=Runnable{if(current() && !ready)stop("voice_network_error")}.also{handler.postDelayed(it,25_000)}
        try{next.connect(key,object:VoiceWire.Events{
            override fun opened(){ui{if(!next.send(codec.setup(settings,knowledge,handle)))stop("voice_network_error")}}
            override fun message(text:String){
                if(!current())return
                if(text.length>4_000_000){ui{stop("voice_protocol_error")};return}
                try{
                    val packet=codec.parse(text)
                    // Interrupt playback immediately, before additional audio from this message.
                    if(!current())return
                    if(packet.interrupted)audio.interrupt()
                    if(!packet.interrupted && micStarted)packet.audio.forEach{if(current())audio.enqueue(it)}
                    ui{
                        if(packet.error){stop(when(packet.errorCode){"invalid_api_key","insufficient_quota","rate_limit_exceeded"->"voice_key_or_quota";"model_not_found"->"voice_model_error";else->"voice_service_error"});return@ui}
                        packet.resumable?.let{if(!it)handle=null else packet.resumeHandle?.let{h->handle=h}}
                        if(packet.ready){
                            ready=true;failures=0;connectTimeout?.let{handler.removeCallbacks(it)}
                            connected()
                            if(!micStarted){
                                try{
                                    micStarted=true
                                    audio.start({bytes,count->
                                        if(active && ready && !muted){
                                            val socket=wire
                                            if(socket==null || socket.queuedBytes()>128_000 || !socket.send(codec.audio(bytes,count)))handler.post{if(active)stop("voice_network_slow")}
                                        }
                                    },{handler.post{if(active)stop("voice_audio_error")}})
                                }catch(_:Exception){stop("voice_audio_error");return@ui}
                            }
                            notifyState("voice_listening")
                            if(!greeted){greeted=true;if(!next.send(codec.greeting()))stop("voice_network_error")}
                        }
                        if(packet.interrupted || packet.complete)notifyState("voice_listening")
                        else if(packet.audio.isNotEmpty())notifyState("voice_speaking")
                        if(packet.goAway){
                            reconnectRequested=true
                            // Give a spoken turn a chance to finish; the server may close sooner.
                            handler.postDelayed({if(current() && reconnectRequested)reconnect()},5000)
                        }
                        if(reconnectRequested && (packet.complete || packet.goAway && lastState!="voice_speaking"))reconnect()
                    }
                }catch(_:Exception){ui{stop("voice_protocol_error")}}
            }
            override fun closed(code:Int){ui{if(code==1000 || code==1001)reconnect()else stop(if(code==1008)"voice_key_or_quota"else "voice_service_error")}}
            override fun failed(httpCode:Int?){ui{when(httpCode){401,403,429->stop("voice_key_or_quota");400,404->stop("voice_model_error");else->reconnect()}}}
        })}catch(_:Exception){stop("voice_network_error")}
    }
    private fun reconnect(){
        if(!active || reconnecting)return
        // OpenAI does not resume a dropped WebSocket session; do not silently lose lesson context.
        if(settings.provider=="openai" && greeted){stop("voice_network_error");return}
        if(failures++>=2){stop("voice_network_error");return}
        reconnecting=true;ready=false;++connection
        wire?.close();wire=null;audio.interrupt();notifyState("voice_reconnecting")
        handler.postDelayed({if(active)connect()},1500)
    }
    override fun sendImage(jpeg:ByteArray):Boolean {
        if(!active || !ready || jpeg.size>600_000)return false
        val socket=wire?:return false
        if(socket.queuedBytes()+jpeg.size*4L/3+256>110_000)return false
        return socket.send(codec.image(jpeg))
    }
    override fun stop(reason:String?){
        if(!active)return
        active=false;ready=false;++connection
        handler.removeCallbacks(timeout);connectTimeout?.let{handler.removeCallbacks(it)}
        wire?.close();wire=null;audio.close();stopped(reason)
    }
}
