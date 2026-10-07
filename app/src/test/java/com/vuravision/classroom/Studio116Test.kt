package com.vuravision.classroom

import android.graphics.*
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.google.gson.Gson
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowDialog
import java.io.ByteArrayOutputStream
import kotlin.math.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="en-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Studio116Test {
    private val context get()=RuntimeEnvironment.getApplication()
    private lateinit var media:Media
    @Before fun setup(){context.getSharedPreferences("vura",0).edit().clear().commit();Fonts.initialize(context);media=Media(context)}
    @After fun close(){media.close()}
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun call(a:MainActivity,name:String)=MainActivity::class.java.getDeclaredMethod(name).apply{isAccessible=true}.invoke(a)
    private fun event(v:View,action:Int,x:Float,y:Float,t:Long=200){val e=MotionEvent.obtain(100,t,action,x,y,0);v.onTouchEvent(e);e.recycle()}
    private fun trace(board:Board,vararg points:Pair<Float,Float>){points.forEachIndexed{i,p->event(board,if(i==0)0 else if(i==points.lastIndex)1 else 2,p.first,p.second,200+i*20L)}}
    private fun board(store:Store)=Board(context,store,Renderer(media)).apply{layout(0,0,1000,600);holdRecognitionEnabled=false}
    private fun withActivity(action:(MainActivity)->Unit){val ctl=Robolectric.buildActivity(MainActivity::class.java).setup();try{action(ctl.get())}finally{ctl.pause().stop().destroy()}}
    private fun dialogViews()=views(ShadowDialog.getLatestDialog().window!!.decorView)
    private fun dragClear(v:SlideToClearView,complete:Boolean=true,cancel:Boolean=false){v.layout(0,0,400,64);event(v,0,30f,32f);event(v,2,if(complete)370f else 200f,32f);event(v,if(cancel)3 else 1,if(complete)370f else 200f,32f)}

    @Test fun returningToSoloRestoresTheOriginalInsteadOfMerging(){
        val original=Item(kind="text",text="Solo",x=10f);val page=Page(items=mutableListOf(original));page.switchPanels(4)
        page.items[0].text="Split";val fourth=Item(kind="shape",pane=3);page.items.add(fourth);page.panes[3].pattern="grid"
        page.switchPanels(1);assertEquals(1,page.visiblePaneCount);assertEquals("Solo",page.items.single().text);assertEquals(10f,page.items.single().x,0f)
        page.switchPanels(4);assertEquals("Split",page.items[0].text);assertTrue(page.items.any{it.id==fourth.id&&it.pane==3});assertEquals("grid",page.pattern(3))
    }
    @Test fun reducingPanelCountHidesAndRetainsTheOtherPanels(){
        val page=Page();page.switchPanels(4);val fourth=Item(kind="shape",pane=3);page.items.add(fourth)
        page.switchPanels(2);assertEquals(2,page.visiblePaneCount);assertTrue(page.visibleItems().isEmpty());assertEquals(3,page.items.single().pane)
        page.switchPanels(3);assertTrue(page.visibleItems().isEmpty());page.switchPanels(4);assertEquals(fourth.id,page.visibleItems().single().id)
    }
    @Test fun splitSwitchAndBothWorkspacesSurviveUndoRedo(){
        val store=Store();store.page.items.add(Item(kind="text",text="Original"));val b=board(store);b.split(2)
        store.editMetadata{store.page.items.add(Item(kind="text",text="Partner",pane=1))};b.split(1)
        assertEquals(listOf("Original"),store.page.items.map{it.text});store.undo();assertEquals(2,store.page.visiblePaneCount);assertEquals(2,store.page.items.size)
        store.redo();assertEquals(1,store.page.visiblePaneCount);assertEquals(2,store.page.alternateCanvas!!.items.size)
    }
    @Test fun oldSplitDocumentsRecoverOnlyTheirFirstPanel(){
        val a=Item(kind="shape");val b=Item(kind="shape",pane=1);val page=Page(items=mutableListOf(a,b),panes=mutableListOf(Pane(),Pane()))
        page.switchPanels(1);assertEquals(listOf(a.id),page.items.map{it.id});page.switchPanels(2);assertEquals(listOf(a.id,b.id),page.items.map{it.id})
    }
    @Test fun dormantImageAssetsArePackedWithTheLesson(){
        val bitmap=Bitmap.createBitmap(24,24,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.RED);val asset=media.save(bitmap);bitmap.recycle()
        val lesson=Lesson();lesson.pages[0].switchPanels(2);lesson.pages[0].items.add(Item(kind="image",asset=asset,pane=1));lesson.pages[0].switchPanels(1)
        assertTrue(lesson.pages[0].items.isEmpty());val out=ByteArrayOutputStream();LessonFiles(media).write(lesson,out)
        val loaded=LessonFiles(media).read(out.toByteArray().inputStream());loaded.pages[0].switchPanels(2)
        assertEquals(asset,loaded.pages[0].items.single().asset);assertNotNull(media.image(loaded.pages[0].items.single(),true))
    }
    @Test fun serializationAndSnapshotsKeepBothCanvasesIndependent(){
        val lesson=Lesson();lesson.pages[0].items.add(Item(points=mutableListOf(Point(1f,2f))));lesson.pages[0].switchPanels(3)
        lesson.pages[0].panes[1].pattern="ruled";val saved=Gson().fromJson(Gson().toJson(lesson),Lesson::class.java);saved.validate()
        val copy=saved.copyDeep();copy.pages[0].alternateCanvas!!.items[0].points[0].x=90f
        assertEquals(1f,saved.pages[0].alternateCanvas!!.items[0].points[0].x,0f);assertEquals("ruled",saved.pages[0].pattern(1))
    }
    @Test fun dormantInvalidObjectsAreValidatedToo(){
        val lesson=Lesson();lesson.pages[0].switchPanels(2);lesson.pages[0].alternateCanvas!!.items.add(Item(x=Float.NaN))
        assertThrows(IllegalArgumentException::class.java){lesson.validate()}
    }
    @Test fun changingAPanelPatternLeavesOtherPanelsUntouched(){withActivity{a->
        a.board.split(2);MainActivity::class.java.getDeclaredMethod("backgroundSettings",Int::class.javaPrimitiveType).apply{isAccessible=true}.invoke(a,1)
        dialogViews().filterIsInstance<Button>().single{it.text.toString()=="Hatched"}.performClick()
        assertEquals("hatch",a.store.page.pattern(1));assertEquals("dots",a.store.page.pattern(0));ShadowDialog.getLatestDialog().dismiss()
    }}
    @Test fun panelBackgroundRenderingUsesItsOwnPattern(){
        val page=Page(background="dots",panes=mutableListOf(Pane(pattern="plain"),Pane(pattern="grid")))
        val bitmap=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888);val c=Canvas(bitmap);val renderer=Renderer(media)
        renderer.background(c,page,RectF(0f,0f,80f,80f),Color.WHITE,0);assertEquals(Color.WHITE,bitmap.getPixel(32,20))
        renderer.background(c,page,RectF(0f,0f,80f,80f),Color.WHITE,1);assertNotEquals(Color.WHITE,bitmap.getPixel(32,20));bitmap.recycle()
    }
    @Test fun exportUsesFourVerticalPanels(){
        val page=Page(panes=mutableListOf(Pane(background=Color.RED),Pane(background=Color.GREEN),Pane(background=Color.BLUE),Pane(background=Color.YELLOW)))
        val bitmap=Bitmap.createBitmap(800,400,Bitmap.Config.ARGB_8888);Renderer(media).page(Canvas(bitmap),page,800,400,true)
        listOf(Color.RED,Color.GREEN,Color.BLUE,Color.YELLOW).forEachIndexed{i,color->assertEquals(color,bitmap.getPixel(i*200+100,60));assertEquals(color,bitmap.getPixel(i*200+100,340))};bitmap.recycle()
    }
    @Test fun graphAndFixedStyleGuidesHaveNoColorOrSmartActions(){withActivity{a->
        for(o in listOf(Item(kind="graph"),Item(kind="shape",shape="ruler"),Item(kind="shape",shape="set_square"))){a.board.insert(o)
            assertFalse(views(a.window.decorView).filterIsInstance<ActionIcon>().any{it.symbol=="color"});call(a,"editSelection")
            val list=(ShadowDialog.getLatestDialog() as AlertDialog).listView;val keys=(0 until list.adapter.count).map{list.adapter.getItem(it)}
            assertFalse(keys.contains(a.s("color")));if(o.shape=="ruler"){assertTrue(keys.contains(a.s("draw_parallel")));assertFalse(keys.contains(a.s("draw_with_tool")));assertTrue(keys.contains(a.s("rotate")))}
            ShadowDialog.getLatestDialog().dismiss()
        }
    }}
    @Test fun mindMapHasOnlyNamedBranchActionsAndNoSmartConversions(){withActivity{a->
        a.board.insert(Item(kind="sticky",shape="mindnode"));val icons=views(a.window.decorView).filterIsInstance<ActionIcon>()
        assertFalse(icons.any{it.symbol in listOf("formula","graph","convert","search","insert")});assertTrue(icons.any{it.symbol=="text"})
        call(a,"editSelection");val list=(ShadowDialog.getLatestDialog() as AlertDialog).listView;val keys=(0 until list.adapter.count).map{list.adapter.getItem(it)}
        assertTrue(keys.contains(a.s("add_child")));assertTrue(keys.contains(a.s("add_sibling")));assertFalse(keys.contains(a.s("smart")));ShadowDialog.getLatestDialog().dismiss()
    }}
    @Test fun connectedPointSegmentsFindTheirCrossingWithoutExtraVertices(){
        val vertices=listOf(PlotPoint(-2.0,-2.0),PlotPoint(2.0,2.0),PlotPoint(-2.0,2.0),PlotPoint(2.0,-2.0))
        val result=CoordinateTools.intersections(emptyList(),10.0,vertices,true);assertEquals(1,result.size);assertEquals(0.0,result.single().x,1e-8);assertEquals(0.0,result.single().y,1e-8)
        assertEquals(4,vertices.size);assertTrue(CoordinateTools.intersections(emptyList(),10.0,vertices,false).isEmpty())
    }
    @Test fun pointSegmentsIntersectFunctionsAndDoNotReportAsymptotes(){
        val points=CoordinateTools.intersections(listOf("x^2"),10.0,listOf(PlotPoint(-3.0,1.0),PlotPoint(3.0,1.0)),true)
        assertEquals(2,points.size);assertEquals(-1.0,points[0].x,1e-6);assertEquals(1.0,points[1].x,1e-6)
        assertTrue(CoordinateTools.intersections(listOf("1/x"),10.0,listOf(PlotPoint(-3.0,0.0),PlotPoint(3.0,0.0)),true).isEmpty())
    }
    @Test fun coincidentSegmentsAreNotReportedAsHundredsOfIntersections(){
        assertTrue(CoordinateTools.intersections(listOf("1"),10.0,listOf(PlotPoint(-3.0,1.0),PlotPoint(3.0,1.0)),true).isEmpty())
    }
    @Test fun connectedGraphAndIntersectionMarksSurviveSaving(){
        val o=Item(kind="graph",connectPlotPoints=true,plotPoints=listOf(PlotPoint(-1.0,-1.0),PlotPoint(1.0,1.0)),plotIntersections=listOf(PlotPoint(0.0,0.0)))
        val loaded=Gson().fromJson(Gson().toJson(Lesson(pages=mutableListOf(Page(items=mutableListOf(o))))),Lesson::class.java);loaded.validate();assertTrue(loaded.pages[0].items.single().connectPlotPoints);assertEquals(1,loaded.pages[0].items.single().plotIntersections!!.size)
    }
    @Test fun triangleStrokeKeepsItsFirstEdgeAtACorner(){
        val store=Store();val b=board(store);store.page.items.add(Item(kind="shape",shape="set_square",x=100f,y=100f,w=300f,h=150f))
        trace(b,240f to 170f,350f to 225f,400f to 250f,350f to 250f,260f to 250f,180f to 250f)
        val ink=store.page.items.single{it.kind=="ink"};ink.points.forEach{assertEquals((ink.x+it.x-100)/2+100,ink.y+it.y,.01f)}
        assertTrue(store.page.items.any{it.kind=="text"&&"°" in it.text})
    }
    @Test fun cornerStartChoosesTheEdgeFromItsFirstMovement(){
        for(path in listOf(listOf(100f to 250f,150f to 250f,220f to 250f),listOf(100f to 100f,140f to 120f,220f to 160f))){
            val store=Store();val b=board(store);store.page.items.add(Item(kind="shape",shape="set_square",x=100f,y=100f,w=300f,h=150f))
            trace(b,*path.toTypedArray());val ink=store.page.items.single{it.kind=="ink"};assertEquals(120f,ink.w,.01f)
            ink.points.forEach{assertEquals(if(path.first().second==250f)250f else (ink.x+it.x-100)/2+100,ink.y+it.y,.01f)}
        }
    }
    @Test fun snappedGuideMeasurementIsPersistentAndSharesUndo(){
        val store=Store();val b=board(store);store.page.items.add(Item(kind="shape",shape="ruler",x=100f,y=100f,w=300f,h=64f))
        trace(b,180f to 98f,230f to 100f,260f to 100f);val text=store.page.items.single{it.kind=="text"}
        assertEquals("8 u",text.text);assertEquals(1,store.undoCount);store.undo();assertEquals(1,store.page.items.size);store.redo();assertEquals("8 u",store.page.items.single{it.kind=="text"}.text)
    }
    @Test fun constructionAndItsLabelUsePanelPenAndUndoTogether(){withActivity{a->
        a.board.split(2)
        a.store.page.panes[0].color=Color.MAGENTA;a.store.page.panes[0].penWidth=9f
        a.store.page.panes[1].color=Color.CYAN;a.store.page.panes[1].penWidth=13f
        val insert=MainActivity::class.java.getDeclaredMethod("insertConstruction",Item::class.java,Boolean::class.javaPrimitiveType).apply{isAccessible=true}
        for(index in 0..1){a.board.applySharedPane(index)
            val ruler=Item(kind="shape",shape="ruler",w=300f,h=64f,pane=index);a.store.page.items.add(ruler)
            for(perpendicular in listOf(false,true)){
                val before=a.store.undoCount;val ids=a.store.page.items.map{it.id}
                insert.invoke(a,a.store.page.items.single{it.id==ruler.id},perpendicular)
                val line=a.store.page.items.single{it.kind=="shape"&&it.shape=="line"}
                assertEquals(if(index==0)Color.MAGENTA else Color.CYAN,line.color);assertEquals(if(index==0)9f else 13f,line.width,0f);assertEquals(index,line.pane)
                val label=a.store.page.items.single{it.kind=="text"};assertTrue(label.text.startsWith("30 u"));assertEquals(index,label.pane)
                assertEquals(before+1,a.store.undoCount);a.store.undo();assertEquals(ids,a.store.page.items.map{it.id})
            }
        }
    }}
    @Test fun compassAndProtractorHandlesUseTheirOwnPanelPen(){
        for(shape in listOf("compass","protractor"))for(index in 0..1){
            val store=Store();val b=board(store);b.split(2);b.penColor=Color.BLACK;b.penWidth=2f
            store.page.panes[0].color=Color.MAGENTA;store.page.panes[0].penWidth=9f
            store.page.panes[1].color=Color.CYAN;store.page.panes[1].penWidth=13f
            val guide=if(shape=="compass")Item(kind="shape",shape=shape,x=50f,y=50f,w=200f,h=200f,pane=index,geometryVersion=1,geometryAngle=0f)
                else Item(kind="shape",shape=shape,x=100f,y=200f,w=300f,h=150f,pane=index,geometryAngle=45f)
            store.page.items.add(guide);val before=store.undoCount;val dx=index*500f
            if(shape=="compass")trace(b,(250f+dx) to 150f,(150f+dx) to 250f,(150f+dx) to 250f)
            else trace(b,(356.066f+dx) to 243.934f,(250f+dx) to 200f,(250f+dx) to 200f)
            val drawing=store.page.items.single{it.id!=guide.id&&it.kind!="text"}
            assertEquals(if(index==0)Color.MAGENTA else Color.CYAN,drawing.color);assertEquals(if(index==0)9f else 13f,drawing.width,0f);assertEquals(index,drawing.pane)
            val label=store.page.items.single{it.kind=="text"};assertEquals(index,label.pane);assertTrue(label.text.endsWith("90°"))
            assertEquals(before+1,store.undoCount);store.undo();assertEquals(listOf(guide.id),store.page.items.map{it.id})
            store.redo();assertEquals(3,store.page.items.size);assertEquals(drawing.id,store.page.items.single{it.id!=guide.id&&it.kind!="text"}.id)
        }
    }
    @Test fun returningToSoloMakesGuideDrawingUseTheCurrentPen(){
        for(tool in listOf("pen","highlight")){
            val store=Store();val b=board(store);b.split(2);store.page.panes[0].color=Color.RED;store.page.panes[0].penWidth=2f;b.split(1)
            b.tool=tool;b.penColor=Color.MAGENTA;b.penWidth=9f;b.highlightColor=Color.CYAN;b.highlightWidth=13f
            store.page.items.add(Item(kind="shape",shape="compass",x=50f,y=50f,w=200f,h=200f,geometryVersion=1,geometryAngle=0f))
            trace(b,250f to 150f,150f to 250f,150f to 250f)
            val arc=store.page.items.single{it.kind=="ink"}
            assertEquals(if(tool=="pen")Color.MAGENTA else Color.CYAN,arc.color);assertEquals(if(tool=="pen")9f else 13f,arc.width,0f)
        }
    }
    @Test fun permanentLabelsRemainValidAtExtremeZoom(){
        for(zoom in listOf(.001f,1f,1000f)){val label=GuideLabels.text("20 u · 90°",100f,100f,zoom,Item());Lesson(pages=mutableListOf(Page(items=mutableListOf(label)))).validate();assertTrue(label.width in .1f..200f)}
    }
    @Test fun compassDrawingCommitsItsAngleLabelWithTheArc(){
        val store=Store();val b=board(store);store.page.items.add(Item(kind="shape",shape="compass",x=50f,y=50f,w=200f,h=200f,geometryVersion=1,geometryAngle=0f))
        trace(b,250f to 150f,150f to 250f,150f to 250f);assertTrue(store.page.items.single{it.kind=="text"}.text.contains("90°"));store.undo();assertEquals(1,store.page.items.size)
    }
    @Test fun rulerRotationHandleRotatesWithoutDrawing(){
        val store=Store();val b=board(store);val ruler=Item(kind="shape",shape="ruler",x=100f,y=100f,w=300f,h=64f);store.page.items.add(ruler)
        val interaction=GeometryInteraction(b);val rotate=interaction.handles(ruler).getValue("rotate");val at=ruler.global(rotate.first,rotate.second)
        assertTrue(interaction.start(PointF(at.first,at.second),22f));val center=ruler.global(ruler.w/2,ruler.h/2);val target=PointF(center.first-(at.second-center.second),center.second)
        val move=MotionEvent.obtain(100,200,2,0f,0f,0);interaction.event(move,target);move.recycle();val up=MotionEvent.obtain(100,220,1,0f,0f,0);interaction.event(up,target);up.recycle()
        assertEquals(90f,ruler.rotation,.01f);assertEquals(1,store.page.items.size);store.undo();assertEquals(0f,store.page.items.single().rotation,.01f)
    }
    @Test fun slopeAndGuideNumbersScaleWithToolSize(){
        val square=Item(kind="shape",shape="set_square",w=300f,h=150f);GeometryTools.setSlope(square,45f);assertEquals(square.w,square.h,.01f)
        assertThrows(IllegalArgumentException::class.java){GeometryTools.setSlope(square,0f)}
        for(shape in GeometryTools.keys){val o=Item(kind="shape",shape=shape,w=400f,h=200f,geometryVersion=1);val size=GeometryTools.numberSize(o);o.w*=2;o.h*=2;assertEquals(size*2,GeometryTools.numberSize(o),.01f)}
    }
    @Test fun slideRequiresFullDragAndReleaseAndCancelDoesNothing(){
        var clears=0;val view=SlideToClearView(context,"Slide"){clears++};dragClear(view,false);assertEquals(0,clears);dragClear(view,true,true);assertEquals(0,clears);dragClear(view);assertEquals(1,clears)
        event(view,0,200f,32f);event(view,2,370f,32f);event(view,1,370f,32f);assertEquals(1,clears)
    }
    @Test fun clearSliderClearsOnlyTheActivePanelAndKeepsLocks(){withActivity{a->
        a.board.split(2);val locked=Item(kind="shape",locked=true);val local=Item(kind="shape");val other=Item(kind="shape",pane=1);a.store.page.items.addAll(listOf(locked,local,other));val count=a.store.undoCount
        call(a,"eraserSettings");dragClear(dialogViews().filterIsInstance<SlideToClearView>().single())
        assertEquals(setOf(locked.id,other.id),a.store.page.items.map{it.id}.toSet());assertEquals(count+1,a.store.undoCount);a.store.undo();assertEquals(3,a.store.page.items.size)
    }}
    @Test fun pieCatalogIncludesClearPageAndItsCommandUsesTheSlider(){withActivity{a->
        assertTrue("clear_page" in ShortcutCatalog.keys);MainActivity::class.java.getDeclaredMethod("runShortcut",String::class.java).apply{isAccessible=true}.invoke(a,"clear_page")
        assertEquals(1,dialogViews().filterIsInstance<SlideToClearView>().size);ShadowDialog.getLatestDialog().dismiss()
    }}
    @Test fun pageActionsAreDirectAndDuplicatePreservesTheDormantCanvas(){withActivity{a->
        a.board.split(2);a.store.page.items.add(Item(kind="shape",pane=1));a.board.split(1);val old=a.store.page.alternateCanvas!!.items.single().id;call(a,"pages")
        val list=dialogViews().filterIsInstance<ListView>().single();val row=list.adapter.getView(0,null,list);val controls=views(row).filterIsInstance<WorkspaceIcon>()
        assertEquals(5,controls.size);controls.single{it.contentDescription.toString()==a.s("duplicate")}.performClick()
        assertEquals(2,a.store.lesson.pages.size);assertNotEquals(old,a.store.page.alternateCanvas!!.items.single().id);assertEquals(1,a.store.page.alternateCanvas!!.items.single().pane)
        ShadowDialog.getLatestDialog().dismiss()
    }}
    @Test fun uiScaleChangesTheActualDrawableSize(){
        fun size(scale:Float):Float{context.getSharedPreferences("vura",0).edit().putFloat("uiScale",scale).commit();val v=WorkspaceIcon(context,"pen","Pen"){};val n=context.dp(48);v.measure(View.MeasureSpec.makeMeasureSpec(n,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(n,View.MeasureSpec.EXACTLY));v.layout(0,0,n,n);val bounds=RectF(v.drawable.bounds);v.imageMatrix.mapRect(bounds);return bounds.width()}
        assertTrue(size(1.4f)>size(.8f)*1.6f)
    }
    @Test fun fiftyHistoryStepsAreDefaultAndTheConfiguredLimitIsApplied(){
        val store=Store();repeat(60){i->store.editMetadata{store.lesson.title="$i"}};assertEquals(50,store.undoCount);repeat(50){store.undo()};assertEquals("9",store.lesson.title)
        store.historyLimit=10;assertEquals(10,store.redoCount);store.redo();assertEquals("10",store.lesson.title)
    }
    @Test fun periodicTableCanInsertACompleteBitmap(){withActivity{a->
        val captured=mutableListOf<Item>();PeriodicTable.show(a){captured.add(it)};dialogViews().filterIsInstance<Button>().single{it.text.toString()=="Add complete table as image"}.performClick()
        val image=captured.single();assertEquals("image",image.kind);val bitmap=media.image(image,true)!!;assertEquals(1800,bitmap.width);assertEquals(1100,bitmap.height)
        assertNotEquals(Color.WHITE,bitmap.getPixel(50,150));assertNotEquals(Color.WHITE,bitmap.getPixel(1750,750))
    }}
    @Test fun playerNameChangesAreLocalPersistentAndUsedByResults(){withActivity{a->
        GamePlayers.edit(a,0){};val d=ShadowDialog.getLatestDialog() as AlertDialog;dialogViews().filterIsInstance<EditText>().single().setText("Sara");d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Sara",GamePlayers.name(context,0));assertEquals("Sara",GamePlayers.display(context,"player1"));assertEquals(context.s("player2"),GamePlayers.name(context,1))
        val game=ArcadeView(context,"reaction");game.layout(0,0,1000,700);var named=-1;game.onRename={named=it};event(game,0,250f,104f);assertEquals(0,named)
    }}
}
