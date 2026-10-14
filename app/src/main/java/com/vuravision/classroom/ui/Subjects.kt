package com.vuravision.classroom.ui

import android.content.Context
import com.vuravision.classroom.labs.Science

/**
 * The teacher's subject (item 79). It only changes order: that subject's tools appear first in the Add panel and its
 * page templates first in the background list. Nothing is hidden, so a math teacher can still open the periodic table.
 */
object Subjects {
    val keys=listOf("general","math","science","language","humanities")
    fun current(c:Context)=c.getSharedPreferences("vura",0).getString("subject","general")?.takeIf{it in keys}?:"general"
    fun name(c:Context,key:String)=when(key){
        "math"->c.tr("Math","ریاضی");"science"->c.tr("Science","علوم");"language"->c.tr("Languages","زبان و ادبیات")
        "humanities"->c.tr("Social studies","علوم اجتماعی");else->c.tr("General","عمومی")
    }
    fun tools(key:String)=when(key){
        "math"->listOf("graph","coordinates","math","ruler","protractor","compass","set_square","dice")
        "science"->listOf("lab","periodic","graph","stopwatch","timer","image","ai_image","spotlight")
        "language"->listOf("text","sticky","mind_map","picker","class_quiz","timer","ai_quiz","spotlight")
        "humanities"->listOf("image","pdf","mind_map","sticky","class_quiz","spotlight","picker","timer")
        else->emptyList()
    }
    fun patterns(key:String)=when(key){
        "math"->listOf("grid","axes","isometric","dots")
        "science"->listOf("grid","axes","dots")
        "language"->listOf("handwriting","ruled")
        "humanities"->listOf("ruled","plain")
        else->emptyList()
    }
    /** [choices] reordered so the subject's templates lead; the rest keep their order. */
    fun <T> patternsFirst(key:String,choices:List<Pair<String,T>>)=patterns(key).mapNotNull{p->choices.firstOrNull{it.first==p}}+choices.filter{it.first !in patterns(key)}
}
