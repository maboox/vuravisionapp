package com.vuravision.classroom

import android.graphics.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.util.Matrix as PdfMatrix
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object PdfExport {
    fun hash(input:InputStream):String = input.use { stream ->
        val digest=MessageDigest.getInstance("SHA-256");val bytes=ByteArray(32768)
        while(true){val n=stream.read(bytes);if(n<0)break;digest.update(bytes,0,n)}
        digest.digest().joinToString(""){"%02x".format(it)}
    }
    fun write(media:Media,state:PdfWorkspaceState,target:File){
        state.validate()
        PDFBoxResourceLoader.init(media.context.applicationContext)
        PDDocument.load(media.file(state.asset)).use { doc ->
            require(doc.numberOfPages==state.sheets.size){"PDF page count changed"}
            val renderer=Renderer(media)
            state.sheets.forEachIndexed { index,sheet ->
                val base=sheet.page.items.first()
                if(sheet.page.items.size==1 && base.cuts.isEmpty())return@forEachIndexed
                val scale=2560f/maxOf(sheet.width,sheet.height)
                val bitmap=Bitmap.createBitmap((sheet.width*scale).toInt().coerceAtLeast(1),(sheet.height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
                try {
                    val canvas=Canvas(bitmap);canvas.scale(bitmap.width/sheet.width,bitmap.height/sheet.height)
                    val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
                    base.cuts.filter{it.pdfPage<0 || it.pdfPage==index}.forEach{cut->
                        canvas.save();canvas.scale(base.w/cut.basisW,base.h/cut.basisH)
                        paint.strokeWidth=cut.radius*2
                        if(cut.ax==cut.bx && cut.ay==cut.by){paint.style=Paint.Style.FILL;canvas.drawCircle(cut.ax,cut.ay,cut.radius,paint);paint.style=Paint.Style.STROKE}
                        else canvas.drawLine(cut.ax,cut.ay,cut.bx,cut.by,paint)
                        canvas.restore()
                    }
                    renderer.scene(canvas,sheet.page,true,exclude=setOf(base.id))
                    val page=doc.getPage(index);val crop=page.cropBox
                    val matrix=placement(page.rotation,crop.width,crop.height,crop.lowerLeftX,crop.lowerLeftY)
                    val image=LosslessFactory.createFromImage(doc,bitmap)
                    PDPageContentStream(doc,page,PDPageContentStream.AppendMode.APPEND,true,true).use{stream->stream.drawImage(image,matrix)}
                } finally { bitmap.recycle() }
            }
            target.parentFile?.mkdirs();doc.save(target)
        }
        require(target.length()>0){"Empty PDF export"}
        PDDocument.load(target).use{require(it.numberOfPages==state.sheets.size){"Incomplete PDF export"}}
    }
    /** Undo the display rotation, with the crop origin retained. */
    fun placement(rotation:Int,w:Float,h:Float,x:Float,y:Float):PdfMatrix = when((rotation%360+360)%360){
        90->PdfMatrix(0f,h,-w,0f,x+w,y)
        180->PdfMatrix(-w,0f,0f,-h,x+w,y+h)
        270->PdfMatrix(0f,-h,w,0f,x,y+h)
        else->PdfMatrix(w,0f,0f,h,x,y)
    }
}
