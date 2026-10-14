package com.vuravision.classroom

import android.graphics.RectF
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.vuravision.classroom.games.GameFx
import com.vuravision.classroom.board.Item
import com.vuravision.classroom.games.Sounds
import com.vuravision.classroom.games.Spring
import com.vuravision.classroom.ui.s

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class FeedbackTest {
    private val context get()=RuntimeEnvironment.getApplication()

    /** Each cue is short (never talks over the teacher), audible, and does not clip. */
    @Test fun cuesAreShortAudibleAndUnclipped(){
        Sounds.Cue.values().forEach{cue->
            val pcm=Sounds.synth(cue)
            assertTrue("$cue empty",pcm.isNotEmpty())
            assertTrue("$cue longer than 0.7 s",pcm.size<=22050*7/10)
            val peak=pcm.maxOf{kotlin.math.abs(it.toInt())}
            assertTrue("$cue too quiet ($peak)",peak>Short.MAX_VALUE/5)
            assertTrue("$cue clips ($peak)",peak<Short.MAX_VALUE)
        }
    }

    @Test fun soundsRespectTheSetting(){
        val prefs=context.getSharedPreferences("vura",0)
        assertTrue("game sounds should be on by default",Sounds.enabled(context))
        prefs.edit().putBoolean("gameSounds",false).commit();assertFalse(Sounds.enabled(context))
        Sounds.play(context,Sounds.Cue.WIN) // must not throw when off
        prefs.edit().clear().commit();Sounds.play(context,Sounds.Cue.CORRECT) // nor when there is no audio device
    }

    @Test fun winnerFlashesLoserShakesAndMatchGetsConfetti(){
        val fx=GameFx();val panels=listOf(RectF(0f,0f,500f,600f),RectF(500f,0f,1000f,600f))
        assertFalse(fx.busy)
        fx.round(0,panels,match=false);assertTrue("feedback should animate",fx.busy)
        assertEquals("the winner does not shake",0f,fx.shake(0),.001f)
        fx.round(1,panels,match=true)
        val bitmap=android.graphics.Bitmap.createBitmap(1000,600,android.graphics.Bitmap.Config.ARGB_8888)
        try{fx.confetti(android.graphics.Canvas(bitmap),600);assertTrue(fx.busy)}finally{bitmap.recycle()}
    }
    /** Item 52: the score pop is a spring (one overshoot, settles), and it is over within half a second. */
    @Test fun scorePopIsASpringThatSettles(){
        assertEquals(1f,Spring.bounce(0f),.001f)
        assertTrue("overshoots below zero once",(0..45).map{Spring.bounce(it/100f)}.any{it<-.05f})
        assertTrue("settled by 450 ms",kotlin.math.abs(Spring.bounce(.45f))<.03f)
    }
}
