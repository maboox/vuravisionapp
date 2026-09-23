package com.vuravision.classroom

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import androidx.appcompat.widget.AppCompatImageButton

/** Feather-based, 48dp touch targets; labels remain available to accessibility and long press. */
class WorkspaceIcon(context:Context,key:String,title:String,active:Boolean=false,action:()->Unit):AppCompatImageButton(context){
    init {
        val resource=when(key){
            "pen"->R.drawable.feather_edit_2;"erase"->R.drawable.vura_eraser
            "select"->R.drawable.feather_mouse_pointer;"touch"->R.drawable.vura_touch;"pan","drag"->R.drawable.feather_move
            "shape"->R.drawable.feather_triangle;"text"->R.drawable.feather_type
            "layers"->R.drawable.feather_layers;"smart"->R.drawable.feather_zap
            "tools"->R.drawable.feather_clock;"split"->R.drawable.feather_columns
            "menu"->R.drawable.feather_menu;"insert"->R.drawable.feather_plus
            "undo"->R.drawable.feather_rotate_ccw;"redo"->R.drawable.feather_rotate_cw
            "share"->R.drawable.feather_share_2;"previous"->R.drawable.feather_chevron_left
            "next"->R.drawable.feather_chevron_right;"close"->R.drawable.feather_x
            "visible"->R.drawable.feather_eye;"hidden"->R.drawable.feather_eye_off
            "lock"->R.drawable.feather_lock;"unlock"->R.drawable.feather_unlock
            "delete"->R.drawable.feather_trash_2;"background"->R.drawable.feather_square
            "fullscreen"->R.drawable.feather_maximize;"fit"->R.drawable.feather_minimize
            "files"->R.drawable.feather_folder;"pages"->R.drawable.feather_file
            "up"->R.drawable.feather_arrow_up;"down"->R.drawable.feather_arrow_down
            "convert"->R.drawable.feather_refresh_cw
            else->R.drawable.feather_more_horizontal
        }
        setImageResource(resource)
        imageTintList=ColorStateList.valueOf(if(active)0xff4262e8.toInt()else NAVY)
        background=RippleDrawable(ColorStateList.valueOf(0x224262e8),rounded(if(active)PRIMARY_CONTAINER else SURFACE,context.dp(8).toFloat()),null)
        setPadding(context.dp(13),context.dp(13),context.dp(13),context.dp(13))
        minimumWidth=context.dp(48);minimumHeight=context.dp(48)
        contentDescription=title;tooltipText=title;isSelected=active
        setOnClickListener{action()}
    }
    override fun onMeasure(w:Int,h:Int){setMeasuredDimension(resolveSize(context.dp(48),w),resolveSize(context.dp(48),h))}
}
