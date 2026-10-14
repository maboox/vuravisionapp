package com.vuravision.classroom.tools

import android.app.Dialog
import android.content.Context
import android.graphics.*
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import com.google.gson.Gson
import com.vuravision.classroom.Media
import com.vuravision.classroom.R
import com.vuravision.classroom.board.Item
import com.vuravision.classroom.board.Page
import com.vuravision.classroom.board.Point
import com.vuravision.classroom.board.Renderer
import com.vuravision.classroom.board.newId
import com.vuravision.classroom.layoutDirection
import com.vuravision.classroom.ui.INK_NAVY
import com.vuravision.classroom.ui.INK_TEAL
import com.vuravision.classroom.ui.MUTED
import com.vuravision.classroom.ui.NAVY
import com.vuravision.classroom.ui.ORANGE
import com.vuravision.classroom.ui.OUTLINE_STRONG
import com.vuravision.classroom.ui.PAPER
import com.vuravision.classroom.ui.PRIMARY_CONTAINER
import com.vuravision.classroom.ui.SECONDARY_CONTAINER
import com.vuravision.classroom.ui.SURFACE
import com.vuravision.classroom.ui.TEAL
import com.vuravision.classroom.ui.UiTheme
import com.vuravision.classroom.ui.WorkspaceIcon
import com.vuravision.classroom.ui.button
import com.vuravision.classroom.ui.card
import com.vuravision.classroom.ui.column
import com.vuravision.classroom.ui.dp
import com.vuravision.classroom.ui.field
import com.vuravision.classroom.ui.label
import com.vuravision.classroom.ui.pad
import com.vuravision.classroom.ui.row
import com.vuravision.classroom.ui.s
import com.vuravision.classroom.ui.scrollRow
import com.vuravision.classroom.ui.tr

data class GuideTopic(
    val id: String,
    val section: String? = null,
    val titleEn: String,
    val titleFa: String,
    val stepsEn: List<String>,
    val stepsFa: List<String>,
    val tipEn: String,
    val tipFa: String
)

object UserGuide {
    fun topics(context: Context): List<GuideTopic> = context.assets.open("guide/topics.json").bufferedReader().use {
        Gson().fromJson(it, Array<GuideTopic>::class.java).toList()
    }
    fun show(context: Context, media: Media, secret: () -> Unit) = show(context, media, secret, null)
    private val sections = listOf(
        "start" to ("Getting started" to "شروع"),
        "draw" to ("Writing and drawing" to "نوشتن و رسم"),
        "organize" to ("Pages and organizing" to "صفحه و سازمان‌دهی"),
        "class" to ("Class tools" to "ابزارهای کلاس"),
        "smart" to ("Smart tools" to "ابزارهای هوشمند"),
        "files" to ("Files, PDF and sharing" to "فایل، PDF و اشتراک")
    )

    /**
     * The guide as a small book: chapters grouped by section on the side (above on narrow screens), the chapter
     * itself with a picture, numbered steps and something to try, and Previous / Next at the bottom.
     */
    fun show(context: Context, media: Media, secret: () -> Unit, back: (() -> Unit)?) {
        val topics = topics(context)
        var fa = context.resources.configuration.locales[0].language == "fa"
        var index = 0
        val wide = context.resources.configuration.screenWidthDp >= 900
        val dialog = Dialog(context, UiTheme.style())
        val root = context.column().apply {
            setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(12))
            setBackgroundColor(PAPER)
        }
        val header = context.row().apply { gravity = Gravity.CENTER_VERTICAL }
        root.addView(header)
        if (back != null) {
            header.addView(
                WorkspaceIcon(context, "menu_back", context.s("menu_back")) {
                    dialog.dismiss()
                    back()
                }.apply {
                    id = R.id.menu_back_button
                    scaleX = if (resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL)-1f else 1f
                }
            )
        }
        val heading = context.label("", 24f, NAVY, true)
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        val language = context.button("فارسی / EN") {}
        header.addView(language, LinearLayout.LayoutParams(-2, context.dp(48)))
        header.addView(context.button("×") { dialog.dismiss() }.apply { contentDescription = context.tr("Close guide", "بستن راهنما") }, LinearLayout.LayoutParams(context.dp(56), context.dp(48)).apply { marginStart = context.dp(8) })
        val search = context.field()
        val chapters = context.column()
        val middle = if (wide) context.row().apply { gravity = Gravity.TOP } else context.column()
        val side = context.column().apply { setPadding(0, 0, context.dp(12), 0) }
        side.addView(search)
        if (wide) {
            side.addView(ScrollView(context).apply { addView(chapters) }, LinearLayout.LayoutParams(-1, 0, 1f))
            middle.addView(side, LinearLayout.LayoutParams(context.dp(300), -1))
        } else {
            side.addView(context.scrollRow(chapters.apply { orientation = LinearLayout.HORIZONTAL }))
            middle.addView(side, LinearLayout.LayoutParams(-1, -2))
        }
        val body = context.column().apply { setPadding(context.dp(16), context.dp(8), context.dp(16), context.dp(16)) }
        val scroll = ScrollView(context).apply {
            addView(body)
            isFillViewport = true
        }
        middle.addView(scroll, if (wide) LinearLayout.LayoutParams(0, -1, 1f) else LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(middle, LinearLayout.LayoutParams(-1, 0, 1f))
        val footer = context.row().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(8), 0, 0)
        }
        root.addView(footer)
        val previous = context.button("") {}
        val next = context.button("", true) {}
        val position = context.label("", 14f, MUTED).apply { gravity = Gravity.CENTER }
        footer.addView(previous, LinearLayout.LayoutParams(-2, context.dp(52)))
        footer.addView(position, LinearLayout.LayoutParams(0, -2, 1f))
        footer.addView(next, LinearLayout.LayoutParams(-2, context.dp(52)))
        fun lang(en: String, persian: String) = if (fa)persian else en
        fun sectionName(key: String?) = sections.firstOrNull { it.first == key }?.second?.let { lang(it.first, it.second) }.orEmpty()
        fun renderBody() {
            root.layoutDirection = if (fa)View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            heading.text = lang("Learn VuraVision", "آموزش ویراویژن")
            search.hint = lang("Search the guide…", "جستجو در راهنما…")
            previous.text = lang("Previous", "قبلی")
            next.text = lang("Next", "بعدی")
            previous.isEnabled = index > 0
            next.isEnabled = index < topics.lastIndex
            position.text = lang("${index + 1} of ${topics.size}", "${index + 1} از ${topics.size}")
            val topic = topics[index]
            body.removeAllViews()
            body.addView(context.label(sectionName(topic.section), 13f, TEAL, true))
            body.addView(context.label(if (fa)topic.titleFa else topic.titleEn, 28f, NAVY, true).apply { setPadding(0, context.dp(2), 0, context.dp(12)) })
            body.addView(context.card(GuideFigure(context, media, topic.id, fa).apply { minimumHeight = context.dp(220) }, SURFACE), LinearLayout.LayoutParams(-1, context.dp(240)))
            (if (fa)topic.stepsFa else topic.stepsEn).forEachIndexed { i, step ->
                val line = context.row().apply {
                    gravity = Gravity.TOP
                    setPadding(context.dp(14), context.dp(12), context.dp(14), context.dp(12))
                    background = com.vuravision.classroom.ui.rounded(SURFACE, context.dp(16).toFloat())
                }
                line.addView(
                    context.label("${i + 1}", 16f, android.graphics.Color.WHITE, true).apply {
                        gravity = Gravity.CENTER
                        background = com.vuravision.classroom.ui.rounded(TEAL, context.dp(16).toFloat())
                    },
                    LinearLayout.LayoutParams(context.dp(32), context.dp(32))
                )
                line.addView(
                    context.label(step, 17f, NAVY).apply {
                        setLineSpacing(context.dp(4).toFloat(), 1.05f)
                        textDirection = if (fa)View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
                    },
                    LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = context.dp(12) }
                )
                body.addView(line, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(10) })
            }
            val tip = context.column().apply { pad(16) }
            tip.addView(context.label(lang("Try it", "امتحان کنید"), 15f, TEAL, true))
            tip.addView(context.label(if (fa)topic.tipFa else topic.tipEn, 16f, NAVY))
            body.addView(context.card(tip, SECONDARY_CONTAINER), LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(14) })
            scroll.post { scroll.scrollTo(0, 0) }
        }
        fun renderChapters() {
            chapters.removeAllViews()
            val query = search.text?.toString()?.trim().orEmpty()
            var lastSection: String? = "-"
            topics.forEachIndexed { i, t ->
                val haystack = listOf(t.titleEn, t.titleFa, t.stepsEn.joinToString(), t.stepsFa.joinToString()).joinToString(" ")
                if (query.isBlank() || haystack.contains(query, ignoreCase = true)) {
                    if (wide && t.section != lastSection) {
                        lastSection = t.section
                        chapters.addView(context.label(sectionName(t.section), 12f, MUTED, true).apply { setPadding(context.dp(6), context.dp(14), context.dp(6), context.dp(4)) })
                    }
                    chapters.addView(
                        context.button((if (fa)t.titleFa else t.titleEn), i == index) {
                            index = i
                            renderBody()
                            renderChapters()
                        }.apply {
                            textDirection = if (fa)View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
                            gravity = Gravity.CENTER_VERTICAL or Gravity.START
                        },
                        if (wide) LinearLayout.LayoutParams(-1, context.dp(48)).apply { bottomMargin = context.dp(4) } else LinearLayout.LayoutParams(-2, context.dp(48)).apply { marginEnd = context.dp(6) }
                    )
                }
            }
            if (chapters.childCount == 0)chapters.addView(context.label(lang("Nothing found", "چیزی پیدا نشد"), 15f, MUTED))
        }

        language.setOnClickListener {
            fa = !fa
            renderBody()
            renderChapters()
        }
        previous.setOnClickListener {
            if (index > 0) {
                index--
                renderBody()
                renderChapters()
            }
        }
        next.setOnClickListener {
            if (index < topics.lastIndex) {
                index++
                renderBody()
                renderChapters()
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderChapters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        renderBody()
        renderChapters()
        dialog.setContentView(root)
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
}

/** Examples are rendered with the application's own object renderer, so the
 * illustrated tools retain the same geometry as board objects and exports. */
class GuideFigure(context: Context, private val media: Media, private val topic: String, private val fa: Boolean) : View(context) {
    private val renderer = Renderer(media)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init {
        contentDescription = if (fa)"تصویر آموزشی"else "Instructional illustration"
    }
    override fun onDraw(canvas: Canvas) {
        val scale = minOf(width / 900f, height / 420f)
        canvas.save()
        canvas.translate((width - 900 * scale) / 2, (height - 420 * scale) / 2)
        canvas.scale(scale, scale)
        paint.color = Color.WHITE
        canvas.drawRoundRect(0f, 0f, 900f, 420f, 24f, 24f, paint)
        fun shape(key: String, x: Float, y: Float, w: Float, h: Float, color: Int = TEAL, stroke: Float = 3f) {
            renderer.draw(canvas, Item(kind = "shape", shape = key, x = x, y = y, w = w, h = h, color = color, width = stroke))
        }
        fun line(coords: List<Pair<Float, Float>>, color: Int = TEAL, stroke: Float = 4f, dashed: Boolean = false) {
            renderer.draw(canvas, Item(w = 900f, h = 420f, inkW = 900f, inkH = 420f, color = color, width = stroke, shape = if (dashed)"dashed"else "round", dashLength = 15f, dashGap = 10f, points = coords.map { Point(it.first, it.second) }.toMutableList()))
        }
        fun text(en: String, persian: String, x: Float, y: Float, color: Int = NAVY) {
            paint.color = color
            paint.style = Paint.Style.FILL
            paint.textSize = 24f
            canvas.drawText(if (fa)persian else en, x, y, paint)
        }
        fun badge(n: Int, x: Float, y: Float) {
            paint.color = TEAL
            paint.style = Paint.Style.FILL
            canvas.drawCircle(x, y, 18f, paint)
            paint.color = Color.WHITE
            paint.textSize = 22f
            canvas.drawText(n.toString(), x - 6, y + 8, paint)
        }
        fun note(x: Float, y: Float, color: Int, text: String) {
            renderer.draw(canvas, Item(kind = "sticky", x = x, y = y, w = 160f, h = 86f, noteColor = color, text = text, width = 20f))
        }
        when (topic) {
            "workspace", "settings" -> {
                paint.color = PAPER
                canvas.drawRoundRect(22f, 22f, 878f, 397f, 18f, 18f, paint)
                paint.color = SURFACE
                canvas.drawRoundRect(505f, 333f, 858f, 389f, 12f, 12f, paint)
                canvas.drawRoundRect(42f, 112f, 98f, 342f, 12f, 12f, paint)
                listOf("files", "share", "help", "settings").forEachIndexed { i, key ->
                    val icon = WorkspaceIcon(context, key, key) {}
                    icon.measure(48, 48)
                    icon.layout(0, 0, 48, 48)
                    canvas.save()
                    canvas.translate(514f + i * 64f, 337f)
                    icon.draw(canvas)
                    canvas.restore()
                }
                for (i in 0..4) {
                    shape(if (i == 0)"circle"else "rectangle", 55f, 125f + i * 42, 24f, 24f, NAVY, 2f)
                }
                line(listOf(175f to 230f, 235f to 160f, 310f to 255f, 392f to 170f, 470f to 242f), NAVY, 5f)
                shape("circle", 580f, 135f, 150f, 150f, ORANGE)
                badge(1, 480f, 360f)
                badge(2, 123f, 157f)
                badge(3, 125f, 365f)
                text("Files · share · guide · settings", "فایل، اشتراک، راهنما، تنظیمات", 485f, 70f)
                text("Drawing tools", "ابزار رسم", 485f, 110f)
                text("1 / 12", "۱ / ۱۲", 166f, 370f)
            }
            "pen", "dashes" -> {
                for (i in 0..3)line(listOf(100f to 100f + i * 65, 220f to 76f + i * 65, 345f to 120f + i * 65, 480f to 94f + i * 65, 620f to 118f + i * 65), intArrayOf(NAVY, TEAL, ORANGE, 0xffbb5279.toInt())[i], if (i == 3)18f else (i + 1) * 3f, topic == "dashes" || i == 2)
                text("Color · thickness · style", "رنگ، ضخامت، سبک", 150f, 376f)
            }
            "eraser", "palm" -> {
                line(listOf(80f to 210f, 810f to 210f), NAVY, 7f)
                paint.color = Color.WHITE
                canvas.drawRect(373f, 192f, 492f, 230f, paint)
                paint.color = 0x440f716f
                canvas.drawCircle(430f, 210f, 60f, paint)
                shape("circle", 370f, 150f, 120f, 120f, TEAL, 2f)
                badge(1, 195f, 145f)
                badge(2, 430f, 125f)
                text("Area eraser", "پاک‌کن ناحیه‌ای", 330f, 336f)
            }
            "hold", "shapes" -> {
                shape("rectangle", 100f, 90f, 180f, 160f, MUTED, 2f)
                shape("rectangle", 380f, 90f, 280f, 230f, TEAL, 4f)
                badge(1, 660f, 320f)
                line(listOf(660f to 320f, 635f to 275f), ORANGE, 5f)
                shape("arrow", 302f, 171f, 60f, 1f, ORANGE)
                text("Start stays fixed", "نقطهٔ شروع ثابت", 522f, 372f)
                text("Hold → drag", "مکث و کشیدن", 70f, 340f)
            }
            "select", "navigation" -> {
                note(290f, 140f, 0xffffe8b2.toInt(), if (fa)"انتخاب"else "Select")
                shape("rectangle", 273f, 123f, 194f, 119f, TEAL, 2f)
                listOf(273f to 123f, 467f to 123f, 273f to 242f, 467f to 242f).forEach { shape("circle", it.first - 5, it.second - 5, 10f, 10f, TEAL) }
                shape("arrow", 494f, 195f, 153f, 1f, ORANGE)
                text("Move / resize", "جابه‌جایی و اندازه", 230f, 340f)
            }
            "text", "mindmap" -> {
                val parent = Item(kind = "sticky", shape = "mindnode", x = 120f, y = 166f, w = 175f, h = 85f, text = if (fa)"موضوع"else "Topic", width = 21f)
                val a = Item(kind = "sticky", shape = "mindnode", x = 530f, y = 65f, w = 195f, h = 85f, text = if (fa)"شاخهٔ اول"else "Branch one", parentNode = parent.id, noteColor = 0xffcdeeeB.toInt(), width = 21f)
                val b = a.copy(id = newId(), y = 277f, text = if (fa)"شاخهٔ دوم"else "Branch two", noteColor = 0xffe9ecff.toInt())
                renderer.scene(canvas, Page(items = mutableListOf(parent, a, b)))
                text("Add branch", "افزودن شاخه", 315f, 376f, TEAL)
            }
            "layers" -> {
                for (i in 2 downTo 0) {
                    paint.color = intArrayOf(SECONDARY_CONTAINER, PRIMARY_CONTAINER, 0xffffe8b2.toInt())[i]
                    canvas.drawRoundRect(160f + i * 90, 80f + i * 70, 550f + i * 90, 220f + i * 70, 15f, 15f, paint)
                    text("Layer ${i + 1}", "لایهٔ ${i + 1}", 200f + i * 90, 125f + i * 70)
                }
            }
            "pages", "split" -> {
                val backgrounds = intArrayOf(Color.WHITE, 0xff242b39.toInt(), 0xffffedc9.toInt(), 0xffdef4e9.toInt())
                for (i in 0..3) {
                    canvas.save()
                    canvas.translate(45f + i * 205f, 25f)
                    canvas.clipRect(0f, 0f, 195f, 370f)
                    renderer.background(canvas, Page(background = if (i == 0)"grid"else "dots"), RectF(0f, 0f, 195f, 370f), backgrounds[i])
                    canvas.restore()
                    badge(i + 1, 75f + i * 205f, 55f)
                }
            }
            "geometry" -> {
                renderer.draw(canvas, Item(kind = "shape", shape = "ruler", x = 85f, y = 180f, w = 390f, h = 70f, color = INK_NAVY))
                line(listOf(85f to 171f, 475f to 171f), ORANGE, 4f)
                renderer.draw(canvas, Item(kind = "shape", shape = "compass", x = 610f, y = 90f, w = 135f, h = 160f, color = INK_TEAL))
                shape("circle", 537f, 145f, 200f, 200f, TEAL, 2f)
                text("Start near the edge", "از نزدیک لبه آغاز کنید", 85f, 336f)
            }
            "pdf-reader", "pdf-save" -> {
                shape("rectangle", 50f, 40f, 330f, 335f, MUTED, 2f)
                shape("rectangle", 430f, 40f, 380f, 335f, TEAL, 2f)
                text("PDF", "PDF", 175f, 86f, TEAL)
                text("Board", "تخته", 555f, 86f, TEAL)
                line(listOf(100f to 145f, 325f to 145f), MUTED, 2f)
                line(listOf(100f to 180f, 325f to 180f), MUTED, 2f)
                line(listOf(110f to 214f, 185f to 233f, 230f to 211f, 306f to 247f), ORANGE, 4f)
                line(listOf(405f to 65f, 405f to 338f), TEAL, 6f)
                text("Save PDF / project", "ذخیرهٔ PDF / پروژه", 125f, 398f)
            }
            "widgets" -> {
                note(85f, 145f, SECONDARY_CONTAINER, "05:00")
                note(355f, 145f, PRIMARY_CONTAINER, "12 : 08")
                shape("rectangle", 627f, 145f, 96f, 96f, ORANGE)
                for (y in listOf(170f, 215f)) {
                    for (x in listOf(650f, 700f)) {
                        paint.color = NAVY
                        canvas.drawCircle(x, y, 4f, paint)
                    }
                }
            }
            "smart", "math" -> {
                note(80f, 160f, 0xffffe8b2.toInt(), "12 inch")
                shape("arrow", 292f, 200f, 140f, 1f, ORANGE)
                note(465f, 160f, SECONDARY_CONTAINER, "30.48 cm")
                text("Select → review → apply", "انتخاب، بررسی، اعمال", 170f, 345f)
            }
            "labs" -> {
                shape("circle", 420f, 115f, 100f, 100f, TEAL)
                line(listOf(450f to 30f, 510f to 130f), NAVY, 3f)
                line(listOf(90f to 325f, 810f to 325f), MUTED, 2f)
                shape("arrow", 600f, 278f, 130f, 1f, ORANGE)
                text("Change a control", "کنترل را تغییر دهید", 135f, 110f)
            }
            "shared-room" -> {
                val colors = intArrayOf(TEAL, ORANGE, 0xff7754ad.toInt())
                listOf("View" to "مشاهده", "Control" to "کنترل", "Collaborate" to "همکاری").forEachIndexed { i, label ->
                    val x = 65f + i * 275
                    shape("rectangle", x, 115f, 240f, 180f, colors[i], 3f)
                    badge(i + 1, x + 30, 145f)
                    text(label.first, label.second, x + 20, 265f, colors[i])
                }
                text("Local profiles · shared board", "پروفایل محلی · تختهٔ مشترک", 120f, 70f)
                text("Same Wi-Fi / LAN / hotspot", "یک وای‌فای، شبکه یا هات‌اسپات", 120f, 360f)
            }
            "replay" -> {
                line(listOf(90f to 150f, 180f to 90f, 270f to 170f, 360f to 100f), NAVY, 6f)
                line(listOf(420f to 150f, 520f to 110f), NAVY, 6f)
                line(listOf(520f to 110f, 560f to 95f), MUTED, 6f, true)
                paint.color = OUTLINE_STRONG
                canvas.drawRoundRect(90f, 300f, 810f, 310f, 5f, 5f, paint)
                paint.color = TEAL
                canvas.drawRoundRect(90f, 300f, 520f, 310f, 5f, 5f, paint)
                canvas.drawCircle(520f, 305f, 14f, paint)
                text("1 min · 2× · Start here", "۱ دقیقه · ۲× · شروع از اینجا", 330f, 365f)
            }
            "quiz" -> {
                note(260f, 40f, 0xffffe8b2.toInt(), if (fa)"۷ × ۸ = ؟"else "7 × 8 = ?")
                listOf("54", "56", "48", "64").forEachIndexed { i, v ->
                    val x = 120f + (i % 2) * 340f
                    val y = 160f + (i / 2) * 110f
                    paint.color = if (i == 1) TEAL else SURFACE
                    canvas.drawRoundRect(x, y, x + 300f, y + 90f, 18f, 18f, paint)
                    text(v, v, x + 130f, y + 55f, if (i == 1) Color.WHITE else NAVY)
                }
            }
            "search" -> {
                paint.color = SURFACE
                canvas.drawRoundRect(430f, 40f, 860f, 380f, 18f, 18f, paint)
                paint.color = PRIMARY_CONTAINER
                canvas.drawRoundRect(430f, 40f, 860f, 92f, 18f, 18f, paint)
                text("Google", "گوگل", 455f, 76f)
                shape("rectangle", 470f, 120f, 160f, 110f, TEAL, 3f)
                shape("triangle", 660f, 120f, 160f, 110f, ORANGE, 3f)
                shape("arrow", 300f, 175f, 150f, 1f, ORANGE)
                shape("rectangle", 80f, 120f, 190f, 140f, TEAL, 3f)
                text("Long-press → board", "نگه دارید ← تخته", 80f, 330f)
            }
            "games" -> {
                shape("rectangle", 130f, 63f, 550f, 280f, MUTED, 2f)
                line(listOf(405f to 63f, 405f to 343f), MUTED, 2f, true)
                line(listOf(165f to 150f, 165f to 250f), TEAL, 12f)
                line(listOf(647f to 150f, 647f to 250f), ORANGE, 12f)
                shape("circle", 360f, 200f, 20f, 20f, NAVY, 8f)
                text("Player 1", "بازیکن ۱", 175f, 388f)
                text("Player 2", "بازیکن ۲", 515f, 388f)
            }
            else -> {
                for (i in 0..2) {
                    shape("rectangle", 125f + i * 22, 70f + i * 22, 215f, 230f, if (i == 2)TEAL else OUTLINE_STRONG, 3f)
                }
                shape("arrow", 417f, 200f, 135f, 1f, ORANGE)
                note(
                    604f,
                    145f,
                    SECONDARY_CONTAINER,
                    when (topic) {
                        "import" -> "PDF"
                        "export" -> "QR / PDF"
                        else -> ".vura"
                    }
                )
                text("Save / view / share", "ذخیره، مشاهده، اشتراک", 420f, 365f)
            }
        }
        canvas.restore()
    }
}
