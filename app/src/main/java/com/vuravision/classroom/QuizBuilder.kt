package com.vuravision.classroom

import android.app.Dialog
import android.content.Context
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Class quizzes kept in the lesson: write your own, keep one the AI made, play any of them with the whole class.
 * Questions live in the lesson file, so a quiz prepared at home is there in class, and next year.
 */
object QuizBuilder {
    fun play(c:Context,quiz:SavedQuiz)=ClassQuiz.show(c,quiz.title,quiz.questions.map{AiTasks.Question(it.q,it.options,it.answer)})

    fun manager(c:Context,quizzes:()->List<SavedQuiz>,save:(SavedQuiz)->Unit,delete:(String)->Unit,fromAi:(()->Unit)?){
        val list=c.column().apply{setPadding(c.dp(16),c.dp(8),c.dp(16),c.dp(8))}
        lateinit var dialog:androidx.appcompat.app.AlertDialog
        fun refresh(){
            list.removeAllViews()
            val all=quizzes()
            if(all.isEmpty())list.addView(c.label(c.tr("No quizzes in this lesson yet. Write one, or let the AI make one from the board.","هنوز آزمونکی در این درس نیست. یکی بنویسید، یا بگذارید هوش از روی تخته بسازد."),14f,MUTED).apply{setPadding(c.dp(4),c.dp(8),c.dp(4),c.dp(16))})
            all.forEach{q->list.addView(c.row().apply{
                setPadding(c.dp(12),c.dp(8),c.dp(8),c.dp(8));background=rounded(PAPER,c.dp(14).toFloat())
                addView(c.column().apply{
                    addView(c.label(q.title.ifBlank{c.tr("Untitled quiz","آزمونک بی‌نام")},16f,NAVY,true))
                    addView(c.label(c.tr("${q.questions.size} questions","${q.questions.size} سؤال"),13f,MUTED))
                },LinearLayout.LayoutParams(0,-2,1f))
                addView(c.button(c.tr("Play","اجرا"),true){dialog.dismiss();play(c,q)},LinearLayout.LayoutParams(-2,c.dp(48)))
                addView(c.button(c.tr("Edit","ویرایش")){editor(c,q){save(it);refresh()}},LinearLayout.LayoutParams(-2,c.dp(48)).apply{marginStart=c.dp(6)})
                addView(WorkspaceIcon(c,"delete",c.s("delete")){MaterialAlertDialogBuilder(c).setMessage(c.tr("Delete “${q.title.ifBlank{"…"}}”?","«${q.title.ifBlank{"…"}}» حذف شود؟")).setNegativeButton(c.s("cancel"),null).setPositiveButton(c.s("delete")){_,_->delete(q.id);refresh()}.show().also{Fonts.onShown(it)}},LinearLayout.LayoutParams(c.dp(48),c.dp(48)).apply{marginStart=c.dp(4)})
            },LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=c.dp(8)})}
            list.addView(c.row().apply{
                addView(c.button(c.tr("New quiz","آزمونک جدید"),true){if(quizzes().size>=50)android.widget.Toast.makeText(c,c.tr("This lesson already has 50 quizzes","این درس ۵۰ آزمونک دارد"),android.widget.Toast.LENGTH_LONG).show() else editor(c,null){save(it);refresh()}},LinearLayout.LayoutParams(0,c.dp(52),1f))
                if(fromAi!=null)addView(c.button(c.tr("Make with AI","ساخت با هوش")){dialog.dismiss();fromAi()},LinearLayout.LayoutParams(0,c.dp(52),1f).apply{marginStart=c.dp(8)})
            },LinearLayout.LayoutParams(-1,-2).apply{topMargin=c.dp(8)})
        }
        refresh()
        dialog=MaterialAlertDialogBuilder(c).setTitle(c.s("class_quiz")).setView(ScrollView(c).apply{addView(list)}).setNegativeButton(c.s("close"),null).show().also{Fonts.onShown(it)}
    }

    /** One card per question: the question, up to four answers, and which one is right (A–D). */
    fun editor(c:Context,existing:SavedQuiz?,save:(SavedQuiz)->Unit){
        val d=Dialog(c,android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        val root=c.column().apply{setBackgroundColor(PAPER);fitsSystemWindows=true;setPadding(c.dp(20),c.dp(12),c.dp(20),c.dp(10))}
        val title=c.field(existing?.title.orEmpty(),c.tr("Quiz title","عنوان آزمونک")).apply{setSingleLine(true)}
        val cards=c.column()
        class Card(val view:View,val question:EditText,val options:List<EditText>,var answer:Int)
        val all=mutableListOf<Card>()
        fun addCard(q:SavedQuestion?){
            val box=c.column().apply{setPadding(c.dp(14),c.dp(10),c.dp(14),c.dp(12));background=rounded(SURFACE,c.dp(18).toFloat(),OUTLINE)}
            val question=c.field(q?.q.orEmpty(),c.tr("Question","سؤال")).apply{inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE;minLines=1;maxLines=4}
            box.addView(question)
            val options=(0..3).map{i->c.field(q?.options?.getOrNull(i).orEmpty(),c.tr("Answer ${"ABCD"[i]}","پاسخ ${"ABCD"[i]}")).apply{setSingleLine(true)}}
            val grid=c.column();options.chunked(2).forEach{pair->grid.addView(c.row().apply{pair.forEach{addView(it,LinearLayout.LayoutParams(0,-2,1f).apply{marginEnd=c.dp(6)})}})}
            box.addView(grid)
            val card=Card(box,question,options,q?.answer?:0)
            box.addView(c.row().apply{
                addView(c.label(c.tr("Correct:","درست:"),14f,MUTED,true))
                addView(c.segmented(listOf("0" to "A","1" to "B","2" to "C","3" to "D"),card.answer.toString()){card.answer=it.toInt()},LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=c.dp(8)})
                addView(WorkspaceIcon(c,"delete",c.s("delete")){cards.removeView(box);all.remove(card)},LinearLayout.LayoutParams(c.dp(48),c.dp(48)))
            },LinearLayout.LayoutParams(-1,-2).apply{topMargin=c.dp(6)})
            all.add(card);cards.addView(box,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=c.dp(10)})
        }
        (existing?.questions?.takeIf{it.isNotEmpty()}?:listOf(null,null)).forEach{addCard(it)}
        root.addView(c.label(if(existing==null)c.tr("New quiz","آزمونک جدید")else c.tr("Edit quiz","ویرایش آزمونک"),24f,NAVY,true))
        root.addView(title)
        root.addView(ScrollView(c).apply{addView(c.column().apply{
            addView(cards)
            addView(c.button(c.tr("Add question","افزودن سؤال")){addCard(null)},LinearLayout.LayoutParams(-2,c.dp(52)))
        })},LinearLayout.LayoutParams(-1,0,1f))
        val error=c.label("",14f,DANGER)
        root.addView(c.row().apply{
            addView(error,LinearLayout.LayoutParams(0,-2,1f))
            addView(c.button(c.s("cancel")){d.dismiss()},LinearLayout.LayoutParams(c.dp(140),c.dp(56)).apply{marginEnd=c.dp(8)})
            addView(c.button(c.tr("Save","ذخیره"),true){
                val questions=all.mapNotNull{card->
                    val options=card.options.map{it.text.toString().trim()}
                    val text=card.question.text.toString().trim()
                    if(text.isEmpty()&&options.all{it.isEmpty()})return@mapNotNull null
                    val kept=options.withIndex().filter{it.value.isNotEmpty()}
                    val answer=kept.indexOfFirst{it.index==card.answer}
                    if(text.isEmpty()||kept.size<2||answer<0||kept.map{it.value}.distinct().size!=kept.size){
                        error.text=c.tr("Each question needs text, two different answers and a filled correct answer.","هر سؤال باید متن، دو پاسخ متفاوت و پاسخ درستِ پرشده داشته باشد.");return@button}
                    SavedQuestion(text.take(1000),kept.map{it.value.take(300)},answer)
                }
                if(questions.isEmpty()){error.text=c.tr("Write at least one question.","دست‌کم یک سؤال بنویسید.");return@button}
                if(questions.size>100){error.text=c.tr("A quiz can hold up to 100 questions.","هر آزمونک حداکثر ۱۰۰ سؤال دارد.");return@button}
                save(SavedQuiz(existing?.id?:newId(),title.text.toString().trim().take(200),questions));d.dismiss()
            },LinearLayout.LayoutParams(c.dp(160),c.dp(56)))
        })
        d.setContentView(root);d.show()
    }
}
