package com.vuravision.classroom

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.net.NetworkInterface
import java.security.SecureRandom

class Sharing(private val file: File, private val mime: String) : NanoHTTPD("0.0.0.0",0) {
    val token =
        ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private val expires = System.currentTimeMillis() + 30 * 60 * 1000
    @Volatile var downloads = 0

    override fun serve(session: IHTTPSession): Response {
        if (System.currentTimeMillis() > expires || session.uri !in listOf("/$token","/$token/file"))
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        if (session.method != Method.GET && session.method != Method.HEAD)
            return newFixedLengthResponse(
                Response.Status.METHOD_NOT_ALLOWED,
                MIME_PLAINTEXT,
                "GET only",
            )
        if(session.uri=="/$token")return newFixedLengthResponse(Response.Status.OK,"text/html; charset=utf-8","""
        <!doctype html><html><meta name="viewport" content="width=device-width,initial-scale=1"><title>VuraVision</title>
        <body style="margin:0;background:#20194f;color:white;font:20px system-ui;padding:48px 24px;text-align:center"><h1>VuraVision</h1><p>Classroom lesson · محتوای کلاس</p>
        <a style="display:inline-block;background:#f8a529;color:#20194f;padding:18px 28px;border-radius:16px;text-decoration:none" href="/$token/file" download>Download PDF · دانلود PDF</a><p style="font-size:14px">Available on your classroom network for 30 minutes.</p></body></html>
        """.trimIndent())
        if (session.method == Method.GET) downloads++
        return newFixedLengthResponse(Response.Status.OK, mime, file.inputStream(), file.length())
            .apply {
                addHeader("Content-Disposition", "attachment; filename=\"${file.name}\"")
                addHeader("Cache-Control", "no-store")
                addHeader("X-Content-Type-Options", "nosniff")
            }
    }

    fun urls(context:android.content.Context?=null):List<String>{
        val preferred=mutableListOf<String>()
        if(context!=null){val manager=context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            manager.allNetworks.forEach{network->val caps=manager.getNetworkCapabilities(network)
                if(caps!=null && !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) && (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)||caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET))){manager.getLinkProperties(network)?.linkAddresses?.forEach{addr->val ip=addr.address;if(ip is java.net.Inet4Address&&!ip.isLoopbackAddress)preferred.add(ip.hostAddress!!)}}
            }
        }
        val fallback=NetworkInterface.getNetworkInterfaces().toList().filter{it.isUp&&!it.isLoopback&&!it.name.startsWith("tun")&&!it.name.startsWith("rmnet")}.flatMap{it.inetAddresses.toList()}.filter{it is java.net.Inet4Address&&it.isSiteLocalAddress}.mapNotNull{it.hostAddress}
        return (preferred+fallback).distinct().map{"http://$it:$listeningPort/$token"}
    }
    fun url():String?=urls().firstOrNull()

    companion object {
        fun qr(url: String): Bitmap {
            val m = MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, 512, 512)
            return Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply {
                setPixels(
                    IntArray(512 * 512) { i ->
                        if (m[i % 512, i / 512]) 0xff172a36.toInt() else -1
                    },
                    0,
                    512,
                    0,
                    0,
                    512,
                    512,
                )
            }
        }
    }
}
