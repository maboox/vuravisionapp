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
    @get:Rule val name=org.junit.rules.TestName()
    private var watchdog:Thread?=null
    @Volatile private var hung:String?=null
    /** If a scenario hangs, stop it after 2 minutes and fail with where the main thread was stuck. */
    @Suppress("DEPRECATION")
    @Before fun watch(){
        val main=Thread.currentThread();hung=null
        watchdog=Thread{try{Thread.sleep(120_000);hung=main.stackTrace.take(40).joinToString("\n"){"  at $it"};main.stop()}catch(_:InterruptedException){}}.apply{isDaemon=true;start()}
    }
    @After fun unwatch(){watchdog?.interrupt();hung?.let{throw AssertionError("${name.methodName} hung:\n$it")}}
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun idle(ms:Long=600){Shadows.shadowOf(Looper.getMainLooper()).idleFor(ms,TimeUnit.MILLISECONDS)}
    private fun rect(v:View):Rect{val p=IntArray(2);v.getLocationInWindow(p);return Rect(p[0],p[1],p[0]+v.width,p[1]+v.height)}

    /** Lay out at the real display size and let posted rebuilds and animations finish. */
    private fun settle(a:MainActivity){
        val m=a.resources.displayMetrics;val decor=a.window.decorView
        // Like frames on a device: keep laying out until nothing posted asks for another pass.
        for(pass in 0 until 8){
            decor.measure(View.MeasureSpec.makeMeasureSpec(m.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(m.heightPixels,View.MeasureSpec.EXACTLY))
            decor.layout(0,0,m.widthPixels,m.heightPixels);idle()
            if(pass>=2&&views(decor).none{it.isLayoutRequested})break
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
        // Bottom bars (pages, main menu) are obstacles too: neither the dock nor any popup may cover them.
        val bars=listOf(R.id.page_bar,R.id.main_bar).mapNotNull{a.findViewById<View>(it)?.takeIf{v->v.isShown}}.map{rect(it)}
        bars.forEach{assertFalse("$state: dock overlaps a bottom bar $it / $dr",Rect.intersects(it,dr))}
        // The selection bar never covers the rotation handle (28dp above the selection) or the resize handle (bottom corner).
        a.findViewById<View>(R.id.selection_toolbar)?.takeIf{it.isShown}?.let{bar->a.board.selectionScreenBounds()?.let{b->
            val p=IntArray(2);a.board.getLocationInWindow(p);val d=a.resources.displayMetrics.density
            val rotate=Rect((p[0]+b.centerX()-23*d).toInt(),(p[1]+b.top-51*d).toInt(),(p[0]+b.centerX()+23*d).toInt(),(p[1]+b.top-5*d).toInt())
            val resize=Rect((p[0]+b.right-23*d).toInt(),(p[1]+b.bottom-23*d).toInt(),(p[0]+b.right+23*d).toInt(),(p[1]+b.bottom+23*d).toInt())
            assertFalse("$state: selection bar ${rect(bar)} covers the rotation handle $rotate",Rect.intersects(rect(bar),rotate))
            assertFalse("$state: selection bar ${rect(bar)} covers the resize handle $resize",Rect.intersects(rect(bar),resize))
        }}
        others.forEach{o->val r=rect(o);val name=if(o.id!=View.NO_ID)a.resources.getResourceEntryName(o.id)else "popup";assertTrue("$state: $name off screen $r",area.contains(r));assertFalse("$state: $name overlaps the dock $r / $dr",Rect.intersects(r,dr))
            bars.forEach{assertFalse("$state: $name overlaps a bottom bar $r / $it",Rect.intersects(r,it))}}
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
            // Search finds a tool by part of its name, in this language.
            val search=views(a.findViewById(R.id.add_panel)).filterIsInstance<EditText>().single()
            search.setText(a.s("protractor").take(4));settle(a)
            assertTrue("$tag: search did not find the protractor",views(a.findViewById(R.id.add_panel)).filterIsInstance<WorkspaceIcon>().any{it.key=="protractor"})
            shot(a,"$tag-3b-search");checkChrome(a,"$tag search")
            search.setText("zzzz");settle(a);assertTrue("$tag: no 'nothing found'",views(a.findViewById(R.id.add_panel)).filterIsInstance<TextView>().any{it.text.toString()==a.tr("Nothing found","چیزی پیدا نشد")})
            search.setText("");settle(a)
            // Long press pins a tool to the dock; again unpins it.
            views(a.findViewById(R.id.add_panel)).filterIsInstance<WorkspaceIcon>().first{it.key=="ruler"}.performLongClick();settle(a)
            assertTrue("$tag: ruler not pinned",views(dock(a)).filterIsInstance<WorkspaceIcon>().any{it.key=="ruler"})
            shot(a,"$tag-3c-pinned");checkChrome(a,"$tag pinned")
            views(a.findViewById(R.id.add_panel)).filterIsInstance<WorkspaceIcon>().first{it.key=="ruler"}.performLongClick();settle(a)
            assertFalse("$tag: ruler not unpinned",views(dock(a)).filterIsInstance<WorkspaceIcon>().any{it.key=="ruler"})
            // A math teacher sees math tools first; nothing else disappears.
            views(a.window.decorView).filterIsInstance<WorkspaceIcon>().first{it.key=="insert"}.performClick();settle(a)
            context.getSharedPreferences("vura",0).edit().putString("subject","math").commit()
            views(a.window.decorView).filterIsInstance<WorkspaceIcon>().first{it.key=="insert"}.performClick();settle(a)
            val tiles=views(a.findViewById(R.id.add_panel)).filterIsInstance<WorkspaceIcon>()
            assertEquals("$tag: subject tools should lead","graph",tiles.first().key)
            assertTrue("$tag: other tools must stay",tiles.any{it.key=="periodic"})
            shot(a,"$tag-3d-subject-math")
            context.getSharedPreferences("vura",0).edit().remove("subject").commit()
            views(a.window.decorView).filterIsInstance<WorkspaceIcon>().first{it.key=="insert"}.performClick();settle(a)

            // Selection bar, its smart mode, and a selection right next to the dock.
            a.board.tool="select";a.board.selected.clear();a.board.selected.add(ink.id);a.board.onSelection();settle(a)
            val bar=a.findViewById<View>(R.id.selection_toolbar);assertTrue("$tag: selection bar hidden",bar.isShown)
            assertTrue("$tag: too many selection actions",views(bar).filterIsInstance<ActionIcon>().size<=6)
            shot(a,"$tag-4-selection");checkChrome(a,"$tag selection")
            views(bar).filterIsInstance<ActionIcon>().first{it.symbol=="smart"}.performClick();settle(a)
            shot(a,"$tag-5-smart");checkChrome(a,"$tag smart")
            // Color opens inside the bar (like Smart), not as a window; a tap recolors at once.
            views(bar).filterIsInstance<ActionIcon>().first{it.contentDescription==a.tr("Back","بازگشت")}.performClick();settle(a)
            views(bar).filterIsInstance<ActionIcon>().first{it.symbol=="color"}.performClick();settle(a)
            assertNull("$tag: color opened a window",org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf{it.isShowing})
            val swatches=views(bar).filterIsInstance<InkStrip.Swatch>().filter{it.color!=null}
            assertTrue("$tag: color choices in the bar",swatches.size>=6)
            swatches.first{it.color==InkStrip.colors[1]}.performClick();settle(a)
            assertEquals("$tag: color not applied",InkStrip.colors[1],ink.color)
            shot(a,"$tag-5b-color");checkChrome(a,"$tag color")
            views(bar).filterIsInstance<ActionIcon>().first{it.contentDescription==a.tr("Back","بازگشت")}.performClick();settle(a)
            views(bar).filterIsInstance<ActionIcon>().first{it.symbol=="more"}.performClick();settle(a)
            assertNull("$tag: more opened a window",org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf{it.isShowing})
            shot(a,"$tag-5c-more");checkChrome(a,"$tag more")
            views(bar).filterIsInstance<ActionIcon>().first{it.contentDescription==a.tr("Back","بازگشت")}.performClick();settle(a)
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

    private fun call(a:MainActivity,name:String)=MainActivity::class.java.getDeclaredMethod(name).apply{isAccessible=true}.invoke(a)
    private fun open(language:String="fa"):org.robolectric.android.controller.ActivityController<MainActivity>{
        context.getSharedPreferences("vura",0).edit().clear().putString("language",language).commit()
        return Robolectric.buildActivity(MainActivity::class.java).setup()
    }

    /** Device report: PDF fullscreen, then Close PDF workspace, left an empty board (it stayed hidden). */
    @Test @Config(qualifiers="fa-w1280dp-h720dp-land-hdpi") fun closingFullscreenPdfShowsTheBoard(){
        val ctl=open();val a=ctl.get()
        try{
            val board=a.board;val state=PdfWorkspaceState(asset="source.pdf",sheets=mutableListOf(PdfSheet(595f,842f,Page(background="plain",items=mutableListOf(Item(kind="pdf",asset="source.pdf",pdfPage=0,pageCount=1,w=595f,h=842f,locked=true))))))
            a.store.editMetadata{a.store.lesson.pdf=state};call(a,"syncPdfWorkspace");settle(a)
            state.fullscreen=true;call(a,"refreshPdfPresentation");settle(a);assertFalse("board should hide in PDF fullscreen",board.isShown)
            a.store.editMetadata{a.store.lesson.pdf=null};call(a,"syncPdfWorkspace");settle(a)
            assertTrue("board must be visible after closing the PDF workspace",board.isShown)
            assertTrue(board.width>0&&board.height>0);shot(a,"pdf-closed-after-fullscreen")
        }finally{ctl.pause().stop().destroy()}
    }

    /** Device report: setting one pen to dashed changed the others. Each preset keeps its own style. */
    @Test @Config(qualifiers="fa-w1280dp-h720dp-land-hdpi") fun penPresetsKeepTheirOwnStyle(){
        val ctl=open();val a=ctl.get()
        try{
            settle(a);val pens=views(dock(a)).filterIsInstance<WorkspaceIcon>().filter{it.key=="pen"}
            // Changed in pen settings, then the next pen tapped directly (no outside tap, no dock refresh in between).
            pens[0].performClick();settle(a);a.board.penStyle="dashed";a.board.penOpacity=120;a.board.dashGap=20f
            pens[1].performClick();settle(a)
            assertEquals("second pen took the first pen's style","round",a.board.penStyle)
            assertEquals("second pen took the first pen's opacity",255,a.board.penOpacity)
            a.board.penOpacity=200;call(a,"savePens")
            pens[0].performClick();settle(a)
            assertEquals("first pen lost its style","dashed",a.board.penStyle)
            assertEquals("first pen lost its opacity",120,a.board.penOpacity)
            assertEquals("first pen lost its dash gap",20f,a.board.dashGap,.01f)
            pens[1].performClick();settle(a);assertEquals("second pen lost its opacity",200,a.board.penOpacity)
        }finally{ctl.pause().stop().destroy()}
    }

    /** Device report: split-panel pen/background settings opened a dialog over every panel. */
    @Test @Config(qualifiers="fa-w1280dp-h720dp-land-hdpi") fun splitPanelSettingsOpenInsideThatPanel(){
        val ctl=open();val a=ctl.get()
        try{
            a.board.split(3);a.board.onSelection();settle(a)
            val area=canvas(a)
            for(i in 0 until 3)for(pen in listOf(true,false)){
                val title=if(pen)a.tr("Panel ${i+1}: pen","بخش ${i+1}: قلم")else a.tr("Panel ${i+1}: background","بخش ${i+1}: پس‌زمینه")
                views(a.window.decorView).filterIsInstance<WorkspaceIcon>().first{it.contentDescription==title}.performClick();settle(a)
                assertNull("panel ${i+1}: a dialog opened",org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf{it.isShowing})
                val bar=views(a.window.decorView).first{v->v is TextView&&v.text.toString().startsWith(a.tr("Panel ${i+1} ·","بخش ${i+1} ·"))}.parent as View
                val r=rect(bar);val paneLeft=area.left+i*area.width()/3;val paneRight=area.left+(i+1)*area.width()/3
                assertTrue("panel ${i+1} bar leaves its panel $r",r.left>=paneLeft&&r.right<=paneRight&&area.contains(r))
                assertFalse("panel ${i+1} bar under the dock",Rect.intersects(r,rect(dock(a))))
                views(a.window.decorView).filterIsInstance<WorkspaceIcon>().filter{it.contentDescription.toString().startsWith(a.tr("Panel","بخش"))}.forEach{assertFalse("panel button under the dock ${rect(it)} dock ${rect(dock(a))}",Rect.intersects(rect(it),rect(dock(a))))}
                if(pen)assertEquals("panel ${i+1}: ink options",9,views(bar).count{it.contentDescription==a.tr("Ink color","رنگ قلم")||it.contentDescription==a.tr("Ink width","ضخامت قلم")})
                shot(a,"split-panel-${i+1}-${if(pen)"pen" else "background"}")
                val tap=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,area.left+5f,area.top+5f,0);a.dispatchTouchEvent(tap);tap.recycle();settle(a)
                assertFalse("panel ${i+1}: bar stayed open after touching elsewhere",bar.isAttachedToWindow)
            }
        }finally{ctl.pause().stop().destroy()}
    }

    /** Draws a dialog's window at the size it asked for, into build/qa (dialogs are not part of the activity's shot). */
    private fun shotWindow(d:android.app.Dialog,name:String){
        val m=context.resources.displayMetrics;val attrs=d.window!!.attributes;val decor=d.window!!.decorView
        val w=if(attrs.width>0)attrs.width else m.widthPixels;val h=if(attrs.height>0)attrs.height else m.heightPixels
        decor.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.AT_MOST));decor.layout(0,0,decor.measuredWidth,decor.measuredHeight)
        val b=Bitmap.createBitmap(decor.measuredWidth.coerceAtLeast(1),decor.measuredHeight.coerceAtLeast(1),Bitmap.Config.ARGB_8888)
        try{Canvas(b).apply{drawColor(Color.DKGRAY);decor.draw(this)};val f=File("build/qa/scenario-$name.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}finally{b.recycle()}
    }
    private fun diag(line:String){val f=File("build/qa/diagnostics.txt");f.parentFile!!.mkdirs();f.appendText(line+"\n")}
    private fun settingsScreen(a:MainActivity)=MainActivity::class.java.getDeclaredField("settingsScreen").apply{isAccessible=true}.get(a) as SettingsScreen
    /** Device report: Settings → Smart assistant did nothing. Now every settings page must open, and Settings stays underneath. */
    private fun settingsScenario(tag:String,language:String){
        val ctl=open(language);val a=ctl.get()
        try{
            settle(a);call(a,"settings");idle()
            val screen=settingsScreen(a);val m=context.resources.displayMetrics
            val attrs=screen.dialog.window!!.attributes
            assertTrue("$tag: settings wider than the screen",attrs.width in 1..m.widthPixels);assertTrue("$tag: settings taller than the screen",attrs.height in 1..m.heightPixels)
            screen.categoryKeys().forEach{key->screen.showCategory(key);shotWindow(screen.dialog,"settings-$tag-$language-$key")}
            for(title in screen.pages()){
                if(title==a.s("cache"))continue
                val before=org.robolectric.shadows.ShadowDialog.getLatestDialog()
                screen.open(title);idle()
                val d=org.robolectric.shadows.ShadowDialog.getLatestDialog()
                assertTrue("$tag: '$title' opened nothing",d!==before&&d!==screen.dialog&&d.isShowing)
                d.dismiss();idle()
                assertTrue("$tag: settings closed after '$title'",screen.dialog.isShowing)
            }
            screen.dialog.dismiss()
        }finally{ctl.pause().stop().destroy();context.getSharedPreferences("vura",0).edit().clear().commit()}
    }
    @Test @Config(qualifiers="fa-w1280dp-h720dp-land-hdpi") fun settingsPanelPersian()=settingsScenario("panel","fa")
    @Test @Config(qualifiers="en-w1280dp-h720dp-land-hdpi") fun settingsPanelEnglish()=settingsScenario("panel","en")
    @Test @Config(qualifiers="fa-w411dp-h891dp-port-xhdpi") fun settingsPhonePersian()=settingsScenario("phone","fa")

    /** Board style: an empty page turns green with white chalk; a page with content keeps its color (its ink would vanish). */
    @Test @Config(qualifiers="fa-w1280dp-h720dp-land-hdpi") fun chalkboardStyleOnlyRecolorsAnEmptyPage(){
        val ctl=open();val a=ctl.get()
        try{
            settle(a);val apply=MainActivity::class.java.getDeclaredMethod("applyBoardStyle",String::class.java).apply{isAccessible=true}
            views(a.window.decorView).filterIsInstance<WorkspaceIcon>().first{it.key=="pen"}.performClick();settle(a)
            apply.invoke(a,"chalk");settle(a)
            assertEquals(CHALKBOARD,a.store.page.panes[0].background);assertEquals(0xfff4f4f0.toInt(),a.board.penColor)
            shot(a,"chalkboard-style")
            a.store.editMetadata{a.store.page.items.add(Item(kind="text",text="x",x=10f,y=10f,w=50f,h=30f))}
            apply.invoke(a,"white");settle(a)
            assertEquals("a page with content changed color",CHALKBOARD,a.store.page.panes[0].background)
            assertEquals(NAVY,a.board.penColor)
        }finally{ctl.pause().stop().destroy()}
    }

    /** Laser pen: a stroke shows, fades, and never becomes part of the page or its Undo history. */
    @Test @Config(qualifiers="fa-w1280dp-h720dp-land-hdpi") fun laserPenLeavesNothingBehind(){
        val ctl=open();val a=ctl.get()
        try{
            settle(a);val run=MainActivity::class.java.getDeclaredMethod("runShortcut",String::class.java).apply{isAccessible=true}
            run.invoke(a,"laser");settle(a);assertEquals("laser",a.board.tool)
            val before=a.store.page.items.size;val undo=a.store.undoCount
            val r=rect(a.board);val x=r.left+300f;val y=r.top+300f
            fun touch(action:Int,dx:Float,t:Long){val e=MotionEvent.obtain(0,t,action,x+dx-r.left,y-r.top,0);a.board.onTouchEvent(e);e.recycle()}
            touch(MotionEvent.ACTION_DOWN,0f,0);val afterDown=a.board.livePoints;touch(MotionEvent.ACTION_MOVE,80f,16);touch(MotionEvent.ACTION_MOVE,160f,32);val afterMove=a.board.livePoints;touch(MotionEvent.ACTION_UP,200f,48)
            assertTrue("laser stroke recorded no movement ($afterDown, $afterMove)",afterMove>=3)
            assertEquals("laser stroke was stored",before,a.store.page.items.size);assertEquals("laser stroke entered Undo",undo,a.store.undoCount)
            assertEquals(1,a.board.laserStrokes)
            // Captured at once: shot() waits for layout, by which time the stroke has faded (as designed).
            val bmp=Bitmap.createBitmap(a.board.width,a.board.height,Bitmap.Config.ARGB_8888);a.board.draw(Canvas(bmp))
            var lit=0;for(px in 0 until bmp.width step 2)for(py in 0 until bmp.height step 2){val c=bmp.getPixel(px,py);if(Color.red(c)>200&&Color.green(c)<120&&Color.blue(c)>60)lit++}
            File("build/qa/scenario-laser-stroke.png").apply{parentFile!!.mkdirs()}.outputStream().use{bmp.compress(Bitmap.CompressFormat.PNG,100,it)};bmp.recycle()
            assertTrue("laser stroke not drawn ($lit pixels)",lit>20)
            idle(2600);a.board.invalidate();settle(a);a.board.draw(Canvas(Bitmap.createBitmap(10,10,Bitmap.Config.ARGB_8888)))
            assertEquals("laser stroke did not fade",0,a.board.laserStrokes)
        }finally{ctl.pause().stop().destroy()}
    }
    @Test @Config(qualifiers="fa-w1280dp-h720dp-land-hdpi") fun spotlightAndNamePicker(){
        val ctl=open();val a=ctl.get()
        try{
            settle(a);val run=MainActivity::class.java.getDeclaredMethod("runShortcut",String::class.java).apply{isAccessible=true}
            run.invoke(a,"spotlight");settle(a)
            val light=views(a.window.decorView).filterIsInstance<Spotlight>().single();shot(a,"spotlight")
            views(light).filterIsInstance<WorkspaceIcon>().first{it.key=="close"}.performClick();settle(a)
            assertTrue("spotlight did not close",views(a.window.decorView).none{it is Spotlight})
            context.getSharedPreferences("vura",0).edit().putString("pickerNames","Sara\nAli\nNima").commit()
            run.invoke(a,"picker");settle(a)
            val pick=views(a.window.decorView).filterIsInstance<Button>().first{it.text.toString()==a.s("pick")}
            val picked=mutableSetOf<String>()
            repeat(3){pick.performClick();idle(3000);settle(a)
                picked.add(views(a.window.decorView).filterIsInstance<TextView>().first{it.text.toString() in setOf("Sara","Ali","Nima")&&it !is EditText}.text.toString())}
            assertEquals("someone was picked twice before everyone had a turn",setOf("Sara","Ali","Nima"),picked)
            shot(a,"name-picker")
        }finally{ctl.pause().stop().destroy();context.getSharedPreferences("vura",0).edit().clear().commit()}
    }

    /** Two objects, Connect: an arrow edge to edge that follows a move, survives saving and duplicating, and Unlink removes it. */
    @Test @Config(qualifiers="fa-w1280dp-h720dp-land-hdpi") fun connectorFollowsObjects(){
        val ctl=open();val a=ctl.get()
        try{
            settle(a)
            val first=Item(kind="sticky",text="علت",x=200f,y=200f,w=200f,h=120f);val second=Item(kind="shape",shape="rectangle",x=600f,y=220f,w=200f,h=100f,width=4f)
            a.store.editMetadata{a.store.page.items.addAll(listOf(first,second))}
            a.board.tool="select";a.board.selected.clear();a.board.selected.add(first.id);a.board.selected.add(second.id);a.board.onSelection();settle(a)
            fun tap(symbol:String){views(a.findViewById(R.id.selection_toolbar)).filterIsInstance<ActionIcon>().first{it.symbol==symbol}.performClick();settle(a)}
            tap("connect")
            assertEquals("the arrow runs from the first chosen to the second",second.id,a.store.page.items.first{it.id==first.id}.links)
            fun pixel(x:Float,y:Float):Int{val b=Bitmap.createBitmap(1000,500,Bitmap.Config.ARGB_8888);try{Canvas(b).apply{drawColor(Color.WHITE);a.board.renderer.scene(this,a.store.page)};return b.getPixel(x.toInt(),y.toInt())}finally{b.recycle()}}
            pixel(500f,265f).let{assertTrue("arrow not drawn between the two: ${Integer.toHexString(it)}",Color.red(it)<150&&Color.green(it)>100)}
            shot(a,"connector")
            a.store.editMetadata{a.store.page.items.first{it.id==second.id}.y=420f}
            assertEquals("the arrow follows a move",Color.WHITE,pixel(500f,265f))
            val loaded=com.google.gson.Gson().fromJson(com.google.gson.Gson().toJson(a.store.lesson),Lesson::class.java);loaded.validate()
            assertEquals(second.id,loaded.pages[0].items.first{it.id==first.id}.links)
            val copies=duplicateItems(a.store.page.items.filter{it.id==first.id||it.id==second.id})
            assertEquals("a copied pair keeps its own arrow",copies[1].id,copies[0].links)
            assertNull("an arrow to an object left behind is not copied",duplicateItems(listOf(a.store.page.items.first{it.id==first.id})).single().links)
            a.board.selected.clear();a.board.selected.add(first.id);a.board.selected.add(second.id);a.board.onSelection();settle(a)
            tap("disconnect");assertNull(a.store.page.items.first{it.id==first.id}.links)
            a.store.undo();assertEquals("Undo brings the arrow back",second.id,a.store.page.items.first{it.id==first.id}.links)
        }finally{ctl.pause().stop().destroy()}
    }

    /** A Persian lab, mid-growth and finished, plus the galleries at panel width: for reviewing texts and layout by eye. */
    @Test @Config(qualifiers="fa-w1280dp-h720dp-land-hdpi") fun persianLabAndGalleries(){
        val ctl=open();val a=ctl.get()
        try{
            settle(a)
            for(key in listOf("native_galton","native_pendulum","native_bayes")){
                Labs.open(a,key){it.recycle()};idle(1500)
                val d=org.robolectric.shadows.ShadowDialog.getLatestDialog();shotWindow(d,"lab-${key.removePrefix("native_")}-fa");d.dismiss()
            }
            Labs.show(a,{it.recycle()},{},null);var d=org.robolectric.shadows.ShadowDialog.getLatestDialog();shotWindow(d,"labs-gallery-fa")
            views(d.window!!.decorView).filterIsInstance<EditText>().forEach{diag("gallery search: hint='${it.hint}' hintColor=${Integer.toHexString(it.currentHintTextColor)} focused=${it.isFocused} size=${it.width}x${it.height} text='${it.text}' gravity=${it.gravity} visible=${it.isShown}")}
            d.dismiss()
            Games.show(a);d=org.robolectric.shadows.ShadowDialog.getLatestDialog();shotWindow(d,"games-gallery-fa");d.dismiss()
        }finally{ctl.pause().stop().destroy()}
    }

    @Test @Config(qualifiers="fa-w1280dp-h720dp-land-hdpi") fun panel1080Persian()=run("fa","panel")
    @Test @Config(qualifiers="en-w1280dp-h720dp-land-hdpi") fun panel1080English()=run("en","panel")
    @Test @Config(qualifiers="fa-w1280dp-h720dp-land-hdpi") fun panelLargeUi()=run("fa","panel-large",1.4f)
    @Test @Config(qualifiers="fa-w411dp-h891dp-port-xhdpi") fun phonePortrait()=run("fa","phone")
}
