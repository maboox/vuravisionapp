package com.vuravision.classroom

import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfWorkspaceDeviceTest {
    @Test fun exportKeepsOriginalTextAndPlacesNotesOnRotatedCroppedPages(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        PDFBoxResourceLoader.init(context)
        val media=Media(context)
        try{
            val asset="${newId()}.pdf"
            PDDocument().use{doc->
                for(rotation in listOf(0,90,180,270)){
                    val page=PDPage(PDRectangle(400f,500f));page.cropBox=PDRectangle(10f,20f,180f,240f);page.rotation=rotation;doc.addPage(page)
                    PDPageContentStream(doc,page).use{stream->
                        stream.setNonStrokingColor(255,0,0);stream.addRect(0f,0f,400f,500f);stream.fill()
                        stream.setNonStrokingColor(0,0,0);stream.beginText();stream.setFont(PDType1Font.HELVETICA,12f)
                        stream.newLineAtOffset(40f,120f);stream.showText("Original searchable text");stream.endText()
                    }
                }
                doc.save(media.file(asset))
            }
            val sizes=media.pdfSizes(asset)
            val state=PdfWorkspaceState(asset=asset,sheets=sizes.mapIndexed{i,(w,h)->
                val base=Item(kind="pdf",asset=asset,pdfPage=i,pageCount=4,w=w,h=h,locked=true)
                base.cuts=listOf(EraseCut(100f,70f,120f,70f,6f,w,h,i))
                PdfSheet(w,h,Page(background="plain",items=mutableListOf(base,Item(kind="sticky",text="",noteColor=Color.GREEN,x=10f,y=20f,w=40f,h=30f))))
            }.toMutableList())
            val output=File(context.cacheDir,"annotated-rotations.pdf");PdfExport.write(media,state,output)
            PDDocument.load(output).use{doc->assertEquals(4,doc.numberOfPages);assertTrue(PDFTextStripper().getText(doc).contains("Original searchable text"))}
            ParcelFileDescriptor.open(output,ParcelFileDescriptor.MODE_READ_ONLY).use{fd->PdfRenderer(fd).use{pdf->
                for(i in 0..3)pdf.openPage(i).use{page->
                    val bitmap=Bitmap.createBitmap(page.width,page.height,Bitmap.Config.ARGB_8888)
                    try{
                        bitmap.eraseColor(Color.WHITE);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val green=bitmap.getPixel(30,35);assertTrue("rotation $i: note",Color.green(green)>240 && Color.red(green)<15)
                        val white=bitmap.getPixel(110,70);assertTrue("rotation $i: cover",Color.red(white)>240 && Color.green(white)>240 && Color.blue(white)>240)
                        val red=bitmap.getPixel(70,40);assertTrue("rotation $i: original",Color.red(red)>240 && Color.green(red)<15)
                    }finally{bitmap.recycle()}
                }
            }}
        }finally{media.close()}
    }
}
