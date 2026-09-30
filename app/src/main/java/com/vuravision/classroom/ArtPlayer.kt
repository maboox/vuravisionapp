package com.vuravision.classroom

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*
import com.google.gson.Gson

data class ArtStroke(val color:String, val width:Float, val points:List<List<Float>>)
data class Artwork(val width:Float, val height:Float, val strokes:List<ArtStroke>) {
    companion object {
        fun load(context:Context,key:String):Artwork {
            require(key in listOf("mona-vura","aurora"))
            val art=context.assets.open("studio/$key.json").bufferedReader().use{Gson().fromJson(it,Artwork::class.java)}
            require(art.width>0 && art.height>0 && art.strokes.isNotEmpty())
            require(art.strokes.all{s->s.width>0 && s.points.size>=2 && s.points.all{p->p.size==2 && p.all{it.isFinite()}}})
            return art
        }
        fun item(stroke:ArtStroke):Item {
            val x=stroke.points.minOf{it[0]};val y=stroke.points.minOf{it[1]}
            val w=(stroke.points.maxOf{it[0]}-x).coerceAtLeast(1f)
            val h=(stroke.points.maxOf{it[1]}-y).coerceAtLeast(1f)
            return Item(kind="ink",x=x,y=y,w=w,h=h,inkW=w,inkH=h,
                color=Color.parseColor(stroke.color),width=stroke.width,
                points=stroke.points.map{Point(it[0]-x,it[1]-y)}.toMutableList())
        }
    }
}

/** Uses exactly the same editable ink items and renderer as hand-written strokes.
 * One undo snapshot, bounded work per frame, no bitmap pasted onto the board. */
class ArtPlayer(private val context:Context,private val board:Board,
                private val store:Store,private val artwork:Artwork) {
    private val handler=Handler(Looper.getMainLooper())
    private val lesson=store.lesson
    private val page=Page(background="plain")
    private val items=artwork.strokes.map{Artwork.item(it)}
    private var index=0
    private var pointIndex=0
    private var current:Item?=null
    private var speed=1
    private var paused=false
    private var stopped=false
    private var notified=0L
    private val progress=ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal)
    private val state=context.label("",14f)
    private var pauseButton:Button?=null
    private val controls=Dialog(context,R.style.AppTheme)
    private val tick=object:Runnable {
        override fun run() {
            if(stopped||paused)return
            if(store.lesson!==lesson || store.page!==page){stop();return}
            var budget=32*speed
            while(budget>0 && index<items.size) {
                val source=items[index]
                if(current==null){
                    if(index==0)page.items.removeAll{it.alpha==0 && it.locked}
                    current=source.copy(id=newId(),points=mutableListOf())
                    page.items.add(current!!)
                }
                val n=minOf(budget,source.points.size-pointIndex)
                current!!.points.addAll(source.points.subList(pointIndex,pointIndex+n).map{it.copy()})
                pointIndex+=n;budget-=n
                if(pointIndex==source.points.size){index++;pointIndex=0;current=null}
            }
            board.sceneChanged()
            if(index==1 && current==null)board.fit()
            progress.progress=index
            state.text=context.tr("Drawing ${index}/${items.size} strokes","رسم خط‌ها: $index از ${items.size}")
            val now=android.os.SystemClock.elapsedRealtime()
            if(now-notified>500){store.changed();notified=now}
            if(index==items.size){board.fit();stop()}else handler.postDelayed(this,16)
        }
    }
    fun start() {
        check(!board.isDrawing)
        require(lesson.pages.size<200){context.tr("The lesson already has 200 pages","درس به سقف ۲۰۰ صفحه رسیده است")}
        require(lesson.pages.sumOf{it.items.size}+items.size<20000){context.tr("Create a new lesson before adding this drawing","قبل از افزودن نقاشی، درس تازه بسازید")}
        require(lesson.pages.sumOf{p->p.items.sumOf{it.points.size}}+items.sumOf{it.points.size}<1000000){context.tr("Create a new lesson before adding this drawing","قبل از افزودن نقاشی، درس تازه بسازید")}
        store.checkpoint()
        lesson.pages.add(page);lesson.current=lesson.pages.lastIndex
        // A transparent bounds guide stabilizes the viewport from the first frame.
        page.items.add(Item(kind="shape",shape="rectangle",x=0f,y=0f,w=artwork.width,h=artwork.height,alpha=0,locked=true))
        store.changed();board.reset();board.clearSelection();board.fit();board.isEnabled=false
        val content=context.column().apply{pad(12)}
        content.addView(state)
        progress.max=items.size;content.addView(progress,LinearLayout.LayoutParams(-1,context.dp(8)))
        val buttons=context.row();content.addView(buttons)
        pauseButton=context.button(context.tr("Pause","مکث")){
            paused=!paused
            (buttons.getChildAt(0) as Button).text=context.tr(if(paused)"Resume"else"Pause",if(paused)"ادامه"else"مکث")
            if(!paused)handler.post(tick)else handler.removeCallbacks(tick)
        }
        buttons.addView(pauseButton)
        buttons.addView(context.button("1×"){
            speed=if(speed==4)1 else speed*2
            (buttons.getChildAt(1) as Button).text="${speed}×"
        })
        buttons.addView(context.button(context.tr("Stop","توقف")){stop()})
        controls.setContentView(content);controls.setOnCancelListener{stop()}
        controls.show()
        controls.window?.apply{
            clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setGravity(Gravity.BOTTOM or Gravity.END)
            setLayout(context.dp(350).coerceAtMost(context.resources.displayMetrics.widthPixels),-2)
            setBackgroundDrawable(rounded(SURFACE,context.dp(16).toFloat(),OUTLINE))
        }
        handler.post(tick)
    }
    fun pause(){if(!stopped){paused=true;handler.removeCallbacks(tick);pauseButton?.text=context.tr("Resume","ادامه");state.text=context.tr("Paused · use Resume to continue","متوقف شده؛ برای ادامه دکمهٔ ادامه را بزنید")}}
    fun stop() {
        if(stopped)return
        stopped=true;handler.removeCallbacksAndMessages(null)
        if(store.lesson===lesson && lesson.pages.any{it===page}){
            page.items.removeAll{it.alpha==0 && it.locked}
            store.changed()
        }
        board.isEnabled=true;board.sceneChanged();controls.dismiss()
    }
}
