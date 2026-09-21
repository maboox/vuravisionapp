package com.vuravision.classroom
import android.app.Dialog
import android.graphics.*
import android.view.*
import android.widget.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowDialog
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="en-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UpgradeTest {
 private val context get()=RuntimeEnvironment.getApplication()
 @Before fun clean(){context.getSharedPreferences("vura",0).edit().clear().commit()}
 @Test fun numericalGameLabelsNeverResolveResourceIds(){listOf("1","19","213123","2 + 7 = ?","3.000 s").forEach{assertEquals(it,context.s(it))}}
 @Test fun everyGameStartsAndRenders(){
  assertEquals(73,Games.keys.size)
  Games.keys.forEach{key->Games.open(context,key);val d=ShadowDialog.getLatestDialog();if(key in NativeGames.keys)views(d.window!!.decorView).filterIsInstance<ArcadeView>().single().startRound()else views(d.window!!.decorView).filterIsInstance<Button>().first{it.text.toString()==context.s("start")}.performClick();capture(d.window!!.decorView,if(key=="arc_math")"game-math"else null);d.dismiss()
   if(key in NativeGames.keys)return@forEach
   val g=GameEngine(key,Random(17));g.next(1000);if(key!="tictac")repeat(10){g.answer(0,g.correct,g.go+100);g.answer(1,g.correct,g.go+200);g.tick(1000000);g.next(2000000)}
  }
 }
 @Test fun allQuizzesHaveUniqueValidAnswers(){QuizCatalog.keys.forEach{key->repeat(200){seed->val q=QuizCatalog.question(key,Random(seed));assertTrue(q.correct in q.options.indices);assertEquals(q.options.size,q.options.distinct().size)}}}
 @Test fun tenPointersCommitIndependentStrokesWithoutSceneReplay(){
  val media=Media(context)
  try{val store=Store();val board=Board(context,store,Renderer(media));board.layout(0,0,1920,1080);store.changed={if(!board.isCommitting)board.sceneChanged()};val b=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888);val c=Canvas(b);board.draw(c)
   fun event(action:Int,n:Int,frame:Int){val props=Array(n){i->MotionEvent.PointerProperties().apply{id=i;toolType=MotionEvent.TOOL_TYPE_FINGER}};val coords=Array(n){i->MotionEvent.PointerCoords().apply{x=50f+i*160+frame*.1f;y=200f+frame*.2f;pressure=1f;size=.01f}};val e=MotionEvent.obtain(1000,1000L+frame,action,n,props,coords,0,0,1f,1f,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);board.onTouchEvent(e);e.recycle()}
   event(MotionEvent.ACTION_DOWN,1,0);for(i in 1..9)event(MotionEvent.ACTION_POINTER_DOWN or(i shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),i+1,i)
   repeat(500){event(MotionEvent.ACTION_MOVE,10,it+10);if(it%5==0)board.draw(c)};assertEquals(1,board.cacheRebuilds)
   for(i in 9 downTo 1)event(MotionEvent.ACTION_POINTER_UP or(i shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),i+1,520+i)
   event(MotionEvent.ACTION_UP,1,540);board.draw(c);assertEquals(10,store.page.items.size);assertTrue(store.page.items.all{it.points.size>=501});assertEquals(1,board.cacheRebuilds)
   store.undo();assertTrue(store.page.items.isEmpty());store.redo();assertEquals(10,store.page.items.size);save(b,"ten-pointer-ink");b.recycle()
  }finally{media.close()}
 }
 @Test fun calibratedWidthAndSingleTouchPolicy(){val p=TouchProfile(true,10f,50f,false,false,12f);assertEquals(3f,p.width(1,5f,3f));assertEquals(12f,p.width(1,20f,3f));assertEquals("reject",p.action(1,70f))}
 @Test fun sharingServesLandingAndActualPdfAndRejectsBadToken(){
  val f=File(context.cacheDir,"network-test.pdf");f.writeText("%PDF-1.4 test");val server=Sharing(f,"application/pdf");server.start(5000,false)
  try{fun request(path:String):Pair<Int,String>{val con=URL("http://127.0.0.1:${server.listeningPort}$path").openConnection()as HttpURLConnection;con.connectTimeout=3000;con.readTimeout=3000;return try{val code=con.responseCode;code to if(code==200)con.inputStream.bufferedReader().readText()else""}finally{con.disconnect()}}
   val landing=request("/${server.token}");assertEquals(200,landing.first);assertTrue(landing.second.contains("Download PDF"));val pdf=request("/${server.token}/file");assertEquals(200,pdf.first);assertTrue(pdf.second.startsWith("%PDF"));assertEquals(1,server.downloads);assertEquals(404,request("/bad").first)
  }finally{server.stop()}
 }
 @Test fun allShapesAndLabsRenderAtControlExtremes(){val media=Media(context);try{val r=Renderer(media);val b=Bitmap.createBitmap(1200,700,Bitmap.Config.ARGB_8888);Shapes.keys.forEach{r.draw(Canvas(b),Item(kind="shape",shape=it))};assertEquals(24,Shapes.keys.size);assertEquals(73,Labs.keys.size);Labs.keys.forEach{key->val controls=Labs.controls(key);listOf(controls.map{it.min},controls.map{it.value},controls.map{it.max}).forEach{values->LabView(context,key,values.toFloatArray()).snapshot().recycle()}};b.recycle()}finally{media.close()}}
 @Test fun scientificReferenceCases(){assertEquals(1.2,Science.current(12.0,10.0),1e-9);assertEquals(25.0,Science.kinetic(2.0,5.0),1e-9);assertEquals(9.81,Science.buoyancy(1000.0,1.0),1e-9);assertEquals(15.0,Science.lens(10.0,30.0),1e-9);assertEquals(-10.0,Science.lens(10.0,5.0),1e-9);assertTrue(Science.lens(10.0,10.0).isInfinite());assertEquals(101.4,Science.gasPressure(1.0,273.15,22.4),.1);assertEquals(1.0/3,Science.dilution(1.0,50.0,150.0),1e-9)}
 @Test fun boardAndFloatingToolsScreenshot(){
  val ctl=Robolectric.buildActivity(MainActivity::class.java).setup();val a=ctl.get();val root=a.findViewById<View>(android.R.id.content)
  a.store.lesson.title="VuraVision · Explore together";a.store.edit{a.store.page.items.add(Item(kind="text",text="Learning starts with a question.",x=50f,y=40f,w=780f,h=75f,width=36f,color=NAVY));a.store.page.items.add(Item(kind="graph",text="sin(x); cos(x)",x=50f,y=160f,w=530f,h=270f));a.store.page.items.add(Item(kind="shape",shape="hexagon",x=690f,y=190f,w=160f,h=150f,color=TEAL));a.store.page.items.add(Item(kind="text",text="Predict. Draw. Discover.",x=620f,y=365f,w=370f,h=60f,width=23f,color=TEAL))}
  capture(root,"workspace-v1",2400,1400)
  views(root).filterIsInstance<Button>().first{it.text.toString().endsWith(a.s("tools"))}.performClick();val d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();d.listView.performItemClick(null,0,0)
  capture(root,"workspace-floating-timer",2400,1400);ctl.pause().stop().destroy()
 }
 @Test fun penProfilesPagesAndShapeGalleryAreReachable(){
  val ctl=Robolectric.buildActivity(MainActivity::class.java).setup();val a=ctl.get();a.board.tool="pen";a.board.inkColor=Color.RED;a.board.inkWidth=3f;a.board.tool="highlight";a.board.inkColor=Color.YELLOW;a.board.inkWidth=8f;a.board.tool="pen";assertEquals(Color.RED,a.board.inkColor);assertEquals(3f,a.board.inkWidth);a.board.tool="highlight";assertEquals(Color.YELLOW,a.board.inkColor);assertEquals(8f,a.board.inkWidth)
  views(a.window.decorView).filterIsInstance<Button>().first{it.text.contains(a.s("add_page"))}.performClick();assertEquals(2,a.store.lesson.pages.size);assertEquals(1,a.store.lesson.current)
  views(a.window.decorView).filterIsInstance<Button>().first{it.contentDescription==a.s("previous")}.performClick();assertEquals(0,a.store.lesson.current)
  views(a.window.decorView).filterIsInstance<Button>().first{it.text.toString().endsWith(a.s("shape"))}.performClick();val gallery=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertEquals(24,views(gallery.window!!.decorView).filterIsInstance<TextView>().count{label->Shapes.keys.any{label.text==a.s(it)}});capture(gallery.window!!.decorView,"shape-gallery");gallery.dismiss();ctl.pause().stop().destroy()
 }
 @Test @Config(qualifiers="fa-port-xhdpi") fun compactPersianScreenKeepsMenuReachable(){
  context.getSharedPreferences("vura",0).edit().putString("language","fa").commit()
  val ctl=Robolectric.buildActivity(MainActivity::class.java).setup()
  try {
   val a=ctl.get()
   val root=a.findViewById<View>(android.R.id.content)
   capture(root,"compact-fa",840,1500)
   // Identify the navigation menu independently of icons and translated labels.
   val menus=views(root).filterIsInstance<Button>().filter{it.id==R.id.main_menu_button}
   assertEquals("Expected one main menu button",1,menus.size)
   val menu=menus.single()
   assertEquals(a.s("main_menu"),menu.contentDescription)
   val rect=Rect()
   assertTrue("Menu must remain visible in compact Persian layout",menu.getGlobalVisibleRect(rect))
   assertTrue(rect.right<=840)
  } finally {ctl.pause().stop().destroy()}
 }
 @Test fun labAndGameCatalogScreensHaveDescriptionsAndPreviews(){Labs.show(context){it.recycle()};var d=ShadowDialog.getLatestDialog();capture(d.window!!.decorView,"labs-catalog",1920,1080);d.dismiss();Games.show(context);d=ShadowDialog.getLatestDialog();capture(d.window!!.decorView,"games-catalog",1920,1080);d.dismiss()}
 private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
 private fun capture(v:View,name:String?,w:Int=1600,h:Int=900){views(v).forEach{it.setScrollIndicators(0)};v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);val b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));if(name!=null)save(b,name);b.recycle()}
 private fun save(b:Bitmap,name:String){val f=File("build/qa/$name.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}
}
