package com.vuravision.classroom

import java.util.UUID
import kotlin.math.*

fun newId(): String = UUID.randomUUID().toString()

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
) {
    fun deepCopy() = copy(points = points.map { it.copy() }.toMutableList())

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
        if (kind != "ink") return true
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

data class Page(
    var id: String = newId(),
    var background: String = "dots",
    var items: MutableList<Item> = mutableListOf(),
)

data class Lesson(
    var schema: Int = 2,
    var title: String = "",
    var current: Int = 0,
    var pages: MutableList<Page> = mutableListOf(Page()),
) {
    fun copyDeep() =
        copy(
            pages =
                pages
                    .map { it.copy(items = it.items.map { v -> v.deepCopy() }.toMutableList()) }
                    .toMutableList()
        )

    fun validate() {
        require(schema == 2) { "Unsupported lesson version" }
        require(pages.size in 1..200 && current in pages.indices)
        require(pages.sumOf { it.items.size } <= 20000)
        var points = 0
        pages.forEach { page ->
            require(page.items.map { it.id }.distinct().size == page.items.size)
            page.items.forEach { o ->
                require(o.kind in setOf("ink", "text", "sticky", "shape", "image", "pdf", "graph"))
                require(
                    listOf(o.x, o.y, o.w, o.h, o.rotation, o.width, o.inkW, o.inkH, o.domain).all {
                        it.isFinite()
                    }
                )
                require(o.w in .01f..100000f && o.h in .01f..100000f && o.inkW > 0 && o.inkH > 0)
                require(o.width in .1f..200f)
                require(o.pageCount > 0 && o.pdfPage in 0 until o.pageCount)
                require(o.domain in .1f..1000f)
                require(o.text.length <= 100000)
                require(o.asset.isEmpty() || o.asset.matches(Regex("[a-zA-Z0-9._-]+")))
                points += o.points.size
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
    private fun snapshot() = lesson.copy(pages = lesson.pages.map { page ->
        page.copy(items = page.items.map { it.copy() }.toMutableList())
    }.toMutableList())
    fun checkpoint() {
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

    fun undo() {
        if (past.isNotEmpty()) {
            future.addLast(snapshot())
            lesson = past.removeLast()
            changed()
        }
    }

    fun redo() {
        if (future.isNotEmpty()) {
            past.addLast(snapshot())
            lesson = future.removeLast()
            changed()
        }
    }

    fun replace(doc: Lesson) {
        doc.validate()
        lesson = doc
        past.clear()
        future.clear()
        changed()
    }
}

data class TouchProfile(
    var calibrated: Boolean = false,
    var thin: Float = 10f,
    var palm: Float = 50f,
    var palmErase: Boolean = false,
    var multiTouch: Boolean = true,
    var thickWidth: Float = 10f,
) {
    fun classify(tool: Int, major: Float) =
        when {
            tool == 4 -> "eraser"
            tool == 2 -> "stylus"
            !calibrated || major <= 0 -> "unknown"
            major >= palm -> "palm"
            major <= thin -> "thin"
            else -> "finger / thick tip"
        }

    fun width(tool:Int, major:Float, normal:Float) = if(classify(tool,major)=="finger / thick tip") thickWidth else normal

    fun action(tool: Int, major: Float) =
        when (classify(tool, major)) {
            "eraser" -> "erase"
            "palm" -> if (palmErase) "erase" else "reject"
            else -> "draw"
        }
}
