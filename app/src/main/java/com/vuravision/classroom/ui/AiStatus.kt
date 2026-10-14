package com.vuravision.classroom.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import com.vuravision.classroom.R

/**
 * What the AI is doing, always visible at the top of the board: "Writing questions… 12 s" with a spinner and Cancel,
 * then either nothing (the result is on the board) or the reason it failed with Try again. Before, a request without
 * a selection showed only a short toast, so a slow image looked like nothing was happening.
 */
@SuppressLint("ViewConstructor")
class AiStatus(context: Context, private val host: FrameLayout) : LinearLayout(context) {
    private val spinner = ProgressBar(context).apply { isIndeterminate = true }
    private val text = context.label("", 15f, ON_NAVY, true).apply {
        maxLines = 3
        id = R.id.ai_status_text
    }
    private val action = context.label("", 14f, NAVY, true).apply {
        gravity = Gravity.CENTER
        minWidth = context.dp(72)
        setPadding(context.dp(14), 0, context.dp(14), 0)
        background = rounded(SURFACE, context.dp(14).toFloat())
    }
    private val close = context.label("✕", 16f, ON_NAVY, true).apply {
        gravity = Gravity.CENTER
        contentDescription = context.s("close")
        setOnClickListener { hide() }
    }
    private var label = ""
    private var started = 0L
    private val tick = object : Runnable {
        override fun run() {
            val seconds = (SystemClock.uptimeMillis() - started) / 1000
            text.text = if (seconds < 1)label else label + "  " + context.tr("$seconds s", "$seconds ثانیه")
            postDelayed(this, 1000)
        }
    }

    /** True while a request is shown as running. */
    var working = false
        private set
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        id = R.id.ai_status
        elevation = context.dp(10).toFloat()
        setPadding(context.dp(14), context.dp(8), context.dp(8), context.dp(8))
        addView(spinner, LayoutParams(context.dp(24), context.dp(24)).apply { marginEnd = context.dp(10) })
        addView(text, LayoutParams(-2, -2).apply { weight = 1f })
        addView(action, LayoutParams(-2, context.dp(40)).apply { marginStart = context.dp(12) })
        addView(close, LayoutParams(context.dp(40), context.dp(40)).apply { marginStart = context.dp(4) })
        visibility = View.GONE
        host.addView(this, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = context.dp(14) })
    }
    fun working(what: String, cancel: () -> Unit) {
        removeCallbacks(tick)
        working = true
        label = what
        started = SystemClock.uptimeMillis()
        background = rounded(NAVY, context.dp(22).toFloat())
        text.setTextColor(ON_NAVY)
        close.setTextColor(ON_NAVY)
        spinner.visibility = View.VISIBLE
        close.visibility = View.GONE
        action.text = context.s("cancel")
        action.visibility = View.VISIBLE
        action.setOnClickListener {
            cancel()
            hide()
        }
        show()
        post(tick)
    }

    /** The failure stays until closed or retried: a teacher looking at the class must still be able to read it. */
    fun failed(message: String, retry: (() -> Unit)?) {
        removeCallbacks(tick)
        working = false
        background = rounded(0xfffde8e8.toInt(), context.dp(22).toFloat(), 0xffdc2626.toInt())
        text.setTextColor(0xff8a1c1c.toInt())
        close.setTextColor(0xff8a1c1c.toInt())
        text.text = message
        spinner.visibility = View.GONE
        close.visibility = View.VISIBLE
        action.visibility = if (retry != null) View.VISIBLE else View.GONE
        action.text = context.tr("Try again", "دوباره")
        action.setOnClickListener {
            hide()
            retry?.invoke()
        }
        show()
    }
    fun hide() {
        removeCallbacks(tick)
        working = false
        visibility = View.GONE
    }
    private fun show() {
        visibility = View.VISIBLE
        bringToFront()
    }
    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }
}
