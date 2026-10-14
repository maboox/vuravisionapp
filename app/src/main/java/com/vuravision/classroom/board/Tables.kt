package com.vuravision.classroom.board

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.vuravision.classroom.R
import com.vuravision.classroom.layoutDirection
import com.vuravision.classroom.ui.INK_NAVY
import com.vuravision.classroom.ui.INK_PAPER
import com.vuravision.classroom.ui.button
import com.vuravision.classroom.ui.column
import com.vuravision.classroom.ui.dp
import com.vuravision.classroom.ui.field
import com.vuravision.classroom.ui.label
import com.vuravision.classroom.ui.row
import com.vuravision.classroom.ui.s
import com.vuravision.classroom.ui.tr

/**
 * Tables on the board (item 39): a shape whose cells live in its text, rows by line and cells by tab, so a table
 * saves, copies, undoes and syncs like any other object. The first row is the header (tinted, bold).
 */
object Tables {
    const val MAX = 12
    fun cells(o: Item): List<List<String>> {
        val rows = o.text.split('\n').map { it.split('\t') }
        val columns = rows.maxOf { it.size }.coerceIn(1, MAX)
        return rows.take(MAX).map { r -> List(columns) { r.getOrElse(it) { "" } } }
    }
    fun encode(rows: List<List<String>>) = rows.joinToString("\n") { r -> r.joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') } }
    fun create(rows: Int, columns: Int, x: Float, y: Float, layer: String, pane: Int) = Item(kind = "shape", shape = "table", text = encode(List(rows) { List(columns) { "" } }), x = x, y = y, w = columns * 200f, h = rows * 72f, width = 2f, layerId = layer, pane = pane)
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
    fun draw(c: Canvas, o: Item, p: Paint) {
        val rows = cells(o)
        val n = rows.size
        val m = rows.first().size
        val cw = o.w / m
        val rh = o.h / n
        p.style = Paint.Style.FILL
        p.color = 0x2621918c
        c.drawRect(0f, 0f, o.w, rh, p)
        p.style = Paint.Style.STROKE
        p.color = o.color
        p.alpha = o.alpha
        c.drawRect(0f, 0f, o.w, o.h, p)
        for (i in 1 until n)c.drawLine(0f, i * rh, o.w, i * rh, p)
        for (j in 1 until m)c.drawLine(j * cw, 0f, j * cw, o.h, p)
        text.color = o.color
        text.alpha = o.alpha
        text.textSize = minOf(rh * .42f, cw * .22f, 40f).coerceAtLeast(8f)
        val ascent = text.fontMetrics.let { (it.ascent + it.descent) / 2 }
        rows.forEachIndexed { i, row ->
            row.forEachIndexed { j, cell ->
                if (cell.isEmpty())return@forEachIndexed
                text.typeface = if (i == 0)Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                val shown = TextUtils.ellipsize(cell, text, cw - 16f, TextUtils.TruncateAt.END).toString()
                // Right-to-left tables (made in Persian) put the first column on the right.
                val col = if (o.flipX)m - 1 - j else j
                c.drawText(shown, col * cw + cw / 2 - text.measureText(shown) / 2, i * rh + rh / 2 - ascent, text)
            }
        }
    }

    /** Every cell as a field; rows and columns are added or removed here, and the table keeps its cell size. */
    fun editor(c: Context, o: Item, save: (List<List<String>>) -> Unit) {
        val d = Dialog(c, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        var rows = cells(o).map { it.toMutableList() }.toMutableList()
        val grid = c.column().apply { layoutDirection = if (o.flipX)android.view.View.LAYOUT_DIRECTION_RTL else android.view.View.LAYOUT_DIRECTION_LTR }
        val fields = mutableListOf<MutableList<EditText>>()
        fun collect() {
            fields.forEachIndexed { i, r -> r.forEachIndexed { j, f -> rows[i][j] = f.text.toString() } }
        }
        fun build() {
            grid.removeAllViews()
            fields.clear()
            rows.forEachIndexed { i, r ->
                val line = c.row()
                val list = mutableListOf<EditText>()
                r.forEachIndexed { j, v ->
                    val f = c.field(v, if (i == 0)c.tr("Heading ${j + 1}", "عنوان ${j + 1}")else "").apply {
                        setSingleLine(true)
                        if (i == 0)setTypeface(typeface, Typeface.BOLD)
                    }
                    list.add(f)
                    line.addView(f, LinearLayout.LayoutParams(c.dp(180), -2).apply { setMargins(c.dp(3), c.dp(3), c.dp(3), c.dp(3)) })
                }
                fields.add(list)
                grid.addView(line)
            }
        }
        build()
        val root = c.column().apply {
            setBackgroundColor(INK_PAPER)
            fitsSystemWindows = true
            setPadding(c.dp(20), c.dp(12), c.dp(20), c.dp(10))
        }
        root.addView(c.label(c.tr("Table", "جدول"), 24f, INK_NAVY, true))
        root.addView(
            c.row().apply {
                fun act(label: String, run: () -> Unit) = addView(
                    c.button(label) {
                        collect()
                        run()
                        build()
                    },
                    LinearLayout.LayoutParams(-2, c.dp(52)).apply { marginEnd = c.dp(8) }
                )
                act(c.tr("+ Row", "+ ردیف")) { if (rows.size < MAX)rows.add(MutableList(rows.first().size) { "" }) }
                act(c.tr("− Row", "− ردیف")) { if (rows.size > 1)rows.removeAt(rows.lastIndex) }
                act(c.tr("+ Column", "+ ستون")) { if (rows.first().size < MAX)rows.forEach { it.add("") } }
                act(c.tr("− Column", "− ستون")) { if (rows.first().size > 1)rows.forEach { it.removeAt(it.lastIndex) } }
            },
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = c.dp(8)
                bottomMargin = c.dp(8)
            }
        )
        root.addView(ScrollView(c).apply { addView(HorizontalScrollView(c).apply { addView(grid) }) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(
            c.row().apply {
                gravity = android.view.Gravity.END
                addView(c.button(c.s("cancel")) { d.dismiss() }, LinearLayout.LayoutParams(c.dp(140), c.dp(56)).apply { marginEnd = c.dp(8) })
                addView(
                    c.button(c.tr("Save", "ذخیره"), true) {
                        collect()
                        save(rows)
                        d.dismiss()
                    },
                    LinearLayout.LayoutParams(c.dp(160), c.dp(56))
                )
            }
        )
        d.setContentView(root)
        d.show()
    }
}
