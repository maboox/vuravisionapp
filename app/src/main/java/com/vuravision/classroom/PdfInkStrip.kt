package com.vuravision.classroom

import android.content.Context
import android.graphics.*
import android.view.View
import android.widget.LinearLayout

/** One-tap pen, highlighter, eraser, color and width for the PDF workspace, including fullscreen. */
class PdfInkStrip(context:Context,private val board:()->Board,private val changed:()->Unit):LinearLayout(context){
    companion object {
        val colors=listOf(0xff1f2430.toInt(),0xff2563eb.toInt(),0xffdc2626.toInt(),0xff16a34a.toInt(),0xfff59e0b.toInt(),0xff7c3aed.toInt())
        val penWidths=listOf(2f,4f,8f)
        val highlightWidths=listOf(4f,6f,10f)
    }
    private val tools=listOf("pen","highlight","erase").map{key->
        ActionIcon(context,key,when(key){"pen"->context.tr("Pen","قلم");"highlight"->context.tr("Highlighter","هایلایتر");else->context.tr("Eraser","پاک‌کن")}){
            board().apply{tool=key;clearSelection()};changed();refresh()
        }
    }
    private val swatches=colors.map{color->Swatch(context,color,null){board().inkColor=color;changed();refresh()}}
    private val widths=penWidths.indices.map{i->Swatch(context,null,i){val b=board();b.inkWidth=(if(b.tool=="highlight")highlightWidths else penWidths)[i];changed();refresh()}}
    init{
        orientation=HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_LTR
        tools.forEach{addView(it,LayoutParams(context.dp(48),context.dp(48)).apply{marginEnd=context.dp(4)})}
        (swatches+widths).forEachIndexed{i,v->addView(v,LayoutParams(context.dp(40),context.dp(48)).apply{if(i==0||i==swatches.size)marginStart=context.dp(10)})}
        refresh()
    }
    fun refresh(){
        val b=board()
        tools.forEach{it.alpha=if(it.symbol==b.tool)1f else .55f}
        swatches.forEach{it.chosen=it.color==b.inkColor;it.ink=b.inkColor;it.invalidate()}
        val sizes=if(b.tool=="highlight")highlightWidths else penWidths
        widths.forEachIndexed{i,w->w.chosen=kotlin.math.abs(sizes[i]-b.inkWidth)<.01f;w.ink=b.inkColor;w.invalidate()}
    }
    private class Swatch(context:Context,val color:Int?,val size:Int?,action:()->Unit):View(context){
        var chosen=false
        var ink=Color.BLACK
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        init{isClickable=true;isFocusable=true;contentDescription=if(color!=null)context.tr("Ink color","رنگ قلم")else context.tr("Ink width","ضخامت قلم");setOnClickListener{action()}}
        override fun onDraw(c:Canvas){
            val cx=width/2f;val cy=height/2f
            if(chosen){paint.style=Paint.Style.STROKE;paint.strokeWidth=context.dp(2).toFloat();paint.color=NAVY;c.drawCircle(cx,cy,context.dp(17).toFloat(),paint)}
            paint.style=Paint.Style.FILL
            if(color!=null){paint.color=color;c.drawCircle(cx,cy,context.dp(13).toFloat(),paint)}
            else{paint.color=ink;c.drawCircle(cx,cy,context.dp(3+4*(size?:0)).toFloat(),paint)}
        }
    }
}
