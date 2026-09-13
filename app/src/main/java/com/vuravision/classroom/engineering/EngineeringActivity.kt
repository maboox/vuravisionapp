package com.vuravision.classroom.engineering

import android.app.ActivityManager
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.vuravision.classroom.R

class EngineeringActivity:AppCompatActivity(){
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(getColor(R.color.vv_bg));setPadding(24,24,24,24)}
        val info=TextView(this).apply{setTextColor(Color.WHITE);textSize=18f;text=deviceInfo()}
        root.addView(info,LinearLayout.LayoutParams(-1,-2));root.addView(TouchDiagnosticsView(this),LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
    }
    private fun deviceInfo():String{ val dm=resources.displayMetrics;val am=getSystemService(ACTIVITY_SERVICE) as ActivityManager; val mi=ActivityManager.MemoryInfo();am.getMemoryInfo(mi)
        return "VuraVision Engineering Mode\n${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n${dm.widthPixels}×${dm.heightPixels} • density ${"%.2f".format(dm.density)} • ABI ${Build.SUPPORTED_ABIS.joinToString()}\nRAM ${mi.totalMem/1024/1024} MB • Touch below: pointer ID, pressure, size, major/minor, tool type"
    }
}
class TouchDiagnosticsView(context:android.content.Context):View(context){
    private val p=Paint(Paint.ANTI_ALIAS_FLAG);private val active=mutableMapOf<Int,Sample>();private var maxObserved=0
    data class Sample(val x:Float,val y:Float,val pressure:Float,val size:Float,val major:Float,val minor:Float,val orientation:Float,val tool:Int)
    override fun onTouchEvent(e:MotionEvent):Boolean{active.clear();for(i in 0 until e.pointerCount){val id=e.getPointerId(i);active[id]=Sample(e.getX(i),e.getY(i),e.getPressure(i),e.getSize(i),e.getTouchMajor(i),e.getTouchMinor(i),e.getOrientation(i),e.getToolType(i))};maxObserved=maxObserved.coerceAtLeast(active.size);if(e.actionMasked==MotionEvent.ACTION_UP||e.actionMasked==MotionEvent.ACTION_CANCEL)active.remove(e.getPointerId(e.actionIndex));invalidate();return true}
    override fun onDraw(c:Canvas){c.drawColor(Color.rgb(24,27,31));p.textSize=20f;p.color=Color.WHITE;c.drawText("Active: ${active.size}   Max observed: $maxObserved",24f,34f,p);active.forEach{(id,s)-> val radius=(s.major/2f).coerceAtLeast(28f);p.style=Paint.Style.STROKE;p.strokeWidth=4f;p.color=Color.rgb(255,138,36);c.drawCircle(s.x,s.y,radius,p);p.style=Paint.Style.FILL;p.color=Color.WHITE;c.drawText("#$id p=${"%.2f".format(s.pressure)} size=${"%.2f".format(s.size)} major=${"%.1f".format(s.major)} minor=${"%.1f".format(s.minor)} tool=${s.tool}",s.x+radius+8,s.y,p)}}
}
