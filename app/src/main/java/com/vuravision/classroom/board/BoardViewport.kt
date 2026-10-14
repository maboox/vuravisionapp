package com.vuravision.classroom.board
import android.graphics.*
import android.view.*
import kotlin.math.*

// Viewport: fit, reveal, show an area, screen and world coordinates (split from Board, item 12).

internal fun Board.fit() {
    backingDirty = true
    if (fixedPageWidth != null) {
        fitFixed()
        invalidate()
        return
    }
    if (store.page.items.isEmpty()) {
        reset()
        return
    }
    val items = store.page.visibleItems().filter { it.pane == activePane }
    if (items.isEmpty()) {
        reset()
        return
    }
    val b = contentBounds(items)
    val panel = paneRect(activePane)
    val viewport = RectF(panel)
    fitViewport?.invoke()?.let { area -> if (area.width() > 0 && area.height() > 0)viewport.intersect(area) }
    zoom =
        min(viewport.width() / density / (b.width() + 90), viewport.height() / density / (b.height() + 90))
            .coerceIn(.15f, 6f)
    tx = (viewport.centerX() - panel.left) / density - b.centerX() * zoom
    ty = (viewport.centerY() - panel.top) / density - b.centerY() * zoom
    invalidate()
}

/**
 * Pans so [r] is fully visible in the free area beside the toolbars, when it is not already; zooms out only as much
 * as needed (never below 40%). Used when something is added next to the selection, e.g. an AI answer.
 */
internal fun Board.reveal(r: RectF) {
    val panel = paneRect(activePane)
    val viewport = RectF(panel)
    fitViewport?.invoke()?.let { area -> if (area.width() > 0 && area.height() > 0)viewport.intersect(area) }
    val visible = RectF(
        ((viewport.left - panel.left) / density - tx) / zoom,
        ((viewport.top - panel.top) / density - ty) / zoom,
        ((viewport.right - panel.left) / density - tx) / zoom,
        ((viewport.bottom - panel.top) / density - ty) / zoom
    )
    if (visible.contains(r))return
    val needed = min(viewport.width() / density / (r.width() + 60), viewport.height() / density / (r.height() + 60))
    if (needed < zoom)zoom = needed.coerceAtLeast(.4f)
    tx = (viewport.centerX() - panel.left) / density - r.centerX() * zoom
    ty = (viewport.centerY() - panel.top) / density - r.centerY() * zoom
    backingDirty = true
    invalidate()
}

/** Shows exactly [r] (plus a margin) in the free area: used to step through frames. */
internal fun Board.showArea(r: RectF) {
    val panel = paneRect(activePane)
    val viewport = RectF(panel)
    fitViewport?.invoke()?.let { area -> if (area.width() > 0 && area.height() > 0)viewport.intersect(area) }
    zoom = min(viewport.width() / density / (r.width() + 80), viewport.height() / density / (r.height() + 80)).coerceIn(.05f, 20f)
    tx = (viewport.centerX() - panel.left) / density - r.centerX() * zoom
    ty = (viewport.centerY() - panel.top) / density - r.centerY() * zoom
    backingDirty = true
    onViewportChanged()
    invalidate()
}

internal fun Board.center(): PointF {
    if (fixedPageWidth != null) {
        val visible = Rect()
        if (getLocalVisibleRect(visible))return world(visible.exactCenterX(), visible.exactCenterY())
    }
    return paneRect(activePane.coerceIn(store.page.visiblePaneIndices)).let { world(it.centerX(), it.centerY()) }
}

internal fun Board.world(x: Float, y: Float, index: Int = activePane): PointF {
    val n = index.coerceIn(store.page.visiblePaneIndices)
    val r = paneRect(n)
    val p = store.page.panes[n]
    return PointF(((x - r.left) / density - p.tx) / p.zoom, ((y - r.top) / density - p.ty) / p.zoom)
}

internal fun Board.reset() {
    measurements.clear()
    gestures.reset()
    abortPendingGesture()
    holdCallbacks.keys.toList().forEach(::cancelHold)
    smoothers.clear()
    removeCallbacks(smoothFrame)
    live.clear()
    guides.clear()
    guideStopped.clear()
    guideLengths.clear()
    guideEdges.clear()
    guideEdgeCandidates.clear()
    guideStart.clear()
    heldStart.clear()
    heldOriginal.clear()
    heldEnd.clear()
    mindStart.clear()
    activePane = activePane.coerceIn(store.page.visiblePaneIndices)
    backingDirty = true
    zoom = 1f
    tx = 0f
    ty = 0f
    fitFixed()
    invalidate()
}

internal fun Board.navigateGesture(e: MotionEvent) {
    for (i in 0 until e.pointerCount)pointerPanes[e.getPointerId(i)] = activePane
    navigate(e)
}
internal fun Board.beginGestureNavigation(x: Float, y: Float, initialSpan: Float) {
    navigationActive = true
    lastFocus = PointF(x, y)
    span = initialSpan
}
internal fun Board.measureScale() = measureScale(activePane)
internal fun Board.measureScale(index: Int): Float {
    val pixels = context.getSharedPreferences("vura", 0).getFloat("pixelsPerCm", 0f)
    return if (pixels > 0)pixels / (density * store.page.panes[index.coerceIn(store.page.visiblePaneIndices)].zoom) else 10f
}

/** The given items alone on white, at most [maxSide] px: the picture sent to the AI. Returns it with its board bounds. */
internal fun Board.snapshotItems(items: List<Item>, maxSide: Int = 1024): Pair<Bitmap, RectF> {
    val b = RectF(contentBounds(items))
    b.inset(-16f, -16f)
    val scale = (maxSide / maxOf(b.width(), b.height(), 1f)).coerceAtMost(3f)
    val bmp = Bitmap.createBitmap(maxOf(1, (b.width() * scale).toInt()), maxOf(1, (b.height() * scale).toInt()), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(Color.WHITE)
    c.scale(scale, scale)
    c.translate(-b.left, -b.top)
    items.forEach { renderer.draw(c, it, sync = true) }
    return bmp to b
}
