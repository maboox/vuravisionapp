package com.vuravision.classroom.sharing

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class LocalShareServer(private val file: File, private val port: Int = 8787) {
    private var server:ServerSocket?=null; private val pool=Executors.newCachedThreadPool(); val downloads=AtomicInteger(0)
    val token=ByteArray(18).also{SecureRandom().nextBytes(it)}.joinToString(""){b->"%02x".format(b)}
    fun start():String { server=ServerSocket(port); pool.execute { loop() }; return "http://${localIp()}:$port/$token" }
    fun stop(){ runCatching{server?.close()}; pool.shutdownNow() }
    private fun loop(){ while(server?.isClosed==false){ val s=runCatching{server!!.accept()}.getOrNull()?:break; pool.execute {
        s.use { sock -> val input=sock.getInputStream().bufferedReader(); val line=input.readLine().orEmpty(); while(input.readLine()?.isNotEmpty()==true){}
            val out=sock.getOutputStream(); if(line.contains("/$token")){ downloads.incrementAndGet(); val bytes=file.readBytes(); out.write("HTTP/1.1 200 OK\r\nContent-Type: application/pdf\r\nContent-Disposition: attachment; filename=VuraVision.pdf\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray()); out.write(bytes) }
            else out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".toByteArray()); out.flush()
        }
    }} }
    private fun localIp():String { val en=NetworkInterface.getNetworkInterfaces(); while(en.hasMoreElements()){ val i=en.nextElement(); val a=i.inetAddresses; while(a.hasMoreElements()){ val x=a.nextElement(); if(!x.isLoopbackAddress && x is Inet4Address) return x.hostAddress ?: "127.0.0.1" } }; return "127.0.0.1" }
    fun qr(url:String,size:Int=700):Bitmap { val m=QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,size,size); return Bitmap.createBitmap(size,size,Bitmap.Config.RGB_565).also { b -> for(y in 0 until size)for(x in 0 until size)b.setPixel(x,y,if(m[x,y])Color.BLACK else Color.WHITE) } }
}
