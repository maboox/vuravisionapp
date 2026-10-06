package com.vuravision.classroom

import android.content.Context
import android.view.View
import android.widget.LinearLayout

fun Context.navigationHeading(title:String,back:(()->Unit)?=null):LinearLayout=row().apply{
    pad(8)
    if(back!=null)addView(WorkspaceIcon(this@navigationHeading,"menu_back",s("menu_back"),action=back).apply{
        id=R.id.menu_back_button;scaleX=if(resources.configuration.layoutDirection==View.LAYOUT_DIRECTION_RTL)-1f else 1f
    },LinearLayout.LayoutParams(dp(48),dp(48)))
    addView(label(title,20f,NAVY,true),LinearLayout.LayoutParams(0,-2,1f))
}
