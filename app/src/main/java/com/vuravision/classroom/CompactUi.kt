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
    /** Phosphor Bold for idle controls; [filled] selects the Fill weight used for active tools. */
    fun resource(key:String,filled:Boolean=false):Int=if(filled)FILLED[name(key)]?:resource(key) else when(name(key)){
        "pen"->R.drawable.ph_pen;"highlighter"->R.drawable.ph_highlighter;"eraser"->R.drawable.ph_eraser;"cursor"->R.drawable.ph_cursor
        "hand_tap"->R.drawable.ph_hand_tap;"hand"->R.drawable.ph_hand;"move"->R.drawable.ph_move;"shapes"->R.drawable.ph_shapes
        "triangle"->R.drawable.ph_triangle;"text"->R.drawable.ph_text;"fonts"->R.drawable.ph_fonts;"layers"->R.drawable.ph_layers
        "sparkle"->R.drawable.ph_sparkle;"toolbox"->R.drawable.ph_toolbox;"timer"->R.drawable.ph_timer;"columns"->R.drawable.ph_columns
        "users"->R.drawable.ph_users;"menu"->R.drawable.ph_menu;"plus"->R.drawable.ph_plus;"rotate"->R.drawable.ph_rotate
        "undo"->R.drawable.ph_undo;"redo"->R.drawable.ph_redo;"share"->R.drawable.ph_share
        "caret_left"->R.drawable.ph_caret_left;"caret_right"->R.drawable.ph_caret_right;"close"->R.drawable.ph_close
        "eye"->R.drawable.ph_eye;"eye_off"->R.drawable.ph_eye_off;"lock"->R.drawable.ph_lock;"unlock"->R.drawable.ph_unlock
        "trash"->R.drawable.ph_trash;"background"->R.drawable.ph_background;"expand"->R.drawable.ph_expand;"fit"->R.drawable.ph_fit
        "folder"->R.drawable.ph_folder;"file"->R.drawable.ph_file;"file_pdf"->R.drawable.ph_file_pdf;"save"->R.drawable.ph_save
        "arrow_up"->R.drawable.ph_arrow_up;"arrow_down"->R.drawable.ph_arrow_down;"convert"->R.drawable.ph_convert
        "palette"->R.drawable.ph_palette;"bucket"->R.drawable.ph_bucket;"search"->R.drawable.ph_search;"graph"->R.drawable.ph_graph
        "ruler"->R.drawable.ph_ruler;"compass"->R.drawable.ph_compass;"protractor"->R.drawable.vura_protractor
        "mic"->R.drawable.ph_mic;"mic_off"->R.drawable.ph_mic_off;"info"->R.drawable.ph_info;"atom"->R.drawable.ph_atom
        "dice"->R.drawable.ph_dice;"settings"->R.drawable.ph_settings;"globe"->R.drawable.ph_globe;"copy"->R.drawable.ph_copy
        "duplicate"->R.drawable.ph_duplicate;"bold"->R.drawable.ph_bold;"italic"->R.drawable.ph_italic
        "align_left"->R.drawable.ph_align_left;"align_center"->R.drawable.ph_align_center;"align_right"->R.drawable.ph_align_right
        "pie"->R.drawable.ph_pie;"image"->R.drawable.ph_image;"sticky"->R.drawable.ph_sticky;"mind_map"->R.drawable.ph_mind_map
        "paste"->R.drawable.ph_paste;"crop"->R.drawable.ph_crop;"trophy"->R.drawable.ph_trophy;"stop"->R.drawable.ph_stop
        "swap"->R.drawable.ph_swap;"content"->R.drawable.ph_content;"flask"->R.drawable.ph_flask;"game"->R.drawable.ph_game
        "spotlight"->R.drawable.ph_spotlight;"table"->R.drawable.ph_table
        else->R.drawable.ph_more
    }
    private val FILLED=mapOf("pen" to R.drawable.ph_pen_fill,"highlighter" to R.drawable.ph_highlighter_fill,"eraser" to R.drawable.ph_eraser_fill,
        "cursor" to R.drawable.ph_cursor_fill,"hand" to R.drawable.ph_hand_fill,"hand_tap" to R.drawable.ph_hand_tap_fill,"shapes" to R.drawable.ph_shapes_fill,
        "text" to R.drawable.ph_text_fill,"sparkle" to R.drawable.ph_sparkle_fill,"layers" to R.drawable.ph_layers_fill,"columns" to R.drawable.ph_columns_fill,
        "ruler" to R.drawable.ph_ruler_fill,"triangle" to R.drawable.ph_triangle_fill,"mic" to R.drawable.ph_mic_fill)
    /** Feature keys → icon names. Unknown keys fall back to "more". */
    fun name(key:String)=when(key){
        "pen","rename","pencil","edit"->"pen";"highlight"->"highlighter"
        "erase","clear_page"->"eraser";"select","selection_settings","selection_free","selection_box"->"cursor"
        "touch","input_controls","touch_test"->"hand_tap";"pan"->"hand";"drag"->"move"
        "shape"->"shapes";"set_square"->"triangle";"text","text_size"->"text";"fonts"->"fonts";"layers","new_layer"->"layers"
        "smart","evaluate","solve","math","formula"->"sparkle";"tools"->"toolbox";"timer","stopwatch"->"timer";"split"->"columns"
        "shared_room","device_profile"->"users";"menu"->"menu";"insert","new","new_lesson","new_page","add_branch"->"plus"
        "rotate"->"rotate";"undo"->"undo";"redo"->"redo";"share","share_pdf"->"share"
        "previous","move_left","menu_back"->"caret_left";"next","move_right"->"caret_right";"close"->"close"
        "visible"->"eye";"hidden"->"eye_off";"lock"->"lock";"unlock","unlock_all"->"unlock"
        "delete","clear"->"trash";"background","page_background"->"background"
        "fullscreen","stress"->"expand";"fit"->"fit";"files","open","recent"->"folder"
        "pages","export_png","export_jpg","page_picker"->"file";"export_pdf","pdf","pdf_crop_page"->"file_pdf";"save"->"save"
        "up","front","thicker","move_up"->"arrow_up";"down","back","thinner","move_down"->"arrow_down"
        "convert","restore_erased","cache"->"convert";"color","palette"->"palette";"fill"->"bucket"
        "search","google_search_settings"->"search";"graph","coordinates","domain"->"graph"
        "measure","ruler","calibration","thresholds"->"ruler";"compass"->"compass";"protractor"->"protractor"
        "voice_assistant","voice_behavior"->"mic";"mute"->"mic_off";"help","about","info","report"->"info"
        "lab","periodic","periodic_table"->"atom";"games"->"game";"dice"->"dice"
        "settings","engineering","advanced_board","ui_size","history_settings","guide_slope","secret_studio","maboox"->"settings"
        "language","models"->"globe";"copy"->"copy";"duplicate"->"duplicate";"bold"->"bold";"italic"->"italic"
        "align_start"->"align_left";"align_center"->"align_center";"align_end"->"align_right"
        "pie_settings"->"pie";"image"->"image";"sticky"->"sticky";"mind_map"->"mind_map";"paste"->"paste"
        "crop","pdf_crop"->"crop";"scoreboard"->"trophy";"stop"->"stop";"swap"->"swap";"content"->"content"
        "spotlight"->"spotlight";"table"->"table"
        else->key
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

fun Context.colorPalette(initial:Int,colors:List<Int>,add:(()->Unit)?,choose:(Int)->Unit):View {
    val r=row();var selected=initial;val cells=mutableListOf<View>()
    colors.distinct().forEach{color->
        val cell=object:View(this){private val p=Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(c:Canvas){val radius=dp(14).toFloat();p.color=color;p.style=Paint.Style.FILL;c.drawCircle(width/2f,height/2f,radius,p);p.color=if(selected==color)TEAL else OUTLINE_STRONG;p.style=Paint.Style.STROKE;p.strokeWidth=dp(if(selected==color)3 else 1).toFloat();c.drawCircle(width/2f,height/2f,radius+dp(3),p)}
        }.apply{contentDescription=String.format(java.util.Locale.US,"#%06X",color and 0xffffff);tooltipText=contentDescription;isFocusable=true;setOnClickListener{selected=color;choose(color);cells.forEach{it.invalidate()}}}
        cells.add(cell);r.addView(cell,LinearLayout.LayoutParams(dp(46),dp(48)))
    }
    if(add!=null){val plus=WorkspaceIcon(this,"insert",tr("Add color to this lesson","افزودن رنگ به این فایل")){add()}
    plus.background=rounded(SURFACE,dp(24).toFloat(),OUTLINE);r.addView(plus,LinearLayout.LayoutParams(dp(46),dp(48)))}
    return scrollRow(r)
}

class OptionAdapter(private val c:Context,values:List<String>):ArrayAdapter<String>(c,android.R.layout.simple_spinner_dropdown_item,values){
    private fun style(v:View):View{if(v is TextView){v.textSize=16f*c.uiScale();v.setTextColor(NAVY);v.setPadding(c.dp(12),c.dp(10),c.dp(12),c.dp(10));Fonts.bind(v)};return v}
    override fun getView(position:Int,convertView:View?,parent:ViewGroup):View=style(super.getView(position,convertView,parent))
    override fun getDropDownView(position:Int,convertView:View?,parent:ViewGroup):View=style(super.getDropDownView(position,convertView,parent))
}
