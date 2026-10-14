package com.vuravision.classroom.board

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.vuravision.classroom.layoutDirection
import com.vuravision.classroom.ui.INK_MUTED
import com.vuravision.classroom.ui.NAVY
import com.vuravision.classroom.ui.OUTLINE
import com.vuravision.classroom.ui.SURFACE
import com.vuravision.classroom.ui.WorkspaceIcon
import com.vuravision.classroom.ui.dp
import com.vuravision.classroom.ui.label
import com.vuravision.classroom.ui.rounded
import com.vuravision.classroom.ui.row
import com.vuravision.classroom.ui.s
import com.vuravision.classroom.ui.tr

/**
 * Frames (item 62): named areas of one large board. A lesson is laid out on a big canvas, then shown frame by frame:
 * each step zooms the board to exactly that frame, and the teacher can still write on it.
 */
object Frames {
    fun of(page: Page, pane: Int) = page.items.filter { it.shape == "frame" && it.pane == pane }
    fun create(x: Float, y: Float, w: Float, h: Float, title: String, layer: String, pane: Int) = Item(kind = "shape", shape = "frame", text = title, x = x, y = y, w = w, h = h, width = 3f, color = INK_MUTED, layerId = layer, pane = pane)
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    fun draw(c: Canvas, o: Item, p: Paint) {
        p.style = Paint.Style.STROKE
        p.color = o.color
        p.alpha = o.alpha
        p.strokeWidth = o.width
        p.pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
        c.drawRoundRect(0f, 0f, o.w, o.h, 16f, 16f, p)
        p.pathEffect = null
        if (o.text.isNotBlank()) {
            title.color = o.color
            title.alpha = o.alpha
            title.textSize = 24f
            c.drawText(o.text, 8f, -12f, title)
        }
    }
}

/** The frame-by-frame bar: back, title with "2 / 5", next, close; at the bottom like every presenting control. */
@SuppressLint("ViewConstructor")
class FramePresenter(context: Context, private val frames: List<Item>, private val show: (Item) -> Unit, private val close: () -> Unit) : FrameLayout(context) {
    var index = 0
        private set
    private val caption: TextView = context.label("", 17f, NAVY, true).apply {
        gravity = Gravity.CENTER
        maxLines = 1
    }
    init {
        val bar = context.row().apply {
            background = rounded(SURFACE, context.dp(24).toFloat(), OUTLINE)
            setPadding(context.dp(6), context.dp(6), context.dp(6), context.dp(6))
            elevation = context.dp(6).toFloat()
            gravity = Gravity.CENTER_VERTICAL
        }
        val rtl = context.resources.configuration.layoutDirection == LAYOUT_DIRECTION_RTL
        bar.addView(WorkspaceIcon(context, if (rtl)"next" else "menu_back", context.tr("Previous frame", "قاب قبلی")) { go(index - 1) }, LinearLayout.LayoutParams(context.dp(56), context.dp(56)))
        bar.addView(caption, LinearLayout.LayoutParams(context.dp(260), -2))
        bar.addView(WorkspaceIcon(context, if (rtl)"menu_back" else "next", context.tr("Next frame", "قاب بعدی")) { go(index + 1) }, LinearLayout.LayoutParams(context.dp(56), context.dp(56)))
        bar.addView(WorkspaceIcon(context, "close", context.s("close")) { close() }, LinearLayout.LayoutParams(context.dp(56), context.dp(56)).apply { marginStart = context.dp(8) })
        addView(bar, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = context.dp(24) })
        go(0)
    }
    fun go(i: Int) {
        if (frames.isEmpty())return
        index = i.coerceIn(0, frames.lastIndex)
        val f = frames[index]
        caption.text = "${f.text.ifBlank{context.tr("Frame","قاب")}} · ${index + 1} / ${frames.size}"
        show(f)
    }

    /** Only the bar takes touches; the board underneath stays writable while presenting. */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent) = false
}
