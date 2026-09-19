package com.vuravision.classroom
import kotlin.math.*
object ShapeRecognition {
 fun detect(items:List<Item>):String {
  val pts=items.flatMap{o->o.points.map{o.global(it.x*o.w/o.inkW,it.y*o.h/o.inkH)}}
  if(pts.size<3)return "line"
  val l=pts.minOf{it.first};val r=pts.maxOf{it.first};val t=pts.minOf{it.second};val b=pts.maxOf{it.second};val w=(r-l).coerceAtLeast(1f);val h=(b-t).coerceAtLeast(1f)
  val first=pts.first();val last=pts.last()
  if(hypot(first.first-last.first,first.second-last.second)>hypot(w,h)*.35f)return "line"
  val edge=pts.count{(x,y)->minOf((x-l)/w,(r-x)/w,(y-t)/h,(b-y)/h)<.08f}.toFloat()/pts.size
  if(edge>.7f)return if(abs(w/h-1)<.15f)"square"else"rectangle"
  if(pts.map{(x,y)->hypot((x-(l+r)/2)/(w/2),(y-(t+b)/2)/(h/2))}.average()>.86)return if(abs(w/h-1)<.15f)"circle"else"ellipse"
  return "triangle"
 }
}
