package com.vuravision.classroom

import android.graphics.*
import kotlin.math.*

/** Object-local closed boundaries. Never flood the screen or page background. */
object ShapeFill {
    val closedShapes=Shapes.keys.toSet()-setOf("line","arrow","double_arrow","axes","cube","cone","cylinder")
    private data class Cached(val points:List<Point>,val cuts:List<EraseCut>,val kind:String,val shape:String,val w:Float,val h:Float,val inkW:Float,val inkH:Float,val runs:List<FillRun>?,val path:Path)
    private val paths=object:android.util.LruCache<String,Cached>(16*1024*1024){
        override fun sizeOf(key:String,value:Cached)=1000+value.points.size*16+value.runs.orEmpty().size*32
    }
    fun path(o:Item):Path? {
        paths.get(o.id)?.takeIf{it.points===o.points && it.cuts===o.cuts && it.kind==o.kind && it.shape==o.shape && it.w==o.w && it.h==o.h && it.inkW==o.inkW && it.inkH==o.inkH && it.runs===o.fillRuns}?.let{return it.path}
        val result=build(o)?:return null
        paths.put(o.id,Cached(o.points,o.cuts,o.kind,o.shape,o.w,o.h,o.inkW,o.inkH,o.fillRuns,result));return result
    }
    private fun build(o:Item):Path? {
        if(o.shape=="region_fill" && o.fillRuns.orEmpty().isNotEmpty())return Path().apply{
            o.fillRuns.orEmpty().forEach{r->addRect(r.x*o.w/o.inkW,r.y*o.h/o.inkH,r.end*o.w/o.inkW,(r.y+r.height)*o.h/o.inkH,Path.Direction.CW)}
        }
        if(o.kind=="ink") {
            if(o.cuts.isNotEmpty() || o.points.size<4)return null
            val a=o.points.first();val b=o.points.last()
            val diagonal=hypot(o.inkW,o.inkH)
            // A small endpoint gap is accepted, not a mostly-open loop.
            if(diagonal<6f || hypot(a.x-b.x,a.y-b.y)>min(diagonal*.12f,max(o.width*2.5f,diagonal*.04f)))return null
            return Path().apply{o.points.forEachIndexed{i,p->val x=p.x*o.w/o.inkW;val y=p.y*o.h/o.inkH;if(i==0)moveTo(x,y)else lineTo(x,y)};close()}
        }
        if(o.kind!="shape" || o.shape !in closedShapes)return null
        return shapePath(o.shape,o.w,o.h)
    }
    private fun shapePath(key:String,w:Float,h:Float):Path {
        val p=Path()
        fun polygon(vararg xy:Float){p.moveTo(xy[0]*w,xy[1]*h);for(i in 2 until xy.size step 2)p.lineTo(xy[i]*w,xy[i+1]*h);p.close()}
        fun regular(n:Int,star:Boolean=false){repeat(if(star)n*2 else n){i->val a=-PI/2+2*PI*i/(if(star)n*2 else n);val r=if(star&&i%2==1).22 else .5;val x=(w*(.5+r*cos(a))).toFloat();val y=(h*(.5+r*sin(a))).toFloat();if(i==0)p.moveTo(x,y)else p.lineTo(x,y)};p.close()}
        when(key){
            "circle","ellipse"->p.addOval(0f,0f,w,h,Path.Direction.CW)
            "rounded_rectangle"->p.addRoundRect(0f,0f,w,h,min(w,h)*.15f,min(w,h)*.15f,Path.Direction.CW)
            "triangle"->polygon(.5f,0f,1f,1f,0f,1f)
            "right_triangle"->polygon(0f,0f,1f,1f,0f,1f)
            "diamond"->polygon(.5f,0f,1f,.5f,.5f,1f,0f,.5f)
            "pentagon"->regular(5);"hexagon"->regular(6);"octagon"->regular(8);"star"->regular(5,true)
            "trapezoid"->polygon(.25f,0f,.75f,0f,1f,1f,0f,1f)
            "parallelogram"->polygon(.25f,0f,1f,0f,.75f,1f,0f,1f)
            "cross"->polygon(.35f,0f,.65f,0f,.65f,.35f,1f,.35f,1f,.65f,.65f,.65f,.65f,1f,.35f,1f,.35f,.65f,0f,.65f,0f,.35f,.35f,.35f)
            "speech"->polygon(0f,0f,1f,0f,1f,.75f,.4f,.75f,.15f,1f,.15f,.75f,0f,.75f)
            "heart"->{p.moveTo(w*.5f,h);p.cubicTo(-w*.5f,h*.35f,w*.15f,-h*.4f,w*.5f,h*.2f);p.cubicTo(w*.85f,-h*.4f,w*1.5f,h*.35f,w*.5f,h);p.close()}
            else->p.addRect(0f,0f,w,h,Path.Direction.CW)
        }
        return p
    }
    fun contains(o:Item,x:Float,y:Float):Boolean {
        val (lx,ly)=o.local(x,y)
        if(lx<0 || ly<0 || lx>o.w || ly>o.h)return false
        val source=path(o)?:return false
        if(o.cuts.any{it.contains(lx,ly,o.w,o.h)})return false
        // Scale a bounded integer region independently of item size and board zoom.
        val scale=1024f/max(o.w,o.h)
        val path=Path(source);path.transform(Matrix().apply{setScale(scale,scale)})
        val region=Region();region.setPath(path,Region(0,0,ceil(o.w*scale).toInt()+1,ceil(o.h*scale).toInt()+1))
        return region.contains((lx*scale).toInt(),(ly*scale).toInt())
    }
}
