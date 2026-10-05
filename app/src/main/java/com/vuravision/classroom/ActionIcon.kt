package com.vuravision.classroom
import android.content.Context
import android.graphics.Canvas
import android.view.View
class ActionIcon(context:Context,val symbol:String,title:String,action:()->Unit):View(context){
    private val icon=IconCatalog.drawable(context,symbol)
    init{contentDescription=title;tooltipText=title;isFocusable=true;isClickable=true;background=rounded(SURFACE_VARIANT,context.dp(10).toFloat(),OUTLINE);setOnClickListener{action()}}
    override fun onMeasure(w:Int,h:Int){setMeasuredDimension(resolveSize(context.dp(48),w),resolveSize(context.dp(48),h))}
    override fun onDraw(c:Canvas){val size=context.dp(24);icon?.setBounds((width-size)/2,(height-size)/2,(width+size)/2,(height+size)/2);icon?.draw(c)}
}
