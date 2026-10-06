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
    var roomOverlay:RoomPresenceOverlay?=null
    var selectionColor=0xffe46d38.toInt()
    private var remotePreviews:Set<String> = emptySet()
    val hasActiveInteraction get()=isDrawing||changed||navigationActive||transforming.isNotEmpty()
    fun reconcilePage(){activePane=activePane.coerceIn(store.page.panes.indices);selected.retainAll(store.page.items.map{it.id}.toSet());sceneChanged()}
    fun applySharedPane(index:Int){activePane=index.coerceIn(store.page.panes.indices);clearSelection();sceneChanged()}
    fun paneScreenBounds(index:Int)=paneRect(index.coerceIn(store.page.panes.indices))
    fun screenRect(bounds:RectF,index:Int=activePane):RectF {
        val n=index.coerceIn(store.page.panes.indices);val r=paneRect(n);val state=store.page.panes[n]
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
    private var transforming:Set<String> = emptySet()
    fun copyToolsFrom(b:Board){
        tool=b.tool;shape=b.shape;touchMode=b.touchMode;selectionMode=b.selectionMode;profile=b.profile.copy()
        penColor=b.penColor;highlightColor=b.highlightColor;penWidth=b.penWidth;highlightWidth=b.highlightWidth
        penStyle=b.penStyle;penOpacity=b.penOpacity;dashLength=b.dashLength;dashGap=b.dashGap
        eraserMode=b.eraserMode;eraserRadius=b.eraserRadius;eraseObjects=b.eraseObjects
        erasePdfContent=b.erasePdfContent;pdfPalmErase=b.pdfPalmErase
        holdRecognitionEnabled=b.holdRecognitionEnabled;holdDelayMillis=b.holdDelayMillis
        holdTolerance=b.holdTolerance;guideSnapEnabled=b.guideSnapEnabled;smartMode=b.smartMode;fillColor=b.fillColor;fillAlpha=b.fillAlpha;gestures.undoEnabled=b.gestures.undoEnabled;gestures.pieEnabled=b.gestures.pieEnabled
    }
    override fun onDetachedFromWindow(){
        smoothers.clear();removeCallbacks(smoothFrame);measurements.clear();gestures.reset();super.onDetachedFromWindow()
    }
    fun releaseBacking(){backing?.recycle();backing=null;backingCanvas=null;backingDirty=true}
    private fun fitFixed(){fixedPageWidth?.let{if(width>0 && height>0)zoom=min(width/density/it,height/density/(fixedPageHeight?:1f));tx=0f;ty=0f}}
    val gestures=BoardGestures(this)
    private val geometry=GeometryInteraction(this)
    val measurements=MeasurementOverlay(this)
    fun showMeasurements(o:Item){measurements.show(o)}
    var onEducationalTap:(Item,PointF)->Unit={_,_->}
    var fillColor:Int?=TEAL
    var fillAlpha=255
    var tool = "pen"
    var selectionMode="free"
    var touchMode=false
    var activePane=0
        private set
    private val pointerPanes=mutableMapOf<Int,Int>()
    fun split(count:Int){
        if(isDrawing)return
        require(count in 1..4)
        store.editMetadata{
            while(store.page.panes.size<count)store.page.panes.add(Pane(color=intArrayOf(NAVY,TEAL,0xffba4058.toInt(),0xff7754ad.toInt())[store.page.panes.size]))
            // Keep all objects when merging panels back into fewer panes.
            store.page.items.filter{it.pane>=count}.forEach{it.pane=0}
            while(store.page.panes.size>count)store.page.panes.removeAt(store.page.panes.lastIndex)
        }
        activePane=0;clearSelection();sceneChanged()
    }
    private fun paneRect(index:Int):RectF{
        val n=store.page.panes.size
        if(n==1)return RectF(0f,0f,width.toFloat(),height.toFloat())
        return RectF(width*index.toFloat()/n,0f,width*(index+1f)/n,height.toFloat())
    }
    private val broadPointers=mutableSetOf<Int>()
    private var touchSpan=0f
    private var touchFocus:PointF?=null
    private val lasso=mutableListOf<PointF>()
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
    var onMindAdd:(Item,Boolean)->Unit={_,_->}
    private var tappedSelected=false
    private val eraseLast=mutableMapOf<Int,PointF>()
    var penColor=NAVY
    var highlightColor=0xffffcf40.toInt()
    var penWidth=4f
    var highlightWidth=6f
    var penStyle="round"
    var penOpacity=255
    var dashLength=12f
    var dashGap=8f
    var inkColor:Int
        get()=if(tool=="highlight")highlightColor else penColor
        set(v) { if(tool=="highlight")highlightColor=v else penColor=v }
    var inkWidth:Float
        get()=if(tool=="highlight")highlightWidth else penWidth
        set(v) { if(tool=="highlight")highlightWidth=v else penWidth=v }
    val isDrawing get()=live.isNotEmpty() || geometry.active
    var isCommitting=false
        private set
    private var backing:Bitmap?=null
    private var backingCanvas:Canvas?=null
    private var backingDirty=true
    private var navigationActive=false
    private var cachedZoom=1f;private var cachedTx=0f;private var cachedTy=0f
    private var lastFullRender=0L
    private var dirtyRegion:RectF?=null
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
    private fun commitToBacking(o:Item){
        val page=store.page;val layer=page.layers.lastOrNull{it.visible&&it.opacity>0}
        val bitmap=backing
        if(bitmap!=null && !backingDirty && dirtyRegion==null && cachedPage===page && cachedCount==page.items.size-1 && layer?.id==o.layerId && layer.opacity==1f){
            val c=Canvas(bitmap);c.save();transformCanvas(c,o.pane);renderer.draw(c,o);c.restore();cachedCount=page.items.size
        }else backingDirty=true
    }

    var profile = TouchProfile()
    var onSelection: () -> Unit = {}
    val selected = linkedSetOf<String>()
    private val pane get()=store.page.panes[activePane.coerceIn(store.page.panes.indices)]
    private var zoom:Float get()=pane.zoom;set(v){pane.zoom=v}
    private var tx:Float get()=pane.tx;set(v){pane.tx=v}
    private var ty:Float get()=pane.ty;set(v){pane.ty=v}
    private val density = resources.displayMetrics.density
    private val live = mutableMapOf<Int, Item>()
    private val smoothers=mutableMapOf<Int,StrokeSmoother>()
    private val smoothFrame=object:Runnable{override fun run(){
        val now=android.os.SystemClock.uptimeMillis()
        var changed=false
        smoothers.forEach{(id,filter)->filter.advance(now)?.let{live[id]?.points?.add(it);changed=true}}
        if(changed)postInvalidateOnAnimation()
        if(smoothers.values.any{it.pending})postOnAnimation(this)
    }}
    private fun sampleInk(id:Int,o:Item,point:Point){
        val filter=smoothers[id]
        if(filter==null)o.points.add(point)else{filter.target(point);filter.advance(point.t)?.let{o.points.add(it)};removeCallbacks(smoothFrame);postOnAnimation(smoothFrame)}
    }
    private val guides=mutableMapOf<Int,Item>()
    private val guideStopped=mutableSetOf<Int>()
    private val guideLengths=mutableMapOf<Int,Float>()
    private val holdCallbacks=mutableMapOf<Int,Runnable>()
    private val holdAt=mutableMapOf<Int,PointF>()
    private val heldStart=mutableMapOf<Int,PointF>()
    private val heldOriginal=mutableMapOf<Int,Item>()
    private val heldEnd=mutableMapOf<Int,PointF>()
    private val mindStart=mutableMapOf<Int,PointF>()
    private val actions = mutableMapOf<Int, String>()
    private var changed = false
    private var primary = -1
    private var anchor = PointF()
    private var lastFocus = PointF()
    private var span = 0f
    private var mode = ""
    private var original = listOf<Item>()
    private var box: RectF? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun snap(id:Int,point:PointF):PointF = (if(guideSnapEnabled)guides[id] else null)?.let{
        GeometryTools.snap(it,point.x,point.y,14f/store.page.panes[it.pane].zoom)
    }?:point
    private fun sampleGuidedInk(id:Int,o:Item,point:Point) {
        if(id in guideStopped)return
        val tolerance=14f/store.page.panes[o.pane].zoom
        if(!guides.containsKey(id) && guideSnapEnabled && tool!="smart"){
            val guide=store.page.visibleItems().asReversed().firstOrNull{it.pane==o.pane && canEditItem(it) && it.shape in GeometryTools.keys && GeometryTools.snap(it,point.x,point.y,tolerance)!=null}
            if(guide!=null){
                guides[id]=guide;guideLengths[id]=0f
                o.points.clear();renderer.forgetInk(o.id);smoothers.remove(id);cancelHold(id)
            }
        }
        val guide=guides[id]
        if(guide==null){sampleInk(id,o,point);return}
        val projected=GeometryTools.snap(guide,point.x,point.y,tolerance)
        if(projected==null){guideStopped.add(id);return}
        o.points.lastOrNull()?.let{last->guideLengths[id]=(guideLengths[id]?:0f)+hypot(projected.x-last.x,projected.y-last.y)}
        o.points.add(point.copy(x=projected.x,y=projected.y))
    }
    private fun drawGuideMeasurements(canvas:Canvas){
        live.forEach{(id,ink)->val guide=guides[id]?:return@forEach
            if(ink.pane!=activePane)return@forEach
            val last=ink.points.lastOrNull()?:return@forEach
            val length=(guideLengths[id]?:0f)/measureScale()
            var text=DisplayNumbers.one(length.toDouble())+" "+measurementUnit()
            if(guide.shape=="compass"){
                val r=GeometryTools.radius(guide);text+=" · "+DisplayNumbers.one((guideLengths[id]?:0f)/r*180/PI)+"°"
            }else if(guide.shape in listOf("protractor","set_square")){
                val first=ink.points.first();val angle=if(guide.shape=="protractor"){
                    val local=guide.local(last.x,last.y);atan2(guide.h-local.second,local.first-guide.w/2)*180/PI
                }else atan2(last.y-first.y,last.x-first.x)*180/PI
                text+=" · "+DisplayNumbers.one(abs(angle))+"°"
            }
            measurements.label(canvas,text,last.x,last.y-28/zoom,zoom)
        }
    }
    fun measurementUnit()=if(context.getSharedPreferences("vura",0).getFloat("pixelsPerCm",0f)>0)"cm"else"u"
    private fun cancelHold(id:Int){holdCallbacks.remove(id)?.let{removeCallbacks(it)};holdAt.remove(id)}
    private fun scheduleHold(id:Int,at:PointF){
        if(!holdRecognitionEnabled || heldStart.containsKey(id) || guides.containsKey(id))return
        val last=holdAt[id]
        if(last!=null && hypot(last.x-at.x,last.y-at.y)<holdTolerance/store.page.panes[pointerPanes[id]?:activePane].zoom)return
        cancelHold(id);holdAt[id]=PointF(at.x,at.y)
        val callback=Runnable{
            holdCallbacks.remove(id);holdAt.remove(id)
            val ink=live[id]?:return@Runnable
            if(ink.kind!="ink" || ink.points.size<3)return@Runnable
            val shape=ShapeRecognition.live(ink)?:return@Runnable
            shape.id=ink.id;shape.layerId=ink.layerId;shape.pane=ink.pane
            shape.alpha=ink.alpha;shape.dashLength=ink.dashLength;shape.dashGap=ink.dashGap
            heldStart[id]=PointF(ink.points.first().x,ink.points.first().y)
            heldEnd[id]=PointF(ink.points.last().x,ink.points.last().y)
            heldOriginal[id]=shape.deepCopy()
            live[id]=shape;renderer.forgetInk(ink.id);postInvalidateOnAnimation()
        }
        holdCallbacks[id]=callback;postDelayed(callback,holdDelayMillis)
    }

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

    fun world(x: Float, y: Float,index:Int=activePane):PointF{val n=index.coerceIn(store.page.panes.indices);val r=paneRect(n);val p=store.page.panes[n];return PointF(((x-r.left)/density-p.tx)/p.zoom,((y-r.top)/density-p.ty)/p.zoom)}

    fun measureScale():Float {
        val pixels=context.getSharedPreferences("vura",0).getFloat("pixelsPerCm",0f)
        return if(pixels>0)pixels/(density*zoom) else 10f
    }
    fun abortPendingGesture(){
        holdCallbacks.keys.toList().forEach(::cancelHold)
        smoothers.clear();removeCallbacks(smoothFrame);live.clear();guides.clear();guideStopped.clear();guideLengths.clear();heldStart.clear();heldOriginal.clear();heldEnd.clear();mindStart.clear();lasso.clear();box=null
        geometry.cancel()
        if(changed){store.cancelCheckpoint();changed=false}
        actions.clear();broadPointers.clear();transforming=emptySet();pointerPanes.clear();mode="";navigationActive=false;touchFocus=null;tappedSelected=false;sceneChanged()
    }
    fun navigateGesture(e:MotionEvent){
        for(i in 0 until e.pointerCount)pointerPanes[e.getPointerId(i)]=activePane
        navigate(e)
    }
    fun beginGestureNavigation(x:Float,y:Float,initialSpan:Float){navigationActive=true;lastFocus=PointF(x,y);span=initialSpan}
    fun center():PointF {
        if(fixedPageWidth!=null){val visible=Rect();if(getLocalVisibleRect(visible))return world(visible.exactCenterX(),visible.exactCenterY())}
        return paneRect(activePane.coerceIn(store.page.panes.indices)).let{world(it.centerX(),it.centerY())}
    }

    fun reset() {
        measurements.clear()
        gestures.reset();geometry.cancel()
        holdCallbacks.keys.toList().forEach(::cancelHold)
        smoothers.clear();removeCallbacks(smoothFrame);live.clear();guides.clear();guideStopped.clear();guideLengths.clear();heldStart.clear();heldOriginal.clear();heldEnd.clear();mindStart.clear()
        activePane=activePane.coerceIn(store.page.panes.indices)
        backingDirty=true
        zoom = 1f
        tx = 0f
        ty = 0f
        fitFixed()
        invalidate()
    }

    fun fit() {
        backingDirty=true
        if(fixedPageWidth!=null){fitFixed();invalidate();return}
        if (store.page.items.isEmpty()) {
            reset()
            return
        }
        val b = contentBounds(store.page.visibleItems().filter{it.pane==activePane})
        val viewport=paneRect(activePane)
        zoom =
            min(viewport.width() / density / (b.width() + 90), viewport.height() / density / (b.height() + 90))
                .coerceIn(.15f, 6f)
        tx = viewport.width() / density / 2 - b.centerX() * zoom
        ty = viewport.height() / density / 2 - b.centerY() * zoom
        invalidate()
    }

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
        val ids=MindMap.group(store.page,chosen()).mapTo(mutableSetOf()){it.id}
        store.editMetadata { store.page.items.removeAll { it.id in ids } }
        clearSelection()
    }

    private fun checkpoint() {
        if (!changed) {
            store.checkpoint()
            changed = true
        }
    }

    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
        backing?.recycle();backing=if(!interactiveResize && w>0 && h>0 && w.toLong()*h<=(if(fixedPageWidth==null)16_000_000L else 6_000_000L))Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888) else null
        backingCanvas=backing?.let{Canvas(it)}
        fitFixed();backingDirty=true
    }
    fun finishResize(){interactiveResize=false;onSizeChanged(width,height,width,height);invalidate()}
    private fun transformCanvas(c:Canvas,index:Int=activePane) {val n=index.coerceIn(store.page.panes.indices);val r=paneRect(n);val p=store.page.panes[n];c.clipRect(r);c.translate(r.left,r.top);c.scale(density,density);c.translate(p.tx,p.ty);c.scale(p.zoom,p.zoom) }
    override fun onDraw(c:Canvas) {
        val previewIds=roomOverlay?.previewIds().orEmpty()
        if(previewIds!=remotePreviews){remotePreviews=previewIds;backingDirty=true}
        val page=store.page
        val navigating=navigationActive || mode=="navigate"
        val age=android.os.SystemClock.uptimeMillis()-lastFullRender
        if(roomOverlay==null && navigating && page.panes.size==1 && live.isEmpty() && backingDirty && cachedPage===page && backing!=null && age<80){
            c.drawColor(page.panes[0].background)
            val ratio=zoom/cachedZoom
            c.save();c.translate(density*(tx-cachedTx*ratio),density*(ty-cachedTy*ratio));c.scale(ratio,ratio);c.drawBitmap(backing!!,0f,0f,p);c.restore()
            postInvalidateDelayed((80-age).coerceAtLeast(1));return
        }
        if(backing==null){
            page.panes.indices.forEach{index->c.save();transformCanvas(c,index)
                val clip=RectF(c.clipBounds);renderer.background(c,page,clip,page.panes[index].background)
                renderer.scene(c,page,region=clip,pane=index,exclude=transforming+remotePreviews);c.restore()}
        }
        backing?.let { bitmap ->
            val target=backingCanvas?:Canvas(bitmap);target.save()
            val page=store.page
            val full=backingDirty || cachedPage!==page || (cachedCount!=page.items.size && dirtyRegion==null)
            if(full) {
                page.panes.indices.forEach{index->
                    target.save();transformCanvas(target,index)
                    val screen=paneRect(index);val l=world(screen.left,screen.top,index);val r=world(screen.right,screen.bottom,index)
                    renderer.background(target,page,RectF(l.x,l.y,r.x,r.y),page.panes[index].background)
                    renderer.scene(target,page,region=RectF(l.x,l.y,r.x,r.y),pane=index,exclude=transforming+remotePreviews);target.restore()
                }
                cachedPage=page;backingDirty=false;cacheRebuilds++;lastFullRender=android.os.SystemClock.uptimeMillis();cachedZoom=zoom;cachedTx=tx;cachedTy=ty
            } else dirtyRegion?.let { region ->
                target.save();transformCanvas(target);target.clipRect(region)
                renderer.background(target,page,region,pane.background)
                renderer.scene(target,page,region=region,pane=activePane,exclude=transforming+remotePreviews)
                target.restore()
            }
            dirtyRegion=null
            cachedCount=page.items.size;target.restore();c.drawBitmap(bitmap,0f,0f,null)
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
            items.singleOrNull()?.takeIf{it.shape=="mindnode"}?.let{node->
                p.color=TEAL;p.strokeWidth=2f/zoom
                val radius=15f/zoom
                listOf(node.x+node.w+30f/zoom to node.y+node.h/2,
                    node.x+node.w/2 to node.y+node.h+30f/zoom).forEach{(x,y)->
                    p.style=Paint.Style.FILL;c.drawCircle(x,y,radius,p)
                    p.color=Color.WHITE;p.style=Paint.Style.STROKE
                    c.drawLine(x-7/zoom,y,x+7/zoom,y,p)
                    c.drawLine(x,y-7/zoom,x,y+7/zoom,p)
                    p.color=TEAL
                }
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
        if(store.page.panes.size>1){
            p.style=Paint.Style.STROKE;p.strokeWidth=density
            store.page.panes.indices.forEach{index->p.color=if(index==activePane)0xff9990b5.toInt()else 0xffdcd9e5.toInt();val border=paneRect(index);border.inset(density/2,density/2);c.drawRect(border,p)}
        }
        roomOverlay?.draw(c)
    }

    override fun onTouchEvent(e:MotionEvent):Boolean {
        val result=handleTouch(e);onInteraction(e);onViewportChanged();return result
    }
    private fun handleTouch(e: MotionEvent): Boolean {
        if(!isEnabled)return true
        if(gestures.event(e))return true
        if(e.actionMasked==MotionEvent.ACTION_DOWN)onActivate()
        val id = e.getPointerId(e.actionIndex)
        if(e.actionMasked==MotionEvent.ACTION_CANCEL){
            if(geometry.active){geometry.cancel();navigationActive=false;transforming=emptySet();pointerPanes.clear();actions.clear();changed=false;sceneChanged();return true}
            if(pdfBaseId!=null && changed && (tool=="erase" || actions.values.any{it=="erase"}))store.undo()
            holdCallbacks.keys.toList().forEach(::cancelHold)
            smoothers.clear();removeCallbacks(smoothFrame);live.clear();guides.clear();guideStopped.clear();guideLengths.clear();heldStart.clear();heldOriginal.clear();heldEnd.clear();mindStart.clear()
            if(transforming.isNotEmpty())original.forEach{base->store.page.items.firstOrNull{it.id==base.id}?.let{o->o.x=base.x;o.y=base.y;o.w=base.w;o.h=base.h;o.rotation=base.rotation;o.width=base.width}}
            navigationActive=false;transforming=emptySet();pointerPanes.clear();actions.clear();changed=false;sceneChanged();return true
        }
        if(e.actionMasked in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_POINTER_DOWN)){
            val n=store.page.panes.indices.firstOrNull{paneRect(it).contains(e.getX(e.actionIndex),e.getY(e.actionIndex))}?:0
            pointerPanes[id]=n
            if(e.actionMasked==MotionEvent.ACTION_DOWN && activePane!=n){activePane=n;clearSelection()}
        }
        if(!sessionCanEdit||!store.editAllowed()){navigate(e);return true}
        val at = world(e.getX(e.actionIndex), e.getY(e.actionIndex),pointerPanes[id]?:activePane)
        if(geometry.active){geometry.event(e,at);if(!geometry.active){transforming=emptySet();backingDirty=true;pointerPanes.clear()};invalidate();return true}
        if(e.actionMasked==MotionEvent.ACTION_DOWN && tool!="erase" && tool!="fill" && geometry.start(at,22f/zoom)){
            transforming=chosen().map{it.id}.toSet();backingDirty=true;onSelection();invalidate();return true
        }
        if(tool=="fill"){
            if(e.actionMasked==MotionEvent.ACTION_UP){val target=store.page.visibleItems().asReversed().firstOrNull{it.pane==activePane&&(ShapeFill.contains(it,at.x,at.y)||it.hit(at.x,at.y,0f))}
                if(target!=null&&ShapeFill.contains(target,at.x,at.y)){
                    if(!target.locked&&canEditItem(target)&&store.page.editable(target))store.editMetadata{target.fillColor=fillColor;target.fillAlpha=fillAlpha}
                }else if((target==null || target.id==pdfBaseId) && fillColor!=null)HandFill.create(store.page,activePane,at.x,at.y,fillColor!!,fillAlpha)?.let{result->
                    store.editMetadata{store.page.items.add(result.index,result.item)}
                }
            }
            return true
        }
        if(tool=="pan"){navigate(e);return true}
        if(e.actionMasked==MotionEvent.ACTION_DOWN){broadPointers.clear();touchSpan=0f;touchFocus=null}
        if(touchMode && pdfBaseId==null && e.actionMasked in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_POINTER_DOWN) && e.getToolType(e.actionIndex)!=MotionEvent.TOOL_TYPE_ERASER && e.getTouchMajor(e.actionIndex)>profile.thin && e.getTouchMajor(e.actionIndex)<profile.palm){
            val index=pointerPanes[id]?:activePane
            if(broadPointers.isNotEmpty() && index!=activePane){actions[id]="reject";return true}
            if(activePane!=index){activePane=index;clearSelection()}
            broadPointers.add(id)
        }
        val effectiveTool=if(id in broadPointers)"select" else tool
        val navIndices=(0 until e.pointerCount).filter{e.getPointerId(it) in broadPointers && pointerPanes[e.getPointerId(it)]==activePane}
        if(navIndices.size>=2){
            if(mode!="navigate"){
                transforming=emptySet()
                // A second finger changes a selection gesture into navigation.
                // Restore any tentative drag, without undoing simultaneous ink.
                if(mode in listOf("move","resize","rotate")){original.forEach{base->store.page.items.firstOrNull{it.id==base.id}?.let{o->o.x=base.x;o.y=base.y;o.w=base.w;o.h=base.h;o.rotation=base.rotation;o.width=base.width}};backingDirty=true}
                tappedSelected=false
            }
            val i=navIndices[0];val j=navIndices[1];val focus=PointF((e.getX(i)+e.getX(j))/2,(e.getY(i)+e.getY(j))/2);val nextSpan=hypot(e.getX(i)-e.getX(j),e.getY(i)-e.getY(j))
            if(e.actionMasked==MotionEvent.ACTION_MOVE && touchFocus!=null){
                val before=world(touchFocus!!.x,touchFocus!!.y)
                if(touchSpan>0)zoom=(zoom*nextSpan/touchSpan).coerceIn(.15f,6f)
                val rect=paneRect(activePane);tx=(focus.x-rect.left)/density-before.x*zoom;ty=(focus.y-rect.top)/density-before.y*zoom;backingDirty=true
            }
            touchSpan=nextSpan;touchFocus=focus;box=null;lasso.clear();mode="navigate"
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                    changed = false
                    primary = id
                    anchor = at
                    mode = ""
                    eraseLast.clear();tappedSelected=false
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if(!profile.multiTouch && id!=primary) { actions[id]="reject";return true }
                if(e.actionMasked==MotionEvent.ACTION_DOWN) requestUnbufferedDispatch(e)
                val behavior =
                    if(pdfBaseId!=null && profile.classify(e.getToolType(e.actionIndex),e.getTouchMajor(e.actionIndex))=="palm") {if(pdfPalmErase)"erase" else "reject"} else profile.action(e.getToolType(e.actionIndex), e.getTouchMajor(e.actionIndex))
                actions[id] = behavior
                if (behavior == "reject") return true
                if(e.actionMasked==MotionEvent.ACTION_DOWN && effectiveTool=="select"){
                    chosen().singleOrNull()?.takeIf{it.shape=="mindnode"}?.let{node->
                        val child=hypot(at.x-(node.x+node.w+30f/zoom),at.y-(node.y+node.h/2))<22f/zoom
                        val sibling=hypot(at.x-(node.x+node.w/2),at.y-(node.y+node.h+30f/zoom))<22f/zoom
                        if(child||sibling){actions[id]="mind_add";post{onMindAdd(node,sibling)};return true}
                    }
                }
                if(id !in broadPointers && (tool in listOf("pen","highlight","shape") || (tool=="smart" && smartMode=="shape"))) {
                    if(!store.page.canDraw()){actions[id]="reject";android.widget.Toast.makeText(context,context.tr("Select an unlocked visible layer","یک لایهٔ نمایان و باز انتخاب کنید"),android.widget.Toast.LENGTH_SHORT).show();return true}
                }
                if(id in broadPointers){actions[id]="select";if(navIndices.size>=2){postInvalidateOnAnimation();return true};primary=id;anchor=at}
                else if(e.actionMasked==MotionEvent.ACTION_POINTER_DOWN && effectiveTool in listOf("select","shape")){actions[id]="reject";return true}
                if (effectiveTool == "erase" || behavior == "erase") {
                    checkpoint()
                    erase(at,eraseLast[id]?:at,pointerPanes[id]?:activePane);eraseLast[id]=at
                } else if (effectiveTool == "select" && id == primary) {
                    actions[id]="select"
                    val chosen = chosen()
                    val bounds = contentBounds(chosen)
                    original = chosen.filter { !it.locked }.map { it.deepCopy() }
                    mode =
                        when {
                            chosen.isNotEmpty() &&
                                hypot(at.x - bounds.right, at.y - bounds.bottom) < 23 / zoom ->
                                "resize"
                            chosen.isNotEmpty() &&
                                hypot(at.x - bounds.centerX(), at.y - (bounds.top - 28 / zoom)) <
                                    23 / zoom -> "rotate"
                            else -> ""
                        }
                    if (mode.isEmpty()) {
                        val hit =
                            store.page.visibleItems().asReversed().filter { it.pane==activePane && store.page.editable(it) && !it.locked }.firstOrNull {
                                it.hit(at.x, at.y, 9 / zoom)
                            }
                        if (hit == null) {
                            selected.clear()
                            mode = "box"
                            lasso.clear();lasso.add(at)
                        } else {
                            tappedSelected=hit.id in selected
                            if (hit.id !in selected) {
                                selected.clear()
                                selected.add(hit.id)
                            }
                            original = MindMap.group(store.page,chosen()).map { it.deepCopy() }
                            mode = "move"
                        }
                    }
                    onSelection()
                } else if (tool == "shape" && id == primary) {
                    checkpoint()
                    live[id] =
                        Item(
                            kind = "shape",
                            layerId = store.page.activeLayerId,
                            pane=pointerPanes[id]?:activePane,
                            x = at.x,
                            y = at.y,
                            w = 1f,
                            h = 1f,
                            shape = shape,
                            color = inkColor,
                            width = inkWidth,
                        )
                } else if (tool in listOf("pen", "highlight", "smart")) {
                    if(tool!="smart" || smartMode=="shape")checkpoint()
                    val drawingPane=store.page.panes[pointerPanes[id]?:activePane]
                    val splitInk=store.page.panes.size>1
                    val style=if(tool=="highlight")"highlight" else if(splitInk)drawingPane.penStyle else profile.style(e.getToolType(e.actionIndex),e.getTouchMajor(e.actionIndex),penStyle)
                    mindStart[id]=at
                    live[id] =
                        Item(
                            layerId=store.page.activeLayerId,
                            pane=pointerPanes[id]?:activePane,
                            w = 1f,
                            h = 1f,
                            color = if(store.page.panes.size>1)store.page.panes[pointerPanes[id]?:activePane].color else if(tool=="highlight") inkColor else profile.color(e.getToolType(e.actionIndex),e.getTouchMajor(e.actionIndex),inkColor),
                            width = (if(splitInk)drawingPane.penWidth else if(tool=="highlight") inkWidth else profile.width(e.getToolType(e.actionIndex),e.getTouchMajor(e.actionIndex),inkWidth)) * if(style=="highlight")4f else 1f,
                            shape = style,
                            dashLength=if(splitInk)drawingPane.dashLength else dashLength,
                            dashGap=if(splitInk)drawingPane.dashGap else dashGap,
                            alpha = if (style == "highlight") 75*penOpacity/255 else penOpacity,
                            points = mutableListOf(),
                        )
                    sampleGuidedInk(id,live.getValue(id),Point(at.x,at.y,e.eventTime,e.getPressure(e.actionIndex)))
                    if(style=="smooth" && id !in guides){smoothers[id]=StrokeSmoother(live.getValue(id).points.first());removeCallbacks(smoothFrame);postOnAnimation(smoothFrame)}
                    if(tool=="pen"&&style!="smooth")scheduleHold(id,at)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val pid = e.getPointerId(i)
                    val point = world(e.getX(i), e.getY(i),pointerPanes[pid]?:activePane)
                    if (actions[pid] == "reject") continue
                    if(pid in broadPointers && mode=="navigate")continue
                    if (tool == "erase" || actions[pid] == "erase") {
                        checkpoint()
                        erase(point,eraseLast[pid]?:point,pointerPanes[pid]?:activePane);eraseLast[pid]=point
                    } else if (actions[pid] == "select" && pid == primary) {
                        if (mode == "box") {
                            if(selectionMode=="free")lasso.add(point)
                            box =
                                RectF(
                                    min(anchor.x, point.x),
                                    min(anchor.y, point.y),
                                    max(anchor.x, point.x),
                                    max(anchor.y, point.y),
                                )
                        }
                        else if(hypot(point.x-anchor.x,point.y-anchor.y)>4/zoom) {
                            checkpoint();transform(point)
                        }
                    } else
                        live[pid]?.let { o ->
                            if (o.kind == "shape") {
                                val endpoint=if(pid in heldStart)snap(pid,point) else point
                                if(pid in heldOriginal && o.shape!="line"){
                                    val base=heldOriginal.getValue(pid);val last=heldEnd.getValue(pid)
                                    val start=heldStart.getValue(pid)
                                    HeldShapeResize.apply(o,base,start.x,start.y,last.x,last.y,endpoint.x,endpoint.y)
                                }else{
                                    val start=heldStart[pid]?:anchor
                                    o.flipX=endpoint.x<start.x;o.flipY=endpoint.y>=start.y
                                    val dx=endpoint.x-start.x;val dy=endpoint.y-start.y
                                    o.w=abs(dx).coerceAtLeast(1f);o.h=abs(dy).coerceAtLeast(1f)
                                    if(Shapes.uniform(o.shape)){val size=max(o.w,o.h);o.w=size;o.h=size}
                                    o.x=if(dx<0)start.x-o.w else start.x
                                    o.y=if(dy<0)start.y-o.h else start.y
                                }
                            } else {
                                val paneIndex=pointerPanes[pid]?:activePane
                                val rect=paneRect(paneIndex)
                                val state=store.page.panes[paneIndex]
                                val scale=density*state.zoom
                                for (j in 0 until e.historySize) {
                                    val q=PointF((e.getHistoricalX(i,j)-rect.left-density*state.tx)/scale,
                                        (e.getHistoricalY(i,j)-rect.top-density*state.ty)/scale)
                                    sampleGuidedInk(pid,o,
                                        Point(
                                            q.x,
                                            q.y,
                                            e.getHistoricalEventTime(j),
                                            e.getHistoricalPressure(i, j),
                                        )
                                    )
                                }
                                sampleGuidedInk(pid,o,Point(point.x,point.y,e.eventTime,e.getPressure(i)))
                                if(tool=="pen"&&o.shape!="smooth")scheduleHold(pid,point)
                            }
                        }
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> {
                cancelHold(id)
                live.remove(id)?.let { o ->
                    if (o.kind == "ink") {
                        val endpoint=Point(at.x,at.y,e.eventTime)
                        val filter=smoothers.remove(id)
                        if(filter!=null)o.points.addAll(filter.finish(endpoint))else sampleGuidedInk(id,o,endpoint)
                        if(o.points.isEmpty() || id in guides && (guideLengths[id]?:0f)<.5f)return@let
                        if(smoothers.isEmpty())removeCallbacks(smoothFrame)
                        val left = o.points.minOf { it.x }
                        val top = o.points.minOf { it.y }
                        o.w = (o.points.maxOf { it.x } - left).coerceAtLeast(1f)
                        o.h = (o.points.maxOf { it.y } - top).coerceAtLeast(1f)
                        o.inkW = o.w
                        o.inkH = o.h
                        o.x = left
                        o.y = top
                        o.points.forEach {
                            it.x -= left
                            it.y -= top
                        }
                    }
                    if(tool=="pen")mindStart[id]?.let{if(MindMap.attach(store.page,o,it.x,it.y))backingDirty=true}
                    renderer.forgetInk(o.id)
                    if(tool=="smart" && smartMode!="shape"){
                        val enclosed=if(SmartSelection.isLoop(o))SmartSelection.enclosed(o,store.page.visibleItems().filter{store.page.editable(it)&&canEditItem(it)})else emptyList()
                        if(enclosed.isNotEmpty())post{onSmart(enclosed)}
                        else android.widget.Toast.makeText(context,context.tr("Circle existing writing with a closed loop. Use Pen to write.","دور نوشتهٔ قبلی یک خط بسته بکشید. برای نوشتن از قلم استفاده کنید."),android.widget.Toast.LENGTH_SHORT).show()
                    }else if(tool=="smart" && smartMode=="shape"){
                        val converted=ShapeRecognition.convert(o)
                        store.page.items.add((converted?:o).apply{layerId=o.layerId;pane=o.pane})
                    }else {checkpoint();store.page.items.add(o)}
                    if(tool!="smart")commitToBacking(o) else if(smartMode=="shape")backingDirty=true
                }
                guides.remove(id);guideStopped.remove(id);guideLengths.remove(id);heldStart.remove(id);heldOriginal.remove(id);heldEnd.remove(id);mindStart.remove(id)
                val wasSelection=actions[id]=="select"
                actions.remove(id);eraseLast.remove(id);broadPointers.remove(id);pointerPanes.remove(id)
                if (id == primary) {
                    box?.let { rect ->
                        selected.addAll(SmartSelection.selectGesture(lasso,rect,store.page.visibleItems().filter{it.pane==activePane&&store.page.editable(it)&&canEditItem(it)&&!it.locked},selectionMode).map{it.id})
                    }
                    box = null
                    lasso.clear()
                    if(wasSelection){
                        if(mode=="move" && hypot(at.x-anchor.x,at.y-anchor.y)<4/zoom){val education=chosen().singleOrNull()?.takeIf{it.kind in listOf("periodic","graph")}
                            if(education!=null)post{onEducationalTap(education,at)}else if(tappedSelected)post{onObjectActions()}
                        }
                        onSelection()
                    }
                }
                if (e.actionMasked == MotionEvent.ACTION_UP) {
                    if(transforming.isNotEmpty()){transforming=emptySet();backingDirty=true}
                    if(changed || mode=="navigate") { isCommitting=true;store.changed();isCommitting=false }
                    changed = false
                    mode = ""
                    performClick()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                smoothers.clear();removeCallbacks(smoothFrame);live.clear()
                broadPointers.clear();pointerPanes.clear();lasso.clear();touchFocus=null
                actions.clear()
                box = null
                if (changed) store.undo()
                changed = false
                mode = ""
            }
        }
        postInvalidateOnAnimation()
        return true
    }

    private fun transform(point: PointF) {
        if(transforming.isEmpty()){transforming=original.mapTo(mutableSetOf()){it.id};backingDirty=true}
        if (original.isEmpty()) return
        val b = contentBounds(original)
        original.forEach { base ->
            val o = store.page.items.firstOrNull { it.id == base.id } ?: return@forEach
            when (mode) {
                "move" -> {
                    o.x = base.x + point.x - anchor.x
                    o.y = base.y + point.y - anchor.y
                }
                "resize" -> {
                    val factor =
                        max(
                                (point.x - b.left) / b.width().coerceAtLeast(1f),
                                (point.y - b.top) / b.height().coerceAtLeast(1f),
                            )
                            .coerceIn(.05f, 20f)
                    o.x = b.left + (base.x - b.left) * factor
                    o.y = b.top + (base.y - b.top) * factor
                    if(original.size==1 && base.kind=="shape" && base.shape=="ruler"){
                        o.w=(base.w+point.x-anchor.x).coerceIn(40f,100000f)
                        o.h=base.h
                    }else if(original.size==1 && base.kind=="shape" && !Shapes.uniform(base.shape) && base.shape !in GeometryTools.keys){
                        o.w=(base.w+point.x-anchor.x).coerceIn(4f,100000f)
                        o.h=(base.h+point.y-anchor.y).coerceIn(4f,100000f)
                    }else{o.w=(base.w*factor).coerceIn(.01f,100000f);o.h=(base.h*factor).coerceIn(.01f,100000f);if(base.kind=="text"){o.width=(base.width*factor).coerceIn(.1f,200f);TextLayout.fit(o)}}
                }
                "rotate" -> {
                    val delta =
                        ((atan2(point.y - b.centerY(), point.x - b.centerX()) -
                                atan2(anchor.y - b.centerY(), anchor.x - b.centerX())) * 180 / PI)
                            .toFloat()
                    o.rotation = base.rotation + delta
                }
            }
        }
    }

    private fun erase(q: PointF,from:PointF=q,index:Int=activePane) {
        if(erasePdfContent && pdfBaseId!=null){
            store.page.items.firstOrNull{it.id==pdfBaseId}?.let{o->
                val was=o.locked;o.locked=false
                Erasing.cut(o,from,q,eraserRadius/store.page.panes[index].zoom);o.locked=was
            }
            backingDirty=true
        }
        if(store.page.panes.size>1)backingDirty=true
        val radius=eraserRadius/store.page.panes[index].zoom
        val region=RectF(min(from.x,q.x)-radius-4/zoom,min(from.y,q.y)-radius-4/zoom,max(from.x,q.x)+radius+4/zoom,max(from.y,q.y)+radius+4/zoom)
        if(dirtyRegion==null)dirtyRegion=region else dirtyRegion!!.union(region)
        if(eraserMode=="area")store.page.items.filter{it.pane==index && store.page.editable(it)&&canEditItem(it) && (eraseObjects||it.kind=="ink")}.forEach{Erasing.cut(it,from,q,radius)}
        else {
            val steps=ceil(hypot(q.x-from.x,q.y-from.y)/max(radius*.5f,1f)).toInt().coerceIn(1,20000)
            store.page.items.removeAll { o ->
                if(o.pane!=index || o.locked || !store.page.editable(o) || !canEditItem(o) || (!eraseObjects&&o.kind!="ink"))false else {
                    val bounds=itemBounds(o).apply{inset(-o.width*3-4,-o.width*3-4)}
                    val removed=RectF.intersects(bounds,region) && (0..steps).any { i ->val t=i.toFloat()/steps;o.hit(from.x+(q.x-from.x)*t,from.y+(q.y-from.y)*t,radius)}
                    if(removed)dirtyRegion?.union(bounds)
                    removed
                }
            }
        }
        selected.retainAll(store.page.items.map { it.id }.toSet())
    }

    private fun navigate(e: MotionEvent) {
        if(e.actionMasked==MotionEvent.ACTION_DOWN)navigationActive=true
        if(e.actionMasked==MotionEvent.ACTION_UP || e.actionMasked==MotionEvent.ACTION_CANCEL)navigationActive=false
        backingDirty=true
        val indices=(0 until e.pointerCount).filter{pointerPanes[e.getPointerId(it)]==activePane}
        if(indices.isEmpty())return
        val x = indices.sumOf { e.getX(it).toDouble() }.toFloat() / indices.size
        val y = indices.sumOf { e.getY(it).toDouble() }.toFloat() / indices.size
        val newSpan =
            if (indices.size > 1) hypot(e.getX(indices[0]) - e.getX(indices[1]), e.getY(indices[0]) - e.getY(indices[1])) else 0f
        if (e.actionMasked == MotionEvent.ACTION_MOVE) {
            val before = world(x, y)
            if (span > 0 && newSpan > 0) {
                zoom = (zoom * newSpan / span).coerceIn(.15f, 6f)
                val rect=paneRect(activePane)
                tx = (x-rect.left) / density - before.x * zoom
                ty = (y-rect.top) / density - before.y * zoom
            }
            tx += (x - lastFocus.x) / density
            ty += (y - lastFocus.y) / density
        } else span = 0f
        span = if (e.actionMasked == MotionEvent.ACTION_MOVE) newSpan else 0f
        lastFocus = PointF(x, y)
        invalidate()
        if (e.actionMasked == MotionEvent.ACTION_UP) {store.changed();pointerPanes.clear();performClick()}
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
