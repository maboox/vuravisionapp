package com.vuravision.classroom
import android.graphics.*
import kotlin.math.*

object GeometryTools {
 val keys=listOf("ruler","set_square","protractor","compass")
 fun draw(c:Canvas,o:Item){
  val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=0xddf0f7f7.toInt();style=Paint.Style.FILL}
  val w=o.w;val h=o.h
  when(o.shape){
   "ruler"->c.drawRoundRect(0f,0f,w,h,6f,6f,p)
   "set_square"->{val path=Path();path.moveTo(0f,0f);path.lineTo(w,h);path.lineTo(0f,h);path.close();c.drawPath(path,p)}
   "protractor"->c.drawArc(0f,0f,w,h*2,180f,180f,true,p)
   "compass"->{p.color=TEAL;p.strokeWidth=5f;c.drawLine(w/2,0f,0f,h,p);c.drawLine(w/2,0f,w,h,p);c.drawCircle(w/2,0f,6f,p)}
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
  if(circle){val a=o.global(0f,o.h);result.x=a.first-o.w;result.y=a.second-o.w;result.w=o.w*2;result.h=o.w*2}
  else {val edge=if(o.shape=="ruler")0f else o.h;val a=o.global(0f,edge);val b=o.global(o.w,edge);result.x=(a.first+b.first)/2-o.w/2;result.y=(a.second+b.second)/2-.5f;result.w=o.w;result.h=1f;result.rotation=o.rotation}
  return result
 }
}
