package com.vuravision.classroom
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.RippleDrawable
import android.view.View

/**
 * The one action control for object bars and the PDF header: transparent until pressed, so the bar
 * itself is the only surface. With [label], the text sits under the icon (64dp tall) because an
 * icon alone is ambiguous for actions like convert, solve or arrange.
 */
class ActionIcon(context:Context,val symbol:String,title:String,private val label:String?=null,active:Boolean=false,action:()->Unit):View(context){
    private val icon=IconCatalog.drawable(context,symbol)?.apply{if(active)setTint(Color.WHITE)}
    private val text=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=if(active)Color.WHITE else NAVY;textSize=context.dp(13).toFloat();textAlign=Paint.Align.CENTER;typeface=Fonts.face(Fonts.persianId)}
    init{
        contentDescription=title;tooltipText=title;isFocusable=true;isClickable=true;setOnClickListener{action()}
        background=RippleDrawable(ColorStateList.valueOf(0x2220194f),rounded(if(active)NAVY else Color.TRANSPARENT,context.dp(12).toFloat()),rounded(Color.WHITE,context.dp(12).toFloat()))
    }
    override fun onMeasure(w:Int,h:Int){
        if(label==null){setMeasuredDimension(resolveSize(context.dp(48),w),resolveSize(context.dp(48),h));return}
        val width=maxOf(context.dp(64),text.measureText(label).toInt()+context.dp(16))
        setMeasuredDimension(resolveSize(width,w),resolveSize(context.dp(64),h))
    }
    override fun onDraw(c:Canvas){
        val size=context.dp(24)
        if(label==null){icon?.setBounds((width-size)/2,(height-size)/2,(width+size)/2,(height+size)/2);icon?.draw(c);return}
        val top=context.dp(9)
        icon?.setBounds((width-size)/2,top,(width+size)/2,top+size);icon?.draw(c)
        c.drawText(label,width/2f,(top+size+context.dp(19)).toFloat(),text)
    }
}
