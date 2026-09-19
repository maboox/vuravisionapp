package com.vuravision.classroom
import android.graphics.*
import kotlin.math.*
/** Subtractive, object-local masks: edits preserve object identity, export and undo. */
data class EraseCut(var ax:Float=0f,var ay:Float=0f,var bx:Float=0f,var by:Float=0f,var radius:Float=1f,var basisW:Float=1f,var basisH:Float=1f,var pdfPage:Int=-1){
 fun contains(x:Float,y:Float,w:Float,h:Float)=distance(x*basisW/w,y*basisH/h,ax,ay,bx,by)<=radius
}
object Erasing {
 fun cut(o:Item,a:PointF,b:PointF,radius:Float):Boolean {
  if(o.locked)return false
  val bounds=itemBounds(o);bounds.inset(-radius,-radius)
  val swept=RectF(min(a.x,b.x)-radius,min(a.y,b.y)-radius,max(a.x,b.x)+radius,max(a.y,b.y)+radius)
  if(!RectF.intersects(bounds,swept))return false
  val aa=o.local(a.x,a.y);val bb=o.local(b.x,b.y)
  o.cuts=o.cuts+EraseCut(aa.first,aa.second,bb.first,bb.second,radius,o.w,o.h,if(o.kind=="pdf")o.pdfPage else -1)
  return true
 }
 fun clip(c:Canvas,o:Item){
  if(o.cuts.isEmpty())return
  val stroke=Paint().apply{style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
  o.cuts.filter{it.pdfPage<0||it.pdfPage==o.pdfPage}.forEach{v->
   val path=Path();val fill=Path();stroke.strokeWidth=v.radius*2
   if(hypot(v.bx-v.ax,v.by-v.ay)<.001f)fill.addCircle(v.ax,v.ay,v.radius,Path.Direction.CW)
   else{path.moveTo(v.ax,v.ay);path.lineTo(v.bx,v.by);stroke.getFillPath(path,fill)}
   fill.transform(Matrix().apply{setScale(o.w/v.basisW,o.h/v.basisH)});c.clipOutPath(fill)
  }
 }
}
