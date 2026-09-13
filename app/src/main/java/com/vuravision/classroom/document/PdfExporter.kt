package com.vuravision.classroom.document

import android.graphics.*
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.OutputStream

object PdfExporter {
    fun export(doc:BoardDocument,out:OutputStream,width:Int=1920,height:Int=1080){
        val pdf=PdfDocument(); val page=pdf.startPage(PdfDocument.PageInfo.Builder(width,height,1).create()); val c=page.canvas; c.drawColor(Color.WHITE)
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND;style=Paint.Style.STROKE}
        doc.objects.forEach { o -> when(o){
            is CanvasObject.InkStroke -> if(o.points.size>1){ val path=Path();path.moveTo(o.points[0].x,o.points[0].y);for(i in 1 until o.points.size){val a=o.points[i-1];val b=o.points[i];path.quadTo(a.x,a.y,(a.x+b.x)/2,(a.y+b.y)/2)};p.color=o.color;p.strokeWidth=o.width;p.alpha=o.alpha;c.drawPath(path,p)}
            is CanvasObject.ImageObject -> BitmapFactory.decodeFile(o.localPath)?.let{c.drawBitmap(it,null,o.bounds,Paint(Paint.ANTI_ALIAS_FLAG));it.recycle()}
            is CanvasObject.PdfObject -> Unit
        }}
        pdf.finishPage(page);pdf.writeTo(out);pdf.close()
    }
}
