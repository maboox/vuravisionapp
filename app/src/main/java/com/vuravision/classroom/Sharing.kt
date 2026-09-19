package com.vuravision.classroom

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.net.NetworkInterface
import java.security.SecureRandom

class Sharing(private val file: File, private val mime: String) : NanoHTTPD(0) {
    val token =
        ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private val expires = System.currentTimeMillis() + 10 * 60 * 1000
    @Volatile var downloads = 0

    override fun serve(session: IHTTPSession): Response {
        if (System.currentTimeMillis() > expires || session.uri != "/$token")
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        if (session.method != Method.GET && session.method != Method.HEAD)
            return newFixedLengthResponse(
                Response.Status.METHOD_NOT_ALLOWED,
                MIME_PLAINTEXT,
                "GET only",
            )
        if (session.method == Method.GET) downloads++
        return newFixedLengthResponse(Response.Status.OK, mime, file.inputStream(), file.length())
            .apply {
                addHeader("Content-Disposition", "attachment; filename=\"${file.name}\"")
                addHeader("Cache-Control", "no-store")
                addHeader("X-Content-Type-Options", "nosniff")
            }
    }

    fun url(): String? {
        val address =
            NetworkInterface.getNetworkInterfaces()
                .toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull {
                    it is java.net.Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress
                } ?: return null
        return "http://${address.hostAddress}:$listeningPort/$token"
    }

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
