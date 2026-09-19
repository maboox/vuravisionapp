package com.vuravision.classroom
import kotlin.math.*
object SmartSelection {
 fun points(o:Item)=o.points.map{o.global(it.x*o.w/o.inkW,it.y*o.h/o.inkH)}
 fun isLoop(o:Item):Boolean {val p=points(o);if(p.size<10)return false;val a=p.first();val b=p.last();return hypot(a.first-b.first,a.second-b.second)<max(20f,hypot(o.w,o.h)*.22f)}
 fun enclosed(loop:Item,items:List<Item>):List<Item>{
  val polygon=points(loop)
  fun inside(x:Float,y:Float):Boolean{var yes=false;var j=polygon.lastIndex;for(i in polygon.indices){val a=polygon[i];val b=polygon[j];if((a.second>y)!=(b.second>y)&&x<(b.first-a.first)*(y-a.second)/(b.second-a.second)+a.first)yes=!yes;j=i};return yes}
  return items.filter{!it.locked && it.kind in listOf("ink","text","sticky") && inside(it.x+it.w/2,it.y+it.h/2)}
 }
}
