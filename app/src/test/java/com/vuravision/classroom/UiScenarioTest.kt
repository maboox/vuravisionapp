package com.vuravision.classroom

import android.graphics.*
import android.os.Looper
import android.view.*
import android.widget.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Scenario screenshots plus layout rules for the canvas chrome, run for each screen class.
 * Every PNG lands in build/qa/scenario-*.png and is attached to the CI pre-release for review.
 * Rules checked in every state: on screen, no overlap with the dock, dock icons on one axis.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiScenarioTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun idle(ms:Long=600){Shadows.shadowOf(Looper.getMainLooper()).idleFor(ms,TimeUnit.MILLISECONDS)}
    private fun rect(v:View):Rect{val p=IntArray(2);v.getLocationInWindow(p);return Rect(p[0],p[1],p[0]+v.width,p[1]+v.height)}

    /** Lay out at the real display size and let posted rebuilds and animations finish. */
    private fun settle(a:MainActivity){
        val m=a.resources.displayMetrics;val decor=a.window.decorView
        repeat(3){
            decor.measure(View.MeasureSpec.makeMeasureSpec(m.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(m.heightPixels,View.MeasureSpec.EXACTLY))
            decor.layout(0,0,m.widthPixels,m.heightPixels);idle()
        }
    }
    private fun shot(a:MainActivity,name:String){
        settle(a);val m=a.resources.displayMetrics;val b=Bitmap.createBitmap(m.widthPixels,m.heightPixels,Bitmap.Config.ARGB_8888)
        try{views(a.window.decorView).forEach{it.setScrollIndicators(0)};a.window.decorView.draw(Canvas(b));val f=File("build/qa/scenario-$name.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}finally{b.recycle()}
    }
    private fun dock(a:MainActivity)=a.findViewById<ToolDock>(R.id.tool_dock)
    private fun canvas(a:MainActivity)=rect(dock(a).parent as View)

    /** Rules that must hold in every state, whatever is open. */
    private fun checkChrome(a:MainActivity,state:String){
        val d=dock(a);val area=canvas(a);val dr=rect(d)
        assertTrue("$state: dock off screen $dr in $area",area.contains(dr))
        assertTrue("$state: dock taller than 75% of the canvas",dr.height()<=area.height()*.75f)
        val icons=views(d).filterIsInstance<WorkspaceIcon>()
        assertTrue("$state: dock has buttons",icons.size>=10)
        icons.forEach{assertTrue("$state: dock button ${it.key} is not square (${it.width}x${it.height})",kotlin.math.abs(it.width-it.height)<=1)}
        // One column: every icon on the dock's center line. Two columns: two lines, symmetric around it.
        val centers=icons.map{rect(it).centerX()}.distinct().sorted()
        if(centers.size==1)assertTrue("$state: icons off center",kotlin.math.abs(centers[0]-dr.centerX())<=2)
        else{assertEquals("$state: unexpected icon columns $centers",2,centers.size);assertTrue("$state: columns not symmetric",kotlin.math.abs(centers[0]+centers[1]-2*dr.centerX())<=3)}
        val others=listOfNotNull(d.inkPopup.takeIf{it.isShown},a.findViewById<View>(R.id.selection_toolbar)?.takeIf{it.isShown},a.findViewById<View>(R.id.add_panel)?.takeIf{it.isShown})
        others.forEach{o->val r=rect(o);val name=if(o.id!=View.NO_ID)a.resources.getResourceEntryName(o.id)else "popup";assertTrue("$state: $name off screen $r",area.contains(r));assertFalse("$state: overlaps the dock $r / $dr",Rect.intersects(r,dr))}
    }

    private fun run(language:String,size:String,uiScale:Float=1f){
        context.getSharedPreferences("vura",0).edit().clear().putString("language",language).putFloat("uiScale",uiScale).commit()
        val ctl=Robolectric.buildActivity(MainActivity::class.java).setup();val a=ctl.get()
        try{
            val tag="$size-$language"
            val ink=Item(kind="ink",x=260f,y=260f,w=220f,h=140f,points=mutableListOf(Point(0f,0f),Point(220f,140f)),inkW=220f,inkH=140f)
            a.store.editMetadata{a.store.page.items.add(Item(kind="text",text=if(language=="fa")"مساحت دایره" else "Area of a circle",x=80f,y=60f,w=420f,h=60f,width=34f));a.store.page.items.add(ink)}
            shot(a,"$tag-1-idle");checkChrome(a,"$tag idle")

            // Pen strip: the active pen tapped again opens all six colors and three widths.
            views(a.window.decorView).filterIsInstance<WorkspaceIcon>().first{it.key=="pen"}.performClick();settle(a)
            val popup=dock(a).inkPopup;assertTrue("$tag: pen strip did not open",popup.isShown)
            val cells=views(popup).filter{it.contentDescription==a.tr("Ink color","رنگ قلم")||it.contentDescription==a.tr("Ink width","ضخامت قلم")}
            assertEquals("$tag: strip options",9,cells.size)
            cells.forEach{val r=Rect();assertTrue("$tag: strip option clipped",it.getGlobalVisibleRect(r)&&r.width()==it.width&&r.height()==it.height)}
            shot(a,"$tag-2-pen-strip");checkChrome(a,"$tag strip")
            // Touching the board elsewhere closes it.
            val area=canvas(a);val down=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,area.left+area.width()/2f,area.top+area.height()*.8f,0)
            a.dispatchTouchEvent(down);down.recycle();a.dispatchTouchEvent(MotionEvent.obtain(0,10,MotionEvent.ACTION_UP,area.left+area.width()/2f,area.top+area.height()*.8f,0));settle(a)
            assertFalse("$tag: pen strip stayed open after touching the board",popup.isShown)

            // Add panel.
            views(a.window.decorView).filterIsInstance<WorkspaceIcon>().first{it.key=="insert"}.performClick();settle(a)
            assertNotNull("$tag: add panel",a.findViewById<View>(R.id.add_panel));shot(a,"$tag-3-add-panel");checkChrome(a,"$tag add panel")
            views(a.window.decorView).filterIsInstance<WorkspaceIcon>().first{it.key=="insert"}.performClick();settle(a)

            // Selection bar, its smart mode, and a selection right next to the dock.
            a.board.tool="select";a.board.selected.clear();a.board.selected.add(ink.id);a.board.onSelection();settle(a)
            val bar=a.findViewById<View>(R.id.selection_toolbar);assertTrue("$tag: selection bar hidden",bar.isShown)
            assertTrue("$tag: too many selection actions",views(bar).filterIsInstance<ActionIcon>().size<=6)
            shot(a,"$tag-4-selection");checkChrome(a,"$tag selection")
            views(bar).filterIsInstance<ActionIcon>().first{it.symbol=="smart"}.performClick();settle(a)
            shot(a,"$tag-5-smart");checkChrome(a,"$tag smart")
            val dr=rect(dock(a));val board=rect(a.board);val at=a.board.world((dr.centerX()-board.left).toFloat(),(dr.centerY()-board.top).toFloat())
            a.store.editMetadata{ink.x=at.x-ink.w/2;ink.y=at.y-ink.h/2};a.board.onSelection();settle(a)
            shot(a,"$tag-6-selection-near-dock");checkChrome(a,"$tag selection near dock")

            // The bar follows a moved object.
            val before=rect(bar);a.store.editMetadata{ink.x=40f;ink.y=420f};a.board.onSelection();settle(a)
            assertNotEquals("$tag: selection bar did not follow the object",before.left to before.top,rect(bar).left to rect(bar).top)

            // Dock on the other side.
            a.board.selected.clear();a.board.onSelection()
            val side=rect(dock(a)).centerX()<canvas(a).centerX()
            views(a.window.decorView).filterIsInstance<WorkspaceIcon>().first{it.key=="swap"}.performClick();settle(a)
            assertNotEquals("$tag: dock did not change side",side,rect(dock(a)).centerX()<canvas(a).centerX())
            shot(a,"$tag-7-swapped");checkChrome(a,"$tag swapped")
        }finally{ctl.pause().stop().destroy();context.getSharedPreferences("vura",0).edit().clear().commit()}
    }

    @Test @Config(qualifiers="fa-w1280dp-h720dp-land-hdpi") fun panel1080Persian()=run("fa","panel")
    @Test @Config(qualifiers="en-w1280dp-h720dp-land-hdpi") fun panel1080English()=run("en","panel")
    @Test @Config(qualifiers="fa-w1280dp-h720dp-land-hdpi") fun panelLargeUi()=run("fa","panel-large",1.4f)
    @Test @Config(qualifiers="fa-w411dp-h891dp-port-xhdpi") fun phonePortrait()=run("fa","phone")
}
