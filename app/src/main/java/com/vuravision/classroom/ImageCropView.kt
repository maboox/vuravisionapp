package com.vuravision.classroom

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

/** Eight crop handles and a movable rectangle, stored in normalized image coordinates. */
class ImageCropView(context: Context, private val bitmap: Bitmap) : View(context) {
    val region = RectF(0f, 0f, 1f, 1f)
    private val image = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var handle = -1
    private var pointer = -1
    private var start = PointF()
    private val before = RectF()
    init { minimumHeight=context.dp(260); contentDescription=context.tr("Drag crop edges or corners; drag inside to move", "گیره‌های کناری یا گوشه‌ها را بکشید؛ داخل کادر برای جابه‌جایی") }
    private fun cropRect()=RectF(image.left+region.left*image.width(),image.top+region.top*image.height(),image.left+region.right*image.width(),image.top+region.bottom*image.height())
    private fun handles(r:RectF)=listOf(r.left to r.top,r.centerX() to r.top,r.right to r.top,r.right to r.centerY(),r.right to r.bottom,r.centerX() to r.bottom,r.left to r.bottom,r.left to r.centerY())
    override fun onDraw(canvas:Canvas) {
        val pad=context.dp(24).toFloat()
        val scale=min((width-2*pad).coerceAtLeast(1f)/bitmap.width,(height-2*pad).coerceAtLeast(1f)/bitmap.height)
        val w=bitmap.width*scale; val h=bitmap.height*scale
        image.set((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2)
        canvas.drawColor(PAPER);paint.style=Paint.Style.FILL;paint.color=Color.WHITE
        canvas.drawBitmap(bitmap,null,image,paint)
        val r=cropRect();paint.color=0xaa111827.toInt()
        canvas.drawRect(image.left,image.top,image.right,r.top,paint)
        canvas.drawRect(image.left,r.bottom,image.right,image.bottom,paint)
        canvas.drawRect(image.left,r.top,r.left,r.bottom,paint)
        canvas.drawRect(r.right,r.top,image.right,r.bottom,paint)
        paint.color=Color.WHITE;paint.strokeWidth=context.dp(2).toFloat();paint.style=Paint.Style.STROKE
        canvas.drawRect(r,paint);paint.color=0x99ffffff.toInt();paint.strokeWidth=context.dp(1).toFloat()
        for(n in 1..2){canvas.drawLine(r.left+r.width()*n/3,r.top,r.left+r.width()*n/3,r.bottom,paint);canvas.drawLine(r.left,r.top+r.height()*n/3,r.right,r.top+r.height()*n/3,paint)}
        handles(r).forEach{(x,y)->paint.style=Paint.Style.FILL;paint.color=TEAL;canvas.drawCircle(x,y,context.dp(7).toFloat(),paint);paint.style=Paint.Style.STROKE;paint.color=Color.WHITE;paint.strokeWidth=context.dp(2).toFloat();canvas.drawCircle(x,y,context.dp(7).toFloat(),paint)}
    }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        if(image.width()<=0 || image.height()<=0)return true
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN->{
                pointer=event.getPointerId(0);start=PointF(event.x,event.y);before.set(region)
                val r=cropRect();val tolerance=context.dp(28).toFloat()
                handle=handles(r).mapIndexed{i,p->i to hypot(event.x-p.first,event.y-p.second)}.minByOrNull{it.second}?.takeIf{it.second<=tolerance}?.first?:if(r.contains(event.x,event.y))8 else -1
                if(handle>=0)parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE->{
                val index=event.findPointerIndex(pointer);if(index<0 || handle<0)return true
                val dx=(event.getX(index)-start.x)/image.width();val dy=(event.getY(index)-start.y)/image.height()
                val minW=min(before.width(),max(1f/bitmap.width,min(.1f,context.dp(32)/image.width())))
                val minH=min(before.height(),max(1f/bitmap.height,min(.1f,context.dp(32)/image.height())))
                region.set(before)
                if(handle==8){region.offset(dx.coerceIn(-before.left,1-before.right),dy.coerceIn(-before.top,1-before.bottom))}
                else {
                    if(handle in listOf(0,6,7))region.left=(before.left+dx).coerceIn(0f,before.right-minW)
                    if(handle in listOf(2,3,4))region.right=(before.right+dx).coerceIn(before.left+minW,1f)
                    if(handle in listOf(0,1,2))region.top=(before.top+dy).coerceIn(0f,before.bottom-minH)
                    if(handle in listOf(4,5,6))region.bottom=(before.bottom+dy).coerceIn(before.top+minH,1f)
                }
                invalidate()
            }
            MotionEvent.ACTION_CANCEL->{region.set(before);handle=-1;pointer=-1;parent?.requestDisallowInterceptTouchEvent(false);invalidate()}
            MotionEvent.ACTION_UP->{handle=-1;pointer=-1;parent?.requestDisallowInterceptTouchEvent(false);performClick()}
        }
        return true
    }
    fun reset(){region.set(0f,0f,1f,1f);invalidate()}
    override fun performClick():Boolean {super.performClick();return true}
}
