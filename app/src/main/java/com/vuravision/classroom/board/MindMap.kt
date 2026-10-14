package com.vuravision.classroom.board

import kotlin.math.max
import kotlin.math.min

object MindMap {
    /** Ink belongs to a node when its first sample is inside that node. */
    fun attach(page: Page, stroke: Item, firstX: Float, firstY: Float, canEdit: (Item) -> Boolean = { true }): Boolean {
        val node = page.items.asReversed().firstOrNull {
            it.kind == "sticky" &&
                it.shape == "mindnode" &&
                it.pane == stroke.pane &&
                page.editable(it) &&
                !it.locked &&
                canEdit(it) &&
                it.local(firstX, firstY).let { p -> p.first in 0f..it.w && p.second in 0f..it.h }
        } ?: return false
        stroke.parentNode = node.id
        stroke.layerId = node.layerId
        val padding = 12f
        val corners = listOf(0f to 0f, stroke.w to 0f, stroke.w to stroke.h, 0f to stroke.h).map { p -> stroke.global(p.first, p.second).let { node.local(it.first, it.second) } }
        val left = min(0f, corners.minOf { it.first } - padding)
        val top = min(0f, corners.minOf { it.second } - padding)
        val right = max(node.w, corners.maxOf { it.first } + padding)
        val bottom = max(node.h, corners.maxOf { it.second } + padding)
        val center = node.global((left + right) / 2, (top + bottom) / 2)
        node.w = right - left
        node.h = bottom - top
        node.x = center.first - node.w / 2
        node.y = center.second - node.h / 2
        return true
    }

    fun group(page: Page, roots: List<Item>): List<Item> {
        val ids = roots.mapTo(mutableSetOf()) { it.id }
        val children = page.items.groupBy { it.parentNode }
        val queue = java.util.ArrayDeque(roots)
        while (queue.isNotEmpty()) {
            val parent = queue.removeFirst()
            children[parent.id].orEmpty().forEach { child -> if (child.pane == parent.pane && ids.add(child.id))queue.add(child) }
        }
        return page.items.filter { it.id in ids && page.editable(it) && !it.locked }
    }
}
