package com.vuravision.classroom

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A quiz for the whole class, made from the board: one big question, four large answer cards (A–D) that students call
 * out, and the teacher taps the class's answer to reveal it. Built for reading from the back of the room and for one
 * person at the panel, unlike the two-player duels.
 */
object ClassQuiz {
    private val letters=listOf("A","B","C","D")
    private val tints=listOf(0xff2563eb.toInt(),0xffdc2626.toInt(),0xff16a34a.toInt(),0xfff59e0b.toInt())

    fun show(context:Context,title:String,questions:List<AiTasks.Question>,onClose:()->Unit={}):Dialog{
        val d=Dialog(context,android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        val root=context.column().apply{setBackgroundColor(PAPER);fitsSystemWindows=true;setPadding(context.dp(24),context.dp(16),context.dp(24),context.dp(12))}
        val progress=context.label("",15f,MUTED,true)
        val question=context.label("",34f,NAVY,true).apply{gravity=Gravity.CENTER;setPadding(context.dp(12),context.dp(16),context.dp(12),context.dp(20))}
        val grid=context.column()
        val footer=context.row().apply{gravity=Gravity.CENTER_VERTICAL}
        val result=context.label("",20f,TEAL,true)
        var index=0;var score=0;var answered=false
        lateinit var render:()->Unit
        val next=context.button(context.tr("Next","بعدی"),true){if(index<questions.size){index++;answered=false;render()}}
        val close=context.button(context.s("close")){d.dismiss()}
        fun card(i:Int,text:String,q:AiTasks.Question):View=context.row().apply{
            setPadding(context.dp(16),context.dp(14),context.dp(16),context.dp(14));minimumHeight=context.dp(110);isClickable=true;isFocusable=true
            val revealed=answered;val right=i==q.answer
            background=rounded(if(!revealed)SURFACE else if(right)0xffdcfce7.toInt() else SURFACE_VARIANT,context.dp(22).toFloat(),if(revealed&&right)0xff16a34a.toInt() else OUTLINE)
            addView(context.label(letters[i],26f,Color.WHITE,true).apply{gravity=Gravity.CENTER;background=rounded(tints[i],context.dp(18).toFloat())},LinearLayout.LayoutParams(context.dp(64),context.dp(64)))
            addView(context.label(text,24f,NAVY,true).apply{setPaddingRelative(context.dp(16),0,0,0)},LinearLayout.LayoutParams(0,-2,1f))
            contentDescription=letters[i]+" · "+text
            setOnClickListener{
                if(answered)return@setOnClickListener
                answered=true;if(right)score++
                Sounds.play(context,if(right)Sounds.Cue.CORRECT else Sounds.Cue.WRONG)
                result.text=if(right)context.tr("Correct!","درست است!")else context.tr("The answer is ${letters[q.answer]}","پاسخ درست: ${letters[q.answer]}")
                result.setTextColor(if(right)0xff16a34a.toInt() else 0xffdc2626.toInt())
                render()
            }
        }
        render={
            grid.removeAllViews()
            if(index>=questions.size){
                progress.text=title
                question.text=context.tr("Score: $score of ${questions.size}","امتیاز: $score از ${questions.size}")
                result.text="";next.visibility=View.GONE
                if(score==questions.size&&questions.isNotEmpty())Sounds.play(context,Sounds.Cue.WIN)
                grid.addView(context.button(context.tr("Play again","دوباره"),true){index=0;score=0;answered=false;next.visibility=View.VISIBLE;render()},LinearLayout.LayoutParams(context.dp(220),context.dp(64)).apply{gravity=Gravity.CENTER_HORIZONTAL})
            }else{
                val q=questions[index]
                progress.text="$title · "+context.tr("Question ${index+1} of ${questions.size}","سؤال ${index+1} از ${questions.size}")
                question.text=q.text
                if(!answered)result.text=""
                next.isEnabled=answered;next.text=if(index==questions.lastIndex)context.tr("Results","نتیجه")else context.tr("Next","بعدی")
                // Two columns on a wide panel, so the four answers sit at hand height without scrolling.
                val wide=context.resources.configuration.screenWidthDp>=720
                q.options.chunked(if(wide)2 else 1).forEachIndexed{r,chunk->
                    val row=context.row()
                    chunk.forEachIndexed{c,text->val i=r*(if(wide)2 else 1)+c
                        row.addView(card(i,text,q),LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(context.dp(8),context.dp(8),context.dp(8),context.dp(8))})}
                    grid.addView(row)
                }
            }
        }
        root.addView(progress);root.addView(question)
        root.addView(android.widget.ScrollView(context).apply{addView(grid);isFillViewport=true},LinearLayout.LayoutParams(-1,0,1f))
        footer.addView(result,LinearLayout.LayoutParams(0,-2,1f))
        footer.addView(close,LinearLayout.LayoutParams(context.dp(150),context.dp(56)).apply{marginEnd=context.dp(10)})
        footer.addView(next,LinearLayout.LayoutParams(context.dp(180),context.dp(56)))
        root.addView(footer)
        render()
        d.setContentView(root);d.setOnDismissListener{onClose()};d.show()
        return d
    }
}
