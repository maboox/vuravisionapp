package com.vuravision.classroom

import android.graphics.*
import android.view.*
import android.widget.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="en-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StudioUiTest {
    private val context get()=RuntimeEnvironment.getApplication()
    @Before fun clean(){context.getSharedPreferences("vura",0).edit().clear().commit()}
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun call(a:MainActivity,name:String)=MainActivity::class.java.getDeclaredMethod(name).apply{isAccessible=true}.invoke(a)
    private fun capture(v:View,name:String,w:Int=1280,h:Int=800){
        views(v).forEach{it.setScrollIndicators(0)}
        v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h)
        val b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);try{v.draw(Canvas(b));val f=File("build/qa/$name.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}finally{b.recycle()}
    }
    @Test fun newPageDefaultsAndStyleInheritanceNeverCopyContent(){
        val ctl=Robolectric.buildActivity(MainActivity::class.java).setup();val a=ctl.get()
        try{val prefs=a.getSharedPreferences("vura",0);prefs.edit().putString("defaultPattern","grid").putInt("defaultBackground",Color.CYAN).commit()
            fun page(previous:Page?)=MainActivity::class.java.getDeclaredMethod("newBoardPage",Page::class.java).apply{isAccessible=true}.invoke(a,previous) as Page
            val previous=Page(background="hatch",panes=mutableListOf(Pane(background=Color.YELLOW),Pane(background=Color.RED)),items=mutableListOf(Item()))
            var next=page(previous);assertEquals("grid",next.background);assertEquals(Color.CYAN,next.panes.single().background);assertTrue(next.items.isEmpty())
            prefs.edit().putBoolean("inheritBackground",true).commit();next=page(previous);assertEquals("hatch",next.background);assertEquals(Color.YELLOW,next.panes.single().background);assertTrue(next.items.isEmpty())
            next=page(null);assertEquals("grid",next.background);assertEquals(Color.CYAN,next.panes.single().background)
        }finally{ctl.pause().stop().destroy()}
    }
    @Test fun settingsExposePieAndCalibrationWithSelectionInItsTool(){
        val ctl=Robolectric.buildActivity(MainActivity::class.java).setup();val a=ctl.get()
        try{assertFalse(a.getSharedPreferences("vura",0).getBoolean("pieEnabled",false));assertFalse(VoiceSettings.enabled(a))
            call(a,"settings");val d=ShadowDialog.getLatestDialog();val keys=views(d.window!!.decorView).filterIsInstance<ListView>().single().adapter.let{(0 until it.count).map{n->it.getItem(n)}}
            assertTrue(keys.contains(a.s("calibration")));assertTrue(keys.contains(a.s("pie_settings")));assertFalse(keys.contains(a.s("selection_settings")))
            assertFalse(keys.contains(a.s("help")));assertFalse(keys.contains(a.s("voice_assistant")));d.dismiss()
            call(a,"eraserSettings");val e=ShadowDialog.getLatestDialog();assertFalse(views(e.window!!.decorView).filterIsInstance<Button>().any{it.text.toString().contains("Calibrate")});e.dismiss()
        }finally{ctl.pause().stop().destroy()}
    }
    @Test fun uiScaleChangesBothIconAndTextSize(){
        val prefs=context.getSharedPreferences("vura",0);prefs.edit().putFloat("uiScale",.8f).commit()
        val small=context.label("Test");val icon=WorkspaceIcon(context,"pen","Pen"){}
        val smallSize=context.dp(48);prefs.edit().putFloat("uiScale",1.4f).commit()
        assertTrue(context.dp(48)>smallSize*1.6);assertTrue(context.label("Test").textSize>small.textSize*1.6)
        assertEquals("Pen",icon.contentDescription)
    }
    @Test fun shortcutsUseActivePaneAndKeepPdfBaseLocked(){
        val ctl=Robolectric.buildActivity(MainActivity::class.java).setup();val a=ctl.get()
        try{fun shortcut(key:String){MainActivity::class.java.getDeclaredMethod("runShortcut",String::class.java).apply{isAccessible=true}.invoke(a,key)}
            a.store.page.panes.add(Pane(penWidth=12f));a.board.layout(0,0,1000,600);a.board.tool="pan"
            for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)){val e=MotionEvent.obtain(100,200,action,800f,200f,0);a.board.onTouchEvent(e);e.recycle()}
            assertEquals(1,a.board.activePane);shortcut("thicker");assertEquals(14f,a.store.page.panes[1].penWidth);assertEquals(4f,a.store.page.panes[0].penWidth)
            a.store.page.panes=mutableListOf(Pane());a.board.reset()
            val base=Item(kind="pdf",asset="source.pdf",locked=true);val note=Item(kind="text",text="Note",locked=true);a.store.page.items=mutableListOf(base,note);a.board.pdfBaseId=base.id
            shortcut("unlock_all");assertTrue(base.locked);assertFalse(note.locked)
        }finally{ctl.pause().stop().destroy()}
    }
    @Test fun englishCompactDialogsAndTableRender(){render("en")}
    @Test fun persianCompactDialogsAndTableRender(){render("fa")}
    private fun render(language:String){
        context.getSharedPreferences("vura",0).edit().putString("language",language).commit();val ctl=Robolectric.buildActivity(MainActivity::class.java).setup();val a=ctl.get()
        try{for(method in listOf("penSettings","eraserSettings","defaultBackgroundSettings","pieSettings","calibrationSettings")){
                call(a,method);val d=ShadowDialog.getLatestDialog();capture(d.window!!.decorView,"studio-$method-$language")
                if(method=="penSettings"){val all=views(d.window!!.decorView);assertTrue(all.any{it.contentDescription==a.tr("Add color to this lesson","افزودن رنگ به این فایل")});assertTrue(all.filterIsInstance<Button>().any{it.text.toString()==PenStyles.name(a,"smooth")});assertFalse(all.filterIsInstance<TextView>().any{it.text.toString().contains("pressure sensing")})}
                d.dismiss()
            }
            MainActivity::class.java.getDeclaredMethod("addText",Boolean::class.javaPrimitiveType).apply{isAccessible=true}.invoke(a,false)
            var d=ShadowDialog.getLatestDialog();capture(d.window!!.decorView,"studio-text-$language");assertTrue(views(d.window!!.decorView).any{it.contentDescription==a.s("bold")});d.dismiss()
            PeriodicTable.show(a){};d=ShadowDialog.getLatestDialog();capture(d.window!!.decorView,"studio-periodic-$language")
            val table=views(d.window!!.decorView).first{it.javaClass.name.contains("PeriodicTable\$show\$table")}
            File("build/qa/periodic-bounds-$language.txt").writeText(views(d.window!!.decorView).joinToString("\n"){"${it.javaClass.simpleName}: ${it.width}x${it.height}, ${it.left},${it.top}, scroll=${it.scrollX},${it.scrollY}, visibility=${it.visibility}"})
            assertTrue(table.width>0);assertTrue(table.height>0);capture(table,"studio-periodic-canvas-$language",900,550);d.dismiss()
            val palette=a.colorPalette(Color.RED,listOf(Color.RED,Color.BLUE),{},{});capture(palette,"studio-palette-$language",320,80)
            PeriodicTable.elementDialog(a,PeriodicTable.elements(a)[7]){};d=ShadowDialog.getLatestDialog();capture(d.window!!.decorView,"studio-element-$language");d.dismiss()
            capture(a.window.decorView,"studio-workspace-$language")
            a.getSharedPreferences("vura",0).edit().putBoolean("pieEnabled",true).commit()
            MainActivity::class.java.getDeclaredMethod("pieMenu",Float::class.javaPrimitiveType,Float::class.javaPrimitiveType).apply{isAccessible=true}.invoke(a,640f,400f)
            capture(a.window.decorView,"studio-pie-$language");val slots=views(a.window.decorView).filterIsInstance<WorkspaceIcon>().filter{it.contentDescription==a.tr("Assign shortcut","تعیین میان‌بر")};assertEquals(6,slots.size)
            val overlay=MainActivity::class.java.getDeclaredField("pieOverlay").apply{isAccessible=true}.get(a) as ViewGroup
            slots.forEach{slot->val r=Rect();slot.getDrawingRect(r);overlay.offsetDescendantRectToMyCoords(slot,r);assertTrue("Pie slot horizontal bounds: $r",r.centerX() in 450..850);assertTrue("Pie slot vertical bounds: $r",r.centerY() in 240..550);assertTrue(r.left>=0&&r.top>=0&&r.right<=overlay.width&&r.bottom<=overlay.height)}
            val rendered=BitmapFactory.decodeFile("build/qa/studio-pie-$language.png");try{assertEquals("Pie slots must be drawn in $language",Color.WHITE,rendered.getPixel(620,280))}finally{rendered.recycle()}
        }finally{ctl.pause().stop().destroy()}
    }
}
