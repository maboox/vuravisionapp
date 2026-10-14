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
 * Length: 30 s, 1 or 3 minutes, or the natural pace (no limit). [mark] (ids on the page when the teacher pressed
 * "Start here") lets the replay show only what came after it.
 */
@SuppressLint("ViewConstructor")
class Replay(
    context: Context,
    source: Page,
    pane: Int,
    private val renderer: Renderer,
    private val mark: Set<String>? = null,
    private val setMark: (() -> Unit)? = null,
    private val close: () -> Unit
) : FrameLayout(context) {
    companion object {
        val LENGTHS = listOf(30_000.0, 60_000.0, 180_000.0, Double.MAX_VALUE)
    }
    private val prefs = context.getSharedPreferences("vura", 0)
    private val page = source.copied().let { p -> p.copy(items = p.items.filter { it.pane == pane }.map { it.copy(pane = 0) }.toMutableList(), panes = mutableListOf(p.panes.getOrElse(pane) { p.panes.first() }.copy()), paneCount = 1, alternateCanvas = null, background = p.pattern(pane)) }
    private val everything = page.visibleItems()
    private val afterMark = mark?.let { m -> everything.filter { it.id !in m } }?.takeIf { it.isNotEmpty() }

    /** True while only what was added after the mark is replayed; what came before stays on screen from the start. */
    var sinceMark = afterMark != null
        private set
    private var before: List<Item> = emptyList()
    private var order: List<Item> = everything
    private var durations: List<Double> = emptyList()
    var length = prefs.getFloat("replayLength", 60_000f).toDouble().let { v -> LENGTHS.minByOrNull { kotlin.math.abs(it - v) }!! }
        private set
    private var scale = 1.0
    private var total = 0.0
    private fun plan() {
        order = if (sinceMark)afterMark!! else everything
        before = if (sinceMark)everything.filter { it.id in mark!! } else emptyList()
        // Each item's share of the replay: strokes by their length, everything else a short fade-in.
        durations = order.map { if (it.kind == "ink")(it.points.size * 12.0).coerceIn(180.0, 1600.0)else 450.0 }
        scale = min(1.0, length / durations.sum().coerceAtLeast(1.0))
        total = durations.sum() * scale
        position = position.coerceAtMost(total)
    }
    private fun lengthText() = when (length) {
        30_000.0 -> context.tr("30 s", "۳۰ ثانیه")
        60_000.0 -> context.tr("1 min", "۱ دقیقه")
        180_000.0 -> context.tr("3 min", "۳ دقیقه")
        else -> context.tr("Natural pace", "سرعت واقعی")
    }
    var position = 0.0
        private set
    var speed = 1.0
        private set
    var playing = true
        private set
    private var last = 0L
    private val bounds = contentBounds(everything).apply { inset(-40f, -40f) }
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
        plan()
        bar.addView(playButton, LinearLayout.LayoutParams(context.dp(120), context.dp(52)))
        bar.addView(speedButton, LinearLayout.LayoutParams(context.dp(72), context.dp(52)).apply { marginStart = context.dp(6) })
        lateinit var lengthButton: android.widget.Button
        lengthButton = context.button(lengthText()) {
            length = LENGTHS[(LENGTHS.indexOf(length) + 1) % LENGTHS.size]
            prefs.edit().putFloat("replayLength", length.toFloat()).apply()
            lengthButton.text = lengthText()
            val fraction = if (total > 0)position / total else 0.0
            plan()
            position = fraction * total
            invalidate()
        }.apply { contentDescription = context.tr("Replay length", "مدت بازپخش") }
        bar.addView(lengthButton, LinearLayout.LayoutParams(-2, context.dp(52)).apply { marginStart = context.dp(6) })
        if (afterMark != null) {
            lateinit var scope: android.widget.Button
            scope = context.button(context.tr("Since the mark", "از علامت")) {
                sinceMark = !sinceMark
                scope.text = if (sinceMark)context.tr("Since the mark", "از علامت")else context.tr("Whole page", "کل صفحه")
                position = 0.0
                plan()
                invalidate()
            }
            bar.addView(scope, LinearLayout.LayoutParams(-2, context.dp(52)).apply { marginStart = context.dp(6) })
        }
        bar.addView(
            context.button(context.tr("From the start", "از اول")) {
                position = 0.0
                if (!playing)toggle()
                invalidate()
            },
            LinearLayout.LayoutParams(-2, context.dp(52)).apply { marginStart = context.dp(6) }
        )
        setMark?.let { set ->
            // "Start here": the next replay of this page begins with what is added from now on.
            bar.addView(
                context.button(context.tr("Start here", "شروع از اینجا")) {
                    set()
                    android.widget.Toast.makeText(context, context.tr("The next replay starts with what you add from now", "بازپخش بعدی از چیزهایی شروع می‌شود که از حالا اضافه می‌کنید"), android.widget.Toast.LENGTH_LONG).show()
                    close()
                }.apply { contentDescription = context.tr("Mark the replay start", "علامت شروع بازپخش") },
                LinearLayout.LayoutParams(-2, context.dp(52)).apply { marginStart = context.dp(6) }
            )
        }
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
        val shown = before.toMutableList()
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
