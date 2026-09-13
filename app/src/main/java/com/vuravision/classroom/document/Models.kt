package com.vuravision.classroom.document

import android.graphics.RectF
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class InkPoint(val x: Float, val y: Float, val pressure: Float = 1f)

sealed class CanvasObject(open val id: String, open var bounds: RectF) {
    data class InkStroke(
        override val id: String = UUID.randomUUID().toString(),
        override var bounds: RectF = RectF(),
        val points: MutableList<InkPoint> = mutableListOf(),
        var color: Int = 0xFF111111.toInt(),
        var width: Float = 5f,
        var alpha: Int = 255
    ) : CanvasObject(id, bounds)

    data class ImageObject(
        override val id: String = UUID.randomUUID().toString(),
        override var bounds: RectF,
        var localPath: String,
        var rotation: Float = 0f
    ) : CanvasObject(id, bounds)

    data class PdfObject(
        override val id: String = UUID.randomUUID().toString(),
        override var bounds: RectF,
        var localPath: String,
        var pageIndex: Int = 0,
        var pageCount: Int = 1
    ) : CanvasObject(id, bounds)
}

data class BoardDocument(
    val schemaVersion: Int = 1,
    val id: String = UUID.randomUUID().toString(),
    var title: String = "Untitled",
    val objects: MutableList<CanvasObject> = mutableListOf()
)

object DocumentJson {
    fun toJson(doc: BoardDocument): JSONObject = JSONObject().apply {
        put("schemaVersion", doc.schemaVersion); put("id", doc.id); put("title", doc.title)
        put("objects", JSONArray().apply { doc.objects.forEach { put(objectToJson(it)) } })
    }

    private fun rect(r: RectF) = JSONArray().apply { put(r.left); put(r.top); put(r.right); put(r.bottom) }
    private fun rect(a: JSONArray) = RectF(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.getDouble(2).toFloat(), a.getDouble(3).toFloat())

    private fun objectToJson(o: CanvasObject): JSONObject = JSONObject().apply {
        put("id", o.id); put("bounds", rect(o.bounds))
        when (o) {
            is CanvasObject.InkStroke -> { put("type","ink"); put("color",o.color); put("width",o.width); put("alpha",o.alpha)
                put("points", JSONArray().apply { o.points.forEach { p -> put(JSONArray().apply { put(p.x); put(p.y); put(p.pressure) }) } }) }
            }
            is CanvasObject.ImageObject -> { put("type","image"); put("path",o.localPath); put("rotation",o.rotation) }
            is CanvasObject.PdfObject -> { put("type","pdf"); put("path",o.localPath); put("pageIndex",o.pageIndex); put("pageCount",o.pageCount) }
        }
    }

    fun fromJson(json: JSONObject): BoardDocument {
        val doc = BoardDocument(json.optInt("schemaVersion",1), json.getString("id"), json.optString("title","Untitled"))
        val arr = json.getJSONArray("objects")
        for(i in 0 until arr.length()) {
            val o=arr.getJSONObject(i); val b=rect(o.getJSONArray("bounds")); val id=o.getString("id")
            when(o.getString("type")) {
                "ink" -> {
                    val pts=mutableListOf<InkPoint>(); val pa=o.getJSONArray("points")
                    for(j in 0 until pa.length()) { val p=pa.getJSONArray(j); pts += InkPoint(p.getDouble(0).toFloat(),p.getDouble(1).toFloat(),p.optDouble(2,1.0).toFloat()) }
                    doc.objects += CanvasObject.InkStroke(id,b,pts,o.getInt("color"),o.getDouble("width").toFloat(),o.optInt("alpha",255))
                }
                "image" -> doc.objects += CanvasObject.ImageObject(id,b,o.getString("path"),o.optDouble("rotation",0.0).toFloat())
                "pdf" -> doc.objects += CanvasObject.PdfObject(id,b,o.getString("path"),o.optInt("pageIndex",0),o.optInt("pageCount",1))
            }
        }
        return doc
    }
}
