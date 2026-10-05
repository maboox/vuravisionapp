package com.vuravision.classroom

import android.graphics.*
import kotlin.math.*

/** Bounded tap-time contour detection. Open regions always escape and are rejected. */
object HandFill {
    data class Result(val item:Item,val index:Int)
    fun create(page:Page,pane:Int,x:Float,y:Float,color:Int,alpha:Int):Result? {
        if(!page.canDraw())return null
        val ink=page.visibleItems().filter{it.pane==pane&&it.kind=="ink"&&it.points.size>1&&it.layerId==page.activeLayerId&&!it.locked&&page.editable(it)}
        if(ink.isEmpty()||ink.size>256||ink.sumOf{it.points.size}>100000)return null
        val box=contentBounds(ink);val padding=ink.maxOf{it.width}/2+8f;box.inset(-padding,-padding)
        if(!box.contains(x,y)||box.width()<6||box.height()<6)return null
        val scale=min(1.5f,640f/max(box.width(),box.height()))
        // A contour thinner than a pixel cannot be detected reliably at this scale.
        if(ink.minOf{it.width}*scale<.6f)return null
        val w=ceil(box.width()*scale).toInt()+2;val h=ceil(box.height()*scale).toInt()+2
        val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);canvas.scale(scale,scale);canvas.translate(-box.left,-box.top)
        val p=Paint().apply{this.color=Color.BLACK;style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
        ink.forEach{o->
            canvas.save();canvas.translate(o.x,o.y);canvas.rotate(o.rotation,o.w/2,o.h/2);Erasing.clip(canvas,o)
            val path=Path();o.points.forEachIndexed{i,q->val xx=q.x*o.w/o.inkW;val yy=q.y*o.h/o.inkH;if(i==0)path.moveTo(xx,yy)else path.lineTo(xx,yy)}
            p.strokeWidth=o.width.coerceAtLeast(1.4f/scale);canvas.drawPath(path,p);canvas.restore()
        }
        val pixels=IntArray(w*h);bitmap.getPixels(pixels,0,w,0,0,w,h);bitmap.recycle()
        val sx=((x-box.left)*scale).toInt().coerceIn(0,w-1);val sy=((y-box.top)*scale).toInt().coerceIn(0,h-1)
        val seed=sy*w+sx;if(pixels[seed]!=0)return null
        val queue=IntArray(pixels.size);var head=0;var tail=1;queue[0]=seed;pixels[seed]=1
        var left=sx;var right=sx;var top=sy;var bottom=sy
        while(head<tail){
            val n=queue[head++];val xx=n%w;val yy=n/w
            if(xx==0||yy==0||xx==w-1||yy==h-1)return null
            left=min(left,xx);right=max(right,xx);top=min(top,yy);bottom=max(bottom,yy)
            if(pixels[n-1]==0){pixels[n-1]=1;queue[tail++]=n-1}
            if(pixels[n+1]==0){pixels[n+1]=1;queue[tail++]=n+1}
            if(pixels[n-w]==0){pixels[n-w]=1;queue[tail++]=n-w}
            if(pixels[n+w]==0){pixels[n+w]=1;queue[tail++]=n+w}
        }
        if(tail<9)return null
        val runs=mutableListOf<FillRun>()
        for(yy in top..bottom){var xx=left;while(xx<=right){if(pixels[yy*w+xx]!=1){xx++;continue};val start=xx;while(xx<=right&&pixels[yy*w+xx]==1)xx++
            if(runs.size>=50000)return null
            runs.add(FillRun((yy-top)/scale,(start-left)/scale,(xx-left)/scale,1f/scale))}}
        val item=Item(kind="ink",shape="region_fill",x=box.left+left/scale,y=box.top+top/scale,w=(right-left+1)/scale,h=(bottom-top+1)/scale,
            inkW=(right-left+1)/scale,inkH=(bottom-top+1)/scale,fillColor=color,fillAlpha=alpha,width=.1f,layerId=page.activeLayerId,pane=pane,fillRuns=runs)
        // Place below its outlines. Store vector runs, not an expensive page-sized image.
        return Result(item,page.items.indexOfFirst{it in ink}.coerceAtLeast(0))
    }
}
