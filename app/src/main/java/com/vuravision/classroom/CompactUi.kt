package com.vuravision.classroom

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat

object IconCatalog {
    fun resource(key:String)=when(key){
        "pen","rename","pencil","edit"->R.drawable.feather_edit_2
        "erase"->R.drawable.vura_eraser;"select"->R.drawable.feather_mouse_pointer
        "touch","input_controls","touch_test"->R.drawable.vura_touch
        "pan","drag"->R.drawable.feather_move;"shape","set_square"->R.drawable.feather_triangle
        "text","fonts","text_size"->R.drawable.feather_type;"layers","new_layer"->R.drawable.feather_layers
        "smart","evaluate","solve","math","formula"->R.drawable.feather_zap
        "tools"->R.drawable.vura_toolbox;"timer","stopwatch"->R.drawable.feather_clock;"split"->R.drawable.feather_columns
        "menu"->R.drawable.feather_menu;"insert","new","new_lesson","new_page","add_branch"->R.drawable.feather_plus
        "undo"->R.drawable.feather_rotate_ccw;"redo"->R.drawable.feather_rotate_cw
        "share","share_pdf"->R.drawable.feather_share_2;"previous"->R.drawable.feather_chevron_left
        "next"->R.drawable.feather_chevron_right;"close"->R.drawable.feather_x
        "visible"->R.drawable.feather_eye;"hidden"->R.drawable.feather_eye_off
        "lock"->R.drawable.feather_lock;"unlock","unlock_all"->R.drawable.feather_unlock
        "delete"->R.drawable.feather_trash_2;"background","page_background"->R.drawable.vura_background
        "fullscreen","stress"->R.drawable.feather_maximize;"fit"->R.drawable.feather_minimize
        "files","open","recent"->R.drawable.feather_folder
        "pages","export_pdf","export_png","export_jpg","pdf","page_picker"->R.drawable.feather_file
        "save"->R.drawable.vura_save;"up","front","thicker"->R.drawable.feather_arrow_up
        "down","back","thinner"->R.drawable.feather_arrow_down
        "convert","restore_erased","cache"->R.drawable.feather_refresh_cw
        "color","palette"->R.drawable.vura_palette;"fill"->R.drawable.vura_bucket;"search","google_search_settings"->R.drawable.vura_search
        "graph","coordinates"->R.drawable.vura_graph;"measure","ruler","calibration","thresholds"->R.drawable.vura_ruler
        "compass"->R.drawable.vura_compass;"protractor"->R.drawable.vura_protractor
        "voice_assistant","voice_behavior"->R.drawable.feather_mic;"mute"->R.drawable.vura_mic_off
        "help","about","info","report"->R.drawable.vura_info
        "lab","periodic"->R.drawable.vura_atom;"games","dice"->R.drawable.vura_dice
        "settings","engineering","advanced_board","ui_size"->R.drawable.vura_settings
        "language","models"->R.drawable.vura_globe;"copy","duplicate"->R.drawable.vura_copy
        "bold"->R.drawable.vura_bold;"italic"->R.drawable.vura_italic
        "align_start"->R.drawable.vura_align_start;"align_center"->R.drawable.vura_align_center;"align_end"->R.drawable.vura_align_end
        "pie_settings"->R.drawable.vura_pie
        "image"->R.drawable.vura_image;"sticky"->R.drawable.vura_sticky;"mind_map"->R.drawable.vura_mind_map
        "paste"->R.drawable.vura_clipboard;"crop","pdf_crop"->R.drawable.vura_crop
        "domain"->R.drawable.vura_graph;"scoreboard"->R.drawable.vura_scoreboard
        "periodic_table"->R.drawable.vura_atom;"secret_studio","maboox"->R.drawable.vura_settings
        else->R.drawable.feather_more_horizontal
    }
    fun drawable(c:Context,key:String):Drawable?=ContextCompat.getDrawable(c,resource(key))?.mutate()?.apply{setTint(NAVY)}
}

class MenuRows(private val c:Context,private val keys:List<String>):BaseAdapter(){
    override fun getCount()=keys.size
    override fun getItem(p:Int)=c.s(keys[p])
    override fun getItemId(p:Int)=p.toLong()
    override fun getView(p:Int,old:View?,parent:ViewGroup?):View=c.row().apply{
        pad(8);minimumHeight=c.dp(56)
        addView(ImageView(c).apply{setImageDrawable(IconCatalog.drawable(c,keys[p]));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},LinearLayout.LayoutParams(c.dp(26),c.dp(26)).apply{setMargins(c.dp(10),0,c.dp(14),0)})
        addView(c.label(c.s(keys[p]),17f),LinearLayout.LayoutParams(0,-2,1f))
        contentDescription=c.s(keys[p])
    }
}

fun Context.infoTitle(title:String,details:String):View=row().apply{
    addView(label(title,16f,NAVY,true),LinearLayout.LayoutParams(0,-2,1f))
    addView(WorkspaceIcon(this@infoTitle,"info",tr("Information: ","اطلاعات: ")+title){
        val anchor=getChildAt(1)
        val body=label(details,14f).apply{pad(14);setTextIsSelectable(true)}
        val popup=PopupWindow(body,minOf(dp(340),resources.displayMetrics.widthPixels-dp(24)),-2,true)
        popup.setBackgroundDrawable(rounded(SURFACE,dp(12).toFloat(),OUTLINE));popup.elevation=dp(5).toFloat();popup.isOutsideTouchable=true
        popup.showAsDropDown(anchor,-dp(260),0)
        val handler=Handler(Looper.getMainLooper());val close=Runnable{popup.dismiss()};handler.postDelayed(close,8000)
        popup.setOnDismissListener{handler.removeCallbacks(close)}
    },LinearLayout.LayoutParams(dp(44),dp(44)))
}

fun Context.colorPalette(initial:Int,colors:List<Int>,add:(()->Unit),choose:(Int)->Unit):View {
    val r=row();var selected=initial;val cells=mutableListOf<View>()
    colors.distinct().forEach{color->
        val cell=object:View(this){private val p=Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(c:Canvas){val radius=dp(14).toFloat();p.color=color;p.style=Paint.Style.FILL;c.drawCircle(width/2f,height/2f,radius,p);p.color=if(selected==color)TEAL else OUTLINE_STRONG;p.style=Paint.Style.STROKE;p.strokeWidth=dp(if(selected==color)3 else 1).toFloat();c.drawCircle(width/2f,height/2f,radius+dp(3),p)}
        }.apply{contentDescription=String.format(java.util.Locale.US,"#%06X",color and 0xffffff);tooltipText=contentDescription;isFocusable=true;setOnClickListener{selected=color;choose(color);cells.forEach{it.invalidate()}}}
        cells.add(cell);r.addView(cell,LinearLayout.LayoutParams(dp(46),dp(48)))
    }
    val plus=WorkspaceIcon(this,"insert",tr("Add color to this lesson","افزودن رنگ به این فایل")){add()}
    plus.background=rounded(SURFACE,dp(24).toFloat(),OUTLINE);r.addView(plus,LinearLayout.LayoutParams(dp(46),dp(48)))
    return scrollRow(r)
}

class OptionAdapter(private val c:Context,values:List<String>):ArrayAdapter<String>(c,android.R.layout.simple_spinner_dropdown_item,values){
    private fun style(v:View):View{if(v is TextView){v.textSize=16f*c.uiScale();v.setTextColor(NAVY);v.setPadding(c.dp(12),c.dp(10),c.dp(12),c.dp(10));Fonts.bind(v)};return v}
    override fun getView(position:Int,convertView:View?,parent:ViewGroup):View=style(super.getView(position,convertView,parent))
    override fun getDropDownView(position:Int,convertView:View?,parent:ViewGroup):View=style(super.getDropDownView(position,convertView,parent))
}
