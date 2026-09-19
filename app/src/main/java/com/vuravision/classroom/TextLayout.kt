package com.vuravision.classroom
import android.graphics.*
import android.text.*
import kotlin.math.*
object TextLayout {
 fun paint(o:Item)=TextPaint(Paint.ANTI_ALIAS_FLAG).apply{color=o.color;textSize=o.width;typeface=Typeface.create("sans-serif",if(o.bold)Typeface.BOLD else Typeface.NORMAL)}
 fun layout(o:Item)=StaticLayout.Builder.obtain(o.text,0,o.text.length,paint(o),(o.w-20).toInt().coerceAtLeast(1)).setIncludePad(false).setAlignment(when(o.textAlign){"center"->Layout.Alignment.ALIGN_CENTER;"end"->Layout.Alignment.ALIGN_OPPOSITE;else->Layout.Alignment.ALIGN_NORMAL}).build()
 fun fit(o:Item){val tp=paint(o);val natural=o.text.split('\n').maxOfOrNull{tp.measureText(it)}?:1f;o.w=(ceil(natural)+22).coerceIn(if(o.kind=="sticky")180f else 24f,720f);o.h=(layout(o).height+18).toFloat().coerceAtLeast(if(o.kind=="sticky")120f else 24f)}
}
