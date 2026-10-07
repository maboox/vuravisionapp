package com.vuravision.classroom

import kotlin.math.*

/** Drawing labels are ordinary editable text, committed with their stroke. */
object GuideLabels {
    fun strokeText(guide:Item,points:List<Point>,length:Float,scale:Float,unit:String):String {
        var text=DisplayNumbers.one((length/scale).toDouble())+" "+unit
        if(guide.shape=="compass")text+=" · "+DisplayNumbers.one(length/GeometryTools.radius(guide)*180/PI)+"°"
        else if(guide.shape in listOf("protractor","set_square") && points.isNotEmpty()){
            val last=points.last();val first=points.first()
            val angle=if(guide.shape=="protractor"){val p=guide.local(last.x,last.y);atan2(guide.h-p.second,p.first-guide.w/2)*180/PI}
            else atan2(last.y-first.y,last.x-first.x)*180/PI
            val shown=if(guide.shape=="set_square")(angle%180+180)%180 else abs(angle)
            text+=" · "+DisplayNumbers.one(shown)+"°"
        }
        return text
    }
    fun text(value:String,x:Float,y:Float,zoom:Float,owner:Item,offset:Float=28f):Item = Item(kind="text",text=value,width=(15f/zoom).coerceIn(.1f,200f),color=TEAL,layerId=owner.layerId,pane=owner.pane).also{
        TextLayout.fit(it);it.x=x-it.w/2;it.y=y-offset/zoom-8-TextLayout.layout(it).getLineBaseline(0)
    }
    fun construction(board:Board,guide:Item,result:Item):Item {
        val length=if(guide.shape=="compass")GeometryTools.radius(guide)else result.w
        var value=DisplayNumbers.one((length/board.measureScale(result.pane)).toDouble())+" "+board.measurementUnit()
        val angle=when(guide.shape){"compass"->360f;"protractor"->guide.geometryAngle;else->result.rotation}
        value+=" · "+DisplayNumbers.one(angle.toDouble())+"°"
        val at=result.global(result.w,result.h/2)
        return text(value,at.first,at.second,board.store.page.panes[result.pane].zoom,result,30f)
    }
}
