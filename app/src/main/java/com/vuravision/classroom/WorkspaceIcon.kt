package com.vuravision.classroom

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.RippleDrawable
import androidx.appcompat.widget.AppCompatImageButton

/** Phosphor-based (Fill when active), 48dp touch targets; labels remain available to accessibility and long press. */
open class WorkspaceIcon(context:Context,val key:String,title:String,active:Boolean=false,action:()->Unit):AppCompatImageButton(context){
    /** Square size in dp before the UI scale; the side dock uses larger buttons. */
    var sizeDp=48
        set(value){field=value;val p=context.dp((value*.26f).toInt());setPadding(p,p,p,p);requestLayout()}
    /** Optional ink color shown as a dot under the icon (pen presets). */
    var dotColor:Int?=null
        set(value){field=value;invalidate()}
    var active=active
        private set
    private val dotPaint=Paint(Paint.ANTI_ALIAS_FLAG)
    init {
        scaleType=android.widget.ImageView.ScaleType.FIT_CENTER
        setPadding(context.dp(13),context.dp(13),context.dp(13),context.dp(13))
        minimumWidth=context.dp(48);minimumHeight=context.dp(48)
        contentDescription=title;tooltipText=title
        setOnClickListener{action()}
        showActive(active)
    }
    /** Active tools read from across the room: filled icon on a solid brand capsule. */
    fun showActive(value:Boolean){
        active=value
        setImageResource(IconCatalog.resource(key,value))
        imageTintList=ColorStateList.valueOf(if(value)Color.WHITE else NAVY)
        background=RippleDrawable(ColorStateList.valueOf(0x2220194f),rounded(if(value)NAVY else SURFACE,context.dp(12).toFloat()),null)
        isSelected=value
    }
    override fun onMeasure(w:Int,h:Int){setMeasuredDimension(resolveSize(context.dp(sizeDp),w),resolveSize(context.dp(sizeDp),h))}
    override fun onDraw(c:Canvas){
        super.onDraw(c)
        val color=dotColor?:return
        val r=context.dp(4).toFloat();val cx=width/2f;val cy=height-context.dp(7).toFloat()
        dotPaint.color=if(active)Color.WHITE else SURFACE;c.drawCircle(cx,cy,r+context.dp(2),dotPaint)
        dotPaint.color=color;c.drawCircle(cx,cy,r,dotPaint)
    }
}
