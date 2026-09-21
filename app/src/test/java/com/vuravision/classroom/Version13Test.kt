package com.vuravision.classroom
import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Looper
import android.view.*
import java.io.*
import java.util.concurrent.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import kotlin.math.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="en-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Version13Test {
 private val ctx get()=RuntimeEnvironment.getApplication()
 @Before fun clean(){ctx.getSharedPreferences("vura",0).edit().clear().commit()}
 private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
 @Test fun documentPickerResultWritesReadableArchive(){
  val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val a=controller.get()
  try{
   val file=File(a.cacheDir,"export-test.vura");LessonFiles(a.media).atomic(Lesson(title="Saved lesson"),file)
   MainActivity::class.java.getDeclaredField("pendingExport").apply{isAccessible=true}.set(a,file)
   val output=ByteArrayOutputStream();val uri=Uri.parse("content://test/export.vura")
   Shadows.shadowOf(a.contentResolver).registerOutputStream(uri,output)
   MainActivity::class.java.getDeclaredMethod("onActivityResult",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,Intent::class.java).apply{isAccessible=true}.invoke(a,104,Activity.RESULT_OK,Intent().setData(uri))
   val worker=MainActivity::class.java.getDeclaredField("worker").apply{isAccessible=true}.get(a) as ExecutorService
   worker.submit{}.get(10,TimeUnit.SECONDS);Shadows.shadowOf(Looper.getMainLooper()).idle()
   assertTrue(output.size()>0);assertEquals("Saved lesson",LessonFiles(a.media).read(output.toByteArray().inputStream()).title)
  }finally{controller.pause().stop().destroy()}
 }
 @Test fun splitSettingsAndMindMapPersistAndUndoIndependently(){
  val media=Media(ctx);try{val store=Store();val board=Board(ctx,store,Renderer(media));board.split(4)
   store.edit{store.page.panes[2].color=Color.RED;store.page.panes[2].background=Color.YELLOW;store.page.items.add(Item(kind="sticky",shape="mindnode",pane=2,text="Child",parentNode="root"))}
   val out=ByteArrayOutputStream();LessonFiles(media).write(store.lesson,out);val saved=LessonFiles(media).read(out.toByteArray().inputStream());assertEquals(Color.YELLOW,saved.pages[0].panes[2].background);assertEquals(2,saved.pages[0].items.single().pane)
   store.undo();assertEquals(4,store.page.panes.size);assertNotEquals(Color.YELLOW,store.page.panes[2].background);store.redo();assertEquals(Color.YELLOW,store.page.panes[2].background)
  }finally{media.close()}
 }
 @Test fun openCircleIsRecognizedWithoutBecomingSquare(){
  val points=(0..60).map{i->val a=i*PI*1.85/60;Point((100+100*cos(a)).toFloat(),(100+100*sin(a)).toFloat())}.toMutableList()
  val o=Item(w=200f,h=200f,inkW=200f,inkH=200f,points=points)
  assertEquals("circle",ShapeRecognition.convert(o)?.shape)
 }
 @Test fun openLassoClosesAndLineGestureSelectsBounds(){
  val inside=Item(kind="text",x=40f,y=40f,w=20f,h=20f);val outside=inside.copy(id=newId(),x=200f)
  val path=listOf(PointF(0f,0f),PointF(100f,0f),PointF(100f,100f),PointF(0f,100f),PointF(0f,15f))
  assertEquals(listOf(inside),SmartSelection.selectGesture(path,RectF(0f,0f,100f,100f),listOf(inside,outside)))
  assertEquals(listOf(inside),SmartSelection.selectGesture(listOf(PointF(0f,0f),PointF(100f,100f)),RectF(0f,0f,100f,100f),listOf(inside,outside)))
 }
 @Test fun pdfPagesAdvanceRepeatedlyInSelectionBar(){
  val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val a=controller.get()
  try{a.board.insert(Item(kind="pdf",pageCount=8))
   repeat(5){views(a.window.decorView).filterIsInstance<ActionIcon>().single{it.symbol=="next"}.performClick()}
   assertEquals(5,a.board.chosen().single().pdfPage)
  }finally{controller.pause().stop().destroy()}
 }
 @Test fun smartTextReplacesAndMathPreservesOriginal(){
  val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val a=controller.get()
  try{val o=Item(kind="text",text="2+3",color=Color.BLUE);a.board.insert(o)
   val method=MainActivity::class.java.getDeclaredMethod("applySmart",List::class.java,String::class.java,String::class.java).apply{isAccessible=true}
   method.invoke(a,listOf(o),"2+3","formula");assertTrue(a.store.page.items.contains(o));assertEquals("= 5",a.store.page.items.last().text)
   method.invoke(a,listOf(o),"Hello","text");assertFalse(a.store.page.items.contains(o));assertEquals(Color.BLUE,a.store.page.items.last().color)
   a.store.undo();assertTrue(a.store.page.items.any{it.id==o.id})
  }finally{controller.pause().stop().destroy()}
 }
}
