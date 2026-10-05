package com.vuravision.classroom

import android.graphics.*
import android.view.MotionEvent
import kotlin.math.*

/** Handles modify only a guide; rotation of a compass creates one undoable arc. */
class GeometryInteraction(private val board:Board){
    private var guide:Item?=null;private var base:Item?=null;private var handle="";private var start=PointF()
    private var arc:Item?=null;private var lastAngle=0f;private var sweep=0f;private var checkpointed=false
    private val arcPath=Path();private val arcPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
    private val handlePaint=Paint(Paint.ANTI_ALIAS_FLAG)
    val active get()=guide!=null
    fun handles(o:Item):Map<String,Pair<Float,Float>> {
        if(o.shape=="compass"){
            val cy=if(o.geometryVersion==1)o.h/2 else 0f;val r=GeometryTools.radius(o);val a=o.geometryAngle*PI/180
            return mapOf("center" to (o.w/2 to cy),"radius" to (o.w/2-r*sin(a).toFloat() to cy+r*cos(a).toFloat()),"turn" to (o.w/2+r*cos(a).toFloat() to cy+r*sin(a).toFloat()),"start_angle" to (o.w/2+r*.55f*cos(a).toFloat() to cy+r*.55f*sin(a).toFloat()))
        }
        val center=if(o.shape=="protractor")o.w/2 to o.h else 0f to o.h/2
        val result=mutableMapOf("center" to center,"rotate" to (o.w/2 to -30f),"size" to (o.w to o.h))
        if(o.shape=="protractor"){val a=o.geometryAngle*PI/180;result["angle"]=o.w/2+o.w/2*cos(a).toFloat() to o.h-o.h*sin(a).toFloat()}
        return result
    }
    fun start(point:PointF,tolerance:Float):Boolean {
        val pair=board.store.page.visibleItems().asReversed().filter{it.pane==board.activePane&&it.shape in GeometryTools.keys && !it.locked&&board.store.page.editable(it)}.firstNotNullOfOrNull{o->handles(o).entries.firstOrNull{val p=o.global(it.value.first,it.value.second);hypot(point.x-p.first,point.y-p.second)<tolerance}?.let{o to it.key}}?:return false
        if(pair.second=="turn" && !board.store.page.canDraw())return false
        guide=pair.first;handle=pair.second;base=pair.first.deepCopy();start=point;checkpointed=false;sweep=0f
        if(handle=="turn"){
            val o=pair.first;val center=GeometryTools.center(o);lastAngle=atan2(point.y-center.second,point.x-center.first)*180/PI.toFloat()
            arc=Item(kind="ink",color=board.penColor,width=board.penWidth,layerId=board.store.page.activeLayerId,pane=board.activePane,points=mutableListOf(Point(point.x,point.y)))
            arcPath.reset();arcPath.moveTo(point.x,point.y);arcPaint.color=board.penColor;arcPaint.strokeWidth=board.penWidth
        }
        board.selected.clear();board.selected.add(pair.first.id);return true
    }
    fun event(e:MotionEvent,point:PointF):Boolean {
        val o=guide?:return false
        if(e.pointerCount>1 || e.actionMasked==MotionEvent.ACTION_CANCEL){cancel();return true}
        if(e.actionMasked==MotionEvent.ACTION_MOVE){
            if(hypot(point.x-start.x,point.y-start.y)<.25f)return true
            if(!checkpointed){board.store.checkpoint();checkpointed=true}
            GeometryTools.prepare(o)
            when(handle){
                "center"->{o.x+=point.x-start.x;o.y+=point.y-start.y;start=point}
                "radius"->{val c=GeometryTools.center(o);val radius=hypot(point.x-c.first,point.y-c.second).coerceIn(12f,5000f);o.w=radius*2;o.h=radius*2;o.x=c.first-radius;o.y=c.second-radius}
                "turn"->{val center=GeometryTools.center(o);val angle=atan2(point.y-center.second,point.x-center.first)*180/PI.toFloat();var delta=angle-lastAngle;while(delta>180)delta-=360;while(delta< -180)delta+=360
                    val steps=ceil(abs(delta)/3).toInt().coerceIn(1,120);for(i in 1..steps){val a=(lastAngle+delta*i/steps)*PI/180;val r=GeometryTools.radius(o);if(arc!!.points.size<10000){val x=center.first+r*cos(a).toFloat();val y=center.second+r*sin(a).toFloat();arc!!.points.add(Point(x,y));arcPath.lineTo(x,y)}}
                    sweep+=delta;o.geometryAngle=angle-o.rotation;o.geometrySweep=sweep;lastAngle=angle
                }
                "start_angle"->{val c=GeometryTools.center(o);o.geometryAngle=atan2(point.y-c.second,point.x-c.first)*180/PI.toFloat()-o.rotation;o.geometrySweep=0f}
                "rotate"->{val center=o.global(o.w/2,o.h/2);o.rotation=atan2(point.y-center.second,point.x-center.first)*180/PI.toFloat()+90}
                "size"->{val local=o.local(point.x,point.y);o.w=local.first.coerceIn(60f,5000f);o.h=when(o.shape){"protractor"->o.w/2;"set_square"->o.w/2;else->o.h}}
                "angle"->{val local=o.local(point.x,point.y);o.geometryAngle=(atan2(o.h-local.second,local.first-o.w/2)*180/PI.toFloat()).coerceIn(0f,180f)}
            }
            board.invalidate()
        }
        if(e.actionMasked==MotionEvent.ACTION_UP){
            arc?.takeIf{checkpointed && it.points.size>1 && board.store.page.canDraw()}?.let{ink->
                val left=ink.points.minOf{it.x};val top=ink.points.minOf{it.y};ink.w=(ink.points.maxOf{it.x}-left).coerceAtLeast(1f);ink.h=(ink.points.maxOf{it.y}-top).coerceAtLeast(1f);ink.x=left;ink.y=top;ink.inkW=ink.w;ink.inkH=ink.h;ink.points.forEach{it.x-=left;it.y-=top};board.store.page.items.add(ink)
            }
            guide=null;base=null;arc=null
            if(checkpointed)board.store.changed();checkpointed=false;board.sceneChanged();board.onSelection()
        }
        return true
    }
    fun cancel(){
        val o=guide;val b=base
        if(o!=null&&b!=null){o.x=b.x;o.y=b.y;o.w=b.w;o.h=b.h;o.rotation=b.rotation;o.geometryAngle=b.geometryAngle;o.geometryVersion=b.geometryVersion;o.geometrySweep=b.geometrySweep}
        if(guide!=null&&checkpointed)board.store.cancelCheckpoint();checkpointed=false;guide=null;base=null;arc=null;board.sceneChanged()
    }
    fun draw(canvas:Canvas,zoom:Float){
        arc?.let{if(it.points.size>1)canvas.drawPath(arcPath,arcPaint)}
        val p=handlePaint
        board.chosen().singleOrNull()?.takeIf{it.shape in GeometryTools.keys}?.let{o->handles(o).forEach{(name,point)->val g=o.global(point.first,point.second);p.color=if(name=="turn"||name=="angle")ORANGE else TEAL;p.style=Paint.Style.FILL;canvas.drawCircle(g.first,g.second,11/zoom,p);p.color=Color.WHITE;p.textSize=14/zoom;p.textAlign=Paint.Align.CENTER;canvas.drawText(when(name){"center"->"+";"radius","size"->"↔";"turn"->"✎";else->"↻"},g.first,g.second+5/zoom,p)}}
    }
}
