package com.vuravision.classroom

import android.graphics.*
import kotlin.math.*

/** Temporary labels, never written to the lesson or its undo history. */
class MeasurementOverlay(private val board:Board){
    private var itemId:String?=null
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val expire=Runnable{itemId=null;board.invalidate()}
    val visible get()=itemId!=null
    fun clear(){board.removeCallbacks(expire);itemId=null}
    fun show(o:Item){clear();itemId=o.id;board.postDelayed(expire,6000);board.invalidate()}
    fun draw(c:Canvas,zoom:Float){
        val o=board.store.page.items.firstOrNull{it.id==itemId&&it.pane==board.activePane}?:return
        val measure=Measurements.of(o)?:return;val scale=board.measureScale().toDouble()
        val unit=if(board.context.getSharedPreferences("vura",0).getFloat("pixelsPerCm",0f)>0)"cm"else"u"
        val size=15f/zoom
        fun label(text:String,x:Float,y:Float){
            paint.textSize=size;paint.typeface=Fonts.face(Fonts.englishId);paint.textAlign=Paint.Align.CENTER
            val pad=5/zoom;val w=paint.measureText(text)/2+pad
            paint.color=0xf4ffffff.toInt();paint.style=Paint.Style.FILL;c.drawRoundRect(x-w,y-size,x+w,y+pad,4/zoom,4/zoom,paint)
            paint.color=TEAL;c.drawText(text,x,y,paint)
        }
        fun local(text:String,x:Double,y:Double){val p=o.global(x.toFloat(),y.toFloat());label(text,p.first,p.second)}
        val v=Measurements.vertices(o)
        if(v!=null){
            v.indices.forEach{i->val a=v[i];val b=v[(i+1)%v.size];val dx=b.first-a.first;val dy=b.second-a.second;val length=hypot(dx,dy).coerceAtLeast(.01)
                val text=DisplayNumbers.one(measure.lengths[i]/scale)+" $unit"
                paint.textSize=size;paint.typeface=Fonts.face(Fonts.englishId)
                val offset=abs(dy/length)*paint.measureText(text)/2+abs(dx/length)*size*.65+8/zoom
                local(text,(a.first+b.first)/2+dy/length*offset,(a.second+b.second)/2-dx/length*offset)
                val ax=a.first+(o.w/2-a.first)*.2;val ay=a.second+(o.h/2-a.second)*.2
                local(DisplayNumbers.one(measure.angles[i])+"°",ax,ay)
            }
        }else if(o.shape in listOf("circle","ellipse")){
            local(DisplayNumbers.one(o.w/scale)+" $unit",o.w/2.0,-18.0/zoom)
            local(DisplayNumbers.one(o.h/scale)+" $unit",o.w+24.0/zoom,o.h/2.0)
        }else{
            local(DisplayNumbers.one(measure.perimeter/scale)+" $unit",o.w/2.0,o.h/2.0-18/zoom)
            local(DisplayNumbers.one(Measurements.lineAngle(o))+"°",o.w/2.0,o.h/2.0+18/zoom)
        }
        measure.area?.let{local("A = "+DisplayNumbers.one(it/(scale*scale))+" $unit²",o.w/2.0,o.h/2.0)
            local("P = "+DisplayNumbers.one(measure.perimeter/scale)+" $unit",o.w/2.0,o.h/2.0+24/zoom)}
    }
}
