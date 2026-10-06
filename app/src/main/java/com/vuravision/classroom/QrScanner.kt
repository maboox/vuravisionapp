package com.vuravision.classroom

import android.app.Activity
import android.app.Dialog
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full-screen camera view that reads a VuraVision room QR code. Uses the long-supported
 * camera preview API, which delivers frames in a format the bundled ZXing decoder reads directly.
 */
@Suppress("DEPRECATION")
class QrScanner(private val activity: Activity, private val found: (String) -> Unit) : SurfaceHolder.Callback, android.hardware.Camera.PreviewCallback {
    private val main = Handler(Looper.getMainLooper())
    private val decoder = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private val done = AtomicBoolean(false)
    private var camera: android.hardware.Camera? = null
    private var size: android.hardware.Camera.Size? = null
    private val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    private val status = activity.label(activity.tr("Point the camera at the room QR code", "دوربین را به‌سمت QR اتاق بگیرید"), 16f, android.graphics.Color.WHITE, true)

    fun show() {
        val root = FrameLayout(activity).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        val surface = SurfaceView(activity)
        surface.holder.addCallback(this)
        root.addView(surface, FrameLayout.LayoutParams(-1, -1))
        root.addView(object : View(activity) {
            private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = activity.dp(4).toFloat(); color = android.graphics.Color.WHITE }
            override fun onDraw(c: Canvas) {
                val side = minOf(width, height) * .62f
                val r = RectF((width - side) / 2, (height - side) / 2, (width + side) / 2, (height + side) / 2)
                c.drawRoundRect(r, activity.dp(24).toFloat(), activity.dp(24).toFloat(), p)
            }
        }, FrameLayout.LayoutParams(-1, -1))
        val top = activity.row().apply { pad(12) }
        top.addView(activity.backIcon { close() }.apply { background = rounded(SURFACE, activity.dp(24).toFloat()) })
        top.addView(status, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = activity.dp(12) })
        root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        dialog.setContentView(root)
        dialog.setOnDismissListener { release(); decoder.shutdown() }
        dialog.show()
    }

    fun close() { if (dialog.isShowing) dialog.dismiss() else { release(); decoder.shutdown() } }

    override fun surfaceCreated(holder: SurfaceHolder) {
        try {
            val count = android.hardware.Camera.getNumberOfCameras()
            if (count == 0) { status.text = activity.tr("No camera on this device. Use nearby rooms or the address instead.", "این دستگاه دوربین ندارد. از فهرست اتاق‌ها یا نشانی استفاده کنید."); return }
            val info = android.hardware.Camera.CameraInfo()
            val id = (0 until count).firstOrNull { android.hardware.Camera.getCameraInfo(it, info); info.facing == android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK } ?: 0
            android.hardware.Camera.getCameraInfo(id, info)
            val cam = android.hardware.Camera.open(id); camera = cam
            val params = cam.parameters
            val preview = params.supportedPreviewSizes.filter { it.width <= 1280 && it.height <= 1280 }.maxByOrNull { it.width * it.height } ?: params.previewSize
            params.setPreviewSize(preview.width, preview.height)
            if (android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE in params.supportedFocusModes.orEmpty()) params.focusMode = android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            cam.parameters = params
            size = cam.parameters.previewSize
            val degrees = when (activity.windowManager.defaultDisplay.rotation) { Surface.ROTATION_90 -> 90; Surface.ROTATION_180 -> 180; Surface.ROTATION_270 -> 270; else -> 0 }
            val orientation = if (info.facing == android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT) (360 - (info.orientation + degrees) % 360) % 360 else (info.orientation - degrees + 360) % 360
            cam.setDisplayOrientation(orientation)
            cam.setPreviewDisplay(holder)
            cam.setPreviewCallback(this)
            cam.startPreview()
        } catch (e: Exception) {
            status.text = activity.tr("The camera is not available: ", "دوربین در دسترس نیست: ") + (e.message ?: e.javaClass.simpleName)
            release()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) { release() }

    override fun onPreviewFrame(data: ByteArray?, cam: android.hardware.Camera?) {
        val frame = data ?: return; val s = size ?: return
        if (done.get() || !busy.compareAndSet(false, true)) return
        try {
            decoder.execute {
                try {
                    val source = PlanarYUVLuminanceSource(frame, s.width, s.height, 0, 0, s.width, s.height, false)
                    val text = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), mapOf(DecodeHintType.TRY_HARDER to true)).text
                    if (CollabInvite.parse(text) != null && done.compareAndSet(false, true)) main.post { close(); found(text) }
                } catch (_: Exception) {
                } finally { busy.set(false) }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) { busy.set(false) }
    }

    private fun release() {
        camera?.let { runCatching { it.setPreviewCallback(null); it.stopPreview(); it.release() } }
        camera = null
    }
}
