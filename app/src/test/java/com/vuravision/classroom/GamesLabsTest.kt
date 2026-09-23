package com.vuravision.classroom

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GamesLabsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun tap(game: ArcadeView, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0).also {
            game.onTouchEvent(it)
            it.recycle()
        }
        MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, x, y, 0).also {
            game.onTouchEvent(it)
            it.recycle()
        }
    }

    @Test fun reactionsAcceptTheWholePanelAndPongTouchDoesNotEndTheRound() {
        val reaction = ArcadeView(context, "reaction")
        reaction.layout(0, 0, 1000, 900)
        reaction.startRound()
        tap(reaction, 250f, 300f)
        assertArrayEquals(intArrayOf(0, 1), reaction.scores)

        val pong = ArcadeView(context, "pong")
        pong.layout(0, 0, 1000, 900)
        pong.startRound()
        tap(pong, 250f, 300f)
        assertArrayEquals(intArrayOf(0, 0), pong.scores)
    }

    @Test fun highLowAlwaysHasDistinctCards() {
        repeat(100) { seed ->
            val round = ArcadeRound("hilo", Random(seed)).apply { prepare() }
            assertNotEquals(round.target, round.sequence.single())
        }
    }

    @Test fun faceToFaceMirrorsTheAnswerHitArea() {
        val game = ArcadeView(context, "evenodd")
        game.face = true
        game.layout(0, 0, 500, 1300)
        game.startRound()
        val round = ArcadeView::class.java.getDeclaredField("game").apply { isAccessible = true }
            .get(game) as ArcadeRound
        val scale = 580f / 600f
        val left = 8f + (484f - 500f * scale) / 2f
        val choiceX = if (round.correct == 0) 135f else 355f
        tap(game, left + (500f - choiceX) * scale, 70f + (600f - 408f) * scale)
        assertArrayEquals(intArrayOf(1, 0), game.scores)
    }

    @Test fun visualLabControlsChangeTheRenderedDiagram() {
        fun snapshot(key: String, vararg values: Float): Bitmap =
            LabView(context, key, values.copyOf()).snapshot()
        val gasSmall = snapshot("native_gas", .1f, 273f, 2f)
        val gasLarge = snapshot("native_gas", 2.5f, 273f, 48f)
        val dilutionWeak = snapshot("dilution", .1f, 20f, 500f)
        val dilutionStrong = snapshot("dilution", 2f, 100f, 0f)
        try {
            assertFalse(gasSmall.sameAs(gasLarge))
            assertFalse(dilutionWeak.sameAs(dilutionStrong))
        } finally {
            listOf(gasSmall, gasLarge, dilutionWeak, dilutionStrong).forEach { it.recycle() }
        }
    }
}
