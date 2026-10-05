package com.vuravision.classroom

import android.graphics.*
import android.os.Looper
import android.view.*
import android.widget.*
import com.google.gson.Gson
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.TimeUnit
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="en-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Studio113Test {
    private val context get()=RuntimeEnvironment.getApplication()
    private lateinit var media:Media
    @Before fun setup(){context.getSharedPreferences("vura",0).edit().clear().commit();media=Media(context);Fonts.initialize(context)}
    @After fun close(){media.close()}
    private fun stroke(vararg xy:Pair<Float,Float>):Item {
        val left=xy.minOf{it.first};val top=xy.minOf{it.second};val w=(xy.maxOf{it.first}-left).coerceAtLeast(1f);val h=(xy.maxOf{it.second}-top).coerceAtLeast(1f)
        return Item(kind="ink",shape="round",x=left,y=top,w=w,h=h,inkW=w,inkH=h,width=4f,points=xy.map{Point(it.first-left,it.second-top)}.toMutableList())
    }
    @Test fun handwrittenNearClosureIsFillableAndOpenOutlineIsRejected(){
        val o=stroke(100f to 100f,200f to 100f,200f to 200f,100f to 200f,100f to 108f)
        assertTrue(ShapeFill.contains(o,150f,150f));assertFalse(ShapeFill.contains(o,90f,150f))
        val open=stroke(100f to 100f,200f to 100f,200f to 200f,100f to 200f);assertNull(ShapeFill.path(open))
    }
    @Test fun multipleHandwrittenStrokesFillOnlyEnclosedAreaAndRoundTrip(){
        val page=Page(items=mutableListOf(stroke(100f to 100f,250f to 100f),stroke(250f to 100f,250f to 250f),stroke(250f to 250f,100f to 250f),stroke(100f to 250f,100f to 100f)))
        val result=HandFill.create(page,0,170f,170f,Color.RED,180)!!
        assertEquals(0,result.index);assertTrue(ShapeFill.contains(result.item,170f,170f));assertTrue(result.item.hit(170f,170f));assertFalse(ShapeFill.contains(result.item,90f,170f))
        page.items.add(result.index,result.item);val lesson=Lesson(pages=mutableListOf(page));lesson.validate()
        val restored=Gson().fromJson(Gson().toJson(lesson),Lesson::class.java);restored.validate();assertEquals(result.item.fillRuns,restored.pages[0].items[0].fillRuns)
        val bitmap=Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888);try{Renderer(media).page(Canvas(bitmap),page,400,400,true);assertTrue(bitmap.getPixel(170,170)!=Color.WHITE)}finally{bitmap.recycle()}
        result.item.w*=2;assertTrue(ShapeFill.contains(result.item,result.item.x+result.item.w/2,170f))
        assertNull(HandFill.create(page,0,20f,20f,Color.RED,255))
    }
    @Test fun erasedOrLockedHandBoundaryDoesNotFill(){
        val loop=stroke(100f to 100f,250f to 100f,250f to 250f,100f to 250f,100f to 100f);val page=Page(items=mutableListOf(loop))
        loop.locked=true;assertNull(HandFill.create(page,0,170f,170f,Color.RED,255));loop.locked=false
        Erasing.cut(loop,PointF(145f,100f),PointF(190f,100f),10f);assertNull(HandFill.create(page,0,170f,170f,Color.RED,255))
    }
    @Test fun bucketTouchFillsHandwritingAndUndoKeepsOutlines(){
        val store=Store();val board=Board(context,store,Renderer(media));board.layout(0,0,800,600)
        store.page.items.addAll(listOf(stroke(100f to 100f,250f to 100f),stroke(250f to 100f,250f to 250f),stroke(250f to 250f,100f to 250f),stroke(100f to 250f,100f to 100f)))
        board.tool="fill";board.fillColor=Color.RED
        for(action in listOf(0,1)){val e=MotionEvent.obtain(100,200,action,170f,170f,0);board.onTouchEvent(e);e.recycle()}
        assertEquals(5,store.page.items.size);assertEquals("region_fill",store.page.items.first().shape);store.undo();assertEquals(4,store.page.items.size)
        val base=Item(kind="pdf",asset="source.pdf",w=800f,h=600f,locked=true);store.page.items.add(0,base);board.pdfBaseId=base.id
        for(action in listOf(0,1)){val e=MotionEvent.obtain(100,300,action,170f,170f,0);board.onTouchEvent(e);e.recycle()}
        assertEquals(6,store.page.items.size);assertEquals(base.id,store.page.items.first().id);assertEquals("region_fill",store.page.items[1].shape)
    }
    @Test fun penDrawnClosedLoopCanBeFilledThroughTheActualTouchPipeline(){
        val store=Store();val board=Board(context,store,Renderer(media));board.layout(0,0,800,600);board.holdRecognitionEnabled=false
        val points=listOf(100f to 100f,250f to 100f,250f to 250f,100f to 250f,100f to 106f)
        points.forEachIndexed{i,xy->val action=if(i==0)0 else if(i==points.lastIndex)1 else 2;val e=MotionEvent.obtain(100,200+i*16L,action,xy.first,xy.second,0);board.onTouchEvent(e);e.recycle()}
        assertEquals(1,store.page.items.size);board.tool="fill";board.fillColor=Color.MAGENTA
        for(action in listOf(0,1)){val e=MotionEvent.obtain(100,350,action,170f,170f,0);board.onTouchEvent(e);e.recycle()}
        assertEquals(Color.MAGENTA,store.page.items.single().fillColor);store.undo();assertNull(store.page.items.single().fillColor)
    }
    @Test fun closedRegionWithinAnOpenIntersectingStrokeCanFill(){
        val loop=stroke(100f to 100f,250f to 100f,250f to 250f,100f to 250f,100f to 100f,50f to 50f)
        assertNull(ShapeFill.path(loop));val page=Page(items=mutableListOf(loop))
        val result=HandFill.create(page,0,170f,170f,Color.RED,255);assertNotNull(result);assertTrue(ShapeFill.contains(result!!.item,170f,170f))
        assertNull(HandFill.create(page,0,60f,180f,Color.RED,255))
    }
    @Test fun defaultsUseKahrobaAndRubikAndPreserveExplicitChoice(){
        assertEquals("kahroba",Fonts.persianId);assertEquals("rubik",Fonts.englishId)
        Fonts.choose(context,"vazirmatn","montserrat");Fonts.initialize(context)
        assertEquals("vazirmatn",Fonts.persianId);assertEquals("montserrat",Fonts.englishId)
    }
    @Test fun timingPrecisionRulerSpacingAndHistorySeekAreBounded(){
        assertEquals("1.3",DisplayNumbers.one(1.26));assertEquals("0",DisplayNumbers.one(-.004));assertEquals("123",DisplayNumbers.one(123.0))
        assertEquals(-45.0,Measurements.lineAngle(Item(kind="shape",shape="line",w=100f,h=100f,rotation=90f)),.01)
        for(px in listOf(5f,10f,20f,40f,70f))assertTrue(GeometryTools.rulerLabelStep(px)*px>=40f)
        val g=AboutGesture();assertFalse(g.click(100));assertFalse(g.click(200));assertFalse(g.click(700));assertFalse(g.click(800));assertFalse(g.click(5000));assertFalse(g.click(5100));assertFalse(g.click(6500));assertTrue(g.click(6600))
        val store=Store();repeat(12){i->store.editMetadata{store.lesson.title="step $i"}};var calls=0;store.changed={calls++}
        store.seekHistory(2);assertEquals("step 1",store.lesson.title);assertEquals(1,calls);store.seekHistory(9);assertEquals("step 8",store.lesson.title);assertEquals(2,calls)
    }
    @Test fun measurementOverlayExpiresWithoutEditingLesson(){
        val ctl=Robolectric.buildActivity(MainActivity::class.java).setup();val a=ctl.get()
        try{val item=Item(kind="shape",shape="triangle",x=200f,y=180f,w=240f,h=180f,rotation=20f);a.store.page.items.add(item)
            val before=Gson().toJson(a.store.lesson);a.board.showMeasurements(item);assertTrue(a.board.measurements.visible)
            capture(a.board,"measure-overlay",1000,600);assertEquals(before,Gson().toJson(a.store.lesson));assertEquals(0,a.store.undoCount)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(6100,TimeUnit.MILLISECONDS);assertFalse(a.board.measurements.visible)
        }finally{ctl.pause().stop().destroy()}
    }
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun call(a:MainActivity,name:String)=MainActivity::class.java.getDeclaredMethod(name).apply{isAccessible=true}.invoke(a)
    private fun capture(v:View,name:String,w:Int=1280,h:Int=800){views(v).forEach{it.setScrollIndicators(0)};v.measure(View.MeasureSpec.makeMeasureSpec(w,1073741824),View.MeasureSpec.makeMeasureSpec(h,1073741824));v.layout(0,0,w,h);val b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);try{v.draw(Canvas(b));val file=File("build/qa/studio113-$name.png");file.parentFile!!.mkdirs();file.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}finally{b.recycle()}}
    @Test fun pieSettingsRefreshImmediatelyAndOnlyAssignedSlotsAppear(){
        val ctl=Robolectric.buildActivity(MainActivity::class.java).setup();val a=ctl.get()
        try{a.getSharedPreferences("vura",0).edit().putBoolean("pieEnabled",true).putString("pieSlots","[\"undo\",\"erase\"]").commit()
            call(a,"pieSettings");val settings=ShadowDialog.getLatestDialog()
            MainActivity::class.java.getDeclaredMethod("assignPie",Int::class.javaPrimitiveType).apply{isAccessible=true}.invoke(a,1)
            val choices=ShadowDialog.getLatestDialog();val list=views(choices.window!!.decorView).filterIsInstance<ListView>().single();val n=ShortcutCatalog.keys.indexOf("redo")+1
            list.performItemClick(list.adapter.getView(n,null,list),n,n.toLong())
            assertTrue(views(settings.window!!.decorView).filterIsInstance<TextView>().any{it.text.toString()=="2 · "+a.s("redo")});settings.dismiss()
            capture(a.window.decorView,"workspace")
            MainActivity::class.java.getDeclaredMethod("pieMenu",Float::class.javaPrimitiveType,Float::class.javaPrimitiveType).apply{isAccessible=true}.invoke(a,640f,400f)
            val overlay=MainActivity::class.java.getDeclaredField("pieOverlay").apply{isAccessible=true}.get(a) as ViewGroup
            assertEquals(2,overlay.childCount);capture(a.window.decorView,"two-slot-pie")
        }finally{ctl.pause().stop().destroy()}
    }
    @Test fun persianPageManagerAndHistoryHudRenderWithConfiguredFonts(){
        context.getSharedPreferences("vura",0).edit().putString("language","fa").commit()
        val ctl=Robolectric.buildActivity(MainActivity::class.java).setup();val a=ctl.get()
        try{capture(a.window.decorView,"workspace-fa");a.board.gestures.onHistory?.invoke(4,12)
            capture(a.window.decorView,"history-fa");val hud=views(a.window.decorView).filterIsInstance<HistoryScrubView>().single();assertEquals(4,hud.position);assertEquals(12,hud.total)
            a.board.gestures.onHistory?.invoke(-1,0);assertTrue(views(a.window.decorView).filterIsInstance<HistoryScrubView>().isEmpty())
            call(a,"pages");val d=ShadowDialog.getLatestDialog();capture(d.window!!.decorView,"pages-fa")
            assertEquals(View.LAYOUT_DIRECTION_RTL,d.window!!.decorView.layoutDirection);assertTrue(views(d.window!!.decorView).filterIsInstance<TextView>().any{it.text.toString()==a.s("pages")});d.dismiss()
        }finally{ctl.pause().stop().destroy()}
    }
    @Test fun elementCardHasTileAndSmallTextAndPageManagerUsesLazyRows(){
        val ctl=Robolectric.buildActivity(MainActivity::class.java).setup();val a=ctl.get()
        try{var card:Item?=null;PeriodicTable.elementDialog(a,PeriodicTable.elements(a)[25]){card=it}
            val d=ShadowDialog.getLatestDialog();views(d.window!!.decorView).filterIsInstance<Button>().first{it.text.toString()==a.tr("Add element card to board","افزودن کارت عنصر به تخته")}.performClick()
            a.board.insert(card!!);assertEquals(15f,card!!.width);assertEquals(26,card!!.selectedElement);assertEquals("element_card",card!!.shape)
            assertTrue(card!!.h>=TextLayout.layout(card!!).height+PeriodicTable.cardHeader(card!!));capture(a.board,"element-card",1000,600)
            repeat(40){a.store.lesson.pages.add(Page())};call(a,"pages");val pages=ShadowDialog.getLatestDialog()
            capture(pages.window!!.decorView,"pages");val list=views(pages.window!!.decorView).filterIsInstance<ListView>().single()
            assertEquals(41,list.adapter.count);assertTrue(list.childCount<41);pages.dismiss()
        }finally{ctl.pause().stop().destroy()}
    }
}
