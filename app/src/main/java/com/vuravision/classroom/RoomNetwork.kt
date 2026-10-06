package com.vuravision.classroom

import android.content.Context
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import java.io.*
import java.net.*

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
class RoomHttp(private val address:RoomAddress,private val peer:String="",private val credential:String="") {
    init{require(peer.isEmpty()||peer.matches(Regex("[a-zA-Z0-9-]{1,80}")));require(credential.isEmpty()||credential.matches(Regex("[a-f0-9]{48}")))}
    private fun open(method:String,endpoint:String,length:Long,body:(OutputStream)->Unit,read:(Int,InputStream,Long)->Unit){
        Socket().use{socket->
            socket.connect(InetSocketAddress(address.host,address.port),2500);socket.soTimeout=8000;socket.tcpNoDelay=true
            val out=BufferedOutputStream(socket.getOutputStream())
            val header="$method ${address.path}$endpoint HTTP/1.1\r\nHost: ${address.host}:${address.port}\r\nConnection: close\r\nContent-Type: application/json\r\nContent-Length: $length\r\nX-Vura-Peer: $peer\r\nX-Vura-Auth: $credential\r\n\r\n"
            out.write(header.toByteArray(Charsets.US_ASCII));body(out);out.flush();val input=BufferedInputStream(socket.getInputStream())
            fun line():String{val b=ByteArrayOutputStream();while(true){val n=input.read();check(n>=0){"connection_closed"};if(n==10)break;b.write(n);require(b.size()<8192)};return b.toString("US-ASCII").trimEnd('\r')}
            val status=line().split(' ').getOrNull(1)?.toIntOrNull()?:error("invalid_response")
            var size=-1L;var count=0
            while(true){val row=line();if(row.isEmpty())break;require(++count<100);if(row.startsWith("content-length:",true))size=row.substringAfter(':').trim().toLong()}
            require(size in 0..64L*1024*1024){"invalid_response_size"};read(status,input,size)
        }
    }
    fun json(endpoint:String,data:Any?=null):String{
        val bytes=data?.let{Gson().toJson(it).toByteArray()}?:ByteArray(0);require(bytes.size<=32*1024*1024){"request_too_large"};var result=""
        open(if(data==null)"GET"else"POST",endpoint,bytes.size.toLong(),{it.write(bytes)}){status,input,size->
            require(size<=32*1024*1024);val out=ByteArrayOutputStream();copyExact(input,out,size)
            check(status==200){if(status==401)"removed"else if(status==404)"room_closed"else"connection_error"};result=out.toString("UTF-8")}
        return result
    }
    fun upload(file:File){require(file.isFile&&file.length() in 1..64L*1024*1024);require(file.name.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,100}"))&&!file.name.contains(".."))
        open("PUT","/asset/${file.name}",file.length(),{out->file.inputStream().use{it.copyTo(out)}}){status,input,size->val out=ByteArrayOutputStream();require(size<=1024*1024);copyExact(input,out,size);check(status==200){if(status==401)"removed"else"asset_upload_failed"}}}
    fun download(name:String,file:File){require(name.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,100}"))&&!name.contains(".."));val temp=File(file.parentFile,"${file.name}.room-part")
        try{open("GET","/asset/$name",0,{}){status,input,size->check(status==200){if(status==401)"removed"else"asset_download_failed"};temp.outputStream().use{copyExact(input,it,size)}};check(temp.renameTo(file)){"asset_save_failed"}}finally{temp.delete()}}
    private fun copyExact(input:InputStream,out:OutputStream,length:Long){var remaining=length;val bytes=ByteArray(32768);while(remaining>0){val n=input.read(bytes,0,minOf(remaining,bytes.size.toLong()).toInt());check(n>0){"connection_closed"};out.write(bytes,0,n);remaining-=n}}
}

class RoomServer(val engine:RoomEngine,private val media:Media):NanoHTTPD("0.0.0.0",0) {
    val invite=RoomEngine.secret()
    private val base="/r/$invite"
    private val gson=Gson()
    fun end(){engine.end();stop()}
    override fun serve(session:IHTTPSession):Response {
        if(!session.uri.startsWith("$base/"))return response(Response.Status.NOT_FOUND,"room_closed")
        return try{
            val path=session.uri.removePrefix(base)
            if(path=="/info"&&session.method==Method.GET)return json(RoomListing(name=engine.name,host=engine.frame(engine.hostId,engine.revision).members.first().profile))
            if(path=="/join"&&session.method==Method.POST)return json(engine.join(gson.fromJson(body(session,128*1024),RoomJoin::class.java)))
            val id=session.headers["x-vura-peer"].orEmpty();val credential=session.headers["x-vura-auth"].orEmpty()
            if(!engine.authenticate(id,credential))return response(Response.Status.UNAUTHORIZED,"removed")
            when {
                path=="/poll"&&session.method==Method.POST->{
                    val poll=gson.fromJson(body(session,32*1024*1024),RoomPoll::class.java);val permission=engine.frame(id,engine.revision)
                    if(permission.approved&&permission.role!=RoomRoles.VIEW)poll.operations.flatMap{it.patches}.mapNotNull{it.after}.filter{it.asset.isNotEmpty()}.forEach{require(media.file(it.asset).isFile){"asset_missing"}}
                    json(engine.poll(id,poll))
                }
                path=="/leave"&&session.method==Method.POST->{engine.leave(id);json(mapOf("ok" to true))}
                path.startsWith("/asset/")->{
                    val member=engine.frame(id,engine.revision);check(member.approved){"not_approved"};val name=path.removePrefix("/asset/")
                    require(name.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,100}"))&&!name.contains(".."));val file=media.file(name)
                    if(session.method==Method.GET){check(engine.snapshot().allAssetItems().any{it.asset==name}&&file.isFile)
                        newFixedLengthResponse(Response.Status.OK,"application/octet-stream",file.inputStream(),file.length()).apply{addHeader("Cache-Control","no-store")}}
                    else if(session.method==Method.PUT){
                        check(member.role!=RoomRoles.VIEW){"read_only"};val size=session.headers["content-length"]?.toLongOrNull()?:-1;require(size in 1..64L*1024*1024)
                        val files=mutableMapOf<String,String>();session.parseBody(files);val temporary=File(requireNotNull(files["content"]));check(temporary.length()==size)
                        if(!file.exists()){val staged=File(media.dir,"${name}.${newId()}.room-part")
                            try{temporary.inputStream().use{i->staged.outputStream().use{boundedCopy(i,it,64L*1024*1024)}};check(staged.length()==size&&staged.renameTo(file))}finally{staged.delete()}}
                        else check(file.length()==size){"asset_conflict"};json(mapOf("ok" to true))
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
