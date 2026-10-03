package com.vuravision.classroom

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.zip.CRC32
import kotlin.math.roundToInt

/** Async surface copy and off-main JPEG encoding. Only one image can be in flight. */
class VoiceScreen(
    private val activity:Activity,private val surface:View,private val seconds:Int,
    private val ready:()->Boolean,private val busy:()->Boolean,
    private val send:(ByteArray)->Boolean,private val unavailable:()->Unit,
) {
    private val handler=Handler(Looper.getMainLooper())
    private val encoder=Executors.newSingleThreadExecutor()
    @Volatile private var closed=false
    @Volatile private var inFlight=false
    @Volatile private var lastHash:Long?=null
    private var failures=0
    private var buffer:Bitmap?=null
    private val revision=java.util.concurrent.atomic.AtomicInteger()
    private val tick=object:Runnable {
        override fun run(){
            if(closed)return
            handler.postDelayed(this,seconds.coerceIn(2,10)*1000L)
            if(inFlight || !ready() || busy() || !surface.isShown || surface.width<=0 || surface.height<=0)return
            val location=IntArray(2);surface.getLocationInWindow(location)
            val rect=Rect(location[0],location[1],location[0]+surface.width,location[1]+surface.height)
            val scale=minOf(1f,1280f/maxOf(rect.width(),rect.height()))
            val w=(rect.width()*scale).roundToInt().coerceAtLeast(1)
            val h=(rect.height()*scale).roundToInt().coerceAtLeast(1)
            val bitmap=buffer?.takeIf{!it.isRecycled && it.width==w && it.height==h}?:run{
                buffer?.takeIf{!it.isRecycled}?.recycle()
                Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).also{buffer=it}
            }
            inFlight=true
            val captureRevision=revision.get()
            try{
                PixelCopy.request(activity.window,rect,bitmap,{result->
                    if(closed || result!=PixelCopy.SUCCESS){
                        bitmap.recycle();buffer=null;inFlight=false
                        if(!closed && ++failures==3)unavailable()
                    }else{
                        failures=0
                        encoder.execute{
                            try{
                                val bytes=encode(bitmap);val hash=CRC32().apply{update(bytes)}.value
                                if(!closed && hash!=lastHash && send(bytes) && revision.get()==captureRevision)lastHash=hash
                            }catch(_:Exception){handler.post{if(!closed)unavailable()}}
                            finally{handler.post{if(closed){bitmap.recycle();buffer=null};inFlight=false}}
                        }
                    }
                },handler)
            }catch(_:Exception){bitmap.recycle();buffer=null;inFlight=false;if(++failures==3)unavailable()}
        }
    }
    private fun encode(bitmap:Bitmap):ByteArray {
        var source=bitmap;var owned=false
        try{
            while(true){
                val stream=ByteArrayOutputStream();source.compress(Bitmap.CompressFormat.JPEG,65,stream)
                val bytes=stream.toByteArray()
                // Keep a screen frame below the live microphone's socket backlog budget.
                if(bytes.size<=60_000 || maxOf(source.width,source.height)<=320)return bytes
                val resized=Bitmap.createScaledBitmap(source,(source.width*.75f).toInt().coerceAtLeast(1),(source.height*.75f).toInt().coerceAtLeast(1),true)
                if(owned)source.recycle();source=resized;owned=true
            }
        }finally{if(owned)source.recycle()}
    }
    fun start(){handler.post(tick)}
    fun refresh(){revision.incrementAndGet();lastHash=null}
    fun close(){closed=true;handler.removeCallbacks(tick);encoder.shutdown();if(!inFlight){buffer?.takeIf{!it.isRecycled}?.recycle();buffer=null}}
}
