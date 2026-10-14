package com.vuravision.classroom.board
import android.graphics.*
import android.view.*
import com.vuravision.classroom.ui.label
import kotlin.math.*

// Ink sampling, smoothing and guided strokes along rulers and other tools (split from Board, item 12).

internal fun Board.sampleInk(id: Int, o: Item, point: Point) {
    val filter = smoothers[id]
    if (filter == null) {
        o.points.add(point)
    } else {
        filter.target(point)
        filter.advance(point.t)?.let { o.points.add(it) }
        removeCallbacks(smoothFrame)
        postOnAnimation(smoothFrame)
    }
}
internal fun Board.snap(id: Int, point: PointF): PointF = (if (guideSnapEnabled)guides[id] else null)?.let {
    GeometryTools.snap(it, point.x, point.y, 14f / store.page.panes[it.pane].zoom, guideEdges[id])
} ?: point
internal fun Board.sampleGuidedInk(id: Int, o: Item, point: Point) {
    if (id in guideStopped)return
    val tolerance = 14f / store.page.panes[o.pane].zoom
    if (!guides.containsKey(id) && guideSnapEnabled && tool != "smart") {
        val guide = store.page.visibleItems().asReversed().firstOrNull { it.pane == o.pane && canEditItem(it) && it.shape in GeometryTools.keys && GeometryTools.snap(it, point.x, point.y, tolerance) != null }
        if (guide != null) {
            guides[id] = guide
            guideLengths[id] = 0f
            guideEdges[id] = GeometryTools.nearestEdge(guide, point.x, point.y) ?: 0
            val candidates = GeometryTools.edgeCandidates(guide, point.x, point.y, tolerance, 3f / store.page.panes[o.pane].zoom)
            if (candidates.size > 1) {
                guideEdgeCandidates[id] = candidates
                guideStart[id] = point.copy()
            }
            o.points.clear()
            renderer.forgetInk(o.id)
            smoothers.remove(id)
            cancelHold(id)
        }
    }
    val guide = guides[id]
    if (guide == null) {
        sampleInk(id, o, point)
        return
    }
    guideStart[id]?.let { start ->
        if (hypot(point.x - start.x, point.y - start.y) > 2f / store.page.panes[o.pane].zoom) {
            guideEdges[id] = GeometryTools.nearestEdge(guide, point.x, point.y, guideEdgeCandidates[id]) ?: guideEdges.getValue(id)
            val first = GeometryTools.snap(guide, start.x, start.y, tolerance, guideEdges[id])
            o.points.clear()
            renderer.forgetInk(o.id)
            guideLengths[id] = 0f
            if (first != null)o.points.add(start.copy(x = first.x, y = first.y))
            guideStart.remove(id)
            guideEdgeCandidates.remove(id)
        } else if (o.points.isNotEmpty()) {
            return
        }
    }
    val projected = GeometryTools.snap(guide, point.x, point.y, tolerance, guideEdges[id])
    if (projected == null) {
        guideStopped.add(id)
        return
    }
    o.points.lastOrNull()?.let { last -> guideLengths[id] = (guideLengths[id] ?: 0f) + hypot(projected.x - last.x, projected.y - last.y) }
    o.points.add(point.copy(x = projected.x, y = projected.y))
}
internal fun Board.drawGuideMeasurements(canvas: Canvas) {
    live.forEach { (id, ink) ->
        val guide = guides[id] ?: return@forEach
        if (ink.pane != activePane)return@forEach
        val last = ink.points.lastOrNull() ?: return@forEach
        val text = GuideLabels.strokeText(guide, ink.points, guideLengths[id] ?: 0f, measureScale(), measurementUnit())
        measurements.label(canvas, text, last.x, last.y - 28 / zoom, zoom)
    }
}
internal fun Board.scheduleHold(id: Int, at: PointF) {
    if (!holdRecognitionEnabled || heldStart.containsKey(id) || guides.containsKey(id))return
    val last = holdAt[id]
    if (last != null && hypot(last.x - at.x, last.y - at.y) < holdTolerance / store.page.panes[pointerPanes[id] ?: activePane].zoom)return
    cancelHold(id)
    holdAt[id] = PointF(at.x, at.y)
    val callback = Runnable {
        holdCallbacks.remove(id)
        holdAt.remove(id)
        val ink = live[id] ?: return@Runnable
        if (ink.kind != "ink" || ink.points.size < 3)return@Runnable
        val shape = ShapeRecognition.live(ink) ?: return@Runnable
        shape.id = ink.id
        shape.layerId = ink.layerId
        shape.pane = ink.pane
        shape.alpha = ink.alpha
        shape.dashLength = ink.dashLength
        shape.dashGap = ink.dashGap
        heldStart[id] = PointF(ink.points.first().x, ink.points.first().y)
        heldEnd[id] = PointF(ink.points.last().x, ink.points.last().y)
        heldOriginal[id] = shape.deepCopy()
        live[id] = shape
        renderer.forgetInk(ink.id)
        postInvalidateOnAnimation()
    }
    holdCallbacks[id] = callback
    postDelayed(callback, holdDelayMillis)
}

internal fun Board.cancelHold(id: Int) {
    holdCallbacks.remove(id)?.let { removeCallbacks(it) }
    holdAt.remove(id)
}
