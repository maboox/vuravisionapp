package com.vuravision.classroom.tools

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.vuravision.classroom.board.Item
import com.vuravision.classroom.board.Page
import com.vuravision.classroom.board.Renderer
import com.vuravision.classroom.board.contentBounds
import com.vuravision.classroom.ui.INK_OUTLINE
import com.vuravision.classroom.ui.INK_PAPER
import com.vuravision.classroom.ui.INK_SURFACE
import com.vuravision.classroom.ui.INK_TEAL
import com.vuravision.classroom.ui.WorkspaceIcon
import com.vuravision.classroom.ui.button
import com.vuravision.classroom.ui.dp
import com.vuravision.classroom.ui.rounded
import com.vuravision.classroom.ui.row
import com.vuravision.classroom.ui.s
import com.vuravision.classroom.ui.tr
import kotlin.math.min

/**
 * Replays the page as it was built: every object on the page now, in the order it was added (strokes drawn again,
 * other objects fading in one by one). It works from the page itself, by action and not by clock, so what was erased
 * is not replayed and pauses while teaching are skipped. Read only: the page itself is never touched.
 * Kept simple on purpose: pause, speed, a step back or forward, and from the start; at most [MAX_MS] at 1×.
 */
@SuppressLint("ViewConstructor")
class Replay(context: Context, source: Page, pane: Int, private val renderer: Renderer, private val close: () -> Unit) : FrameLayout(context) {
    companion object {
        const val MAX_MS = 60_000.0
        const val STEP_MS = 5_000.0
    }
    private val page = source.copied().let { p -> p.copy(items = p.items.filter { it.pane == pane }.map { it.copy(pane = 0) }.toMutableList(), panes = mutableListOf(p.panes.getOrElse(pane) { p.panes.first() }.copy()), paneCount = 1, alternateCanvas = null, background = p.pattern(pane)) }
    private val order = page.visibleItems()

    /** Each item's share of the replay: strokes by their length, everything else a short fade-in. */
    private val durations = order.map { if (it.kind == "ink")(it.points.size * 12.0).coerceIn(180.0, 1600.0)else 450.0 }
    private val scale = min(1.0, MAX_MS / durations.sum().coerceAtLeast(1.0))
    private val total = durations.sum() * scale
    var position = 0.0
        private set
    var speed = 1.0
        private set
    var playing = true
        private set
    private var last = 0L
    private val bounds = contentBounds(order).apply { inset(-40f, -40f) }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x3320194f }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK_TEAL }
    private lateinit var playButton: android.widget.Button
    private lateinit var speedButton: android.widget.Button
    init {
        setWillNotDraw(false)
        isClickable = true
        contentDescription = context.tr("Replay", "بازپخش")
        setBackgroundColor(INK_PAPER)
        val bar = context.row().apply {
            background = rounded(INK_SURFACE, context.dp(24).toFloat(), INK_OUTLINE)
            setPadding(context.dp(6), context.dp(6), context.dp(6), context.dp(6))
            elevation = context.dp(6).toFloat()
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = android.view.View.LAYOUT_DIRECTION_LTR
        }
        playButton = context.button(context.s("pause"), true) { toggle() }
        speedButton = context.button("1×") {
            speed = when (speed) {
                1.0 -> 2.0
                2.0 -> 4.0
                else -> 1.0
            }
            speedButton.text = "${speed.toInt()}×"
        }.apply { contentDescription = context.tr("Speed", "سرعت") }
        fun step(by: Double) = seek(position + by)
        bar.addView(context.button("⏪") { step(-STEP_MS) }.apply { contentDescription = context.tr("Back 5 seconds", "۵ ثانیه عقب") }, LinearLayout.LayoutParams(context.dp(64), context.dp(52)))
        bar.addView(playButton, LinearLayout.LayoutParams(context.dp(120), context.dp(52)).apply { marginStart = context.dp(6) })
        bar.addView(context.button("⏩") { step(STEP_MS) }.apply { contentDescription = context.tr("Forward 5 seconds", "۵ ثانیه جلو") }, LinearLayout.LayoutParams(context.dp(64), context.dp(52)).apply { marginStart = context.dp(6) })
        bar.addView(speedButton, LinearLayout.LayoutParams(context.dp(72), context.dp(52)).apply { marginStart = context.dp(6) })
        bar.addView(
            context.button(context.tr("From the start", "از اول")) {
                position = 0.0
                if (!playing)toggle()
                invalidate()
            },
            LinearLayout.LayoutParams(-2, context.dp(52)).apply { marginStart = context.dp(6) }
        )
        bar.addView(WorkspaceIcon(context, "close", context.s("close")) { close() }, LinearLayout.LayoutParams(context.dp(52), context.dp(52)).apply { marginStart = context.dp(6) })
        // Controls at the bottom, within reach on a tall panel, like the spotlight's.
        addView(bar, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = context.dp(28) })
    }
    private fun toggle() {
        if (!playing && position >= total)position = 0.0
        playing = !playing
        last = 0L
        playButton.text = if (playing)context.s("pause")else context.tr("Play", "پخش")
        invalidate()
    }

    /** Moves the replay to [ms] (for tests and for scrubbing). */
    fun seek(ms: Double) {
        position = ms.coerceIn(0.0, total)
        invalidate()
    }
    override fun onDraw(c: Canvas) {
        val now = SystemClock.uptimeMillis()
        if (playing && last != 0L)position = (position + (now - last).coerceAtMost(100) * speed).coerceAtMost(total)
        last = now
        val w = width.toFloat()
        val h = height - context.dp(110).toFloat()
        if (w <= 0 || h <= 0)return
        val s = min(w / bounds.width(), h / bounds.height())
        val ex = (w / s - bounds.width()) / 2
        val ey = (h / s - bounds.height()) / 2
        c.save()
        c.clipRect(0f, 0f, w, h)
        c.scale(s, s)
        c.translate(-bounds.left + ex, -bounds.top + ey)
        renderer.background(c, page, RectF(bounds.left - ex, bounds.top - ey, bounds.right + ex, bounds.bottom + ey))
        var t = position
        val shown = mutableListOf<Item>()
        var partial: Item? = null
        var fade = 1f
        for (i in order.indices) {
            val d = durations[i] * scale
            if (t >= d) {
                shown.add(order[i])
                t -= d
                continue
            }
            if (t > 0) {
                val o = order[i]
                val f = (t / d).toFloat()
                if (o.kind == "ink") {
                    val k = (o.points.size * f).toInt().coerceIn(1, o.points.size)
                    partial = o.copy(points = o.points.subList(0, k).toMutableList())
                } else {
                    partial = o
                    fade = f
                }
            }
            break
        }
        renderer.scene(c, page.copy(items = shown.toMutableList()))
        partial?.let { o ->
            val save = if (fade < 1f)c.saveLayerAlpha(null, (fade * 255).toInt())else c.save()
            renderer.draw(c, o)
            c.restoreToCount(save)
        }
        c.restore()
        // A thin progress line under the picture.
        val y = h + context.dp(8)
        val left = context.dp(24).toFloat()
        val right = width - left
        c.drawRoundRect(left, y, right, y + context.dp(4), context.dp(2).toFloat(), context.dp(2).toFloat(), track)
        c.drawRoundRect(left, y, left + (right - left) * (if (total > 0)(position / total).toFloat()else 1f), y + context.dp(4), context.dp(2).toFloat(), context.dp(2).toFloat(), fill)
        if (playing && position < total) {
            postInvalidateOnAnimation()
        } else if (playing) {
            playing = false
            playButton.text = context.tr("Play", "پخش")
        }
    }

    /** Touching the progress line jumps there; dragging along it scrubs. */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val line = height - context.dp(110).toFloat() + context.dp(6)
        if (e.actionMasked != MotionEvent.ACTION_UP && e.y in line - context.dp(24)..line + context.dp(28) && total > 0) {
            val left = context.dp(24).toFloat()
            seek(((e.x - left) / (width - 2 * left)).coerceIn(0f, 1f) * total)
        }
        return true
    }
}
