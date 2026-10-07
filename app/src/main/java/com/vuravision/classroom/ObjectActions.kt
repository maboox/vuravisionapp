package com.vuravision.classroom
object ObjectActions {
    fun canColor(o:Item)=o.kind !in listOf("image","pdf","graph","periodic") && o.shape !in GeometryTools.keys
    fun canSmart(o:Item)=o.kind in listOf("ink","text","sticky") && o.shape !in listOf("mindnode","element_card")
}
object HistorySettings {
    fun load(c:android.content.Context)=c.getSharedPreferences("vura",0).getInt("historyLimit",50).coerceIn(10,200)
}
