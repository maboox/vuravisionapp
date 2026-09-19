package com.vuravision.classroom

import android.content.Context
import android.graphics.*
import android.view.*

class TouchDiagnostics(context: Context, private val profile: TouchProfile) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lines = listOf(context.s("calibration_off"))
    private var points = listOf<Pair<Float, Float>>()

    override fun onTouchEvent(e: MotionEvent): Boolean {
        points = (0 until e.pointerCount).map { e.getX(it) to e.getY(it) }
        lines =
            (0 until e.pointerCount).map { i ->
                "ID ${e.getPointerId(i)} · tool ${e.getToolType(i)} · ${profile.classify(e.getToolType(i),e.getTouchMajor(i))}\nmajor %.1f px · minor %.1f · pressure %.3f"
                    .format(e.getTouchMajor(i), e.getTouchMinor(i), e.getPressure(i))
            }
        invalidate()
        if (e.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(PAPER)
        paint.color = TEAL
        points.forEach { c.drawCircle(it.first, it.second, 24f, paint) }
        paint.textSize = 14 * resources.displayMetrics.density
        paint.color = NAVY
        var y = paint.textSize * 2
        lines.forEach { v ->
            v.split('\n').forEach {
                c.drawText(it, 20f, y, paint)
                y += paint.textSize * 1.5f
            }
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
