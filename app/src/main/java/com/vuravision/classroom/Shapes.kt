package com.vuravision.classroom
import android.graphics.*
import kotlin.math.*
object Shapes {
 val keys=listOf("rectangle","square","rounded_rectangle","circle","ellipse","triangle","right_triangle","diamond","pentagon","hexagon","octagon","star","heart","trapezoid","parallelogram","line","arrow","double_arrow","cross","speech","cylinder","cube","cone","axes")
 fun draw(c:Canvas,key:String,w:Float,h:Float,p:Paint) {
  fun polygon(vararg pts:Float) { val path=Path();path.moveTo(pts[0]*w,pts[1]*h);for(i in 2 until pts.size step 2)path.lineTo(pts[i]*w,pts[i+1]*h);path.close();c.drawPath(path,p) }
  fun regular(n:Int,star:Boolean=false) { val path=Path();val total=if(star)n*2 else n;repeat(total){i->val a=-PI/2+2*PI*i/total;val r=if(star&&i%2==1).22 else .5;val x=(w*(.5+r*cos(a))).toFloat();val y=(h*(.5+r*sin(a))).toFloat();if(i==0)path.moveTo(x,y)else path.lineTo(x,y)};path.close();c.drawPath(path,p) }
  fun line(a:Float,b:Float,d:Float,e:Float)=c.drawLine(a*w,b*h,d*w,e*h,p)
  when(key) {
   "circle","ellipse"->c.drawOval(0f,0f,w,h,p)
   "rounded_rectangle"->c.drawRoundRect(0f,0f,w,h,min(w,h)*.15f,min(w,h)*.15f,p)
   "triangle"->polygon(.5f,0f,1f,1f,0f,1f)
   "right_triangle"->polygon(0f,0f,1f,1f,0f,1f)
   "diamond"->polygon(.5f,0f,1f,.5f,.5f,1f,0f,.5f)
   "pentagon"->regular(5)
   "hexagon"->regular(6)
   "octagon"->regular(8)
   "star"->regular(5,true)
   "trapezoid"->polygon(.25f,0f,.75f,0f,1f,1f,0f,1f)
   "parallelogram"->polygon(.25f,0f,1f,0f,.75f,1f,0f,1f)
   "cross"->polygon(.35f,0f,.65f,0f,.65f,.35f,1f,.35f,1f,.65f,.65f,.65f,.65f,1f,.35f,1f,.35f,.65f,0f,.65f,0f,.35f,.35f,.35f)
   "speech"->polygon(0f,0f,1f,0f,1f,.75f,.4f,.75f,.15f,1f,.15f,.75f,0f,.75f)
   "heart"->{val path=Path();path.moveTo(w*.5f,h);path.cubicTo(-w*.5f,h*.35f,w*.15f,-h*.4f,w*.5f,h*.2f);path.cubicTo(w*.85f,-h*.4f,w*1.5f,h*.35f,w*.5f,h);c.drawPath(path,p)}
   "line","arrow","double_arrow"->{line(0f,1f,1f,0f);val a=atan2(-h,w);val head=min(22f,hypot(w,h)*.25f);if(key!="line")for(sign in listOf(-1,1)){c.drawLine(w,0f,w-head*cos(a+sign*.5f),-head*sin(a+sign*.5f),p);if(key=="double_arrow")c.drawLine(0f,h,head*cos(a+sign*.5f),h+head*sin(a+sign*.5f),p)}}
   "cylinder"->{c.drawOval(0f,0f,w,h*.25f,p);line(0f,.125f,0f,.875f);line(1f,.125f,1f,.875f);c.drawOval(0f,h*.75f,w,h,p)}
   "cone"->{line(.5f,0f,0f,.875f);line(.5f,0f,1f,.875f);c.drawOval(0f,h*.75f,w,h,p)}
   "cube"->{polygon(0f,.25f,.75f,.25f,.75f,1f,0f,1f);polygon(0f,.25f,.25f,0f,1f,0f,.75f,.25f);line(1f,0f,1f,.75f);line(1f,.75f,.75f,1f)}
   "axes"->{line(0f,.5f,1f,.5f);line(.5f,0f,.5f,1f);line(1f,.5f,.9f,.45f);line(1f,.5f,.9f,.55f);line(.5f,0f,.45f,.1f);line(.5f,0f,.55f,.1f)}
   else->c.drawRect(0f,0f,w,h,p)
  }
 }
}
