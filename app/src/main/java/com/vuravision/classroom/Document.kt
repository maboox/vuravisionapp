package com.vuravision.classroom

import java.util.UUID
import kotlin.math.*

fun newId(): String = UUID.randomUUID().toString()

/** Keep connections within a copied tree; detach references outside the selection. */
fun duplicateItems(items: List<Item>): List<Item> {
    val ids=items.associate{it.id to newId()}
    return items.map{it.deepCopy().apply{id=ids.getValue(it.id);parentNode=ids[it.parentNode].orEmpty()}}
}

data class FillRun(val y:Float=0f,val x:Float=0f,val end:Float=0f,val height:Float=1f)

data class PlotPoint(val x:Double=0.0,val y:Double=0.0)

data class Point(var x: Float = 0f, var y: Float = 0f, var t: Long = 0, var pressure: Float = 1f)

data class Item(
    var id: String = newId(),
    var kind: String = "ink",
    var x: Float = 0f,
    var y: Float = 0f,
    var w: Float = 200f,
    var h: Float = 100f,
    var rotation: Float = 0f,
    var flipY: Boolean = false,
    var flipX: Boolean = false,
    var color: Int = 0xff243746.toInt(),
    var width: Float = 4f,
    var alpha: Int = 255,
    var text: String = "",
    var asset: String = "",
    var shape: String = "rectangle",
    var pdfPage: Int = 0,
    var pageCount: Int = 1,
    var locked: Boolean = false,
    var inkW: Float = 1f,
    var inkH: Float = 1f,
    var points: MutableList<Point> = mutableListOf(),
    var domain: Float = 10f,
    var bold: Boolean = false,
    var textAlign: String = "start",
    var noteColor: Int = 0xffffe8b2.toInt(),
    var cuts: List<EraseCut> = emptyList(),
    var layerId: String = "base",
    var pane: Int = 0,
    var parentNode: String = "",
    var dashLength: Float = 0f,
    var dashGap: Float = 0f,
    var italic: Boolean = false,
    var fontFa: String? = null,
    var fontEn: String? = null,
    var fillColor: Int? = null,
    var fillAlpha: Int = 255,
    var geometryVersion: Int = 0,
    var geometryAngle: Float = 0f,
    var geometrySweep: Float = 0f,
    var selectedElement: Int = 0,
    var plotPoints: List<PlotPoint>? = emptyList(),
    var fillRuns: List<FillRun>? = emptyList(),

) {
    fun deepCopy() = copy(points = points.map { it.copy() }.toMutableList(), cuts = cuts.map { it.copy() }, plotPoints = plotPoints?.map { it.copy() })

    fun local(px: Float, py: Float): Pair<Float, Float> {
        val a = -rotation * PI / 180
        val dx = px - x - w / 2
        val dy = py - y - h / 2
        return (dx * cos(a) - dy * sin(a) + w / 2).toFloat() to
            (dx * sin(a) + dy * cos(a) + h / 2).toFloat()
    }

    fun global(px: Float, py: Float): Pair<Float, Float> {
        val a = rotation * PI / 180
        val dx = px - w / 2
        val dy = py - h / 2
        return (x + w / 2 + dx * cos(a) - dy * sin(a)).toFloat() to
            (y + h / 2 + dx * sin(a) + dy * cos(a)).toFloat()
    }

    fun hit(px: Float, py: Float, tolerance: Float = 10f): Boolean {
        val (a, b) = local(px, py)
        if (a < -tolerance || b < -tolerance || a > w + tolerance || b > h + tolerance) return false
        if(cuts.any { (it.pdfPage<0||it.pdfPage==pdfPage) && it.contains(a,b,w,h) })return false
        if (kind != "ink") return true
        if(shape=="region_fill")return ShapeFill.contains(this,px,py)
        val sx=w/inkW;val sy=h/inkH;val limit=tolerance+width/2
        var prev:Point?=null
        for(point in points) {
            if(hypot(a-point.x*sx,b-point.y*sy)<limit) return true
            prev?.let { if(distance(a,b,it.x*sx,it.y*sy,point.x*sx,point.y*sy)<limit)return true }
            prev=point
        }
        return false
    }
}

fun distance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax
    val dy = by - ay
    val n = dx * dx + dy * dy
    val t = if (n == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / n).coerceIn(0f, 1f)
    return hypot(px - ax - t * dx, py - ay - t * dy)
}

data class Layer(var id: String = newId(), var name: String = "Layer", var visible: Boolean = true, var locked: Boolean = false, var opacity: Float = 1f)
data class Pane(var color:Int=0xff20194f.toInt(),var background:Int=-1,var zoom:Float=1f,var tx:Float=0f,var ty:Float=0f,var penWidth:Float=4f,var penStyle:String="round",var dashLength:Float=12f,var dashGap:Float=8f)

data class Page(
    var id: String = newId(),
    var background: String = "dots",
    var items: MutableList<Item> = mutableListOf(),
    var layers: MutableList<Layer> = mutableListOf(Layer(id="base", name="Layer 1")),
    var activeLayerId: String = "base",
    var panes: MutableList<Pane> = mutableListOf(Pane()),
) {
    fun layer(item: Item) = layers.first { it.id == item.layerId }
    fun visibleItems() = layers.filter { it.visible && it.opacity > 0f }.flatMap { l -> items.filter { it.layerId == l.id } }
    fun editable(item: Item) = layer(item).let { it.visible && !it.locked && it.opacity > 0f }
    fun canDraw() = layers.first { it.id == activeLayerId }.let { it.visible && !it.locked && it.opacity > 0f }
}

data class Lesson(
    var schema: Int = 3,
    var title: String = "",
    var current: Int = 0,
    var pages: MutableList<Page> = mutableListOf(Page()),
    var pdf: PdfWorkspaceState? = null,
    var customColors: MutableList<Int>? = mutableListOf(),
) {
    // Committed point lists are immutable; Store.edit detaches them before edits.
    fun copyForSave(): Lesson = copy(customColors=customColors?.toMutableList(),pdf=pdf?.copyForSave(),pages=pages.map{p->p.copy(items=p.items.map{it.copy()}.toMutableList(),layers=p.layers.map{it.copy()}.toMutableList(),panes=p.panes.map{it.copy()}.toMutableList())}.toMutableList())
    fun copyDeep(): Lesson =
        copy(
            pdf = pdf?.deepCopy(),
            customColors = customColors?.toMutableList(),
            pages =
                pages
                    .map { it.copy(items = it.items.map { v -> v.deepCopy() }.toMutableList(), layers = it.layers.map { l -> l.copy() }.toMutableList(),panes=it.panes.map{p->p.copy()}.toMutableList()) }
                    .toMutableList()
        )

    fun validate() {
        require(schema in 2..3) { "Unsupported lesson version" }
        if (schema == 2) {
            pages.forEach { page ->
                page.layers = mutableListOf(Layer(id="base", name="Layer 1"))
                page.activeLayerId = "base"
                page.items.forEach { it.layerId = "base" }
            }
            schema = 3
        }
        require(pages.size in 1..200 && current in pages.indices)
        customColors = (customColors ?: mutableListOf()).distinct().take(40).toMutableList()
        pdf?.validate()
        require(pages.sumOf { it.items.size } <= 20000)
        var points = 0
        pages.forEach { page ->
            require(page.panes.size in 1..4)
            require(page.panes.all{it.penWidth.isFinite() && it.penWidth in .1f..80f && it.penStyle in PenStyles.keys && it.dashLength.isFinite() && it.dashLength in 1f..80f && it.dashGap.isFinite() && it.dashGap in 1f..80f})
            require(page.panes.all{it.zoom.isFinite() && it.zoom in .0001f..100000f && it.tx.isFinite() && it.ty.isFinite()})
            require(page.items.all{it.pane in page.panes.indices})
            require(page.layers.size in 1..100)
            require(page.layers.map { it.id }.distinct().size == page.layers.size)
            require(page.layers.all { it.id.isNotBlank() && it.name.length <= 200 && it.opacity.isFinite() && it.opacity in 0f..1f })
            require(page.layers.any { it.id == page.activeLayerId })
            require(page.items.all { o -> page.layers.any { it.id == o.layerId } })
            require(page.items.map { it.id }.distinct().size == page.items.size)
            page.items.forEach { o ->
                require(o.kind in setOf("ink", "text", "sticky", "shape", "image", "pdf", "graph", "periodic"))
                require(
                    listOf(o.x, o.y, o.w, o.h, o.rotation, o.width, o.inkW, o.inkH, o.domain).all {
                        it.isFinite()
                    }
                )
                require(o.w in .01f..100000f && o.h in .01f..100000f && o.inkW > 0 && o.inkH > 0)
                require(o.width in .1f..200f)
                require(o.dashLength.isFinite() && o.dashGap.isFinite() && o.dashLength in 0f..1000f && o.dashGap in 0f..1000f)
                require(o.pageCount > 0 && o.pdfPage in 0 until o.pageCount)
                require(o.domain in .1f..1000f)
                require(o.text.length <= 100000)
                require(listOf(o.fontFa,o.fontEn).all{it==null || it.matches(Regex("[a-z0-9_]{1,40}"))})
                require(o.asset.isEmpty() || o.asset.matches(Regex("[a-zA-Z0-9._-]+")))
                require(o.cuts.size <= 100000)
                require(o.cuts.all { c -> listOf(c.ax,c.ay,c.bx,c.by,c.radius,c.basisW,c.basisH).all { it.isFinite() } && c.radius>0 && c.basisW>0 && c.basisH>0 })
                require(o.fillRuns.orEmpty().size<=50000 && o.fillRuns.orEmpty().all{r->listOf(r.x,r.y,r.end,r.height).all{it.isFinite()} && r.x>=0 && r.y>=0 && r.end>=r.x && r.height>0})
                require(o.fillAlpha in 0..255 && o.geometryAngle.isFinite() && o.geometrySweep.isFinite() && o.geometryVersion in 0..1 && o.selectedElement in 0..118)
                require(o.plotPoints.orEmpty().size <= 1000 && o.plotPoints.orEmpty().all{it.x.isFinite() && it.y.isFinite() && abs(it.x)<=100000 && abs(it.y)<=100000})
                require(o.textAlign in listOf("start","center","end"))
                points += o.points.size+o.fillRuns.orEmpty().size
                require(points <= 1000000)
                require(o.points.all { it.x.isFinite() && it.y.isFinite() })
            }
        }
    }
}

class Store(var lesson: Lesson = Lesson()) {
    private val past = ArrayDeque<Lesson>()
    private val future = ArrayDeque<Lesson>()
    var changed: () -> Unit = {}
    val page
        get() = lesson.pages[lesson.current]

    // Point samples are shared in history and detached before arbitrary edits.
    private fun snapshot() = lesson.copy(customColors=lesson.customColors?.toMutableList(),pages = lesson.pages.map { page ->
        page.copy(items = page.items.map { it.copy() }.toMutableList(), layers = page.layers.map { it.copy() }.toMutableList(),panes=page.panes.map{it.copy()}.toMutableList())
    }.toMutableList())
    private var historyBefore:Pair<List<Lesson>,List<Lesson>>?=null
    var isCanceling=false;private set
    fun cancelCheckpoint(){
        val before=historyBefore?:return
        val saved=past.lastOrNull()?:return
        lesson=saved;past.clear();past.addAll(before.first);future.clear();future.addAll(before.second);historyBefore=null
        isCanceling=true;try{changed()}finally{isCanceling=false}
    }
    fun checkpoint() {
        historyBefore=past.toList() to future.toList()
        past.addLast(snapshot())
        while (past.size > 30) past.removeFirst()
        future.clear()
    }

    fun edit(action: () -> Unit) {
        checkpoint()
        lesson.pages.forEach { page -> page.items.forEach { o -> if(o.points.isNotEmpty()) o.points=o.points.map { it.copy() }.toMutableList() } }
        action()
        changed()
    }

    /** Only metadata or immutable-list replacements; no copies of existing ink samples. */
    fun editMetadata(action:()->Unit){checkpoint();action();changed()}
    val undoCount get()=past.size
    val redoCount get()=future.size
    /** Rebuild only once per scrub update, even when crossing many history steps. */
    fun seekHistory(position:Int){
        val target=position.coerceIn(0,past.size+future.size)
        if(target==past.size)return
        historyBefore=null
        while(past.size>target){future.addLast(snapshot());lesson=past.removeLast()}
        while(past.size<target){past.addLast(snapshot());lesson=future.removeLast()}
        changed()
    }
    fun undo() {
        historyBefore=null
        if (past.isNotEmpty()) {
            future.addLast(snapshot())
            lesson = past.removeLast()
            changed()
        }
    }

    fun redo() {
        historyBefore=null
        if (future.isNotEmpty()) {
            past.addLast(snapshot())
            lesson = future.removeLast()
            changed()
        }
    }

    fun replace(doc: Lesson) {
        historyBefore=null
        doc.validate()
        lesson = doc
        past.clear()
        future.clear()
        changed()
    }
}

data class TouchProfile(
    var calibrated: Boolean = false,
    var thin: Float = 5f,
    var palm: Float = 50f,
    var palmErase: Boolean = false,
    var multiTouch: Boolean = true,
    var thickWidth: Float = 10f,
    var thickColor: Int = 0xffe45756.toInt(),
    var thickStyle: String = "round",
) {
    fun classify(tool: Int, major: Float) =
        when {
            tool == 4 -> "eraser"
            tool == 2 && (!calibrated || major <= 0) -> "stylus"
            !calibrated || major <= 0 -> "unknown"
            major >= palm -> "palm"
            major <= thin -> "thin"
            else -> "finger / thick tip"
        }

    fun style(tool:Int, major:Float, normal:String) = if(classify(tool,major)=="finger / thick tip") thickStyle else normal

    fun color(tool:Int, major:Float, normal:Int) = if(classify(tool,major)=="finger / thick tip") thickColor else normal

    fun width(tool:Int, major:Float, normal:Float) = if(classify(tool,major)=="finger / thick tip") thickWidth else normal

    fun action(tool: Int, major: Float) =
        when (classify(tool, major)) {
            "eraser" -> "erase"
            "palm" -> if (palmErase) "erase" else "reject"
            else -> "draw"
        }
}
