package com.vuravision.classroom

import android.graphics.*
import android.text.*
import kotlin.math.*

fun itemBounds(item: Item): RectF {
    if(item.rotation%360f==0f)return RectF(item.x,item.y,item.x+item.w,item.y+item.h)
    val angle=item.rotation*PI/180
    val hw=(abs(cos(angle))*item.w+abs(sin(angle))*item.h).toFloat()/2
    val hh=(abs(sin(angle))*item.w+abs(cos(angle))*item.h).toFloat()/2
    val cx=item.x+item.w/2;val cy=item.y+item.h/2
    return RectF(cx-hw,cy-hh,cx+hw,cy+hh)
}

fun contentBounds(items: List<Item>): RectF {
    if (items.isEmpty()) return RectF(0f, 0f, 1000f, 600f)
    val b = itemBounds(items[0])
    items.drop(1).forEach { b.union(itemBounds(it)) }
    return b
}

class Renderer(private val media: Media) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pdfBaseId:String?=null
    private var pdfFrame:Bitmap?=null
    private var pdfFrameKey:String?=null
    fun retainPdfBase(id:String?){if(pdfBaseId!=id){releasePdfFrame();pdfBaseId=id}}
    fun releasePdfFrame(){pdfFrame=null;pdfFrameKey=null}
    private class InkPath(val owner:Item,val stride:Int=1) {
        val source=owner.points;val path=Path();var count=0
        fun update() {
            if(count==0 && source.isNotEmpty()) { path.moveTo(source[0].x,source[0].y);count=1 }
            while(count<source.size) { val index=count++;val v=source[index];if(index%stride==0 || index==source.lastIndex)path.lineTo(v.x,v.y) }
        }
    }
    private val inkPaths=object:android.util.LruCache<String,InkPath>(512) {}
    fun forgetInk(id:String) { listOf(1,2,4,8).forEach{inkPaths.remove(id+":"+it)} }

    private val functions = object : android.util.LruCache<String, (Double) -> Double>(32) {}

    companion object {
        fun backgroundStep(pixelsPerUnit:Float):Float{
            var step=32f
            while(step*pixelsPerUnit<12f && step<32768f)step*=2f
            return step
        }
    }
    fun background(c: Canvas, page: Page, area: RectF, fill:Int=page.panes.firstOrNull()?.background?:Color.WHITE) {
        val color=if(page.background=="dark" && fill==Color.WHITE)0xff172a36.toInt()else fill
        c.drawColor(color)
        if (page.background !in listOf("dots", "grid", "ruled", "hatch")) return
        p.color = if(android.graphics.Color.luminance(color)<.35)0x55ffffff else 0xffd8dce2.toInt()
        p.strokeWidth = 1f
        p.style = Paint.Style.FILL
        val matrix=Matrix();c.getMatrix(matrix);val transform=FloatArray(9);matrix.getValues(transform)
        val pixelsPerUnit=hypot(transform[0],transform[3]).coerceAtLeast(.0001f)
        val step=backgroundStep(pixelsPerUnit)
        val left = floor(area.left / step).toInt()
        val right = ceil(area.right / step).toInt()
        val top = floor(area.top / step).toInt()
        val bottom = ceil(area.bottom / step).toInt()
        if ((right - left).toLong() * (bottom - top) > 400000) return
        if(page.background=="hatch"){
            c.save();c.clipRect(area)
            val diagonals=ceil((area.height()+area.width())/step).toInt()
            for(i in 0..diagonals){val x=area.left-area.height()+i*step
                c.drawLine(x,area.top,x+area.height(),area.bottom,p)
            }
            c.restore();return
        }
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
        if(o.shape=="dashed") p.pathEffect=DashPathEffect(floatArrayOf(if(o.dashLength>0)o.dashLength else o.width*3,if(o.dashGap>0)o.dashGap else o.width*2),0f)
        p.strokeJoin = Paint.Join.ROUND
        when (o.kind) {
            "ink" -> {
                c.save()
                c.scale(o.w / o.inkW, o.h / o.inkH)
                p.style = Paint.Style.STROKE
                val matrix=Matrix();c.getMatrix(matrix);val values=FloatArray(9);matrix.getValues(values)
                val scale=hypot(values[0],values[3])
                val stride=if(sync)1 else if(scale<.25f)8 else if(scale<.5f)4 else if(scale<.8f)2 else 1
                val key=o.id+":"+stride
                val entry=inkPaths.get(key)?.takeIf { it.owner===o && it.source===o.points && it.count<=o.points.size }
                    ?: InkPath(o,stride).also { inkPaths.put(key,it) }
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
                val matrix=Matrix();c.getMatrix(matrix)
                val values=FloatArray(9);matrix.getValues(values)
                val edge=(maxOf(o.w,o.h)*kotlin.math.hypot(values[0],values[3])).toInt()
                val retain=!sync && o.kind=="pdf" && o.id==pdfBaseId
                val key=o.asset+":"+o.pdfPage
                if(retain && pdfFrameKey!=key){pdfFrame=null;pdfFrameKey=key}
                // Bound page-preview cost; exports still render at their requested resolution.
                val b = media.image(o, sync, if(retain)edge.coerceAtMost(1536)else edge, retained=if(retain)pdfFrame else null)
                if(retain && b!=null)pdfFrame=b
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

    fun scene(c:Canvas,page:Page,sync:Boolean=false,region:RectF?=null,pane:Int?=null,exclude:Set<String> = emptySet()) {
        val visibleLayers=page.layers.filter { it.visible && it.opacity>0f }
        // Draw all branches first: a parent may occur before its child in item order.
        val nodes=page.items.filter{it.kind=="sticky"&&it.shape=="mindnode"}.associateBy{it.id}
        if(nodes.isNotEmpty()){
            val layerOpacity=visibleLayers.associate{it.id to it.opacity}
            nodes.values.forEach{child->
                val parent=nodes[child.parentNode]?:return@forEach
                if(child.pane!=parent.pane || pane!=null&&child.pane!=pane)return@forEach
                val opacity=minOf(layerOpacity[child.layerId]?:0f,layerOpacity[parent.layerId]?:0f)
                if(opacity<=0f)return@forEach
                val x=parent.x+parent.w/2;val y=parent.y+parent.h/2
                val xx=child.x+child.w/2;val yy=child.y+child.h/2
                if(region!=null){
                    val bounds=RectF(minOf(x,xx),minOf(y,yy),maxOf(x,xx),maxOf(y,yy)).apply{inset(-3f,-3f)}
                    if(!RectF.intersects(bounds,region))return@forEach
                }
                p.reset();p.isAntiAlias=true;p.color=TEAL;p.alpha=(opacity*255).toInt();p.strokeWidth=2f;p.style=Paint.Style.STROKE
                c.drawLine(x,y,xx,yy,p)
            }
        }
        visibleLayers.forEach { layer ->
            val save=if(layer.opacity<1f)c.saveLayerAlpha(null,(layer.opacity*255).toInt())else c.save()
            page.items.filter { it.layerId==layer.id && (pane==null||it.pane==pane) && it.id !in exclude }.forEach { o ->
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
        scene(c,page,sync)
        c.restore()
    }
}
