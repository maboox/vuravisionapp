package com.vuravision.classroom

import android.graphics.PointF
import android.view.MotionEvent
import kotlin.math.*

/** Double taps use the maximum contact count, so a five-finger tap cannot undo. */
class BoardGestures(private val board:Board){
    var undoEnabled=true
    var pieEnabled=false
    var onPie:(Float,Float)->Unit={_,_->}
    var onHistory:((Int,Int)->Unit)?=null
    private var mode="";private var started=0L;private var center=PointF();private var origins=mapOf<Int,PointF>()
    private var moved=false;private var firstTap=0L;private var firstCenter=PointF()
    private var firstFive=0L;private var fiveCenter=PointF();private var second=false
    private var historyStart=0;private var historyTotal=0;private var historyStep=24f
    private val hold=Runnable{if(mode=="two"&&second&&!moved)beginHistory()}
    fun reset(){board.removeCallbacks(hold);if(mode=="scrub")onHistory?.invoke(-1,0);mode="";origins=emptyMap();firstTap=0;firstFive=0;moved=false;second=false}
    private fun fingers(e:MotionEvent)= (0 until e.pointerCount).all{e.getToolType(it)==MotionEvent.TOOL_TYPE_FINGER && e.getTouchMajor(it)<board.profile.palm && (e.getTouchMajor(it)>board.profile.thin || e.getTouchMajor(it)<=0 && (e.pointerCount==5 || board.touchMode))}
    private fun beginHistory(){
        board.removeCallbacks(hold);mode="scrub";firstTap=0
        historyStart=board.store.undoCount;historyTotal=historyStart+board.store.redoCount
        val left=(center.x-board.context.dp(12)).coerceAtLeast(1f)/historyStart.coerceAtLeast(1)
        val right=(board.width-center.x-board.context.dp(12)).coerceAtLeast(1f)/(historyTotal-historyStart).coerceAtLeast(1)
        historyStep=minOf(board.context.dp(24).toFloat(),left,right).coerceAtLeast(.5f)
        board.store.seekHistory((historyStart-1).coerceAtLeast(0));onHistory?.invoke(board.store.undoCount,historyTotal)
    }
    fun event(e:MotionEvent,navigateBoard:Boolean=true):Boolean {
        if(!undoEnabled&&!pieEnabled)return false
        if(e.actionMasked==MotionEvent.ACTION_CANCEL){val taken=mode.isNotEmpty();reset();return taken}
        if(e.actionMasked==MotionEvent.ACTION_DOWN){board.removeCallbacks(hold);mode="";moved=false;second=false}
        if(e.actionMasked==MotionEvent.ACTION_POINTER_DOWN&&mode=="scrub"){onHistory?.invoke(-1,0);mode="blocked";board.removeCallbacks(hold);return true}
        if(e.actionMasked==MotionEvent.ACTION_POINTER_DOWN && fingers(e) && (e.pointerCount==5&&pieEnabled || e.pointerCount==2&&undoEnabled&&hypot(e.getX(0)-e.getX(1),e.getY(0)-e.getY(1))<=board.context.dp(140))){
            board.abortPendingGesture();board.removeCallbacks(hold)
            center=PointF((0 until e.pointerCount).map{e.getX(it)}.average().toFloat(),(0 until e.pointerCount).map{e.getY(it)}.average().toFloat())
            origins=(0 until e.pointerCount).associate{e.getPointerId(it) to PointF(e.getX(it),e.getY(it))};started=e.eventTime;moved=false
            mode=if(e.pointerCount==5)"five"else"two"
            second=mode=="two"&&firstTap>0&&started-firstTap<=400&&hypot(center.x-firstCenter.x,center.y-firstCenter.y)<=board.context.dp(70)
            if(mode=="five"){firstTap=0;second=false}
            else if(second)board.postDelayed(hold,300)
            return true
        }
        if(mode.isEmpty())return false
        if(e.actionMasked==MotionEvent.ACTION_POINTER_DOWN){board.removeCallbacks(hold);mode="blocked";firstTap=0;second=false;return true}
        if(e.actionMasked==MotionEvent.ACTION_MOVE){
            if(!fingers(e)){reset();return true}
            if(mode=="two"&&second&&e.pointerCount==2){
                val x=(e.getX(0)+e.getX(1))/2;val y=(e.getY(0)+e.getY(1))/2
                if(abs(x-center.x)>board.context.dp(12)&&abs(y-center.y)<board.context.dp(30))beginHistory()
            }
            if(mode=="scrub"){
                if(e.pointerCount==2){val x=(e.getX(0)+e.getX(1))/2;val step=((x-center.x)/historyStep).roundToInt()
                    board.store.seekHistory((historyStart-1+step).coerceIn(0,historyTotal));onHistory?.invoke(board.store.undoCount,historyTotal)}
                return true
            }
            if((0 until e.pointerCount).any{i->origins[e.getPointerId(i)]?.let{hypot(e.getX(i)-it.x,e.getY(i)-it.y)>board.context.dp(8)}?:true}){
                moved=true;firstTap=0;firstFive=0;board.removeCallbacks(hold)
                if(mode=="two"){
                    if(navigateBoard){val p=origins.values.toList();board.beginGestureNavigation(center.x,center.y,if(p.size==2)hypot(p[0].x-p[1].x,p[0].y-p[1].y)else 0f)}
                    mode="navigate"
                }
            }
            if(mode=="navigate"){if(navigateBoard)board.navigateGesture(e)else return false}
        }
        if(e.actionMasked==MotionEvent.ACTION_POINTER_UP){board.removeCallbacks(hold);if(mode=="scrub"){onHistory?.invoke(-1,0);mode="blocked"}}
        if(e.actionMasked==MotionEvent.ACTION_UP){
            board.removeCallbacks(hold);if(mode=="scrub")onHistory?.invoke(-1,0)
            if(mode=="two"&&!moved&&e.eventTime-started<=300){
                if(second){firstTap=0;board.store.undo()}else{firstTap=e.eventTime;firstCenter=center}
            }else if(mode=="five"&&!moved&&e.eventTime-started<=300){
                if(firstFive>0&&e.eventTime-firstFive<=450&&hypot(center.x-fiveCenter.x,center.y-fiveCenter.y)<=board.context.dp(100)){firstFive=0;onPie(center.x,center.y)}
                else{firstFive=e.eventTime;fiveCenter=center}
            }else if(mode=="navigate"&&navigateBoard)board.navigateGesture(e)
            mode="";origins=emptyMap();board.sceneChanged()
        }
        return true
    }
}
