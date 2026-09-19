package com.vuravision.classroom

import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceTest {
    @Test
    fun pdfObjectSurvivesPdfExport() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val media = Media(context)
        try {
            val asset = "${newId()}.pdf"
            val source = PdfDocument()
            try {
                val page = source.startPage(PdfDocument.PageInfo.Builder(100, 100, 1).create())
                page.canvas.drawColor(Color.RED)
                source.finishPage(page)
                media.file(asset).outputStream().use { source.writeTo(it) }
            } finally {
                source.close()
            }
            assertEquals(1, media.pdfCount(asset))
            val doc =
                Page(
                    background = "white",
                    items = mutableListOf(Item(kind = "pdf", asset = asset, w = 100f, h = 100f)),
                )
            val file = File(context.cacheDir, "export-test.pdf")
            val export = PdfDocument()
            try {
                val page = export.startPage(PdfDocument.PageInfo.Builder(400, 400, 1).create())
                Renderer(media).page(page.canvas, doc, 400, 400, true)
                export.finishPage(page)
                file.outputStream().use { export.writeTo(it) }
            } finally {
                export.close()
            }
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { pdf ->
                    assertEquals(1, pdf.pageCount)
                    pdf.openPage(0).use { page ->
                        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        assertEquals(Color.RED, bitmap.getPixel(200, 200))
                    }
                }
            }
        } finally {
            media.close()
        }
    }

    @Test
    fun recognitionLanguagesExist() {
        assertNotNull(
            com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
                .fromLanguageTag("en-US")
        )
        assertNotNull(
            com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
                .fromLanguageTag("fa")
        )
    }
}
