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
class VoiceUiTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun capture(view:View,name:String,w:Int,h:Int){
        views(view).forEach{it.setScrollIndicators(0)}
        view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h)
        val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        try{view.draw(Canvas(bitmap));val file=File("build/qa/$name.png");file.parentFile!!.mkdirs();file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}finally{bitmap.recycle()}
    }
    @Test fun englishVoiceControlsRenderAndRemainVisibleInFocusMode(){render("en")}
    @Test fun persianVoiceControlsRenderAndRemainVisibleInFocusMode(){render("fa")}
    @Test fun keySettingsAcceptLongPastedCredentialsBeforeSchedulingEncryption(){
        for(language in listOf("en","fa")){
            context.getSharedPreferences("vura",0).edit().clear().putString("language",language).commit()
            val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
            try{
                val a=controller.get();var scheduled=false;var changed=false
                VoiceSettingsUi(a).connection(VoiceKeyStore(a,"voice_ui_accept_test"),java.util.concurrent.Executor{scheduled=true}){changed=true}
                val dialog=ShadowDialog.getLatestDialog();val controls=views(dialog.window!!.decorView)
                val input=controls.filterIsInstance<EditText>().single()
                input.setText("\u200F\"new-format:"+"abc-_.+=/".repeat(40)+"\"\u200E")
                controls.filterIsInstance<Button>().first{it.text.toString() in listOf("Save key","ذخیرهٔ کلید")}.performClick()
                assertTrue("$language must schedule encryption",scheduled);assertTrue(changed)
                assertNull(input.error)
                // getTextDirection is a resolved view flag, not the displayed paragraph direction.
                // Measure/render first, then check the actual masked text layout in both locales.
                capture(input,"voice-key-direction-$language",640,80)
                assertTrue(input.transformationMethod is android.text.method.PasswordTransformationMethod)
                val displayed=input.transformationMethod.getTransformation(input.text,input).toString()
                assertFalse(displayed.contains("new-format"));assertNotEquals(input.text.toString(),displayed)
                assertNotNull(input.layout)
                assertEquals(android.text.Layout.DIR_LEFT_TO_RIGHT,input.layout!!.getParagraphDirection(0))
                assertEquals(Gravity.LEFT,Gravity.getAbsoluteGravity(input.gravity,input.layoutDirection) and Gravity.HORIZONTAL_GRAVITY_MASK)
                dialog.dismiss()
            }finally{controller.pause().stop().destroy();context.getSharedPreferences("vura",0).edit().clear().commit()}
        }
    }
    @Test fun keySettingsRejectLinksBeforeSchedulingEncryption(){
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        try{
            val a=controller.get();var scheduled=false
            VoiceSettingsUi(a).connection(VoiceKeyStore(a,"voice_ui_reject_test"),java.util.concurrent.Executor{scheduled=true}){}
            val dialog=ShadowDialog.getLatestDialog();val controls=views(dialog.window!!.decorView)
            val input=controls.filterIsInstance<EditText>().single();input.setText("https://aistudio.google.com/apikey")
            controls.filterIsInstance<Button>().first{it.text.toString() in listOf("Save key","ذخیرهٔ کلید")}.performClick()
            assertFalse(scheduled);assertNotNull(input.error);dialog.dismiss()
        }finally{controller.pause().stop().destroy()}
    }
    private fun render(language:String){
        context.getSharedPreferences("vura",0).edit().clear().putString("language",language).commit()
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        try{
            val a=controller.get();val decor=a.window.decorView
            capture(decor,"voice-board-$language",1280,800)
            val voice=a.findViewById<Button>(R.id.voice_assistant_button)
            assertTrue(voice.width>=48);assertTrue(voice.isShown)
            val voiceRect=Rect();voice.getGlobalVisibleRect(voiceRect)
            views(decor).filterIsInstance<WorkspaceIcon>().forEach{control->
                val rect=Rect();if(control.getGlobalVisibleRect(rect))assertFalse("Assistant overlaps a drawing control",Rect.intersects(voiceRect,rect))
            }
            val focus=views(decor).first{it.contentDescription?.toString()?.let{description->description.contains("Hide / show toolbars") || description.contains("پنهان / نمایان کردن ابزارها")}==true}
            focus.performClick();assertTrue(voice.isShown)
            voice.performClick();val dialog=ShadowDialog.getLatestDialog()
            capture(dialog.window!!.decorView,"voice-connection-$language",1000,900)
            assertTrue(views(dialog.window!!.decorView).filterIsInstance<EditText>().any{it.hint?.toString()?.contains("API")==true})
            dialog.dismiss()
            VoiceSettingsUi(a).advanced{}
            val admin=ShadowDialog.getLatestDialog()
            capture(admin.window!!.decorView,"voice-admin-$language",1000,1000)
            assertEquals(5,views(admin.window!!.decorView).filterIsInstance<Spinner>().size)
            admin.dismiss()
        }finally{controller.pause().stop().destroy();context.getSharedPreferences("vura",0).edit().clear().commit()}
    }
}
