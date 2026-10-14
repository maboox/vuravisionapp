package com.vuravision.classroom.board
import android.graphics.*
import android.view.*
import com.vuravision.classroom.ui.LASER_COLOR
import com.vuravision.classroom.ui.tr
import kotlin.math.*

// Touch input: pens, erasers, selection, geometry handles and two-finger navigation (split from Board, item 12).

internal fun Board.handleTouch(e: MotionEvent): Boolean {
    if (!isEnabled)return true
    if (gestures.event(e))return true
    if (e.actionMasked == MotionEvent.ACTION_DOWN)onActivate()
    val id = e.getPointerId(e.actionIndex)
    if (e.actionMasked == MotionEvent.ACTION_CANCEL) {
        abortPendingGesture()
        return true
    }
    if (e.actionMasked in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN)) {
        val n = store.page.visiblePaneIndices.firstOrNull { paneRect(it).contains(e.getX(e.actionIndex), e.getY(e.actionIndex)) } ?: 0
        pointerPanes[id] = n
        if (e.actionMasked == MotionEvent.ACTION_DOWN && activePane != n) {
            activePane = n
            clearSelection()
        }
    }
    if (!sessionCanEdit || !store.editAllowed()) {
        navigate(e)
        return true
    }
    val at = world(e.getX(e.actionIndex), e.getY(e.actionIndex), pointerPanes[id] ?: activePane)
    if (geometry.active) {
        geometry.event(e, at)
        if (!geometry.active) {
            transforming = emptySet()
            backingDirty = true
            pointerPanes.clear()
        }
        invalidate()
        return true
    }
    if (e.actionMasked == MotionEvent.ACTION_DOWN && tool in listOf("pen", "highlight", "shape", "select") && profile.action(e.getToolType(e.actionIndex), e.getTouchMajor(e.actionIndex)) == "draw" && geometry.start(at, 22f / zoom)) {
        transforming = chosen().map { it.id }.toSet()
        backingDirty = true
        onSelection()
        invalidate()
        return true
    }
    if (tool == "fill") {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            val target = store.page.visibleItems().asReversed().firstOrNull { it.pane == activePane && (ShapeFill.contains(it, at.x, at.y) || it.hit(at.x, at.y, 0f)) }
            if (target != null && ShapeFill.contains(target, at.x, at.y)) {
                if (!target.locked && canEditItem(target) && store.page.editable(target)) {
                    store.editMetadata {
                        target.fillColor = fillColor
                        target.fillAlpha = fillAlpha
                    }
                }
            } else if ((target == null || target.id == pdfBaseId) && fillColor != null) {
                HandFill.create(store.page, activePane, at.x, at.y, fillColor!!, fillAlpha)?.let { result ->
                    store.editMetadata { store.page.items.add(result.index, result.item) }
                }
            }
        }
        return true
    }
    if (tool == "pan") {
        navigate(e)
        return true
    }
    if (e.actionMasked == MotionEvent.ACTION_DOWN) {
        broadPointers.clear()
        touchSpan = 0f
        touchFocus = null
    }
    if (touchMode && pdfBaseId == null && e.actionMasked in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN) && e.getToolType(e.actionIndex) != MotionEvent.TOOL_TYPE_ERASER && e.getTouchMajor(e.actionIndex) > profile.thin && e.getTouchMajor(e.actionIndex) < profile.palm) {
        val index = pointerPanes[id] ?: activePane
        if (broadPointers.isNotEmpty() && index != activePane) {
            actions[id] = "reject"
            return true
        }
        if (activePane != index) {
            activePane = index
            clearSelection()
        }
        broadPointers.add(id)
    }
    val effectiveTool = if (id in broadPointers)"select" else tool
    val navIndices = (0 until e.pointerCount).filter { e.getPointerId(it) in broadPointers && pointerPanes[e.getPointerId(it)] == activePane }
    if (navIndices.size >= 2) {
        if (mode != "navigate") {
            transforming = emptySet()
            // A second finger changes a selection gesture into navigation.
            // Restore any tentative drag, without undoing simultaneous ink.
            if (mode in listOf("move", "resize", "rotate")) {
                original.forEach { base ->
                    store.page.items.firstOrNull { it.id == base.id }?.let { o ->
                        o.x = base.x
                        o.y = base.y
                        o.w = base.w
                        o.h = base.h
                        o.rotation = base.rotation
                        o.width = base.width
                    }
                }
                backingDirty = true
            }
            tappedSelected = false
        }
        val i = navIndices[0]
        val j = navIndices[1]
        val focus = PointF((e.getX(i) + e.getX(j)) / 2, (e.getY(i) + e.getY(j)) / 2)
        val nextSpan = hypot(e.getX(i) - e.getX(j), e.getY(i) - e.getY(j))
        if (e.actionMasked == MotionEvent.ACTION_MOVE && touchFocus != null) {
            val before = world(touchFocus!!.x, touchFocus!!.y)
            if (touchSpan > 0)zoom = (zoom * nextSpan / touchSpan).coerceIn(.15f, 6f)
            val rect = paneRect(activePane)
            tx = (focus.x - rect.left) / density - before.x * zoom
            ty = (focus.y - rect.top) / density - before.y * zoom
            backingDirty = true
        }
        touchSpan = nextSpan
        touchFocus = focus
        box = null
        lasso.clear()
        mode = "navigate"
    }
    when (e.actionMasked) {
        MotionEvent.ACTION_DOWN,
        MotionEvent.ACTION_POINTER_DOWN -> {
            if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                changed = false
                primary = id
                anchor = at
                mode = ""
                eraseLast.clear()
                tappedSelected = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            if (!profile.multiTouch && id != primary) {
                actions[id] = "reject"
                return true
            }
            if (e.actionMasked == MotionEvent.ACTION_DOWN) requestUnbufferedDispatch(e)
            // One palm setting for the board and PDF sheets (a separate, default-off PDF switch made palm erasing look broken there).
            val behavior = profile.action(e.getToolType(e.actionIndex), e.getTouchMajor(e.actionIndex))
            actions[id] = behavior
            if (behavior == "reject") return true

            if (id !in broadPointers && (tool in listOf("pen", "highlight", "shape") || (tool == "smart" && smartMode == "shape"))) {
                if (!store.page.canDraw()) {
                    actions[id] = "reject"
                    android.widget.Toast.makeText(context, context.tr("Select an unlocked visible layer", "یک لایهٔ نمایان و باز انتخاب کنید"), android.widget.Toast.LENGTH_SHORT).show()
                    return true
                }
            }
            if (id in broadPointers) {
                actions[id] = "select"
                if (navIndices.size >= 2) {
                    postInvalidateOnAnimation()
                    return true
                }
                primary = id
                anchor = at
            } else if (e.actionMasked == MotionEvent.ACTION_POINTER_DOWN && effectiveTool in listOf("select", "shape")) {
                actions[id] = "reject"
                return true
            }
            if (effectiveTool == "erase" || behavior == "erase") {
                checkpoint()
                if (erase(at, eraseLast[id] ?: at, pointerPanes[id] ?: activePane))eraseLast[id] = at
            } else if (effectiveTool == "select" && id == primary) {
                actions[id] = "select"
                val chosen = chosen()
                val bounds = contentBounds(chosen)
                original = chosen.filter { !it.locked }.map { it.copy() }
                mode =
                    when {
                        chosen.isNotEmpty() &&
                            hypot(at.x - bounds.right, at.y - bounds.bottom) < 23 / zoom ->
                            "resize"
                        chosen.isNotEmpty() &&
                            hypot(at.x - bounds.centerX(), at.y - (bounds.top - 28 / zoom)) <
                            23 / zoom -> "rotate"
                        else -> ""
                    }
                if (mode.isEmpty()) {
                    val hit =
                        store.page.visibleItems().asReversed().filter { it.pane == activePane && store.page.editable(it) && canEditItem(it) && !it.locked }.firstOrNull {
                            it.hit(at.x, at.y, 9 / zoom)
                        }
                    if (hit == null) {
                        selected.clear()
                        mode = "box"
                        lasso.clear()
                        lasso.add(at)
                    } else {
                        tappedSelected = hit.id in selected
                        if (hit.id !in selected) {
                            selected.clear()
                            selected.add(hit.id)
                        }
                        original = MindMap.group(store.page, chosen()).filter { canEditItem(it) && it.pane == activePane }.map { it.copy() }
                        mode = "move"
                    }
                }
                onSelection()
            } else if (tool == "shape" && id == primary) {
                checkpoint()
                live[id] =
                    Item(
                        kind = "shape",
                        layerId = store.page.activeLayerId,
                        pane = pointerPanes[id] ?: activePane,
                        x = at.x,
                        y = at.y,
                        w = 1f,
                        h = 1f,
                        shape = shape,
                        color = inkColor,
                        width = inkWidth
                    )
            } else if (tool == "laser") {
                // Laser pen: for pointing while explaining. The line fades by itself and never enters the page or Undo.
                // Points are recorded directly: no ruler snapping, smoothing or hold-to-shape for a pointer that vanishes.
                live[id] = Item(
                    layerId = store.page.activeLayerId,
                    pane = pointerPanes[id] ?: activePane,
                    w = 1f,
                    h = 1f,
                    color = LASER_COLOR,
                    width = 5f,
                    shape = "round",
                    points = mutableListOf(Point(at.x, at.y, e.eventTime, e.getPressure(e.actionIndex)))
                )
            } else if (tool in listOf("pen", "highlight", "smart")) {
                if (tool != "smart" || smartMode == "shape")checkpoint()
                val drawingPane = store.page.panes[pointerPanes[id] ?: activePane]
                val splitInk = store.page.visiblePaneCount > 1
                val style = if (tool == "highlight") {
                    "highlight"
                } else if (splitInk) {
                    drawingPane.penStyle
                } else {
                    profile.style(e.getToolType(e.actionIndex), e.getTouchMajor(e.actionIndex), penStyle)
                }
                mindStart[id] = at
                live[id] =
                    Item(
                        layerId = store.page.activeLayerId,
                        pane = pointerPanes[id] ?: activePane,
                        w = 1f,
                        h = 1f,
                        color = if (store.page.visiblePaneCount > 1) {
                            store.page.panes[pointerPanes[id] ?: activePane].color
                        } else if (tool == "highlight") {
                            inkColor
                        } else {
                            profile.color(e.getToolType(e.actionIndex), e.getTouchMajor(e.actionIndex), inkColor)
                        },
                        width = (
                            if (splitInk) {
                                drawingPane.penWidth
                            } else if (tool == "highlight") {
                                inkWidth
                            } else {
                                profile.width(e.getToolType(e.actionIndex), e.getTouchMajor(e.actionIndex), inkWidth)
                            }
                            ) * if (style == "highlight")4f else 1f,
                        shape = style,
                        dashLength = if (splitInk)drawingPane.dashLength else dashLength,
                        dashGap = if (splitInk)drawingPane.dashGap else dashGap,
                        alpha = if (style == "highlight") 75 * penOpacity / 255 else penOpacity,
                        points = mutableListOf()
                    )
                sampleGuidedInk(id, live.getValue(id), Point(at.x, at.y, e.eventTime, e.getPressure(e.actionIndex)))
                if (style == "smooth" && id !in guides) {
                    smoothers[id] = StrokeSmoother(live.getValue(id).points.first())
                    removeCallbacks(smoothFrame)
                    postOnAnimation(smoothFrame)
                }
                if (tool == "pen" && style != "smooth")scheduleHold(id, at)
            }
        }
        MotionEvent.ACTION_MOVE -> {
            for (i in 0 until e.pointerCount) {
                val pid = e.getPointerId(i)
                val point = world(e.getX(i), e.getY(i), pointerPanes[pid] ?: activePane)
                if (actions[pid] == "reject") continue
                if (pid in broadPointers && mode == "navigate")continue
                if (tool == "erase" || actions[pid] == "erase") {
                    checkpoint()
                    if (erase(point, eraseLast[pid] ?: point, pointerPanes[pid] ?: activePane))eraseLast[pid] = point
                } else if (actions[pid] == "select" && pid == primary) {
                    if (mode == "box") {
                        if (selectionMode == "free")lasso.add(point)
                        box =
                            RectF(
                                min(anchor.x, point.x),
                                min(anchor.y, point.y),
                                max(anchor.x, point.x),
                                max(anchor.y, point.y)
                            )
                    } else if (hypot(point.x - anchor.x, point.y - anchor.y) > 4 / zoom) {
                        checkpoint()
                        transform(point)
                    }
                } else {
                    live[pid]?.let { o ->
                        if (o.kind == "shape") {
                            val endpoint = if (pid in heldStart)snap(pid, point) else point
                            if (pid in heldOriginal && o.shape != "line") {
                                val base = heldOriginal.getValue(pid)
                                val last = heldEnd.getValue(pid)
                                val start = heldStart.getValue(pid)
                                HeldShapeResize.apply(o, base, start.x, start.y, last.x, last.y, endpoint.x, endpoint.y)
                            } else {
                                val start = heldStart[pid] ?: anchor
                                o.flipX = endpoint.x < start.x
                                o.flipY = endpoint.y >= start.y
                                val dx = endpoint.x - start.x
                                val dy = endpoint.y - start.y
                                o.w = abs(dx).coerceAtLeast(1f)
                                o.h = abs(dy).coerceAtLeast(1f)
                                if (Shapes.uniform(o.shape)) {
                                    val size = max(o.w, o.h)
                                    o.w = size
                                    o.h = size
                                }
                                o.x = if (dx < 0)start.x - o.w else start.x
                                o.y = if (dy < 0)start.y - o.h else start.y
                            }
                        } else if (tool == "laser") {
                            o.points.add(Point(point.x, point.y, e.eventTime, e.getPressure(i)))
                        } else {
                            val paneIndex = pointerPanes[pid] ?: activePane
                            val rect = paneRect(paneIndex)
                            val state = store.page.panes[paneIndex]
                            val scale = density * state.zoom
                            for (j in 0 until e.historySize) {
                                val q = PointF(
                                    (e.getHistoricalX(i, j) - rect.left - density * state.tx) / scale,
                                    (e.getHistoricalY(i, j) - rect.top - density * state.ty) / scale
                                )
                                sampleGuidedInk(
                                    pid,
                                    o,
                                    Point(
                                        q.x,
                                        q.y,
                                        e.getHistoricalEventTime(j),
                                        e.getHistoricalPressure(i, j)
                                    )
                                )
                            }
                            sampleGuidedInk(pid, o, Point(point.x, point.y, e.eventTime, e.getPressure(i)))
                            if (tool == "pen" && o.shape != "smooth")scheduleHold(pid, point)
                        }
                    }
                }
            }
        }
        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_POINTER_UP -> {
            cancelHold(id)
            live.remove(id)?.let { o ->
                if (tool == "laser") {
                    fading.add(o to android.os.SystemClock.uptimeMillis())
                    postInvalidateOnAnimation()
                    return@let
                }
                var guideLabel: Item? = null
                if (o.kind == "ink") {
                    val endpoint = Point(at.x, at.y, e.eventTime)
                    val filter = smoothers.remove(id)
                    if (filter != null)o.points.addAll(filter.finish(endpoint))else sampleGuidedInk(id, o, endpoint)
                    if (o.points.isEmpty() || id in guides && (guideLengths[id] ?: 0f) < .5f)return@let
                    if (smoothers.isEmpty())removeCallbacks(smoothFrame)
                    guides[id]?.let { guide ->
                        val last = o.points.last()
                        guideLabel = GuideLabels.text(GuideLabels.strokeText(guide, o.points, guideLengths[id] ?: 0f, measureScale(o.pane), measurementUnit()), last.x, last.y, store.page.panes[o.pane].zoom, o)
                    }
                    val left = o.points.minOf { it.x }
                    val top = o.points.minOf { it.y }
                    o.w = (o.points.maxOf { it.x } - left).coerceAtLeast(1f)
                    o.h = (o.points.maxOf { it.y } - top).coerceAtLeast(1f)
                    o.inkW = o.w
                    o.inkH = o.h
                    o.x = left
                    o.y = top
                    o.points.forEach {
                        it.x -= left
                        it.y -= top
                    }
                }
                if (tool == "pen")mindStart[id]?.let { if (MindMap.attach(store.page, o, it.x, it.y, canEditItem))backingDirty = true }
                renderer.forgetInk(o.id)
                if (tool == "smart" && smartMode != "shape") {
                    val enclosed = if (SmartSelection.isLoop(o))SmartSelection.enclosed(o, store.page.visibleItems().filter { it.pane == o.pane && store.page.editable(it) && canEditItem(it) })else emptyList()
                    if (enclosed.isNotEmpty()) {
                        post { onSmart(enclosed) }
                    } else {
                        android.widget.Toast.makeText(context, context.tr("Circle existing writing with a closed loop. Use Pen to write.", "دور نوشتهٔ قبلی یک خط بسته بکشید. برای نوشتن از قلم استفاده کنید."), android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else if (tool == "smart" && smartMode == "shape") {
                    val converted = ShapeRecognition.convert(o)
                    store.page.items.add(
                        (converted ?: o).apply {
                            layerId = o.layerId
                            pane = o.pane
                        }
                    )
                } else {
                    checkpoint()
                    store.page.items.add(o)
                    guideLabel?.let {
                        store.page.items.add(it)
                        backingDirty = true
                    }
                }
                if (tool != "smart") {
                    commitToBacking(o)
                } else if (smartMode == "shape") {
                    backingDirty = true
                }
            }
            guides.remove(id)
            guideStopped.remove(id)
            guideLengths.remove(id)
            guideEdges.remove(id)
            guideEdgeCandidates.remove(id)
            guideStart.remove(id)
            heldStart.remove(id)
            heldOriginal.remove(id)
            heldEnd.remove(id)
            mindStart.remove(id)
            val wasSelection = actions[id] == "select"
            actions.remove(id)
            eraseLast.remove(id)
            broadPointers.remove(id)
            pointerPanes.remove(id)
            if (id == primary) {
                box?.let { rect ->
                    selected.addAll(SmartSelection.selectGesture(lasso, rect, store.page.visibleItems().filter { it.pane == activePane && store.page.editable(it) && canEditItem(it) && !it.locked }, selectionMode).map { it.id })
                }
                box = null
                lasso.clear()
                if (wasSelection) {
                    if (mode == "move" && hypot(at.x - anchor.x, at.y - anchor.y) < 4 / zoom) {
                        val education = chosen().singleOrNull()?.takeIf { it.kind in listOf("periodic", "graph") }
                        if (education != null) {
                            post { onEducationalTap(education, at) }
                        } else if (tappedSelected) {
                            post { onObjectActions() }
                        }
                    }
                    onSelection()
                }
            }
            if (e.actionMasked == MotionEvent.ACTION_UP) {
                // The object bar hides during a drag; tell the host once it ends so it reappears at the new place.
                if (transforming.isNotEmpty()) {
                    transforming = emptySet()
                    backingDirty = true
                    post { onSelection() }
                }
                if (changed || mode == "navigate") {
                    isCommitting = true
                    try {
                        if (!changed || !store.discardUnchangedCheckpoint())store.changed()
                    } finally {
                        isCommitting = false
                    }
                }
                changed = false
                mode = ""
                performClick()
            }
        }
    }
    postInvalidateOnAnimation()
    return true
}

internal fun Board.transform(point: PointF) {
    if (transforming.isEmpty()) {
        transforming = original.mapTo(mutableSetOf()) { it.id }
        backingDirty = true
    }
    if (original.isEmpty()) return
    val b = contentBounds(original)
    original.forEach { base ->
        val o = store.page.items.firstOrNull { it.id == base.id } ?: return@forEach
        when (mode) {
            "move" -> {
                o.x = base.x + point.x - anchor.x
                o.y = base.y + point.y - anchor.y
            }
            "resize" -> {
                val factor =
                    max(
                        (point.x - b.left) / b.width().coerceAtLeast(1f),
                        (point.y - b.top) / b.height().coerceAtLeast(1f)
                    )
                        .coerceIn(.05f, 20f)
                o.x = b.left + (base.x - b.left) * factor
                o.y = b.top + (base.y - b.top) * factor
                if (original.size == 1 && base.kind == "shape" && base.shape == "ruler") {
                    // A ruler only gets longer or shorter, along its own edge; its width and markings stay the same size.
                    val angle = Math.toRadians(base.rotation.toDouble())
                    o.x = base.x
                    o.y = base.y
                    o.w = (base.w + ((point.x - anchor.x) * cos(angle) + (point.y - anchor.y) * sin(angle)).toFloat()).coerceIn(40f, 100000f)
                    o.h = base.h
                } else if (original.size == 1 && base.kind == "shape" && !Shapes.uniform(base.shape) && base.shape !in GeometryTools.keys) {
                    o.w = (base.w + point.x - anchor.x).coerceIn(4f, 100000f)
                    o.h = (base.h + point.y - anchor.y).coerceIn(4f, 100000f)
                } else {
                    o.w = (base.w * factor).coerceIn(.01f, 100000f)
                    o.h = (base.h * factor).coerceIn(.01f, 100000f)
                    if (base.kind == "text") {
                        o.width = (base.width * factor).coerceIn(.1f, 200f)
                        TextLayout.fit(o)
                    }
                }
            }
            "rotate" -> {
                val delta =
                    (
                        (
                            atan2(point.y - b.centerY(), point.x - b.centerX()) -
                                atan2(anchor.y - b.centerY(), anchor.x - b.centerX())
                            ) * 180 / PI
                        )
                        .toFloat()
                o.rotation = base.rotation + delta
            }
        }
    }
}

/**
 * Returns false when the step was too short to record; the caller then keeps its last point, so the next sweep covers it.
 * Covering PDF content was slow: every move event added a cut and redrew the whole sheet. Now short steps merge into one
 * cut and only the swept area is redrawn.
 */
internal fun Board.erase(q: PointF, from: PointF = q, index: Int = activePane): Boolean {
    if (erasePdfContent && pdfBaseId != null) {
        val r = eraserRadius / store.page.panes[index].zoom
        if (from !== q && hypot(q.x - from.x, q.y - from.y) < r * .4f)return false
        store.page.items.firstOrNull { it.id == pdfBaseId }?.let { o ->
            val was = o.locked
            o.locked = false
            Erasing.cut(o, from, q, r)
            o.locked = was
        }
    }
    if (store.page.visiblePaneCount > 1)backingDirty = true
    val radius = eraserRadius / store.page.panes[index].zoom
    val region = RectF(min(from.x, q.x) - radius - 4 / zoom, min(from.y, q.y) - radius - 4 / zoom, max(from.x, q.x) + radius + 4 / zoom, max(from.y, q.y) + radius + 4 / zoom)
    if (dirtyRegion == null)dirtyRegion = region else dirtyRegion!!.union(region)
    if (eraserMode == "area") {
        store.page.items.filter { it.pane == index && store.page.editable(it) && canEditItem(it) && (eraseObjects || it.kind == "ink") }.forEach { Erasing.cut(it, from, q, radius) }
    } else {
        val steps = ceil(hypot(q.x - from.x, q.y - from.y) / max(radius * .5f, 1f)).toInt().coerceIn(1, 20000)
        store.page.items.removeAll { o ->
            if (o.pane != index || o.locked || !store.page.editable(o) || !canEditItem(o) || (!eraseObjects && o.kind != "ink")) {
                false
            } else {
                val bounds = itemBounds(o).apply { inset(-o.width * 3 - 4, -o.width * 3 - 4) }
                val removed = RectF.intersects(bounds, region) &&
                    if (o.kind == "ink" && o.shape != "region_fill" && o.cuts.isEmpty()) {
                        Erasing.hitsInk(o, from, q, radius)
                    } else {
                        (0..steps).any { i ->
                            val t = i.toFloat() / steps
                            o.hit(from.x + (q.x - from.x) * t, from.y + (q.y - from.y) * t, radius)
                        }
                    }
                if (removed)dirtyRegion?.union(bounds)
                removed
            }
        }
    }
    selected.retainAll(store.page.items.map { it.id }.toSet())
    return true
}

internal fun Board.navigate(e: MotionEvent) {
    if (e.actionMasked == MotionEvent.ACTION_DOWN)navigationActive = true
    if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL)navigationActive = false
    backingDirty = true
    val indices = (0 until e.pointerCount).filter { pointerPanes[e.getPointerId(it)] == activePane }
    if (indices.isEmpty())return
    val x = indices.sumOf { e.getX(it).toDouble() }.toFloat() / indices.size
    val y = indices.sumOf { e.getY(it).toDouble() }.toFloat() / indices.size
    val newSpan =
        if (indices.size > 1) hypot(e.getX(indices[0]) - e.getX(indices[1]), e.getY(indices[0]) - e.getY(indices[1])) else 0f
    if (e.actionMasked == MotionEvent.ACTION_MOVE) {
        val before = world(x, y)
        if (span > 0 && newSpan > 0) {
            zoom = (zoom * newSpan / span).coerceIn(.15f, 6f)
            val rect = paneRect(activePane)
            tx = (x - rect.left) / density - before.x * zoom
            ty = (y - rect.top) / density - before.y * zoom
        }
        tx += (x - lastFocus.x) / density
        ty += (y - lastFocus.y) / density
    } else {
        span = 0f
    }
    span = if (e.actionMasked == MotionEvent.ACTION_MOVE) newSpan else 0f
    lastFocus = PointF(x, y)
    invalidate()
    if (e.actionMasked == MotionEvent.ACTION_UP) {
        store.changed()
        pointerPanes.clear()
        performClick()
    }
}
