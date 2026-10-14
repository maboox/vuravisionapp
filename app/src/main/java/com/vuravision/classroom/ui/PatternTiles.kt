package com.vuravision.classroom.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import com.vuravision.classroom.board.Page
import com.vuravision.classroom.board.Pane
import com.vuravision.classroom.board.Renderer
import com.vuravision.classroom.pick
import com.vuravision.classroom.board.reset

/**
 * Page patterns picked by sight: each tile is a small live preview in the chosen background color, with its name below.
 * Names like "isometric" or "staff" mean little to many teachers; the picture does.
 */
@SuppressLint("ViewConstructor")
class PatternPreview(context:Context,private val renderer:Renderer,val key:String,private val color:()->Int):View(context){
    private val clip=Path()
    override fun onDraw(c:Canvas){
        val r=context.dp(12).toFloat();clip.reset();clip.addRoundRect(RectF(0f,0f,width.toFloat(),height.toFloat()),r,r,Path.Direction.CW)
        c.save();c.clipPath(clip)
        // One board unit per dp, as on the board at 100%: lines as crisp and as dense as the teacher will see them.
        val s=context.resources.displayMetrics.density;val units=width/s;c.scale(s,s)
        val page=Page(background=key,panes=mutableListOf(Pane(background=color(),pattern=key)))
        // Centered on the origin, so "Axes" shows its cross and not just a corner.
        val h=height/s;c.translate(units/2,h/2)
        renderer.background(c,page,RectF(-units/2,-h/2,units/2,h/2),color())
        c.restore()
    }
}

/** A grid of pattern tiles; [pick] gets the key. Call [PatternGrid.refresh] after the color changes. */
class PatternGrid(context:Context,renderer:Renderer,choices:List<Pair<String,String>>,selected:String,columns:Int,color:()->Int,pick:(String)->Unit):GridLayout(context){
    private val previews=mutableListOf<PatternPreview>()
    private val frames=mutableListOf<Pair<String,LinearLayout>>()
    var selected=selected
        private set
    init{
        columnCount=columns
        choices.forEach{(key,title)->
            val preview=PatternPreview(context,renderer,key,color);previews.add(preview)
            val frame=context.column().apply{
                gravity=Gravity.CENTER_HORIZONTAL;setPadding(context.dp(4),context.dp(4),context.dp(4),context.dp(6));isClickable=true;isFocusable=true
                contentDescription=title
                addView(preview,LinearLayout.LayoutParams(-1,context.dp(64)))
                addView(context.label(title,13f,NAVY).apply{gravity=Gravity.CENTER;maxLines=1},LinearLayout.LayoutParams(-1,-2).apply{topMargin=context.dp(4)})
                setOnClickListener{this@PatternGrid.selected=key;mark();pick(key)}
            }
            frames.add(key to frame)
            addView(frame,LayoutParams(spec(UNDEFINED,1f),spec(UNDEFINED,1f)).apply{width=0;setMargins(context.dp(3),context.dp(3),context.dp(3),context.dp(3))})
        }
        mark()
    }
    private fun mark(){frames.forEach{(key,frame)->frame.background=rounded(if(key==selected)0xffe2f2f0.toInt() else SURFACE,context.dp(16).toFloat(),if(key==selected)TEAL else OUTLINE);frame.isSelected=key==selected}}
    fun refresh()=previews.forEach{it.invalidate()}
}
