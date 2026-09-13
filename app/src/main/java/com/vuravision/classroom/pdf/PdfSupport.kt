package com.vuravision.classroom.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.vuravision.classroom.document.CanvasObject
import java.io.File
import java.io.FileOutputStream

object PdfSupport {
    fun importPdf(context: Context, uri: Uri): CanvasObject.PdfObject {
        val out=File(context.filesDir,"imports/pdf_${System.currentTimeMillis()}.pdf"); out.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
        val pfd=android.os.ParcelFileDescriptor.open(out,android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        val count=PdfRenderer(pfd).use { it.pageCount }; pfd.close()
        return CanvasObject.PdfObject(bounds=android.graphics.RectF(120f,100f,1120f,850f),localPath=out.absolutePath,pageCount=count)
    }

    fun renderPreview(context: Context, obj: CanvasObject.PdfObject) {
        val dest=File(context.cacheDir,"pdf_${obj.id}_${obj.pageIndex}.png")
        val pfd=android.os.ParcelFileDescriptor.open(File(obj.localPath),android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        PdfRenderer(pfd).use { renderer -> renderer.openPage(obj.pageIndex.coerceIn(0,renderer.pageCount-1)).use { page ->
            val w=1400; val h=(w*(page.height.toFloat()/page.width)).toInt().coerceAtLeast(1)
            val bm=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888); bm.eraseColor(android.graphics.Color.WHITE)
            page.render(bm,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            FileOutputStream(dest).use { bm.compress(Bitmap.CompressFormat.PNG,95,it) }; bm.recycle()
        }}; pfd.close()
    }
}
