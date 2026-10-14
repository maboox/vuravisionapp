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
import com.vuravision.classroom.board.Item
import com.vuravision.classroom.board.Lesson
import com.vuravision.classroom.board.Page
import com.vuravision.classroom.pdf.allAssetItems
import com.vuravision.classroom.board.newId

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
    // Memory failures are transient (for example after leaving fullscreen); retry them after a pause instead of forever showing a blank page.
    private val memoryFailed = java.util.concurrent.ConcurrentHashMap<String,Long>()
    private class Session(file:File):java.io.Closeable {
        private val fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer=try{PdfRenderer(fd)}catch(e:Exception){fd.close();throw e}
        override fun close(){renderer.close();fd.close()}
    }
    private val sessions=linkedMapOf<String,Session>()
    @Volatile private var closed=false
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
        require(AssetNames.valid(name))
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
        try { file(name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Image save failed" } } }
        catch(e:Exception){file(name).delete();throw e}
        return name
    }

    fun pdfCount(name:String)=withPdf(name){it.pageCount}
    fun pdfSizes(name:String):List<Pair<Float,Float>> = withPdf(name){pdf->
        require(pdf.pageCount in 1..1000){"PDF supports up to 1000 pages"}
        (0 until pdf.pageCount).map { i->pdf.openPage(i).use{it.width.toFloat() to it.height.toFloat()} }
    }
    private fun quality(edge:Int)=when {edge<=256->256;edge<=768->768;edge<=1536->1536;else->2560}
    fun image(item: Item, sync: Boolean = false, edge:Int=1600, priority:Int=0, retained:Bitmap?=null): Bitmap? {
        if(closed)return null
        val size=quality(edge)
        // A mounted PDF page keeps its last frame even when the shared LRU evicts it.
        val held=retained?.takeIf{item.kind=="pdf" && !sync && !it.isRecycled}
        if(held!=null && maxOf(held.width,held.height)>=size-1)return held
        val prefix=item.asset+":"+item.pdfPage+":"
        val key=prefix+size
        cache.get(key)?.let{return it}
        if(sync)return load(item,size).also{cache.put(key,it)}
        val fallback=(listOfNotNull(held)+listOf(2560,1536,768,256).mapNotNull{cache.get(prefix+it)}).maxByOrNull{maxOf(it.width,it.height)}
        if(fallback!=null && maxOf(fallback.width,fallback.height)>=size-1)return fallback
        if(failed.contains(key))return fallback
        memoryFailed[key]?.let{if(android.os.SystemClock.uptimeMillis()-it<2000)return fallback else memoryFailed.remove(key)}
        if(pending.add(key)){
            val copy=item.copy()
            val action:()->Unit={
                try {
                    if(!closed){val bitmap=load(copy,size);if(!closed){cache.put(key,bitmap);assetReady(copy.asset)}else bitmap.recycle()}
                } catch(e:Exception){if(!closed){failed.add(key);error(e.javaClass.simpleName)}}
                catch(e:OutOfMemoryError){cache.evictAll();if(!closed){memoryFailed[key]=android.os.SystemClock.uptimeMillis();error("Image memory limit")
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({if(!closed)assetReady(copy.asset)},2100)}}
                finally { pending.remove(key);if(!closed)ready() }
            }
            try {
                if(item.kind=="pdf")pdfWorker.execute(RenderJob(priority,sequence.getAndIncrement(),action))
                else imageWorker.execute(action)
            }catch(_:java.util.concurrent.RejectedExecutionException){pending.remove(key)}
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
        while (maxOf(opt.outWidth, opt.outHeight) / factor > edge) factor *= 2
        return requireNotNull(
            BitmapFactory.decodeFile(
                file(item.asset).path,
                BitmapFactory.Options().apply { inSampleSize = factor },
            )
        )
    }

    fun close() {
        if(closed)return
        closed=true
        assetReady={};ready={};error={}
        imageWorker.shutdownNow();pdfWorker.shutdownNow();pending.clear()
        // An in-flight render owns its session until it leaves withPdf.
        Executors.newSingleThreadExecutor().let{cleanup->cleanup.execute{
            synchronized(sessions){sessions.values.forEach{runCatching{it.close()}};sessions.clear()}
            cache.evictAll()
        };cleanup.shutdown()}
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
            require(entry.size in 1..ArchiveLimits.DOCUMENT){"Invalid document size"}
            val doc=zip.getInputStream(entry).bufferedReader().use{requireNotNull(Gson().fromJson(it,Lesson::class.java))}
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
            z.putNextEntry(ZipEntry("document.json"))
            val limited=LimitedOutputStream(z,ArchiveLimits.DOCUMENT)
            val writer=OutputStreamWriter(limited,Charsets.UTF_8)
            Gson().toJson(doc,writer);writer.flush();z.closeEntry()
            var total=limited.count
            val assetNames=doc.allAssetItems().map{it.asset}.filter{it.isNotEmpty()}.toSet()
            require(assetNames.size<=4998){"Too many assets"}
            assetNames.forEach{name->val file=media.file(name);require(file.isFile && file.length()<=ArchiveLimits.ASSET){"Missing or oversized asset"};total+=file.length()}
            require(total+65536<=ArchiveLimits.TOTAL){"Archive too large"}
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
        val staged=File(media.context.cacheDir,"import-${newId()}").apply{check(mkdirs())}
        val document=File(staged,"document.json")
        val assets=File(staged,"assets").apply{check(mkdirs())}
        var total=0L
        val seen=mutableSetOf<String>()
        val created=mutableListOf<File>()
        try {
            ZipInputStream(BufferedInputStream(input)).use{zip->
                while(true){
                    val entry=zip.nextEntry?:break
                    require(!entry.isDirectory && seen.add(entry.name) && seen.size<=5000){"Invalid archive entries"}
                    val remaining=ArchiveLimits.TOTAL-total
                    when {
                        entry.name=="document.json" -> document.outputStream().use{total+=boundedCopy(zip,it,minOf(ArchiveLimits.DOCUMENT,remaining))}
                        entry.name=="manifest.json" -> {
                            val bytes=ByteArrayOutputStream()
                            total+=boundedCopy(zip,bytes,minOf(65536L,remaining))
                            require(bytes.toString("UTF-8").contains("VuraVision")){"Invalid manifest"}
                        }
                        entry.name.startsWith("assets/") -> {
                            val name=entry.name.removePrefix("assets/")
                            require(AssetNames.valid(name)){"Unsafe archive path"}
                            File(assets,name).outputStream().use{total+=boundedCopy(zip,it,minOf(ArchiveLimits.ASSET,remaining))}
                        }
                        else -> error("Unknown archive entry")
                    }
                    zip.closeEntry()
                }
            }
            require(document.isFile){"Missing document"}
            val doc=document.bufferedReader().use{requireNotNull(Gson().fromJson(it,Lesson::class.java)){"Missing document"}}
            doc.validate()
            val names=doc.allAssetItems().map{it.asset}.filter{it.isNotEmpty()}.toSet()
            val mapping=mutableMapOf<String,String>()
            names.forEach{name->
                val source=File(assets,name);require(source.isFile){"Missing lesson asset"}
                val existing=media.file(name)
                if(existing.isFile && existing.length()==source.length() && sha(existing)==sha(source))mapping[name]=name
                else {
                    val next=media.import(source.inputStream(),source.extension)
                    mapping[name]=next;created.add(media.file(next))
                }
            }
            doc.allAssetItems().filter{it.asset.isNotEmpty()}.forEach{it.asset=mapping.getValue(it.asset)}
            doc.pdf?.let{it.asset=mapping.getValue(it.asset);if(!keepSourceUris){it.sourceUri="";it.originalHash=""}}
            return doc
        }catch(e:Exception){created.forEach{it.delete()};throw e}
        finally{staged.deleteRecursively()}
    }

    fun atomic(doc: Lesson, destination: File) {
        destination.parentFile?.mkdirs()
        val tmp=File(destination.path+".tmp")
        val backup=File(destination.path+".bak")
        try {
            tmp.outputStream().use{write(doc,it)}
            verify(tmp)
            RandomAccessFile(tmp,"rw").use{it.fd.sync()}
            if(destination.exists()){
                check(!backup.exists()||backup.delete()){"Could not replace backup"}
                check(destination.renameTo(backup)){"Could not preserve previous save"}
            }
            if(!tmp.renameTo(destination)){
                if(backup.exists()&&!destination.exists())backup.renameTo(destination)
                error("Could not commit save; previous version preserved")
            }
        }finally{tmp.delete()}
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
