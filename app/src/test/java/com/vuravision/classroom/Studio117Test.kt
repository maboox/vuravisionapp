package com.vuravision.classroom

import android.graphics.*
import android.view.*
import com.google.gson.Gson
import java.io.*
import java.util.zip.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="en-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Studio117Test {
    private val context get()=RuntimeEnvironment.getApplication()
    private lateinit var media:Media
    @Before fun setup(){context.getSharedPreferences("vura",0).edit().clear().commit();Fonts.initialize(context);media=Media(context)}
    @After fun close(){media.close()}
    private fun board(store:Store)=Board(context,store,Renderer(media)).apply{layout(0,0,1000,600);holdRecognitionEnabled=false}
    private fun event(v:View,action:Int,x:Float,y:Float,time:Long=200){val e=MotionEvent.obtain(100,time,action,x,y,0);try{v.onTouchEvent(e)}finally{e.recycle()}}
    private fun trace(b:Board,x:Float,y:Float,xx:Float,yy:Float){event(b,0,x,y);event(b,2,xx,yy,220);event(b,1,xx,yy,240)}
    private fun line(x:Float=100f,y:Float=100f)=Item(x=x,y=y,w=200f,h=1f,inkW=200f,inkH=1f,points=mutableListOf(Point(0f,0f),Point(200f,0f)))
    private fun zip(doc:Lesson,assets:Map<String,ByteArray>):ByteArray {val out=ByteArrayOutputStream();ZipOutputStream(out).use{z->
        (mapOf("document.json" to Gson().toJson(doc).toByteArray())+assets.mapKeys{"assets/${it.key}"}).forEach{(name,bytes)->z.putNextEntry(ZipEntry(name));z.write(bytes);z.closeEntry()}
    };return out.toByteArray()}
    private fun set(obj:Any,name:String,value:Any){obj.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(obj,value)}

    @Test fun cancelledStrokeEraseRestoresTheStrokeAndRedo(){
        val store=Store();store.page.items.add(line());store.editMetadata{store.lesson.title="next"};store.undo();val b=board(store);b.tool="erase"
        event(b,0,180f,100f);assertTrue(store.page.items.isEmpty());event(b,3,180f,100f)
        assertEquals(1,store.page.items.size);assertEquals(0,store.undoCount);assertEquals(1,store.redoCount)
    }
    @Test fun cancelledAreaEraseRestoresMasks(){
        val store=Store();store.page.items.add(line());val b=board(store);b.tool="erase";b.eraserMode="area"
        event(b,0,180f,100f);assertTrue(store.page.items.single().cuts.isNotEmpty());event(b,3,180f,100f)
        assertTrue(store.page.items.single().cuts.isEmpty());assertEquals(0,store.undoCount)
    }
    @Test fun toolbarUndoCancelsTentativeEraseBeforeUndoingLastEdit(){
        val store=Store();store.editMetadata{store.page.items.add(line())};val b=board(store);b.tool="erase"
        event(b,0,180f,100f);b.undo();assertTrue(store.page.items.isEmpty());assertEquals(1,store.redoCount)
        b.redo();assertEquals(1,store.page.items.size);assertTrue(store.page.items.single().cuts.isEmpty())
    }
    @Test fun changingSplitDuringActiveGestureIsBlocked(){
        val store=Store();val b=board(store);event(b,0,50f,50f);b.split(4);assertEquals(1,store.page.visiblePaneCount)
        event(b,3,50f,50f);b.split(4);assertEquals(4,store.page.visiblePaneCount)
    }
    @Test fun panelPenSettingsRemainIndependent(){
        val store=Store();val b=board(store);b.split(2);b.inkColor=Color.RED;b.inkWidth=12f;b.applySharedPane(1);b.inkColor=Color.BLUE;b.inkWidth=8f
        b.applySharedPane(0);assertEquals(Color.RED,b.inkColor);assertEquals(12f,b.inkWidth,0f);assertEquals(Color.BLUE,store.page.panes[1].color)
    }
    @Test fun panToolCannotDragGeometryHandles(){
        val store=Store();val ruler=Item(kind="shape",shape="ruler",x=100f,y=100f,w=300f,h=64f);store.page.items.add(ruler)
        val b=board(store);b.tool="pan";trace(b,100f,132f,140f,172f);assertEquals(100f,ruler.x,0f);assertEquals(0,store.undoCount)
    }
    @Test fun graphCurvesAreReusedUntilTheirGeometryChanges(){
        val renderer=Renderer(media);val graph=Item(kind="graph",text="sin(x);x^2",w=500f,h=300f)
        val bitmap=Bitmap.createBitmap(600,400,Bitmap.Config.ARGB_8888);val c=Canvas(bitmap)
        renderer.draw(c,graph);val count=renderer.curveBuilds;repeat(10){renderer.draw(c,graph)};assertEquals(count,renderer.curveBuilds)
        graph.w=550f;renderer.draw(c,graph);assertEquals(count+2,renderer.curveBuilds);bitmap.recycle()
    }
    @Test fun changingTextOpacityUpdatesTheCachedLayout(){
        val item=Item(kind="text",text="Hello سلام",alpha=240,width=25f)
        val first=TextLayout.layout(item);assertEquals(240,first.paint.alpha);item.alpha=60
        val second=TextLayout.layout(item);assertNotSame(first,second);assertEquals(60,second.paint.alpha)
    }
    @Test fun thumbnailsDecodeAtSmallResolutionAndCanUpgrade(){
        val source=Bitmap.createBitmap(2048,1024,Bitmap.Config.ARGB_8888);source.eraseColor(Color.RED);val name=media.save(source);source.recycle()
        val item=Item(kind="image",asset=name);val small=media.image(item,true,256)!!;val large=media.image(item,true,1536)!!
        assertTrue(small.width<=256);assertTrue(large.width>small.width);assertEquals(Color.RED,large.getPixel(10,10))
    }
    @Test fun closedMediaDoesNotStartNewImageJobs(){
        var callbacks=0;media.ready={callbacks++};media.close();assertNull(media.image(Item(kind="image",asset="missing.png")));assertEquals(0,callbacks)
    }
    @Test fun assetNamedDocumentJsonCannotOverwriteImportedDocument(){
        val doc=Lesson(title="Project",pages=mutableListOf(Page(items=mutableListOf(Item(kind="image",asset="document.json")))))
        val loaded=LessonFiles(media).read(zip(doc,mapOf("document.json" to byteArrayOf(1,2,3))).inputStream())
        assertEquals("Project",loaded.title);assertArrayEquals(byteArrayOf(1,2,3),media.file(loaded.pages[0].items.single().asset).readBytes())
    }
    @Test fun collidingAssetsAreRenamedWithoutReplacingLocalBytes(){
        val name="${newId()}.png";media.file(name).writeBytes(byteArrayOf(1,2,3))
        val doc=Lesson(pages=mutableListOf(Page(items=mutableListOf(Item(kind="image",asset=name)))))
        val loaded=LessonFiles(media).read(zip(doc,mapOf(name to byteArrayOf(3,2,1))).inputStream());val actual=loaded.pages[0].items.single().asset
        assertNotEquals(name,actual);assertArrayEquals(byteArrayOf(1,2,3),media.file(name).readBytes());assertArrayEquals(byteArrayOf(3,2,1),media.file(actual).readBytes())
    }
    @Test fun failedImportRollsBackNewAssets(){
        val before=media.dir.list()!!.toSet();val first="${newId()}.png";val missing="${newId()}.png"
        val doc=Lesson(pages=mutableListOf(Page(items=mutableListOf(Item(kind="image",asset=first),Item(kind="image",asset=missing)))))
        assertThrows(IllegalArgumentException::class.java){LessonFiles(media).read(zip(doc,mapOf(first to byteArrayOf(1))).inputStream())}
        assertEquals(before,media.dir.list()!!.toSet())
    }
    @Test fun failedAtomicSaveKeepsLastReadableDocument(){
        val target=File(context.cacheDir,"${newId()}.vura");val files=LessonFiles(media);files.atomic(Lesson(title="safe"),target)
        val invalid=Lesson(pages=mutableListOf(Page(items=mutableListOf(Item(kind="image",asset="missing-${newId()}.png")))))
        assertThrows(IllegalArgumentException::class.java){files.atomic(invalid,target)};assertEquals("safe",files.read(target.inputStream()).title);assertFalse(File(target.path+".tmp").exists())
    }
    @Test fun equalAssetSizeDoesNotImplyEqualBytes(){
        val a=File(context.cacheDir,"${newId()}.bin").apply{writeBytes(byteArrayOf(1,2))};val b=File(context.cacheDir,"${newId()}.bin").apply{writeBytes(byteArrayOf(2,1))}
        assertFalse(sameFileContents(a,b));b.writeBytes(a.readBytes());assertTrue(sameFileContents(a,b));a.delete();b.delete()
    }
    @Test fun sweptEraserDetectsCrossingBetweenDistantPointerSamples(){
        val ink=line();assertTrue(Erasing.hitsInk(ink,PointF(200f,0f),PointF(200f,300f),2f))
        assertFalse(Erasing.hitsInk(ink,PointF(100f,150f),PointF(300f,150f),2f))
    }
    @Test fun sweptEraserRespectsRotatedInkAndDegenerateSegments(){
        val ink=line().apply{rotation=90f};val hit=ink.global(80f,0f)
        assertTrue(Erasing.hitsInk(ink,PointF(hit.first-100,hit.second),PointF(hit.first+100,hit.second),2f))
        assertTrue(Erasing.hitsInk(ink,PointF(hit.first,hit.second),PointF(hit.first,hit.second),2f))
        assertFalse(Erasing.hitsInk(ink,PointF(800f,800f),PointF(800f,800f),2f))
    }
    @Test fun inkAttachesInRotatedNodeCoordinatesAndHonorsLease(){
        val node=Item(kind="sticky",shape="mindnode",x=100f,y=100f,w=200f,h=80f,rotation=90f);val page=Page(items=mutableListOf(node));val start=node.global(10f,40f)
        val ink=line(start.first,start.second);assertFalse(MindMap.attach(page,ink,start.first,start.second){false});assertEquals("",ink.parentNode)
        assertTrue(MindMap.attach(page,ink,start.first,start.second));assertEquals(node.id,ink.parentNode)
    }
    @Test fun mindMapDescendantsCannotCrossPanels(){
        val parent=Item(kind="sticky",shape="mindnode");val child=Item(parentNode=parent.id,pane=0);val other=Item(parentNode=parent.id,pane=1)
        val page=Page(items=mutableListOf(parent,child,other),panes=mutableListOf(Pane(),Pane()))
        assertEquals(setOf(parent.id,child.id),MindMap.group(page,listOf(parent)).map{it.id}.toSet())
    }
    @Test fun cancellingGuideMoveRestoresItWithoutUndoEntry(){
        val store=Store();val ruler=Item(kind="shape",shape="ruler",x=100f,y=100f,w=300f,h=64f);store.page.items.add(ruler);val b=board(store)
        event(b,0,100f,132f);event(b,2,160f,172f);event(b,3,160f,172f)
        assertEquals(100f,store.page.items.single().x,0f);assertEquals(100f,store.page.items.single().y,0f);assertEquals(0,store.undoCount)
    }
    @Test fun fitKeepsContentWithinUnobstructedViewport(){
        val store=Store();val item=Item(kind="shape",x=500f,y=300f,w=700f,h=500f);store.page.items.add(item);val b=board(store)
        val area=RectF(90f,20f,1000f,470f);b.fitViewport={area};b.fit();assertTrue(area.contains(b.screenRect(itemBounds(item))))
    }
    @Test fun incrementalInkCommitMatchesFullRenderOnScaled4kBuffer(){
        val store=Store();val b=board(store);b.layout(0,0,4000,2400)
        val fast=Bitmap.createBitmap(4000,2400,Bitmap.Config.ARGB_8888);b.draw(Canvas(fast));trace(b,1800f,900f,2600f,1300f);b.draw(Canvas(fast))
        val full=Bitmap.createBitmap(4000,2400,Bitmap.Config.ARGB_8888);b.sceneChanged();b.draw(Canvas(full))
        assertTrue("Incremental and rebuilt board pixels differ",fast.sameAs(full));fast.recycle();full.recycle();b.releaseBacking()
    }
    @Test fun endingHostFlushesQueuedDrawingAndIsIdempotent(){
        val store=Store();val b=board(store);val room=RoomController(context,store,b,media,{},{})
        val engine=RoomEngine(store.lesson,DeviceProfile(name="Host"),"Class");val server=RoomServer(engine,media)
        set(room,"server",server);set(room,"peer",engine.hostId);set(room,"active",true);set(room,"hosting",true);set(room,"frame",engine.frame(engine.hostId,-1));set(room,"joinedDocument",true)
        try{store.editMetadata{store.page.items.add(line())};room.localChange();room.endHostAndFlush();room.endHostAndFlush()
            assertEquals(1,engine.snapshot().pages[0].items.size);assertEquals(1,store.page.items.size)
        }finally{room.close()}
    }
    @Test fun newerAppliedRoomSnapshotSupersedesDeferredSnapshot(){
        val store=Store();val b=board(store);val room=RoomController(context,store,b,media,{},{})
        val method=RoomController::class.java.getDeclaredMethod("receive",RoomFrame::class.java,Lesson::class.java).apply{isAccessible=true}
        set(room,"joinedDocument",true);set(room,"deferred",Lesson(title="older"));set(room,"frame",RoomFrame(revision=1))
        try{method.invoke(room,RoomFrame(revision=3),Lesson(title="latest"));assertEquals("latest",store.lesson.title)
            assertNull(RoomController::class.java.getDeclaredField("deferred").apply{isAccessible=true}.get(room))
            method.invoke(room,RoomFrame(revision=2),Lesson(title="stale"));assertEquals("latest",store.lesson.title)
        }finally{room.close()}
    }
}
