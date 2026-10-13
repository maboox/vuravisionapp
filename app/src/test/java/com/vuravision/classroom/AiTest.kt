package com.vuravision.classroom

import android.graphics.*
import android.os.Looper
import android.view.*
import android.widget.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.util.concurrent.TimeUnit

/** The AI layer against a fake service: request formats, caching, limits, parsing, and every board action end to end. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AiTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private val sent=mutableListOf<Pair<String,String>>()
    private var reply:(String,String)->String={_,_->"{}"}
    @Before fun fake(){
        context.getSharedPreferences("vura",0).edit().clear().commit();SmartAi.clearCache();sent.clear()
        SmartAi.testKey={"test-key"};SmartAi.testTransport=AiTransport{url,_,body->sent.add(url to body);reply(url,body)}
    }
    @After fun real(){SmartAi.testKey=null;SmartAi.testTransport=null;SmartAi.clearCache()}
    private fun openAi(text:String)=JSONObject().put("choices",org.json.JSONArray().put(JSONObject().put("message",JSONObject().put("content",text)))).toString()
    private fun gemini(text:String)=JSONObject().put("candidates",org.json.JSONArray().put(JSONObject().put("content",JSONObject().put("parts",org.json.JSONArray().put(JSONObject().put("text",text)))))).toString()
    private fun idle()=org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idleFor(200,TimeUnit.MILLISECONDS)

    @Test fun geminiAndOpenAiRequestsAndReplies(){
        context.getSharedPreferences("vura",0).edit().putString("aiProvider","gemini").commit()
        reply={_,_->gemini("Hello")}
        val bitmap=Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888)
        assertEquals("Hello",SmartAi(context,{""}).ask("sys","prompt",bitmap))
        assertTrue(sent.last().first.contains("generativelanguage.googleapis.com")&&sent.last().first.endsWith(":generateContent"))
        assertTrue("image missing",sent.last().second.contains("inline_data"))
        context.getSharedPreferences("vura",0).edit().putString("aiProvider","avalai").commit()
        reply={_,_->openAi("```json\n{\"text\":\"ok\"}\n```")}
        assertEquals("ok",SmartAi(context,{""}).askJson("sys","p2",bitmap).getString("text"))
        assertTrue(sent.last().first.endsWith("/chat/completions")&&sent.last().second.contains("image_url"))
        bitmap.recycle()
    }
    @Test fun picturesFromEveryServiceFormat(){
        val png=java.io.ByteArrayOutputStream().also{Bitmap.createBitmap(8,6,Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray()
        val b64=android.util.Base64.encodeToString(png,android.util.Base64.NO_WRAP)
        val prefs=context.getSharedPreferences("vura",0)
        prefs.edit().putString("aiProvider","gemini").commit()
        reply={_,_->JSONObject().put("candidates",org.json.JSONArray().put(JSONObject().put("content",JSONObject().put("parts",org.json.JSONArray().put(JSONObject().put("text","here")).put(JSONObject().put("inlineData",JSONObject().put("mimeType","image/png").put("data",b64))))))).toString()}
        assertEquals(8,SmartAi(context,{""}).picture("a cell").width)
        prefs.edit().putString("aiProvider","openai").commit()
        reply={url,_->if(url.endsWith("/images/generations"))JSONObject().put("data",org.json.JSONArray().put(JSONObject().put("b64_json",b64))).toString() else openAi("a tree on a hill")}
        val sketch=Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888)
        assertEquals(6,SmartAi(context,{""}).picture("",sketch).height)
        assertTrue("a sketch is described first where the service cannot take it directly",sent.any{it.first.endsWith("/chat/completions")})
        assertTrue(sent.last().second.contains("a tree on a hill"))
        prefs.edit().putString("aiProvider","openrouter").commit()
        reply={_,_->openAi("").replace("\"content\":\"\"","\"content\":\"\",\"images\":[{\"image_url\":{\"url\":\"data:image/png;base64,$b64\"}}]")}
        assertEquals(8,SmartAi(context,{""}).picture("x",sketch).width);sketch.recycle()
    }
    @Test fun cachesAndCapsRequests(){
        context.getSharedPreferences("vura",0).edit().putString("aiProvider","openai").putInt("aiDailyLimit",10).commit()
        reply={_,_->openAi("same")}
        val ai=SmartAi(context,{""})
        ai.ask("s","p");ai.ask("s","p");assertEquals("cached answers must not be requested again",1,sent.size)
        repeat(9){ai.ask("s","p$it")}
        try{ai.ask("s","over");fail("daily limit not enforced")}catch(e:AiException){assertEquals("limit",e.reason)}
    }
    @Test fun noKeyIsReportedNotSent(){
        SmartAi.testKey={""}
        try{SmartAi(context,{""}).ask("s","p");fail()}catch(e:AiException){assertEquals("no_key",e.reason)}
        assertTrue(sent.isEmpty())
    }
    @Test fun parsersAcceptRealWorldReplies(){
        val check=AiTasks.parseCheck(JSONObject("""{"correct":false,"summary":"2+2 is 4","mistakes":[{"box":[10,20,40,60],"note":"should be 4"},{"box":[0.5]}]}"""))
        assertEquals(1,check.mistakes.size);assertEquals(.1f,check.mistakes[0].first.left,.001f);assertEquals(.6f,check.mistakes[0].first.bottom,.001f)
        val quiz=AiTasks.parseQuiz(JSONObject("""{"questions":[{"q":"2+2?","options":["3","4","5","6"],"answer":1},{"q":"bad","options":["a","a"],"answer":0},{"q":"x","options":["a","b"],"answer":7}]}"""))
        assertEquals(1,quiz.size);assertEquals(1,quiz[0].answer)
        val map=AiTasks.parseMindMap(JSONObject("""{"title":"Water","children":[{"title":"Cycle","children":[{"title":"Rain"},{"title":"Snow","children":[{"title":"too deep"}]}]},{"title":"Uses"}]}"""))
        assertEquals(2,map.children.size);assertTrue(map.children[0].children[1].children.isEmpty())
        assertEquals(2,AiTasks.parseSteps(JSONObject("""{"steps":["a","",  "b"],"answer":"c"}""")).steps.size)
        assertEquals("{\"a\":1}",SmartAi.jsonOf("Sure! ```json\n{\"a\":1}\n``` hope it helps"))
    }

    private fun shot(a:MainActivity,name:String){
        val m=a.resources.displayMetrics;val decor=a.window.decorView
        decor.measure(View.MeasureSpec.makeMeasureSpec(m.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(m.heightPixels,View.MeasureSpec.EXACTLY));decor.layout(0,0,m.widthPixels,m.heightPixels)
        val b=Bitmap.createBitmap(m.widthPixels,m.heightPixels,Bitmap.Config.ARGB_8888)
        try{decor.draw(Canvas(b));File("build/qa/ai-$name.png").apply{parentFile!!.mkdirs()}.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}finally{b.recycle()}
    }
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun waitFor(condition:()->Boolean){repeat(50){if(condition())return;Thread.sleep(20);idle()};assertTrue("timed out",condition())}

    /** Every AI action from the selection bar, with a fake service answering in Persian. */
    @Test @Config(qualifiers="fa-w1280dp-h720dp-land-hdpi") fun boardActionsEndToEnd(){
        context.getSharedPreferences("vura",0).edit().putString("language","fa").putString("aiProvider","openai").commit()
        reply={_,body->when{
            body.contains("mistakes")->openAi("""{"correct":false,"summary":"یک اشتباه در جمع","mistakes":[{"box":[0.5,0.2,0.9,0.8],"note":"۷ + ۵ = ۱۲"}]}""")
            body.contains("\\\"steps\\\"")->openAi("""{"steps":["دو طرف را منهای ۳ کنید","تقسیم بر ۲"],"answer":"x = 4"}""")
            body.contains("questions")->openAi("""{"questions":[{"q":"۲ + ۲ چند است؟","options":["۳","۴","۵","۶"],"answer":1},{"q":"پایتخت ایران؟","options":["تهران","شیراز","تبریز","مشهد"],"answer":0}]}""")
            body.contains("mind map")->openAi("""{"title":"آب","children":[{"title":"چرخه","children":[{"title":"باران"},{"title":"تبخیر"}]},{"title":"کاربرد","children":[{"title":"کشاورزی"}]}]}""")
            else->openAi("این یک معادلهٔ درجهٔ اول است؛ x را تنها کنید.")
        }}
        val ctl=Robolectric.buildActivity(MainActivity::class.java).setup();val a=ctl.get()
        try{
            val ink=Item(kind="ink",x=300f,y=200f,w=300f,h=120f,inkW=300f,inkH=120f,width=4f,points=mutableListOf(Point(0f,60f),Point(100f,0f),Point(200f,120f),Point(300f,60f)))
            a.store.editMetadata{a.store.page.items.add(ink)}
            fun select(){a.board.tool="select";a.board.selected.clear();a.board.selected.add(ink.id);a.board.onSelection();idle()}
            fun tap(symbol:String){views(a.findViewById(R.id.selection_toolbar)).filterIsInstance<ActionIcon>().first{it.symbol==symbol}.performClick();idle()}
            select();tap("smart");shot(a,"smart-bar")
            assertTrue("AI actions should lead when configured",views(a.findViewById(R.id.selection_toolbar)).filterIsInstance<ActionIcon>().any{it.symbol=="ai_explain"})
            val count=a.store.page.items.size
            tap("ai_explain");waitFor{a.store.page.items.size==count+1}
            assertTrue(a.store.page.items.last().text.contains("معادله"));shot(a,"explain")
            select();tap("smart");tap("ai_check");waitFor{a.store.page.items.any{it.shape=="ellipse"}}
            val circle=a.store.page.items.first{it.shape=="ellipse"};assertTrue("mistake circle should sit on the work",circle.x>=ink.x+100f&&circle.x<ink.x+ink.w)
            shot(a,"check")
            select();tap("smart");tap("ai_steps");waitFor{views(a.window.decorView).any{it is StepReveal}}
            val before=a.store.page.items.size
            views(a.window.decorView).filterIsInstance<StepReveal>().single().let{bar->views(bar).filterIsInstance<Button>().first{it.text.toString()==a.tr("Next step","گام بعد")}.performClick()}
            assertEquals(before+1,a.store.page.items.size);shot(a,"steps")
            select();tap("smart");tap("ai_mindmap");waitFor{a.store.page.items.count{it.shape=="mindnode"}==6}
            shot(a,"mindmap")
            // Nothing the AI added may cover the teacher's work or another answer (circles on the work aside).
            val added=a.store.page.items.filter{it.id!=ink.id&&it.shape!="ellipse"&&!(it.kind=="text"&&it.text.length<=2)}
            added.forEach{x->val r=contentBounds(listOf(x));assertFalse("${x.text} covers the work",RectF.intersects(r,contentBounds(listOf(ink))))
                added.filter{it!==x}.forEach{y->assertFalse("'${x.text}' overlaps '${y.text}'",RectF.intersects(r,contentBounds(listOf(y))).also{if(it){val i=RectF(r);i.intersect(contentBounds(listOf(y)));if(i.width()<2||i.height()<2)return@forEach}})}}
            select();tap("smart");tap("ai_quiz");waitFor{ShadowDialog.getLatestDialog()?.isShowing==true}
            val quiz=ShadowDialog.getLatestDialog()
            val m=a.resources.displayMetrics;val decor=quiz.window!!.decorView
            decor.measure(View.MeasureSpec.makeMeasureSpec(m.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(m.heightPixels,View.MeasureSpec.EXACTLY));decor.layout(0,0,m.widthPixels,m.heightPixels)
            views(decor).first{it.contentDescription?.toString()?.startsWith("B")==true}.performClick()
            // The cards are rebuilt on reveal: lay out again before drawing.
            decor.measure(View.MeasureSpec.makeMeasureSpec(m.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(m.heightPixels,View.MeasureSpec.EXACTLY));decor.layout(0,0,m.widthPixels,m.heightPixels)
            val b=Bitmap.createBitmap(m.widthPixels,m.heightPixels,Bitmap.Config.ARGB_8888);decor.draw(Canvas(b))
            File("build/qa/ai-quiz.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
            assertTrue(views(decor).filterIsInstance<TextView>().any{it.text.toString()==a.tr("Correct!","درست است!")})
            views(decor).filterIsInstance<Button>().first{it.text.toString()==a.tr("Save to lesson","ذخیره در درس")}.performClick()
            assertEquals("the AI quiz should be kept in the lesson",2,a.store.lesson.quizzes!!.single().questions.size)
            quiz.dismiss()
            assertTrue("only the selection should be sent, never the whole board",sent.all{it.second.length<2_000_000})
        }finally{ctl.pause().stop().destroy()}
    }

    /** A teacher writes a quiz by hand: half-filled questions are refused, a good one is kept in the lesson and survives saving. */
    @Test @Config(qualifiers="fa-w1280dp-h720dp-land-hdpi") fun quizBuilderWritesKeepsAndPlays(){
        context.getSharedPreferences("vura",0).edit().putString("language","fa").commit()
        val ctl=Robolectric.buildActivity(MainActivity::class.java).setup();val a=ctl.get()
        fun layout(d:android.app.Dialog):View{val m=a.resources.displayMetrics;val decor=d.window!!.decorView
            decor.measure(View.MeasureSpec.makeMeasureSpec(m.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(m.heightPixels,View.MeasureSpec.AT_MOST));decor.layout(0,0,decor.measuredWidth,decor.measuredHeight);return decor}
        fun capture(d:android.app.Dialog,name:String){val decor=layout(d);val b=Bitmap.createBitmap(decor.width.coerceAtLeast(1),decor.height.coerceAtLeast(1),Bitmap.Config.ARGB_8888)
            try{Canvas(b).apply{drawColor(Color.DKGRAY);decor.draw(this)};File("build/qa/ai-$name.png").apply{parentFile!!.mkdirs()}.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}finally{b.recycle()}}
        fun button(d:android.app.Dialog,text:String)=views(layout(d)).filterIsInstance<Button>().first{it.text.toString()==text}
        try{
            MainActivity::class.java.getDeclaredMethod("runShortcut",String::class.java).apply{isAccessible=true}.invoke(a,"class_quiz");idle()
            val manager=ShadowDialog.getLatestDialog();capture(manager,"quiz-manager-empty")
            button(manager,a.tr("New quiz","آزمونک جدید")).performClick();idle()
            val editor=ShadowDialog.getLatestDialog();assertNotSame(manager,editor)
            val fields=views(layout(editor)).filterIsInstance<EditText>()
            assertEquals("title plus two question cards of five fields",11,fields.size)
            fields[0].setText("جمع");fields[1].setText("۲ + ۳ = ?");fields[2].setText("۵")
            button(editor,a.tr("Save","ذخیره")).performClick();idle()
            assertTrue("a question with one answer must not be saved",editor.isShowing);assertNull(a.store.lesson.quizzes)
            fields[3].setText("۶");fields[4].setText("۴")
            capture(editor,"quiz-editor")
            button(editor,a.tr("Save","ذخیره")).performClick();idle()
            assertFalse(editor.isShowing)
            val saved=a.store.lesson.quizzes!!.single()
            assertEquals("جمع",saved.title);assertEquals(listOf("۵","۶","۴"),saved.questions.single().options);assertEquals(0,saved.questions.single().answer)
            capture(manager,"quiz-manager")
            val loaded=com.google.gson.Gson().fromJson(com.google.gson.Gson().toJson(a.store.lesson),Lesson::class.java);loaded.validate()
            assertEquals(saved,loaded.quizzes!!.single())
            button(manager,a.tr("Play","اجرا")).performClick();idle()
            val play=ShadowDialog.getLatestDialog();assertTrue(views(layout(play)).filterIsInstance<TextView>().any{it.text.toString()=="۲ + ۳ = ?"})
            play.dismiss()
            val broken=Lesson(quizzes=mutableListOf(SavedQuiz(questions=listOf(SavedQuestion("q",listOf("a"),0)))))
            assertThrows(Exception::class.java){broken.validate()}
        }finally{ctl.pause().stop().destroy();context.getSharedPreferences("vura",0).edit().clear().commit()}
    }

    /** Brainstorm notes sorted by the AI into titled columns (one Undo), and offline by color; nothing is rewritten. */
    @Test @Config(qualifiers="fa-w1280dp-h720dp-land-hdpi") fun stickyNotesGroupIntoColumns(){
        context.getSharedPreferences("vura",0).edit().putString("language","fa").putString("aiProvider","openai").commit()
        reply={_,_->openAi("""{"groups":[{"title":"میوه","notes":[1,3]},{"title":"حیوان","notes":[2,4,9]}]}""")}
        val ctl=Robolectric.buildActivity(MainActivity::class.java).setup();val a=ctl.get()
        try{
            val words=listOf("سیب","گربه","موز","سگ","هویج")
            val notes=words.mapIndexed{i,t->Item(kind="sticky",text=t,x=200f+i*70f,y=150f+(i%2)*90f,w=200f,h=120f,noteColor=if(i%2==0)0xffffe8b2.toInt() else 0xffdcfce7.toInt())}
            a.store.editMetadata{a.store.page.items.addAll(notes)}
            a.board.tool="select";a.board.selected.clear();a.board.selected.addAll(notes.map{it.id});a.board.onSelection();idle()
            fun tap(symbol:String){views(a.findViewById(R.id.selection_toolbar)).filterIsInstance<ActionIcon>().first{it.symbol==symbol}.performClick();idle()}
            tap("smart");tap("ai_group");waitFor{a.store.page.items.any{it.kind=="text"&&it.text=="میوه"}}
            assertTrue("only the notes' words are sent",sent.last().second.contains("سیب")&&!sent.last().second.contains("image_url"))
            val byText=a.store.page.items.associateBy{it.text}
            assertEquals("fruit in one column",byText["سیب"]!!.x,byText["موز"]!!.x,0.1f)
            assertEquals("animals in one column",byText["گربه"]!!.x,byText["سگ"]!!.x,0.1f)
            assertTrue("Persian columns run right to left",byText["سیب"]!!.x>byText["گربه"]!!.x)
            assertTrue("the forgotten note goes to Other",a.store.page.items.any{it.text==a.tr("Other","سایر")})
            val columns=a.store.page.items.filter{it.kind=="sticky"}.map{contentBounds(listOf(it))}
            columns.forEach{x->columns.filter{it!==x}.forEach{y->assertFalse("notes overlap after grouping",RectF.intersects(x,y))}}
            shot(a,"grouped")
            a.store.undo();assertEquals("one Undo restores the notes",200f,a.store.page.items.first{it.text=="سیب"}.x,0.1f)
            assertTrue(a.store.page.items.none{it.kind=="text"})
            val all=a.store.page.items.filter{it.kind=="sticky"}
            MainActivity::class.java.getDeclaredMethod("groupByColor",List::class.java).apply{isAccessible=true}.invoke(a,all);idle()
            val after=a.store.page.items.filter{it.kind=="sticky"}.groupBy{it.noteColor}
            after.values.forEach{same->assertEquals("one column per color",1,same.map{it.x}.distinct().size)}
            assertEquals(2,after.values.map{it.first().x}.distinct().size)
        }finally{ctl.pause().stop().destroy();context.getSharedPreferences("vura",0).edit().clear().commit()}
    }
    @Test fun groupParserKeepsEveryNoteOnce(){
        val g=AiTasks.parseGroups(JSONObject("""{"groups":[{"title":"A","notes":[1,2,2]},{"title":"B","notes":[2,7,0]},{"title":"C","notes":[]}]}"""),4)
        assertEquals(listOf(0,1),g[0].notes);assertEquals("B had only duplicates and bad numbers",2,g.size);assertEquals(listOf(2,3),g[1].notes);assertEquals("",g[1].title)
    }
}
