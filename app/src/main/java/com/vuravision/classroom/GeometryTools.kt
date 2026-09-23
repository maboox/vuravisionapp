package com.vuravision.classroom
import android.graphics.*
import kotlin.math.*

object GeometryTools {
 val keys=listOf("ruler","set_square","protractor","compass")
 /** Project a pen sample onto the edge nearest the first contact. The returned
  * point is in board coordinates, so rotation, zoom and pane transforms agree. */
 fun snap(tool:Item,x:Float,y:Float,tolerance:Float=14f):PointF? {
  if(tool.shape !in keys)return null
  val (lx,ly)=tool.local(x,y)
  var best:Pair<Float,Float>?=null
  var gap=Float.POSITIVE_INFINITY
  fun edge(ax:Float,ay:Float,bx:Float,by:Float){
   val dx=bx-ax;val dy=by-ay;val length=dx*dx+dy*dy
   val t=if(length==0f)0f else (((lx-ax)*dx+(ly-ay)*dy)/length).coerceIn(0f,1f)
   val px=ax+t*dx;val py=ay+t*dy;val d=hypot(lx-px,ly-py)
   if(d<gap){gap=d;best=px to py}
  }
  when(tool.shape){
   "ruler"->{edge(0f,0f,tool.w,0f);edge(0f,tool.h,tool.w,tool.h)}
   "set_square"->{edge(0f,0f,0f,tool.h);edge(0f,tool.h,tool.w,tool.h);edge(0f,0f,tool.w,tool.h)}
   "protractor"->{edge(0f,tool.h,tool.w,tool.h)
    val radius=tool.w/2;val angle=atan2((tool.h-ly).coerceAtLeast(0f),lx-radius)
    val px=radius+radius*cos(angle);val py=tool.h-radius*sin(angle)
    val d=hypot(lx-px,ly-py);if(d<gap){gap=d;best=px to py}
   }
   "compass"->{val cx=tool.w/2;val cy=0f;val radius=hypot(cx,tool.h)
    val dist=hypot(lx-cx,ly-cy);if(dist>1f){gap=abs(dist-radius);best=(cx+(lx-cx)*radius/dist) to (cy+ly*radius/dist)}
   }
  }
  if(gap>tolerance)return null
  val point=best?:return null;val (gx,gy)=tool.global(point.first,point.second)
  return PointF(gx,gy)
 }
 fun draw(c:Canvas,o:Item){
  val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=0xddf0f7f7.toInt();style=Paint.Style.FILL}
  val w=o.w;val h=o.h
  when(o.shape){
   "ruler"->c.drawRoundRect(0f,0f,w,h,6f,6f,p)
   "set_square"->{val path=Path();path.moveTo(0f,0f);path.lineTo(w,h);path.lineTo(0f,h);path.close();c.drawPath(path,p)}
   "protractor"->c.drawArc(0f,0f,w,h*2,180f,180f,true,p)
   "compass"->{p.color=TEAL;p.strokeWidth=5f;c.drawLine(w/2,0f,0f,h,p);c.drawLine(w/2,0f,w,h,p);c.drawCircle(w/2,0f,6f,p)
    p.color=0x55666666;p.style=Paint.Style.STROKE;p.strokeWidth=1.5f;p.pathEffect=DashPathEffect(floatArrayOf(5f,7f),0f)
    c.drawCircle(w/2,0f,hypot(w/2,h),p);p.pathEffect=null
   }
  }
  p.color=TEAL;p.strokeWidth=1.5f;p.style=Paint.Style.STROKE
  if(o.shape=="ruler"){c.drawRoundRect(0f,0f,w,h,6f,6f,p);p.textSize=11f;for(i in 0..(w/10).toInt()){val x=i*10f;c.drawLine(x,0f,x,if(i%5==0)18f else 9f,p);if(i%5==0){p.style=Paint.Style.FILL;c.drawText("${i/5}",x+2,32f,p);p.style=Paint.Style.STROKE}}}
  if(o.shape=="set_square"){c.drawLine(0f,0f,w,h,p);c.drawLine(w,h,0f,h,p);c.drawLine(0f,h,0f,0f,p)}
  if(o.shape=="protractor"){
   c.drawArc(0f,0f,w,h*2,180f,180f,false,p);c.drawLine(0f,h,w,h,p);p.textSize=11f
   for(deg in 0..180 step 10){val a=deg*PI/180;val x=w/2+w/2*cos(a).toFloat();val y=h-h*sin(a).toFloat();val xx=w/2+(w/2-12)*cos(a).toFloat();val yy=h-(h-12)*sin(a).toFloat();c.drawLine(x,y,xx,yy,p);p.style=Paint.Style.FILL;c.drawText("$deg",w/2+(w/2-30)*cos(a).toFloat()-8,h-(h-30)*sin(a).toFloat(),p);p.style=Paint.Style.STROKE}
  }
 }
 fun construction(o:Item):Item {
  val circle=o.shape=="compass";val result=Item(kind="shape",shape=if(circle)"circle" else "line",color=o.color,width=3f,layerId=o.layerId,pane=o.pane)
  if(circle){val a=o.global(o.w/2,0f);val radius=hypot(o.w/2,o.h);result.x=a.first-radius;result.y=a.second-radius;result.w=radius*2;result.h=radius*2}
  else {val edge=if(o.shape=="ruler")0f else o.h;val a=o.global(0f,edge);val b=o.global(o.w,edge);result.x=(a.first+b.first)/2-o.w/2;result.y=(a.second+b.second)/2-.5f;result.w=o.w;result.h=1f;result.rotation=o.rotation}
  return result
 }
}
