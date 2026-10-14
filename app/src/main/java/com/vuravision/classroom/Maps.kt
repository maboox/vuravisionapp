package com.vuravision.classroom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos

/**
 * Iran with its 31 provinces (item 80), from Natural Earth (public domain), as a scalable object on the board:
 * borders stay crisp at any zoom, and names can be shown in Persian or English or hidden for a "where is …?" quiz.
 */
object Maps {
    class Province(val en:String,val fa:String,val labelX:Float,val labelY:Float,val path:Path)
    class Map(val provinces:List<Province>,val width:Float,val height:Float)
    @Volatile private var iran:Map?=null
    /** Projected with the cosine of the middle latitude, so the shape is not stretched east-west. */
    fun iran(c:Context):Map=iran?:synchronized(this){iran?:load(c).also{iran=it}}
    private fun load(c:Context):Map{
        val o=JSONObject(c.assets.open("maps/iran.json").bufferedReader().use{it.readText()})
        val b=o.getJSONArray("bounds");val minLon=b.getDouble(0);val minLat=b.getDouble(1);val maxLon=b.getDouble(2);val maxLat=b.getDouble(3)
        val k=cos((minLat+maxLat)/2*PI/180)
        fun x(lon:Double)=((lon-minLon)*k).toFloat();fun y(lat:Double)=(maxLat-lat).toFloat()
        val list=o.getJSONArray("provinces")
        val provinces=(0 until list.length()).map{i->val p=list.getJSONObject(i);val path=Path()
            val rings=p.getJSONArray("rings")
            for(r in 0 until rings.length()){val ring=rings.getJSONArray(r)
                for(j in 0 until ring.length() step 2){val px=x(ring.getDouble(j));val py=y(ring.getDouble(j+1));if(j==0)path.moveTo(px,py)else path.lineTo(px,py)}
                path.close()}
            val l=p.getJSONArray("label")
            Province(p.getString("en"),p.getString("fa"),x(l.getDouble(0)),y(l.getDouble(1)),path)}
        return Map(provinces,x(maxLon),y(minLat))
    }
    /** "names" is stored in the item's text: "fa", "en" or "" (no names). */
    fun draw(c:Canvas,o:Item,context:Context,p:Paint){
        val map=iran(context)
        c.save();c.scale(o.w/map.width,o.h/map.height)
        val unit=map.width/o.w
        p.style=Paint.Style.FILL;p.color=0x14000000 or (o.color and 0xffffff);map.provinces.forEach{c.drawPath(it.path,p)}
        p.style=Paint.Style.STROKE;p.color=o.color;p.alpha=o.alpha;p.strokeJoin=Paint.Join.ROUND;p.strokeWidth=o.width*unit
        map.provinces.forEach{c.drawPath(it.path,p)}
        c.restore()
        // Names at normal text size (not inside the tiny map scale, where glyphs render poorly).
        if(o.text in listOf("fa","en")){
            val sx=o.w/map.width;val sy=o.h/map.height
            val t=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=o.color;alpha=o.alpha;textSize=(o.w/900f*15f).coerceIn(9f,40f);textAlign=Paint.Align.CENTER;typeface=Typeface.DEFAULT}
            map.provinces.forEach{v->c.drawText(if(o.text=="fa")v.fa else v.en,v.labelX*sx,v.labelY*sy,t)}
        }
    }
    fun create(c:Context,x:Float,y:Float,layer:String,pane:Int,names:String):Item{
        val map=iran(c);val w=900f
        return Item(kind="shape",shape="map_iran",text=names,x=x,y=y,w=w,h=w*map.height/map.width,width=1.5f,color=0xff3d4a5c.toInt(),layerId=layer,pane=pane)
    }
}
