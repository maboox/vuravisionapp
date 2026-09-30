package com.vuravision.classroom

import kotlin.math.*

/** Scale about the actual first ink sample. Closed strokes end near that sample,
 * so finger displacement moves a virtual opposite handle, rather than jumping
 * the handle to the finger when recognition happens. */
object HeldShapeResize {
    fun apply(target: Item, base: Item, startX: Float, startY: Float,
              endX: Float, endY: Float, pointerX: Float, pointerY: Float) {
        val pivotX = startX
        val pivotY = startY
        val handleX = if (pivotX <= base.x + base.w / 2) base.x + base.w else base.x
        val handleY = if (pivotY <= base.y + base.h / 2) base.y + base.h else base.y
        var sx = (handleX + pointerX - endX - pivotX) / (handleX - pivotX)
        var sy = (handleY + pointerY - endY - pivotY) / (handleY - pivotY)
        fun bounded(v: Float, extent: Float): Float =
            (if (v < 0) -1f else 1f) * abs(v).coerceAtLeast(4f / extent)
        sx = bounded(sx, base.w)
        sy = bounded(sy, base.h)
        if (Shapes.uniform(base.shape)) {
            val scale = max(abs(sx), abs(sy))
            sx = if (sx < 0) -scale else scale
            sy = if (sy < 0) -scale else scale
        }
        val left = pivotX + (base.x - pivotX) * sx
        val right = pivotX + (base.x + base.w - pivotX) * sx
        val top = pivotY + (base.y - pivotY) * sy
        val bottom = pivotY + (base.y + base.h - pivotY) * sy
        target.x = min(left, right)
        target.y = min(top, bottom)
        target.w = abs(right - left)
        target.h = abs(bottom - top)
    }
}
