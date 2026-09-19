package com.vuravision.classroom

import android.content.Context
import android.graphics.*
import android.view.*
import kotlin.math.*

class Board(context: Context, val store: Store, val renderer: Renderer) : View(context) {
    var tool = "pen"
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
    private var cachedPage:Page?=null
    private var cachedCount=0
    var cacheRebuilds=0
        private set
    fun sceneChanged() { backingDirty=true;invalidate() }

    var profile = TouchProfile()
    var onSelection: () -> Unit = {}
    val selected = linkedSetOf<String>()
    private var zoom = 1f
    private var tx = 0f
    private var ty = 0f
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

    fun chosen() = store.page.items.filter { it.id in selected }

    fun clearSelection() {
        selected.clear()
        onSelection()
        invalidate()
    }

    fun world(x: Float, y: Float) = PointF((x / density - tx) / zoom, (y / density - ty) / zoom)

    fun center() = world(width / 2f, height / 2f)

    fun reset() {
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
        val b = contentBounds(store.page.items)
        zoom =
            min(width / density / (b.width() + 90), height / density / (b.height() + 90))
                .coerceIn(.15f, 6f)
        tx = width / density / 2 - b.centerX() * zoom
        ty = height / density / 2 - b.centerY() * zoom
        invalidate()
    }

    fun insert(o: Item) {
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
        store.edit { store.page.items.removeAll { it.id in selected && !it.locked } }
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
    private fun transformCanvas(c:Canvas) { c.scale(density,density);c.translate(tx,ty);c.scale(zoom,zoom) }
    override fun onDraw(c:Canvas) {
        backing?.let { bitmap ->
            val target=Canvas(bitmap);target.save();transformCanvas(target)
            val page=store.page
            if(backingDirty || cachedPage!==page || cachedCount>page.items.size) {
                val l=world(0f,0f);val r=world(width.toFloat(),height.toFloat())
                renderer.background(target,page,RectF(l.x,l.y,r.x,r.y))
                cachedCount=0;cachedPage=page;backingDirty=false;cacheRebuilds++
            }
            for(i in cachedCount until page.items.size)renderer.draw(target,page.items[i])
            cachedCount=page.items.size;target.restore();c.drawBitmap(bitmap,0f,0f,null)
        }
        c.save();transformCanvas(c)
        live.values.forEach { renderer.draw(c,it) }
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
        c.restore()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (tool == "pan") {
            navigate(e)
            return true
        }
        val id = e.getPointerId(e.actionIndex)
        val at = world(e.getX(e.actionIndex), e.getY(e.actionIndex))
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
                if (tool == "erase" || behavior == "erase") {
                    checkpoint()
                    erase(at,eraseLast[id]?:at);eraseLast[id]=at
                } else if (tool == "select" && id == primary) {
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
                            store.page.items.asReversed().firstOrNull {
                                it.hit(at.x, at.y, 9 / zoom)
                            }
                        if (hit == null) {
                            selected.clear()
                            mode = "box"
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
                    live[id] =
                        Item(
                            w = 1f,
                            h = 1f,
                            color = if(tool=="highlight") inkColor else profile.color(e.getToolType(e.actionIndex),e.getTouchMajor(e.actionIndex),inkColor),
                            width = if(tool=="highlight") inkWidth*4 else profile.width(e.getToolType(e.actionIndex),e.getTouchMajor(e.actionIndex),inkWidth),
                            shape = if(tool=="highlight") "marker" else penStyle,
                            alpha = if (tool == "highlight") 75 else 255,
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
                    val point = world(e.getX(i), e.getY(i))
                    if (actions[pid] == "reject") continue
                    if (tool == "erase" || actions[pid] == "erase") {
                        checkpoint()
                        erase(point,eraseLast[pid]?:point);eraseLast[pid]=point
                    } else if (tool == "select" && pid == primary) {
                        if (mode == "box")
                            box =
                                RectF(
                                    min(anchor.x, point.x),
                                    min(anchor.y, point.y),
                                    max(anchor.x, point.x),
                                    max(anchor.y, point.y),
                                )
                        else if(hypot(point.x-anchor.x,point.y-anchor.y)>6/zoom) {
                            val pdf=chosen().singleOrNull()?.takeIf{it.kind=="pdf"}
                            if(mode=="pdf" || (mode=="move" && pdf!=null && abs(point.y-anchor.y)>abs(point.x-anchor.x)*1.5f))mode="pdf"
                            else{checkpoint();transform(point)}
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
                                    val q = world(e.getHistoricalX(i, j), e.getHistoricalY(i, j))
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
                    if(tool=="smart" && smartMode!="shape" && SmartSelection.isLoop(o)){
                        val enclosed=SmartSelection.enclosed(o,store.page.items)
                        if(enclosed.isNotEmpty()){post{onSmart(enclosed)}}else {checkpoint();store.page.items.add(o)}
                    }else if(tool=="smart" && smartMode=="shape"){
                        val converted=ShapeRecognition.convert(o)
                        store.page.items.add(converted?:o)
                    }else {checkpoint();store.page.items.add(o)}
                }
                actions.remove(id);eraseLast.remove(id)
                if (id == primary) {
                    box?.let { rect ->
                        selected.addAll(
                            store.page.items
                                .filter { rect.contains(it.x + it.w / 2, it.y + it.h / 2) }
                                .map { it.id }
                        )
                    }
                    box = null
                    if(tool=="select"){
                        if(mode=="pdf" && abs(at.y-anchor.y)>35/zoom){val pdf=chosen().singleOrNull();if(pdf!=null)store.edit{pdf.pdfPage=(pdf.pdfPage+if(at.y<anchor.y)1 else -1).coerceIn(0,pdf.pageCount-1)}}
                        if(mode=="move" && tappedSelected && hypot(at.x-anchor.x,at.y-anchor.y)<6/zoom)post{onObjectActions()}
                        onSelection()
                    }
                }
                if (e.actionMasked == MotionEvent.ACTION_UP) {
                    if(changed) { isCommitting=true;store.changed();isCommitting=false }
                    changed = false
                    mode = ""
                    performClick()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                live.clear()
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
                    if(original.size==1 && base.kind=="shape" && !Shapes.uniform(base.shape)){
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

    private fun erase(q: PointF,from:PointF=q) {
        backingDirty=true
        val radius=eraserRadius/zoom
        if(eraserMode=="area")store.page.items.filter{eraseObjects||it.kind=="ink"}.forEach{Erasing.cut(it,from,q,radius)}
        else {
            val steps=ceil(hypot(q.x-from.x,q.y-from.y)/max(radius*.5f,1f)).toInt().coerceIn(1,20000)
            store.page.items.removeAll { o -> !o.locked && (eraseObjects||o.kind=="ink") && (0..steps).any { i ->
                val t=i.toFloat()/steps;o.hit(from.x+(q.x-from.x)*t,from.y+(q.y-from.y)*t,radius)
            } }
        }
        selected.retainAll(store.page.items.map { it.id }.toSet())
    }

    override fun onGenericMotionEvent(event:MotionEvent):Boolean {
        if(tool=="select" && event.action==MotionEvent.ACTION_SCROLL){
            val o=chosen().singleOrNull()?.takeIf{it.kind=="pdf"}?:return super.onGenericMotionEvent(event)
            val delta=event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if(delta!=0f){store.edit{o.pdfPage=(o.pdfPage+if(delta<0)1 else -1).coerceIn(0,o.pageCount-1)};return true}
        }
        return super.onGenericMotionEvent(event)
    }

    private fun navigate(e: MotionEvent) {
        backingDirty=true
        val x = (0 until e.pointerCount).sumOf { e.getX(it).toDouble() }.toFloat() / e.pointerCount
        val y = (0 until e.pointerCount).sumOf { e.getY(it).toDouble() }.toFloat() / e.pointerCount
        val newSpan =
            if (e.pointerCount > 1) hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1)) else 0f
        if (e.actionMasked == MotionEvent.ACTION_MOVE) {
            val before = world(x, y)
            if (span > 0 && newSpan > 0) {
                zoom = (zoom * newSpan / span).coerceIn(.15f, 6f)
                tx = x / density - before.x * zoom
                ty = y / density - before.y * zoom
            }
            tx += (x - lastFocus.x) / density
            ty += (y - lastFocus.y) / density
        } else span = 0f
        span = if (e.actionMasked == MotionEvent.ACTION_MOVE) newSpan else 0f
        lastFocus = PointF(x, y)
        invalidate()
        if (e.actionMasked == MotionEvent.ACTION_UP) performClick()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
