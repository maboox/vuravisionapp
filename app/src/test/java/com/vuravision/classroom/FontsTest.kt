package com.vuravision.classroom

import android.graphics.*
import android.text.Spanned
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.gson.Gson
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="en-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FontsTest {
    private val context get()=RuntimeEnvironment.getApplication()
    @Before fun setup(){context.getSharedPreferences("vura",0).edit().clear().commit();Fonts.initialize(context)}
    @After fun reset(){context.getSharedPreferences("vura",0).edit().clear().commit();Fonts.initialize(context)}
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()

    @Test fun mixedTextUsesIndependentFontsAndKeepsPersianJoiners(){
        val text="یادگیری\u200cمان VuraVision 123 و ۱۲۳"
        val styled=Fonts.style(text,"kahroba","rubik",bold=true) as Spanned
        assertEquals(text,styled.toString())
        val spans=styled.getSpans(0,styled.length,Fonts.RunSpan::class.java)
        assertTrue(spans.any{it.familyId=="kahroba"});assertTrue(spans.any{it.familyId=="rubik"})
        assertTrue(spans.all{it.bold})
        assertEquals("rubik",spans.first{styled.getSpanStart(it)<=text.indexOf("VuraVision") && styled.getSpanEnd(it)>text.indexOf("VuraVision")}.familyId)
    }
    @Test fun selectionsSurviveRestartAndStampedProjectsKeepTheirFonts(){
        Fonts.choose(context,"kahroba","montserrat")
        val item=Item(kind="text",text="سلام Hello",width=30f,bold=true,italic=true);TextLayout.fit(item)
        assertEquals("kahroba",item.fontFa);assertEquals("montserrat",item.fontEn)
        val restored=Gson().fromJson(Gson().toJson(Lesson(pages=mutableListOf(Page(items=mutableListOf(item))))),Lesson::class.java)
        restored.validate()
        Fonts.choose(context,"vazirmatn","rubik");Fonts.initialize(context)
        assertEquals("vazirmatn",Fonts.persianId);assertEquals("rubik",Fonts.englishId)
        assertEquals("kahroba",restored.pages[0].items[0].fontFa)
        val fresh=Item(kind="text",text="تبدیل هوشمند smart conversion");TextLayout.fit(fresh)
        assertEquals("vazirmatn",fresh.fontFa);assertEquals("rubik",fresh.fontEn)
        val old=Gson().fromJson("""{"kind":"text","text":"old text"}""",Item::class.java)
        assertNull(old.fontFa);assertEquals("system",Fonts.fa(old))
    }
    @Test fun textLayoutsAndFacesAreCachedButFontChangesInvalidateLayout(){
        Fonts.choose(context,"kahroba","rubik")
        val item=Item(kind="text",text="سلام Hello");TextLayout.fit(item)
        val layout=TextLayout.layout(item)
        assertSame(layout,TextLayout.layout(item));assertSame(Fonts.face("rubik"),Fonts.face("rubik"))
        Fonts.choose(context,"sahel","montserrat");Fonts.stamp(item,true)
        assertNotSame(layout,TextLayout.layout(item))
        Fonts.persian.plus(Fonts.english).forEach{family->assertNotNull(Fonts.face(family.id));assertNotNull(Fonts.face(family.id,true,true))}
    }
    @Test fun inputFontChangesPreserveTextAndCursorAndPreviewOverridesRemain(){
        val field=context.field("Hello فارسی");field.setSelection(3)
        Fonts.bind(field,fontFa="kahroba",fontEn="rubik")
        assertEquals("Hello فارسی",field.text.toString());assertEquals(3,field.selectionStart)
        Fonts.choose(context,"vazirmatn","montserrat");Fonts.applyTree(field)
        assertEquals(3,field.selectionStart)
        val spans=field.text!!.getSpans(0,field.length(),Fonts.RunSpan::class.java)
        assertTrue(spans.any{it.familyId=="rubik"});assertTrue(spans.any{it.familyId=="kahroba"})
        field.append(" abc");assertTrue(field.text!!.getSpans(0,field.length(),Fonts.RunSpan::class.java).any{it.familyId=="rubik"})
    }
    @Test fun fontSettingsPreviewRendersAndOptionalApplyCanBeUndone(){
        val ctl=Robolectric.buildActivity(MainActivity::class.java).setup()
        try{
            val a=ctl.get();Fonts.choose(a,"kahroba","rubik")
            val text=Item(kind="text",text="Hello",fontFa="system",fontEn="system",width=30f)
            val locked=text.copy(id=newId(),locked=true)
            a.store.editMetadata{a.store.page.items.addAll(listOf(text,locked))}
            MainActivity::class.java.getDeclaredMethod("fontSettings").apply{isAccessible=true}.invoke(a)
            val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog()
            val decor=dialog.window!!.decorView
            views(decor).forEach{it.setScrollIndicators(0)}
            decor.measure(View.MeasureSpec.makeMeasureSpec(1200,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY));decor.layout(0,0,1200,1000)
            val bitmap=Bitmap.createBitmap(1200,1000,Bitmap.Config.ARGB_8888)
            try{decor.draw(Canvas(bitmap));val file=File("build/qa/font-settings-en.png");file.parentFile!!.mkdirs();file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}finally{bitmap.recycle()}
            views(decor).filterIsInstance<CheckBox>().single().isChecked=true
            views(decor).filterIsInstance<Button>().first{it.text.toString()==a.s("apply")}.performClick()
            assertEquals("kahroba",a.store.page.items.first{it.id==text.id}.fontFa)
            assertEquals("system",a.store.page.items.first{it.id==locked.id}.fontFa)
            a.store.undo();assertEquals("system",a.store.page.items.first{it.id==text.id}.fontFa)
        }finally{ctl.pause().stop().destroy()}
    }
}
