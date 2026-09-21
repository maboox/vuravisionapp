package com.vuravision.classroom

import android.content.Context
import android.graphics.*
import android.view.*
import kotlin.math.*

class Board(context: Context, val store: Store, val renderer: Renderer) : View(context) {
    var tool = "pen"
    var touchMode=false
    var activePane=0
        private set
    private val pointerPanes=mutableMapOf<Int,Int>()
    fun split(count:Int){
        if(isDrawing)return
        require(count in 1..4)
        store.edit{
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
        if(n<4)return RectF(width*index.toFloat()/n,0f,width*(index+1f)/n,height.toFloat())
        return RectF((index%2)*width/2f,(index/2)*height/2f,(index%2+1)*width/2f,(index/2+1)*height/2f)
    }
    private val broadPointers=mutableSetOf<Int>()
    private var touchSpan=0f
    private var touchFocus:PointF?=null
    private val lasso=mutableListOf<PointF>()
    var shape = "rectangle"
    var eraserMode="stroke"
    var eraserRadius=18f
    var eraseObjects=true
    var smartMode="text"
    var onSmart:(List<Item>)->Unit={}
    var onObjectActions:()->Unit={}
    private var tappedSelected=false
    private val eraseLast=mutableMapOf<Int,PointF>()
    var penColor=NAVY
    var highlightColor=0xffffcf40.toInt()
    var penWidth=4f
    var highlightWidth=6f
    var penStyle="round"
    var inkColor:Int
        get()=if(tool=="highlight")highlightColor else penColor
        set(v) { if(tool=="highlight")highlightColor=v else penColor=v }
    var inkWidth:Float
        get()=if(tool=="highlight")highlightWidth else penWidth
        set(v) { if(tool=="highlight")highlightWidth=v else penWidth=v }
    val isDrawing get()=live.isNotEmpty()
    var isCommitting=false
        private set
    private var backing:Bitmap?=null
    private var backingDirty=true
    private var dirtyRegion:RectF?=null
    private var cachedPage:Page?=null
    private var cachedCount=0
    var cacheRebuilds=0
        private set
    fun sceneChanged() { backingDirty=true;invalidate() }
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

    init {
        isFocusable = true
        contentDescription = context.s("board")
        setBackgroundColor(Color.WHITE)
    }

    fun chosen() = if(selected.isEmpty()) emptyList() else store.page.visibleItems().filter { it.pane==activePane && it.id in selected && store.page.editable(it) }

    fun clearSelection() {
        selected.clear()
        onSelection()
        invalidate()
    }

    fun world(x: Float, y: Float,index:Int=activePane):PointF{val n=index.coerceIn(store.page.panes.indices);val r=paneRect(n);val p=store.page.panes[n];return PointF(((x-r.left)/density-p.tx)/p.zoom,((y-r.top)/density-p.ty)/p.zoom)}

    fun center() = paneRect(activePane.coerceIn(store.page.panes.indices)).let{world(it.centerX(),it.centerY())}

    fun reset() {
        activePane=activePane.coerceIn(store.page.panes.indices)
        backingDirty=true
        zoom = 1f
        tx = 0f
        ty = 0f
        invalidate()
    }

    fun fit() {
        backingDirty=true
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
        if(!store.page.canDraw())return
        o.layerId=store.page.activeLayerId
        o.pane=activePane
        if(o.kind in listOf("text","sticky"))TextLayout.fit(o)
        val c = center()
        o.x = c.x - o.w / 2
        o.y = c.y - o.h / 2
        store.edit { store.page.items.add(o) }
        selected.clear()
        selected.add(o.id)
        tool = "select"
        onSelection()
        invalidate()
    }

    fun edit(action: (Item) -> Unit) {
        store.edit { chosen().filter { !it.locked }.forEach(action) }
        invalidate()
    }

    fun delete() {
        store.edit { store.page.items.removeAll { it.id in selected && !it.locked && store.page.editable(it) } }
        clearSelection()
    }

    private fun checkpoint() {
        if (!changed) {
            store.checkpoint()
            changed = true
        }
    }

    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
        backing?.recycle();backing=if(w>0 && h>0)Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888) else null
        backingDirty=true
    }
    private fun transformCanvas(c:Canvas,index:Int=activePane) {val n=index.coerceIn(store.page.panes.indices);val r=paneRect(n);val p=store.page.panes[n];c.clipRect(r);c.translate(r.left,r.top);c.scale(density,density);c.translate(p.tx,p.ty);c.scale(p.zoom,p.zoom) }
    override fun onDraw(c:Canvas) {
        backing?.let { bitmap ->
            val target=Canvas(bitmap);target.save()
            val page=store.page
            val full=backingDirty || cachedPage!==page || (cachedCount!=page.items.size && dirtyRegion==null)
            if(full) {
                page.panes.indices.forEach{index->
                    target.save();transformCanvas(target,index)
                    val screen=paneRect(index);val l=world(screen.left,screen.top,index);val r=world(screen.right,screen.bottom,index)
                    renderer.background(target,page,RectF(l.x,l.y,r.x,r.y))
                    if(page.panes[index].background!=Color.WHITE)target.drawColor(page.panes[index].background)
                    renderer.scene(target,page,pane=index);target.restore()
                }
                cachedPage=page;backingDirty=false;cacheRebuilds++
            } else dirtyRegion?.let { region ->
                target.save();transformCanvas(target);target.clipRect(region)
                renderer.background(target,page,region)
                if(pane.background!=Color.WHITE)target.drawColor(pane.background)
                renderer.scene(target,page,region=region,pane=activePane)
                target.restore()
            }
            dirtyRegion=null
            cachedCount=page.items.size;target.restore();c.drawBitmap(bitmap,0f,0f,null)
        }
        live.values.forEach { o ->
            c.save();transformCanvas(c,o.pane)
            val alpha=store.page.layers.firstOrNull{it.id==o.layerId}?.opacity?:1f
            val save=if(alpha<1f)c.saveLayerAlpha(null,(alpha*255).toInt())else c.save();renderer.draw(c,o);c.restoreToCount(save)
            c.restore()
        }
        c.save();transformCanvas(c)
        p.color = 0xffe46d38.toInt()
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f / zoom
        val items = chosen()
        if (items.isNotEmpty()) {
            val b = contentBounds(items)
            c.drawRect(b, p)
            p.style = Paint.Style.FILL
            c.drawCircle(b.right, b.bottom, 10f / zoom, p)
            c.drawCircle(b.centerX(), b.top - 28 / zoom, 10f / zoom, p)
        }
        box?.let {
            p.style = Paint.Style.STROKE
            c.drawRect(it, p)
        }
        if(lasso.size>1){val path=Path();path.moveTo(lasso[0].x,lasso[0].y);lasso.drop(1).forEach{path.lineTo(it.x,it.y)};p.style=Paint.Style.STROKE;c.drawPath(path,p)}
        c.restore()
        if(store.page.panes.size>1){
            p.style=Paint.Style.STROKE;p.strokeWidth=density
            store.page.panes.indices.forEach{index->p.color=if(index==activePane)0xff9990b5.toInt()else 0xffdcd9e5.toInt();val border=paneRect(index);border.inset(density/2,density/2);c.drawRect(border,p)}
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val id = e.getPointerId(e.actionIndex)
        if(e.actionMasked in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_POINTER_DOWN)){
            val n=store.page.panes.indices.firstOrNull{paneRect(it).contains(e.getX(e.actionIndex),e.getY(e.actionIndex))}?:0
            pointerPanes[id]=n
            if(e.actionMasked==MotionEvent.ACTION_DOWN && activePane!=n){activePane=n;clearSelection()}
        }
        val at = world(e.getX(e.actionIndex), e.getY(e.actionIndex),pointerPanes[id]?:activePane)
        if(tool=="pan"){navigate(e);return true}
        if(e.actionMasked==MotionEvent.ACTION_DOWN){broadPointers.clear();touchSpan=0f;touchFocus=null}
        if(touchMode && e.actionMasked in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_POINTER_DOWN) && e.getToolType(e.actionIndex)!=MotionEvent.TOOL_TYPE_ERASER && e.getTouchMajor(e.actionIndex)>profile.thin && e.getTouchMajor(e.actionIndex)<profile.palm){
            val index=pointerPanes[id]?:activePane
            if(broadPointers.isNotEmpty() && index!=activePane){actions[id]="reject";return true}
            if(activePane!=index){activePane=index;clearSelection()}
            broadPointers.add(id)
        }
        val effectiveTool=if(id in broadPointers)"select" else tool
        val navIndices=(0 until e.pointerCount).filter{e.getPointerId(it) in broadPointers && pointerPanes[e.getPointerId(it)]==activePane}
        if(navIndices.size>=2){
            if(mode!="navigate"){
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
                    profile.action(e.getToolType(e.actionIndex), e.getTouchMajor(e.actionIndex))
                actions[id] = behavior
                if (behavior == "reject") return true
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
                            store.page.visibleItems().asReversed().filter { it.pane==activePane && store.page.editable(it) }.firstOrNull {
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
                            original = chosen().filter { !it.locked }.map { it.deepCopy() }
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
                    val style=if(tool=="highlight")"highlight" else profile.style(e.getToolType(e.actionIndex),e.getTouchMajor(e.actionIndex),penStyle)
                    live[id] =
                        Item(
                            layerId=store.page.activeLayerId,
                            pane=pointerPanes[id]?:activePane,
                            w = 1f,
                            h = 1f,
                            color = if(store.page.panes.size>1)store.page.panes[pointerPanes[id]?:activePane].color else if(tool=="highlight") inkColor else profile.color(e.getToolType(e.actionIndex),e.getTouchMajor(e.actionIndex),inkColor),
                            width = (if(tool=="highlight") inkWidth else profile.width(e.getToolType(e.actionIndex),e.getTouchMajor(e.actionIndex),inkWidth)) * if(style=="highlight")4f else 1f,
                            shape = if(style=="highlight") "marker" else style,
                            alpha = if (style == "highlight") 75 else 255,
                            points =
                                mutableListOf(
                                    Point(at.x, at.y, e.eventTime, e.getPressure(e.actionIndex))
                                ),
                        )
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
                            lasso.add(point)
                            box =
                                RectF(
                                    min(anchor.x, point.x),
                                    min(anchor.y, point.y),
                                    max(anchor.x, point.x),
                                    max(anchor.y, point.y),
                                )
                        }
                        else if(hypot(point.x-anchor.x,point.y-anchor.y)>6/zoom) {
                            checkpoint();transform(point)
                        }
                    } else
                        live[pid]?.let { o ->
                            if (o.kind == "shape") {
                                o.flipX=point.x<anchor.x;o.flipY=point.y>=anchor.y
                                val dx=point.x-anchor.x;val dy=point.y-anchor.y
                                o.w=abs(dx).coerceAtLeast(1f);o.h=abs(dy).coerceAtLeast(1f)
                                if(Shapes.uniform(shape)){val size=max(o.w,o.h);o.w=size;o.h=size}
                                o.x=if(dx<0)anchor.x-o.w else anchor.x
                                o.y=if(dy<0)anchor.y-o.h else anchor.y
                            } else {
                                for (j in 0 until e.historySize) {
                                    val q = world(e.getHistoricalX(i, j), e.getHistoricalY(i, j),pointerPanes[pid]?:activePane)
                                    o.points.add(
                                        Point(
                                            q.x,
                                            q.y,
                                            e.getHistoricalEventTime(j),
                                            e.getHistoricalPressure(i, j),
                                        )
                                    )
                                }
                                o.points.add(Point(point.x, point.y, e.eventTime, e.getPressure(i)))
                            }
                        }
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> {
                live.remove(id)?.let { o ->
                    if (o.kind == "ink") {
                        o.points.add(Point(at.x, at.y, e.eventTime))
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
                    renderer.forgetInk(o.id)
                    if(tool=="smart" && smartMode!="shape"){
                        val enclosed=if(SmartSelection.isLoop(o))SmartSelection.enclosed(o,store.page.visibleItems().filter{store.page.editable(it)})else emptyList()
                        if(enclosed.isNotEmpty())post{onSmart(enclosed)}
                        else android.widget.Toast.makeText(context,context.tr("Circle existing writing with a closed loop. Use Pen to write.","دور نوشتهٔ قبلی یک خط بسته بکشید. برای نوشتن از قلم استفاده کنید."),android.widget.Toast.LENGTH_SHORT).show()
                    }else if(tool=="smart" && smartMode=="shape"){
                        val converted=ShapeRecognition.convert(o)
                        store.page.items.add((converted?:o).apply{layerId=o.layerId;pane=o.pane})
                    }else {checkpoint();store.page.items.add(o)}
                    if(tool!="smart")commitToBacking(o) else if(smartMode=="shape")backingDirty=true
                }
                val wasSelection=actions[id]=="select"
                actions.remove(id);eraseLast.remove(id);broadPointers.remove(id);pointerPanes.remove(id)
                if (id == primary) {
                    box?.let { rect ->
                        selected.addAll(SmartSelection.selectGesture(lasso,rect,store.page.visibleItems().filter{it.pane==activePane&&store.page.editable(it)}).map{it.id})
                    }
                    box = null
                    lasso.clear()
                    if(wasSelection){
                        if(mode=="move" && tappedSelected && hypot(at.x-anchor.x,at.y-anchor.y)<6/zoom)post{onObjectActions()}
                        onSelection()
                    }
                }
                if (e.actionMasked == MotionEvent.ACTION_UP) {
                    if(changed || mode=="navigate") { isCommitting=true;store.changed();isCommitting=false }
                    changed = false
                    mode = ""
                    performClick()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                live.clear()
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
        backingDirty=true
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
                    if(original.size==1 && base.kind=="shape" && !Shapes.uniform(base.shape) && base.shape !in GeometryTools.keys){
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
        if(store.page.panes.size>1)backingDirty=true
        val radius=eraserRadius/store.page.panes[index].zoom
        val region=RectF(min(from.x,q.x)-radius-4/zoom,min(from.y,q.y)-radius-4/zoom,max(from.x,q.x)+radius+4/zoom,max(from.y,q.y)+radius+4/zoom)
        if(dirtyRegion==null)dirtyRegion=region else dirtyRegion!!.union(region)
        if(eraserMode=="area")store.page.items.filter{it.pane==index && store.page.editable(it) && (eraseObjects||it.kind=="ink")}.forEach{Erasing.cut(it,from,q,radius)}
        else {
            val steps=ceil(hypot(q.x-from.x,q.y-from.y)/max(radius*.5f,1f)).toInt().coerceIn(1,20000)
            store.page.items.removeAll { o ->
                if(o.pane!=index || o.locked || !store.page.editable(o) || (!eraseObjects&&o.kind!="ink"))false else {
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
