package com.vuravision.classroom

import android.graphics.*
import android.view.*
import android.widget.*
import java.io.*
import java.util.zip.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "en-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidTest {
    private lateinit var media: Media

    @Before
    fun setup() {
        media = Media(RuntimeEnvironment.getApplication())
        media.context.getSharedPreferences("vura", 0).edit().clear().commit()
    }

    @After
    fun cleanup() {
        media.close()
    }

    @Test
    fun archiveRoundTrip() {
        val b = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        b.eraseColor(Color.RED)
        val name = media.save(b)
        val doc =
            Lesson(
                title = "Lesson فارسی",
                pages =
                    mutableListOf(
                        Page(
                            items =
                                mutableListOf(
                                    Item(kind = "image", asset = name),
                                    Item(kind = "text", text = "سلام"),
                                )
                        )
                    ),
            )
        val out = ByteArrayOutputStream()
        LessonFiles(media).write(doc, out)
        val restored = LessonFiles(media).read(ByteArrayInputStream(out.toByteArray()))
        assertEquals(doc.title, restored.title)
        assertEquals("سلام", restored.pages[0].items[1].text)
        assertNotNull(media.image(restored.pages[0].items[0], true))
    }

    @Test
    fun traversalRejected() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use {
            it.putNextEntry(ZipEntry("assets/../../escape"))
            it.write(byteArrayOf(1))
            it.closeEntry()
        }
        assertThrows(IllegalArgumentException::class.java) {
            LessonFiles(media).read(ByteArrayInputStream(out.toByteArray()))
        }
    }

    @Test
    fun missingAssetRejected() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use {
            it.putNextEntry(ZipEntry("document.json"))
            it.write(
                com.google.gson
                    .Gson()
                    .toJson(
                        Lesson(
                            pages =
                                mutableListOf(
                                    Page(
                                        items =
                                            mutableListOf(
                                                Item(kind = "image", asset = "missing.png")
                                            )
                                    )
                                )
                        )
                    )
                    .toByteArray()
            )
            it.closeEntry()
        }
        assertThrows(IllegalArgumentException::class.java) {
            LessonFiles(media).read(ByteArrayInputStream(out.toByteArray()))
        }
    }

    @Test
    fun atomicSaveBackup() {
        val f = File(media.context.cacheDir, "test-${newId()}.vura")
        val files = LessonFiles(media)
        files.atomic(Lesson(title = "first"), f)
        files.atomic(Lesson(title = "second"), f)
        assertEquals("second", files.read(f.inputStream()).title)
        assertEquals("first", files.read(File(f.path + ".bak").inputStream()).title)
    }

    @Test
    fun boundedStream() {
        assertThrows(IllegalArgumentException::class.java) {
            boundedCopy(ByteArrayInputStream(ByteArray(11)), ByteArrayOutputStream(), 10)
        }
    }

    @Test
    fun imageExport() {
        val b = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        b.eraseColor(Color.RED)
        val name = media.save(b)
        val page =
            Page(
                background = "white",
                items = mutableListOf(Item(kind = "image", asset = name, w = 100f, h = 100f)),
            )
        val output = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        Renderer(media).page(Canvas(output), page, 400, 400, true)
        assertEquals(Color.RED, output.getPixel(200, 200))
    }

    @Test
    fun boardScreenshot() {
        val ctl = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = ctl.get()
        a.store.replace(demo())
        capture(a.window.decorView, "board-en.png") { a.board.fit() }
        assertTrue(a.board.width > 0)
        ctl.pause().stop().destroy()
    }

    @Test
    fun persianInterface() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("vura", 0)
            .edit()
            .putString("language", "fa")
            .commit()
        val ctl = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = ctl.get()
        a.store.replace(
            demo().apply {
                title = "درس حرکت"
                pages[0].items[0].text = "شکل حرکت"
                pages[0].items[1].text = "کشف کن • پیش‌بینی کن • توضیح بده"
                pages[0].items[3].text =
                    "با هم فکر کنیم\n\nاگر سرعت اولیه دو برابر شود چه چیزی تغییر می‌کند؟"
            }
        )
        capture(a.window.decorView, "board-fa.png") { a.board.fit() }
        assertEquals("fa", a.resources.configuration.locales[0].language)
        ctl.pause().stop().destroy()
    }

    @Test
    fun labSnapshots() {
        listOf(
                "projectile" to floatArrayOf(22f, 45f, 9.8f),
                "pendulum" to floatArrayOf(1.5f, 9.8f, 12f),
                "spring" to floatArrayOf(1f, 12f, 1f),
                "waves" to floatArrayOf(2f, 1f, .7f),
                "quadratic" to floatArrayOf(1f, 0f, 0f),
                "trig" to floatArrayOf(45f),
                "ph" to floatArrayOf(7f),
                "coin" to floatArrayOf(.5f, 100f),
            )
            .forEach { (key, values) ->
                val b = LabView(media.context, key, values).snapshot()
                assertEquals(1200, b.width)
                save(b, "lab-$key.png")
            }
    }

    @Test
    fun multiplePointersAndUndo() {
        val s = Store()
        val b = Board(media.context, s, Renderer(media))
        fun event(action: Int, ids: IntArray, shift: Float, time: Long) {
            val props =
                ids.map {
                        MotionEvent.PointerProperties().apply {
                            id = it
                            toolType = MotionEvent.TOOL_TYPE_FINGER
                        }
                    }
                    .toTypedArray()
            val coords =
                ids.mapIndexed { i, _ ->
                        MotionEvent.PointerCoords().apply {
                            x = 100f + i * 150 + shift
                            y = 100f + shift
                            pressure = 1f
                            size = 1f
                        }
                    }
                    .toTypedArray()
            val e =
                MotionEvent.obtain(
                    1,
                    time,
                    action,
                    ids.size,
                    props,
                    coords,
                    0,
                    0,
                    1f,
                    1f,
                    0,
                    0,
                    android.view.InputDevice.SOURCE_TOUCHSCREEN,
                    0,
                )
            b.onTouchEvent(e)
            e.recycle()
        }
        event(MotionEvent.ACTION_DOWN, intArrayOf(3), 0f, 1)
        event(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8), intArrayOf(3, 9), 0f, 2)
        event(MotionEvent.ACTION_MOVE, intArrayOf(3, 9), 40f, 3)
        event(MotionEvent.ACTION_POINTER_UP, intArrayOf(3, 9), 50f, 4)
        event(MotionEvent.ACTION_UP, intArrayOf(9), 80f, 5)
        assertEquals(2, s.page.items.size)
        s.lesson.validate()
        s.undo()
        assertTrue(s.page.items.isEmpty())
    }

    @Test
    fun insertTextThroughInterfaceAndUndo() {
        val ctl = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = ctl.get()
        a.store.replace(Lesson())
        views(a.window.decorView)
            .filterIsInstance<Button>()
            .first { it.text.toString().endsWith(a.s("insert")) }
            .performClick()
        val menu = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        menu.listView.performItemClick(null, 0, 0)
        val input = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        views(input.window!!.decorView)
            .filterIsInstance<EditText>()
            .first()
            .setText("Beta classroom")
        input.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals("Beta classroom", a.store.page.items.single().text)
        a.store.undo()
        assertTrue(a.store.page.items.isEmpty())
        ctl.pause().stop().destroy()
    }

    @Test
    fun gameScreenAndAnswerLock() {
        val ctl = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = ctl.get()
        Games.open(a, "rps")
        val d = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        views(d.window!!.decorView)
            .filterIsInstance<Button>()
            .first { it.text == a.s("start") }
            .performClick()
        val options =
            views(d.window!!.decorView)
                .filterIsInstance<Button>()
                .filter { it.text == a.s("rock") }
                .toList()
        assertEquals(2, options.size)
        options[0].performClick()
        assertFalse(options[0].isEnabled)
        assertTrue(options[1].isEnabled)
        capture(d.window!!.decorView, "game-rps.png")
        d.dismiss()
        ctl.pause().stop().destroy()
    }

    private fun views(v: View): Sequence<View> = sequence {
        yield(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) yieldAll(views(v.getChildAt(i)))
    }

    private fun capture(view: View, name: String, after: () -> Unit = {}) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 1600, 1000)
        after()
        val b = Bitmap.createBitmap(1600, 1000, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(b))
        save(b, name)
    }

    private fun save(b: Bitmap, name: String) {
        val dir = File("build/qa").apply { mkdirs() }
        File(dir, name).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun demo() =
        Lesson(
            title = "The shape of motion",
            pages =
                mutableListOf(
                    Page(
                        items =
                            mutableListOf(
                                Item(
                                    kind = "text",
                                    text = "The shape of motion",
                                    x = 65f,
                                    y = 40f,
                                    w = 660f,
                                    h = 75f,
                                    width = 44f,
                                ),
                                Item(
                                    kind = "text",
                                    text = "Explore • predict • explain",
                                    x = 65f,
                                    y = 110f,
                                    w = 500f,
                                    h = 50f,
                                    width = 21f,
                                    color = TEAL,
                                ),
                                Item(
                                    kind = "graph",
                                    text = "-0.1*x^2+5; sin(x)",
                                    x = 65f,
                                    y = 210f,
                                    w = 580f,
                                    h = 340f,
                                ),
                                Item(
                                    kind = "sticky",
                                    text =
                                        "Think together\n\nWhat changes when the initial speed doubles?",
                                    x = 715f,
                                    y = 230f,
                                    w = 270f,
                                    h = 300f,
                                    width = 25f,
                                ),
                                Item(
                                    kind = "shape",
                                    shape = "arrow",
                                    x = 620f,
                                    y = 145f,
                                    w = 120f,
                                    h = 70f,
                                    color = ORANGE,
                                    width = 4f,
                                ),
                            )
                    )
                ),
        )
}
