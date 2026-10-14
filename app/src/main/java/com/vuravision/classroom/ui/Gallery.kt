package com.vuravision.classroom.ui

import android.app.Dialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import com.vuravision.classroom.games.Games
import com.vuravision.classroom.labs.Labs
import com.vuravision.classroom.R
import com.vuravision.classroom.layoutDirection
import com.vuravision.classroom.pick

/**
 * The catalog screen shared by Labs and Games: title and result count on top, search, filter chips, a grid of cards
 * that reflows as filters change (no gaps where cards were hidden), and Close at the bottom, within reach on a tall panel.
 * Why one shared screen: both catalogs answer the same question ("what can I show the class now?") and should
 * look and behave the same.
 */
class Gallery(private val context:Context,title:String,intro:String,back:(()->Unit)?) {
    val dialog=Dialog(context,android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
    val count=context.label("",14f,MUTED)
    val filters=context.column()
    private val grid=context.column()
    private val root=context.column().apply{setBackgroundColor(PAPER);fitsSystemWindows=true;setPadding(context.dp(20),context.dp(10),context.dp(20),0)}
    val search:EditText=EditText(context).apply{
        hint=context.tr("Search","جستجو");contentDescription=hint;isSingleLine=true;textSize=16f*context.uiScale();setTextColor(NAVY);setHintTextColor(MUTED)
        background=rounded(SURFACE,context.dp(14).toFloat(),OUTLINE);setPadding(context.dp(16),0,context.dp(16),0);Fonts.bind(this,false)
        gravity=android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START;textDirection=View.TEXT_DIRECTION_LOCALE
    }
    /** 1 column on a phone, up to 4 on a wide panel; cards stay at least ~300dp wide. */
    val columns=context.resources.configuration.screenWidthDp.let{w->when{w<620->1;w<1000->2;w<1400->3;else->4}}
    init {
        val header=context.row()
        if(back!=null)header.addView(WorkspaceIcon(context,"menu_back",context.s("menu_back")){dialog.dismiss();back()}.apply{
            id=R.id.menu_back_button;scaleX=if(context.resources.configuration.layoutDirection==View.LAYOUT_DIRECTION_RTL)-1f else 1f})
        header.addView(context.label(title,26f,NAVY,true))
        header.addView(count,LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=context.dp(12)})
        root.addView(header)
        root.addView(context.label(intro,14f,MUTED).apply{maxLines=2})
        root.addView(search,LinearLayout.LayoutParams(-1,context.dp(48)).apply{setMargins(0,context.dp(8),0,context.dp(6))})
        root.addView(filters)
        root.addView(ScrollView(context).apply{addView(grid);isFillViewport=true},LinearLayout.LayoutParams(-1,0,1f))
        root.addView(context.row().apply{gravity=android.view.Gravity.END;setPadding(0,context.dp(6),0,context.dp(10))
            addView(context.button(context.s("close")){dialog.dismiss()},LinearLayout.LayoutParams(context.dp(160),context.dp(52)))})
        // The search field must not take focus on open: that would pop up the keyboard over the catalog on every visit.
        root.isFocusableInTouchMode=true;root.requestFocus()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        dialog.setContentView(root)
    }
    /** A row of choices; the current one is filled. Rebuild it after a pick with the new current value. */
    fun chips(row:FlowRow,options:List<Pair<String,String>>,current:String,pick:(String)->Unit){
        row.removeAllViews()
        options.forEach{(key,title)->row.addView(context.button(title,key==current){pick(key)},ViewGroup.LayoutParams(-2,context.dp(40)))}
    }
    fun chipRow()=FlowRow(context,context.dp(6)).also{filters.addView(it,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=context.dp(6)})}
    /** Lays out [cards] in rows; empty cells keep the last row's cards the same width as the others. */
    fun show(cards:List<View>){
        grid.removeAllViews()
        cards.chunked(columns).forEach{chunk->
            val row=context.row().apply{gravity=android.view.Gravity.TOP}
            chunk.forEach{card->(card.parent as? ViewGroup)?.removeView(card)
                row.addView(card,LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(context.dp(6),context.dp(6),context.dp(6),context.dp(6))})}
            repeat(columns-chunk.size){row.addView(View(context),LinearLayout.LayoutParams(0,1,1f).apply{setMargins(context.dp(6),0,context.dp(6),0)})}
            grid.addView(row)
        }
        if(cards.isEmpty())grid.addView(context.label(context.tr("Nothing found","چیزی پیدا نشد"),16f,MUTED).apply{setPadding(context.dp(8),context.dp(24),context.dp(8),context.dp(24))})
    }
    fun onSearch(changed:()->Unit)=search.addTextChangedListener(object:TextWatcher{
        override fun beforeTextChanged(s:CharSequence?,a:Int,b:Int,c:Int){};override fun onTextChanged(s:CharSequence?,a:Int,b:Int,c:Int){}
        override fun afterTextChanged(s:Editable?){changed()}
    })
    val query get()=search.text?.toString().orEmpty().trim()
}
