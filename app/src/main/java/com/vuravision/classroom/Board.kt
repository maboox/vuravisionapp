package com.vuravision.classroom

import android.content.Context
import android.graphics.*
import android.view.*
import kotlin.math.*

class Board(context: Context, val store: Store, val renderer: Renderer) : View(context) {
    var sessionCanEdit=true
    var canEditItem:(Item)->Boolean={true}
    var onInteraction:(MotionEvent)->Unit={}
    var onViewportChanged:()->Unit={}
    /** View-local area left clear by the surrounding application controls. */
    var fitViewport:(()->RectF)?=null
    var roomOverlay:RoomPresenceOverlay?=null
    var selectionColor=0xffe46d38.toInt()
    private var remotePreviews:Set<String> = emptySet()
    val hasActiveInteraction get()=isDrawing||changed||navigationActive||transforming.isNotEmpty()
    fun reconcilePage(){activePane=activePane.coerceIn(store.page.visiblePaneIndices);selected.retainAll(store.page.items.map{it.id}.toSet());sceneChanged()}
    fun applySharedPane(index:Int){activePane=index.coerceIn(store.page.visiblePaneIndices);clearSelection();sceneChanged()}
    fun paneScreenBounds(index:Int)=paneRect(index.coerceIn(store.page.visiblePaneIndices))
    fun screenRect(bounds:RectF,index:Int=activePane):RectF {
        val n=index.coerceIn(store.page.visiblePaneIndices);val r=paneRect(n);val state=store.page.panes[n]
        return RectF(r.left+density*(state.tx+bounds.left*state.zoom),r.top+density*(state.ty+bounds.top*state.zoom),r.left+density*(state.tx+bounds.right*state.zoom),r.top+density*(state.ty+bounds.bottom*state.zoom))
    }
    fun selectionScreenBounds():RectF?=chosen().takeIf{it.isNotEmpty()}?.let{screenRect(contentBounds(it))}
    fun viewportWorld():RoomRect {val r=paneRect(activePane);val a=world(r.left,r.top);val b=world(r.right,r.bottom);return RoomRect(a.x,a.y,b.x,b.y)}
    fun roomPreviews():List<Item> =(live.values.toList()+listOfNotNull(geometry.preview())+store.page.items.filter{it.id in transforming}).take(8).map {v->
        v.deepCopy().apply{if(points.size>2000){val source=points;points=(0..1999).map{source[(it.toLong()*(source.size-1)/1999).toInt()].copy()}.toMutableList()}}
    }
    var interactiveResize=false
    var fixedPageWidth:Float?=null
    var fixedPageHeight:Float?=null
    var pdfBaseId:String?=null
        set(value){field=value;renderer.retainPdfBase(value)}
    var erasePdfContent=false
    var pdfPalmErase=false
    var onActivate:()->Unit={}
    internal var transforming:Set<String> = emptySet()
    val isTransforming get()=transforming.isNotEmpty()
    fun copyToolsFrom(b:Board){
        tool=b.tool;shape=b.shape;touchMode=b.touchMode;selectionMode=b.selectionMode;profile=b.profile.copy()
        penColor=b.penColor;highlightColor=b.highlightColor;penWidth=b.penWidth;highlightWidth=b.highlightWidth
        penStyle=b.penStyle;penOpacity=b.penOpacity;dashLength=b.dashLength;dashGap=b.dashGap
        eraserMode=b.eraserMode;eraserRadius=b.eraserRadius;eraseObjects=b.eraseObjects
        erasePdfContent=b.erasePdfContent;pdfPalmErase=b.pdfPalmErase
        holdRecognitionEnabled=b.holdRecognitionEnabled;holdDelayMillis=b.holdDelayMillis
        holdTolerance=b.holdTolerance;guideSnapEnabled=b.guideSnapEnabled;smartMode=b.smartMode;fillColor=b.fillColor;fillAlpha=b.fillAlpha;gestures.undoEnabled=b.gestures.undoEnabled;gestures.pieEnabled=b.gestures.pieEnabled;gestures.pieHold=b.gestures.pieHold
    }
    override fun onDetachedFromWindow(){
        smoothers.clear();removeCallbacks(smoothFrame);measurements.clear();gestures.reset();super.onDetachedFromWindow()
    }
    fun releaseBacking(){backing?.recycle();backing=null;backingCanvas=null;backingDirty=true}
    internal fun fitFixed(){fixedPageWidth?.let{if(width>0 && height>0)zoom=min(width/density/it,height/density/(fixedPageHeight?:1f));tx=0f;ty=0f}}
    val gestures=BoardGestures(this)
    internal val geometry=GeometryInteraction(this)
    val measurements=MeasurementOverlay(this)
    fun showMeasurements(o:Item){measurements.show(o)}
    var onEducationalTap:(Item,PointF)->Unit={_,_->}
    var fillColor:Int?=INK_TEAL
    var fillAlpha=255
    var tool = "pen"
    var selectionMode="free"
    var touchMode=false
    var activePane=0
        private set
    internal val pointerPanes=mutableMapOf<Int,Int>()
    fun split(count:Int){
        if(hasActiveInteraction)return
        require(count in 1..4)
        store.editMetadata{store.page.switchPanels(count)}
        activePane=0;clearSelection();sceneChanged()
    }
    internal fun paneRect(index:Int):RectF{
        val n=store.page.visiblePaneCount
        if(n==1)return RectF(0f,0f,width.toFloat(),height.toFloat())
        return RectF(width*index.toFloat()/n,0f,width*(index+1f)/n,height.toFloat())
    }
    internal val broadPointers=mutableSetOf<Int>()
    internal var touchSpan=0f
    internal var touchFocus:PointF?=null
    internal val lasso=mutableListOf<PointF>()
    var shape = "rectangle"
    var eraserMode="stroke"
    var eraserRadius=18f
    var eraseObjects=true
    var holdRecognitionEnabled=true
    var holdDelayMillis=1000L
    var holdTolerance=4f
    var guideSnapEnabled=true
    var smartMode="text"
    var onSmart:(List<Item>)->Unit={}
    var onObjectActions:()->Unit={}
    /** Laser strokes that are fading out: drawn on top, never stored. */
    internal val fading=mutableListOf<Pair<Item,Long>>()
    val laserStrokes get()=fading.size
    val laserPoints get()=fading.sumOf{it.first.points.size}
    val livePoints get()=live.values.sumOf{it.points.size}
    var onMindAdd:(Item,Boolean)->Unit={_,_->}
    internal var tappedSelected=false
    internal val eraseLast=mutableMapOf<Int,PointF>()
    var penColor=INK_NAVY
    var highlightColor=0xffffcf40.toInt()
    var penWidth=4f
    var highlightWidth=6f
    var penStyle="round"
    var penOpacity=255
    var dashLength=12f
    var dashGap=8f
    var inkColor:Int
        get()=if(store.page.visiblePaneCount>1)pane.color else if(tool=="highlight")highlightColor else penColor
        set(v) { if(store.page.visiblePaneCount>1)store.editMetadata{pane.color=v} else if(tool=="highlight")highlightColor=v else penColor=v }
    var inkWidth:Float
        get()=if(store.page.visiblePaneCount>1)pane.penWidth else if(tool=="highlight")highlightWidth else penWidth
        set(v) { if(store.page.visiblePaneCount>1)store.editMetadata{pane.penWidth=v.coerceIn(.1f,80f)} else if(tool=="highlight")highlightWidth=v else penWidth=v }
    fun guideInkColor(index:Int=activePane):Int =
        if(store.page.visiblePaneCount>1)store.page.panes[index.coerceIn(store.page.visiblePaneIndices)].color else inkColor
    fun guideInkWidth(index:Int=activePane):Float =
        if(store.page.visiblePaneCount>1)store.page.panes[index.coerceIn(store.page.visiblePaneIndices)].penWidth else inkWidth
    val isDrawing get()=live.isNotEmpty() || geometry.active
    var isCommitting=false
        private set
    private var backing:Bitmap?=null
    private val bitmapPaint=Paint(Paint.FILTER_BITMAP_FLAG)
    private var backingCanvas:Canvas?=null
    internal var backingDirty=true
    internal var navigationActive=false
    private var cachedZoom=1f;private var cachedTx=0f;private var cachedTy=0f
    private var lastFullRender=0L
    internal var dirtyRegion:RectF?=null
    private var cachedPage:Page?=null
    private var cachedCount=0
    var cacheRebuilds=0
        private set
    fun sceneChanged() { backingDirty=true;invalidate() }
    /** Recover objects without selecting them first, while keeping the PDF source protected. */
    fun unlockAll():Int {
        if(isDrawing)return 0
        val objects=store.page.items.filter{it.locked && it.id!=pdfBaseId}
        val layers=store.page.layers.filter{it.locked}
        val count=objects.size+layers.size
        if(count>0){store.editMetadata{objects.forEach{it.locked=false};layers.forEach{it.locked=false}};clearSelection();sceneChanged()}
        return count
    }
    internal fun commitToBacking(o:Item){
        val page=store.page;val layer=page.layers.lastOrNull{it.visible&&it.opacity>0}
        val bitmap=backing
        if(bitmap!=null && !backingDirty && dirtyRegion==null && cachedPage===page && cachedCount==page.items.size-1 && layer?.id==o.layerId && layer.opacity==1f){
            val c=backingCanvas?:return;c.save();transformCanvas(c,o.pane);renderer.draw(c,o);c.restore();cachedCount=page.items.size
        }else backingDirty=true
    }

    var profile = TouchProfile()
    var onSelection: () -> Unit = {}
    val selected = linkedSetOf<String>()
    internal val pane get()=store.page.panes[activePane.coerceIn(store.page.visiblePaneIndices)]
    internal var zoom:Float get()=pane.zoom;set(v){pane.zoom=v}
    internal var tx:Float get()=pane.tx;set(v){pane.tx=v}
    internal var ty:Float get()=pane.ty;set(v){pane.ty=v}
    internal val density = resources.displayMetrics.density
    internal val live = mutableMapOf<Int, Item>()
    internal val smoothers=mutableMapOf<Int,StrokeSmoother>()
    internal val smoothFrame=object:Runnable{override fun run(){
        val now=android.os.SystemClock.uptimeMillis()
        var changed=false
        smoothers.forEach{(id,filter)->filter.advance(now)?.let{live[id]?.points?.add(it);changed=true}}
        if(changed)postInvalidateOnAnimation()
        if(smoothers.values.any{it.pending})postOnAnimation(this)
    }}
    internal val guides=mutableMapOf<Int,Item>()
    internal val guideStopped=mutableSetOf<Int>()
    internal val guideLengths=mutableMapOf<Int,Float>()
    internal val guideEdges=mutableMapOf<Int,Int>()
    internal val guideEdgeCandidates=mutableMapOf<Int,List<Int>>()
    internal val guideStart=mutableMapOf<Int,Point>()
    internal val holdCallbacks=mutableMapOf<Int,Runnable>()
    internal val holdAt=mutableMapOf<Int,PointF>()
    internal val heldStart=mutableMapOf<Int,PointF>()
    internal val heldOriginal=mutableMapOf<Int,Item>()
    internal val heldEnd=mutableMapOf<Int,PointF>()
    internal val mindStart=mutableMapOf<Int,PointF>()
    internal val actions = mutableMapOf<Int, String>()
    internal var changed = false
    internal var primary = -1
    internal var anchor = PointF()
    internal var lastFocus = PointF()
    internal var span = 0f
    internal var mode = ""
    internal var original = listOf<Item>()
    internal var box: RectF? = null
    internal val p = Paint(Paint.ANTI_ALIAS_FLAG)

    fun measurementUnit()=if(context.getSharedPreferences("vura",0).getFloat("pixelsPerCm",0f)>0)"cm"else"u"
    init {
        isFocusable = true
        contentDescription = context.s("board")
        setBackgroundColor(Color.WHITE)
    }

    fun chosen() = if(selected.isEmpty()) emptyList() else store.page.visibleItems().filter { it.pane==activePane && it.id in selected && canEditItem(it) && store.page.editable(it) && !it.locked }

    fun clearSelection() {
        selected.clear()
        onSelection()
        invalidate()
    }

    fun abortPendingGesture(){
        holdCallbacks.keys.toList().forEach(::cancelHold)
        smoothers.clear();removeCallbacks(smoothFrame);live.clear();guides.clear();guideStopped.clear();guideLengths.clear();guideEdges.clear();guideEdgeCandidates.clear();guideStart.clear();heldStart.clear();heldOriginal.clear();heldEnd.clear();mindStart.clear();lasso.clear();box=null
        geometry.cancel()
        if(changed){store.cancelCheckpoint();changed=false}
        actions.clear();eraseLast.clear();broadPointers.clear();transforming=emptySet();pointerPanes.clear();mode="";navigationActive=false;touchFocus=null;tappedSelected=false
        parent?.requestDisallowInterceptTouchEvent(false);sceneChanged()
    }
    fun undo(){gestures.reset();abortPendingGesture();store.undo();reconcilePage();clearSelection()}
    fun redo(){gestures.reset();abortPendingGesture();store.redo();reconcilePage();clearSelection()}
    var keepToolAfterInsert = false
    fun insert(o: Item) {
        if(!sessionCanEdit||!store.editAllowed()||!store.page.canDraw())return
        o.layerId=store.page.activeLayerId
        o.pane=activePane
        if(o.kind in listOf("text","sticky"))TextLayout.fit(o)
        val c = center()
        o.x = c.x - o.w / 2
        o.y = c.y - o.h / 2
        store.editMetadata { store.page.items.add(o) }
        selected.clear()
        // Item 50, tool lock: with "keep my tool" on, the pen stays in hand after adding text or a note.
        if (keepToolAfterInsert && tool in listOf("pen", "highlight", "shape")) {
            onSelection(); invalidate(); return
        }
        selected.add(o.id)
        tool = "select"
        onSelection()
        invalidate()
    }

    fun edit(action: (Item) -> Unit) {
        store.editMetadata { chosen().filter { !it.locked }.forEach(action) }
        invalidate()
    }

    fun delete() {
        val ids=MindMap.group(store.page,chosen()).filter{canEditItem(it)&&it.pane==activePane}.mapTo(mutableSetOf()){it.id}
        store.editMetadata { store.page.items.removeAll { it.id in ids };store.page.items.filter{it.parentNode in ids}.forEach{it.parentNode=""} }
        clearSelection()
    }

    internal fun checkpoint() {
        if (!changed) {
            store.checkpoint()
            changed = true
        }
    }

    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
        releaseBacking()
        if(!interactiveResize && w>0 && h>0){
            val budget=(Runtime.getRuntime().maxMemory()/12).coerceIn(4L*1024*1024,32L*1024*1024)
            val pixels=minOf(budget/4,if(fixedPageWidth==null)8_000_000L else 2_000_000L)
            val factor=minOf(1.0,sqrt(pixels.toDouble()/(w.toDouble()*h))).toFloat()
            try{
                val bitmap=Bitmap.createBitmap((w*factor).toInt().coerceAtLeast(1),(h*factor).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
                backing=bitmap;backingCanvas=Canvas(bitmap).apply{scale(bitmap.width.toFloat()/w,bitmap.height.toFloat()/h)}
            }catch(_:OutOfMemoryError){backing=null;backingCanvas=null}
        }
        fitFixed();backingDirty=true
    }

    fun finishResize(){interactiveResize=false;onSizeChanged(width,height,width,height);invalidate()}
    private fun transformCanvas(c:Canvas,index:Int=activePane) {val n=index.coerceIn(store.page.visiblePaneIndices);val r=paneRect(n);val p=store.page.panes[n];c.clipRect(r);c.translate(r.left,r.top);c.scale(density,density);c.translate(p.tx,p.ty);c.scale(p.zoom,p.zoom) }
    override fun onDraw(c:Canvas) {
        val previewIds=roomOverlay?.previewIds().orEmpty()
        if(previewIds!=remotePreviews){remotePreviews=previewIds;backingDirty=true}
        val page=store.page
        val navigating=navigationActive || mode=="navigate"
        val age=android.os.SystemClock.uptimeMillis()-lastFullRender
        if(roomOverlay==null && navigating && page.visiblePaneCount==1 && live.isEmpty() && backingDirty && cachedPage===page && backing!=null && age<80){
            c.drawColor(page.panes[0].background)
            val ratio=zoom/cachedZoom
            c.save();c.translate(density*(tx-cachedTx*ratio),density*(ty-cachedTy*ratio));c.scale(ratio,ratio);c.drawBitmap(backing!!,null,RectF(0f,0f,width.toFloat(),height.toFloat()),bitmapPaint);c.restore()
            postInvalidateDelayed((80-age).coerceAtLeast(1));return
        }
        if(backing==null){
            page.visiblePaneIndices.forEach{index->c.save();transformCanvas(c,index)
                val clip=RectF(c.clipBounds);renderer.background(c,page,clip,page.panes[index].background,index)
                renderer.scene(c,page,region=clip,pane=index,exclude=transforming+remotePreviews);c.restore()}
        }
        backing?.let { bitmap ->
            val target=backingCanvas?:Canvas(bitmap);target.save()
            val page=store.page
            val full=backingDirty || cachedPage!==page || (cachedCount!=page.items.size && dirtyRegion==null)
            if(full) {
                page.visiblePaneIndices.forEach{index->
                    target.save();transformCanvas(target,index)
                    val screen=paneRect(index);val l=world(screen.left,screen.top,index);val r=world(screen.right,screen.bottom,index)
                    renderer.background(target,page,RectF(l.x,l.y,r.x,r.y),page.panes[index].background,index)
                    renderer.scene(target,page,region=RectF(l.x,l.y,r.x,r.y),pane=index,exclude=transforming+remotePreviews);target.restore()
                }
                cachedPage=page;backingDirty=false;cacheRebuilds++;lastFullRender=android.os.SystemClock.uptimeMillis();cachedZoom=zoom;cachedTx=tx;cachedTy=ty
            } else dirtyRegion?.let { region ->
                target.save();transformCanvas(target);target.clipRect(region)
                renderer.background(target,page,region,pane.background,activePane)
                renderer.scene(target,page,region=region,pane=activePane,exclude=transforming+remotePreviews)
                target.restore()
            }
            dirtyRegion=null
            cachedCount=page.items.size;target.restore();c.drawBitmap(bitmap,null,RectF(0f,0f,width.toFloat(),height.toFloat()),bitmapPaint)
        }
        store.page.items.filter{it.id in transforming}.forEach{o->
            c.save();transformCanvas(c,o.pane)
            val alpha=store.page.layer(o).opacity
            val save=if(alpha<1f)c.saveLayerAlpha(null,(alpha*255).toInt())else c.save()
            renderer.draw(c,o);c.restoreToCount(save);c.restore()
        }
        live.values.forEach { o ->
            c.save();transformCanvas(c,o.pane)
            val alpha=store.page.layers.firstOrNull{it.id==o.layerId}?.opacity?:1f
            val save=if(alpha<1f)c.saveLayerAlpha(null,(alpha*255).toInt())else c.save();renderer.draw(c,o);c.restoreToCount(save)
            c.restore()
        }
        if(fading.isNotEmpty()){
            val now=android.os.SystemClock.uptimeMillis()
            fading.removeAll{(o,at)->(now-at>LASER_GONE).also{gone->if(gone)renderer.forgetInk(o.id)}}
            // Fully visible for a second, then fades out over the next second.
            fading.forEach{(o,at)->val a=(1f-(now-at-LASER_HOLD).coerceAtLeast(0)/(LASER_GONE-LASER_HOLD).toFloat()).coerceIn(0f,1f)
                c.save();transformCanvas(c,o.pane);val layer=c.saveLayerAlpha(null,(a*255).toInt());renderer.draw(c,o);c.restoreToCount(layer);c.restore()}
            if(fading.isNotEmpty())postInvalidateOnAnimation()
        }
        c.save();transformCanvas(c)
        p.color = selectionColor
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f / zoom
        val items = chosen()
        if (items.isNotEmpty()) {
            val b = contentBounds(items)
            c.drawRect(b, p)
            p.style = Paint.Style.FILL
            if(items.singleOrNull()?.shape !in GeometryTools.keys){
                c.drawCircle(b.right, b.bottom, 10f / zoom, p)
                c.drawCircle(b.centerX(), b.top - 28 / zoom, 10f / zoom, p)
            }

        }
        if(selectionMode=="box")box?.let {
            p.style = Paint.Style.STROKE;p.pathEffect=DashPathEffect(floatArrayOf(6f/zoom,4f/zoom),0f)
            c.drawRect(it,p);p.pathEffect=null
        }
        if(selectionMode=="free" && lasso.size>1){val path=Path();path.moveTo(lasso[0].x,lasso[0].y);lasso.drop(1).forEach{path.lineTo(it.x,it.y)};p.style=Paint.Style.STROKE;c.drawPath(path,p)}
        geometry.draw(c,zoom)
        drawGuideMeasurements(c)
        measurements.draw(c,zoom)
        c.restore()
        if(store.page.visiblePaneCount>1){
            p.style=Paint.Style.STROKE;p.strokeWidth=density
            store.page.visiblePaneIndices.forEach{index->p.color=if(index==activePane)0xff9990b5.toInt()else 0xffdcd9e5.toInt();val border=paneRect(index);border.inset(density/2,density/2);c.drawRect(border,p)}
        }
        roomOverlay?.draw(c)
    }

    override fun onTouchEvent(e:MotionEvent):Boolean {
        val result=handleTouch(e);onInteraction(e);onViewportChanged();return result
    }
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
