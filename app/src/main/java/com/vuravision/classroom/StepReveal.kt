package com.vuravision.classroom

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout

/**
 * A small bar at the bottom of the board for a step-by-step solution: each Next writes the next step on the board,
 * so the class follows one step at a time instead of seeing the whole answer at once. "All" writes the rest.
 */
@SuppressLint("ViewConstructor")
class StepReveal(context:Context,private val steps:List<String>,private val reveal:(Int)->Unit,close:()->Unit):LinearLayout(context){
    private var shown=0
    private val status=context.label("",16f,NAVY,true).apply{gravity=Gravity.CENTER_VERTICAL}
    private val next=context.button(context.tr("Next step","گام بعد"),true){step()}
    private val all=context.button(context.tr("All","همه")){while(shown<steps.size)step()}
    init{
        orientation=HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;elevation=context.dp(8).toFloat()
        background=rounded(SURFACE,context.dp(24).toFloat(),OUTLINE);setPadding(context.dp(16),context.dp(8),context.dp(8),context.dp(8))
        addView(status,LayoutParams(-2,-2).apply{marginEnd=context.dp(12)})
        addView(next,LayoutParams(context.dp(150),context.dp(52)))
        addView(all,LayoutParams(context.dp(90),context.dp(52)).apply{marginStart=context.dp(6)})
        addView(WorkspaceIcon(context,"close",context.s("close")){close()},LayoutParams(context.dp(52),context.dp(52)).apply{marginStart=context.dp(6)})
        refresh()
    }
    private fun step(){if(shown>=steps.size)return;reveal(shown);shown++;refresh()}
    private fun refresh(){
        status.text=context.tr("Step $shown of ${steps.size}","گام $shown از ${steps.size}")
        next.isEnabled=shown<steps.size;all.isEnabled=shown<steps.size
    }
}
