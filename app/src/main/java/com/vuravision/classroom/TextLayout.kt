package com.vuravision.classroom
import android.graphics.*
import android.text.*
import kotlin.math.*
object TextLayout {
 private data class Key(val text:String,val w:Float,val size:Float,val color:Int,val bold:Boolean,val italic:Boolean,val align:String,val fa:String,val en:String)
 private data class Entry(val key:Key,val layout:StaticLayout)
 private val layouts=object:android.util.LruCache<String,Entry>(128){}
 fun clearCache(){layouts.evictAll()}
 fun paint(o:Item)=TextPaint(Paint.ANTI_ALIAS_FLAG).apply{color=o.color;textSize=o.width;typeface=Fonts.face(if(Fonts.primary(o.text))Fonts.fa(o)else Fonts.en(o),o.bold,o.italic)}
 fun layout(o:Item):StaticLayout{
  val key=Key(o.text,o.w,o.width,o.color,o.bold,o.italic,o.textAlign,Fonts.fa(o),Fonts.en(o))
  layouts.get(o.id)?.takeIf{it.key==key}?.let{return it.layout}
  val text=Fonts.style(o.text,key.fa,key.en,o.bold,o.italic)
  return StaticLayout.Builder.obtain(text,0,text.length,paint(o),(o.w-20).toInt().coerceAtLeast(1)).setIncludePad(false).setAlignment(when(o.textAlign){"center"->Layout.Alignment.ALIGN_CENTER;"end"->Layout.Alignment.ALIGN_OPPOSITE;else->Layout.Alignment.ALIGN_NORMAL}).build().also{layouts.put(o.id,Entry(key,it))}
 }
 fun fit(o:Item){Fonts.stamp(o);val tp=paint(o);val natural=o.text.split('\n').maxOfOrNull{line->Layout.getDesiredWidth(Fonts.style(line,Fonts.fa(o),Fonts.en(o),o.bold,o.italic),tp)}?:1f;o.w=(ceil(natural)+22).coerceIn(if(o.kind=="sticky")180f else 24f,720f);o.h=(layout(o).height+18).toFloat().coerceAtLeast(if(o.kind=="sticky")120f else 24f)}
}
