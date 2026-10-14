package com.vuravision.classroom.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import com.vuravision.classroom.pick

/*
 * Building blocks shared by tool sheets and Settings, so every page reads and behaves the same:
 * a choice between a few options is a segmented row (all options visible, the current one filled),
 * an on/off option is a switch with an optional one-line explanation, and groups get a small muted title.
 * Long paragraphs are avoided: each option says what it does in one line.
 */

/** A few mutually exclusive options side by side; tapping one applies it at once. */
fun Context.segmented(items:List<Pair<String,String>>,current:String?,pick:(String)->Unit):LinearLayout {
    val r=row()
    fun fill(selected:String?){
        r.removeAllViews()
        items.forEachIndexed{i,(key,title)->
            r.addView(button(title,key==selected){fill(key);pick(key)},LinearLayout.LayoutParams(0,dp(44),1f).apply{if(i>0)marginStart=dp(6)})
        }
    }
    fill(current);return r
}

/** An on/off option. The switch text is the option itself; [detail] explains it in one muted line. */
fun Context.switchRow(title:String,checked:Boolean,detail:String?=null,changed:(Boolean)->Unit):View=column().apply{
    addView(Switch(this@switchRow).apply{
        text=title;textSize=15f*uiScale();setTextColor(NAVY);isChecked=checked;minHeight=dp(48);Fonts.bind(this,false)
        setOnCheckedChangeListener{_,value->changed(value)}
    },LinearLayout.LayoutParams(-1,-2))
    if(detail!=null)addView(label(detail,13f,MUTED).apply{setPadding(dp(4),0,dp(56),dp(6))})
}

/** Small muted heading above a group of options. */
fun Context.sectionTitle(text:String)=label(text,13f,MUTED,true).apply{setPadding(dp(4),dp(14),dp(4),dp(6))}
