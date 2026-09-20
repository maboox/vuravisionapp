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
    private val worker = Executors.newSingleThreadExecutor()
    private val pending = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val failed = java.util.Collections.synchronizedSet(mutableSetOf<String>())
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

    fun pdfCount(name: String) =
        ParcelFileDescriptor.open(file(name), ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { it.pageCount }
        }

    fun image(item: Item, sync: Boolean = false): Bitmap? {
        val key = item.asset + ":" + item.pdfPage
        cache.get(key)?.let {
            return it
        }
        if (sync) return load(item).also { cache.put(key, it) }
        if (failed.contains(key)) return null
        if (pending.add(key)) {
            val copy = item.deepCopy()
            worker.execute {
                try {
                    cache.put(key, load(copy))
                } catch (e: Exception) {
                    failed.add(key)
                    error(e.javaClass.simpleName)
                } finally {
                    pending.remove(key)
                    ready()
                }
            }
        }
        return null
    }

    private fun load(item: Item): Bitmap {
        if (item.kind == "pdf")
            return ParcelFileDescriptor.open(file(item.asset), ParcelFileDescriptor.MODE_READ_ONLY)
                .use { fd ->
                    PdfRenderer(fd).use { pdf ->
                        pdf.openPage(item.pdfPage).use { p ->
                            val scale = 1600f / maxOf(p.width, p.height)
                            val b =
                                Bitmap.createBitmap(
                                    (p.width * scale).toInt().coerceAtLeast(1),
                                    (p.height * scale).toInt().coerceAtLeast(1),
                                    Bitmap.Config.ARGB_8888,
                                )
                            b.eraseColor(Color.WHITE)
                            p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            b
                        }
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
        worker.shutdown()
    }

    fun clear() {
        cache.evictAll()
        failed.clear()
    }
}

class LessonFiles(private val media: Media) {
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
            doc.pages
                .flatMap { it.items }
                .map { it.asset }
                .filter { it.isNotEmpty() }
                .toSet()
                .forEach { n ->
                    z.putNextEntry(ZipEntry("assets/$n"))
                    media.file(n).inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
        }
    }

    fun read(input: InputStream): Lesson {
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
                doc.pages.flatMap { it.items }.map { it.asset }.filter { it.isNotEmpty() }.toSet()
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
            doc.pages
                .flatMap { it.items }
                .filter { it.asset.isNotEmpty() }
                .forEach { it.asset = mapping.getValue(it.asset) }
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
        RandomAccessFile(tmp, "rw").use { it.fd.sync() }
        val backup = File(destination.path + ".bak")
        if (destination.exists()) {
            backup.delete()
            check(destination.renameTo(backup)) { "Could not preserve previous save" }
        }
        check(tmp.renameTo(destination)) { "Could not commit save" }
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
