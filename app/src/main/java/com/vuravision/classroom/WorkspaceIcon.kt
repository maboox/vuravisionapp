package com.vuravision.classroom

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import androidx.appcompat.widget.AppCompatImageButton

/** Feather-based, 48dp touch targets; labels remain available to accessibility and long press. */
class WorkspaceIcon(context:Context,key:String,title:String,active:Boolean=false,action:()->Unit):AppCompatImageButton(context){
    init {
        val resource=IconCatalog.resource(key)
        setImageResource(resource)
        scaleType=android.widget.ImageView.ScaleType.FIT_CENTER
        imageTintList=ColorStateList.valueOf(if(active)0xff4262e8.toInt()else NAVY)
        background=RippleDrawable(ColorStateList.valueOf(0x224262e8),rounded(if(active)PRIMARY_CONTAINER else SURFACE,context.dp(8).toFloat()),null)
        setPadding(context.dp(13),context.dp(13),context.dp(13),context.dp(13))
        minimumWidth=context.dp(48);minimumHeight=context.dp(48)
        contentDescription=title;tooltipText=title;isSelected=active
        setOnClickListener{action()}
    }
    override fun onMeasure(w:Int,h:Int){setMeasuredDimension(resolveSize(context.dp(48),w),resolveSize(context.dp(48),h))}
}
