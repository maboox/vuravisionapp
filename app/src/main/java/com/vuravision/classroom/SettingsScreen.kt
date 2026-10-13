package com.vuravision.classroom

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView

/** One entry on the settings screen. */
sealed class SettingRow(val title:String) {
    /** Opens a page of its own (fonts, calibration, ...); [value] shows the current state under the title. */
    class Page(title:String,val value:()->String={""},val open:()->Unit):SettingRow(title)
    /** Applies at once, no page in between. */
    class Toggle(title:String,val detail:String?=null,val get:()->Boolean,val set:(Boolean)->Unit):SettingRow(title)
    /** A few exclusive options shown side by side. */
    class Choice(title:String,val options:List<Pair<String,String>>,val get:()->String,val set:(String)->Unit):SettingRow(title)
}
class SettingsCategory(val key:String,val title:String,val icon:String,val rows:List<SettingRow>)

/**
 * All settings on one screen, grouped by category (a rail at the start side on wide screens, chips on a phone).
 * Simple options apply in place; only options that need room (fonts, calibration, the assistant's service) open a page.
 * Why: the old flat list of 13 entries had no grouping, and every option, even an on/off switch, was a window away.
 */
class SettingsScreen(private val activity:Activity,private val categories:List<SettingsCategory>,private val remember:(String)->Unit) {
    private val c=activity
    private var current=categories.first().key
    private val content=c.column().apply{setPadding(c.dp(20),c.dp(8),c.dp(20),c.dp(20))}
    private val rail=c.column()
    lateinit var dialog:Dialog
        private set

    fun show(initial:String?):Dialog {
        current=categories.firstOrNull{it.key==initial}?.key?:categories.first().key
        val metrics=c.resources.displayMetrics
        val wide=metrics.widthPixels>=c.dp(720)
        val root=c.column().apply{background=rounded(SURFACE,c.dp(24).toFloat());clipToOutline=true}
        val header=c.row().apply{setPadding(c.dp(20),c.dp(10),c.dp(8),c.dp(4))}
        header.addView(c.label(c.s("settings"),22f,NAVY,true),LinearLayout.LayoutParams(0,-2,1f))
        header.addView(WorkspaceIcon(c,"close",c.s("close")){dialog.dismiss()})
        root.addView(header)
        val scroll=ScrollView(c).apply{addView(content);isFillViewport=true}
        if(wide){
            val body=c.row().apply{gravity=Gravity.TOP}
            rail.setPadding(c.dp(12),c.dp(4),c.dp(8),c.dp(12))
            body.addView(rail,LinearLayout.LayoutParams(c.dp(220),-1))
            body.addView(View(c).apply{setBackgroundColor(OUTLINE)},LinearLayout.LayoutParams(c.dp(1),-1))
            body.addView(scroll,LinearLayout.LayoutParams(0,-1,1f))
            root.addView(body,LinearLayout.LayoutParams(-1,0,1f))
        }else{
            // Phone: categories become a wrapping row of chips above the content; nothing hides behind a scroll.
            root.addView(FlowRow(c,c.dp(6)).apply{setPadding(c.dp(16),0,c.dp(16),c.dp(8));tag="rail"}.also{flow->railHost=flow},LinearLayout.LayoutParams(-1,-2))
            root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        }
        dialog=Dialog(c).apply{
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(root)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(minOf(c.dp(1000),metrics.widthPixels-c.dp(32)),minOf(c.dp(720),metrics.heightPixels-c.dp(32)))
            // Values shown under rows may change on a page opened from here; refresh when this screen is back in front.
            window?.decorView?.viewTreeObserver?.addOnWindowFocusChangeListener{focused->if(focused)render()}
            show();Fonts.onShown(this)
        }
        render();return dialog
    }
    private var railHost:ViewGroup?=null
    /** Every row title across categories, and the ones that open a page (used by tests). */
    fun titles()=categories.flatMap{cat->cat.rows.map{it.title}}
    fun pages()=categories.flatMap{it.rows}.filterIsInstance<SettingRow.Page>().map{it.title}
    fun categoryKeys()=categories.map{it.key}
    fun showCategory(key:String)=select(key)
    /** Opens the page row titled [title] after switching to its category, as a tap there would. */
    fun open(title:String){
        val cat=categories.first{cat->cat.rows.any{it.title==title}};select(cat.key)
        (cat.rows.first{it.title==title} as SettingRow.Page).open()
    }
    private fun select(key:String){current=key;remember(key);render()}
    private fun render(){
        val host=railHost?:rail
        host.removeAllViews()
        categories.forEach{cat->
            val active=cat.key==current
            if(host is FlowRow)host.addView(c.button(cat.title,active){select(cat.key)},ViewGroup.LayoutParams(-2,c.dp(40)))
            else host.addView(c.row().apply{
                setPadding(c.dp(12),0,c.dp(12),0);minimumHeight=c.dp(52);isClickable=true;isFocusable=true;contentDescription=cat.title
                background=rounded(if(active)PRIMARY_CONTAINER else Color.TRANSPARENT,c.dp(14).toFloat())
                addView(ImageView(c).apply{setImageResource(IconCatalog.resource(cat.icon,active));setColorFilter(NAVY)},LinearLayout.LayoutParams(c.dp(24),c.dp(24)))
                addView(c.label(cat.title,16f,NAVY,active).apply{setPaddingRelative(c.dp(12),0,0,0)})
                setOnClickListener{select(cat.key)}
            },LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=c.dp(4)})
        }
        content.removeAllViews()
        val cat=categories.first{it.key==current}
        content.addView(c.label(cat.title,18f,NAVY,true).apply{setPadding(c.dp(4),c.dp(8),c.dp(4),c.dp(8))})
        cat.rows.forEach{row->content.addView(view(row),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=c.dp(8)})}
    }
    private fun view(row:SettingRow):View=when(row){
        is SettingRow.Page->c.column().apply{
            setPadding(c.dp(16),c.dp(10),c.dp(16),c.dp(10));minimumHeight=c.dp(56);isClickable=true;isFocusable=true
            contentDescription=row.title;tag="setting_page";gravity=Gravity.CENTER_VERTICAL
            background=rounded(PAPER,c.dp(14).toFloat())
            addView(c.label(row.title,16f,NAVY,true))
            row.value().takeIf{it.isNotBlank()}?.let{addView(c.label(it,13f,MUTED))}
            setOnClickListener{row.open()}
        }
        is SettingRow.Toggle->c.switchRow(row.title,row.get(),row.detail){row.set(it)}
        is SettingRow.Choice->c.column().apply{
            addView(c.label(row.title,15f,NAVY,true))
            addView(c.segmented(row.options,row.get()){row.set(it)},LinearLayout.LayoutParams(-1,-2).apply{topMargin=c.dp(4)})
        }
    }
}
