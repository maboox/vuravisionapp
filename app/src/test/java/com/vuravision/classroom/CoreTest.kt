package com.vuravision.classroom

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    @Test
    fun undoRestoresNestedStroke() {
        val s = Store()
        s.edit { s.page.items.add(Item(points = mutableListOf(Point(1f, 2f)))) }
        s.edit { s.page.items[0].points[0].x = 80f }
        s.undo()
        assertEquals(1f, s.page.items[0].points[0].x)
        s.redo()
        assertEquals(80f, s.page.items[0].points[0].x)
    }

    @Test
    fun moveUndo() {
        val s = Store(Lesson(pages = mutableListOf(Page(items = mutableListOf(Item(x = 10f))))))
        s.edit { s.page.items[0].x = 100f }
        s.undo()
        assertEquals(10f, s.page.items[0].x)
    }

    @Test
    fun editClearsRedo() {
        val s = Store()
        s.edit { s.lesson.title = "A" }
        s.undo()
        s.edit { s.lesson.title = "B" }
        s.redo()
        assertEquals("B", s.lesson.title)
    }

    @Test
    fun rotationRoundTrip() {
        val i = Item(x = 12f, y = 24f, w = 150f, h = 90f, rotation = 67f)
        val p = i.global(15f, 40f)
        val q = i.local(p.first, p.second)
        assertEquals(15f, q.first, .0001f)
        assertEquals(40f, q.second, .0001f)
    }

    @Test
    fun rotatedHit() {
        val o = Item(kind = "shape", x = 100f, y = 100f, w = 200f, h = 100f, rotation = 90f)
        assertTrue(o.hit(200f, 70f))
        assertFalse(o.hit(10f, 10f))
    }

    @Test
    fun scaledStrokeHit() {
        val o =
            Item(
                w = 200f,
                h = 100f,
                inkW = 100f,
                inkH = 100f,
                points = mutableListOf(Point(0f, 0f), Point(100f, 100f)),
            )
        assertTrue(o.hit(100f, 50f, 2f))
        assertFalse(o.hit(20f, 70f, 2f))
    }

    @Test
    fun finiteGeometry() {
        assertThrows(IllegalArgumentException::class.java) {
            Lesson(pages = mutableListOf(Page(items = mutableListOf(Item(x = Float.NaN)))))
                .validate()
        }
    }

    @Test
    fun validPageIndex() {
        assertThrows(IllegalArgumentException::class.java) { Lesson(current = 3).validate() }
    }

    @Test
    fun unsafeAssetRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            Lesson(pages = mutableListOf(Page(items = mutableListOf(Item(asset = "../bad")))))
                .validate()
        }
    }

    @Test
    fun duplicateIdsRejected() {
        val i = Item()
        assertThrows(IllegalArgumentException::class.java) {
            Lesson(pages = mutableListOf(Page(items = mutableListOf(i, i.copy())))).validate()
        }
    }

    @Test
    fun uncalibratedInput() {
        val p = TouchProfile()
        assertEquals("unknown", p.classify(1, 70f))
        assertEquals("draw", p.action(1, 70f))
        assertEquals("stylus", p.classify(2, 1f))
        assertEquals("erase", p.action(4, 1f))
    }

    @Test
    fun calibratedPalm() {
        val p = TouchProfile(true, 10f, 40f, false)
        assertEquals("reject", p.action(1, 60f))
        p.palmErase = true
        assertEquals("erase", p.action(1, 60f))
        assertEquals("thin", p.classify(1, 5f))
    }

    @Test
    fun unicodeMath() {
        assertEquals(14.0, MathTools().evaluate("۲×(۳+۴)"), 1e-8)
        assertEquals(6.0, MathTools().compile("2x")(3.0), 1e-8)
    }

    @Test
    fun linearEquation() {
        assertEquals("x = 4", MathTools().solve("2x+3=11"))
        assertEquals("x = -3", MathTools().solve("-x+2=5"))
    }

    @Test
    fun unsupportedEquationRejected() {
        assertThrows(IllegalStateException::class.java) { MathTools().solve("x^2=9") }
    }

    @Test
    fun undefinedMathRejected() {
        assertThrows(ArithmeticException::class.java) { MathTools().evaluate("1/0") }
    }

    @Test
    fun projectile() {
        val (range, t) = Physics.flight(10.0, 45.0, 10.0)
        assertEquals(10.0, range, 1e-8)
        assertEquals(kotlin.math.sqrt(2.0), t, 1e-8)
    }

    @Test
    fun oscillatorPeriods() {
        assertEquals(2 * Math.PI, Physics.period(10.0, 10.0), 1e-8)
        assertEquals(2 * Math.PI, Physics.springPeriod(5.0, 5.0), 1e-8)
    }

    @Test
    fun acidityLogarithm() {
        assertEquals(10.0, Physics.hydrogen(3.0) / Physics.hydrogen(4.0), 1e-8)
    }

    @Test
    fun earlyReaction() {
        val g = GameEngine("reaction", Random(4))
        g.next(1000)
        g.answer(0, 0, 1100)
        assertEquals(-1, g.scores[0])
        g.answer(1, 0, g.go + 200)
        assertTrue(g.resolved)
        assertEquals(1, g.scores[1])
    }

    @Test
    fun lockedAnswers() {
        val g = GameEngine("math_race", Random(4))
        g.next(1000)
        g.answer(0, (g.correct + 1) % 3, 1100)
        g.answer(0, g.correct, 1200)
        g.answer(1, g.correct, 1300)
        assertEquals(0, g.scores[0])
        assertEquals(1, g.scores[1])
        g.next(1400)
        assertNull(g.answers[0])
    }

    @Test
    fun rpsCycle() {
        val g = GameEngine("rps")
        g.next(0)
        g.answer(0, 0, 100)
        g.answer(1, 1, 200)
        assertEquals(1, g.scores[1])
        g.next(300)
        g.answer(0, 2, 400)
        g.answer(1, 1, 500)
        assertEquals(1, g.scores[0])
    }

    @Test
    fun timing() {
        val g = GameEngine("timing")
        g.next(1000)
        g.answer(0, 0, 4050)
        g.answer(1, 0, 3800)
        assertEquals(1, g.scores[0])
    }

    @Test
    fun tapRaceDeadline() {
        val g = GameEngine("tap_race")
        g.next(1000)
        g.answer(0, 0, 2000)
        g.answer(1, 0, 16001)
        assertEquals(1, g.scores[0])
        assertEquals(0, g.scores[1])
        assertTrue(g.finished)
    }

    @Test
    fun ticTacToeWin() {
        val g = GameEngine("tictac")
        g.next(0)
        listOf(0, 3, 1, 4, 2).forEach { g.cell(it) }
        assertTrue(g.finished)
        assertEquals(1, g.scores[0])
        g.cell(5)
        assertEquals(-1, g.cells[5])
    }

    @Test
    fun tenRounds() {
        val g = GameEngine("bigger", Random(1))
        repeat(10) {
            g.next(it * 1000L)
            g.answer(0, g.correct, it * 1000 + 100L)
            g.answer(1, 1 - g.correct, it * 1000 + 200L)
        }
        assertTrue(g.finished)
        assertEquals(10, g.scores[0])
    }
}
