package com.vuravision.classroom

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test

class AutosaveAndValidationTest {
    private class ManualExecutor:Executor {
        val tasks=ArrayDeque<Runnable>()
        override fun execute(command:Runnable){tasks.addLast(command)}
        fun run(){while(tasks.isNotEmpty())tasks.removeFirst().run()}
    }
    @Test fun waitingSavesCoalesceToLatestSnapshot(){
        val executor=ManualExecutor();val writes=mutableListOf<String>();val receipts=mutableListOf<Long>()
        val queue=ProjectSaveQueue(executor){doc,_->writes.add(doc.title)}
        repeat(20){queue.submit(Lesson(title="$it"),File("a.vura")){id,error->assertNull(error);receipts.add(id)}}
        assertEquals(1,executor.tasks.size);executor.run();assertEquals(listOf("19"),writes);assertEquals(listOf(20L),receipts)
    }
    @Test fun coalescingDoesNotDropOtherProjects(){
        val executor=ManualExecutor();val writes=mutableListOf<String>();val queue=ProjectSaveQueue(executor){doc,_->writes.add(doc.title)}
        queue.submit(Lesson(title="a1"),File("a")){_,_->};queue.submit(Lesson(title="b"),File("b")){_,_->};queue.submit(Lesson(title="a2"),File("a")){_,_->}
        executor.run();assertEquals(listOf("a2","b"),writes)
    }
    @Test fun newSnapshotDuringWriteRunsAfterIt(){
        val executor=ManualExecutor();val writes=mutableListOf<String>();lateinit var queue:ProjectSaveQueue
        queue=ProjectSaveQueue(executor){doc,_->writes.add(doc.title);if(doc.title=="first")queue.submit(Lesson(title="latest"),File("a")){_,_->}}
        queue.submit(Lesson(title="first"),File("a")){_,_->};executor.run();assertEquals(listOf("first","latest"),writes)
    }
    @Test fun failedWriteDoesNotBlockNextProject(){
        val executor=ManualExecutor();val receipts=mutableListOf<Boolean>();val writes=mutableListOf<String>()
        val queue=ProjectSaveQueue(executor){doc,_->if(doc.title=="bad")throw java.io.IOException("disk");writes.add(doc.title)}
        queue.submit(Lesson(title="bad"),File("a")){_,e->receipts.add(e!=null)};queue.submit(Lesson(title="good"),File("b")){_,e->receipts.add(e!=null)}
        executor.run();assertEquals(listOf(true,false),receipts);assertEquals(listOf("good"),writes)
    }
    @Test fun outputLimitRejectsOverflowBeforeWriting(){
        val out=ByteArrayOutputStream();val limited=LimitedOutputStream(out,4)
        limited.write(byteArrayOf(1,2,3));assertThrows(IllegalArgumentException::class.java){limited.write(byteArrayOf(4,5))}
        limited.write(4);assertThrows(IllegalArgumentException::class.java){limited.write(5)};assertArrayEquals(byteArrayOf(1,2,3,4),out.toByteArray())
    }
    @Test fun duplicateAndEmptyPageIdsAreRejected(){
        val page=Page();assertThrows(IllegalArgumentException::class.java){Lesson(pages=mutableListOf(page,page.copied())).validate()}
        assertThrows(IllegalArgumentException::class.java){Lesson(pages=mutableListOf(Page(id=""))).validate()}
    }
    @Test fun assetNamesRejectDirectoryAndTraversalPaths(){
        listOf(".","..","../a","a/b","a\\b","").forEach{assertFalse(it,AssetNames.valid(it))}
        assertTrue(AssetNames.valid("document.json"));assertTrue(AssetNames.valid("board-1.png"))
    }
    @Test fun broadHighlightWidthCanBeSavedButInvalidAlphaCannot(){
        val item=Item(shape="highlight",width=320f);val lesson=Lesson(pages=mutableListOf(Page(items=mutableListOf(item))))
        lesson.validate();item.alpha=256;assertThrows(IllegalArgumentException::class.java){lesson.validate()}
    }
    @Test fun unchangedEditPreservesRedoAndHistoryCapacity(){
        val store=Store();store.editMetadata{store.lesson.title="one"};store.editMetadata{store.lesson.title="two"};store.undo()
        store.editMetadata{};assertEquals(1,store.undoCount);assertEquals(1,store.redoCount);store.redo();assertEquals("two",store.lesson.title)
    }
    @Test fun throwingEditRestoresDocumentAndRedo(){
        val store=Store();store.editMetadata{store.lesson.title="one"};store.undo()
        assertThrows(IllegalStateException::class.java){store.editMetadata{store.lesson.title="broken";error("stop")}}
        assertEquals("",store.lesson.title);assertEquals(0,store.undoCount);assertEquals(1,store.redoCount)
    }
    @Test fun dormantCanvasPointEditsDoNotChangeSavedSnapshots(){
        val store=Store();store.page.items.add(Item(points=mutableListOf(Point(3f,4f))));store.page.switchPanels(2)
        val saved=store.lesson.copyForSave();store.edit{store.page.alternateCanvas!!.items[0].points[0].x=90f}
        assertEquals(3f,saved.pages[0].alternateCanvas!!.items[0].points[0].x,0f);store.undo();assertEquals(3f,store.page.alternateCanvas!!.items[0].points[0].x,0f)
    }
    @Test fun overlappingPausesStopTheClockExactlyOnce(){
        var time=100L;val clock=ActivityClock{time};time=120;clock.pause("dialog",true);time=140;clock.pause("background",true)
        time=160;clock.pause("dialog",false);assertEquals(120L,clock.now());time=190;clock.pause("background",false)
        time=200;assertEquals(130L,clock.now());clock.pause("background",false);assertEquals(130L,clock.now());assertFalse(clock.paused)
    }
}
