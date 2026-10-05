package com.vuravision.classroom

import kotlin.math.*

data class ShapeMeasure(val perimeter:Double,val area:Double?,val lengths:List<Double>,val angles:List<Double>)
object Measurements {
    fun of(o:Item):ShapeMeasure? {
        val w=o.w.toDouble();val h=o.h.toDouble()
        if(o.kind!="shape" || o.shape in GeometryTools.keys)return null
        if(o.shape in listOf("line","arrow","double_arrow"))return ShapeMeasure(hypot(w,h),null,listOf(hypot(w,h)),emptyList())
        if(o.shape in listOf("circle","ellipse")){
            val a=w/2;val b=h/2;val k=(a-b).pow(2)/(a+b).pow(2)
            return ShapeMeasure(PI*(a+b)*(1+3*k/(10+sqrt(4-3*k))),PI*a*b,listOf(w,h),emptyList())
        }
        val vertices=when(o.shape){
            "rectangle","square"->listOf(0.0 to 0.0,w to 0.0,w to h,0.0 to h)
            "triangle"->listOf(w/2 to 0.0,w to h,0.0 to h)
            "right_triangle"->listOf(0.0 to 0.0,w to h,0.0 to h)
            "diamond"->listOf(w/2 to 0.0,w to h/2,w/2 to h,0.0 to h/2)
            "trapezoid"->listOf(w*.25 to 0.0,w*.75 to 0.0,w to h,0.0 to h)
            "parallelogram"->listOf(w*.25 to 0.0,w to 0.0,w*.75 to h,0.0 to h)
            "pentagon","hexagon","octagon"->{val n=when(o.shape){"pentagon"->5;"hexagon"->6;else->8};(0 until n).map{val a=-PI/2+2*PI*it/n;w*(.5+.5*cos(a)) to h*(.5+.5*sin(a))}}
            else->return null
        }
        val lengths=vertices.indices.map{i->val a=vertices[i];val b=vertices[(i+1)%vertices.size];hypot(a.first-b.first,a.second-b.second)}
        val area=abs(vertices.indices.sumOf{i->val a=vertices[i];val b=vertices[(i+1)%vertices.size];a.first*b.second-b.first*a.second})/2
        val angles=vertices.indices.map{i->val a=vertices[(i+vertices.size-1)%vertices.size];val b=vertices[i];val c=vertices[(i+1)%vertices.size];val ux=a.first-b.first;val uy=a.second-b.second;val vx=c.first-b.first;val vy=c.second-b.second;acos(((ux*vx+uy*vy)/(hypot(ux,uy)*hypot(vx,vy))).coerceIn(-1.0,1.0))*180/PI}
        return ShapeMeasure(lengths.sum(),area,lengths,angles)
    }
}
