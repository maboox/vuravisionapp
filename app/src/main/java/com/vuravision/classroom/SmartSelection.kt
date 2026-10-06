package com.vuravision.classroom
import kotlin.math.*
object SmartSelection {
 fun selectGesture(path:List<android.graphics.PointF>,bounds:android.graphics.RectF,items:List<Item>,mode:String?=null):List<Item>{
  val area=abs(path.indices.sumOf{i->val a=path[i];val b=path[(i+1)%path.size];(a.x*b.y-b.x*a.y).toDouble()})/2
  val polygon=if(mode==null)path.size>=5 && area>bounds.width()*bounds.height()*.2 else mode=="free"
  if(polygon && path.size<3)return emptyList()
  return items.filter{o->
   val x=o.x+o.w/2;val y=o.y+o.h/2
   if(!polygon) android.graphics.RectF(bounds).apply{inset(-8f,-8f)}.contains(x,y)
   else {var inside=false;var j=path.lastIndex;for(i in path.indices){val a=path[i];val b=path[j];if((a.y>y)!=(b.y>y)&&x<(b.x-a.x)*(y-a.y)/(b.y-a.y)+a.x)inside=!inside;j=i};inside}
  }
 }
 fun points(o:Item)=o.points.map{o.global(it.x*o.w/o.inkW,it.y*o.h/o.inkH)}
 fun isLoop(o:Item):Boolean {val p=points(o);if(p.size<10)return false;val a=p.first();val b=p.last();return hypot(a.first-b.first,a.second-b.second)<max(20f,hypot(o.w,o.h)*.22f)}
 fun enclosed(loop:Item,items:List<Item>):List<Item>{
  val polygon=points(loop)
  fun inside(x:Float,y:Float):Boolean{var yes=false;var j=polygon.lastIndex;for(i in polygon.indices){val a=polygon[i];val b=polygon[j];if((a.second>y)!=(b.second>y)&&x<(b.first-a.first)*(y-a.second)/(b.second-a.second)+a.first)yes=!yes;j=i};return yes}
  return items.filter{!it.locked && it.kind in listOf("ink","text","sticky") && inside(it.x+it.w/2,it.y+it.h/2)}
 }
}
