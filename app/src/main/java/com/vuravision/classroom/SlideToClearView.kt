package com.vuravision.classroom

import android.content.Context
import android.graphics.*
import android.view.*

/** Clear only after dragging the thumb to the end and releasing there. */
class SlideToClearView(context:Context,val title:String,private val cleared:()->Unit):View(context){
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var dragging=false
    private var progress=0f
    private val radius get()=context.dp(24).toFloat()
    private val trackStart get()=radius+context.dp(6)
    private val trackEnd get()=(width-radius-context.dp(6)).coerceAtLeast(trackStart+1)
    private val rtl get()=layoutDirection==LAYOUT_DIRECTION_RTL
    private fun at(value:Float)=if(rtl)trackEnd-(trackEnd-trackStart)*value else trackStart+(trackEnd-trackStart)*value
    init{contentDescription=title;isFocusable=true;minimumHeight=context.dp(64)}
    override fun onDraw(c:Canvas){
        paint.color=PRIMARY_CONTAINER;c.drawRoundRect(context.dp(4).toFloat(),height/2-radius,width-context.dp(4).toFloat(),height/2+radius,radius,radius,paint)
        paint.color=NAVY;paint.textSize=context.dp(14).toFloat();paint.textAlign=Paint.Align.CENTER;paint.typeface=Fonts.face(if(Fonts.primary(title))Fonts.persianId else Fonts.englishId)
        val available=(width-radius*3).coerceAtLeast(1f);paint.textSize*=minOf(1f,available/paint.measureText(title).coerceAtLeast(1f))
        c.drawText(title,width/2f+(if(rtl)-1 else 1)*radius*.35f,height/2f-paint.fontMetrics.run{(ascent+descent)/2},paint)
        paint.color=TEAL;c.drawCircle(at(progress),height/2f,radius-2,paint)
        paint.color=Color.WHITE;paint.strokeWidth=context.dp(2).toFloat();val x=at(progress);val direction=if(rtl)-1 else 1;val arrow=context.dp(8).toFloat();val tail=context.dp(2).toFloat();val rise=context.dp(6).toFloat()
        c.drawLine(x-arrow*direction,height/2f,x+arrow*direction,height/2f,paint);c.drawLine(x+arrow*direction,height/2f,x+tail*direction,height/2f-rise,paint);c.drawLine(x+arrow*direction,height/2f,x+tail*direction,height/2f+rise,paint)
    }
    override fun onTouchEvent(e:MotionEvent):Boolean{
        if(!isEnabled)return false
        if(e.pointerCount>1||e.actionMasked==MotionEvent.ACTION_CANCEL){dragging=false;progress=0f;invalidate();return true}
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{dragging=kotlin.math.abs(e.x-at(0f))<=radius&&kotlin.math.abs(e.y-height/2f)<=radius;if(dragging)parent?.requestDisallowInterceptTouchEvent(true)}
            MotionEvent.ACTION_MOVE->{if(dragging){if(kotlin.math.abs(e.y-height/2f)>radius*2){dragging=false;progress=0f}else progress=((if(rtl)trackEnd-e.x else e.x-trackStart)/(trackEnd-trackStart)).coerceIn(0f,1f)}}
            MotionEvent.ACTION_UP->{val end=((if(rtl)trackEnd-e.x else e.x-trackStart)/(trackEnd-trackStart));val commit=dragging&&progress>=.97f&&end>=.97f&&kotlin.math.abs(e.y-height/2f)<=radius*2;dragging=false;progress=0f;if(commit){performClick();cleared()}}
        }
        invalidate();return true
    }
    override fun performClick():Boolean{super.performClick();return true}
}
