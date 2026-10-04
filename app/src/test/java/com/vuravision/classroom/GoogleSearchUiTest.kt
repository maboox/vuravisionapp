package com.vuravision.classroom

import android.app.Activity
import android.content.Intent
import android.view.*
import android.widget.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="en-land-mdpi")
class GoogleSearchUiTest {
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    @Test fun destinationPersistsAndAmbiguityKeepsReview(){
        val context=RuntimeEnvironment.getApplication()
        val prefs=context.getSharedPreferences("vura",0);prefs.edit().clear().commit()
        assertEquals("ask",GoogleSearchSettings.load(context).destination)
        val target=GoogleSearchSettings("browser",true);target.save(context);assertEquals(target,GoogleSearchSettings.load(context))
        assertFalse(target.needsReview(listOf("water"),false))
        assertTrue(target.needsReview(listOf("water","weather"),false))
        assertTrue(target.needsReview(listOf("water"),true));assertTrue(target.needsReview(emptyList(),false))
        assertTrue(GoogleSearchSettings().needsReview(listOf("water"),false));prefs.edit().clear().commit()
    }
    @Test fun fixedDestinationReviewUsesOnlySearchAndPreservesBrowserFlags(){
        val controller=Robolectric.buildActivity(Activity::class.java).setup()
        try{
            val a=controller.get();a.setTheme(R.style.AppTheme);var query="";var adjacent=false
            GoogleSearchReview(a).show(listOf("wrong","another"),{fail("Wrong route")},{q,v->query=q;adjacent=v},GoogleSearchSettings("browser",true))
            val dialog=ShadowDialog.getLatestDialog();val all=views(dialog.window!!.decorView)
            assertFalse(all.filterIsInstance<Button>().any{it.text.toString()=="Search in floating window"})
            all.filterIsInstance<EditText>().single().setText("correct")
            all.filterIsInstance<Button>().first{it.text.toString()=="Search"}.performClick()
            assertEquals("correct",query);assertTrue(adjacent)
        }finally{controller.pause().stop().destroy()}
    }
    @Test fun fixedBrowserSearchSkipsChooserAndRetainsOriginalWriting(){
        val prefs=RuntimeEnvironment.getApplication().getSharedPreferences("vura",0);prefs.edit().clear().putString("language","en").commit()
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        try{
            val a=controller.get();GoogleSearchSettings("browser",false).save(a)
            val item=Item(kind="text",text="آب چیست؟")
            a.store.editMetadata{a.store.page.items.clear();a.store.page.items.add(item)}
            a.board.selected.add(item.id);a.board.onSelection()
            views(a.window.decorView).filterIsInstance<ActionIcon>().first{it.symbol=="search"}.performClick()
            val intent=Shadows.shadowOf(a).nextStartedActivity
            assertNotNull(intent);assertEquals(item.text,intent.data!!.getQueryParameter("q"))
            assertSame(item,a.store.page.items.single())
        }finally{controller.pause().stop().destroy();prefs.edit().clear().commit()}
    }
    @Test fun reviewWaitsForConfirmationAndUsesEditedQuestion(){
        val controller=Robolectric.buildActivity(Activity::class.java).setup()
        try{
            val a=controller.get();a.setTheme(R.style.AppTheme)
            var received:String?=null
            GoogleSearchReview(a).show(listOf("first recognition"),{received=it},{_,_->fail("Wrong route")})
            assertNull(received)
            val d=ShadowDialog.getLatestDialog();val all=views(d.window!!.decorView)
            val input=all.filterIsInstance<EditText>().single();input.setText("corrected question")
            all.filterIsInstance<Button>().first{it.text.toString()=="Search in floating window"}.performClick()
            assertEquals("corrected question",received);assertFalse(d.isShowing)
        }finally{controller.pause().stop().destroy()}
    }
    @Test fun cancelAndBlankDraftDoNotStartSearch(){
        val controller=Robolectric.buildActivity(Activity::class.java).setup()
        try{
            val a=controller.get();a.setTheme(R.style.AppTheme);var searched=false
            GoogleSearchReview(a).show(emptyList(),{searched=true},{_,_->searched=true})
            val d=ShadowDialog.getLatestDialog();val all=views(d.window!!.decorView)
            all.filterIsInstance<Button>().first{it.text.toString()=="Search in floating window"}.performClick()
            assertFalse(searched);assertTrue(d.isShowing);assertNotNull(all.filterIsInstance<EditText>().single().error)
            d.cancel();assertFalse(searched)
        }finally{controller.pause().stop().destroy()}
    }
    @Test fun externalBrowserIntentEncodesQuestionAndRequestsAdjacentOnlyWhenChosen(){
        val normal=GoogleSearchBrowser.intent("a+b & c",false)
        assertEquals(Intent.ACTION_VIEW,normal.action);assertTrue(normal.categories.contains(Intent.CATEGORY_BROWSABLE))
        assertEquals(0,normal.flags and Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        val adjacent=GoogleSearchBrowser.intent("آب چیست؟",true)
        assertTrue(adjacent.flags and Intent.FLAG_ACTIVITY_NEW_TASK!=0)
        assertTrue(adjacent.flags and Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT!=0)
        assertEquals("آب چیست؟",adjacent.data!!.getQueryParameter("q"))
    }
    @Test fun selectionSearchAlwaysReviewsWithoutChangingBoardItems(){
        val prefs=RuntimeEnvironment.getApplication().getSharedPreferences("vura",0)
        prefs.edit().clear().putString("language","en").commit()
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        try{
            val a=controller.get();val item=Item(kind="text",text="why is the sky blue?")
            a.store.editMetadata{a.store.page.items.clear();a.store.page.items.add(item)}
            a.board.selected.add(item.id);a.board.onSelection()
            val search=views(a.window.decorView).filterIsInstance<ActionIcon>().first{it.symbol=="search"}
            search.performClick()
            val dialog=ShadowDialog.getLatestDialog()
            assertEquals(item.text,views(dialog.window!!.decorView).filterIsInstance<EditText>().single().text.toString())
            assertSame(item,a.store.page.items.single());dialog.cancel()
            assertSame(item,a.store.page.items.single());assertEquals("why is the sky blue?",item.text)
        }finally{controller.pause().stop().destroy();prefs.edit().clear().commit()}
    }
}
