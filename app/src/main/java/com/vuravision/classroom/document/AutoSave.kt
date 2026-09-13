package com.vuravision.classroom.document

import android.content.Context
import java.io.File

class AutoSave(private val context: Context) {
    private val file get()= File(context.filesDir,"recovery/last_session.vura")
    fun save(doc: BoardDocument) { file.parentFile?.mkdirs(); val tmp=File(file.parentFile,"last_session.tmp"); tmp.outputStream().use { VuraArchive.write(context,doc,it) }; if(file.exists()) file.delete(); tmp.renameTo(file) }
    fun restore(): BoardDocument? = if(file.exists()) runCatching { file.inputStream().use { VuraArchive.read(context,it) } }.getOrNull() else null
}
