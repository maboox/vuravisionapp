package com.vuravision.classroom.games

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.vuravision.classroom.ai.AiTasks
import com.vuravision.classroom.ui.MUTED
import com.vuravision.classroom.ui.NAVY
import com.vuravision.classroom.ui.OUTLINE
import com.vuravision.classroom.ui.PAPER
import com.vuravision.classroom.room.PollServer
import com.vuravision.classroom.R
import com.vuravision.classroom.ui.SURFACE
import com.vuravision.classroom.ui.SURFACE_VARIANT
import com.vuravision.classroom.room.Sharing
import com.vuravision.classroom.ui.TEAL
import com.vuravision.classroom.ui.button
import com.vuravision.classroom.ui.card
import com.vuravision.classroom.ui.column
import com.vuravision.classroom.ui.dp
import com.vuravision.classroom.ui.label
import com.vuravision.classroom.board.reveal
import com.vuravision.classroom.ui.rounded
import com.vuravision.classroom.ui.row
import com.vuravision.classroom.ui.s
import com.vuravision.classroom.ui.tr

/**
 * A quiz for the whole class, made from the board: one big question, four large answer cards (A–D) that students call
 * out, and the teacher taps the class's answer to reveal it. Built for reading from the back of the room and for one
 * person at the panel, unlike the two-player duels.
 */
object ClassQuiz {
    private val letters=listOf("A","B","C","D")
    private val tints=listOf(0xff2563eb.toInt(),0xffdc2626.toInt(),0xff16a34a.toInt(),0xfff59e0b.toInt())

    /** [keep] adds "Save to lesson" for a quiz the AI just wrote, so it can be played again next lesson. */
    fun show(context:Context,title:String,questions:List<AiTasks.Question>,keep:(()->Unit)?=null,onClose:()->Unit={}):Dialog{
        val d=Dialog(context,android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        val root=context.column().apply{setBackgroundColor(PAPER);fitsSystemWindows=true;setPadding(context.dp(24),context.dp(16),context.dp(24),context.dp(12))}
        val progress=context.label("",15f,MUTED,true)
        val question=context.label("",34f,NAVY,true).apply{gravity=Gravity.CENTER;setPadding(context.dp(12),context.dp(16),context.dp(12),context.dp(20))}
        val grid=context.column()
        val footer=context.row().apply{gravity=Gravity.CENTER_VERTICAL}
        val result=context.label("",20f,TEAL,true)
        var index=0;var score=0;var answered=false
        lateinit var render:()->Unit
        // Answers from phones (item 83): off until the teacher taps "Phones"; then each card shows its live count.
        var poll:PollServer?=null
        val counts=mutableListOf<TextView>()
        val phonePanel=context.row().apply{visibility=View.GONE;gravity=Gravity.CENTER_VERTICAL;background=rounded(SURFACE,context.dp(18).toFloat(),OUTLINE);setPadding(context.dp(10),context.dp(8),context.dp(14),context.dp(8))}
        val phoneQr=android.widget.ImageView(context);val phoneText=context.label("",15f,NAVY,true)
        phonePanel.addView(phoneQr,LinearLayout.LayoutParams(context.dp(150),context.dp(150)))
        phonePanel.addView(phoneText,LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=context.dp(12)})
        val ticker=object:Runnable{override fun run(){val server=poll?:return
            val tally=server.tally();val total=tally.sum()
            counts.forEachIndexed{i,v->val n=tally.getOrElse(i){0};v.text=if(total==0)"" else "$n";v.visibility=if(total==0)View.GONE else View.VISIBLE}
            phoneText.text=context.tr("Scan to answer on your phone\n${server.people} phones · $total answers","با گوشی اسکن کنید و پاسخ دهید\n${server.people} گوشی · $total پاسخ")
            phonePanel.postDelayed(this,1000)}}
        val next=context.button(context.tr("Next","بعدی"),true){if(index<questions.size){index++;answered=false;render()}}
        val close=context.button(context.s("close")){d.dismiss()}
        fun card(i:Int,text:String,q:AiTasks.Question):View=context.row().apply{
            setPadding(context.dp(16),context.dp(14),context.dp(16),context.dp(14));minimumHeight=context.dp(110);isClickable=true;isFocusable=true
            val revealed=answered;val right=i==q.answer
            background=rounded(if(!revealed)SURFACE else if(right)0xffdcfce7.toInt() else SURFACE_VARIANT,context.dp(22).toFloat(),if(revealed&&right)0xff16a34a.toInt() else OUTLINE)
            addView(context.label(letters[i],26f,Color.WHITE,true).apply{gravity=Gravity.CENTER;background=rounded(tints[i],context.dp(18).toFloat())},LinearLayout.LayoutParams(context.dp(64),context.dp(64)))
            addView(context.label(text,24f,NAVY,true).apply{setPaddingRelative(context.dp(16),0,0,0)},LinearLayout.LayoutParams(0,-2,1f))
            addView(context.label("",26f,tints[i],true).apply{visibility=View.GONE;counts.add(this)})
            contentDescription=letters[i]+" · "+text
            setOnClickListener{
                if(answered)return@setOnClickListener
                answered=true;if(right)score++;poll?.revealed=q.answer
                Sounds.play(context,if(right)Sounds.Cue.CORRECT else Sounds.Cue.WRONG)
                result.text=if(right)context.tr("Correct!","درست است!")else context.tr("The answer is ${letters[q.answer]}","پاسخ درست: ${letters[q.answer]}")
                result.setTextColor(if(right)0xff16a34a.toInt() else 0xffdc2626.toInt())
                render()
            }
        }
        render={
            grid.removeAllViews();counts.clear()
            if(index>=questions.size){poll?.clear()
                progress.text=title
                question.text=context.tr("Score: $score of ${questions.size}","امتیاز: $score از ${questions.size}")
                result.text="";next.visibility=View.GONE
                if(score==questions.size&&questions.isNotEmpty())Sounds.play(context,Sounds.Cue.WIN)
                grid.addView(context.button(context.tr("Play again","دوباره"),true){index=0;score=0;answered=false;next.visibility=View.VISIBLE;render()},LinearLayout.LayoutParams(context.dp(220),context.dp(64)).apply{gravity=Gravity.CENTER_HORIZONTAL})
            }else{
                val q=questions[index]
                poll?.ask(index,q.text,q.options)
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
        root.addView(progress);root.addView(phonePanel,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=context.dp(4)});root.addView(question)
        root.addView(android.widget.ScrollView(context).apply{addView(grid);isFillViewport=true},LinearLayout.LayoutParams(-1,0,1f))
        footer.addView(result,LinearLayout.LayoutParams(0,-2,1f))
        if(keep!=null){lateinit var saveButton:TextView
            saveButton=context.button(context.tr("Save to lesson","ذخیره در درس")){keep();saveButton.isEnabled=false;saveButton.text=context.tr("Saved in the lesson","در درس ذخیره شد")}
            footer.addView(saveButton,LinearLayout.LayoutParams(-2,context.dp(56)).apply{marginEnd=context.dp(10)})}
        lateinit var phones:TextView
        phones=context.button(context.tr("Phones","گوشی‌ها")){
            if(poll!=null){poll?.stop();poll=null;phonePanel.visibility=View.GONE;phones.text=context.tr("Phones","گوشی‌ها");render();return@button}
            val server=try{PollServer().also{it.start(5000,false)}}catch(_:Exception){null}
            val url=server?.let{s->Sharing.localAddresses(context).firstOrNull()?.let{"http://$it:${s.listeningPort}/${s.token}"}}
            if(server==null||url==null){server?.stop();android.widget.Toast.makeText(context,context.s("no_network"),android.widget.Toast.LENGTH_LONG).show();return@button}
            poll=server;phoneQr.setImageBitmap(Sharing.qr(url));phoneQr.contentDescription=url;phonePanel.visibility=View.VISIBLE
            phones.text=context.tr("Stop phones","توقف گوشی‌ها");render();phonePanel.removeCallbacks(ticker);ticker.run()
        }
        footer.addView(phones,LinearLayout.LayoutParams(-2,context.dp(56)).apply{marginEnd=context.dp(10)})
        footer.addView(close,LinearLayout.LayoutParams(context.dp(150),context.dp(56)).apply{marginEnd=context.dp(10)})
        footer.addView(next,LinearLayout.LayoutParams(context.dp(180),context.dp(56)))
        root.addView(footer)
        render()
        d.setContentView(root);d.setOnDismissListener{phonePanel.removeCallbacks(ticker);poll?.stop();poll=null;onClose()};d.show()
        return d
    }
}
