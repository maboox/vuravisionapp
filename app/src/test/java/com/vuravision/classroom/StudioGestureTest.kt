package com.vuravision.classroom

import android.app.Activity
import android.view.ContextThemeWrapper
import android.graphics.*
import android.os.Looper
import android.view.MotionEvent
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="en-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StudioGestureTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private lateinit var media:Media;private lateinit var store:Store;private lateinit var board:Board
    @Before fun setup(){context.getSharedPreferences("vura",0).edit().clear().commit();media=Media(context);store=Store();board=Board(context,store,Renderer(media));board.layout(0,0,1000,600);board.profile=TouchProfile(calibrated=true,thin=10f,palm=60f)}
    @After fun close(){board.gestures.reset();media.close()}
    private fun event(action:Int,time:Long,xy:List<Pair<Float,Float>>,major:Float=20f,tool:Int=MotionEvent.TOOL_TYPE_FINGER){
        val properties=xy.indices.map{i->MotionEvent.PointerProperties().apply{id=i;toolType=tool}}.toTypedArray()
        val coords=xy.map{p->MotionEvent.PointerCoords().apply{x=p.first;y=p.second;touchMajor=major;pressure=.4f}}.toTypedArray()
        val e=MotionEvent.obtain(100,time,action,xy.size,properties,coords,0,0,1f,1f,0,0,0,0)
        board.onTouchEvent(e);e.recycle()
    }
    private fun pairTap(time:Long,moving:Boolean=false,major:Float=20f){
        event(MotionEvent.ACTION_DOWN,time,listOf(300f to 300f),major)
        event(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8),time+10,listOf(300f to 300f,340f to 300f),major)
        if(moving)event(MotionEvent.ACTION_MOVE,time+20,listOf(330f to 300f,370f to 300f),major)
        event(MotionEvent.ACTION_POINTER_UP or (1 shl 8),time+30,listOf(300f to 300f,340f to 300f),major)
        event(MotionEvent.ACTION_UP,time+40,listOf(300f to 300f),major)
    }
    @Test fun onlyTwoStationaryPairTapsUndoAndFirstTapPreservesRedo(){
        store.editMetadata{store.lesson.title="A"};store.editMetadata{store.lesson.title="B"};store.undo()
        pairTap(200);assertEquals("A",store.lesson.title);store.redo();assertEquals("B",store.lesson.title)
        pairTap(400);assertEquals("A",store.lesson.title)
    }
    @Test fun movingPairAndPalmCannotUndo(){
        store.editMetadata{store.lesson.title="A"};pairTap(200,true);assertEquals(30f,store.page.panes[0].tx,.01f);pairTap(350);assertEquals("A",store.lesson.title)
        board.gestures.reset();pairTap(600,major=80f);pairTap(750,major=80f);assertEquals("A",store.lesson.title)
    }
    @Test fun fiveFingerMenuIsOffByDefaultAndNeedsTwoQuickFiveFingerTaps(){
        var calls=0;board.gestures.onPie={_,_->calls++};val xy=(0..4).map{300f+it*30 to 300f}
        fun tap(time:Long,moving:Boolean=false){event(0,time,xy.take(1));for(n in 2..5)event(5 or ((n-1) shl 8),time+n*10,xy.take(n))
            if(moving)event(2,time+60,xy.map{it.first+30 to it.second})
            for(n in 5 downTo 2)event(6 or ((n-1) shl 8),time+70+(5-n)*10,xy.take(n));event(1,time+110,xy.take(1))}
        store.editMetadata{store.lesson.title="keep"};tap(200);tap(400);assertEquals(0,calls);assertEquals("keep",store.lesson.title)
        board.gestures.pieEnabled=true;board.gestures.reset();tap(800);assertEquals(0,calls);tap(1000);assertEquals(1,calls)
        board.gestures.reset();tap(1500,true);tap(1700);assertEquals(1,calls)
    }
    @Test fun secondPairHoldBrowsesMultipleStepsAndReleaseKeepsChosenHistory(){
        val host=Robolectric.buildActivity(Activity::class.java).setup();host.get().setContentView(board);board.layout(0,0,1000,600)
        try{repeat(12){i->store.editMetadata{store.lesson.title="step $i"}};var shown=-1;board.gestures.onHistory={n,_->shown=n}
            pairTap(200);event(0,400,listOf(300f to 300f));event(5 or (1 shl 8),410,listOf(300f to 300f,340f to 300f))
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(310,TimeUnit.MILLISECONDS)
            assertEquals(11,shown);assertEquals("step 10",store.lesson.title)
            event(2,740,listOf(60f to 300f,100f to 300f));assertEquals("step 0",store.lesson.title);assertEquals(1,shown)
            event(2,780,listOf(180f to 300f,220f to 300f));assertEquals("step 5",store.lesson.title);assertEquals(6,shown)
            event(6 or (1 shl 8),800,listOf(180f to 300f,220f to 300f));event(1,810,listOf(180f to 300f));assertEquals(-1,shown)
            assertEquals("step 5",store.lesson.title);store.redo();assertEquals("step 6",store.lesson.title)
        }finally{host.pause().stop().destroy()}
    }
    @Test fun compassStartHandleRotatesWithoutCreatingInk(){
        val compass=Item(kind="shape",shape="compass",x=50f,y=50f,w=200f,h=200f,geometryVersion=1,geometryAngle=0f);store.page.items.add(compass)
        event(0,200,listOf(205f to 150f),0f,MotionEvent.TOOL_TYPE_STYLUS)
        event(2,240,listOf(150f to 95f),0f,MotionEvent.TOOL_TYPE_STYLUS);event(1,260,listOf(150f to 95f),0f,MotionEvent.TOOL_TYPE_STYLUS)
        assertEquals(1,store.page.items.size);assertEquals(-90f,compass.geometryAngle,.01f)
        store.undo();assertEquals(0f,store.page.items.single().geometryAngle)
    }
    @Test fun rotatedCompassKeepsStartHandleUnderTheFingerAndDrawsFromThere(){
        val compass=Item(kind="shape",shape="compass",x=50f,y=50f,w=200f,h=200f,geometryVersion=1,geometryAngle=0f,rotation=90f);store.page.items.add(compass)
        event(0,200,listOf(150f to 205f),0f,MotionEvent.TOOL_TYPE_STYLUS)
        event(2,230,listOf(95f to 150f),0f,MotionEvent.TOOL_TYPE_STYLUS);event(1,250,listOf(95f to 150f),0f,MotionEvent.TOOL_TYPE_STYLUS)
        assertEquals(1,store.page.items.size);assertEquals(90f,compass.geometryAngle,.01f)
        val start=compass.global(100f,155f);assertEquals(95f,start.first,.01f);assertEquals(150f,start.second,.01f)
        event(0,300,listOf(50f to 150f),0f,MotionEvent.TOOL_TYPE_STYLUS)
        event(2,330,listOf(150f to 50f),0f,MotionEvent.TOOL_TYPE_STYLUS);event(1,350,listOf(150f to 50f),0f,MotionEvent.TOOL_TYPE_STYLUS)
        val handle=GeometryInteraction(board).handles(compass).getValue("turn");val end=compass.global(handle.first,handle.second)
        assertEquals(150f,end.first,.01f);assertEquals(50f,end.second,.01f);assertEquals(2,store.page.items.size)
    }
    @Test fun smoothPenFollowsWithLagAndFinishesAtTheTipWithoutPressure(){
        board.penStyle="smooth";board.holdRecognitionEnabled=false
        event(0,200,listOf(100f to 100f),0f,MotionEvent.TOOL_TYPE_STYLUS)
        event(2,216,listOf(200f to 120f),0f,MotionEvent.TOOL_TYPE_STYLUS)
        event(2,232,listOf(300f to 80f),0f,MotionEvent.TOOL_TYPE_STYLUS)
        event(1,248,listOf(300f to 80f),0f,MotionEvent.TOOL_TYPE_STYLUS)
        val o=store.page.items.single();assertEquals("smooth",o.shape);assertEquals(300f,o.x+o.points.last().x,.01f)
        assertTrue(o.x+o.points[1].x<200f);assertTrue(o.points.size>5);assertEquals(4f,o.width)
        store.undo();assertTrue(store.page.items.isEmpty())
    }
    @Test fun compassTurnDrawsOneArcAndUndoRestoresGuide(){
        val compass=Item(kind="shape",shape="compass",x=50f,y=50f,w=200f,h=200f,geometryVersion=1,geometryAngle=0f);store.page.items.add(compass)
        event(0,200,listOf(250f to 150f),0f,MotionEvent.TOOL_TYPE_STYLUS);assertTrue(board.isDrawing)
        event(2,230,listOf(150f to 250f),0f,MotionEvent.TOOL_TYPE_STYLUS);event(2,260,listOf(50f to 150f),0f,MotionEvent.TOOL_TYPE_STYLUS)
        event(1,280,listOf(50f to 150f),0f,MotionEvent.TOOL_TYPE_STYLUS)
        assertFalse(board.isDrawing);assertEquals(2,store.page.items.size);val arc=store.page.items.last();assertEquals("ink",arc.kind);assertTrue(arc.points.size>=60)
        arc.points.forEach{assertEquals(100f,kotlin.math.hypot(arc.x+it.x-150f,arc.y+it.y-150f),.05f)}
        assertEquals(180f,compass.geometrySweep,.01f);store.undo();assertEquals(1,store.page.items.size);assertEquals(0f,store.page.items.single().geometryAngle)
    }
    @Test fun highlighterOpacityAffectsFixedWidthStroke(){
        board.penStyle="highlight";board.penOpacity=128;board.penWidth=4f
        event(0,200,listOf(100f to 100f),0f,MotionEvent.TOOL_TYPE_STYLUS);event(2,230,listOf(150f to 100f),0f,MotionEvent.TOOL_TYPE_STYLUS);event(1,250,listOf(150f to 100f),0f,MotionEvent.TOOL_TYPE_STYLUS)
        assertEquals(16f,store.page.items.single().width);assertEquals(37,store.page.items.single().alpha)
    }
    @Test fun canceledCompassMoveRestoresPositionAndRedo(){
        store.editMetadata{store.lesson.title="saved"};store.undo()
        val compass=Item(kind="shape",shape="compass",x=50f,y=50f,w=200f,h=200f,geometryVersion=1);store.page.items.add(compass)
        event(0,200,listOf(150f to 150f),0f,MotionEvent.TOOL_TYPE_STYLUS);event(2,230,listOf(200f to 200f),0f,MotionEvent.TOOL_TYPE_STYLUS);event(3,250,listOf(200f to 200f),0f,MotionEvent.TOOL_TYPE_STYLUS)
        assertEquals(50f,store.page.items.single().x);assertEquals(50f,store.page.items.single().y);store.redo();assertEquals("saved",store.lesson.title)
    }
    @Test fun bucketNeverPaintsPageOpenShapeLockedLayerOrCoveredObject(){
        board.tool="fill";board.fillColor=Color.RED
        fun tap(x:Float=150f,y:Float=150f){event(0,200,listOf(x to y),0f,MotionEvent.TOOL_TYPE_STYLUS);event(1,230,listOf(x to y),0f,MotionEvent.TOOL_TYPE_STYLUS)}
        tap();assertEquals(Color.WHITE,store.page.panes[0].background)
        val shape=Item(kind="shape",x=100f,y=100f,w=100f,h=100f);store.page.items.add(shape);tap();assertEquals(Color.RED,shape.fillColor);store.undo()
        val target=store.page.items.single();target.locked=true;tap();assertNull(target.fillColor);target.locked=false
        store.page.layers[0].locked=true;tap();assertNull(target.fillColor);store.page.layers[0].locked=false
        val image=Item(kind="image",x=120f,y=120f,w=60f,h=60f);store.page.items.add(image);tap();assertNull(target.fillColor);store.page.items.remove(image)
        target.shape="line";tap();assertNull(target.fillColor);target.shape="rectangle";tap(20f,20f);assertNull(target.fillColor)
    }
    @Test fun pdfScrollContainerAllowsStationaryDoubleTapUndo(){
        val base=Item(kind="pdf",asset="source.pdf",locked=true);store.page.items.add(base);board.pdfBaseId=base.id
        store.editMetadata{store.lesson.title="A"};store.editMetadata{store.lesson.title="B"}
        val themed=ContextThemeWrapper(context,R.style.AppTheme)
        val pane=PdfPane(themed,PdfWorkspaceState(asset="source.pdf",sheets=mutableListOf(PdfSheet(page=store.page))),media,{board},{},{},{},{})
        val scroll=PdfPane::class.java.getDeclaredField("horizontal").apply{isAccessible=true}.get(pane) as android.widget.HorizontalScrollView
        fun send(action:Int,time:Long,n:Int){
            val props=(0 until n).map{i->MotionEvent.PointerProperties().apply{id=i;toolType=MotionEvent.TOOL_TYPE_FINGER}}.toTypedArray()
            val coords=(0 until n).map{i->MotionEvent.PointerCoords().apply{x=300f+i*40;y=300f;touchMajor=20f}}.toTypedArray()
            val e=MotionEvent.obtain(100,time,action,n,props,coords,0,0,1f,1f,0,0,0,0);scroll.dispatchTouchEvent(e);e.recycle()
        }
        fun tap(time:Long){send(0,time,1);send(5 or (1 shl 8),time+10,2);send(6 or (1 shl 8),time+30,2);send(1,time+40,1)}
        try{tap(200);assertEquals("B",store.lesson.title);tap(400);assertEquals("A",store.lesson.title);assertEquals(1,store.page.items.size)}finally{pane.dispose()}
    }
}
