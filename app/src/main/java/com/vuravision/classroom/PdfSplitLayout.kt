package com.vuravision.classroom

import android.content.Context
import android.view.*
import android.graphics.*

class PdfSplitLayout(context:Context,private val state:PdfWorkspaceState,val whiteboard:Board,val pdf:PdfPane,private val changed:()->Unit):ViewGroup(context){
    private val divider=object:View(context){
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c:Canvas){paint.color=0xffd0d4dd.toInt();c.drawColor(paint.color);paint.color=TEAL;c.drawRoundRect(width*.3f,height/2f-context.dp(28),width*.7f,height/2f+context.dp(28),4f,4f,paint)}
        override fun onTouchEvent(e:MotionEvent):Boolean{
            parent.requestDisallowInterceptTouchEvent(true)
            if(e.actionMasked==MotionEvent.ACTION_DOWN){whiteboard.interactiveResize=true;whiteboard.releaseBacking();pdf.beginResize()}
            if(e.actionMasked==MotionEvent.ACTION_MOVE){
                val pos=IntArray(2);this@PdfSplitLayout.getLocationOnScreen(pos)
                val fraction=(e.rawX-pos[0])/this@PdfSplitLayout.width
                state.ratio=(if(state.onRight)1f-fraction else fraction).coerceIn(.2f,.8f)
                requestLayout();this@PdfSplitLayout.requestLayout()
            }
            if(e.actionMasked==MotionEvent.ACTION_UP || e.actionMasked==MotionEvent.ACTION_CANCEL){whiteboard.finishResize();pdf.endResize();changed();performClick()}
            return true
        }
        override fun performClick():Boolean{super.performClick();return true}
    }.apply{contentDescription=context.tr("Drag to resize PDF and board","برای تغییر اندازهٔ PDF و تخته بکشید")}
    init {layoutDirection=LAYOUT_DIRECTION_LTR;addView(whiteboard);addView(pdf);addView(divider)}
    override fun onMeasure(wSpec:Int,hSpec:Int){
        val w=MeasureSpec.getSize(wSpec);val h=MeasureSpec.getSize(hSpec);setMeasuredDimension(w,h)
        val d=context.dp(16);val pw=if(state.fullscreen)w else ((w-d)*state.ratio).toInt()
        pdf.measure(MeasureSpec.makeMeasureSpec(pw,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY))
        whiteboard.measure(MeasureSpec.makeMeasureSpec(if(state.fullscreen)0 else w-d-pw,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY))
        divider.measure(MeasureSpec.makeMeasureSpec(d,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY))
    }
    override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int){
        val w=r-l;val h=b-t;val d=context.dp(16);val pw=pdf.measuredWidth
        whiteboard.visibility=if(state.fullscreen)GONE else VISIBLE;divider.visibility=if(state.fullscreen)GONE else VISIBLE
        if(state.fullscreen){pdf.layout(0,0,w,h);return}
        if(state.onRight){whiteboard.layout(0,0,w-pw-d,h);divider.layout(w-pw-d,0,w-pw,h);pdf.layout(w-pw,0,w,h)}
        else{pdf.layout(0,0,pw,h);divider.layout(pw,0,pw+d,h);whiteboard.layout(pw+d,0,w,h)}
    }
}
