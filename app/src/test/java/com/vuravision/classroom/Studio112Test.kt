package com.vuravision.classroom

import android.graphics.*
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="en-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Studio112Test {
    private val context get()=RuntimeEnvironment.getApplication()
    @Test fun closedFillFollowsRotationAndRejectsOpenInk(){
        val o=Item(kind="shape",shape="triangle",x=40f,y=60f,w=200f,h=160f,rotation=41f)
        val inside=o.global(100f,100f);val outside=o.global(10f,10f)
        assertTrue(ShapeFill.contains(o,inside.first,inside.second));assertFalse(ShapeFill.contains(o,outside.first,outside.second))
        assertNull(ShapeFill.path(o.copy(shape="line")))
        val ink=Item(w=100f,h=100f,inkW=100f,inkH=100f,points=mutableListOf(Point(),Point(100f,0f),Point(100f,100f),Point(0f,100f),Point()))
        assertTrue(ShapeFill.contains(ink,50f,50f))
        assertNull(ShapeFill.path(ink.copy(points=ink.points.dropLast(1).toMutableList())))
        assertNull(ShapeFill.path(ink.copy(cuts=listOf(EraseCut(0f,0f,10f,10f,4f,100f,100f)))))
    }
    @Test fun fillKeepsOutlineAndDoesNotCoverOutsideShape(){
        val media=Media(context);val bitmap=Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888)
        try{val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE)
            val shape=Item(kind="shape",shape="rectangle",x=20f,y=20f,w=100f,h=100f,color=Color.BLACK,width=4f,fillColor=Color.RED,fillAlpha=128)
            repeat(3){assertTrue(ShapeFill.contains(shape,70f,70f))}
            Renderer(media).draw(canvas,shape)
            val center=bitmap.getPixel(70,70);assertEquals(255,Color.red(center));assertTrue(Color.green(center) in 120..135)
            assertEquals(Color.BLACK,bitmap.getPixel(20,70));assertEquals(Color.WHITE,bitmap.getPixel(10,10))
        }finally{media.close();bitmap.recycle()}
    }
    @Test fun measurementsUseActualResizedDimensions(){
        val rectangle=Measurements.of(Item(kind="shape",w=100f,h=50f))!!
        assertEquals(300.0,rectangle.perimeter,1e-8);assertEquals(5000.0,rectangle.area!!,1e-8);assertEquals(listOf(90.0,90.0,90.0,90.0),rectangle.angles)
        val triangle=Measurements.of(Item(kind="shape",shape="right_triangle",w=30f,h=40f))!!
        assertEquals(120.0,triangle.perimeter,1e-8);assertEquals(600.0,triangle.area!!,1e-8);assertEquals(180.0,triangle.angles.sum(),1e-7)
        val circle=Measurements.of(Item(kind="shape",shape="circle",w=20f,h=20f))!!
        assertEquals(Math.PI*100,circle.area!!,1e-7);assertEquals(Math.PI*20,circle.perimeter,1e-7)
        assertNull(Measurements.of(Item(kind="shape",shape="compass")))
    }
    @Test fun periodicTableHas118UniqueAccessibleCellsAndPersianNames(){
        val elements=PeriodicTable.elements(context);assertEquals((1..118).toList(),elements.map{it.number})
        assertEquals(118,elements.map{it.col to it.row}.distinct().size);assertEquals(118,elements.map{it.symbol}.distinct().size)
        val table=Item(kind="periodic",x=30f,y=40f,w=900f,h=550f,rotation=18f)
        elements.forEach{e->assertTrue(e.fa.any{it in '\u0600'..'\u06ff'});assertTrue(e.col in 0..17 && e.row in 0..9)
            val at=table.global((e.col+.5f)*50,(e.row+1.5f)*50);assertEquals(e.number,PeriodicTable.hit(context,table,at.first,at.second)?.number)
        }
        assertEquals("Au",elements[78].symbol);assertEquals(6,elements[78].period)
        assertTrue(PeriodicTable.details(context,elements[7]).contains("Atomic number: 8"))
        assertNull(PeriodicTable.hit(context,table,-100f,-100f))
    }
    @Test fun lessonRoundTripIncludesPaletteFillAndPlotPointsAndReadsOldFiles(){
        val lesson=Lesson(customColors=mutableListOf(Color.MAGENTA,Color.CYAN),pages=mutableListOf(Page(items=mutableListOf(
            Item(kind="shape",fillColor=Color.RED,fillAlpha=120),Item(kind="graph",text="x;2*x",plotPoints=listOf(PlotPoint(2.0,4.0))),Item(kind="periodic",selectedElement=79)
        ),panes=mutableListOf(Pane(penStyle="chalk")))))
        val gson=Gson();val loaded=gson.fromJson(gson.toJson(lesson),Lesson::class.java);loaded.validate();assertEquals(lesson,loaded)
        val old=gson.fromJson("{\"schema\":3,\"title\":\"Old\",\"current\":0,\"pages\":[{\"items\":[]}]}",Lesson::class.java);old.validate();assertTrue(old.customColors!!.isEmpty())
        val store=Store(loaded);store.editMetadata{store.lesson.customColors!!.add(Color.GREEN);store.page.items[0].fillColor=null};store.undo()
        assertEquals(2,store.lesson.customColors!!.size);assertEquals(Color.RED,store.page.items[0].fillColor);store.redo();assertEquals(3,store.lesson.customColors!!.size)
    }
    @Test fun canceledTentativeEditRestoresRedoAndNotifiesPdfStyleObservers(){
        val store=Store();var observed=store.page;var canceling=false;store.changed={observed=store.page;canceling=store.isCanceling}
        store.editMetadata{store.lesson.title="saved"};store.undo();store.checkpoint();store.page.items.add(Item());store.cancelCheckpoint()
        assertSame(store.page,observed);assertTrue(canceling);assertFalse(store.isCanceling);assertTrue(store.page.items.isEmpty());store.redo();assertEquals("saved",store.lesson.title)
    }
    @Test fun coordinateIntersectionsRejectAsymptotes(){
        val points=CoordinateTools.intersections(listOf("x^2","1"),10.0)
        assertEquals(2,points.size);assertEquals(-1.0,points[0].x,1e-7);assertEquals(1.0,points[1].x,1e-7)
        assertTrue(CoordinateTools.intersections(listOf("1/x","0"),10.0).isEmpty())
    }
    @Test fun compassMigrationKeepsCenterAndRadius(){
        val old=Item(kind="shape",shape="compass",x=20f,y=50f,w=100f,h=80f,rotation=20f)
        val center=GeometryTools.center(old);val radius=GeometryTools.radius(old);GeometryTools.prepare(old)
        assertEquals(radius,GeometryTools.radius(old),1e-5f);assertEquals(center.first,GeometryTools.center(old).first,1e-5f);assertEquals(center.second,GeometryTools.center(old).second,1e-5f)
        val circle=GeometryTools.construction(old);assertEquals(radius*2,circle.w,1e-5f)
    }
    @Test fun shortcutIconsAreRelatedAndFixedWidthStylesIgnorePressure(){
        ShortcutCatalog.keys.forEach{assertNotEquals(it,R.drawable.feather_more_horizontal,IconCatalog.resource(it))}
        assertNotEquals(IconCatalog.resource("tools"),IconCatalog.resource("timer"));assertNotEquals(IconCatalog.resource("color"),IconCatalog.resource("fill"))
        val a=Item(width=7f,shape="pencil",points=mutableListOf(Point(pressure=.1f)));val b=a.copy(points=mutableListOf(Point(pressure=1f)))
        val p=Paint().apply{strokeWidth=7f;alpha=255};val q=Paint().apply{strokeWidth=7f;alpha=255};PenStyles.apply(p,a);PenStyles.apply(q,b)
        assertEquals(p.strokeWidth,q.strokeWidth);assertEquals(p.alpha,q.alpha)
    }
}
