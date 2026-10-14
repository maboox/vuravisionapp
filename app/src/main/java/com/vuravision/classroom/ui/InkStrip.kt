package com.vuravision.classroom.ui

import android.content.Context
import android.graphics.*
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import com.vuravision.classroom.board.Board
import com.vuravision.classroom.layoutDirection

/**
 * One-tap ink color and width.
 * - Bar form (bottom of the PDF view): optional pen/highlighter/eraser, then colors and widths; wraps when narrow.
 * - Grid form (beside the dock): 3×3 — six colors, three widths — plus "More pen settings". About 150dp wide,
 *   so it fits beside the dock even on a phone in portrait, and every option is visible without scrolling.
 */
class InkStrip(context: Context, private val board: () -> Board, showTools: Boolean = true, grid: Boolean = false, more: (() -> Unit)? = null, private val changed: () -> Unit) : LinearLayout(context) {
    companion object {
        // Dark ink, blue, red, green, orange, purple: distinct on white, green chalkboard and dark backgrounds.
        val colors = listOf(0xff1f2430.toInt(), 0xff2563eb.toInt(), 0xffdc2626.toInt(), 0xff16a34a.toInt(), 0xfff59e0b.toInt(), 0xff7c3aed.toInt())
        val penWidths = listOf(2f, 4f, 8f)
        val highlightWidths = listOf(4f, 6f, 10f)
    }
    private val tools = (if (showTools)listOf("pen", "highlight", "erase")else emptyList()).map { key ->
        ActionIcon(
            context,
            key,
            when (key) {
                "pen" -> context.tr("Pen", "قلم")
                "highlight" -> context.tr("Highlighter", "هایلایتر")
                else -> context.tr("Eraser", "پاک‌کن")
            }
        ) {
            board().apply {
                tool = key
                clearSelection()
            }
            changed()
            refresh()
        }
    }
    private val swatches = colors.map { color ->
        Swatch(context, color, null) {
            board().inkColor = color
            changed()
            refresh()
        }
    }
    private val widths = penWidths.indices.map { i ->
        Swatch(context, null, i) {
            val b = board()
            b.inkWidth = (if (b.tool == "highlight")highlightWidths else penWidths)[i]
            changed()
            refresh()
        }
    }
    init {
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        if (grid) {
            orientation = VERTICAL
            val cells = GridLayout(context).apply { columnCount = 3 }
            (swatches + widths).forEach {
                cells.addView(
                    it,
                    GridLayout.LayoutParams().apply {
                        width = context.dp(48)
                        height = context.dp(48)
                    }
                )
            }
            addView(cells)
            if (more != null) {
                addView(
                    context.label(context.tr("More pen settings", "تنظیمات بیشتر قلم"), 14f, NAVY, true).apply {
                        gravity = Gravity.CENTER
                        minHeight = context.dp(44)
                        isClickable = true
                        isFocusable = true
                        background = rounded(SURFACE_VARIANT, context.dp(10).toFloat())
                        setOnClickListener { more() }
                    },
                    LayoutParams(-1, -2).apply { topMargin = context.dp(6) }
                )
            }
        } else {
            // Bar form: one centered line that wraps instead of scrolling, so every option stays visible.
            orientation = VERTICAL
            val flow = FlowRow(context)
            tools.forEach { flow.addView(it, LayoutParams(context.dp(48), context.dp(48))) }
            (swatches + widths).forEach { flow.addView(it, LayoutParams(context.dp(44), context.dp(48))) }
            addView(flow, LayoutParams(-1, -2))
        }
        refresh()
    }
    fun refresh() {
        val b = board()
        tools.forEach { it.alpha = if (it.symbol == b.tool)1f else .55f }
        swatches.forEach {
            it.chosen = it.color == b.inkColor
            it.ink = b.inkColor
            it.invalidate()
        }
        val sizes = if (b.tool == "highlight")highlightWidths else penWidths
        widths.forEachIndexed { i, w ->
            w.chosen = kotlin.math.abs(sizes[i] - b.inkWidth) < .01f
            w.ink = b.inkColor
            w.invalidate()
        }
    }

    /** One color or width option; also used in the selection bar's color mode. */
    class Swatch(context: Context, val color: Int?, val size: Int?, action: () -> Unit) : View(context) {
        var chosen = false
        var ink = Color.BLACK
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        init {
            isClickable = true
            isFocusable = true
            contentDescription = if (color != null)context.tr("Ink color", "رنگ قلم")else context.tr("Ink width", "ضخامت قلم")
            setOnClickListener { action() }
        }
        override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(resolveSize(context.dp(44), w), resolveSize(context.dp(48), h))
        override fun onDraw(c: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            // The chosen option gets a navy ring: visible on every swatch color, including dark ink.
            if (chosen) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = context.dp(2).toFloat()
                paint.color = NAVY
                c.drawCircle(cx, cy, context.dp(19).toFloat(), paint)
            }
            paint.style = Paint.Style.FILL
            if (color != null) {
                paint.color = color
                c.drawCircle(cx, cy, context.dp(14).toFloat(), paint)
                // White and pale colors get a thin outline, or they vanish on the white bar.
                if (Color.luminance(color) > .8f) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = context.dp(1).toFloat()
                    paint.color = OUTLINE_STRONG
                    c.drawCircle(cx, cy, context.dp(14).toFloat(), paint)
                }
            }
            // Widths are previewed as dots of increasing size in the current ink color.
            else {
                paint.color = if (Color.luminance(ink) > .8f)NAVY else ink
                c.drawCircle(cx, cy, context.dp(3 + 4 * (size ?: 0)).toFloat(), paint)
            }
        }
    }
}
