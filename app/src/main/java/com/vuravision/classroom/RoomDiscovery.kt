package com.vuravision.classroom

import android.content.Context
import android.net.nsd.*
import android.os.Handler
import android.os.Looper

/** Resolve serially on older panels; manual and QR joining do not depend on NSD. */
class RoomDiscovery(context:Context):AutoCloseable {
    private val manager by lazy{context.getSystemService(Context.NSD_SERVICE) as NsdManager}
    private val main=Handler(Looper.getMainLooper())
    private var registration:NsdManager.RegistrationListener?=null
    private var discovery:NsdManager.DiscoveryListener?=null
    private val queue=ArrayDeque<NsdServiceInfo>()
    private var resolving=false
    private var closed=false
    private var scan=0
    companion object {const val TYPE="_vura-room._tcp."}
    fun advertise(server:RoomServer){
        val listener=object:NsdManager.RegistrationListener {
            override fun onServiceRegistered(info:NsdServiceInfo){}
            override fun onRegistrationFailed(info:NsdServiceInfo,errorCode:Int){}
            override fun onServiceUnregistered(info:NsdServiceInfo){}
            override fun onUnregistrationFailed(info:NsdServiceInfo,errorCode:Int){}
        };registration=listener
        val info=NsdServiceInfo().apply{serviceName="VuraVision ${server.engine.name.take(30)}";serviceType=TYPE;port=server.listeningPort;setAttribute("invite",server.invite);setAttribute("version","1")}
        try{manager.registerService(info,NsdManager.PROTOCOL_DNS_SD,listener)}catch(_:Exception){registration=null}
    }
    fun discover(found:(String,RoomAddress)->Unit,lost:(String)->Unit,error:()->Unit){
        stopDiscovery();closed=false;val expected=scan
        fun resolveNext(){
            if(closed||scan!=expected||resolving||queue.isEmpty())return
            resolving=true;val info=queue.removeFirst()
            @Suppress("DEPRECATION")
            try{manager.resolveService(info,object:NsdManager.ResolveListener {
                override fun onResolveFailed(service:NsdServiceInfo,code:Int){main.post{if(scan==expected){resolving=false;resolveNext()}}}
                override fun onServiceResolved(service:NsdServiceInfo){main.post{
                    if(scan!=expected)return@post;resolving=false
                    if(!closed)try{val invite=service.attributes["invite"]?.toString(Charsets.UTF_8).orEmpty();found(service.serviceName,RoomAddress(service.host?.hostAddress.orEmpty(),service.port,invite))}catch(_:Exception){}
                    resolveNext()
                }}
            })}catch(_:Exception){resolving=false;resolveNext()}
        }
        val listener=object:NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type:String){}
            override fun onServiceFound(info:NsdServiceInfo){main.post{if(!closed&&scan==expected&&info.serviceType.trimEnd('.')==TYPE.trimEnd('.')){queue.addLast(info);resolveNext()}}}
            override fun onServiceLost(info:NsdServiceInfo){main.post{if(!closed&&scan==expected)lost(info.serviceName)}}
            override fun onDiscoveryStopped(type:String){}
            override fun onStartDiscoveryFailed(type:String,code:Int){main.post{if(!closed&&scan==expected)error()}}
            override fun onStopDiscoveryFailed(type:String,code:Int){}
        };discovery=listener
        try{manager.discoverServices(TYPE,NsdManager.PROTOCOL_DNS_SD,listener)}catch(_:Exception){discovery=null;error()}
    }
    fun stopDiscovery(){scan++;discovery?.let{try{manager.stopServiceDiscovery(it)}catch(_:Exception){}};discovery=null;queue.clear();resolving=false}
    override fun close(){closed=true;stopDiscovery();registration?.let{try{manager.unregisterService(it)}catch(_:Exception){}};registration=null;main.removeCallbacksAndMessages(null)}
}
