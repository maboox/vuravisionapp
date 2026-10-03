package com.vuravision.classroom

import android.content.Context
import android.graphics.Color
import android.view.*
import android.widget.*
import kotlin.math.*

/** Native scrolling; only nearby sheets own drawing surfaces and bitmap backing. */
class PdfPane(
    context:Context, val state:PdfWorkspaceState, private val media:Media,
    private val currentBoard:()->Board,
    private val activate:(Board)->Unit,
    private val edited:()->Unit,
    private val viewChanged:()->Unit,
    private val menu:()->Unit,
):LinearLayout(context) {
    private val sheets=LinearLayout(context).apply{orientation=VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_LTR}
    private val scroll=ScrollView(context).apply{isFillViewport=false;addView(sheets);setBackgroundColor(0xffe1e5eb.toInt())}
    private val horizontal=object:HorizontalScrollView(context){
        private var navigating=false
        private var singleScroll=false
        private var gestureZoom=1f
        private var focusY=0f
        private var focusX=0f
        private var lastY=0f
        private val scale=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
            override fun onScaleBegin(d:ScaleGestureDetector):Boolean{gestureZoom=state.zoom;focusY=d.focusY;focusX=d.focusX;return true}
            override fun onScale(d:ScaleGestureDetector):Boolean{
                gestureZoom=(gestureZoom*d.scaleFactor).coerceIn(1f,3f)
                val factor=gestureZoom/state.zoom
                scroll.pivotX=scrollX+focusX;scroll.pivotY=focusY
                scroll.scaleX=factor;scroll.scaleY=factor
                return true
            }
            override fun onScaleEnd(d:ScaleGestureDetector){
                scroll.scaleX=1f;scroll.scaleY=1f
                setZoom(gestureZoom,focusY,focusX)
            }
        })
        override fun dispatchTouchEvent(e:MotionEvent):Boolean {
            val b=currentBoard()
            if(e.actionMasked==MotionEvent.ACTION_DOWN){
                navigating=false
                // Touch mode means two-tip drawing is off. Broad contact scrolls, including palm-size contact.
                singleScroll=hand || b.tool=="pan" || (b.touchMode && PdfTouch.scrolls(b.profile,e.getToolType(0),e.getTouchMajor(0)))
                lastY=e.y
            }
            if(e.pointerCount>=2 && b.profile.multiTouch){
                if(!navigating){
                    val cancel=MotionEvent.obtain(e);cancel.action=MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(cancel);cancel.recycle();navigating=true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                scale.onTouchEvent(e)
                val y=(e.getY(0)+e.getY(1))/2
                if(e.actionMasked==MotionEvent.ACTION_MOVE && !scale.isInProgress)scroll.scrollBy(0,(lastY-y).toInt())
                lastY=y
                return true
            }
            if(navigating){
                scale.onTouchEvent(e)
                if(e.actionMasked==MotionEvent.ACTION_UP || e.actionMasked==MotionEvent.ACTION_CANCEL){navigating=false;parent?.requestDisallowInterceptTouchEvent(false)}
                return true
            }
            if(singleScroll){
                if(e.actionMasked==MotionEvent.ACTION_DOWN || e.actionMasked==MotionEvent.ACTION_UP)activateVisible()
                // Dispatch directly to native scroll views; no tentative ink or selection is created.
                scroll.onTouchEvent(e)
                if(state.zoom>1f)onTouchEvent(e)
                if(e.actionMasked==MotionEvent.ACTION_UP || e.actionMasked==MotionEvent.ACTION_CANCEL){singleScroll=false;parent?.requestDisallowInterceptTouchEvent(false)}
                return true
            }
            return super.dispatchTouchEvent(e)
        }
    }.apply{isFillViewport=true;addView(scroll,ViewGroup.LayoutParams(-1,-1))}
    private val frames=mutableListOf<FrameLayout>()
    private val boards=mutableMapOf<Int,Board>()
    private val stores=mutableMapOf<Int,Store>()
    private val pageNumber=context.button(""){}
    private val handButton=context.button(context.tr("Hand","دست")){toggleHand()}
    private val restoreScroll=state.scroll
    private var restored=false
    private var resizing=false
    private var interactiveResize=false
    private var lastZoom=state.zoom
    private var pendingAnchor:Pair<Int,Float>?=null
    private var pendingScroll:Pair<Int,Int>?=null
    private var disposed=false
    var hand=false
    init {
        orientation=VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_LTR
        setBackgroundColor(Color.WHITE)
        val bar=context.row().apply{pad(3)}
        bar.addView(context.button("☰ PDF"){menu()})
        bar.addView(context.button("‹"){goTo(state.current-1)})
        bar.addView(pageNumber,LayoutParams(-2,-2))
        bar.addView(handButton)
        bar.addView(context.button("›"){goTo(state.current+1)})
        bar.addView(context.button("−"){setZoom((state.zoom-.25f).coerceAtLeast(1f))})
        bar.addView(context.button("＋"){setZoom((state.zoom+.25f).coerceAtMost(3f))})
        addView(HorizontalScrollView(context).apply{addView(bar);isFillViewport=true;isHorizontalScrollBarEnabled=false})
        pageNumber.setOnClickListener {
            val input=context.field((state.current+1).toString()).apply{inputType=android.text.InputType.TYPE_CLASS_NUMBER}
            com.google.android.material.dialog.MaterialAlertDialogBuilder(context).setTitle(context.tr("Go to PDF page","رفتن به صفحهٔ PDF")).setView(input)
                .setPositiveButton(context.s("apply")){_,_->input.text.toString().toIntOrNull()?.let{goTo(it-1)}}.setNegativeButton(context.s("cancel"),null).show()
        }
        addView(horizontal,LayoutParams(-1,0,1f))
        state.sheets.forEachIndexed{i,_->
            val frame=FrameLayout(context).apply{setBackgroundColor(Color.WHITE);contentDescription="PDF ${i+1}"}
            frame.addView(context.label("${i+1}",20f,MUTED).apply{gravity=Gravity.CENTER},FrameLayout.LayoutParams(-1,-1))
            frames.add(frame);sheets.addView(frame,LayoutParams(-1,1).apply{setMargins(0,context.dp(8),0,context.dp(8))})
        }
        scroll.setOnScrollChangeListener{_,_,y,_,_->
            state.scroll=y;materialize();viewChanged()
        }
        horizontal.addOnLayoutChangeListener{_,l,_,r,_,ol,_,or,_->if(r-l!=or-ol)resize()}
        sheets.addOnLayoutChangeListener{_,_,_,_,_,_,_,_,_->
            pendingScroll?.let{(y,x)->pendingScroll=null;scroll.scrollTo(0,y);horizontal.scrollTo(x,0)}
            pendingAnchor?.let{(index,fraction)->pendingAnchor=null;frames.getOrNull(index)?.let{f->scroll.scrollTo(0,f.top+(fraction*f.height).toInt())}}
            materialize()
        }
        post{resize()}
    }
    fun toggleHand(){hand=!hand;handButton.text=if(hand)context.tr("Write","نوشتن")else context.tr("Hand","دست")}
    fun writingMode(){hand=false;handButton.text=context.tr("Hand","دست")}
    private fun activateVisible(){boards[state.current]?.let{b->if(b!==currentBoard()){b.copyToolsFrom(currentBoard());activate(b)}}}
    fun activeBoard():Board?=boards[state.current]
    fun goTo(index:Int){if(index !in frames.indices)return;state.current=index;scroll.smoothScrollTo(0,frames[index].top);updateNumber();materialize()}
    private fun updateNumber(){pageNumber.text="${state.current+1} / ${state.sheets.size}"}
    fun setZoom(value:Float,focusY:Float=horizontal.height/2f,focusX:Float=horizontal.width/2f){
        val next=value.coerceIn(1f,3f);if(next==state.zoom)return
        val factor=next/state.zoom;val y=((scroll.scrollY+focusY)*factor-focusY).toInt()
        val x=((horizontal.scrollX+focusX)*factor-focusX).toInt()
        pendingScroll=y to x;state.zoom=next;resize();viewChanged()
    }
    fun fitWidth(){state.zoom=1f;resize();horizontal.scrollTo(0,0);viewChanged()}
    private fun resize(){
        if(disposed || resizing || horizontal.width==0)return
        resizing=true
        val pageWidth=(horizontal.width*state.zoom).toInt().coerceAtLeast(1)
        if(restored && state.zoom==lastZoom && frames.firstOrNull()?.width!=pageWidth){
            val index=frames.indexOfFirst{it.bottom>=scroll.scrollY}.coerceAtLeast(0)
            val frame=frames[index]
            if(frame.height>0)pendingAnchor=index to ((scroll.scrollY-frame.top).toFloat()/frame.height)
        }
        lastZoom=state.zoom
        scroll.layoutParams=scroll.layoutParams.apply{width=pageWidth;height=ViewGroup.LayoutParams.MATCH_PARENT}
        state.sheets.forEachIndexed{i,s->frames[i].layoutParams=frames[i].layoutParams.apply{width=pageWidth;height=(pageWidth*s.height/s.width).toInt().coerceIn(1,16384)}}
        resizing=false
        post{materialize()}
    }
    private fun materialize(){
        if(disposed || scroll.height==0)return
        if(frames.firstOrNull()?.height==0){postOnAnimation{materialize()};return}
        if(!restored){restored=true;scroll.scrollTo(0,restoreScroll)}
        val top=scroll.scrollY;val bottom=top+scroll.height
        val visible=frames.indices.filter{frames[it].bottom>=top-scroll.height/2 && frames[it].top<=bottom+scroll.height/2}
        state.current=frames.indices.firstOrNull{frames[it].bottom>=top+scroll.height/3}?:state.sheets.lastIndex
        updateNumber()
        boards.keys.toList().filter{it !in visible}.forEach{i->
            val b=boards.getValue(i)
            if(!b.isDrawing && b!==currentBoard()) {frames[i].removeView(b);b.releaseBacking();boards.remove(i)}
        }
        visible.forEach{i->
            if(i !in boards){
                val sheet=state.sheets[i]
                val store=stores.getOrPut(i){Store(Lesson(pages=mutableListOf(sheet.page)))}
                val b=Board(context,store,Renderer(media)).apply{interactiveResize=this@PdfPane.interactiveResize;fixedPageWidth=sheet.width;fixedPageHeight=sheet.height;pdfBaseId=sheet.page.items.first().id;copyToolsFrom(currentBoard())}
                b.onActivate={b.copyToolsFrom(currentBoard());activate(b)}
                b.onSelection={if(b===currentBoard())activate(b)}
                store.changed={sheet.page=store.page;state.revision++;if(b.isCommitting)b.invalidate()else b.sceneChanged();edited();if(b===currentBoard())activate(b)}
                boards[i]=b;frames[i].addView(b,FrameLayout.LayoutParams(-1,-1))
            }
        }
        if(currentBoard().pdfBaseId!=null && !currentBoard().isDrawing)activateVisible()
        // Low-resolution neighbors do not block high-priority visible-page rendering.
        media.prefetchPdf(state.asset,state.current-1,state.sheets.size)
        media.prefetchPdf(state.asset,state.current+1,state.sheets.size)
    }
    fun beginResize(){interactiveResize=true;boards.values.forEach{it.interactiveResize=true;it.releaseBacking();it.invalidate()}}
    fun endResize(){interactiveResize=false;boards.values.forEach{it.finishResize()}}
    fun mediaReady(asset:String){if(asset==state.asset)boards.values.forEach{it.sceneChanged()}}
    fun dispose(){disposed=true;boards.values.forEach{it.reset();it.releaseBacking()};boards.clear();stores.clear()}
}
object PdfTouch {
    fun scrolls(profile:TouchProfile,tool:Int,major:Float):Boolean = tool!=MotionEvent.TOOL_TYPE_STYLUS && tool!=MotionEvent.TOOL_TYPE_ERASER && major>profile.thin
}
