package com.vuravision.classroom
import android.app.*
import android.content.*
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.*
import android.view.*
import android.widget.*
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vuravision.classroom.board.Lesson
import com.vuravision.classroom.board.Renderer
import com.vuravision.classroom.board.newId
import com.vuravision.classroom.board.reset
import com.vuravision.classroom.room.Sharing
import com.vuravision.classroom.ui.MUTED
import com.vuravision.classroom.ui.button
import com.vuravision.classroom.ui.column
import com.vuravision.classroom.ui.dp
import com.vuravision.classroom.ui.label
import com.vuravision.classroom.ui.pad
import com.vuravision.classroom.ui.s
import com.vuravision.classroom.ui.tr
import java.io.File
import kotlin.math.*

// Lesson files: open, save, recent, export and sharing with the class (split from MainActivity, item 11).

internal fun MainActivity.lessonFile(id: String = documentId) = File(filesDir, "lessons/$id.vura")

internal fun MainActivity.persist() {
    if (!dirty || destroyed)return
    if (whiteboard.hasActiveInteraction || board.hasActiveInteraction) {
        handler.removeCallbacks(autosave)
        handler.postDelayed(autosave, 1000)
        return
    }
    val doc = projectStore.lesson.copyForSave()
    val id = documentId
    dirty = false
    lastSaveTicket = saveQueue.submit(doc, lessonFile()) { ticket, failure ->
        if (!destroyed) {
            handler.post {
                if (!destroyed && id == documentId && ticket == lastSaveTicket) {
                    if (failure == null) {
                        if (!dirty)status.text = s("saved")
                    } else {
                        dirty = true
                        lastError = failure.message.orEmpty()
                        status.text = s("save_error")
                        toast(s("save_error"))
                    }
                }
            }
        }
    }
}

internal fun MainActivity.loadFile(f: File, changeId: Boolean = true) {
    handler.removeCallbacks(autosave)
    persist()
    work({
        try {
            files.read(f.inputStream(), true) to false
        } catch (e: Exception) {
            val backup = File(f.path + ".bak")
            if (!backup.exists()) throw e
            files.read(backup.inputStream(), true) to true
        }
    }) { (doc, backup) ->
        if (changeId || backup) {
            documentId = if (backup) newId() else f.nameWithoutExtension
            prefs.edit().putString("current", documentId).apply()
        }
        projectStore.replace(doc)
        whiteboard.clearSelection()
        whiteboard.reset()
        if (backup) {
            status.text = s("restored_backup")
            toast(s("restored_backup"))
        }
    }
}

internal fun MainActivity.rename() {
    input(s("lesson"), projectStore.lesson.title) { value ->
        require(value.isNotBlank())
        projectStore.editMetadata { projectStore.lesson.title = value.take(100) }
    }
}

internal fun MainActivity.fileMenu() {
    if (projectStore.lesson.pdf != null) {
        choices(s("files"), listOf(tr("PDF file and workspace", "فایل PDF و فضای مطالعه"), tr("Board projects and exports", "پروژه‌ها و خروجی تخته"))) { i -> if (i == 0)pdfMenu()else projectFileMenu() }
    } else {
        projectFileMenu()
    }
}
internal fun MainActivity.projectFileMenu() {
    if (rooms?.active == true) {
        choices(s("files"), listOf("save", "export_pdf", "export_png", "export_jpg")) { i -> if (i == 0)export("vura", true) { createDocument(it, "application/octet-stream") } else exportChoice(listOf("pdf", "png", "jpg")[i - 1]) }
        return
    }
    activateBoard(whiteboard)
    choices(
        s("files"),
        listOf("new", "open", "save", "recent", "export_pdf", "export_png", "export_jpg", "rename")
    ) {
        when (it) {
            0 ->
                withPdfExit {
                    confirm(s("new_confirm")) {
                        persist()
                        documentId = newId()
                        prefs.edit().putString("current", documentId).apply()
                        projectStore.replace(Lesson(title = s("lesson"), pages = mutableListOf(newBoardPage(null))))
                        whiteboard.clearSelection()
                        whiteboard.reset()
                    }
                }
            1 -> withPdfExit { pick("application/octet-stream", 101) }
            2 -> export("vura", true) { createDocument(it, "application/octet-stream") }
            3 -> withPdfExit { recent() }
            4 -> exportChoice("pdf")
            5 -> exportChoice("png")
            6 -> exportChoice("jpg")
            7 -> rename()
        }
    }
}

internal fun MainActivity.recent() {
    val list =
        File(filesDir, "lessons")
            .listFiles()
            ?.filter { it.extension == "vura" }
            ?.sortedByDescending { it.lastModified() }
            ?.take(30)
            .orEmpty()
    if (list.isEmpty()) {
        toast(s("empty"))
        return
    }
    work({
        list.map { f ->
            val name =
                runCatching {
                    java.util.zip.ZipFile(f).use { z ->
                        com.google.gson
                            .Gson()
                            .fromJson(
                                z.getInputStream(z.getEntry("document.json")).reader(),
                                Lesson::class.java
                            )
                            .title
                    }
                }
                    .getOrDefault(f.name)
            "$name  ·  ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(f.lastModified()))}"
        }
    }) { names ->
        MaterialAlertDialogBuilder(this)
            .setTitle(s("recent"))
            .setItems(names.toTypedArray()) { _, i -> loadFile(list[i]) }
            .setNegativeButton(s("cancel"), null)
            .show()
    }
}

internal fun MainActivity.exportChoice(ext: String) {
    if (ext == "pdf" && projectStore.lesson.pdf != null) {
        pdfMenu()
        return
    }
    if (ext == "pdf") {
        choices(s("export_pdf"), listOf("current_page", "all_pages")) { i ->
            export(ext, i == 1) { createDocument(it, "application/pdf") }
        }
    } else {
        export(ext, false) { createDocument(it, "image/${if (ext == "jpg")"jpeg"else "png"}") }
    }
}

internal fun MainActivity.export(ext: String, all: Boolean, done: (File) -> Unit) {
    val doc = projectStore.lesson.copyForSave()
    work({
        val folder = File(cacheDir, "exports").apply { mkdirs() }
        val target = File(folder, "VuraVision-${System.currentTimeMillis()}.$ext")
        val renderer = Renderer(media)
        if (ext == "vura") {
            files.atomic(doc, target)
        } else if (ext == "pdf") {
            val pdf = PdfDocument()
            try {
                val pages = if (all) doc.pages else listOf(doc.pages[doc.current])
                pages.forEachIndexed { i, page ->
                    val p =
                        pdf.startPage(PdfDocument.PageInfo.Builder(1190, 842, i + 1).create())
                    renderer.page(p.canvas, page, 1190, 842, true)
                    pdf.finishPage(p)
                }
                target.outputStream().use { pdf.writeTo(it) }
            } finally {
                pdf.close()
            }
        } else {
            val bitmap = Bitmap.createBitmap(1920, 1200, Bitmap.Config.ARGB_8888)
            renderer.page(Canvas(bitmap), doc.pages[doc.current], 1920, 1200, true)
            target.outputStream().use {
                check(
                    bitmap.compress(
                        if (ext == "jpg") Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG,
                        92,
                        it
                    )
                ) { "Image export failed" }
            }
            bitmap.recycle()
        }
        check(target.isFile && target.length() > 0) { "Export is empty" }
        target
    }) {
        done(it)
    }
}

internal fun MainActivity.createDocument(file: File, mime: String) {
    pendingExport = file
    prefs.edit().putString("pendingExport", file.name).apply()
    try {
        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = mime
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_TITLE, file.name)
            },
            104
        )
    } catch (e: Exception) {
        error(e)
    }
}

internal fun MainActivity.shareOptions() {
    val choices = arrayOf(
        tr("Send PDF via another app / cloud", "فرستادن PDF با برنامهٔ دیگر / فضای ابری"),
        tr("QR for a public HTTPS link", "QR برای لینک عمومی HTTPS"),
        tr("QR on the same local network", "QR روی شبکهٔ محلی"),
        // Where a teacher looks for "share with the class"; it was only in the hidden Engineering menu.
        tr("Live class on this network (experimental)", "کلاس زنده روی همین شبکه (آزمایشی)")
    )
    MaterialAlertDialogBuilder(this).setTitle(s("share"))
        .setItems(choices) { _, index ->
            when (index) {
                0 -> sharePdf()
                1 -> publicQr()
                2 -> share()
                else -> sharedRoom()
            }
        }
        .setNegativeButton(s("cancel"), null).show()
}
internal fun MainActivity.sharePdf() {
    export("pdf", true) { file ->
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newRawUri(file.name, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    s("share")
                )
            )
        } catch (e: Exception) {
            error(e)
        }
    }
}
internal fun MainActivity.publicQr() {
    input(tr("Paste a public HTTPS link to your uploaded PDF", "لینک عمومی HTTPS فایل PDF بارگذاری‌شده را وارد کنید")) { raw ->
        val url = raw.trim()
        val uri = android.net.Uri.parse(url)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.authority?.contains('@') != true) {
            tr("Enter a valid public HTTPS address", "نشانی معتبر و عمومی HTTPS وارد کنید")
        }
        val c = column().apply {
            pad(20)
            gravity = Gravity.CENTER
        }
        c.addView(label(tr("Anyone with access to this public link can open it from another network. Check sharing permissions in the cloud app.", "هر کسی که به این لینک عمومی دسترسی داشته باشد می‌تواند از شبکهٔ دیگر آن را باز کند. مجوزهای اشتراک را در برنامهٔ ابری بررسی کنید."), 14f, MUTED))
        c.addView(
            ImageView(this).apply {
                setImageBitmap(Sharing.qr(url))
                contentDescription = url
            },
            LinearLayout.LayoutParams(dp(230), dp(230))
        )
        c.addView(label(url, 12f).apply { setTextIsSelectable(true) })
        c.addView(
            button(s("copy")) {
                (getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("VuraVision", url))
                toast(s("done"))
            }
        )
        dialog(s("share"), c)
    }
}
internal fun MainActivity.share() {
    export("pdf", true) { file ->
        try {
            shareDialog?.dismiss()
            sharing?.stop()
            val server = Sharing(file, "application/pdf")
            server.start(5000, false)
            val urls = server.urls(this)
            val url = urls.firstOrNull()
            if (url == null) {
                server.stop()
                toast(s("no_network"))
                return@export
            }
            sharing = server
            val c =
                column().apply {
                    pad(20)
                    gravity = Gravity.CENTER
                }
            c.addView(label(s("share_help"), 15f))
            val qr = ImageView(this).apply {
                setImageBitmap(Sharing.qr(url))
                contentDescription = url
            }
            c.addView(qr, LinearLayout.LayoutParams(dp(230), dp(230)))
            val address = label(url, 12f).apply { setTextIsSelectable(true) }
            c.addView(address)
            c.addView(
                button(s("copy")) {
                    (getSystemService(Context.CLIPBOARD_SERVICE)as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("VuraVision", address.text))
                    toast(s("done"))
                }
            )
            if (urls.size > 1) {
                c.addView(
                    button(s("network_address")) {
                        MaterialAlertDialogBuilder(this).setItems(urls.toTypedArray()) { _, index ->
                            address.text = urls[index]
                            qr.setImageBitmap(Sharing.qr(urls[index]))
                            qr.contentDescription = urls[index]
                        }.show()
                    }
                )
            }
            c.addView(label(s("share_troubleshoot"), 12f, MUTED))
            val count = label("${s("downloads")}: 0", 12f, MUTED)
            c.addView(count)
            val d = dialog(s("share"), c)
            shareDialog = d
            val update =
                object : Runnable {
                    override fun run() {
                        if (d.isShowing) {
                            count.text = "${s("downloads")}: ${server.downloads}"
                            handler.postDelayed(this, 1000)
                        }
                    }
                }
            handler.post(update)
            handler.postDelayed(
                {
                    if (sharing === server) {
                        server.stop()
                        d.dismiss()
                        toast(s("expired"))
                    }
                },
                1800000
            )
            c.addView(
                button(s("stop_sharing")) {
                    server.stop()
                    if (sharing === server)sharing = null
                    d.dismiss()
                }
            )
            d.setOnDismissListener {
                handler.removeCallbacks(update)
                server.stop()
                if (sharing === server)sharing = null
                shareDialog = null
            }
        } catch (e: Exception) {
            error(e)
        }
    }
}

internal fun MainActivity.pick(mime: String, code: Int) {
    try {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = mime
                if (code == 101) type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                if (code == 103)addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            },
            code
        )
    } catch (e: Exception) {
        error(e)
    }
}
