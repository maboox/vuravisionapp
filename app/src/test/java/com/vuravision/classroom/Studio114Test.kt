package com.vuravision.classroom

import android.graphics.*
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowDialog
import kotlin.math.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="en-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Studio114Test {
    private val context get()=RuntimeEnvironment.getApplication()
    private lateinit var media:Media
    private lateinit var store:Store
    private lateinit var board:Board
    @Before fun setup(){context.getSharedPreferences("vura",0).edit().clear().commit();Fonts.initialize(context);media=Media(context);store=Store();board=Board(context,store,Renderer(media));board.layout(0,0,1000,600);board.holdRecognitionEnabled=false}
    @After fun close(){media.close()}
    private fun event(view:View,action:Int,x:Float,y:Float,time:Long=200){val e=MotionEvent.obtain(100,time,action,x,y,0);view.onTouchEvent(e);e.recycle()}
    private fun trace(vararg points:Pair<Float,Float>){points.forEachIndexed{i,p->event(board,if(i==0)0 else if(i==points.lastIndex)1 else 2,p.first,p.second,200+i*20L)}}
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun call(a:MainActivity,name:String)=MainActivity::class.java.getDeclaredMethod(name).apply{isAccessible=true}.invoke(a)
    private fun choose(d:AlertDialog,label:String){
        val list=requireNotNull(d.listView){"Expected an open menu containing $label"}
        val labels=(0 until list.adapter.count).map{list.adapter.getItem(it).toString()}
        val index=labels.indexOf(label)
        assertTrue("Menu must contain $label; actual items: $labels",index>=0)
        list.performItemClick(list.adapter.getView(index,null,list),index,index.toLong())
    }
    @Test fun freeAndBoxSelectionHaveDifferentHitRegions(){
        val inside=Item(kind="shape",x=20f,y=20f,w=10f,h=10f)
        val outside=Item(kind="shape",x=80f,y=80f,w=10f,h=10f)
        val locked=inside.copy(id=newId(),locked=true)
        val path=listOf(PointF(0f,0f),PointF(100f,0f),PointF(0f,100f))
        assertEquals(listOf(inside),SmartSelection.selectGesture(path,RectF(0f,0f,100f,100f),listOf(inside,outside),"free"))
        assertEquals(listOf(inside,outside),SmartSelection.selectGesture(path,RectF(0f,0f,100f,100f),listOf(inside,outside),"box"))
        store.page.items.addAll(listOf(inside,outside,locked));board.tool="select"
        trace(-10f to -10f,110f to -10f,-10f to 110f,-10f to -10f)
        assertEquals(setOf(inside.id),board.selected)
        board.clearSelection();board.selectionMode="box";trace(-10f to -10f,110f to 110f,110f to 110f)
        assertEquals(setOf(inside.id,outside.id),board.selected)
    }
    @Test fun fourPanesShareTheEntireHeightAndHaveIndependentOrigins(){
        board.split(4)
        for(i in 0..3){val world=board.world(i*250f+125f,550f,i);assertEquals(125f,world.x,.01f);assertEquals(550f,world.y,.01f)}
        trace(875f to 550f,900f to 550f,900f to 550f)
        assertEquals(3,board.activePane);assertEquals(3,store.page.items.single().pane)
    }
    @Test fun guideEntryDropsFreePrefixAndExitIgnoresTheRestUntilLift(){
        store.page.items.add(Item(kind="shape",shape="ruler",x=100f,y=100f,w=300f,h=60f))
        trace(160f to 60f,180f to 80f,180f to 96f,260f to 102f,300f to 50f,350f to 100f,380f to 100f)
        val ink=store.page.items.single{it.kind=="ink"}
        assertEquals(180f,ink.x,.01f);assertEquals(80f,ink.w,.01f)
        ink.points.forEach{assertEquals(100f,ink.y+it.y,.01f)}
        store.undo();assertEquals(1,store.page.items.size)
        trace(500f to 300f,550f to 350f,600f to 400f)
        assertTrue(store.page.items.any{it.kind=="ink" && it.x==500f})
    }
    @Test fun compassHandleStartsExactlyOnCircleAndStopsWhenDraggedOutside(){
        store.page.items.add(Item(kind="shape",shape="compass",x=50f,y=50f,w=200f,h=200f,geometryVersion=1,geometryAngle=0f))
        trace(258f to 154f,150f to 250f,350f to 350f,50f to 150f,50f to 150f)
        val arc=store.page.items.single{it.kind=="ink"}
        arc.points.forEach{assertEquals(100f,hypot(arc.x+it.x-150f,arc.y+it.y-150f),.05f)}
        assertEquals(90f,store.page.items.first().geometrySweep,.01f)
        assertEquals(1,store.undoCount)
    }
    @Test fun protractorDrawingHandleCreatesOneRayAndUndoRestoresAngle(){
        val protractor=Item(kind="shape",shape="protractor",x=100f,y=200f,w=300f,h=150f,geometryAngle=45f)
        store.page.items.add(protractor)
        trace(356.066f to 243.934f,250f to 200f,250f to 200f)
        assertEquals(2,store.page.items.size);val ray=store.page.items.last()
        assertEquals("line",ray.shape);assertEquals(150f,ray.w,.01f);assertEquals(-90f,ray.rotation,.01f)
        assertEquals(90f,protractor.geometryAngle,.01f)
        store.undo();assertEquals(1,store.page.items.size);assertEquals(45f,store.page.items.single().geometryAngle,.01f)
    }
    @Test fun protractorZeroAnglePencilDoesNotResizeTheGuide(){
        val guide=Item(kind="shape",shape="protractor",x=100f,y=200f,w=300f,h=150f,geometryAngle=0f)
        store.page.items.add(guide);trace(400f to 350f,400f to 350f)
        assertEquals(2,store.page.items.size);assertEquals("line",store.page.items.last().shape);assertEquals(300f,guide.w,.01f)
    }
    @Test fun boardUnitsUseTenCoordinatesButPhysicalCalibrationStillUsesPixels(){
        assertEquals(10f,board.measureScale(),.01f)
        context.getSharedPreferences("vura",0).edit().putFloat("pixelsPerCm",40f).commit()
        assertEquals(40f,board.measureScale(),.01f)
        store.page.panes[0].zoom=2f;assertEquals(20f,board.measureScale(),.01f)
        assertEquals("cm",board.measurementUnit())
    }
    @Test fun cropHandlesResizeEverySideAndCancelRestoresPriorRegion(){
        val bitmap=Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888)
        val view=ImageCropView(context,bitmap);view.layout(0,0,400,400)
        val canvas=Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888)
        try{
            fun render(){view.draw(Canvas(canvas))}
            fun drag(x:Float,y:Float,xx:Float,yy:Float){event(view,0,x,y);event(view,2,xx,yy);event(view,1,xx,yy);render()}
            render();drag(24f,200f,70f,200f);assertTrue(view.region.left>0)
            drag(376f,200f,330f,200f);assertTrue(view.region.right<1)
            drag(200f,24f,200f,70f);assertTrue(view.region.top>0)
            drag(200f,376f,200f,330f);assertTrue(view.region.bottom<1)
            val before=RectF(view.region);event(view,0,200f,200f);event(view,2,230f,230f);event(view,3,230f,230f);assertEquals(before,view.region)
            view.reset();assertEquals(RectF(0f,0f,1f,1f),view.region)
        }finally{bitmap.recycle();canvas.recycle()}
    }
    @Test fun ordinarySettingsExposePieSelectionAndBackReturnsThroughMenus(){
        val ctl=Robolectric.buildActivity(MainActivity::class.java).setup();val a=ctl.get()
        try{
            call(a,"menu");choose(ShadowDialog.getLatestDialog() as AlertDialog,a.s("settings"))
            var d=ShadowDialog.getLatestDialog() as AlertDialog
            assertTrue((0 until d.listView.adapter.count).any{d.listView.adapter.getItem(it)==a.s("pie_settings")})
            assertTrue((0 until d.listView.adapter.count).any{d.listView.adapter.getItem(it)==a.s("selection_settings")})
            choose(d,a.s("fonts"));d=ShadowDialog.getLatestDialog() as AlertDialog
            // 1.15: Back is a leading icon in the title; the bottom button closes.
            assertEquals(a.s("close"),d.getButton(AlertDialog.BUTTON_NEGATIVE).text.toString())
            views(d.window!!.decorView).filterIsInstance<WorkspaceIcon>().single{it.contentDescription==a.s("menu_back")}.performClick()
            // Dismissal and the reopened parent are delivered through the main message queue.
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertFalse("Back must close the font settings dialog",d.isShowing)
            val settings=ShadowDialog.getLatestDialog() as AlertDialog
            assertNotSame("Back must reopen the parent menu",d,settings)
            assertTrue("The parent settings menu must be visible",settings.isShowing)
            choose(settings,a.s("pie_settings"))
            d=ShadowDialog.getLatestDialog() as AlertDialog;assertTrue(views(d.window!!.decorView).filterIsInstance<Switch>().any{!it.text.toString().contains("experimental")})
            d.dismiss()
        }finally{ctl.pause().stop().destroy()}
    }
    @Test fun imageSelectionHasCropAndDuplicateWithoutColorOrClipboardActions(){
        val ctl=Robolectric.buildActivity(MainActivity::class.java).setup();val a=ctl.get()
        try{
            a.board.insert(Item(kind="image",asset="sample.png"));call(a,"editSelection")
            val d=ShadowDialog.getLatestDialog() as AlertDialog;val labels=(0 until d.listView.adapter.count).map{d.listView.adapter.getItem(it)}
            assertTrue(a.s("crop") in labels);assertTrue(a.s("duplicate") in labels)
            assertFalse(a.s("copy") in labels);assertFalse(a.s("paste") in labels);assertFalse(a.s("color") in labels)
            assertFalse("copy" in ShortcutCatalog.keys);assertFalse("paste" in ShortcutCatalog.keys);d.dismiss()
        }finally{ctl.pause().stop().destroy()}
    }
    @Test
    @Config(qualifiers="en-w1400dp-h900dp-land-mdpi")
    fun labAndGameCloseAndSaveControlsAreInBottomRows(){
        val ctl=Robolectric.buildActivity(MainActivity::class.java).setup();val a=ctl.get()
        try{
            fun assertBottomActions(vararg labels:String) {
                val d=ShadowDialog.getLatestDialog()
                assertTrue("Lab/game dialog must be visible",d.isShowing)
                // Measure the app's content, not platform DecorView constraints from
                // Robolectric's default phone window; use the same panel dimensions.
                val content=d.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as LinearLayout
                val metrics=a.resources.displayMetrics
                content.measure(View.MeasureSpec.makeMeasureSpec(metrics.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(metrics.heightPixels,View.MeasureSpec.EXACTLY))
                content.layout(0,0,metrics.widthPixels,metrics.heightPixels)
                val footer=content.getChildAt(content.childCount-1) as ViewGroup
                val footerBounds=Rect();footer.getDrawingRect(footerBounds);content.offsetDescendantRectToMyCoords(footer,footerBounds)
                assertEquals("Action footer must touch the padded bottom: $footerBounds in ${content.width}x${content.height}",content.height-content.paddingBottom,footerBounds.bottom)
                labels.forEach{label->
                    val button=views(footer).filterIsInstance<Button>().firstOrNull{it.text.toString()==label}
                    assertNotNull("Bottom action row must contain $label",button)
                    val v=requireNotNull(button);val bounds=Rect();v.getDrawingRect(bounds);content.offsetDescendantRectToMyCoords(v,bounds)
                    assertTrue("$label must have a visible touch target: $bounds",v.isShown && bounds.width()>0 && bounds.height()>0)
                    assertTrue("$label must sit in the bottom third: $bounds in ${content.width}x${content.height}",bounds.top>=content.height*2/3)
                    assertTrue("$label must fit in its footer: $bounds in $footerBounds",footerBounds.contains(bounds))
                }
            }
            Labs.open(a,"native_lens"){it.recycle()};assertBottomActions(a.s("close"),a.s("add_board"));ShadowDialog.getLatestDialog().dismiss()
            NativeGames.open(a,"arc_math");assertBottomActions(a.s("close"));ShadowDialog.getLatestDialog().dismiss()
        }finally{ctl.pause().stop().destroy()}
    }
    @Test fun pageCounterAndPrimaryControlsStayNearBottomInBothLanguages(){
        for(language in listOf("en","fa")){
            context.getSharedPreferences("vura",0).edit().putString("language",language).commit()
            val ctl=Robolectric.buildActivity(MainActivity::class.java).setup();val a=ctl.get()
            try{
                a.store.replace(Lesson(pages=MutableList(12){Page()}));val root=a.window.decorView
                root.measure(View.MeasureSpec.makeMeasureSpec(1400,1073741824),View.MeasureSpec.makeMeasureSpec(900,1073741824));root.layout(0,0,1400,900)
                assertTrue(views(root).filterIsInstance<TextView>().any{it.text.toString()=="1 / 12" && it.isShown})
                val menu=a.findViewById<View>(R.id.main_menu_button);val position=IntArray(2);menu.getLocationInWindow(position)
                assertTrue(position[1]>600);assertTrue(position[0]>700)
            }finally{ctl.pause().stop().destroy()}
        }
    }
}
