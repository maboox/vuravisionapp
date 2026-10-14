package com.vuravision.classroom.room

import android.content.Context
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import java.io.*
import java.net.*
import com.vuravision.classroom.Media
import com.vuravision.classroom.ui.label
import com.vuravision.classroom.recent
import com.vuravision.classroom.ui.row
import com.vuravision.classroom.ui.s
import com.vuravision.classroom.board.newId

/** Room invitations contain a literal local IPv4 address, never an Internet relay. */
class RoomAddress(val host:String,val port:Int,val invite:String) {
    val path="/r/$invite"
    val url="http://$host:$port$path"
    init {
        require(host.matches(Regex("[0-9.]{7,15}"))&&port in 1..65535&&invite.matches(Regex("[a-f0-9]{48}"))){"invalid_address"}
        val ip=InetAddress.getByName(host);require(ip is Inet4Address&&(ip.isSiteLocalAddress||ip.isLoopbackAddress||ip.isLinkLocalAddress)){"local_network_only"}
    }
    companion object {
        fun parse(text:String):RoomAddress {val uri=URI(text.trim());require(uri.scheme=="http"&&uri.userInfo==null&&uri.query==null&&uri.fragment==null)
            val parts=uri.path.trim('/').split('/');require(parts.size==2&&parts[0]=="r");return RoomAddress(requireNotNull(uri.host),uri.port,parts[1])}
    }
}

/** Bounded local sockets leave the existing Internet HTTPS policy unchanged. */
class RoomHttp(private val address:RoomAddress,private val peer:String="",private val credential:String="",private val key:ByteArray?=null) {
    init{require(peer.isEmpty()||peer.matches(Regex("[a-zA-Z0-9-]{1,80}")));require(credential.isEmpty()||credential.matches(Regex("[a-f0-9]{48}")))}
    private fun open(method:String,endpoint:String,length:Long,body:(OutputStream)->Unit,read:(Int,InputStream,Long)->Unit){
        Socket().use{socket->
            socket.connect(InetSocketAddress(address.host,address.port),2500);socket.soTimeout=8000;socket.tcpNoDelay=true
            val out=BufferedOutputStream(socket.getOutputStream())
            val auth=key?.let{authHeader(it,"$method $endpoint")}?:credential
            val header="$method ${address.path}$endpoint HTTP/1.1\r\nHost: ${address.host}:${address.port}\r\nConnection: close\r\nContent-Type: application/json\r\nContent-Length: $length\r\nX-Vura-Peer: $peer\r\nX-Vura-Auth: $auth\r\n\r\n"
            out.write(header.toByteArray(Charsets.US_ASCII));body(out);out.flush();val input=BufferedInputStream(socket.getInputStream())
            fun line():String{val b=ByteArrayOutputStream();while(true){val n=input.read();check(n>=0){"connection_closed"};if(n==10)break;b.write(n);require(b.size()<8192)};return b.toString("US-ASCII").trimEnd('\r')}
            val status=line().split(' ').getOrNull(1)?.toIntOrNull()?:error("invalid_response")
            var size=-1L;var count=0
            while(true){val row=line();if(row.isEmpty())break;require(++count<100);if(row.startsWith("content-length:",true))size=row.substringAfter(':').trim().toLong()}
            require(size in 0..64L*1024*1024){"invalid_response_size"};read(status,input,size)
        }
    }
    private fun plain(endpoint:String,data:Any?,raw:String?=null):String{
        val bytes=raw?.toByteArray()?:data?.let{Gson().toJson(it).toByteArray()}?:ByteArray(0);require(bytes.size<=48*1024*1024){"request_too_large"};var result=""
        open(if(data==null&&raw==null)"GET"else"POST",endpoint,bytes.size.toLong(),{it.write(bytes)}){status,input,size->
            require(size<=48*1024*1024);val out=ByteArrayOutputStream();copyExact(input,out,size)
            check(status==200){if(status==401)"removed"else if(status==404)"room_closed"else"connection_error"};result=out.toString("UTF-8")}
        return result
    }
    private fun envelope(payload:String)=Gson().toJson(RoomEnvelope(System.currentTimeMillis(),credential,payload)).toByteArray()
    /** Sealed when the session has a key (every request after joining); /info and the key exchange itself are plain. */
    fun json(endpoint:String,data:Any?=null):String{
        val k=key?:return plain(endpoint,data)
        val body=RoomCrypto.text(RoomCrypto.seal(k,envelope(Gson().toJson(data)),"POST $endpoint"))
        return String(RoomCrypto.open(k,RoomCrypto.bytes(plain(endpoint,null,body).trim()),"RESP $endpoint"),Charsets.UTF_8)
    }
    /** Joins with a fresh key pair; returns the welcome and the session key the host agreed. */
    fun join(profile:DeviceProfile):Pair<RoomWelcome,ByteArray>{
        val pair=RoomCrypto.keyPair()
        val sealed=Gson().fromJson(plain("/join",RoomJoin(profile,key=RoomCrypto.publicKey(pair))),RoomSealedWelcome::class.java)
        val k=RoomCrypto.agree(pair,sealed.key,address.invite)
        return Gson().fromJson(String(RoomCrypto.open(k,RoomCrypto.bytes(sealed.data),"welcome"),Charsets.UTF_8),RoomWelcome::class.java) to k
    }
    private fun authHeader(k:ByteArray,label:String)=RoomCrypto.text(RoomCrypto.seal(k,envelope(label),"auth $label"))
    fun upload(file:File){require(file.isFile&&file.length() in 1..64L*1024*1024);require(file.name.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,100}"))&&!file.name.contains(".."))
        val sealed=key?.let{RoomCrypto.seal(it,file.readBytes(),"PUT ${file.name}")}
        open("PUT","/asset/${file.name}",sealed?.size?.toLong()?:file.length(),{out->if(sealed!=null)out.write(sealed) else file.inputStream().use{it.copyTo(out)}}){status,input,size->val out=ByteArrayOutputStream();require(size<=1024*1024);copyExact(input,out,size);check(status==200){if(status==401)"removed"else"asset_upload_failed"}}}
    fun download(name:String,file:File){require(name.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,100}"))&&!name.contains(".."));val temp=File(file.parentFile,"${file.name}.room-part")
        try{open("GET","/asset/$name",0,{}){status,input,size->check(status==200){if(status==401)"removed"else"asset_download_failed"}
            val k=key
            if(k==null)temp.outputStream().use{copyExact(input,it,size)}
            else{val buffer=ByteArrayOutputStream();copyExact(input,buffer,size);temp.writeBytes(RoomCrypto.open(k,buffer.toByteArray(),"FILE $name"))}}
            check(temp.renameTo(file)){"asset_save_failed"}}finally{temp.delete()}}
    private fun copyExact(input:InputStream,out:OutputStream,length:Long){var remaining=length;val bytes=ByteArray(32768);while(remaining>0){val n=input.read(bytes,0,minOf(remaining,bytes.size.toLong()).toInt());check(n>0){"connection_closed"};out.write(bytes,0,n);remaining-=n}}
}

/** The guest side of the room WebSocket: one sealed poll at a time, answered in order. */
class RoomSocket(address:RoomAddress,peer:String,private val credential:String,private val key:ByteArray):AutoCloseable{
    private val answers=java.util.concurrent.LinkedBlockingQueue<String>()
    @Volatile var open=false;private set
    @Volatile private var failed=false
    private val client=okhttp3.OkHttpClient.Builder().connectTimeout(3,java.util.concurrent.TimeUnit.SECONDS).readTimeout(0,java.util.concurrent.TimeUnit.SECONDS).pingInterval(15,java.util.concurrent.TimeUnit.SECONDS).build()
    private val socket=client.newWebSocket(okhttp3.Request.Builder().url("ws://${address.host}:${address.port}${address.path}/ws")
        .header("X-Vura-Peer",peer).header("X-Vura-Auth",RoomCrypto.text(RoomCrypto.seal(key,Gson().toJson(RoomEnvelope(System.currentTimeMillis(),credential,"GET /ws")).toByteArray(),"auth GET /ws"))).build(),
        object:okhttp3.WebSocketListener(){
            override fun onOpen(webSocket:okhttp3.WebSocket,response:okhttp3.Response){open=true}
            override fun onMessage(webSocket:okhttp3.WebSocket,text:String){answers.offer(text)}
            override fun onClosed(webSocket:okhttp3.WebSocket,code:Int,reason:String){open=false;failed=true}
            override fun onFailure(webSocket:okhttp3.WebSocket,t:Throwable,response:okhttp3.Response?){open=false;failed=true}
        })
    val usable get()=!failed
    /** Blocking; throws when the socket is not open, too slow (8 s) or the message is too big for one frame. */
    fun poll(request:RoomPoll):String{
        check(open&&!failed){"socket_closed"}
        val sealed=RoomCrypto.text(RoomCrypto.seal(key,Gson().toJson(RoomEnvelope(System.currentTimeMillis(),credential,Gson().toJson(request))).toByteArray(),"WS /poll"))
        require(sealed.length<=8*1024*1024){"too_large_for_socket"}
        answers.clear();check(socket.send(sealed)){"socket_closed"}
        val answer=answers.poll(8,java.util.concurrent.TimeUnit.SECONDS)?:run{failed=true;error("socket_timeout")}
        return String(RoomCrypto.open(key,RoomCrypto.bytes(answer),"WS RESP"),Charsets.UTF_8)
    }
    override fun close(){runCatching{socket.close(1000,null)};client.dispatcher.executorService.shutdown();client.connectionPool.evictAll()}
}

class RoomServer(val engine:RoomEngine,private val media:Media):NanoWSD("0.0.0.0",0) {
    val invite=RoomEngine.secret()
    private val base="/r/$invite"
    private val gson=Gson()
    fun end(){engine.end();stop()}
    /** Session keys agreed at join time, per guest; they live only as long as the room. */
    private val keys=java.util.concurrent.ConcurrentHashMap<String,ByteArray>()
    /** The 4-digit code the host compares with the guest's screen before approving. */
    fun code(peer:String)=keys[peer]?.let{RoomCrypto.code(it)}
    /** Opens a sealed envelope, checks the credential and that it is recent (two minutes), and returns its payload. */
    private fun opened(id:String,key:ByteArray,sealed:ByteArray,label:String):String{
        val e=gson.fromJson(String(RoomCrypto.open(key,sealed,label),Charsets.UTF_8),RoomEnvelope::class.java)
        check(kotlin.math.abs(System.currentTimeMillis()-e.t)<=120_000&&engine.authenticate(id,e.c)){"removed"}
        return e.p
    }
    /**
     * The guest's polling over one WebSocket instead of a new connection every 140 ms (item 84). Same sealed envelopes;
     * the handshake is authenticated with a sealed header, and anything that fails closes the socket (the guest then
     * falls back to plain sealed HTTP).
     */
    override fun openWebSocket(handshake:IHTTPSession):NanoWSD.WebSocket=object:NanoWSD.WebSocket(handshake){
        val id=handshake.headers["x-vura-peer"].orEmpty()
        val key=keys[id]
        val authorized=handshake.uri=="$base/ws"&&key?.let{k->runCatching{opened(id,k,RoomCrypto.bytes(handshake.headers["x-vura-auth"].orEmpty()),"auth GET /ws")}.isSuccess}==true
        override fun onOpen(){if(!authorized)runCatching{close(NanoWSD.WebSocketFrame.CloseCode.PolicyViolation,"removed",false)}}
        override fun onClose(code:NanoWSD.WebSocketFrame.CloseCode?,reason:String?,remote:Boolean){}
        override fun onPong(pong:NanoWSD.WebSocketFrame?){}
        override fun onException(e:IOException?){}
        override fun onMessage(message:NanoWSD.WebSocketFrame){
            val k=key;if(!authorized||k==null)return
            val answer=try{
                val poll=gson.fromJson(opened(id,k,RoomCrypto.bytes(message.textPayload),"WS /poll"),RoomPoll::class.java)
                val permission=engine.frame(id,engine.revision)
                if(permission.approved&&permission.role!=RoomRoles.VIEW)poll.operations.flatMap{it.patches}.mapNotNull{it.after}.filter{it.asset.isNotEmpty()}.forEach{require(media.file(it.asset).isFile){"asset_missing"}}
                RoomCrypto.text(RoomCrypto.seal(k,gson.toJson(engine.poll(id,poll)).toByteArray(),"WS RESP"))
            }catch(_:Exception){runCatching{close(NanoWSD.WebSocketFrame.CloseCode.PolicyViolation,"removed",false)};return}
            runCatching{send(answer)}
        }
    }
    override fun serveHttp(session:IHTTPSession):Response {
        if(!session.uri.startsWith("$base/"))return response(Response.Status.NOT_FOUND,"room_closed")
        return try{
            val path=session.uri.removePrefix(base)
            if(path=="/info"&&session.method==Method.GET)return json(RoomListing(name=engine.name,host=engine.frame(engine.hostId,engine.revision).members.first().profile))
            if(path=="/join"&&session.method==Method.POST){
                val join=gson.fromJson(body(session,128*1024),RoomJoin::class.java);require(join.key.isNotEmpty()){"update_required"}
                val pair=RoomCrypto.keyPair();val key=RoomCrypto.agree(pair,join.key,invite)
                val welcome=engine.join(join.copy(key=""));keys[welcome.peer]=key
                return json(RoomSealedWelcome(RoomCrypto.publicKey(pair),RoomCrypto.text(RoomCrypto.seal(key,gson.toJson(welcome).toByteArray(),"welcome"))))
            }
            val id=session.headers["x-vura-peer"].orEmpty()
            val key=keys[id]?:return response(Response.Status.UNAUTHORIZED,"removed")
            fun sealedJson(value:Any)=newFixedLengthResponse(Response.Status.OK,"text/plain; charset=utf-8",RoomCrypto.text(RoomCrypto.seal(key,gson.toJson(value).toByteArray(),"RESP $path"))).apply{addHeader("Cache-Control","no-store")}
            if(session.method==Method.POST&&path in listOf("/poll","/leave")){
                val payload=try{opened(id,key,RoomCrypto.bytes(body(session,48*1024*1024).trim()),"POST $path")}catch(_:Exception){return response(Response.Status.UNAUTHORIZED,"removed")}
                return when(path){
                    "/poll"->{
                        val poll=gson.fromJson(payload,RoomPoll::class.java);val permission=engine.frame(id,engine.revision)
                        if(permission.approved&&permission.role!=RoomRoles.VIEW)poll.operations.flatMap{it.patches}.mapNotNull{it.after}.filter{it.asset.isNotEmpty()}.forEach{require(media.file(it.asset).isFile){"asset_missing"}}
                        sealedJson(engine.poll(id,poll))
                    }
                    else->{engine.leave(id);keys.remove(id);sealedJson(mapOf("ok" to true))}
                }
            }
            try{opened(id,key,RoomCrypto.bytes(session.headers["x-vura-auth"].orEmpty()),"auth ${session.method.name} $path")}catch(_:Exception){return response(Response.Status.UNAUTHORIZED,"removed")}
            when {
                path.startsWith("/asset/")->{
                    val member=engine.frame(id,engine.revision);check(member.approved){"not_approved"};val name=path.removePrefix("/asset/")
                    require(name.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,100}"))&&!name.contains(".."));val file=media.file(name)
                    if(session.method==Method.GET){check(engine.referencesAsset(name)&&file.isFile&&file.length()<=64L*1024*1024)
                        val sealed=RoomCrypto.seal(key,file.readBytes(),"FILE $name")
                        newFixedLengthResponse(Response.Status.OK,"application/octet-stream",java.io.ByteArrayInputStream(sealed),sealed.size.toLong()).apply{addHeader("Cache-Control","no-store")}}
                    else if(session.method==Method.PUT){
                        check(member.role!=RoomRoles.VIEW){"read_only"};val size=session.headers["content-length"]?.toLongOrNull()?:-1;require(size in 1..64L*1024*1024+64)
                        val files=mutableMapOf<String,String>();session.parseBody(files);val temporary=File(requireNotNull(files["content"]));check(temporary.length()==size)
                        val bytes=RoomCrypto.open(key,temporary.readBytes(),"PUT $name")
                        if(!file.exists()){val staged=File(media.dir,"${name}.${newId()}.room-part")
                            try{staged.writeBytes(bytes);check(staged.renameTo(file))}finally{staged.delete()}}
                        else check(file.length()==bytes.size.toLong()&&file.readBytes().contentEquals(bytes)){"asset_conflict"};sealedJson(mapOf("ok" to true))
                    }else response(Response.Status.METHOD_NOT_ALLOWED,"invalid_method")
                }
                else->response(Response.Status.NOT_FOUND,"room_closed")
            }
        }catch(_:Exception){response(Response.Status.BAD_REQUEST,"invalid_request")}
    }
    private fun body(s:IHTTPSession,max:Int):String{val size=s.headers["content-length"]?.toLongOrNull()?:-1;require(size in 1..max.toLong());val data=mutableMapOf<String,String>();s.parseBody(data);return requireNotNull(data["postData"]).also{require(it.toByteArray().size<=max)}}
    private fun json(value:Any)=newFixedLengthResponse(Response.Status.OK,"application/json; charset=utf-8",gson.toJson(value)).apply{addHeader("Cache-Control","no-store")}
    private fun response(status:Response.Status,message:String)=newFixedLengthResponse(status,"text/plain",message)
    fun addresses(context:Context):List<RoomAddress> = runCatching{
        val manager=context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val preferred=manager.allNetworks.flatMap{manager.getLinkProperties(it)?.linkAddresses.orEmpty().map{v->v.address}}
        val fallback=NetworkInterface.getNetworkInterfaces().toList().filter{it.isUp&&!it.isLoopback}.flatMap{it.inetAddresses.toList()}
        (preferred+fallback).filter{it is Inet4Address&&it.isSiteLocalAddress}.mapNotNull{it.hostAddress}.distinct().map{RoomAddress(it,listeningPort,invite)}
    }.getOrDefault(emptyList())
}

/** An existing immutable asset must match bytes, not only the file length. */
internal fun sameFileContents(first:File,second:File):Boolean {
    if(first.length()!=second.length())return false
    return first.inputStream().buffered().use{a->second.inputStream().buffered().use{b->
        val left=ByteArray(32768);val right=ByteArray(32768)
        var n=a.read(left)
        while(n>=0){
            var read=0;while(read<n){val count=b.read(right,read,n-read);if(count<=0)return false;read+=count}
            for(i in 0 until n)if(left[i]!=right[i])return false
            n=a.read(left)
        }
        b.read()<0
    }}
}
