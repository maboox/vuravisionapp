package com.vuravision.classroom
import android.graphics.*
import kotlin.math.*

object GeometryTools {
 val keys=listOf("ruler","set_square","protractor","compass")
 /** Project a pen sample onto the edge nearest the first contact. The returned
  * point is in board coordinates, so rotation, zoom and pane transforms agree. */
 private fun projections(tool:Item,x:Float,y:Float):List<Pair<Float,Float>> {
  val (lx,ly)=tool.local(x,y)
  fun edge(ax:Float,ay:Float,bx:Float,by:Float):Pair<Float,Float>{
   val dx=bx-ax;val dy=by-ay;val length=dx*dx+dy*dy
   val t=if(length==0f)0f else (((lx-ax)*dx+(ly-ay)*dy)/length).coerceIn(0f,1f)
   return ax+t*dx to ay+t*dy
  }
  return when(tool.shape){
   "ruler"->listOf(edge(0f,0f,tool.w,0f),edge(0f,tool.h,tool.w,tool.h))
   "set_square"->listOf(edge(0f,0f,0f,tool.h),edge(0f,tool.h,tool.w,tool.h),edge(0f,0f,tool.w,tool.h))
   "protractor"->{val r=tool.w/2;val a=atan2((tool.h-ly).coerceAtLeast(0f),lx-r)
    listOf(edge(0f,tool.h,tool.w,tool.h),r+r*cos(a) to tool.h-r*sin(a))}
   "compass"->{val cx=tool.w/2;val cy=if(tool.geometryVersion==1)tool.h/2 else 0f;val r=radius(tool);val d=hypot(lx-cx,ly-cy)
    if(d<=1f)emptyList()else listOf(cx+(lx-cx)*r/d to cy+(ly-cy)*r/d)}
   else->emptyList()
  }
 }
 fun nearestEdge(tool:Item,x:Float,y:Float,allowed:List<Int>?=null):Int? {
  val (lx,ly)=tool.local(x,y);val all=projections(tool,x,y)
  return (allowed?:all.indices.toList()).filter{it in all.indices}.minByOrNull{hypot(lx-all[it].first,ly-all[it].second)}
 }
 fun edgeCandidates(tool:Item,x:Float,y:Float,tolerance:Float,ambiguity:Float):List<Int> {
  val (lx,ly)=tool.local(x,y);val gaps=projections(tool,x,y).map{hypot(lx-it.first,ly-it.second)}
  val nearest=gaps.minOrNull()?:return emptyList()
  return gaps.indices.filter{gaps[it]<=tolerance && gaps[it]<=nearest+ambiguity}
 }

 /** A contact keeps its first edge until lift, including at triangle corners. */
 fun snap(tool:Item,x:Float,y:Float,tolerance:Float=14f,edgeIndex:Int?=null):PointF? {
  val (lx,ly)=tool.local(x,y);val all=projections(tool,x,y)
  val point=all.getOrNull(edgeIndex?:nearestEdge(tool,x,y)?:return null)?:return null
  if(hypot(lx-point.first,ly-point.second)>tolerance)return null
  val (gx,gy)=tool.global(point.first,point.second);return PointF(gx,gy)
 }
 fun setSlope(o:Item,degrees:Float){require(o.shape=="set_square" && degrees.isFinite() && degrees in 10f..80f);o.h=(o.w*tan(degrees*PI/180)).toFloat()}
 fun numberSize(o:Item)=when(o.shape){"ruler"->o.h*.30f;"set_square"->min(o.w,o.h)*.12f;"protractor"->o.w*.048f;else->radius(o)*.13f}.coerceIn(16f,160f)

 fun radius(o:Item)=if(o.geometryVersion==1)o.w/2 else hypot(o.w/2,o.h)
 fun center(o:Item)=o.global(o.w/2,if(o.geometryVersion==1)o.h/2 else 0f)
 fun rulerLabelStep(pixelsPerUnit:Float,spacing:Float=40f):Float {
  val need=spacing/pixelsPerUnit.coerceAtLeast(.001f)
  val power=10.0.pow(floor(log10(need.toDouble()))).toFloat()
  return listOf(1f,2f,5f,10f).first{it*power>=need}*power
 }
 fun prepare(o:Item){if(o.shape=="compass"&&o.geometryVersion==0){val center=center(o);val r=radius(o);o.x=center.first-r;o.y=center.second-r;o.w=r*2;o.h=r*2;o.rotation=0f;o.geometryVersion=1;o.geometryAngle=-60f}}
 fun draw(c:Canvas,o:Item,pixelsPerCm:Float=0f){
  val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=0xddf0f7f7.toInt();style=Paint.Style.FILL}
  val w=o.w;val h=o.h
  val matrix=Matrix();c.getMatrix(matrix);val values=FloatArray(9);matrix.getValues(values)
  val pxPerUnit=hypot(values[0],values[3]).coerceAtLeast(.0001f)
  val unit=if(pixelsPerCm>0)pixelsPerCm/pxPerUnit else 10f
  val unitName=if(pixelsPerCm>0)"cm"else"u"
  when(o.shape){
   "ruler"->c.drawRoundRect(0f,0f,w,h,6f,6f,p)
   "set_square"->{val path=Path();path.moveTo(0f,0f);path.lineTo(w,h);path.lineTo(0f,h);path.close();c.drawPath(path,p)}
   "protractor"->c.drawArc(0f,0f,w,h*2,180f,180f,true,p)
   "compass"->{val cx=w/2;val cy=if(o.geometryVersion==1)h/2 else 0f;val r=radius(o);val a=o.geometryAngle*PI/180
    p.color=TEAL;p.strokeWidth=4f;p.style=Paint.Style.STROKE
    p.pathEffect=DashPathEffect(floatArrayOf(5f,7f),0f);p.strokeWidth=1.2f;c.drawCircle(cx,cy,r,p);p.pathEffect=null
    p.strokeWidth=4f;c.drawLine(cx,cy,cx+r*cos(a).toFloat(),cy+r*sin(a).toFloat(),p)
    p.style=Paint.Style.FILL;c.drawCircle(cx,cy,6f,p);p.textSize=numberSize(o);c.drawText("r = ${DisplayNumbers.one((r/unit).toDouble())} $unitName",cx+8,cy-12,p)
    c.drawText("${DisplayNumbers.one(o.geometrySweep.toDouble())}°",cx+8,cy+numberSize(o)*1.4f,p)
   }
  }
  p.color=TEAL;p.strokeWidth=1.5f;p.style=Paint.Style.STROKE
  if(o.shape=="ruler"){
   c.drawRoundRect(0f,0f,w,h,6f,6f,p)
   // Fonts follow guide size; label density also adapts to display scale.
   val text=numberSize(o);p.textSize=text
   val labelUnits=rulerLabelStep(unit*pxPerUnit,max(40f,p.measureText(DisplayNumbers.one((w/unit).toDouble()))*pxPerUnit+12f))
   val tickUnits=if(unit*pxPerUnit>=35).1f else if(unit*pxPerUnit>=14).5f else labelUnits/5
   val tick=(unit*tickUnits).coerceAtLeast(3f/pxPerUnit)
   for(i in 0..(w/tick).toInt().coerceAtMost(1000)){
    val x=i*tick;val value=x/unit;val labelled=abs(value/labelUnits-round(value/labelUnits))<.01f
    c.drawLine(x,0f,x,if(labelled)h*.27f else h*.12f,p)
   }
   val labelStep=unit*labelUnits
   for(i in 0..(w/labelStep).toInt().coerceAtMost(200)){
    val x=i*labelStep;val label=DisplayNumbers.one((i*labelUnits).toDouble())
    if(x+p.measureText(label)+3f/pxPerUnit<=w){p.style=Paint.Style.FILL;c.drawText(label,x+2f/pxPerUnit,h*.60f,p);p.style=Paint.Style.STROKE}
   }
   p.style=Paint.Style.FILL;c.drawText(if(pixelsPerCm>0)"cm" else "u",w-p.measureText(unitName)-6,h-6,p);c.drawText("${DisplayNumbers.one(o.rotation.toDouble())}°",8f,h-6,p)
  }
  if(o.shape=="set_square"){c.drawLine(0f,0f,w,h,p);c.drawLine(w,h,0f,h,p);c.drawLine(0f,h,0f,0f,p);p.style=Paint.Style.FILL;p.textSize=numberSize(o);c.drawText("90°",10f,h-12,p);c.drawText("${DisplayNumbers.one((atan2(h,w)*180/PI).toDouble())}°",w*.5f,h*.5f,p)}
  if(o.shape=="protractor"){
   c.drawArc(0f,0f,w,h*2,180f,180f,false,p);c.drawLine(0f,h,w,h,p);p.textSize=numberSize(o)
   val angle=o.geometryAngle.coerceIn(0f,180f)*PI/180
   p.color=ORANGE;p.strokeWidth=2f;c.drawLine(w/2,h,w/2+w/2*cos(angle).toFloat(),h-h*sin(angle).toFloat(),p);p.color=TEAL;p.textSize=numberSize(o);p.style=Paint.Style.FILL;c.drawText("${DisplayNumbers.one(o.geometryAngle.toDouble())}°",w/2+8,h-16,p);p.style=Paint.Style.STROKE
   for(deg in 0..180 step 20){val a=deg*PI/180;val x=w/2+w/2*cos(a).toFloat();val y=h-h*sin(a).toFloat();val xx=w/2+(w/2-w*.035f)*cos(a).toFloat();val yy=h-(h-h*.07f)*sin(a).toFloat();c.drawLine(x,y,xx,yy,p);p.style=Paint.Style.FILL;c.drawText("$deg",w/2+(w/2-w*.10f)*cos(a).toFloat()-p.measureText("$deg")/2,h-(h-h*.20f)*sin(a).toFloat(),p);p.style=Paint.Style.STROKE}
  }
 }
 fun construction(o:Item,perpendicular:Boolean=false):Item {
  val circle=o.shape=="compass";val result=Item(kind="shape",shape=if(circle)"circle" else "line",color=o.color,width=3f,layerId=o.layerId,pane=o.pane)
  if(circle){val a=center(o);val radius=radius(o);result.x=a.first-radius;result.y=a.second-radius;result.w=radius*2;result.h=radius*2}
  else {val edge=if(o.shape=="ruler")0f else o.h;val a=o.global(0f,edge);val b=o.global(o.w,edge);result.x=(a.first+b.first)/2-o.w/2;result.y=(a.second+b.second)/2-.5f;result.w=o.w;result.h=1f;result.rotation=o.rotation
    if(o.shape=="protractor"){val a=o.global(o.w/2,o.h);val angle=(o.rotation-o.geometryAngle)*PI/180;val length=o.w/2;result.x=a.first+length*cos(angle).toFloat()/2-length/2;result.y=a.second+length*sin(angle).toFloat()/2-.5f;result.w=length;result.rotation=o.rotation-o.geometryAngle}
    else if(perpendicular){val start=o.global(0f,edge);val angle=(o.rotation-90f)*PI/180;result.x=start.first+o.w*cos(angle).toFloat()/2-o.w/2;result.y=start.second+o.w*sin(angle).toFloat()/2-.5f;result.rotation=o.rotation-90f}
   }
  return result
 }
}
