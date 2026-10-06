package com.vuravision.classroom

import android.graphics.*
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowDialog

/** 1.15: placement fixes and the connected-displays preview (profiles, invites, sync rules). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="en-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Release115Test {
    private val context get()=RuntimeEnvironment.getApplication()
    @Before fun clean(){context.getSharedPreferences("vura",0).edit().clear().commit();Fonts.initialize(context)}
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun call(a:MainActivity,name:String)=MainActivity::class.java.getDeclaredMethod(name).apply{isAccessible=true}.invoke(a)
    private fun field(a:MainActivity,name:String)=MainActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.get(a)
    private fun latest()=ShadowDialog.getLatestDialog() as AlertDialog
    private fun choose(d:AlertDialog,label:String){
        val list=requireNotNull(d.listView){"Expected a menu containing $label"}
        val labels=(0 until list.adapter.count).map{list.adapter.getItem(it).toString()}
        val index=labels.indexOf(label);assertTrue("Menu must contain $label; actual items: $labels",index>=0)
        list.performItemClick(list.adapter.getView(index,null,list),index,index.toLong())
    }
    private fun layout(a:MainActivity){val root=a.window.decorView;root.measure(View.MeasureSpec.makeMeasureSpec(1400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(900,View.MeasureSpec.EXACTLY));root.layout(0,0,1400,900)}

    @Test fun pieMenuIsOnlyInSettingsNotOnTheToolbar(){
        val ctl=Robolectric.buildActivity(MainActivity::class.java).setup();val a=ctl.get()
        try{
            layout(a)
            assertTrue(views(a.window.decorView).filterIsInstance<WorkspaceIcon>().none{it.contentDescription==a.s("pie_settings")})
            call(a,"settings");val labels=latest().listView.adapter.let{l->(0 until l.count).map{l.getItem(it)}}
            assertTrue(a.s("pie_settings") in labels);latest().dismiss()
        }finally{ctl.pause().stop().destroy()}
    }

    @Test fun nestedMenusOpenCentredWithBackIconAtTheTop(){
        val ctl=Robolectric.buildActivity(MainActivity::class.java).setup();val a=ctl.get()
        try{
            call(a,"menu");choose(latest(),a.s("settings"))
            val settings=latest();choose(settings,a.s("fonts"))
            val fonts=latest()
            assertNotEquals(Gravity.BOTTOM,(fonts.window!!.attributes.gravity and Gravity.VERTICAL_GRAVITY_MASK))
            assertEquals(a.s("close"),fonts.getButton(AlertDialog.BUTTON_NEGATIVE).text.toString())
            val back=views(fonts.window!!.decorView).filterIsInstance<WorkspaceIcon>().single{it.contentDescription==a.s("menu_back")}
            val icon=IntArray(2);back.getLocationInWindow(icon);val close=IntArray(2);fonts.getButton(AlertDialog.BUTTON_NEGATIVE).getLocationInWindow(close)
            assertTrue("Back must be above the bottom buttons",icon[1]<=close[1])
            back.performClick();Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertFalse(fonts.isShowing)
            val parent=latest();assertNotSame(fonts,parent);assertTrue(parent.isShowing)
            parent.dismiss()
        }finally{ctl.pause().stop().destroy()}
    }

    @Test fun selectionActionsFloatBesideTheSelectedObject(){
        val ctl=Robolectric.buildActivity(MainActivity::class.java).setup();val a=ctl.get()
        try{
            layout(a)
            a.board.insert(Item(kind="shape",shape="rectangle",w=200f,h=120f));layout(a);a.board.onSelection();layout(a)
            val host=field(a,"selectionHost") as View
            assertEquals(View.VISIBLE,host.visibility)
            val bounds=a.board.screenBounds(a.board.chosen())
            val top=host.top+host.translationY;val bottom=top+host.height
            assertTrue("Bar must sit above or below the object: bar=$top..$bottom object=$bounds",bottom<=bounds.top+1 || top>=bounds.bottom-1)
            val centre=host.left+host.translationX+host.width/2f
            assertEquals("Bar is centred on the object",bounds.centerX(),centre,host.width/2f+1)
        }finally{ctl.pause().stop().destroy()}
    }

    @Test fun connectedDisplaysLiveInTheHiddenEngineeringMenu(){
        val ctl=Robolectric.buildActivity(MainActivity::class.java).setup();val a=ctl.get()
        try{
            call(a,"settings");val normal=latest().listView.adapter.let{l->(0 until l.count).map{l.getItem(it)}};latest().dismiss()
            assertFalse(a.s("collaboration") in normal)
            call(a,"engineering");val hidden=latest().listView.adapter.let{l->(0 until l.count).map{l.getItem(it)}}
            assertTrue(a.s("collaboration") in hidden);latest().dismiss()
        }finally{ctl.pause().stop().destroy()}
    }

    @Test fun profileIsLocalAndRoundTrips(){
        val first=CollabProfile.load(context)
        assertTrue(first.id.isNotBlank());assertEquals(first.id,CollabProfile.load(context).id)
        val photo=Bitmap.createBitmap(300,200,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.RED)}
        val encoded=CollabProfile.photoFrom(photo)
        val decoded=requireNotNull(CollabProfile.decodePhoto(encoded));assertEquals(128,decoded.width);assertEquals(128,decoded.height)
        CollabProfile.save(context,first.copy(name="  Sara  ",color=Color.BLUE,emoji="🦊",photo=encoded))
        val loaded=CollabProfile.load(context)
        assertEquals("Sara",loaded.name);assertEquals(Color.BLUE,loaded.color);assertEquals("🦊",loaded.emoji);assertTrue(loaded.photo.isNotBlank())
        CollabProfile.save(context,loaded.copy(photo=""));assertTrue(CollabProfile.load(context).photo.isBlank())
    }

    @Test fun invitesCarryAddressesCodeAndName(){
        val invite=CollabInvite(listOf("192.168.1.5","10.0.0.2"),40123,"482913","room1234","Sara & Ali")
        assertEquals(invite,CollabInvite.parse(invite.uri()))
        assertEquals(CollabInvite(listOf("192.168.1.20"),40123,"1234","",""),CollabInvite.parseAddress(" 192.168.1.20 : 40123 ","12 34"))
        assertNull(CollabInvite.parseAddress("example.com:80",""))
        assertNull(CollabInvite.parse("https://example.com/join?h=1.2.3.4&p=1"))
        assertNull(CollabInvite.parse("vura://join?h=1.2.3.4&p=99999"))
    }

    @Test fun controllingDisplayForwardsUndoToTheSharedHistory(){
        val store=Store();val calls=mutableListOf<String>()
        store.editMetadata{store.page.items.add(Item())}
        store.remoteHistory={calls.add(it)}
        assertEquals(0,store.undoCount);store.undo();store.redo()
        assertEquals(listOf("undo","redo"),calls);assertEquals(1,store.page.items.size)
        store.remoteHistory=null;store.undo();assertTrue(store.page.items.isEmpty())
    }

    @Test fun objectsSelectedBySomeoneElseCannotBeTakenOrErased(){
        val media=Media(context)
        try{
            val store=Store();val board=Board(context,store,Renderer(media));board.layout(0,0,1000,600);board.holdRecognitionEnabled=false
            val item=Item(kind="shape",x=100f,y=100f,w=100f,h=100f);store.page.items.add(item)
            board.foreignLocks=mapOf(item.id to Color.RED);board.tool="select"
            fun tap(x:Float,y:Float){listOf(0,1).forEach{action->val e=MotionEvent.obtain(100,200,action,x,y,0);board.onTouchEvent(e);e.recycle()}}
            tap(150f,150f);assertTrue(board.selected.isEmpty())
            board.tool="erase";tap(150f,150f);assertEquals(listOf(item),store.page.items)
            board.foreignLocks=emptyMap();board.tool="select";tap(150f,150f);assertEquals(setOf(item.id),board.selected)
        }finally{media.close()}
    }

    /** Host relays guest changes; each display keeps its own Undo; concurrent edits converge. */
    @Test fun syncConvergesAndKeepsUndoPerPerson(){
        val start=Lesson(title="Shared",pages=mutableListOf(Page()))
        fun copy(l:Lesson)=CollabSync.gson.fromJson(CollabSync.gson.toJson(l),Lesson::class.java)
        val host=Store(copy(start));val guest=Store(copy(start))
        val hostShadow=CollabSync.capture(host.lesson);val guestShadow=CollabSync.capture(guest.lesson)
        fun guestToHost(){
            val diff=CollabSync.diff(guest.lesson,guestShadow)?:return
            CollabSync.diff(host.lesson,hostShadow)?.let{CollabSync.apply(guest.lesson,it.body);guest.rebase{l->CollabSync.apply(l,it.body)};CollabSync.diff(guest.lesson,guestShadow)}
            CollabSync.apply(host.lesson,diff.body);host.rebase{CollabSync.apply(it,diff.body)};CollabSync.diff(host.lesson,hostShadow)
        }
        fun hostToGuest(){
            val diff=CollabSync.diff(host.lesson,hostShadow)?:return
            CollabSync.apply(guest.lesson,diff.body);guest.rebase{CollabSync.apply(it,diff.body)};CollabSync.diff(guest.lesson,guestShadow)
        }
        fun texts(s:Store)=s.page.items.map{it.text}.sorted()
        guest.editMetadata{guest.page.items.add(Item(kind="text",text="guest",points=mutableListOf(Point(1f,2f))))};guestToHost()
        host.editMetadata{host.page.items.add(Item(kind="text",text="host"))};hostToGuest()
        assertEquals(listOf("guest","host"),texts(host));assertEquals(texts(host),texts(guest))
        guest.editMetadata{guest.page.items.first{it.text=="host"}.x=40f};guestToHost()
        assertEquals(40f,host.page.items.first{it.text=="host"}.x)
        guest.undo();guestToHost()
        assertEquals("Guest Undo reverts only the guest's move",0f,host.page.items.first{it.text=="host"}.x)
        guest.undo();guestToHost()
        assertEquals(listOf("host"),texts(host));assertEquals(listOf("host"),texts(guest))
        host.page.panes[0].zoom=3f;host.changed();assertNull("View zoom is personal",CollabSync.diff(host.lesson,hostShadow))
        host.editMetadata{host.lesson.pages.add(Page(background="grid"))};hostToGuest()
        assertEquals(2,guest.lesson.pages.size);assertEquals(0,guest.lesson.current);assertEquals("grid",guest.lesson.pages[1].background)
        copy(guest.lesson).validate();copy(host.lesson).validate()
    }
}
