package com.vuravision.classroom

import android.graphics.PointF
import android.view.MotionEvent
import kotlin.math.hypot

/** Nearby finger taps and a stationary five-finger hold; movement becomes navigation. */
class BoardGestures(private val board:Board){
    var undoEnabled=true
    var pieEnabled=false
    var onPie:(Float,Float)->Unit={_,_->}
    private var mode="";private var started=0L;private var center=PointF();private var origins=mapOf<Int,PointF>()
    private var moved=false;private var firstTap=0L;private var firstCenter=PointF();private var pieShown=false
    private val hold=Runnable{if(mode=="five"&&!moved&&pieEnabled){pieShown=true;onPie(center.x,center.y)}}
    fun reset(){board.removeCallbacks(hold);mode="";origins=emptyMap();firstTap=0;moved=false;pieShown=false}
    private fun fingers(e:MotionEvent)= (0 until e.pointerCount).all{e.getToolType(it)==MotionEvent.TOOL_TYPE_FINGER && e.getTouchMajor(it)<board.profile.palm && (e.getTouchMajor(it)>board.profile.thin || e.getTouchMajor(it)<=0 && (e.pointerCount==5 || board.touchMode))}
    fun event(e:MotionEvent,navigateBoard:Boolean=true):Boolean {
        if(!undoEnabled&&!pieEnabled)return false
        if(e.actionMasked==MotionEvent.ACTION_CANCEL){val taken=mode.isNotEmpty();reset();return taken}
        if(e.actionMasked==MotionEvent.ACTION_DOWN){board.removeCallbacks(hold);mode="";moved=false;pieShown=false}
        if(e.actionMasked==MotionEvent.ACTION_POINTER_DOWN && fingers(e) && (e.pointerCount==5&&pieEnabled || e.pointerCount==2&&undoEnabled&&hypot(e.getX(0)-e.getX(1),e.getY(0)-e.getY(1))<=board.context.dp(140))){
            board.abortPendingGesture();board.removeCallbacks(hold)
            center=PointF((0 until e.pointerCount).map{e.getX(it)}.average().toFloat(),(0 until e.pointerCount).map{e.getY(it)}.average().toFloat())
            origins=(0 until e.pointerCount).associate{e.getPointerId(it) to PointF(e.getX(it),e.getY(it))};started=e.eventTime;moved=false
            mode=if(e.pointerCount==5)"five"else"two"
            if(mode=="five"){firstTap=0;board.postDelayed(hold,1000)}
            return true
        }
        if(mode.isEmpty())return false
        if(e.actionMasked==MotionEvent.ACTION_POINTER_DOWN && e.pointerCount !in listOf(2,5)){board.removeCallbacks(hold);mode="blocked";firstTap=0;return true}
        if(e.actionMasked==MotionEvent.ACTION_MOVE){
            if((0 until e.pointerCount).any{i->origins[e.getPointerId(i)]?.let{hypot(e.getX(i)-it.x,e.getY(i)-it.y)>board.context.dp(8)}?:true}){
                moved=true;firstTap=0;board.removeCallbacks(hold)
                if(mode=="two"){
                    if(navigateBoard){val p=origins.values.toList();board.beginGestureNavigation(center.x,center.y,if(p.size==2)hypot(p[0].x-p[1].x,p[0].y-p[1].y)else 0f)}
                    mode="navigate"
                }
            }
            if(mode=="navigate"){if(navigateBoard)board.navigateGesture(e)else return false}
        }
        if(e.actionMasked==MotionEvent.ACTION_POINTER_UP){board.removeCallbacks(hold);if(mode=="five"&&!pieShown)mode="blocked"}
        if(e.actionMasked==MotionEvent.ACTION_UP){
            if(mode=="two"&&!moved&&e.eventTime-started<=250){
                if(firstTap>0&&e.eventTime-firstTap<=400&&hypot(center.x-firstCenter.x,center.y-firstCenter.y)<=board.context.dp(70)){firstTap=0;board.store.undo()}
                else{firstTap=e.eventTime;firstCenter=center}
            }else if(mode=="navigate"&&navigateBoard)board.navigateGesture(e)
            mode="";origins=emptyMap();board.sceneChanged()
        }
        return true
    }
}
