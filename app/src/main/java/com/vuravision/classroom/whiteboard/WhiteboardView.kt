package com.vuravision.classroom.whiteboard

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.vuravision.classroom.document.*
import java.io.File
import kotlin.math.max
import kotlin.math.min

class WhiteboardView(context: Context): View(context) {
    enum class Tool { PEN, ERASER, PAN, SELECT }
    var tool: Tool = Tool.PEN
    var document: BoardDocument = BoardDocument()
        set(value) { field=value; commands=CommandStack(field); bitmapCache.clear(); invalidate() }
    private var commands = CommandStack(document)
    private var currentStroke: CanvasObject.InkStroke? = null
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND; style=Paint.Style.STROKE }
    private val bitmapCache=mutableMapOf<String,Bitmap>()
    private var scale=1f; private var tx=0f; private var ty=0f
    private var lastX=0f; private var lastY=0f
    private var selectedId:String?=null
    private var lastTap=0L

    private val scaleDetector=ScaleGestureDetector(context, object: ScaleGestureDetector.SimpleOnScaleGestureListener(){
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val old=scale; scale=(scale*detector.scaleFactor).coerceIn(0.2f,5f)
            val factor=scale/old; tx=detector.focusX-(detector.focusX-tx)*factor; ty=detector.focusY-(detector.focusY-ty)*factor
            invalidate(); return true
        }
    })

    fun undo(){ if(commands.undo()) invalidate() }
    fun redo(){ if(commands.redo()) invalidate() }
    fun clearPage(){ val all=document.objects.toList(); all.reversed().forEach{commands.execute(RemoveObjectCommand(it))}; invalidate() }
    fun addObject(obj: CanvasObject){ commands.execute(AddObjectCommand(obj)); invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas); canvas.drawColor(Color.WHITE)
        canvas.save(); canvas.translate(tx,ty); canvas.scale(scale,scale)
        drawGrid(canvas)
        document.objects.forEach { o ->
            when(o){
                is CanvasObject.InkStroke -> drawStroke(canvas,o)
                is CanvasObject.ImageObject -> drawBitmapObject(canvas,o.id,o.localPath,o.bounds,o.rotation)
                is CanvasObject.PdfObject -> drawBitmapObject(canvas,o.id+"_${o.pageIndex}",pdfPreviewPath(o),o.bounds,0f)
            }
            if(o.id==selectedId) { paint.style=Paint.Style.STROKE; paint.strokeWidth=2f/scale; paint.color=Color.rgb(255,138,36); paint.alpha=255; canvas.drawRect(o.bounds,paint) }
        }
        currentStroke?.let { drawStroke(canvas,it) }
        canvas.restore()
    }

    private fun drawGrid(canvas:Canvas){
        paint.color=Color.rgb(238,240,243); paint.strokeWidth=1f/scale; paint.alpha=255; paint.style=Paint.Style.STROKE
        val step=80f; val l=(-tx/scale)-step; val t=(-ty/scale)-step; val r=l+width/scale+2*step; val b=t+height/scale+2*step
        var x=(l/step).toInt()*step; while(x<r){canvas.drawLine(x,t,x,b,paint); x+=step}
        var y=(t/step).toInt()*step; while(y<b){canvas.drawLine(l,y,r,y,paint); y+=step}
    }

    private fun drawStroke(canvas:Canvas,s:CanvasObject.InkStroke){
        if(s.points.size<2)return; val p=Path(); p.moveTo(s.points[0].x,s.points[0].y)
        for(i in 1 until s.points.size){ val a=s.points[i-1]; val b=s.points[i]; p.quadTo(a.x,a.y,(a.x+b.x)/2f,(a.y+b.y)/2f) }
        paint.color=s.color; paint.strokeWidth=s.width; paint.alpha=s.alpha; paint.style=Paint.Style.STROKE; canvas.drawPath(p,paint)
    }

    private fun drawBitmapObject(canvas:Canvas,key:String,path:String,b:RectF,rotation:Float){
        val bm=bitmapCache[key] ?: runCatching { BitmapFactory.decodeFile(path) }.getOrNull()?.also { bitmapCache[key]=it }
        if(bm!=null){ canvas.save(); canvas.rotate(rotation,b.centerX(),b.centerY()); canvas.drawBitmap(bm,null,b,Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)); canvas.restore() }
        else { paint.style=Paint.Style.FILL; paint.color=Color.LTGRAY; canvas.drawRect(b,paint); paint.color=Color.DKGRAY; paint.textSize=24f; canvas.drawText("Media",b.left+20,b.centerY(),paint) }
    }

    private fun pdfPreviewPath(o:CanvasObject.PdfObject):String = File(context.cacheDir,"pdf_${o.id}_${o.pageIndex}.png").absolutePath

    override fun onTouchEvent(e:MotionEvent):Boolean{
        scaleDetector.onTouchEvent(e)
        if(e.pointerCount>1 && tool!=Tool.PEN){ toolPan(e); return true }
        val wx=(e.x-tx)/scale; val wy=(e.y-ty)/scale
        when(tool){
            Tool.PEN -> handlePen(e,wx,wy)
            Tool.ERASER -> handleEraser(e,wx,wy)
            Tool.PAN -> toolPan(e)
            Tool.SELECT -> handleSelect(e,wx,wy)
        }
        return true
    }

    private fun handlePen(e:MotionEvent,x:Float,y:Float){
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN -> { val s=CanvasObject.InkStroke(width=5f/scale); s.points+=InkPoint(x,y,e.pressure); s.bounds=RectF(x,y,x,y); currentStroke=s; invalidate() }
            MotionEvent.ACTION_MOVE -> { val s=currentStroke?:return; for(i in 0 until e.historySize){ addPoint(s,(e.getHistoricalX(i)-tx)/scale,(e.getHistoricalY(i)-ty)/scale,e.getHistoricalPressure(i)) }; addPoint(s,x,y,e.pressure); invalidate() }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { currentStroke?.let { if(it.points.size>1) commands.execute(AddObjectCommand(it)) }; currentStroke=null; invalidate() }
        }
        currentStroke?.let { if(!document.objects.contains(it)) { // draw transient without mutating document
            invalidate()
        }}
    }
    private fun addPoint(s:CanvasObject.InkStroke,x:Float,y:Float,p:Float){ s.points+=InkPoint(x,y,p); if(s.points.size==1)s.bounds.set(x,y,x,y) else s.bounds.union(x,y); val pad=s.width*2; s.bounds.inset(-pad,-pad) }

    private fun handleEraser(e:MotionEvent,x:Float,y:Float){ if(e.actionMasked==MotionEvent.ACTION_DOWN || e.actionMasked==MotionEvent.ACTION_MOVE){ val hit=document.objects.lastOrNull{it.bounds.contains(x,y)}; if(hit!=null){commands.execute(RemoveObjectCommand(hit));invalidate()} } }

    private fun handleSelect(e:MotionEvent,x:Float,y:Float){ if(e.actionMasked==MotionEvent.ACTION_DOWN){ selectedId=document.objects.lastOrNull{it.bounds.contains(x,y)}?.id; lastX=x; lastY=y; invalidate() }
        else if(e.actionMasked==MotionEvent.ACTION_MOVE){ val o=document.objects.firstOrNull{it.id==selectedId}?:return; val dx=x-lastX; val dy=y-lastY; o.bounds.offset(dx,dy); if(o is CanvasObject.InkStroke) o.points.forEachIndexed{idx,p->o.points[idx]=p.copy(x=p.x+dx,y=p.y+dy)}; lastX=x; lastY=y; invalidate() }
    }

    private fun toolPan(e:MotionEvent){ when(e.actionMasked){ MotionEvent.ACTION_DOWN->{lastX=e.x;lastY=e.y}; MotionEvent.ACTION_MOVE->{tx+=e.x-lastX;ty+=e.y-lastY;lastX=e.x;lastY=e.y;invalidate()} } }

    override fun dispatchDraw(canvas: Canvas) { super.dispatchDraw(canvas) }

    fun drawTransient(canvas:Canvas){ currentStroke?.let{drawStroke(canvas,it)} }
}
