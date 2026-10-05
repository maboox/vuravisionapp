package com.vuravision.classroom

import android.graphics.*
import android.view.*
import android.widget.*
import java.io.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="en-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Version14Test {
    private val ctx get()=RuntimeEnvironment.getApplication()
    @Before fun reset(){ctx.getSharedPreferences("vura",0).edit().clear().commit()}
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun call(a:MainActivity,name:String){MainActivity::class.java.getDeclaredMethod(name).apply{isAccessible=true}.invoke(a)}
    private fun capture(a:MainActivity,name:String){
        val v=a.findViewById<View>(android.R.id.content)
        v.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));v.layout(0,0,1920,1080)
        val bitmap=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888);v.draw(Canvas(bitmap))
        val f=File("build/qa/$name.png");f.parentFile!!.mkdirs();f.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @Test fun iconWorkspaceCanHideAndRestoreAndShareIsPublic(){
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
        try{
            capture(a,"workspace-v14")
            val icons=views(a.window.decorView).filterIsInstance<WorkspaceIcon>()
            assertTrue(icons.any{it.contentDescription==a.s("share")})
            assertTrue(icons.all{!it.contentDescription.isNullOrBlank()})
            val focus=icons.single{it.contentDescription.toString().contains("Hide / show")}
            focus.performClick();assertFalse(a.findViewById<View>(R.id.main_menu_button).isShown)
            assertTrue(focus.isShown);focus.performClick();assertTrue(a.findViewById<View>(R.id.main_menu_button).isShown)
        }finally{c.pause().stop().destroy()}
    }
    @Test fun layersAreInCanvasAndOpacityIsUndoable(){
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
        try{
            call(a,"layers");capture(a,"layers-v14")
            assertTrue(views(a.window.decorView).filterIsInstance<TextView>().any{it.text=="Layers"})
            views(a.window.decorView).filterIsInstance<Button>().single{it.text=="Add layer"}.performClick()
            assertEquals(2,a.store.page.layers.size)
            val selected=a.store.page.layers.last();a.store.edit{selected.opacity=.4f;selected.locked=true;selected.visible=false}
            a.store.undo();assertEquals(1f,a.store.page.layers.last().opacity,0f);assertFalse(a.store.page.layers.last().locked)
        }finally{c.pause().stop().destroy()}
    }
    @Test fun paletteRetainsRealColorsAndNoDuplicateModeSwitch(){
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
        try{
            call(a,"penSettings");val d=ShadowDialog.getLatestDialog();val all=views(d.window!!.decorView)
            assertFalse(all.filterIsInstance<Switch>().any{it.text=="Two pen tips"})
            val colors=all.filter{it.contentDescription?.startsWith("#")==true}
            assertEquals(10,colors.size)
            colors.forEach{cell->val w=a.dp(46);val h=a.dp(48);cell.layout(0,0,w,h);val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);cell.draw(Canvas(bitmap));assertEquals(Color.parseColor(cell.contentDescription.toString()),bitmap.getPixel(w/2,h/2));bitmap.recycle()}
            d.dismiss()
        }finally{c.pause().stop().destroy()}
    }
    @Test fun dashLayersAndSplitSurviveArchiveAndExportRenders(){
        val media=Media(ctx)
        try{
            val doc=Lesson();val page=doc.pages[0];page.panes.add(Pane(background=Color.YELLOW))
            page.layers.add(Layer(id="top",name="Top",opacity=.6f,locked=true))
            page.items.add(Item(shape="dashed",pane=1,layerId="top",dashLength=18f,dashGap=7f,w=200f,h=50f,inkW=200f,inkH=50f,points=mutableListOf(Point(0f,0f),Point(200f,50f))))
            val files=LessonFiles(media);val file=File(ctx.cacheDir,"v14-roundtrip.vura");files.atomic(doc,file);files.verify(file)
            val restored=file.inputStream().use{files.read(it)}
            val item=restored.pages[0].items.single();assertEquals(18f,item.dashLength,0f);assertEquals(7f,item.dashGap,0f)
            assertEquals(1,item.pane);assertEquals(.6f,restored.pages[0].layers.last().opacity,0f);assertTrue(restored.pages[0].layers.last().locked)
            val b=Bitmap.createBitmap(1200,800,Bitmap.Config.ARGB_8888);Renderer(media).page(Canvas(b),restored.pages[0],1200,800,true);assertTrue(b.getPixel(900,700)!=Color.TRANSPARENT);b.recycle()
        }finally{media.close()}
    }
    @Test fun conversionPreservesWritingAndUndoRemovesOnlyResult(){
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
        try{
            val original=Item(kind="text",text="12 inch to cm");a.board.insert(original)
            MainActivity::class.java.getDeclaredMethod("applySmart",List::class.java,String::class.java,String::class.java).apply{isAccessible=true}.invoke(a,listOf(original),original.text,"convert")
            assertEquals(2,a.store.page.items.size);assertEquals("12 in = 30.48 cm",a.store.page.items.last().text)
            a.store.undo();assertEquals(original.id,a.store.page.items.single().id)
        }finally{c.pause().stop().destroy()}
    }
    @Test fun refusedConnectionHasActionableMessage(){
        val text=Recognition().failure(ctx,java.net.ConnectException("ECONNREFUSED dl.google.com"))
        assertTrue(text.contains("network"));assertTrue(text.contains("Offline"));assertFalse(text.contains("404"))
    }
}
