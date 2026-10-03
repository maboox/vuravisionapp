package com.vuravision.classroom
import android.content.Context
import android.graphics.*
import android.view.*
import kotlin.math.*
class PdfRegionSelector(context:Context,private val bitmap:Bitmap):View(context){
    var region=RectF(.1f,.1f,.9f,.9f);private set
    private val imageRect=RectF()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var sx=0f;private var sy=0f
    init{minimumHeight=context.dp(300);contentDescription=context.tr("Select PDF region","انتخاب ناحیهٔ PDF")}
    override fun onMeasure(w:Int,h:Int){setMeasuredDimension(MeasureSpec.getSize(w),context.dp(360))}
    override fun onDraw(c:Canvas){
        val scale=min(width.toFloat()/bitmap.width,height.toFloat()/bitmap.height)
        imageRect.set((width-bitmap.width*scale)/2,(height-bitmap.height*scale)/2,(width+bitmap.width*scale)/2,(height+bitmap.height*scale)/2)
        c.drawColor(0xffe3e7ee.toInt());paint.style=Paint.Style.FILL;paint.isFilterBitmap=true;c.drawBitmap(bitmap,null,imageRect,paint)
        paint.color=TEAL;paint.style=Paint.Style.STROKE;paint.strokeWidth=context.dp(3).toFloat()
        c.drawRect(imageRect.left+region.left*imageRect.width(),imageRect.top+region.top*imageRect.height(),imageRect.left+region.right*imageRect.width(),imageRect.top+region.bottom*imageRect.height(),paint)
    }
    override fun onTouchEvent(e:MotionEvent):Boolean{
        val x=((e.x-imageRect.left)/imageRect.width()).coerceIn(0f,1f);val y=((e.y-imageRect.top)/imageRect.height()).coerceIn(0f,1f)
        if(e.actionMasked==MotionEvent.ACTION_DOWN){sx=x;sy=y}
        if(e.actionMasked==MotionEvent.ACTION_MOVE || e.actionMasked==MotionEvent.ACTION_UP){region=RectF(min(sx,x),min(sy,y),max(sx,x).coerceAtLeast(min(sx,x)+.001f),max(sy,y).coerceAtLeast(min(sy,y)+.001f));invalidate()}
        if(e.actionMasked==MotionEvent.ACTION_UP)performClick()
        return true
    }
    override fun performClick():Boolean{super.performClick();return true}
}
