package com.vuravision.classroom

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import androidx.appcompat.widget.AppCompatImageButton

/** Phosphor-based (Fill when active), 48dp touch targets; labels remain available to accessibility and long press. */
class WorkspaceIcon(context:Context,key:String,title:String,active:Boolean=false,action:()->Unit):AppCompatImageButton(context){
    init {
        val resource=IconCatalog.resource(key,active)
        setImageResource(resource)
        scaleType=android.widget.ImageView.ScaleType.FIT_CENTER
        // Active tools read from across the room: filled icon on a solid brand capsule.
        imageTintList=ColorStateList.valueOf(if(active)Color.WHITE else NAVY)
        background=RippleDrawable(ColorStateList.valueOf(0x2220194f),rounded(if(active)NAVY else SURFACE,context.dp(12).toFloat()),null)
        setPadding(context.dp(13),context.dp(13),context.dp(13),context.dp(13))
        minimumWidth=context.dp(48);minimumHeight=context.dp(48)
        contentDescription=title;tooltipText=title;isSelected=active
        setOnClickListener{action()}
    }
    override fun onMeasure(w:Int,h:Int){setMeasuredDimension(resolveSize(context.dp(48),w),resolveSize(context.dp(48),h))}
}
