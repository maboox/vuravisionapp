package com.vuravision.classroom

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class Release19Test {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private fun state():PdfWorkspaceState {
        val asset="source.pdf"
        val sheets=(0..1).map{i->PdfSheet(300f,400f,Page(background="plain",items=mutableListOf(Item(kind="pdf",asset=asset,pdfPage=i,pageCount=2,w=300f,h=400f,locked=true))))}.toMutableList()
        return PdfWorkspaceState(asset=asset,sourceUri="content://provider/document/123",sheets=sheets,originalHash="source-checksum")
    }
    @Test fun pdfPagesAndAnnotationsPersistWithProject(){
        val media=Media(context)
        try{
            val pdf=state();media.file(pdf.asset).writeBytes("fixture bytes, not rendered by this test".toByteArray())
            pdf.sheets[0].page.items.add(Item(kind="text",text="Page one",x=10f,y=20f))
            pdf.sheets[1].page.items.add(Item(kind="shape",shape="circle",x=30f,y=40f))
            pdf.revision=2;pdf.scroll=150;pdf.zoom=1.5f;pdf.ratio=.3f;pdf.onRight=false
            val doc=Lesson(pdf=pdf);val bytes=ByteArrayOutputStream();LessonFiles(media).write(doc,bytes)
            val restored=LessonFiles(media).read(ByteArrayInputStream(bytes.toByteArray()),true)
            assertEquals("Page one",restored.pdf!!.sheets[0].page.items[1].text)
            assertEquals("circle",restored.pdf!!.sheets[1].page.items[1].shape)
            assertEquals(150,restored.pdf!!.scroll);assertEquals(.3f,restored.pdf!!.ratio,.001f)
            assertEquals(pdf.sourceUri,restored.pdf!!.sourceUri)
            val imported=LessonFiles(media).read(ByteArrayInputStream(bytes.toByteArray()))
            assertEquals("",imported.pdf!!.sourceUri)
            assertEquals("",imported.pdf!!.originalHash)
        }finally{media.close()}
    }
    @Test fun oldProjectsStillOpenAndPdfValidationRejectsMissingBase(){
        val old=Gson().fromJson("""{"schema":3,"title":"Legacy","current":0,"pages":[{"id":"old","background":"plain","items":[],"layers":[{"id":"base","name":"Layer","visible":true,"locked":false,"opacity":1}],"activeLayerId":"base","panes":[{"color":-1,"background":-1,"zoom":1,"tx":0,"ty":0,"penWidth":4,"penStyle":"round","dashLength":12,"dashGap":8}]}]}""",Lesson::class.java)
        old.validate();assertNull(old.pdf)
        val pdf=state();pdf.sheets[0].page.items.clear()
        assertThrows(IllegalArgumentException::class.java){pdf.validate()}
    }
    @Test fun boardUndoDoesNotRevertPdfAnnotations(){
        val pdf=state();val white=Store(Lesson(pdf=pdf))
        white.editMetadata{white.page.items.add(Item(kind="text",text="board"))}
        val notes=Store(Lesson(pages=mutableListOf(pdf.sheets[0].page)))
        notes.changed={pdf.sheets[0].page=notes.page}
        notes.editMetadata{notes.page.items.add(Item(kind="text",text="PDF note"))}
        white.undo()
        assertTrue(white.page.items.isEmpty())
        assertEquals("PDF note",white.lesson.pdf!!.sheets[0].page.items.last().text)
        notes.undo();assertEquals(1,pdf.sheets[0].page.items.size)
        notes.redo();assertEquals("PDF note",pdf.sheets[0].page.items.last().text)
    }
    @Test fun metadataEditsKeepInkSamplesAndAutosaveSnapshotIsStable(){
        val ink=Item(points=mutableListOf(Point(1f,2f),Point(3f,4f)))
        val store=Store(Lesson(pages=mutableListOf(Page(items=mutableListOf(ink))),pdf=state()))
        val source=ink.points;val saved=store.lesson.copyForSave()
        store.editMetadata{ink.x=20f}
        assertSame(source,ink.points);assertEquals(0f,saved.pages[0].items[0].x,.001f)
        store.undo();assertEquals(0f,store.page.items[0].x,.001f)
        val snapshot=store.lesson.copyForSave();store.lesson.pdf!!.sheets[0].page.items.add(Item(kind="text",text="later"))
        assertEquals(1,snapshot.pdf!!.sheets[0].page.items.size)
    }
    @Test fun broadTouchScrollsWhileFineContactAndStylusWrite(){
        val profile=TouchProfile(thin=5f,palm=50f)
        assertTrue(PdfTouch.scrolls(profile,MotionEvent.TOOL_TYPE_FINGER,12f))
        assertTrue(PdfTouch.scrolls(profile,MotionEvent.TOOL_TYPE_FINGER,60f))
        assertFalse(PdfTouch.scrolls(profile,MotionEvent.TOOL_TYPE_FINGER,3f))
        assertFalse(PdfTouch.scrolls(profile,MotionEvent.TOOL_TYPE_FINGER,0f))
        assertFalse(PdfTouch.scrolls(profile,MotionEvent.TOOL_TYPE_STYLUS,12f))
        assertFalse(PdfTouch.scrolls(profile,MotionEvent.TOOL_TYPE_ERASER,12f))
    }
    @Test fun distantBackgroundHasBoundedScreenDensity(){
        for(scale in listOf(.15f,.3f,.6f,1f,3f)){
            val step=Renderer.backgroundStep(scale)
            assertTrue(step*scale>=12f);assertTrue(step>=32f)
            assertTrue(1920f/(step*scale)*1080f/(step*scale)<15000f)
        }
    }
    @Test fun pdfBaseCannotBeSelectedOrMoved(){
        val state=state();val store=Store(Lesson(pages=mutableListOf(state.sheets[0].page)))
        val media=Media(context)
        try{
            val b=Board(context,store,Renderer(media));b.pdfBaseId=store.page.items.first().id
            b.selected.add(b.pdfBaseId!!)
            assertTrue(b.chosen().isEmpty());b.delete();assertEquals(1,store.page.items.size)
        }finally{media.close()}
    }
    @Test fun allNativeExperimentsHaveBilingualSubjectAndLessonTopic(){
        assertEquals(73,Labs.keys.size)
        Labs.keys.forEach{key->val topic=LabCatalog.topic(key);assertTrue(topic.subject in LabCatalog.subjects);assertTrue(topic.en.isNotBlank() && topic.fa.isNotBlank())}
        assertEquals("electricity",LabCatalog.topic("native_circuit").id)
        assertEquals("chem",LabCatalog.topic("dilution").subject)
    }
    @Test fun guideHasOrderedBilingualPdfChapters(){
        val topics=UserGuide.topics(context)
        assertEquals(27,topics.size)
        assertTrue(topics.indexOfFirst{it.id=="pdf-reader"}<topics.indexOfFirst{it.id=="pdf-save"})
        assertTrue(topics.all{it.stepsEn.size==it.stepsFa.size && it.titleEn.isNotBlank() && it.titleFa.isNotBlank()})
    }
}
