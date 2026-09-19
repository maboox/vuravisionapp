package com.vuravision.classroom
import android.content.Context
import android.graphics.*
import kotlin.math.*
object Science {
 fun current(v:Double,r:Double)=v/r
 fun kinetic(m:Double,v:Double)=.5*m*v*v
 fun buoyancy(density:Double,liters:Double)=density*9.81*liters/1000
 fun lens(f:Double,u:Double)=if(abs(u-f)<1e-7)Double.POSITIVE_INFINITY else f*u/(u-f)
 fun gasPressure(moles:Double,kelvin:Double,liters:Double)=moles*8.314462618*kelvin/liters
 fun dilution(c:Double,v:Double,total:Double)=c*v/total
}
object ExtraLabs {
 val keys=listOf("ohm","energy","buoyancy","lens","triangle_area","statistics","gas","dilution")
 fun controls(key:String):List<LabControl> = when(key){
  "ohm"->listOf(LabControl("V (V)",1f,24f,12f),LabControl("R (Ω)",1f,100f,10f))
  "energy"->listOf(LabControl("m (kg)",.1f,10f,2f),LabControl("v (m/s)",0f,20f,5f))
  "buoyancy"->listOf(LabControl("ρ (kg/m³)",500f,1500f,1000f),LabControl("V (L)",.1f,10f,1f))
  "lens"->listOf(LabControl("f (cm)",5f,30f,10f),LabControl("u (cm)",1f,90f,30f))
  "triangle_area"->listOf(LabControl("b (cm)",1f,20f,8f),LabControl("h (cm)",1f,20f,6f))
  "statistics"->listOf(LabControl("A",0f,100f,20f),LabControl("B",0f,100f,40f),LabControl("C",0f,100f,60f))
  "gas"->listOf(LabControl("n (mol)",.1f,3f,1f),LabControl("T (K)",100f,500f,273.15f),LabControl("V (L)",1f,50f,22.4f))
  "dilution"->listOf(LabControl("C₁ (mol/L)",.1f,2f,1f),LabControl("V₁ (mL)",10f,100f,50f),LabControl("Added water (mL)",0f,500f,100f))
  else->emptyList()
 }
 fun draw(c:Canvas,key:String,values:FloatArray,context:Context){
  val a=values[0].toDouble();val b=values[1].toDouble();val d=values.getOrElse(2){0f}.toDouble();val p=Paint(Paint.ANTI_ALIAS_FLAG)
  fun text(s:String,x:Float,y:Float,size:Float=25f,color:Int=NAVY){p.style=Paint.Style.FILL;p.color=color;p.textSize=size;c.drawText(s,x,y,p)}
  fun line(x:Float,y:Float,xx:Float,yy:Float,color:Int=NAVY){p.color=color;p.style=Paint.Style.STROKE;p.strokeWidth=4f;c.drawLine(x,y,xx,yy,p)}
  fun block(x:Float,y:Float,w:Float,h:Float,color:Int){p.style=Paint.Style.FILL;p.color=color;c.drawRoundRect(x,y,x+w,y+h,14f,14f,p)}
  fun f(v:Double)="%.2f".format(java.util.Locale.US,v)
  when(key){
   "ohm"->{line(220f,180f,780f,180f);line(780f,180f,780f,380f);line(780f,380f,220f,380f);line(220f,380f,220f,180f);block(425f,145f,150f,70f,TEAL);block(200f,255f,40f,60f,ORANGE);text("R = ${f(b)} Ω",420f,120f);text("I = V/R = ${f(Science.current(a,b))} A",280f,480f);text("P = VI = ${f(a*a/b)} W",280f,520f)}
   "energy"->{block(150f,350f,700f,10f,MUTED);block(150f,250f,(a*15+60).toFloat(),90f,TEAL);line(450f,295f,(450+b*15).toFloat(),295f,ORANGE);text("Eₖ = ½mv² = ${f(Science.kinetic(a,b))} J",260f,440f);text("p = mv = ${f(a*b)} kg·m/s",260f,490f)}
   "buoyancy"->{block(220f,180f,560f,240f,0xffe2f5fc.toInt());block(420f,260f,160f,110f,TEAL);line(500f,270f,500f,165f,ORANGE);line(500f,165f,480f,195f,ORANGE);line(500f,165f,520f,195f,ORANGE);text("Fᵦ = ρgV = ${f(Science.buoyancy(a,b))} N",230f,490f)}
   "lens"->{val v=Science.lens(a,b);line(100f,285f,900f,285f,MUTED);line(500f,100f,500f,445f,TEAL);val scale=350/max(max(a,b),if(v.isFinite())abs(v).coerceAtMost(1000.0)else a);val ox=(500-b*scale).toFloat();line(ox,285f,ox,220f,ORANGE)
    c.save();c.clipRect(90f,85f,910f,465f);line(ox,220f,500f,220f,ORANGE);line(500f,220f,900f,(220+400*65/(a*scale)).toFloat(),ORANGE);line(ox,220f,900f,(285+400*65/(b*scale)).toFloat(),MUTED)
    if(v.isFinite()){val ix=(500+v*scale).toFloat();val iy=(285+65*v/b).toFloat();line(ix,285f,ix,iy,TEAL);if(v<0){p.pathEffect=DashPathEffect(floatArrayOf(8f,8f),0f);line(500f,220f,ix,iy,ORANGE);p.pathEffect=null}};c.restore();text("1/f = 1/u + 1/v",320f,490f);text(if(v.isFinite())"v = ${f(v)} cm · ${context.s(if(v>0)"real_image"else"virtual_image")}"else context.s("image_infinity"),220f,535f,21f)}
   "triangle_area"->{val w=(a*28).toFloat();val h=(b*15).toFloat();val x=500-w/2;val y=420f;line(x,y,x+w,y);line(x,y,x+w*.4f,y-h,TEAL);line(x+w,y,x+w*.4f,y-h,TEAL);line(x+w*.4f,y,x+w*.4f,y-h,ORANGE);text("A = ½bh = ${f(a*b/2)} cm²",300f,510f)}
   "statistics"->{val arr=listOf(a,b,d);arr.forEachIndexed{i,v->block(210f+i*210,420-v.toFloat()*3,120f,v.toFloat()*3,if(i==1)ORANGE else TEAL);text(f(v),215f+i*210,455f)};val mean=arr.average();val sd=sqrt(arr.sumOf{(it-mean).pow(2)}/3);text("μ = ${f(mean)}     Median = ${f(arr.sorted()[1])}     σ = ${f(sd)}",160f,510f,22f)}
   "gas"->{block(270f,150f,460f,260f,0xffe2f5fc.toInt());p.color=TEAL;p.style=Paint.Style.FILL;repeat((a*15).toInt()){i->c.drawCircle(300f+(i*79%390),180f+(i*43%200),7f,p)};text("PV = nRT",360f,460f);text("P = ${f(Science.gasPressure(a,b,d))} kPa",320f,510f)}
   "dilution"->{block(170f,220f,240f,180f,TEAL);block(540f,130f,260f,270f,0xffb9e6e5.toInt());text("V₂ = ${f(b+d)} mL",570f,450f,21f);text("C₁V₁ = C₂V₂   →   C₂ = ${f(Science.dilution(a,b,b+d))} mol/L",130f,515f,23f)}
  }
 }
}
