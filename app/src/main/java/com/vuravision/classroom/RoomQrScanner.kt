package com.vuravision.classroom

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.graphics.ImageFormat
import android.hardware.Camera
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/** Legacy Camera remains available on the older interactive panels supported by the app. */
@Suppress("DEPRECATION")
class RoomQrScanner(private val activity:Activity,private val found:(String)->Unit):AutoCloseable,SurfaceHolder.Callback {
    private val main=Handler(Looper.getMainLooper())
    private val io=Executors.newSingleThreadExecutor()
    private var camera:Camera?=null
    private var dialog:Dialog?=null
    @Volatile private var busy=false
    @Volatile private var closed=false
    private var last=0L
    fun open(){val body=activity.column().apply{pad(12)};val surface=SurfaceView(activity);surface.holder.addCallback(this);body.addView(surface,LinearLayout.LayoutParams(-1,activity.dp(300)));body.addView(activity.label(activity.s("room_scan_hint"),14f))
        dialog=MaterialAlertDialogBuilder(activity).setCustomTitle(activity.navigationHeading(activity.s("room_scan_qr")){close()}).setView(body).setNegativeButton(activity.s("close")){_,_->close()}.create().also{it.setOnDismissListener{close()};it.show()}}
    @SuppressLint("MissingPermission")
    override fun surfaceCreated(holder:SurfaceHolder){
        if(closed||activity.checkSelfPermission(android.Manifest.permission.CAMERA)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return
        try{val id=(0 until Camera.getNumberOfCameras()).firstOrNull{val info=Camera.CameraInfo();Camera.getCameraInfo(it,info);info.facing==Camera.CameraInfo.CAMERA_FACING_BACK}?:0
            val cam=Camera.open(id);camera=cam;val params=cam.parameters;val size=params.supportedPreviewSizes.filter{it.width<=1280&&it.height<=960}.maxByOrNull{it.width*it.height}?:params.previewSize
            params.setPreviewSize(size.width,size.height);params.previewFormat=ImageFormat.NV21;if(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE in params.supportedFocusModes.orEmpty())params.focusMode=Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE;cam.parameters=params
            val info=Camera.CameraInfo();Camera.getCameraInfo(id,info);val display=when(activity.windowManager.defaultDisplay.rotation){Surface.ROTATION_90->90;Surface.ROTATION_180->180;Surface.ROTATION_270->270;else->0};val rotation=(info.orientation-display+360)%360;cam.setDisplayOrientation(rotation);cam.setPreviewDisplay(holder)
            cam.setPreviewCallback{data,_->if(!closed&&!busy&&System.currentTimeMillis()-last>300){busy=true;last=System.currentTimeMillis();val bytes=data.clone()
                io.execute{try{val source=PlanarYUVLuminanceSource(bytes,size.width,size.height,0,0,size.width,size.height,false);val reader=MultiFormatReader();val hints=mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),DecodeHintType.TRY_HARDER to true)
                    val result=try{reader.decode(BinaryBitmap(HybridBinarizer(source)),hints)}catch(_:Exception){reader.reset();reader.decode(BinaryBitmap(HybridBinarizer(PlanarYUVLuminanceSource(ByteArray(size.width*size.height).also{rotated->
                        for(y in 0 until size.height)for(x in 0 until size.width)rotated[(size.width-1-x)*size.height+y]=bytes[y*size.width+x]
                    },size.height,size.width,0,0,size.height,size.width,false))),hints)}
                    RoomAddress.parse(result.text);main.post{if(!closed){close();found(result.text)}}
                }catch(_:Exception){}finally{busy=false}}}}
            cam.startPreview()
        }catch(_:Exception){close();android.widget.Toast.makeText(activity,activity.s("room_camera_needed"),android.widget.Toast.LENGTH_LONG).show()}
    }
    override fun surfaceChanged(holder:SurfaceHolder,format:Int,width:Int,height:Int){}
    override fun surfaceDestroyed(holder:SurfaceHolder){camera?.let{runCatching{it.setPreviewCallback(null);it.stopPreview();it.release()}};camera=null}
    override fun close(){if(closed)return;closed=true;camera?.let{runCatching{it.setPreviewCallback(null);it.stopPreview();it.release()}};camera=null;dialog?.dismiss();dialog=null;io.shutdownNow();main.removeCallbacksAndMessages(null)}
}
