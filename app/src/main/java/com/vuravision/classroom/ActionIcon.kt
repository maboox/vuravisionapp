package com.vuravision.classroom

import android.content.Context
import android.graphics.*
import android.view.View
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.view.ViewOutlineProvider

/** Small native line icons. No bitmap allocation, animations or blur in the ink path. */
class ActionIcon(context:Context, val symbol:String, title:String, action:()->Unit):View(context){
 private val p=Paint(Paint.ANTI_ALIAS_FLAG)
 init { contentDescription=title;tooltipText=title;isFocusable=true;isClickable=true
  background=RippleDrawable(
   ColorStateList.valueOf(0x1f2d2463),
   rounded(if(symbol=="delete")0xfffff1f0.toInt() else SURFACE_VARIANT,context.dp(14).toFloat(),if(symbol=="delete")0xffffd9d5.toInt() else OUTLINE),
   null,
  )
  elevation=context.dp(1).toFloat()
  outlineProvider=ViewOutlineProvider.BACKGROUND
  setOnClickListener{action()}
 }
 override fun onMeasure(w:Int,h:Int){setMeasuredDimension(resolveSize(context.dp(52),w),resolveSize(context.dp(52),h))}
 override fun onDraw(c:Canvas){
  c.save();c.translate(width/2f,height/2f);c.scale(context.dp(24)/24f,context.dp(24)/24f);c.translate(-12f,-12f)
  p.color=if(symbol=="delete")DANGER else NAVY;p.style=Paint.Style.STROKE;p.strokeWidth=1.7f;p.strokeCap=Paint.Cap.ROUND;p.strokeJoin=Paint.Join.ROUND
  fun line(x:Float,y:Float,a:Float,b:Float)=c.drawLine(x,y,a,b,p)
  when(symbol){
   "copy","duplicate"->{c.drawRoundRect(8f,8f,21f,21f,2f,2f,p);line(4f,16f,3f,16f);line(3f,16f,3f,3f);line(3f,3f,16f,3f);if(symbol=="duplicate"){line(11f,14f,18f,14f);line(14.5f,11f,14.5f,18f)}}
   "delete"->{line(3f,6f,21f,6f);line(9f,3f,15f,3f);line(6f,6f,7f,21f);line(7f,21f,17f,21f);line(17f,21f,18f,6f);line(10f,10f,10f,17f);line(14f,10f,14f,17f)}
   "color"->{c.drawCircle(12f,12f,9f,p);for(i in 0..2){p.style=Paint.Style.FILL;p.color=intArrayOf(TEAL,ORANGE,0xff7465bc.toInt())[i];c.drawCircle(7f+i*5,12f,2f,p)}}
   "text"->{line(4f,5f,20f,5f);line(12f,5f,12f,21f);line(8f,21f,16f,21f)}
   "insert"->{line(12f,4f,12f,20f);line(4f,12f,20f,12f)}
   "shape"->{line(12f,3f,22f,20f);line(22f,20f,2f,20f);line(2f,20f,12f,3f)}
   "formula"->{line(5f,8f,19f,8f);line(5f,16f,19f,16f)}
   "graph"->{line(3f,3f,3f,21f);line(3f,21f,22f,21f);val path=Path();path.moveTo(5f,17f);path.cubicTo(11f,17f,11f,4f,20f,5f);c.drawPath(path,p)}
   "search"->{c.drawCircle(10f,10f,6f,p);line(14.5f,14.5f,21f,21f)}
   "previous","next"->{val sign=if(symbol=="next")1 else -1;line(12f-sign*3,5f,12f+sign*4,12f);line(12f+sign*4,12f,12f-sign*3,19f)}
   else->{p.style=Paint.Style.FILL;for(i in 0..2)c.drawCircle(5f+i*7,12f,1.8f,p)}
  };c.restore()
 }
}
