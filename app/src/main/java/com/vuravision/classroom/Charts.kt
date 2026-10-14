package com.vuravision.classroom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Bar, line and pie charts drawn as a picture for the board (item 69): from a table on the board (offline) or from
 * data the AI writes for a request. Large text and few colors, for reading from the back of the room.
 */
object Charts {
    data class Spec(val title:String,val type:String,val labels:List<String>,val values:List<Double>,val unit:String="")
    private val palette=intArrayOf(0xff218c91.toInt(),0xffe46d38.toInt(),0xff7754ad.toInt(),0xff2f8f4e.toInt(),0xffd9a404.toInt(),0xffba4058.toInt(),0xff3f6fb5.toInt(),0xff8a6d3b.toInt())

    fun prompt(request:String)="""
        Make data for a classroom chart about: "${request.take(300)}". Use real, well-known figures when they exist; otherwise
        simple illustrative numbers and say "(example)" in the title. Reply with JSON only:
        {"title": "...", "type": "bar" or "line" or "pie", "labels": ["...", ...], "values": [numbers], "unit": "..."}.
        2 to 12 labels, each at most 3 words. Pie only for parts of a whole.
    """.trimIndent()
    fun parse(o:JSONObject):Spec{
        val l=o.optJSONArray("labels")?:throw AiException("format");val v=o.optJSONArray("values")?:throw AiException("format")
        val n=min(min(l.length(),v.length()),12)
        val labels=(0 until n).map{l.optString(it).trim().take(30)};val values=(0 until n).map{v.optDouble(it,Double.NaN)}
        if(n<2||values.any{!it.isFinite()})throw AiException("format")
        val type=o.optString("type").takeIf{it in listOf("bar","line","pie")}?:"bar"
        return Spec(o.optString("title").trim().take(80),if(type=="pie"&&values.any{it<0})"bar" else type,labels,values,o.optString("unit").trim().take(20))
    }
    /** First column = labels, the first column with numbers = values; the header row names the chart. */
    fun fromTable(rows:List<List<String>>):Spec?{
        if(rows.size<3)return null
        fun number(s:String)=s.trim().map{c->if(c in '۰'..'۹')'0'+(c-'۰') else if(c in '٠'..'٩')'0'+(c-'٠') else if(c=='٫')'.' else c}.joinToString("").replace(",","").replace("٬","").toDoubleOrNull()
        val body=rows.drop(1)
        val column=(1 until rows.first().size).firstOrNull{j->body.count{number(it[j])!=null}>=2}?:return null
        val data=body.mapNotNull{r->number(r[column])?.let{r[0].ifBlank{"—"} to it}}
        if(data.size<2)return null
        return Spec(rows.first()[column].ifBlank{rows.first()[0]},"bar",data.map{it.first},data.map{it.second})
    }

    fun render(spec:Spec,dark:Boolean=false):Bitmap{
        val w=1400;val h=900;val b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);val c=Canvas(b)
        val ink=if(dark)Color.WHITE else 0xff20194f.toInt();val muted=if(dark)0xffb8c4cc.toInt() else 0xff5f6b76.toInt()
        c.drawColor(if(dark)0xff172a36.toInt() else Color.WHITE)
        val p=Paint(Paint.ANTI_ALIAS_FLAG);val t=android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply{color=ink}
        t.textSize=48f;t.typeface=Typeface.DEFAULT_BOLD
        val title=spec.title+if(spec.unit.isNotBlank()&&spec.type!="pie")" (${spec.unit})" else ""
        c.drawText(TextUtils.ellipsize(title,t,w-80f,TextUtils.TruncateAt.END).toString(),40f,80f,t)
        t.typeface=Typeface.DEFAULT;t.textSize=30f
        if(spec.type=="pie"){pie(c,spec,p,t,ink,muted,w,h);return b}
        val lo=min(0.0,spec.values.min());val hi=max(0.0,spec.values.max()).let{if(it==lo)lo+1 else it}
        val step=nice((hi-lo)/5);val top=ceil(hi/step)*step;val bottom=floor(lo/step)*step
        val area=RectF(150f,130f,w-50f,h-130f)
        fun y(v:Double)=(area.bottom-(v-bottom)/(top-bottom)*area.height()).toFloat()
        p.strokeWidth=2f
        var v=bottom
        while(v<=top+step/2){p.color=if(abs(v)<step/1000)ink else (if(dark)0x33ffffff else 0x22000000);c.drawLine(area.left,y(v),area.right,y(v),p)
            t.color=muted;val s=fmt(v);c.drawText(s,area.left-16f-t.measureText(s),y(v)+10f,t);v+=step}
        val n=spec.values.size;val slot=area.width()/n
        spec.values.forEachIndexed{i,value->
            val x=area.left+slot*i+slot/2
            if(spec.type=="bar"){p.color=palette[0];val bw=slot*.6f;c.drawRoundRect(x-bw/2,min(y(value),y(0.0)),x+bw/2,max(y(value),y(0.0)),8f,8f,p)}
            t.color=ink;val label=TextUtils.ellipsize(spec.labels[i],t,slot-8f,TextUtils.TruncateAt.END).toString()
            c.drawText(label,x-t.measureText(label)/2,area.bottom+50f,t)
            val s=fmt(value);t.color=muted;c.drawText(s,x-t.measureText(s)/2,y(value)-(if(value>=0)14f else -40f),t)
        }
        if(spec.type=="line"){p.color=palette[0];p.strokeWidth=6f;p.style=Paint.Style.STROKE
            val path=android.graphics.Path();spec.values.forEachIndexed{i,value->val x=area.left+slot*i+slot/2;if(i==0)path.moveTo(x,y(value))else path.lineTo(x,y(value))};c.drawPath(path,p)
            p.style=Paint.Style.FILL;spec.values.forEachIndexed{i,value->c.drawCircle(area.left+slot*i+slot/2,y(value),10f,p)}}
        return b
    }
    private fun pie(c:Canvas,spec:Spec,p:Paint,t:android.text.TextPaint,ink:Int,muted:Int,w:Int,h:Int){
        val total=spec.values.sum().takeIf{it>0}?:1.0;val r=320f;val cx=420f;val cy=h/2f+40f
        var angle=-90f
        spec.values.forEachIndexed{i,v->val sweep=(v/total*360).toFloat();p.color=palette[i%palette.size];c.drawArc(cx-r,cy-r,cx+r,cy+r,angle,sweep,true,p);angle+=sweep}
        spec.labels.forEachIndexed{i,label->val y=180f+i*58f;p.color=palette[i%palette.size];c.drawRoundRect(820f,y-30f,856f,y+6f,6f,6f,p)
            t.color=ink;val pct=" · ${fmt(spec.values[i]/total*100)}%";c.drawText(TextUtils.ellipsize(label,t,w-900f-t.measureText(pct),TextUtils.TruncateAt.END).toString()+pct,872f,y,t)}
    }
    private fun nice(raw:Double):Double{if(raw<=0)return 1.0;val e=10.0.pow(floor(log10(raw)));val f=raw/e;return e*when{f<=1->1.0;f<=2->2.0;f<=5->5.0;else->10.0}}
    private fun fmt(v:Double)=if(abs(v-Math.rint(v))<1e-9&&abs(v)<1e12)"%,d".format(java.util.Locale.US,v.toLong()) else "%,.1f".format(java.util.Locale.US,v)
}
