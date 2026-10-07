package com.vuravision.classroom
import kotlin.math.*
object CoordinateTools {
    fun intersections(expressions:List<String>,domain:Double,vertices:List<PlotPoint> = emptyList(),connected:Boolean=false):List<PlotPoint>{
        require(domain.isFinite()&&domain in .1..1000.0&&expressions.size<=3)
        val functions=expressions.filter{it.isNotBlank()}.map{MathTools().compile(it)}
        val points=mutableListOf<PlotPoint>()
        for(i in functions.indices)for(j in i+1 until functions.size){
            val f=functions[i];val g=functions[j]
            fun difference(x:Double)=try{f(x)-g(x)}catch(_:Exception){Double.NaN}
            var last=-domain;var value=difference(last)
            for(k in 1..1200){val x=-domain+2*domain*k/1200;val next=difference(x)
                if(value.isFinite()&&next.isFinite() && ((value==0.0&&next!=0.0)||(next==0.0&&value!=0.0)||value*next<0)){
                    var a=last;var b=x
                    repeat(40){val mid=(a+b)/2;val v=difference(mid);if(!v.isFinite())return@repeat;if(difference(a)*v<=0)b=mid else a=mid}
                    val root=if(value==0.0)last else if(next==0.0)x else (a+b)/2;val y=try{f(root)}catch(_:Exception){Double.NaN}
                    val residual=difference(root)
                    if(y.isFinite()&&residual.isFinite()&&abs(residual)<1e-5 && points.none{hypot(it.x-root,it.y-y)<1e-4})points.add(PlotPoint(root,y))
                }
                last=x;value=next
            }
        }
        if(connected){
            val segments=vertices.zipWithNext()
            fun add(x:Double,y:Double){if(x.isFinite()&&y.isFinite()&&abs(x)<=domain&&points.none{hypot(it.x-x,it.y-y)<1e-4})points.add(PlotPoint(x,y))}
            for(i in segments.indices)for(j in i+2 until segments.size){
                val (a,b)=segments[i];val (c,d)=segments[j]
                val dx=b.x-a.x;val dy=b.y-a.y;val ex=d.x-c.x;val ey=d.y-c.y;val det=dx*ey-dy*ex
                if(abs(det)<1e-12)continue
                val t=((c.x-a.x)*ey-(c.y-a.y)*ex)/det;val u=((c.x-a.x)*dy-(c.y-a.y)*dx)/det
                if(t in 0.0..1.0&&u in 0.0..1.0)add(a.x+t*dx,a.y+t*dy)
            }
            for((a,b) in segments)for(f in functions){
                fun difference(t:Double):Double=try{val x=a.x+(b.x-a.x)*t;a.y+(b.y-a.y)*t-f(x)}catch(_:Exception){Double.NaN}
                var last=0.0;var value=difference(last)
                for(k in 1..256){val t=k/256.0;val next=difference(t)
                    if(value.isFinite()&&next.isFinite()&&((abs(value)<1e-9&&abs(next)>=1e-9)||(abs(next)<1e-9&&abs(value)>=1e-9)||value*next<0)){
                        var lo=last;var hi=t
                        repeat(40){val mid=(lo+hi)/2;if(difference(lo)*difference(mid)<=0)hi=mid else lo=mid}
                        val root=if(abs(value)<1e-9)last else if(abs(next)<1e-9)t else (lo+hi)/2
                        val x=a.x+(b.x-a.x)*root;val y=a.y+(b.y-a.y)*root
                        if(abs(difference(root))<1e-5)add(x,y)
                    };last=t;value=next
                }
            }
        }
        return points.take(100)
    }
}
object ShortcutCatalog {
    val keys=listOf("undo","redo","clear_page","pen","erase","select","pan","shape","fill","color","thicker","thinner","text","sticky","new_page","new_layer","layers","pages","background","fit","coordinates","graph","measure","periodic","ruler","set_square","protractor","compass","mind_map","smart","math","insert","image","pdf","timer","stopwatch","dice","scoreboard","files","new","open","save","recent","export_pdf","export_png","export_jpg","rename","lab","games","share","settings","help","language","fonts","google_search_settings","models","input_controls","page_background","calibration","ui_size","duplicate","delete","lock","unlock_all","front","back")
}
