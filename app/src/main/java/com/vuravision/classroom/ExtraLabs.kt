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
 fun draw(c:Canvas,key:String,values:FloatArray,context:Context,time:Double=0.0){
  val a=values[0].toDouble();val b=values[1].toDouble();val d=values.getOrElse(2){0f}.toDouble();val p=Paint(Paint.ANTI_ALIAS_FLAG)
  fun text(s:String,x:Float,y:Float,size:Float=25f,color:Int=NAVY){p.style=Paint.Style.FILL;p.color=color;p.textSize=size;c.drawText(s,x,y,p)}
  fun line(x:Float,y:Float,xx:Float,yy:Float,color:Int=NAVY){p.color=color;p.style=Paint.Style.STROKE;p.strokeWidth=4f;c.drawLine(x,y,xx,yy,p)}
  fun block(x:Float,y:Float,w:Float,h:Float,color:Int){p.style=Paint.Style.FILL;p.color=color;c.drawRoundRect(x,y,x+w,y+h,14f,14f,p)}
  fun f(v:Double)="%.2f".format(java.util.Locale.US,v)
  when(key){
   "ohm"->{
    val current=Science.current(a,b);val resistor=(85+b*1.4).toFloat()
    line(220f,180f,780f,180f);line(780f,180f,780f,380f);line(780f,380f,220f,380f);line(220f,380f,220f,180f)
    block(500-resistor/2,145f,resistor,70f,TEAL);block(200f,255f,40f,60f,ORANGE)
    p.style=Paint.Style.FILL;p.color=ORANGE
    repeat(7){i->val fraction=((i/7.0+time*.12*sqrt(current.coerceAtMost(24.0)))%1.0).toFloat();c.drawCircle(260+fraction*480,380f,5f,p)}
    text("${f(a)} V",75f,305f,22f,ORANGE);text("R = ${f(b)} Ω",420f,120f)
    text("I = V/R = ${f(current)} A",280f,480f);text("P = VI = ${f(a*a/b)} W",280f,520f)
   }
   "energy"->{block(150f,350f,700f,10f,MUTED);block(150f,250f,(a*15+60).toFloat(),90f,TEAL);line(450f,295f,(450+b*15).toFloat(),295f,ORANGE);text("Eₖ = ½mv² = ${f(Science.kinetic(a,b))} J",260f,440f);text("p = mv = ${f(a*b)} kg·m/s",260f,490f)}
   "buoyancy"->{
    val volume=(75+b*12).toFloat();val force=Science.buoyancy(a,b)
    val water=Color.rgb(208,228,(228+(a-500)*.027).toInt().coerceIn(0,255))
    block(220f,180f,560f,240f,water);block(500-volume/2,330-volume,volume,volume,TEAL)
    val tip=(310-force*2.2).toFloat().coerceIn(125f,290f)
    line(500f,330-volume/2,500f,tip,ORANGE);line(500f,tip,484f,tip+28,ORANGE);line(500f,tip,516f,tip+28,ORANGE)
    text("ρ = ${f(a)} kg/m³",240f,465f,20f);text("Fᵦ = ρgV = ${f(force)} N",230f,515f)
   }
   "lens"->{val v=Science.lens(a,b);line(100f,285f,900f,285f,MUTED);line(500f,100f,500f,445f,TEAL);val scale=350/max(max(a,b),if(v.isFinite())abs(v).coerceAtMost(1000.0)else a);val ox=(500-b*scale).toFloat();line(ox,285f,ox,220f,ORANGE)
    c.save();c.clipRect(90f,85f,910f,465f);line(ox,220f,500f,220f,ORANGE);line(500f,220f,900f,(220+400*65/(a*scale)).toFloat(),ORANGE);line(ox,220f,900f,(285+400*65/(b*scale)).toFloat(),MUTED)
    if(v.isFinite()){val ix=(500+v*scale).toFloat();val iy=(285+65*v/b).toFloat();line(ix,285f,ix,iy,TEAL);if(v<0){p.pathEffect=DashPathEffect(floatArrayOf(8f,8f),0f);line(500f,220f,ix,iy,ORANGE);p.pathEffect=null}};c.restore();text("1/f = 1/u + 1/v",320f,490f);text(if(v.isFinite())"v = ${f(v)} cm · ${context.s(if(v>0)"real_image"else"virtual_image")}"else context.s("image_infinity"),220f,535f,21f)}
   "triangle_area"->{val w=(a*28).toFloat();val h=(b*15).toFloat();val x=500-w/2;val y=420f;line(x,y,x+w,y);line(x,y,x+w*.4f,y-h,TEAL);line(x+w,y,x+w*.4f,y-h,TEAL);line(x+w*.4f,y,x+w*.4f,y-h,ORANGE);text("A = ½bh = ${f(a*b/2)} cm²",300f,510f)}
   "statistics"->{val arr=listOf(a,b,d);arr.forEachIndexed{i,v->block(210f+i*210,420-v.toFloat()*3,120f,v.toFloat()*3,if(i==1)ORANGE else TEAL);text(f(v),215f+i*210,455f)};val mean=arr.average();val sd=sqrt(arr.sumOf{(it-mean).pow(2)}/3);text("μ = ${f(mean)}     Median = ${f(arr.sorted()[1])}     σ = ${f(sd)}",160f,510f,22f)}
   "gas"->{
    val chamber=(175+d*6).coerceIn(180.0,480.0).toFloat();val left=500-chamber/2
    block(left,145f,chamber,270f,0xffe2f5fc.toInt())
    p.color=TEAL;p.style=Paint.Style.FILL
    val count=(a*15).toInt().coerceIn(1,45);val speed=sqrt(b/273.15)
    repeat(count){i->
     val x=left+18+((i*79.0+time*34*speed*(1+i%3))%(chamber.toDouble()-36)).toFloat()
     val y=168+((i*53.0+time*22*speed*(1+i%4))%220).toFloat()
     c.drawCircle(x,y,if(b>370)7.5f else 6f,p)
    }
    text("V = ${f(d)} L   T = ${f(b)} K",290f,465f,23f)
    text("P = nRT/V = ${f(Science.gasPressure(a,b,d))} kPa",250f,515f,23f)
   }
   "dilution"->{
    val result=Science.dilution(a,b,b+d)
    val before=(b*1.8).toFloat();val after=((b+d)/600*260).toFloat()
    block(170f,130f,240f,270f,0xfff1f5f5.toInt());block(170f,400-before,240f,before,TEAL)
    block(540f,130f,260f,270f,0xfff1f5f5.toInt());block(540f,400-after,260f,after,Color.rgb(174,(221-result*27).toInt().coerceIn(150,230),215))
    text("C₁ = ${f(a)} mol/L",170f,110f,20f);text("V₂ = ${f(b+d)} mL",565f,455f,21f)
    text("C₁V₁ = C₂V₂   →   C₂ = ${f(result)} mol/L",130f,515f,23f)
   }
  }
 }
}
