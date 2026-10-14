package com.vuravision.classroom.tools

import android.content.Context
import android.graphics.*
import android.view.*
import android.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vuravision.classroom.Media
import com.vuravision.classroom.board.Item
import com.vuravision.classroom.ui.Fonts
import com.vuravision.classroom.ui.INK_MUTED
import com.vuravision.classroom.ui.INK_NAVY
import com.vuravision.classroom.ui.INK_PAPER
import com.vuravision.classroom.ui.MUTED
import com.vuravision.classroom.ui.NAVY
import com.vuravision.classroom.ui.OptionAdapter
import com.vuravision.classroom.ui.button
import com.vuravision.classroom.ui.column
import com.vuravision.classroom.ui.dp
import com.vuravision.classroom.ui.field
import com.vuravision.classroom.ui.label
import com.vuravision.classroom.ui.navigationHeading
import com.vuravision.classroom.ui.pad
import com.vuravision.classroom.ui.row
import com.vuravision.classroom.ui.s
import com.vuravision.classroom.ui.tr
import org.json.JSONArray
import org.json.JSONObject

object PeriodicTable {
    data class Element(val data: JSONObject, val fa: String, val col: Int, val row: Int, val group: Int, val period: Int) {
        val number get() = data.getString("AtomicNumber").toInt()
        val symbol get() = data.getString("Symbol")
        val name get() = data.getString("Name")
        fun value(key: String) = data.optString(key).ifBlank { "—" }
    }
    private var cached: List<Element>? = null
    private val names = "هیدروژن|هلیم|لیتیم|بریلیم|بور|کربن|نیتروژن|اکسیژن|فلوئور|نئون|سدیم|منیزیم|آلومینیوم|سیلیسیم|فسفر|گوگرد|کلر|آرگون|پتاسیم|کلسیم|اسکاندیم|تیتانیم|وانادیم|کروم|منگنز|آهن|کبالت|نیکل|مس|روی|گالیم|ژرمانیم|آرسنیک|سلنیم|برم|کریپتون|روبیدیم|استرانسیم|ایتریم|زیرکونیم|نیوبیم|مولیبدن|تکنسیم|روتنیم|رودیم|پالادیم|نقره|کادمیم|ایندیم|قلع|آنتیموان|تلوریم|ید|زنون|سزیم|باریم|لانتانیم|سریم|پرازئودیمیم|نئودیمیم|پرومتیم|ساماریم|یوروپیم|گادولینیم|تربیم|دیسپروزیم|هولمیم|اربیم|تولیم|ایتربیم|لوتتیم|هافنیم|تانتالیم|تنگستن|رنیم|اسمیم|ایریدیم|پلاتین|طلا|جیوه|تالیم|سرب|بیسموت|پولونیم|آستاتین|رادون|فرانسیم|رادیم|اکتینیم|توریم|پروتاکتینیم|اورانیم|نپتونیم|پلوتونیم|آمریسیم|کوریم|برکلیم|کالیفرنیم|اینشتینیم|فرمیم|مندلیفیم|نوبلیم|لارنسیم|رادرفوردیم|دوبنیم|سیبورگیم|بوریم|هاسیم|مایتنریم|دارمشتاتیم|رونتگنیم|کوپرنیسیم|نیهونیم|فلروویم|مسکوویم|لیورموریم|تنسین|اوگانسون".split('|')

    @Synchronized fun elements(c: Context): List<Element> {
        cached?.let { return it }
        val a = JSONArray(c.assets.open("science/elements.json").bufferedReader().use { it.readText() })
        return (0 until a.length()).map { i ->
            val n = i + 1
            val position = position(n)
            Element(
                a.getJSONObject(i),
                names[i],
                position.first,
                position.second,
                if (n in 57..71 || n in 89..103)0 else position.first + 1,
                when (n) {
                    in 1..2 -> 1
                    in 3..10 -> 2
                    in 11..18 -> 3
                    in 19..36 -> 4
                    in 37..54 -> 5
                    in 55..86 -> 6
                    else -> 7
                }
            )
        }.also {
            require(it.size == 118)
            cached = it
        }
    }
    fun position(n: Int): Pair<Int, Int> = when (n) {
        1 -> 0 to 0
        2 -> 17 to 0
        in 3..4 -> n - 3 to 1
        in 5..10 -> n + 7 to 1
        in 11..12 -> n - 11 to 2
        in 13..18 -> n - 1 to 2
        in 19..36 -> n - 19 to 3
        in 37..54 -> n - 37 to 4
        in 55..56 -> n - 55 to 5
        in 72..86 -> n - 69 to 5
        in 87..88 -> n - 87 to 6
        in 104..118 -> n - 101 to 6
        in 57..71 -> n - 55 to 8
        else -> n - 87 to 9
    }

    /** Family colors: soft enough for black text, distinct enough to see the blocks from the back of the class. */
    private val colors = mapOf("Nonmetal" to 0xffbfe8d8.toInt(), "Noble gas" to 0xffcfdcff.toInt(), "Alkali metal" to 0xffffc9a8.toInt(), "Alkaline earth metal" to 0xffffe1a3.toInt(), "Metalloid" to 0xffd5ecb5.toInt(), "Halogen" to 0xffb8e3e8.toInt(), "Transition metal" to 0xffe0d4f7.toInt(), "Post-transition metal" to 0xffd3dde8.toInt(), "Lanthanide" to 0xfff6cfe6.toInt(), "Actinide" to 0xffffc8cc.toInt())
    fun color(e: Element) = colors[e.value("GroupBlock")] ?: INK_PAPER
    /** The family color, deep and saturated: for card headers and cell numbers (plain darkening looked grey). */
    private fun darker(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = (hsv[1] * 2.6f).coerceIn(.45f, .85f)
        hsv[2] = .58f
        return Color.HSVToColor(hsv)
    }
    fun family(c: Context, key: String) = when (key) {
        "Nonmetal" -> c.tr(key, "نافلز")
        "Noble gas" -> c.tr(key, "گاز نجیب")
        "Alkali metal" -> c.tr(key, "فلز قلیایی")
        "Alkaline earth metal" -> c.tr(key, "قلیایی خاکی")
        "Metalloid" -> c.tr(key, "شبه‌فلز")
        "Halogen" -> c.tr(key, "هالوژن")
        "Transition metal" -> c.tr(key, "فلز واسطه")
        "Post-transition metal" -> c.tr(key, "فلز پس‌واسطه")
        "Lanthanide" -> c.tr(key, "لانتانید")
        "Actinide" -> c.tr(key, "اکتینید")
        else -> c.tr(key, "نامشخص")
    }

    /** The table on the board and in the viewer: one cell per element (number, symbol, name), group numbers on top. */
    fun draw(canvas: Canvas, o: Item, c: Context, persian: Boolean = c.resources.configuration.locales[0].language == "fa") {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cw = o.w / 18
        val ch = o.h / 11
        p.color = Color.WHITE
        canvas.drawRoundRect(0f, 0f, o.w, o.h, cw * .3f, cw * .3f, p)
        val r = cw * .12f
        val gap = cw * .045f
        elements(c).forEach { e ->
            val x = e.col * cw
            val y = (e.row + 1) * ch
            p.style = Paint.Style.FILL
            p.color = color(e)
            canvas.drawRoundRect(x + gap, y + gap, x + cw - gap, y + ch - gap, r, r, p)
            if (e.number == o.selectedElement) {
                p.style = Paint.Style.STROKE
                p.color = INK_NAVY
                p.strokeWidth = cw * .06f
                canvas.drawRoundRect(x + gap, y + gap, x + cw - gap, y + ch - gap, r, r, p)
                p.style = Paint.Style.FILL
            }
            p.color = darker(color(e))
            p.typeface = Typeface.DEFAULT
            p.textAlign = Paint.Align.LEFT
            p.textSize = cw * .17f
            canvas.drawText(e.number.toString(), x + cw * .1f, y + ch * .24f, p)
            p.color = INK_NAVY
            p.textAlign = Paint.Align.CENTER
            p.typeface = Typeface.DEFAULT_BOLD
            p.textSize = cw * .38f
            canvas.drawText(e.symbol, x + cw / 2, y + ch * .64f, p)
            p.typeface = Typeface.DEFAULT
            p.textSize = cw * .13f
            val name = if (persian) e.fa else e.name
            val fitted = if (p.measureText(name) > cw * .9f) TextUtilsShim.ellipsize(name, p, cw * .9f) else name
            canvas.drawText(fitted, x + cw / 2, y + ch * .88f, p)
            p.textAlign = Paint.Align.LEFT
        }
        p.textSize = ch * .24f
        p.color = INK_MUTED
        p.textAlign = Paint.Align.CENTER
        for (g in 1..18)canvas.drawText(g.toString(), (g - .5f) * cw, ch * .7f, p)
        p.textSize = ch * .2f
        canvas.drawText("57–71", 2.5f * cw, 6.62f * ch, p)
        canvas.drawText("89–103", 2.5f * cw, 7.62f * ch, p)
        p.textAlign = Paint.Align.LEFT
    }

    /** Ellipsizes a name to fit a cell without allocating a layout. */
    private object TextUtilsShim {
        fun ellipsize(text: String, p: Paint, width: Float): String {
            var end = text.length
            while (end > 1 && p.measureText(text, 0, end) + p.measureText("…") > width)end--
            return text.substring(0, end) + "…"
        }
    }

    /** The colored header of an element card on the board; the card's text starts below it. */
    fun cardHeader(o: Item) = 136f
    fun drawCard(canvas: Canvas, o: Item, c: Context) {
        val e = elements(c).firstOrNull { it.number == o.selectedElement } ?: return
        val persian = o.text.any { it in '\u0600'..'\u06ff' }
        val h = cardHeader(o) - 12
        val base = color(e)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = darker(base) }
        canvas.save()
        canvas.clipRect(0f, 0f, o.w, h)
        canvas.drawRoundRect(0f, 0f, o.w, h + 20f, 12f, 12f, p)
        canvas.restore()
        p.color = Color.WHITE
        p.typeface = Fonts.face(Fonts.en(o))
        p.textSize = 18f
        canvas.drawText(e.number.toString(), 16f, 30f, p)
        p.typeface = Fonts.face(Fonts.en(o), true)
        p.textSize = 64f
        canvas.drawText(e.symbol, 16f, 92f, p)
        val symbolEnd = 16f + p.measureText(e.symbol) + 18f
        p.typeface = Fonts.face(if (persian) Fonts.fa(o) else Fonts.en(o), true)
        p.textSize = 24f
        val name = if (persian) e.fa else e.name
        canvas.drawText(name, symbolEnd, 66f, p)
        p.typeface = Fonts.face(Fonts.en(o))
        p.textSize = 16f
        p.color = 0xddffffff.toInt()
        canvas.drawText(e.value("AtomicMass") + " u", symbolEnd, 94f, p)
        p.textSize = 14f
        canvas.drawText(family(c, e.value("GroupBlock")), 16f, h - 6f, p)
    }
    fun hit(c: Context, o: Item, x: Float, y: Float): Element? {
        val (lx, ly) = o.local(x, y)
        val col = (lx / (o.w / 18)).toInt()
        val row = (ly / (o.h / 11)).toInt() - 1
        return elements(c).firstOrNull { it.col == col && it.row == row && lx >= 0 && ly >= 0 && lx < o.w && ly < o.h }
    }
    private val examples = mapOf(
        1 to ("Water: H₂O" to "آب: H₂O"), 6 to ("Diamond and graphite are forms of carbon." to "الماس و گرافیت شکل‌هایی از کربن‌اند."), 7 to ("Nitrogen is a major component of air." to "نیتروژن بخش بزرگی از هوا را تشکیل می‌دهد."), 8 to ("Oxygen is involved in respiration; water contains oxygen." to "اکسیژن در تنفس نقش دارد و در آب هم وجود دارد."), 11 to ("Table salt: NaCl" to "نمک خوراکی: NaCl"), 13 to ("Aluminium is used in cans and light alloys." to "آلومینیوم در قوطی‌ها و آلیاژهای سبک کاربرد دارد."), 14 to ("Silicon is used in semiconductor chips." to "سیلیسیم در تراشه‌های نیمه‌رسانا کاربرد دارد."), 17 to ("Chloride is present in table salt: NaCl." to "یون کلرید در نمک خوراکی NaCl وجود دارد."), 20 to ("Calcium carbonate: CaCO₃" to "کربنات کلسیم: CaCO₃"), 26 to ("Iron is a principal component of steel." to "آهن جزء اصلی فولاد است."), 29 to ("Copper is used in electrical wiring." to "مس در سیم‌کشی برق کاربرد دارد."), 47 to ("Silver is used in jewellery and electrical contacts." to "نقره در زیورآلات و اتصال‌های الکتریکی کاربرد دارد."), 79 to ("Gold is used in jewellery and electronic contacts." to "طلا در زیورآلات و اتصال‌های الکترونیکی کاربرد دارد.")
    )
    fun details(c: Context, e: Element): String {
        val phase = when (e.value("StandardState")) {
            "Gas" -> c.tr("Gas", "گاز")
            "Liquid" -> c.tr("Liquid", "مایع")
            "Solid" -> c.tr("Solid", "جامد")
            else -> c.tr("Not established", "تعیین نشده")
        }
        val example = examples[e.number]?.let { c.tr(it.first, it.second) } ?: c.tr("Compare its family, period and electron configuration with neighbouring elements.", "خانواده، دوره و آرایش الکترونی آن را با عناصر همسایه مقایسه کنید.")
        return "${e.symbol} — ${c.tr(e.name,e.fa)}\n" +
            c.tr("Atomic number", "عدد اتمی") + ": ${e.number}\n" + c.tr("Atomic mass (u)", "جرم اتمی (u)") + ": ${e.value("AtomicMass")}\n" +
            c.tr("Group / period", "گروه / دوره") + ": ${if (e.group == 0)"f-block" else e.group} / ${e.period}\n" +
            family(c, e.value("GroupBlock")) + " · " + phase + "\n" + c.tr("Electron configuration", "آرایش الکترونی") + ": ${e.value("ElectronConfiguration")}\n" +
            c.tr("Oxidation states", "عددهای اکسایش") + ": ${e.value("OxidationStates")}\n" +
            c.tr("Electronegativity (Pauling)", "الکترونگاتیوی (پائولینگ)") + ": ${e.value("Electronegativity")}\n\n$example"
    }

    /** What an element card says under its header: short enough to read from the back of the class. */
    fun card(c: Context, e: Element) = Item(kind = "sticky", shape = "element_card", selectedElement = e.number, text = cardText(c, e), width = 15f, w = 320f, h = 300f, noteColor = color(e))
    fun cardText(c: Context, e: Element): String {
        val phase = when (e.value("StandardState")) {
            "Gas" -> c.tr("Gas", "گاز")
            "Liquid" -> c.tr("Liquid", "مایع")
            "Solid" -> c.tr("Solid", "جامد")
            else -> c.tr("State not established", "حالت تعیین‌نشده")
        }
        val example = examples[e.number]?.let { c.tr(it.first, it.second) }
        return listOfNotNull(
            c.tr("Group ${if (e.group == 0) "f" else e.group.toString()} · period ${e.period} · $phase", "گروه ${if (e.group == 0) "f" else e.group.toString()} · دورهٔ ${e.period} · $phase"),
            c.tr("Electrons: ", "آرایش الکترونی: ") + e.value("ElectronConfiguration"),
            example
        ).joinToString("\n")
    }

    /** Facts as label/value rows, the header drawn like the board card. */
    private fun panel(c: Context, e: Element, add: (Item) -> Unit): View {
        val body = c.column()
        val header = object : View(c) {
            override fun onDraw(canvas: Canvas) {
                val o = Item(kind = "sticky", shape = "element_card", selectedElement = e.number, w = width / resources.displayMetrics.density, text = if (c.resources.configuration.locales[0].language == "fa") e.fa else "")
                canvas.save()
                canvas.scale(resources.displayMetrics.density, resources.displayMetrics.density)
                drawCard(canvas, o, c)
                canvas.restore()
            }
        }
        body.addView(header, LinearLayout.LayoutParams(-1, c.dp(124)))
        val phase = when (e.value("StandardState")) {
            "Gas" -> c.tr("Gas", "گاز")
            "Liquid" -> c.tr("Liquid", "مایع")
            "Solid" -> c.tr("Solid", "جامد")
            else -> "—"
        }
        listOf(
            c.tr("Family", "خانواده") to family(c, e.value("GroupBlock")),
            c.tr("Group / period", "گروه / دوره") to "${if (e.group == 0) "f" else e.group} / ${e.period}",
            c.tr("State at room temperature", "حالت در دمای اتاق") to phase,
            c.tr("Electron configuration", "آرایش الکترونی") to e.value("ElectronConfiguration"),
            c.tr("Oxidation states", "عددهای اکسایش") to e.value("OxidationStates"),
            c.tr("Electronegativity", "الکترونگاتیوی") to e.value("Electronegativity")
        ).forEach { (k, v) ->
            body.addView(
                c.row().apply {
                    setPadding(0, c.dp(6), 0, c.dp(6))
                    addView(c.label(k, 13f, MUTED), LinearLayout.LayoutParams(0, -2, 1f))
                    addView(c.label(v, 14f, NAVY, true).apply { textDirection = View.TEXT_DIRECTION_LTR.takeIf { k.contains("onfig") || v.any { it.isDigit() } } ?: View.TEXT_DIRECTION_LOCALE }, LinearLayout.LayoutParams(0, -2, 1.3f))
                }
            )
        }
        examples[e.number]?.let { body.addView(c.label(c.tr(it.first, it.second), 14f, NAVY).apply { setPadding(0, c.dp(8), 0, c.dp(4)) }) }
        val compare = Spinner(c)
        compare.adapter = OptionAdapter(c, elements(c).map { "${it.symbol} · ${c.tr(it.name, it.fa)}" })
        compare.setSelection((e.number % 118))
        body.addView(
            c.row().apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(compare, LinearLayout.LayoutParams(0, c.dp(48), 1f))
                addView(
                    c.button(c.tr("Compare", "مقایسه")) {
                        val other = elements(c)[compare.selectedItemPosition]
                        val r = c.row()
                        listOf(e, other).forEach { r.addView(c.label(details(c, it), 14f), LinearLayout.LayoutParams(0, -2, 1f)) }
                        MaterialAlertDialogBuilder(c).setTitle(c.tr("Element comparison", "مقایسهٔ عناصر")).setView(ScrollView(c).apply { addView(r) }).setPositiveButton(c.s("close"), null).show().also { Fonts.onShown(it) }
                    },
                    LinearLayout.LayoutParams(-2, c.dp(48)).apply { marginStart = c.dp(8) }
                )
            },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = c.dp(8) }
        )
        body.addView(c.button(c.tr("Add element card to board", "افزودن کارت عنصر به تخته"), true) { add(card(c, e)) }, LinearLayout.LayoutParams(-1, c.dp(52)).apply { topMargin = c.dp(8) })
        return body
    }
    fun elementDialog(c: Context, e: Element, insert: (Item) -> Unit) {
        var close: () -> Unit = {}
        val body = c.column().apply { pad(16) }
        body.addView(
            panel(c, e) {
                insert(it)
                close()
            }
        )
        MaterialAlertDialogBuilder(c).setTitle("${e.symbol} · ${c.tr(e.name, e.fa)}").setView(ScrollView(c).apply { addView(body) }).setNegativeButton(c.s("close"), null).show().also {
            close = it::dismiss
            Fonts.onShown(it)
        }
    }
    fun show(c: Context, insert: (Item) -> Unit) = show(c, insert, null)

    /**
     * Full screen: the whole table fits the width (no sideways scrolling), the tapped element opens beside it,
     * search filters as you type, and the actions sit at the bottom.
     */
    fun show(c: Context, insert: (Item) -> Unit, back: (() -> Unit)?) {
        val d = android.app.Dialog(c, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        val add: (Item) -> Unit = {
            insert(it)
            d.dismiss()
        }
        val o = Item(kind = "periodic", w = 900f, h = 550f)
        val side = c.column().apply { setPadding(c.dp(16), c.dp(4), c.dp(4), c.dp(4)) }
        fun select(e: Element) {
            o.selectedElement = e.number
            side.removeAllViews()
            side.addView(panel(c, e, add))
        }
        val table = object : View(c) {
            override fun onMeasure(w: Int, h: Int) {
                val width = MeasureSpec.getSize(w).takeIf { MeasureSpec.getMode(w) != MeasureSpec.UNSPECIFIED } ?: c.dp(900)
                val byWidth = width * 11 / 18
                val height = if (MeasureSpec.getMode(h) == MeasureSpec.UNSPECIFIED) byWidth else minOf(byWidth, MeasureSpec.getSize(h))
                setMeasuredDimension(minOf(width, height * 18 / 11), height)
            }
            override fun onDraw(canvas: Canvas) {
                canvas.save()
                canvas.scale(width / o.w, height / o.h)
                draw(canvas, o, c)
                canvas.restore()
            }
            override fun onTouchEvent(e: MotionEvent): Boolean {
                if (e.actionMasked == MotionEvent.ACTION_UP) {
                    hit(c, o, e.x * o.w / width, e.y * o.h / height)?.let {
                        select(it)
                        invalidate()
                    }
                    performClick()
                }
                return true
            }
            override fun performClick(): Boolean {
                super.performClick()
                return true
            }
        }
        val root = c.column().apply {
            setBackgroundColor(com.vuravision.classroom.ui.PAPER)
            fitsSystemWindows = true
            setPadding(c.dp(20), c.dp(12), c.dp(20), c.dp(10))
        }
        root.addView(
            c.navigationHeading(
                c.s("periodic"),
                back?.let { action ->
                    {
                        d.dismiss()
                        action()
                    }
                }
            )
        )
        val middle = c.row().apply { gravity = Gravity.TOP }
        val left = c.column()
        left.addView(table, LinearLayout.LayoutParams(-1, -2))
        val legend = com.vuravision.classroom.ui.FlowRow(c, c.dp(10)).apply { setPadding(0, c.dp(8), 0, 0) }
        colors.forEach { (family, color) ->
            legend.addView(
                c.row().apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(View(c).apply { background = com.vuravision.classroom.ui.rounded(color, c.dp(6).toFloat()) }, LinearLayout.LayoutParams(c.dp(14), c.dp(14)).apply { marginEnd = c.dp(4) })
                    addView(c.label(family(c, family), 12f, MUTED))
                }
            )
        }
        left.addView(legend)
        middle.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        side.addView(c.label(c.tr("Tap an element to see it here.", "روی یک عنصر بزنید تا اینجا نمایش داده شود."), 15f, MUTED).apply { setPadding(0, c.dp(24), 0, 0) })
        middle.addView(ScrollView(c).apply { addView(side) }, LinearLayout.LayoutParams(c.dp(330), -1))
        root.addView(ScrollView(c).apply { addView(middle) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val search = c.field(hintValue = c.tr("Find: name, symbol or number", "یافتن: نام، نماد یا عدد اتمی")).apply {
            setSingleLine(true)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, n: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, n: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val q = s.toString().trim()
                    if (q.isEmpty())return
                    val found = elements(c).firstOrNull { it.symbol.equals(q, true) || it.number.toString() == q || it.fa == q || it.name.equals(q, true) }
                        ?: elements(c).firstOrNull { it.name.startsWith(q, true) || it.fa.startsWith(q) }
                    if (found != null) {
                        select(found)
                        table.invalidate()
                    }
                }
            })
        }
        root.addView(
            c.row().apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(search, LinearLayout.LayoutParams(0, -2, 1f))
                addView(c.button(c.tr("Add interactive table to board", "افزودن جدول تعاملی به تخته"), true) { add(o.deepCopy()) }, LinearLayout.LayoutParams(-2, c.dp(52)).apply { marginStart = c.dp(8) })
                addView(
                    c.button(c.tr("Add complete table as image", "افزودن تصویر کامل جدول به تخته")) {
                        val bitmap = Bitmap.createBitmap(1800, 1100, Bitmap.Config.ARGB_8888)
                        val media = Media(c)
                        try {
                            draw(Canvas(bitmap), Item(kind = "periodic", w = 1800f, h = 1100f), c)
                            val asset = media.save(bitmap)
                            add(Item(kind = "image", asset = asset, w = 900f, h = 550f))
                        } finally {
                            bitmap.recycle()
                            media.close()
                        }
                    },
                    LinearLayout.LayoutParams(-2, c.dp(52)).apply { marginStart = c.dp(8) }
                )
                addView(c.button(c.s("close")) { d.dismiss() }, LinearLayout.LayoutParams(-2, c.dp(52)).apply { marginStart = c.dp(8) })
            },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = c.dp(8) }
        )
        root.addView(c.label("PubChem / NCBI · " + c.tr("missing values are shown as —", "مقادیر نامعلوم با — نشان داده می‌شوند"), 11f, MUTED))
        d.setContentView(root)
        d.show()
        Fonts.applyTree(root)
    }
}
