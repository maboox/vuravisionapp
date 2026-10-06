package com.vuravision.classroom

import android.graphics.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="en-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Studio115Test {
    private val context get()=RuntimeEnvironment.getApplication()
    @Before fun clean(){context.getSharedPreferences("vura",0).edit().clear().commit()}
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun call(a:MainActivity,name:String)=MainActivity::class.java.getDeclaredMethod(name).apply{isAccessible=true}.invoke(a)
    private fun layout(v:View){v.measure(View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));v.layout(0,0,1280,800)}
    private fun keys()=(ShadowDialog.getLatestDialog() as AlertDialog).listView.adapter.let{adapter->(0 until adapter.count).map{adapter.getItem(it).toString()}}
    @Test fun pieHasNoToolbarButtonAndRoomsRemainInEngineering(){val ctl=Robolectric.buildActivity(MainActivity::class.java).setup();val a=ctl.get()
        try{assertFalse(views(a.window.decorView).filterIsInstance<WorkspaceIcon>().any{it.contentDescription==a.s("pie_settings")});call(a,"settings");assertTrue(a.s("device_profile") in keys());assertTrue(a.s("pie_settings") in keys());assertFalse(a.s("shared_room") in keys());ShadowDialog.getLatestDialog().dismiss();call(a,"engineering");assertTrue(a.s("shared_room") in keys());ShadowDialog.getLatestDialog().dismiss()}finally{ctl.pause().stop().destroy()}}
    @Test fun contextualToolbarIsAboveObjectWhileMainActionsStayAtBottom(){val ctl=Robolectric.buildActivity(MainActivity::class.java).setup();val a=ctl.get()
        try{layout(a.window.decorView);val item=Item(kind="shape",x=450f,y=300f,w=200f,h=120f);a.store.page.items.add(item);a.board.selected.add(item.id);a.board.onSelection();layout(a.window.decorView)
            val bar=a.findViewById<View>(R.id.selection_toolbar);val board=a.board;val location=IntArray(2);bar.getLocationOnScreen(location);val boardLocation=IntArray(2);board.getLocationOnScreen(boardLocation)
            val bounds=board.selectionScreenBounds()!!;assertTrue("Selection actions should appear above the object",location[1]+bar.height<=boardLocation[1]+bounds.top)
            val menu=a.findViewById<View>(R.id.main_menu_button);menu.getLocationOnScreen(location);assertTrue("Main actions stay near the bottom",location[1]>=600)
        }finally{ctl.pause().stop().destroy()}}
    @Test fun nestedMenuUsesTopBackIconAndCenteredWindow(){val ctl=Robolectric.buildActivity(MainActivity::class.java).setup();val a=ctl.get()
        try{call(a,"settings");val parent=ShadowDialog.getLatestDialog() as AlertDialog;val list=parent.listView;val index=(0 until list.adapter.count).first{list.adapter.getItem(it).toString()==a.s("fonts")};list.performItemClick(list.adapter.getView(index,null,list),index,index.toLong())
            val d=ShadowDialog.getLatestDialog();assertNotNull(d.findViewById<View>(R.id.menu_back_button));assertEquals(Gravity.CENTER,d.window!!.attributes.gravity);assertFalse(views(d.window!!.decorView).filterIsInstance<Button>().any{it.text==a.s("menu_back")})
        }finally{ctl.pause().stop().destroy()}}
    @Test fun profileIsStableLocalAndValidated(){val a=DeviceProfiles.load(context);assertEquals(a,DeviceProfiles.load(context));val next=a.copy(name="Tablet two",color=TEAL,emoji="🌱");DeviceProfiles.save(context,next);assertEquals(next,DeviceProfiles.load(context));try{DeviceProfiles.save(context,next.copy(name=""));fail("Blank names must fail")}catch(_:IllegalArgumentException){};assertEquals(next,DeviceProfiles.load(context))}
    @Test fun readOnlySessionGuardsInkInsertionAndStoreEdits(){val media=Media(context);val store=Store();val board=Board(context,store,Renderer(media));board.layout(0,0,1000,600);board.sessionCanEdit=false;store.editAllowed={false}
        try{board.insert(Item(kind="shape"));store.editMetadata{store.page.items.add(Item(kind="shape"))};store.edit{store.lesson.title="changed"};assertTrue(store.page.items.isEmpty());assertEquals("",store.lesson.title)
            for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP)){val e=MotionEvent.obtain(100,200,action,150f,150f,0);board.onTouchEvent(e);e.recycle()};assertTrue(store.page.items.isEmpty());assertEquals(0,store.undoCount)
        }finally{media.close()}}
}
