package com.vuravision.classroom

import android.graphics.*
import android.text.*
import kotlin.math.*

fun itemBounds(item: Item): RectF {
    val corners =
        listOf(
            item.global(0f, 0f),
            item.global(item.w, 0f),
            item.global(item.w, item.h),
            item.global(0f, item.h),
        )
    return RectF(
        corners.minOf { it.first },
        corners.minOf { it.second },
        corners.maxOf { it.first },
        corners.maxOf { it.second },
    )
}

fun contentBounds(items: List<Item>): RectF {
    if (items.isEmpty()) return RectF(0f, 0f, 1000f, 600f)
    val b = itemBounds(items[0])
    items.drop(1).forEach { b.union(itemBounds(it)) }
    return b
}

class Renderer(private val media: Media) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private class InkPath(val owner:Item) {
        val source=owner.points;val path=Path();var count=0
        fun update() {
            if(count==0 && source.isNotEmpty()) { path.moveTo(source[0].x,source[0].y);count=1 }
            while(count<source.size) { val v=source[count++];path.lineTo(v.x,v.y) }
        }
    }
    private val inkPaths=object:android.util.LruCache<String,InkPath>(512) {}
    fun forgetInk(id:String) { inkPaths.remove(id) }

    private val functions = object : android.util.LruCache<String, (Double) -> Double>(32) {}

    fun background(c: Canvas, page: Page, area: RectF) {
        c.drawColor(if (page.background == "dark") 0xff172a36.toInt() else Color.WHITE)
        if (page.background !in listOf("dots", "grid", "ruled")) return
        p.color = 0xffe4eaed.toInt()
        p.strokeWidth = 1f
        p.style = Paint.Style.FILL
        val step = 32f
        val left = floor(area.left / step).toInt()
        val right = ceil(area.right / step).toInt()
        val top = floor(area.top / step).toInt()
        val bottom = ceil(area.bottom / step).toInt()
        if ((right - left).toLong() * (bottom - top) > 400000) return
        for (y in top..bottom) if (page.background == "dots")
            for (x in left..right) c.drawCircle(x * step, y * step, 1.1f, p)
        else c.drawLine(area.left, y * step, area.right, y * step, p)
        if (page.background == "grid")
            for (x in left..right) c.drawLine(x * step, area.top, x * step, area.bottom, p)
    }

    fun draw(c: Canvas, o: Item, sync: Boolean = false) {
        c.save()
        c.translate(o.x, o.y)
        c.rotate(o.rotation, o.w / 2, o.h / 2)
        Erasing.clip(c,o)
        p.reset()
        p.isAntiAlias = true
        p.color = o.color
        p.alpha = o.alpha
        p.strokeWidth = o.width
        p.strokeCap = if(o.shape=="marker") Paint.Cap.SQUARE else Paint.Cap.ROUND
        if(o.kind=="ink" && o.shape=="marker" && o.alpha==255)p.strokeWidth=o.width*1.8f
        if(o.shape=="dashed") p.pathEffect=DashPathEffect(floatArrayOf(o.width*3,o.width*2),0f)
        p.strokeJoin = Paint.Join.ROUND
        when (o.kind) {
            "ink" -> {
                c.save()
                c.scale(o.w / o.inkW, o.h / o.inkH)
                p.style = Paint.Style.STROKE
                val entry=inkPaths.get(o.id)?.takeIf { it.owner===o && it.source===o.points && it.count<=o.points.size }
                    ?: InkPath(o).also { inkPaths.put(o.id,it) }
                entry.update()
                if(o.points.size==1) { p.style=Paint.Style.FILL;c.drawCircle(o.points[0].x,o.points[0].y,o.width/2,p) }
                else c.drawPath(entry.path,p)
                c.restore()
            }
            "text",
            "sticky" -> {
                if (o.kind == "sticky") {
                    p.color = o.noteColor
                    p.style = Paint.Style.FILL
                    c.drawRoundRect(0f, 0f, o.w, o.h, 12f, 12f, p)
                }
                c.save()
                c.clipRect(0f, 0f, o.w, o.h)
                c.translate(10f, 8f)
                TextLayout.layout(o).draw(c)
                c.restore()
            }
            "shape" -> {
                if(o.shape in GeometryTools.keys){GeometryTools.draw(c,o);c.restore();return}
                p.style = Paint.Style.STROKE
                if(o.flipX && o.shape in listOf("line","arrow","double_arrow")) { c.translate(o.w,0f);c.scale(-1f,1f) }
                if(o.flipY && o.shape in listOf("line","arrow","double_arrow")) { c.translate(0f,o.h);c.scale(1f,-1f) }
                Shapes.draw(c,o.shape,o.w,o.h,p)
            }

            "image",
            "pdf" -> {
                p.color = Color.WHITE
                p.style = Paint.Style.FILL
                c.drawRect(0f, 0f, o.w, o.h, p)
                val b = media.image(o, sync)
                if (b != null) {
                    p.isFilterBitmap = true
                    c.drawBitmap(b, null, RectF(0f, 0f, o.w, o.h), p)
                } else {
                    p.color = 0xffdce5eb.toInt()
                    c.drawRect(8f, 8f, o.w - 8, o.h - 8, p)
                }
            }
            "graph" -> graph(c, o)
        }
        c.restore()
    }

    private fun graph(c: Canvas, o: Item) {
        p.color = 0xfff5f9fa.toInt()
        p.style = Paint.Style.FILL
        c.drawRoundRect(0f, 0f, o.w, o.h, 12f, 12f, p)
        c.save()
        c.clipRect(0f, 0f, o.w, o.h)
        val d = o.domain
        val sx = min(o.w,o.h) / (2 * d)
        val sy = sx
        p.color = 0xffdce5eb.toInt()
        p.strokeWidth = 1f
        val step = 10.0.pow(floor(log10(d.toDouble()))).toFloat()
        for (i in -10..10) {
            c.drawLine(o.w / 2 + i * step * sx, 0f, o.w / 2 + i * step * sx, o.h, p)
            c.drawLine(0f, o.h / 2 + i * step * sy, o.w, o.h / 2 + i * step * sy, p)
        }
        p.color = 0xff879aa6.toInt()
        c.drawLine(o.w / 2, 0f, o.w / 2, o.h, p)
        c.drawLine(0f, o.h / 2, o.w, o.h / 2, p)
        p.textSize=12f;p.style=Paint.Style.FILL;p.color=NAVY
        for(i in -10..10){
            if(i==0)continue
            val x=o.w/2+i*step*sx;val y=o.h/2-i*step*sy;val label=MathTools.format((i*step).toDouble())
            if(x in 18f..o.w-25f)c.drawText(label,x+3,o.h/2+16,p)
            if(y in 28f..o.h-16f)c.drawText(label,o.w/2+5,y-3,p)
        }
        c.drawText("0",o.w/2+4,o.h/2+16,p);c.drawText("x",o.w-16,o.h/2-8,p);c.drawText("y",o.w/2+8,16f,p)
        val colors = listOf(0xffe46d38.toInt(), 0xff218c91.toInt(), 0xff7465bc.toInt())
        o.text.split(';').take(3).forEachIndexed { index, s ->
            try {
                val f = functions.get(s) ?: MathTools().compile(s).also { functions.put(s, it) }
                val path = Path()
                var last = Float.NaN
                for (i in 0..600) {
                    val px = o.w * i / 600
                    val y =
                        try {
                            o.h / 2 - f(((px - o.w / 2) / sx).toDouble()).toFloat() * sy
                        } catch (_: ArithmeticException) {
                            Float.NaN
                        }
                    if (y.isFinite() && y in -o.h..o.h * 2) {
                        if (last.isFinite() && abs(last - y) < o.h) path.lineTo(px, y)
                        else path.moveTo(px, y)
                        last = y
                    } else last = Float.NaN
                }
                p.color = colors[index]
                p.strokeWidth = 2.5f
                p.style = Paint.Style.STROKE
                c.drawPath(path, p)
                p.style = Paint.Style.FILL
                p.textSize = 14f
                c.drawText(s, 12f, 20f + index * 18, p)
            } catch (_: IllegalArgumentException) {}
        }
        c.restore()
    }

    fun scene(c:Canvas,page:Page,sync:Boolean=false,region:RectF?=null,pane:Int?=null) {
        page.layers.filter { it.visible && it.opacity>0f }.forEach { layer ->
            val save=if(layer.opacity<1f)c.saveLayerAlpha(null,(layer.opacity*255).toInt())else c.save()
            page.items.filter { it.layerId==layer.id && (pane==null||it.pane==pane) }.forEach { o ->
                if(o.parentNode.isNotEmpty())page.items.firstOrNull{it.id==o.parentNode && it.pane==o.pane && page.layer(it).visible}?.let{parent->
                    p.reset();p.isAntiAlias=true;p.color=TEAL;p.strokeWidth=2f;p.style=Paint.Style.STROKE
                    c.drawLine(parent.x+parent.w/2,parent.y+parent.h/2,o.x+o.w/2,o.y+o.h/2,p)
                }
                if(region==null || RectF.intersects(itemBounds(o).apply { inset(-o.width*3f,-o.width*3f) },region))draw(c,o,sync)
            }
            c.restoreToCount(save)
        }
    }

    fun page(c: Canvas, page: Page, width: Int, height: Int, sync: Boolean) {
        if(page.panes.size>1){
            val n=page.panes.size;val columns=if(n==4)2 else n;val rows=if(n==4)2 else 1
            page.panes.forEachIndexed{i,p->
                val w=width/columns;val h=height/rows;c.save();c.translate((i%columns*w).toFloat(),(i/columns*h).toFloat());c.clipRect(0,0,w,h)
                val subset=page.copy(items=page.items.filter{it.pane==i}.toMutableList(),panes=mutableListOf(p.copy()))
                page(c,subset,w,h,sync);c.restore()
            };return
        }
        val b = contentBounds(page.visibleItems())
        b.inset(-40f, -40f)
        val scale = min(width / b.width(), height / b.height())
        val extraX = (width / scale - b.width()) / 2
        val extraY = (height / scale - b.height()) / 2
        c.save()
        c.scale(scale, scale)
        c.translate(-b.left + extraX, -b.top + extraY)
        background(
            c,
            page,
            RectF(b.left - extraX, b.top - extraY, b.right + extraX, b.bottom + extraY),
        )
        if(page.panes[0].background!=Color.WHITE)c.drawColor(page.panes[0].background)
        scene(c,page,sync)
        c.restore()
    }
}
