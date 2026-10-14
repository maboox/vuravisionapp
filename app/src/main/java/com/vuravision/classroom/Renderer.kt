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

/** Page backgrounds the board draws, and the order they are offered in. */
object PagePatterns {
    val drawn=listOf("dots","grid","ruled","hatch","isometric","axes","staff","handwriting")
    val offered=listOf("plain")+drawn
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

    private data class CurveKey(val expression:String,val width:Float,val height:Float,val domain:Float)
    private val curves=object:android.util.LruCache<CurveKey,Path>(64){}
    var curveBuilds=0;private set

    companion object {
        fun backgroundStep(pixelsPerUnit:Float):Float{
            var step=32f
            while(step*pixelsPerUnit<12f && step<32768f)step*=2f
            return step
        }
    }
    fun background(c: Canvas, page: Page, area: RectF, fill:Int=page.panes.firstOrNull()?.background?:Color.WHITE,paneIndex:Int=0) {
        val pattern=page.pattern(paneIndex)
        val color=if(pattern=="dark" && fill==Color.WHITE)0xff172a36.toInt()else fill
        c.drawColor(color)
        if (pattern !in PagePatterns.drawn) return
        val dark=android.graphics.Color.luminance(color)<.35
        p.color = if(dark)0x55ffffff else 0xffd8dce2.toInt()
        if(pattern in listOf("staff","handwriting")){fixedLines(c,pattern,area,dark);return}
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
        if(pattern=="isometric"){
            // Triangular dot grid for 3D sketches: alternate rows shift by half a step, rows are √3/2 apart.
            val rowStep=step*.866f;val rowTop=floor(area.top/rowStep).toInt();val rowBottom=ceil(area.bottom/rowStep).toInt()
            if((right-left).toLong()*(rowBottom-rowTop)>400000)return
            for(y in rowTop..rowBottom)for(x in left..right)c.drawCircle(x*step+(if(y%2!=0)step/2 else 0f),y*rowStep,1.3f,p)
            return
        }
        if(pattern=="axes"){
            // Coordinate plane: light grid, every fifth line stronger, and the two axes through the origin.
            for(y in top..bottom){p.alpha=if(y%5==0)200 else 110;c.drawLine(area.left,y*step,area.right,y*step,p)}
            for(x in left..right){p.alpha=if(x%5==0)200 else 110;c.drawLine(x*step,area.top,x*step,area.bottom,p)}
            p.alpha=255;val axis=p.color;p.color=if(dark)0xccffffff.toInt() else 0xff8a8fa3.toInt();p.strokeWidth=2.2f
            c.drawLine(area.left,0f,area.right,0f,p);c.drawLine(0f,area.top,0f,area.bottom,p)
            p.strokeWidth=1f;p.color=axis;return
        }
        if(pattern=="hatch"){
            c.save();c.clipRect(area)
            val diagonals=ceil((area.height()+area.width())/step).toInt()
            for(i in 0..diagonals){val x=area.left-area.height()+i*step
                c.drawLine(x,area.top,x+area.height(),area.bottom,p)
            }
            c.restore();return
        }
        for (y in top..bottom) if (pattern == "dots")
            for (x in left..right) c.drawCircle(x * step, y * step, 1.1f, p)
        else c.drawLine(area.left, y * step, area.right, y * step, p)
        if (pattern == "grid")
            for (x in left..right) c.drawLine(x * step, area.top, x * step, area.bottom, p)
    }

    /** Patterns with a fixed size on the page (music staff, handwriting lines): they keep their meaning at any zoom. */
    private fun fixedLines(c:Canvas,pattern:String,area:RectF,dark:Boolean){
        val period=if(pattern=="staff")120f else 96f
        val first=floor(area.top/period).toInt();val last=ceil(area.bottom/period).toInt()
        if(last-first>2000)return
        val base=p.color;val strong=if(dark)0x99ffffff.toInt() else 0xffaab0c0.toInt()
        for(i in first..last){val y=i*period
            if(pattern=="staff"){p.color=strong;for(l in 0..4)c.drawLine(area.left,y+l*12f,area.right,y+l*12f,p)}
            else{
                // Four-line handwriting paper: top line, dashed middle, strong base line, light descender line.
                p.color=base;c.drawLine(area.left,y,area.right,y,p)
                p.color=strong;p.pathEffect=DashPathEffect(floatArrayOf(10f,8f),0f);c.drawLine(area.left,y+28f,area.right,y+28f,p);p.pathEffect=null
                p.strokeWidth=1.8f;c.drawLine(area.left,y+56f,area.right,y+56f,p);p.strokeWidth=1f
                p.color=base;c.drawLine(area.left,y+76f,area.right,y+76f,p)
            }
        }
        p.color=base
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
        p.strokeCap = if(o.shape in listOf("marker","highlight")) Paint.Cap.SQUARE else Paint.Cap.ROUND
        if(o.kind=="ink" && o.shape=="marker")p.strokeWidth=o.width*1.8f
        if(o.shape=="dashed") p.pathEffect=DashPathEffect(floatArrayOf(if(o.dashLength>0)o.dashLength else o.width*3,if(o.dashGap>0)o.dashGap else o.width*2),0f)
        p.strokeJoin = Paint.Join.ROUND
        if(o.kind=="ink")PenStyles.apply(p,o)
        o.fillColor?.let{color->ShapeFill.path(o)?.let{path->val fill=Paint(Paint.ANTI_ALIAS_FLAG).apply{this.color=color;alpha=o.fillAlpha*o.alpha/255;style=Paint.Style.FILL};c.drawPath(path,fill)}}
        when (o.kind) {
            "ink" -> {
                if(o.shape=="region_fill"){c.restore();return}
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
                if(o.kind=="sticky" && o.shape=="element_card")PeriodicTable.drawCard(c,o,media.context)
                c.translate(10f, if(o.shape=="element_card")PeriodicTable.cardHeader(o) else 8f)
                TextLayout.layout(o).draw(c)
                c.restore()
            }
            "shape" -> {
                if(o.shape in GeometryTools.keys){GeometryTools.draw(c,o,media.context.getSharedPreferences("vura",0).getFloat("pixelsPerCm",0f));c.restore();return}
                if(o.shape=="table"){Tables.draw(c,o,p);c.restore();return}
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
            "periodic" -> PeriodicTable.draw(c,o,media.context)
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
        p.textSize=12f;p.style=Paint.Style.FILL;p.color=INK_NAVY
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
                val curveKey=CurveKey(s,o.w,o.h,o.domain)
                val path=curves.get(curveKey)?:run{
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
                    curveBuilds++;curves.put(curveKey,path);path
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
        if(o.connectPlotPoints && o.plotPoints.orEmpty().size>1){
            val path=Path();o.plotPoints.orEmpty().forEachIndexed{i,v->val x=o.w/2+(v.x*sx).toFloat();val y=o.h/2-(v.y*sy).toFloat();if(i==0)path.moveTo(x,y)else path.lineTo(x,y)}
            p.color=INK_TEAL;p.style=Paint.Style.STROKE;p.strokeWidth=2.5f;c.drawPath(path,p)
        }
        o.plotIntersections.orEmpty().forEach{v->val x=o.w/2+(v.x*sx).toFloat();val y=o.h/2-(v.y*sy).toFloat()
            if(x in 0f..o.w&&y in 0f..o.h){p.color=INK_ORANGE;p.style=Paint.Style.STROKE;p.strokeWidth=2f;c.drawCircle(x,y,6f,p);c.drawLine(x-4,y-4,x+4,y+4,p);c.drawLine(x-4,y+4,x+4,y-4,p);p.style=Paint.Style.FILL;p.textSize=12f;c.drawText("(${DisplayNumbers.one(v.x)}, ${DisplayNumbers.one(v.y)})",x+8,y-8,p)}
        }
        o.plotPoints.orEmpty().forEach{point->val x=o.w/2+point.x*sx;val y=o.h/2-point.y*sy
            if(x>=0&&x<=o.w&&y>=0&&y<=o.h){p.color=INK_TEAL;p.style=Paint.Style.FILL;c.drawCircle(x.toFloat(),y.toFloat(),4f,p);p.textSize=12f;c.drawText("(${DisplayNumbers.one(point.x)}, ${DisplayNumbers.one(point.y)})",x.toFloat()+6,y.toFloat()-6,p)}
        }
        c.restore()
    }

    /** Connector arrows run edge to edge between the two objects' frames, under the objects, and follow every move. */
    private fun connectors(c:Canvas,items:List<Item>,region:RectF?){
        val sources=items.filter{!it.links.isNullOrBlank()};if(sources.isEmpty())return
        val byId=items.associateBy{it.id}
        fun edge(r:RectF,tx:Float,ty:Float):PointF{
            val dx=tx-r.centerX();val dy=ty-r.centerY()
            if(dx==0f&&dy==0f)return PointF(r.centerX(),r.centerY())
            val t=minOf(if(dx!=0f)(r.width()/2+8f)/kotlin.math.abs(dx)else Float.MAX_VALUE,if(dy!=0f)(r.height()/2+8f)/kotlin.math.abs(dy)else Float.MAX_VALUE)
            return PointF(r.centerX()+dx*t,r.centerY()+dy*t)
        }
        sources.forEach{from->from.linkIds().forEach{id->
            val to=byId[id]?:return@forEach
            if(to.pane!=from.pane)return@forEach
            val a=itemBounds(from);val b=itemBounds(to)
            if(RectF.intersects(a,b))return@forEach
            val start=edge(a,b.centerX(),b.centerY());val end=edge(b,a.centerX(),a.centerY())
            if(region!=null&&!RectF.intersects(RectF(minOf(start.x,end.x),minOf(start.y,end.y),maxOf(start.x,end.x),maxOf(start.y,end.y)).apply{inset(-20f,-20f)},region))return@forEach
            p.reset();p.isAntiAlias=true;p.color=CONNECTOR;p.strokeWidth=3f;p.style=Paint.Style.STROKE;p.strokeCap=Paint.Cap.ROUND
            c.drawLine(start.x,start.y,end.x,end.y,p)
            val angle=kotlin.math.atan2(end.y-start.y,end.x-start.x);val head=16f;val spread=.45f
            val path=android.graphics.Path().apply{moveTo(end.x,end.y)
                lineTo(end.x-head*kotlin.math.cos(angle-spread),end.y-head*kotlin.math.sin(angle-spread))
                lineTo(end.x-head*kotlin.math.cos(angle+spread),end.y-head*kotlin.math.sin(angle+spread));close()}
            p.style=Paint.Style.FILL;c.drawPath(path,p)
        }}
    }

    fun scene(c:Canvas,page:Page,sync:Boolean=false,region:RectF?=null,pane:Int?=null,exclude:Set<String> = emptySet()) {
        val visibleLayers=page.layers.filter { it.visible && it.opacity>0f }
        // Draw all branches first: a parent may occur before its child in item order.
        val grouped=page.items.filter{it.pane in page.visiblePaneIndices && (pane==null||it.pane==pane)}.groupBy{it.layerId}
        val nodes=visibleLayers.flatMap{grouped[it.id].orEmpty()}.filter{it.kind=="sticky"&&it.shape=="mindnode"}.associateBy{it.id}
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
                p.reset();p.isAntiAlias=true;p.color=INK_TEAL;p.alpha=(opacity*255).toInt();p.strokeWidth=2f;p.style=Paint.Style.STROKE
                c.drawLine(x,y,xx,yy,p)
            }
        }
        connectors(c,visibleLayers.flatMap{grouped[it.id].orEmpty()},region)
        visibleLayers.forEach { layer ->
            val save=if(layer.opacity<1f)c.saveLayerAlpha(null,(layer.opacity*255).toInt())else c.save()
            grouped[layer.id].orEmpty().forEach { o ->
                if(o.id in exclude)return@forEach
                if(region==null || RectF.intersects(itemBounds(o).apply { inset(-o.width*3f,-o.width*3f) },region))draw(c,o,sync)
            }
            c.restoreToCount(save)
        }
    }

    fun page(c: Canvas, page: Page, width: Int, height: Int, sync: Boolean) {
        if(page.visiblePaneCount>1){
            val n=page.visiblePaneCount;val columns=n;val rows=1
            page.panes.take(n).forEachIndexed{i,p->
                val w=width/columns;val h=height/rows;c.save();c.translate((i%columns*w).toFloat(),(i/columns*h).toFloat());c.clipRect(0,0,w,h)
                val subset=page.copy(background=page.pattern(i),items=page.items.filter{it.pane==i}.map{it.copy(pane=0)}.toMutableList(),panes=mutableListOf(p.copy()),paneCount=1,alternateCanvas=null)
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
