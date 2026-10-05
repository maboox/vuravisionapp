package com.vuravision.classroom

import android.content.Context
import android.graphics.*
import android.view.View

/** A non-intercepting HUD; the user's two fingers stay on the canvas. */
class HistoryScrubView(context:Context):View(context){
    var position=0;var total=0
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c:Canvas){
        val d=resources.displayMetrics.density;val margin=20*d;val y=height-20*d
        paint.color=SURFACE;c.drawRoundRect(0f,0f,width.toFloat(),height.toFloat(),16*d,16*d,paint)
        paint.color=NAVY;paint.textSize=14*d;paint.textAlign=Paint.Align.CENTER
        val text=context.tr("History · $position / $total","تاریخچه · $position از $total")
        paint.typeface=Fonts.face(if(Fonts.primary(text))Fonts.persianId else Fonts.englishId)
        c.drawText(text,width/2f,24*d,paint)
        paint.strokeCap=Paint.Cap.ROUND;paint.strokeWidth=4*d;paint.color=OUTLINE;c.drawLine(margin,y,width-margin,y,paint)
        val x=margin+(width-margin*2)*(if(total==0)0f else position.toFloat()/total)
        paint.color=TEAL;c.drawLine(margin,y,x,y,paint);c.drawCircle(x,y,7*d,paint)
    }
    fun update(value:Int,count:Int){position=value;total=count;contentDescription=context.tr("History $value of $count","تاریخچه $value از $count");invalidate()}
}
