package com.vuravision.classroom

import android.content.Context
import android.graphics.*

/** Pressure-free styles: stable width, deterministic texture, no per-frame bitmap. */
object PenStyles {
    val selectable=listOf("round","smooth","marker","dashed","highlight")
    val keys=listOf("smooth","round","marker","pencil","fineliner","chalk","dashed","highlight")
    private val pencilEffect by lazy{DashPathEffect(floatArrayOf(1.6f,.8f),0f)}
    private val chalkEffect by lazy{DashPathEffect(floatArrayOf(3.2f,1.2f),0f)}
    fun name(c:Context,key:String)=when(key){
        "smooth"->c.tr("Smooth pen","قلم نرم");"round"->c.tr("Ink","جوهری");"marker"->c.tr("Marker","ماژیک");"pencil"->c.tr("Pencil","مداد")
        "fineliner"->c.tr("Fineliner","راپید");"chalk"->c.tr("Chalk","گچ");"dashed"->c.tr("Dashed","خط‌چین")
        else->c.tr("Highlighter","هایلایتر")
    }
    fun apply(p:Paint,o:Item){
        when(o.shape){
            "pencil"->{p.alpha=o.alpha*170/255;p.strokeWidth=o.width*.7f;p.pathEffect=pencilEffect}
            "fineliner"->{p.strokeWidth=o.width*.55f;p.strokeCap=Paint.Cap.ROUND}
            "chalk"->{p.alpha=o.alpha*200/255;p.strokeWidth=o.width*1.3f;p.pathEffect=chalkEffect}
        }
    }
}
