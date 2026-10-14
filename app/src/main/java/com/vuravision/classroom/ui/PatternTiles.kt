package com.vuravision.classroom.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import com.vuravision.classroom.board.Page
import com.vuravision.classroom.board.Pane
import com.vuravision.classroom.board.Renderer
import com.vuravision.classroom.board.reset
import com.vuravision.classroom.pick

/**
 * Page patterns picked by sight: each is a small live circle in the chosen background color, a little larger than the
 * color swatches beside it, with a one-line name below. Names like "isometric" or "staff" mean little to many teachers; the picture does.
 */
@SuppressLint("ViewConstructor")
class PatternPreview(context: Context, private val renderer: Renderer, val key: String, private val color: () -> Int) : View(context) {
    private val clip = Path()
    private val ring = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE }
    var chosen = false
        set(value) {
            field = value
            invalidate()
        }
    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(cx, cy) - context.dp(4)
        clip.reset()
        clip.addCircle(cx, cy, r, Path.Direction.CW)
        c.save()
        c.clipPath(clip)
        // One board unit per dp, as on the board at 100%: lines as crisp and as dense as the teacher will see them.
        val s = context.resources.displayMetrics.density
        c.translate(cx, cy)
        c.scale(s, s)
        val page = Page(background = key, panes = mutableListOf(Pane(background = color(), pattern = key)))
        // Centered on the origin, so "Axes" shows its cross and not just a corner.
        val u = r / s
        renderer.background(c, page, RectF(-u, -u, u, u), color())
        c.restore()
        ring.color = if (chosen) TEAL else OUTLINE_STRONG
        ring.strokeWidth = context.dp(if (chosen) 3 else 1).toFloat()
        c.drawCircle(cx, cy, r + if (chosen) context.dp(2) else 0, ring)
    }
}

/** A wrapping row of pattern circles; [pick] gets the key. Call [PatternGrid.refresh] after the color changes. */
class PatternGrid(context: Context, renderer: Renderer, choices: List<Pair<String, String>>, selected: String, columns: Int, color: () -> Int, pick: (String) -> Unit) : GridLayout(context) {
    private val previews = mutableListOf<PatternPreview>()
    var selected = selected
        private set
    init {
        columnCount = columns
        choices.forEach { (key, title) ->
            val preview = PatternPreview(context, renderer, key, color)
            previews.add(preview)
            val cell = context.column().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                isClickable = true
                isFocusable = true
                contentDescription = title
                tooltipText = title
                addView(preview, LinearLayout.LayoutParams(context.dp(52), context.dp(52)))
                addView(
                    context.label(title, 11f, MUTED).apply {
                        gravity = Gravity.CENTER
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    LinearLayout.LayoutParams(context.dp(60), -2)
                )
                setOnClickListener {
                    this@PatternGrid.selected = key
                    mark()
                    pick(key)
                }
            }
            addView(cell, LayoutParams().apply { setMargins(context.dp(2), context.dp(2), context.dp(2), context.dp(4)) })
        }
        mark()
    }
    private fun mark() = previews.forEach {
        it.chosen = it.key == selected
        (it.parent as View).isSelected = it.chosen
    }
    fun refresh() = previews.forEach { it.invalidate() }
}

/** The three board styles as circles like the patterns: the board color with a stroke of its default chalk or ink. */
fun Context.boardStyleRow(selected: String?, pick: (String) -> Unit): View {
    val row = row()
    val cells = mutableListOf<Pair<String, View>>()
    var chosen = selected
    listOf(
        Triple("white", tr("Whiteboard", "وایت‌برد"), android.graphics.Color.WHITE to INK_NAVY),
        Triple("chalk", tr("Chalkboard", "تخته‌سبز"), CHALKBOARD to 0xfff4f4f0.toInt()),
        Triple("dark", tr("Dark", "تیره"), DARK_BOARD to 0xfffff176.toInt())
    ).forEach { (key, title, colors) ->
        val circle = object : View(this) {
            private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            private val stroke = Path()
            override fun onDraw(c: Canvas) {
                val cx = width / 2f
                val cy = height / 2f
                val r = minOf(cx, cy) - dp(4)
                p.style = android.graphics.Paint.Style.FILL
                p.color = colors.first
                c.drawCircle(cx, cy, r, p)
                p.style = android.graphics.Paint.Style.STROKE
                p.strokeCap = android.graphics.Paint.Cap.ROUND
                p.strokeWidth = dp(3).toFloat()
                p.color = colors.second
                stroke.reset()
                stroke.moveTo(cx - r * .5f, cy + r * .1f)
                stroke.cubicTo(cx - r * .2f, cy - r * .45f, cx + r * .05f, cy + r * .45f, cx + r * .5f, cy - r * .15f)
                c.drawPath(stroke, p)
                val on = chosen == key
                p.strokeWidth = dp(if (on) 3 else 1).toFloat()
                p.color = if (on) TEAL else OUTLINE_STRONG
                c.drawCircle(cx, cy, r + if (on) dp(2) else 0, p)
            }
        }
        val cell = column().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
            isFocusable = true
            contentDescription = title
            addView(circle, LinearLayout.LayoutParams(dp(52), dp(52)))
            addView(
                label(title, 11f, MUTED).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(dp(64), -2)
            )
            setOnClickListener {
                chosen = key
                cells.forEach { (_, v) -> v.invalidate() }
                pick(key)
            }
        }
        cells.add(key to circle)
        row.addView(cell, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(4) })
    }
    return row
}
