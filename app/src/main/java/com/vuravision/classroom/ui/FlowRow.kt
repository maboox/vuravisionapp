package com.vuravision.classroom.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * Lays children out left to right and wraps to a new line when the width runs out, each line centered.
 * Used where every control must stay visible without horizontal scrolling (narrow PDF or split panels).
 */
class FlowRow(context:Context,private val gap:Int=context.dp(4)):ViewGroup(context){
    override fun onMeasure(widthSpec:Int,heightSpec:Int){
        val limit=if(MeasureSpec.getMode(widthSpec)==MeasureSpec.UNSPECIFIED)Int.MAX_VALUE else MeasureSpec.getSize(widthSpec)-paddingLeft-paddingRight
        var lineWidth=0;var lineHeight=0;var widest=0;var height=0
        children().forEach{child->
            measureChild(child,MeasureSpec.makeMeasureSpec(limit.coerceAtMost(1 shl 20),MeasureSpec.AT_MOST),heightSpec)
            val w=child.measuredWidth
            if(lineWidth>0&&lineWidth+gap+w>limit){widest=maxOf(widest,lineWidth);height+=lineHeight+gap;lineWidth=0;lineHeight=0}
            lineWidth+=(if(lineWidth>0)gap else 0)+w;lineHeight=maxOf(lineHeight,child.measuredHeight)
        }
        widest=maxOf(widest,lineWidth);height+=lineHeight
        setMeasuredDimension(resolveSize(widest+paddingLeft+paddingRight,widthSpec),resolveSize(height+paddingTop+paddingBottom,heightSpec))
    }
    override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int){
        val limit=r-l-paddingLeft-paddingRight
        val lines=mutableListOf<MutableList<View>>(mutableListOf());var used=0
        children().forEach{child->
            if(used>0&&used+gap+child.measuredWidth>limit){lines.add(mutableListOf());used=0}
            used+=(if(used>0)gap else 0)+child.measuredWidth;lines.last().add(child)
        }
        var y=paddingTop
        lines.filter{it.isNotEmpty()}.forEach{line->
            val width=line.sumOf{it.measuredWidth}+gap*(line.size-1);val height=line.maxOf{it.measuredHeight}
            var x=paddingLeft+(limit-width)/2
            line.forEach{child->val top=y+(height-child.measuredHeight)/2;child.layout(x,top,x+child.measuredWidth,top+child.measuredHeight);x+=child.measuredWidth+gap}
            y+=height+gap
        }
    }
    private fun children()=(0 until childCount).map{getChildAt(it)}.filter{it.visibility!=View.GONE}
}
