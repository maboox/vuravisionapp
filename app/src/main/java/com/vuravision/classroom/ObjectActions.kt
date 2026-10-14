package com.vuravision.classroom

import com.vuravision.classroom.board.GeometryTools
import com.vuravision.classroom.board.Item
object ObjectActions {
    fun canColor(o: Item) = o.kind !in listOf("image", "pdf", "graph", "periodic") && o.shape !in GeometryTools.keys
    fun canSmart(o: Item) = o.kind in listOf("ink", "text", "sticky") && o.shape !in listOf("mindnode", "element_card")
}
object HistorySettings {
    fun load(c: android.content.Context) = c.getSharedPreferences("vura", 0).getInt("historyLimit", 50).coerceIn(10, 200)
}
