package com.vuravision.classroom.document

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONObject

object VuraArchive {
    fun write(context: Context, doc: BoardDocument, out: OutputStream) {
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"));
            zip.write(JSONObject().put("format","VuraVision").put("schemaVersion",doc.schemaVersion).toString(2).toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("document.json")); zip.write(DocumentJson.toJson(doc).toString().toByteArray()); zip.closeEntry()
            doc.objects.mapNotNull { when(it){ is CanvasObject.ImageObject -> it.localPath; is CanvasObject.PdfObject -> it.localPath; else -> null } }.distinct().forEach { path ->
                val f=File(path); if(f.exists()) { zip.putNextEntry(ZipEntry("assets/${f.name}")); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
            }
        }
    }

    fun read(context: Context, input: InputStream): BoardDocument {
        var json: String? = null; val extracted = mutableMapOf<String,String>()
        ZipInputStream(input.buffered()).use { zip ->
            var e=zip.nextEntry
            while(e!=null) {
                when {
                    e.name=="document.json" -> json=zip.readBytes().toString(Charsets.UTF_8)
                    e.name.startsWith("assets/") -> { val f=File(context.filesDir,"vura_assets/${System.currentTimeMillis()}_${File(e.name).name}"); f.parentFile?.mkdirs(); f.outputStream().use { zip.copyTo(it) }; extracted[File(e.name).name]=f.absolutePath }
                }
                zip.closeEntry(); e=zip.nextEntry
            }
        }
        val doc=DocumentJson.fromJson(JSONObject(json ?: error("document.json missing")))
        doc.objects.forEach { o -> when(o) { is CanvasObject.ImageObject -> extracted[File(o.localPath).name]?.let { o.localPath=it }; is CanvasObject.PdfObject -> extracted[File(o.localPath).name]?.let { o.localPath=it }; else -> Unit } }
        return doc
    }
}
