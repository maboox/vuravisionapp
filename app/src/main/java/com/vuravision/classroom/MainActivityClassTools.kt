package com.vuravision.classroom
import android.app.*
import android.content.*
import android.graphics.*
import android.os.*
import android.view.*
import android.widget.*
import com.vuravision.classroom.board.FramePresenter
import com.vuravision.classroom.board.Frames
import com.vuravision.classroom.board.Item
import com.vuravision.classroom.board.Maps
import com.vuravision.classroom.board.Tables
import com.vuravision.classroom.board.center
import com.vuravision.classroom.board.contentBounds
import com.vuravision.classroom.board.showArea
import com.vuravision.classroom.tools.Replay
import com.vuravision.classroom.tools.Spotlight
import com.vuravision.classroom.ui.Subjects
import com.vuravision.classroom.ui.s
import com.vuravision.classroom.ui.tr
import kotlin.math.*

// Class tools and board objects: frames, map, table, replay, spotlight, page patterns (split from MainActivity, item 11).

/** A frame around what is on screen now, numbered; lay the lesson out on a big board, then present frame by frame. */
internal fun MainActivity.insertFrame() {
    val v = board.viewportWorld()
    val w = (v.right - v.left) * .8f
    val h = (v.bottom - v.top) * .8f
    val n = Frames.of(store.page, board.activePane).size + 1
    val f = Frames.create(v.left + (v.right - v.left - w) / 2, v.top + (v.bottom - v.top - h) / 2, w, h, tr("Frame $n", "قاب $n"), store.page.activeLayerId, board.activePane)
    store.editMetadata { store.page.items.add(f) }
    board.selected.clear()
    board.selected.add(f.id)
    board.tool = "select"
    board.sceneChanged()
    refreshDock()
    refreshSelectionBar()
}
internal fun MainActivity.presentFrames() {
    val frames = Frames.of(store.page, board.activePane)
    if (frames.isEmpty()) {
        toast(tr("Add frames first: Add → Frame", "اول قاب بگذارید: افزودن ← قاب"))
        return
    }
    framePresenter?.let { canvasHost.removeView(it) }
    board.clearSelection()
    framePresenter = FramePresenter(this, frames, { f -> board.showArea(contentBounds(listOf(f))) }) {
        framePresenter?.let { canvasHost.removeView(it) }
        framePresenter = null
    }
        .also { canvasHost.addView(it, FrameLayout.LayoutParams(-1, -1)) }
}
internal fun MainActivity.insertIranMap() {
    val c = board.center()
    val m = Maps.create(this, c.x - 450f, c.y - 400f, store.page.activeLayerId, board.activePane, if (resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL)"fa" else "en")
    m.y = c.y - m.h / 2
    store.editMetadata { store.page.items.add(m) }
    board.selected.clear()
    board.selected.add(m.id)
    board.tool = "select"
    board.sceneChanged()
    refreshDock()
    refreshSelectionBar()
}

/** A 3 × 3 table in the middle of the view, selected, with its cells open for typing at once. */
internal fun MainActivity.insertTable() {
    val c = board.center()
    val t = Tables.create(3, 3, c.x - 300f, c.y - 108f, store.page.activeLayerId, board.activePane).apply { flipX = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL }
    store.editMetadata { store.page.items.add(t) }
    board.selected.clear()
    board.selected.add(t.id)
    board.tool = "select"
    board.sceneChanged()
    refreshDock()
    refreshSelectionBar()
    editTable(t)
}

/** Cell size stays the same when rows or columns change, so the table grows instead of squeezing. */
internal fun MainActivity.editTable(t: Item) = Tables.editor(this, t) { rows ->
    val before = Tables.cells(t)
    val cw = t.w / before.first().size
    val rh = t.h / before.size
    store.page.items.firstOrNull { it.id == t.id && !it.locked }?.let { o ->
        store.editMetadata {
            o.text = Tables.encode(rows)
            o.w = cw * rows.first().size
            o.h = rh * rows.size
        }
        board.sceneChanged()
    }
    refreshSelectionBar()
}

/** Recap: the page redrawn stroke by stroke, over the board; the page itself is untouched. */
internal fun MainActivity.showReplay() {
    if (store.page.visibleItems().none { it.pane == board.activePane }) {
        toast(tr("This page is empty", "این صفحه خالی است"))
        return
    }
    replay?.let { canvasHost.removeView(it) }
    val page = store.page
    replay = Replay(this, page, board.activePane, board.renderer, replayMarks[page.id], { replayMarks[page.id] = page.items.map { it.id }.toSet() }) {
        replay?.let { canvasHost.removeView(it) }
        replay = null
    }.also { canvasHost.addView(it, FrameLayout.LayoutParams(-1, -1)) }
}
internal fun MainActivity.showSpotlight() {
    spotlight?.let { canvasHost.removeView(it) }
    spotlight = Spotlight(this) {
        spotlight?.let { canvasHost.removeView(it) }
        spotlight = null
    }.also { canvasHost.addView(it, FrameLayout.LayoutParams(-1, -1)) }
}

/** Every page background, named for teachers: also staff paper for music, four-line paper for first writing, axes for graphs. */
internal fun MainActivity.patternChoices() = Subjects.patternsFirst(
    Subjects.current(this),
    listOf(
        "plain" to tr("Plain", "ساده"), "dots" to s("dots"), "grid" to s("grid"), "ruled" to s("ruled"), "hatch" to tr("Hatched", "هاشور"),
        "isometric" to tr("Isometric", "ایزومتریک"), "axes" to tr("Axes", "محور مختصات"), "staff" to tr("Music staff", "حامل موسیقی"), "handwriting" to tr("Handwriting", "خط‌نویسی")
    )
)
