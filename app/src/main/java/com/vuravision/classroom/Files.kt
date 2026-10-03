package com.vuravision.classroom

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.google.gson.Gson
import java.io.*
import java.util.concurrent.Executors
import java.util.zip.*

fun boundedCopy(input: InputStream, out: OutputStream, max: Long): Long {
    val buffer = ByteArray(32768)
    var total = 0L
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        total += n
        require(total <= max) { "File is too large" }
        out.write(buffer, 0, n)
    }
    return total
}

class Media(val context: Context) {
    val dir = File(context.filesDir, "media").apply { mkdirs() }
    private val cache =
        object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
            override fun sizeOf(k: String, v: Bitmap) = v.byteCount
        }
    private class RenderJob(val priority:Int, val sequence:Long, val action:()->Unit):Runnable,Comparable<RenderJob> {
        override fun run()=action()
        override fun compareTo(other:RenderJob)=compareValuesBy(this,other,{it.priority},{it.sequence})
    }
    private val sequence=java.util.concurrent.atomic.AtomicLong()
    private val pdfWorker=java.util.concurrent.ThreadPoolExecutor(1,1,0L,java.util.concurrent.TimeUnit.MILLISECONDS,java.util.concurrent.PriorityBlockingQueue<Runnable>())
    private val imageWorker=Executors.newSingleThreadExecutor()
    private val pending = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val failed = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private class Session(file:File):java.io.Closeable {
        private val fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer=try{PdfRenderer(fd)}catch(e:Exception){fd.close();throw e}
        override fun close(){renderer.close();fd.close()}
    }
    private val sessions=linkedMapOf<String,Session>()
    private var closed=false
    private fun <T> withPdf(name:String, action:(PdfRenderer)->T):T = synchronized(sessions) {
        check(!closed){"Media is closed"}
        val session=sessions.remove(name)?:Session(file(name))
        sessions[name]=session
        while(sessions.size>2){val first=sessions.keys.first();sessions.remove(first)?.close()}
        action(session.renderer)
    }
    var assetReady:(String)->Unit = {}
    var ready: () -> Unit = {}
    var error: (String) -> Unit = {}

    fun file(name: String): File {
        require(name.matches(Regex("[a-zA-Z0-9._-]+")))
        return File(dir, name)
    }

    fun import(input: InputStream, ext: String): String {
        val name = "${newId()}.$ext"
        val f = file(name)
        try {
            input.use { i -> f.outputStream().use { boundedCopy(i, it, 64L * 1024 * 1024) } }
            return name
        } catch (e: Exception) {
            f.delete()
            throw e
        }
    }

    fun save(bitmap: Bitmap): String {
        val name = "${newId()}.png"
        file(name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return name
    }

    fun pdfCount(name:String)=withPdf(name){it.pageCount}
    fun pdfSizes(name:String):List<Pair<Float,Float>> = withPdf(name){pdf->
        require(pdf.pageCount in 1..1000){"PDF supports up to 1000 pages"}
        (0 until pdf.pageCount).map { i->pdf.openPage(i).use{it.width.toFloat() to it.height.toFloat()} }
    }
    private fun quality(edge:Int)=when {edge<=768->768;edge<=1536->1536;else->2560}
    fun image(item: Item, sync: Boolean = false, edge:Int=1600, priority:Int=0): Bitmap? {
        val size=if(item.kind=="pdf")quality(edge) else 2048
        val prefix=item.asset+":"+item.pdfPage+":"
        val key=prefix+size
        cache.get(key)?.let{return it}
        if(sync)return load(item,size).also{cache.put(key,it)}
        val fallback=if(item.kind=="pdf") listOf(768,1536,2560).mapNotNull{cache.get(prefix+it)}.firstOrNull() else null
        if(failed.contains(key))return fallback
        if(pending.add(key)){
            val copy=item.copy()
            val action={
                try { cache.put(key,load(copy,size));assetReady(copy.asset) }
                catch(e:Exception){failed.add(key);error(e.javaClass.simpleName)}
                finally { pending.remove(key);ready() }
            }
            if(item.kind=="pdf")pdfWorker.execute(RenderJob(priority,sequence.getAndIncrement(),action))
            else imageWorker.execute(action)
        }
        return fallback
    }
    fun prefetchPdf(asset:String,page:Int,count:Int){
        if(page in 0 until count)image(Item(kind="pdf",asset=asset,pdfPage=page,pageCount=count),edge=768,priority=10)
    }

    private fun load(item: Item, edge:Int): Bitmap {
        if (item.kind == "pdf") return withPdf(item.asset){pdf->
            pdf.openPage(item.pdfPage).use{p->
                val scale=edge.toFloat()/maxOf(p.width,p.height)
                val b=Bitmap.createBitmap((p.width*scale).toInt().coerceAtLeast(1),(p.height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
                try{b.eraseColor(Color.WHITE);p.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);b}catch(e:Exception){b.recycle();throw e}
            }
        }
        val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file(item.asset).path, opt)
        require(opt.outWidth > 0 && opt.outHeight > 0) { "Invalid image" }
        var factor = 1
        while (maxOf(opt.outWidth, opt.outHeight) / factor > 2048) factor *= 2
        return requireNotNull(
            BitmapFactory.decodeFile(
                file(item.asset).path,
                BitmapFactory.Options().apply { inSampleSize = factor },
            )
        )
    }

    fun close() {
        imageWorker.shutdown()
        pdfWorker.shutdown()
        // Closing from a background thread avoids blocking the activity on a PDF render.
        Executors.newSingleThreadExecutor().let{cleanup->cleanup.execute{synchronized(sessions){closed=true;sessions.values.forEach{it.close()};sessions.clear()};cache.evictAll()};cleanup.shutdown()}
    }

    fun clear() {
        cache.evictAll()
        failed.clear()
    }
}

class LessonFiles(private val media: Media) {
    fun verify(file:File){
        check(file.length()>0){"Empty archive"}
        ZipFile(file).use{zip->
            val entry=requireNotNull(zip.getEntry("document.json")){"Missing document"}
            val doc=zip.getInputStream(entry).bufferedReader().use{Gson().fromJson(it,Lesson::class.java)}
            doc.validate()
            doc.allAssetItems().filter{it.asset.isNotEmpty()}.forEach{check(zip.getEntry("assets/${it.asset}")!=null){"Missing asset"}}
        }
    }
    fun write(doc: Lesson, out: OutputStream) {
        doc.validate()
        ZipOutputStream(BufferedOutputStream(out)).use { z ->
            fun entry(n: String, b: ByteArray) {
                z.putNextEntry(ZipEntry(n))
                z.write(b)
                z.closeEntry()
            }
            entry("manifest.json", """{"format":"VuraVision","schema":3}""".toByteArray())
            entry("document.json", Gson().toJson(doc).toByteArray())
            doc.allAssetItems()
                .map { it.asset }
                .filter { it.isNotEmpty() }
                .toSet()
                .forEach { n ->
                    z.setLevel(Deflater.NO_COMPRESSION)
                    z.putNextEntry(ZipEntry("assets/$n"))
                    media.file(n).inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
        }
    }

    fun read(input: InputStream, keepSourceUris:Boolean=false): Lesson {
        val staged = File(media.context.cacheDir, "import-${newId()}").apply { mkdirs() }
        var json: String? = null
        var total = 0L
        val seen = mutableSetOf<String>()
        val created = mutableListOf<File>()
        try {
            ZipInputStream(BufferedInputStream(input)).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    require(seen.add(e.name) && seen.size <= 5000) { "Invalid archive entries" }
                    val out = ByteArrayOutputStream()
                    total += boundedCopy(z, out, 64L * 1024 * 1024)
                    require(total <= 128L * 1024 * 1024) { "Archive too large" }
                    when {
                        e.name == "document.json" -> {
                            require(out.size() <= 16 * 1024 * 1024)
                            json = out.toString("UTF-8")
                        }
                        e.name == "manifest.json" ->
                            require(out.toString("UTF-8").contains("VuraVision"))
                        e.name.startsWith("assets/") -> {
                            val name = e.name.removePrefix("assets/")
                            require(name.matches(Regex("[a-zA-Z0-9._-]+"))) {
                                "Unsafe archive path"
                            }
                            File(staged, name).writeBytes(out.toByteArray())
                        }
                        else -> error("Unknown archive entry")
                    }
                    z.closeEntry()
                }
            }
            val doc =
                Gson().fromJson(requireNotNull(json) { "Missing document" }, Lesson::class.java)
            doc.validate()
            val names =
                doc.allAssetItems().map { it.asset }.filter { it.isNotEmpty() }.toSet()
            val mapping = mutableMapOf<String, String>()
            names.forEach { name ->
                val src = File(staged, name)
                require(src.isFile) { "Missing lesson asset" }
                val existing = media.file(name)
                if (
                    existing.isFile &&
                        existing.length() == src.length() &&
                        sha(existing) == sha(src)
                )
                    mapping[name] = name
                else {
                    val n = media.import(src.inputStream(), src.extension)
                    mapping[name] = n
                    created.add(media.file(n))
                }
            }
            doc.allAssetItems()
                .filter { it.asset.isNotEmpty() }
                .forEach { it.asset = mapping.getValue(it.asset) }
            doc.pdf?.let{it.asset=mapping.getValue(it.asset);if(!keepSourceUris){it.sourceUri="";it.originalHash=""}}
            return doc
        } catch (e: Exception) {
            created.forEach { it.delete() }
            throw e
        } finally {
            staged.deleteRecursively()
        }
    }

    fun atomic(doc: Lesson, destination: File) {
        destination.parentFile?.mkdirs()
        val tmp = File(destination.path + ".tmp")
        tmp.outputStream().use { write(doc, it) }
        verify(tmp)
        RandomAccessFile(tmp, "rw").use { it.fd.sync() }
        val backup = File(destination.path + ".bak")
        if (destination.exists()) {
            backup.delete()
            check(destination.renameTo(backup)) { "Could not preserve previous save" }
        }
        if(!tmp.renameTo(destination)){
            if(backup.exists() && !destination.exists())backup.renameTo(destination)
            error("Could not commit save; previous version preserved")
        }
    }

    private fun sha(file: File): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val b = ByteArray(32768)
            while (true) {
                val n = input.read(b)
                if (n < 0) break
                md.update(b, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
