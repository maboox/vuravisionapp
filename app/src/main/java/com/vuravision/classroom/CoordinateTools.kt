package com.vuravision.classroom
import kotlin.math.*
object CoordinateTools {
    fun intersections(expressions:List<String>,domain:Double):List<PlotPoint>{
        require(domain.isFinite()&&domain in .1..1000.0&&expressions.size<=3)
        val functions=expressions.filter{it.isNotBlank()}.map{MathTools().compile(it)}
        val points=mutableListOf<PlotPoint>()
        for(i in functions.indices)for(j in i+1 until functions.size){
            val f=functions[i];val g=functions[j]
            fun difference(x:Double)=try{f(x)-g(x)}catch(_:Exception){Double.NaN}
            var last=-domain;var value=difference(last)
            for(k in 1..1200){val x=-domain+2*domain*k/1200;val next=difference(x)
                if(value.isFinite()&&next.isFinite() && (value==0.0||value*next<0)){
                    var a=last;var b=x
                    repeat(40){val mid=(a+b)/2;val v=difference(mid);if(!v.isFinite())return@repeat;if(difference(a)*v<=0)b=mid else a=mid}
                    val root=if(value==0.0)last else (a+b)/2;val y=try{f(root)}catch(_:Exception){Double.NaN}
                    val residual=difference(root)
                    if(y.isFinite()&&residual.isFinite()&&abs(residual)<1e-5 && points.none{hypot(it.x-root,it.y-y)<1e-4})points.add(PlotPoint(root,y))
                }
                last=x;value=next
            }
        }
        return points.take(100)
    }
}
object ShortcutCatalog {
    val keys=listOf("undo","redo","pen","erase","select","pan","shape","fill","color","thicker","thinner","text","sticky","new_page","new_layer","layers","pages","background","fit","coordinates","graph","measure","periodic","ruler","set_square","protractor","compass","mind_map","smart","math","insert","image","pdf","timer","stopwatch","dice","scoreboard","files","new","open","save","recent","export_pdf","export_png","export_jpg","rename","lab","games","share","settings","help","language","fonts","google_search_settings","models","input_controls","page_background","calibration","ui_size","copy","paste","duplicate","delete","lock","unlock_all","front","back")
}
