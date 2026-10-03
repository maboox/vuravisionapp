package com.vuravision.classroom

import android.graphics.Bitmap
import android.view.ContextThemeWrapper
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class Release193Test {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun unlockRecoversLockedItemsAndLayersPreservesPdfBaseAndSupportsUndo(){
        val media=Media(context)
        try{
            val base=Item(kind="pdf",asset="source.pdf",locked=true)
            val note=Item(kind="text",text="locked note",locked=true)
            val hidden=Item(kind="shape",locked=true,layerId="hidden")
            val page=Page(items=mutableListOf(base,note,hidden),layers=mutableListOf(Layer(id="base",locked=true),Layer(id="hidden",visible=false,locked=true)))
            val other=Page(items=mutableListOf(Item(locked=true)))
            val store=Store(Lesson(pages=mutableListOf(page,other)))
            val board=Board(context,store,Renderer(media));board.pdfBaseId=base.id
            assertEquals(4,board.unlockAll())
            assertTrue(base.locked);assertFalse(note.locked);assertFalse(hidden.locked)
            assertTrue(store.page.layers.none{it.locked});assertFalse(store.page.layers[1].visible)
            assertTrue(other.items[0].locked)
            board.selected.add(note.id);assertEquals(note.id,board.chosen().single().id)
            store.undo();assertTrue(store.page.items.all{it.locked});assertTrue(store.page.layers.all{it.locked})
            store.redo();assertTrue(store.page.items[0].locked);assertFalse(store.page.items[1].locked)
            assertEquals(0,board.unlockAll())
        }finally{media.close()}
    }

    @Test fun retainedPdfFrameSurvivesSharedCacheClearingWithoutReloading(){
        val media=Media(context)
        val frame=Bitmap.createBitmap(1536,1000,Bitmap.Config.ARGB_8888)
        try{
            // There is no source file: a reload would fail. The mounted frame is sufficient.
            val item=Item(kind="pdf",asset="missing-source.pdf")
            repeat(10){media.clear();assertSame(frame,media.image(item,edge=1536,retained=frame))}
            assertSame(frame,media.image(item,edge=768,retained=frame))
        }finally{media.close();frame.recycle()}
    }

    @Test fun fullscreenAndSwapRequestChildLayoutWithoutTouchAndRestoreWhiteboard(){
        val themed=ContextThemeWrapper(context,R.style.AppTheme)
        val media=Media(themed)
        val state=PdfWorkspaceState(asset="source.pdf",sheets=mutableListOf(PdfSheet()))
        val board=Board(themed,Store(),Renderer(media))
        val pane=PdfPane(themed,state,media,{board},{},{},{},{})
        try{
            val split=PdfSplitLayout(themed,state,board,pane){}
            fun layout(){split.measure(View.MeasureSpec.makeMeasureSpec(1200,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));split.layout(0,0,1200,800)}
            layout();assertTrue(pane.left>board.left)
            state.onRight=false;split.applyPresentation();assertTrue(split.isLayoutRequested)
            layout();assertEquals(0,pane.left);assertTrue(board.left>pane.left)
            state.fullscreen=true;split.applyPresentation();assertEquals(View.GONE,board.visibility)
            layout();assertEquals(1200,pane.width)
            state.fullscreen=false;split.applyPresentation();assertEquals(View.VISIBLE,board.visibility)
            layout();assertTrue(board.width>0);assertTrue(pane.width<1200)
        }finally{pane.dispose();board.releaseBacking();media.close()}
    }

    @Test fun currentSolverAcceptsOneUnknownAndRejectsTwoUnknownSystems(){
        assertEquals("x = 4",SmartMath.solve("2x+3=11"))
        assertEquals("x = -3 or x = 3",SmartMath.solve("x^2=9"))
        assertThrows(IllegalArgumentException::class.java){SmartMath.solve("x+y=5\nx-y=1")}
    }
}
