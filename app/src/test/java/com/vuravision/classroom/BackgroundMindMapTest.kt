package com.vuravision.classroom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="en-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackgroundMindMapTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()

    @Test fun pageBackgroundKeepsPatternAndCustomColorTogether(){
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity=controller.get()
        try {
            MainActivity::class.java.getDeclaredMethod("backgroundSettings",Int::class.javaPrimitiveType)
                .apply{isAccessible=true}.invoke(activity,0)
            val background=ShadowDialog.getLatestDialog()
            val buttons=views(background.window!!.decorView).filterIsInstance<MaterialButton>()
            buttons.single{it.text=="Hatched"}.performClick()
            assertEquals("hatch",activity.store.page.background)
            buttons.single{it.text=="Custom color…"}.performClick()
            val picker=ShadowDialog.getLatestDialog() as AlertDialog
            val hex=views(picker.window!!.decorView).filterIsInstance<TextInputEditText>().single()
            hex.setText("#123ABC")
            picker.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertEquals(Color.parseColor("#123ABC"),activity.store.page.panes[0].background)
            assertEquals("hatch",activity.store.page.background)
            background.dismiss()
            val second=Page()
            activity.store.lesson.pages.add(second)
            assertNotEquals(activity.store.page.panes[0].background,second.panes[0].background)
            val node=Item(kind="sticky",shape="mindnode")
            activity.store.page.items.add(node);activity.board.selected.add(node.id)
            MainActivity::class.java.getDeclaredMethod("colors").apply{isAccessible=true}.invoke(activity)
            val colors=ShadowDialog.getLatestDialog()
            views(colors.window!!.decorView).filterIsInstance<MaterialButton>()
                .single{it.contentDescription=="#000000"}.performClick()
            assertEquals(Color.BLACK,node.noteColor)
            assertEquals(Color.WHITE,node.color)
            colors.dismiss()
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun branchesStayBehindBothNodeBoxes(){
        val media=Media(context)
        try {
            val parent=Item(kind="sticky",shape="mindnode",x=20f,y=40f,w=120f,h=100f,noteColor=Color.RED)
            val child=Item(kind="sticky",shape="mindnode",x=300f,y=40f,w=120f,h=100f,noteColor=Color.BLUE,parentNode=parent.id)
            val page=Page(items=mutableListOf(parent,child))
            val bitmap=Bitmap.createBitmap(500,200,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE)
            Renderer(media).scene(canvas,page,true)
            assertEquals(Color.RED,bitmap.getPixel(80,90))
            assertNotEquals(Color.WHITE,bitmap.getPixel(220,90))
            assertEquals(Color.BLUE,bitmap.getPixel(360,90))
            bitmap.recycle()
        } finally { media.close() }
    }
}
