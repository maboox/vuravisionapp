package com.vuravision.classroom

import android.graphics.*
import android.os.SystemClock

/** Presence is transient. Host timestamps are projected onto the local monotonic clock. */
class RoomPresenceOverlay(private val board:Board) {
    private var frame=RoomFrame()
    private var self=""
    private var received=0L
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    fun update(value:RoomFrame,peer:String){frame=value;self=peer;received=SystemClock.elapsedRealtime()}
    private fun now()=frame.serverTime+(SystemClock.elapsedRealtime()-received)
    fun previewIds():Set<String> = frame.presence.filter{it.peer!=self&&it.page==board.store.page.id&&now()-it.at<1500}.flatMap{p->p.live.filter{frame.locks[RoomDocuments.key(p.page,it.id)]==p.peer}.map{it.id}}.toSet()
    fun draw(c:Canvas){
        val page=board.store.page;val density=board.resources.displayMetrics.density;val members=frame.members.associateBy{it.profile.id};var visible=false
        frame.presence.filter{it.peer!=self&&it.page==page.id}.forEach{presence->
            if(presence.pane !in page.visiblePaneIndices)return@forEach
            val age=now()-presence.at;if(age !in 0..60000)return@forEach;visible=true
            val profile=members[presence.peer]?.profile?:return@forEach;val pane=presence.pane;val state=page.panes[pane];val clip=board.paneScreenBounds(pane)
            val alpha=if(presence.active)1f else (.65f*(1-age/60000f)).coerceIn(0f,.65f)
            c.save();c.clipRect(clip)
            if(age<1500)presence.live.forEach{v->c.save();c.translate(clip.left,clip.top);c.scale(density,density);c.translate(state.tx,state.ty);c.scale(state.zoom,state.zoom);board.renderer.draw(c,v);c.restore()}
            val r=presence.viewport;val bounds=board.screenRect(RectF(r.left,r.top,r.right,r.bottom),pane)
            paint.color=profile.color;paint.alpha=(alpha*100).toInt();paint.style=Paint.Style.STROKE;paint.strokeWidth=2*density;paint.pathEffect=DashPathEffect(floatArrayOf(8*density,5*density),0f);c.drawRect(bounds,paint);paint.pathEffect=null
            val point=board.screenRect(RectF(presence.x,presence.y,presence.x,presence.y),pane);paint.alpha=(alpha*255).toInt();paint.style=Paint.Style.FILL;c.drawCircle(point.left,point.top,5*density,paint)
            label(c,profile,point.left+8*density,point.top-12*density,clip,alpha)
            c.restore()
        }
        frame.locks.filterValues{it!=self}.forEach{(key,peer)->val item=page.items.firstOrNull{RoomDocuments.key(page.id,it.id)==key}?:return@forEach;val profile=members[peer]?.profile?:return@forEach
            if(item.pane !in page.visiblePaneIndices)return@forEach
            val pane=board.paneScreenBounds(item.pane);val bounds=board.screenRect(itemBounds(item),item.pane)
            c.save();c.clipRect(pane);paint.color=profile.color;paint.alpha=220;paint.style=Paint.Style.STROKE;paint.strokeWidth=2*density;paint.pathEffect=null;c.drawRoundRect(bounds,4*density,4*density,paint);label(c,profile,bounds.left,bounds.top-8*density,pane,1f);c.restore()}
        if(visible)board.postInvalidateDelayed(1000)
    }
    private fun label(c:Canvas,profile:DeviceProfile,x:Float,y:Float,clip:RectF,alpha:Float){
        val d=board.resources.displayMetrics.density;val text=(profile.emoji+" "+profile.name).take(50);paint.textSize=12*d;paint.typeface=Typeface.DEFAULT_BOLD;paint.textAlign=Paint.Align.LEFT
        val w=(paint.measureText(text)+12*d).coerceAtMost(clip.width());val left=x.coerceIn(clip.left,(clip.right-w).coerceAtLeast(clip.left));val bottom=y.coerceIn(clip.top+24*d,clip.bottom.coerceAtLeast(clip.top+24*d))
        paint.style=Paint.Style.FILL;paint.color=profile.color;paint.alpha=(alpha*220).toInt();c.drawRoundRect(RectF(left,bottom-23*d,left+w,bottom),5*d,5*d,paint);paint.color=Color.WHITE;paint.alpha=(alpha*255).toInt();c.drawText(text,left+6*d,bottom-6*d,paint)
    }
}
