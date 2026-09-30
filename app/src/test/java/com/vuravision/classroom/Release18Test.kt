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
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="en-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Release18Test {
    private val ctx get()=RuntimeEnvironment.getApplication()
    @Before fun reset(){ctx.getSharedPreferences("vura",0).edit().clear().commit()}
    private fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{all(v.getChildAt(it))}else emptyList()
    private fun call(a:MainActivity,name:String){MainActivity::class.java.getDeclaredMethod(name).apply{isAccessible=true}.invoke(a)}
    private fun capture(v:View,name:String,w:Int=1600,h:Int=900){
        all(v).forEach{it.setScrollIndicators(0)}
        v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h)
        val b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);v.draw(Canvas(b))
        val f=File("build/qa/$name.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
    }
    @Test fun resizePreservesEveryStartingCornerAndDoesNotJump(){
        val base=Item(kind="shape",shape="rectangle",x=100f,y=150f,w=200f,h=120f)
        listOf(100f to 150f,300f to 150f,100f to 270f,300f to 270f).forEach{(x,y)->
            val target=base.deepCopy()
            HeldShapeResize.apply(target,base,x,y,x,y,x,y)
            assertEquals(base.x,target.x,0f);assertEquals(base.y,target.y,0f)
            val dx=if(x==100f)50f else -50f;val dy=if(y==150f)40f else -40f
            HeldShapeResize.apply(target,base,x,y,x,y,x+dx,y+dy)
            assertEquals(250f,target.w,.001f);assertEquals(160f,target.h,.001f)
            assertEquals(x,if(x==100f)target.x else target.x+target.w,.001f)
            assertEquals(y,if(y==150f)target.y else target.y+target.h,.001f)
        }
    }
    @Test fun midpointAndCrossingKeepPivotAndUniformAspect(){
        val base=Item(kind="shape",shape="square",x=100f,y=100f,w=200f,h=200f)
        val target=base.deepCopy()
        HeldShapeResize.apply(target,base,100f,200f,100f,200f,150f,225f)
        assertEquals(target.w,target.h,0f);assertEquals(100f,target.x,0f)
        assertEquals(200f,target.y+target.h/2,.001f)
        HeldShapeResize.apply(target,base,300f,300f,300f,300f,600f,600f)
        assertEquals(300f,target.x,.001f);assertEquals(300f,target.y,.001f)
        assertTrue(target.w.isFinite() && target.w>=4)
    }
    @Test fun actualHoldGestureAnchorsBottomRightAndUndoWorks(){
        val media=Media(ctx);val store=Store();val board=Board(ctx,store,Renderer(media))
        val host=Robolectric.buildActivity(android.app.Activity::class.java).setup();host.get().setContentView(board);board.layout(0,0,1000,700)
        try{
            fun event(action:Int,x:Float,y:Float){val e=MotionEvent.obtain(0,android.os.SystemClock.uptimeMillis(),action,x,y,0);board.onTouchEvent(e);e.recycle()}
            event(MotionEvent.ACTION_DOWN,300f,300f)
            listOf(100f to 300f,100f to 150f,300f to 150f,300f to 300f).forEach{event(MotionEvent.ACTION_MOVE,it.first,it.second)}
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(1100,TimeUnit.MILLISECONDS)
            event(MotionEvent.ACTION_MOVE,250f,250f);event(MotionEvent.ACTION_UP,250f,250f)
            val item=store.page.items.single();assertEquals("rectangle",item.shape)
            assertEquals(300f,item.x+item.w,.01f);assertEquals(300f,item.y+item.h,.01f)
            assertEquals(250f,item.w,.01f);assertEquals(200f,item.h,.01f)
            store.undo();assertTrue(store.page.items.isEmpty())
        }finally{host.pause().stop().destroy();media.close()}
    }
    @Test fun palmSwitchIsPublicAndPersists(){
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val a=controller.get()
        try{
            call(a,"eraserSettings");val d=ShadowDialog.getLatestDialog()
            val toggle=all(d.window!!.decorView).filterIsInstance<Switch>().single{it.text=="Use palm as a temporary eraser"}
            assertFalse(toggle.isChecked);toggle.isChecked=true
            assertTrue(a.board.profile.palmErase)
            assertTrue(ctx.getSharedPreferences("vura",0).getBoolean("palmErase",false))
            assertEquals("erase",a.board.profile.action(1,70f))
            capture(d.window!!.decorView,"eraser-en-v18")
            d.dismiss();a.board.eraserMode="area"
            a.store.page.items.add(Item(x=50f,y=50f,w=200f,h=1f,inkW=200f,inkH=1f,points=mutableListOf(Point(0f,0f),Point(200f,0f))))
            a.board.layout(0,0,1000,700)
            fun palm(action:Int){
                val props=arrayOf(MotionEvent.PointerProperties().apply{id=0;toolType=MotionEvent.TOOL_TYPE_FINGER})
                val coords=arrayOf(MotionEvent.PointerCoords().apply{x=150f;y=50f;touchMajor=70f;touchMinor=60f;pressure=1f})
                val e=MotionEvent.obtain(0,1,action,1,props,coords,0,0,1f,1f,0,0,InputDevice.SOURCE_TOUCHSCREEN,0)
                a.board.onTouchEvent(e);e.recycle()
            }
            palm(MotionEvent.ACTION_DOWN);palm(MotionEvent.ACTION_UP)
            assertTrue(a.store.page.items.single().cuts.isNotEmpty())
        }finally{controller.pause().stop().destroy()}
    }
    @Test fun guideIsCompleteSearchableAndBilingual(){
        val media=Media(ctx)
        try{
            val topics=UserGuide.topics(ctx);assertEquals(24,topics.size);assertEquals(24,topics.map{it.id}.distinct().size)
            assertTrue(topics.all{it.stepsEn.size==it.stepsFa.size && it.stepsEn.size>=3 && it.titleFa.isNotBlank() && it.tipEn.isNotBlank()})
            UserGuide.show(ctx,media){}
            val d=ShadowDialog.getLatestDialog()
            fun texts()=all(d.window!!.decorView).filterIsInstance<TextView>()
            assertTrue(texts().any{it.text=="Meet your board"})
            texts().filterIsInstance<Button>().single{it.text=="Next"}.performClick()
            assertTrue(texts().any{it.text=="Pen, color and highlighter"})
            texts().filterIsInstance<Button>().single{it.text=="فارسی / EN"}.performClick()
            assertTrue(texts().any{it.text=="قلم، رنگ و هایلایتر"})
            val search=texts().filterIsInstance<EditText>().single();search.setText("کف دست")
            assertTrue(texts().filterIsInstance<Button>().any{it.text.toString().contains("پاک‌کردن با کف دست")})
            texts().filterIsInstance<Button>().first{it.text.toString().contains("پاک‌کردن با کف دست")}.performClick()
            capture(d.window!!.decorView,"guide-fa-v18");d.dismiss()
            topics.forEach{capture(GuideFigure(ctx,media,it.id,false),"guide-${it.id}-v18",1200,560)}
        }finally{media.close()}
    }
    @Test fun artworkIsRealInkRoundTripsAndRenders(){
        val media=Media(ctx)
        try{
            listOf("mona-vura","aurora").forEach{key->
                val art=Artwork.load(ctx,key);assertTrue(art.strokes.size>90)
                val page=Page(background="plain",items=art.strokes.map{Artwork.item(it)}.toMutableList())
                assertTrue(page.items.all{it.kind=="ink" && it.asset.isBlank()})
                val doc=Lesson(pages=mutableListOf(page));doc.validate()
                val file=File(ctx.cacheDir,"$key.vura");LessonFiles(media).atomic(doc,file)
                val restored=file.inputStream().use{LessonFiles(media).read(it)}
                assertEquals(page.items.size,restored.pages.single().items.size)
                val b=Bitmap.createBitmap(1200,1600,Bitmap.Config.ARGB_8888);val canvas=Canvas(b);canvas.drawColor(Color.WHITE);canvas.scale(2f,2f)
                page.items.forEach{Renderer(media).draw(canvas,it,true)}
                assertNotEquals(Color.WHITE,b.getPixel(600,600))
                val out=File("build/qa/$key-native-v18.png");out.parentFile!!.mkdirs();out.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
            }
        }finally{media.close()}
    }
    @Test fun artPlaybackRetainsPreviousPageAndStopsOnUndo(){
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val a=controller.get()
        try{
            a.board.layout(0,0,1000,700);a.board.split(4)
            listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP).forEach{action->
                val e=MotionEvent.obtain(0,1,action,800f,450f,0);a.board.onTouchEvent(e);e.recycle()
            }
            assertEquals(3,a.board.activePane)
            val original=a.store.page
            val player=ArtPlayer(a,a.board,a.store,Artwork.load(a,"aurora"));player.start()
            assertEquals(0,a.board.activePane)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(200,TimeUnit.MILLISECONDS)
            assertEquals(2,a.store.lesson.pages.size);assertFalse(a.board.isEnabled)
            a.store.undo()
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(32,TimeUnit.MILLISECONDS)
            assertEquals(1,a.store.lesson.pages.size);assertTrue(a.board.isEnabled)
            assertEquals(original.id,a.store.page.id);player.stop()
        }finally{controller.pause().stop().destroy()}
    }
    @Test fun captureCurrentWorkspaceInBothLanguages(){
        listOf("en","fa").forEach{lang->
            ctx.getSharedPreferences("vura",0).edit().clear().putString("language",lang).commit()
            val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val a=controller.get()
            try{
                a.store.edit{
                    a.store.page.items.add(Item(kind="shape",shape="circle",x=400f,y=150f,w=180f,h=180f,color=TEAL,width=4f))
                    a.store.page.items.add(Item(kind="shape",shape="rectangle",x=100f,y=150f,w=200f,h=150f,color=ORANGE,width=4f))
                    a.store.page.items.add(Item(kind="text",text=if(lang=="fa")"به تختهٔ خود خوش آمدید"else"Welcome to your board",x=100f,y=30f,w=600f,h=80f,width=28f))
                }
                capture(a.window.decorView,"workspace-$lang-v18")
                call(a,"eraserSettings");val d=ShadowDialog.getLatestDialog();capture(d.window!!.decorView,"eraser-$lang-v18");d.dismiss()
            }finally{controller.pause().stop().destroy()}
        }
    }
}
