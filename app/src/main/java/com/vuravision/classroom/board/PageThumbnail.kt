package com.vuravision.classroom.board

import android.content.Context
import android.graphics.Canvas
import android.view.View
import com.vuravision.classroom.ui.field

/** ListView binds only visible page previews. No full-document bitmap allocation. */
class PageThumbnail(context: Context, private val renderer: Renderer) : View(context) {
    var page: Page? = null
        set(value) {
            field = value
            invalidate()
        }
    override fun onDraw(canvas: Canvas) {
        page?.let { renderer.page(canvas, it, width, height, false) }
    }
}
