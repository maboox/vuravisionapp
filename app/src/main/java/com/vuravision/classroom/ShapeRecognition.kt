package com.vuravision.classroom

import kotlin.math.*

object ShapeRecognition {
    /** A stroke in progress contains world-space samples; normalize it before detection. */
    fun live(o:Item):Item? {
        if(o.points.size<3)return null
        val left=o.points.minOf{it.x};val top=o.points.minOf{it.y}
        val w=(o.points.maxOf{it.x}-left).coerceAtLeast(1f)
        val h=(o.points.maxOf{it.y}-top).coerceAtLeast(1f)
        val normalized=o.copy(x=left,y=top,w=w,h=h,inkW=w,inkH=h,
            points=o.points.map{it.copy(x=it.x-left,y=it.y-top)}.toMutableList())
        return convert(normalized)
    }
    fun convert(o:Item):Item? {
        if(o.points.size<3)return null
        val pts=SmartSelection.points(o)
        val kind=detectPoints(pts)
        if(kind=="unknown")return null
        if(kind=="line"){
            val a=pts.first();val b=pts.last()
            return Item(kind="shape",shape="line",x=min(a.first,b.first),y=min(a.second,b.second),
                w=abs(b.first-a.first).coerceAtLeast(1f),h=abs(b.second-a.second).coerceAtLeast(1f),
                flipX=b.first<a.first,flipY=b.second>=a.second,color=o.color,width=o.width)
        }
        // Open near-complete circles are intentional; polygons must actually close.
        val a=pts.first();val b=pts.last();val diagonal=hypot(o.w,o.h)
        if(kind !in listOf("circle","ellipse") && hypot(a.first-b.first,a.second-b.second)>max(16f,diagonal*.22f))return null
        return Item(kind="shape",shape=kind,x=o.x,y=o.y,w=o.w,
            h=if(kind in listOf("circle","square"))o.w else o.h,color=o.color,width=o.width)
    }
    fun detect(items:List<Item>)=detectPoints(items.flatMap{SmartSelection.points(it)})
    private fun detectPoints(pts:List<Pair<Float,Float>>):String {
        if(pts.size<3)return "unknown"
        val l=pts.minOf{it.first};val r=pts.maxOf{it.first};val t=pts.minOf{it.second};val b=pts.maxOf{it.second}
        val w=r-l;val h=b-t;val diag=hypot(w,h)
        if(diag<8f)return "unknown"
        val a=pts.first();val z=pts.last();val chord=hypot(a.first-z.first,a.second-z.second)
        val pathLength=pts.zipWithNext().sumOf{(p,q)->hypot(p.first-q.first,p.second-q.second).toDouble()}.toFloat()
        if(chord>diag*.35f && pathLength/chord<1.18f &&
            pts.all{distance(it.first,it.second,a.first,a.second,z.first,z.second)<max(5f,chord*.08f)})return "line"
        if(chord<=max(18f,diag*.23f)){
            val outline=if(chord<max(6f,diag*.07f))pts else pts+pts.first()
            val corners=simplify(outline,diag*.065f).toMutableList()
            if(corners.size>1 && hypot(corners.first().first-corners.last().first,corners.first().second-corners.last().second)<diag*.12f)corners.removeAt(corners.lastIndex)
            // A stroke may begin halfway along an edge: discard nearly straight split points.
            var removed=true
            while(removed && corners.size>3){removed=false
                for(i in corners.indices){val p=corners[(i+corners.size-1)%corners.size];val q=corners[i];val next=corners[(i+1)%corners.size]
                    if(distance(q.first,q.second,p.first,p.second,next.first,next.second)<diag*.07f){corners.removeAt(i);removed=true;break}
                }
            }
            if(corners.size==3)return "triangle"
            if(corners.size==4)return if(abs(w-h)/max(w,h)<.16f)"square"else"rectangle"
        }
        val cx=(l+r)/2;val cy=(t+b)/2
        val radii=pts.map{hypot((it.first-cx)/(w/2).coerceAtLeast(1f),(it.second-cy)/(h/2).coerceAtLeast(1f))}
        val radialError=radii.map{abs(it-1f)}.average()
        val sectors=pts.map{(x,y)->((atan2((y-cy).toDouble(),(x-cx).toDouble())+PI)*16/(2*PI)).toInt().coerceIn(0,15)}.distinct().size
        if(radialError<.115 && sectors>=13 && pathLength>diag*1.8f)
            return if(abs(w-h)/max(w,h)<.16f)"circle"else"ellipse"
        return "unknown"
    }
    private fun simplify(pts:List<Pair<Float,Float>>,epsilon:Float):List<Pair<Float,Float>>{
        if(pts.size<3)return pts
        val first=pts.first();val last=pts.last()
        var index=0;var far=0f
        for(i in 1 until pts.lastIndex){val d=distance(pts[i].first,pts[i].second,first.first,first.second,last.first,last.second)
            if(d>far){far=d;index=i}}
        if(far<=epsilon || index==0)return listOf(first,last)
        return simplify(pts.subList(0,index+1),epsilon).dropLast(1)+simplify(pts.subList(index,pts.size),epsilon)
    }
}
