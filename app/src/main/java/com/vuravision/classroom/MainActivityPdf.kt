package com.vuravision.classroom
import android.app.*
import android.content.*
import android.graphics.*
import android.os.*
import android.view.*
import android.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vuravision.classroom.board.Item
import com.vuravision.classroom.board.Page
import com.vuravision.classroom.board.Renderer
import com.vuravision.classroom.pdf.PdfExport
import com.vuravision.classroom.pdf.PdfPane
import com.vuravision.classroom.pdf.PdfRegionSelector
import com.vuravision.classroom.pdf.PdfSheet
import com.vuravision.classroom.pdf.PdfSplitLayout
import com.vuravision.classroom.pdf.PdfWorkspaceState
import com.vuravision.classroom.ui.dp
import com.vuravision.classroom.ui.s
import com.vuravision.classroom.ui.tr
import java.io.File
import kotlin.math.*

// The PDF workspace beside the board (split from MainActivity, item 11).

internal fun MainActivity.syncPdfWorkspace() {
    if (!workspaceHostReady)return
    val state = projectStore.lesson.pdf
    if (shownPdf === state)return
    shownPdf = state
    if (board !== whiteboard) {
        whiteboard.copyToolsFrom(board)
        board = whiteboard
    }
    pdfPane?.dispose()
    pdfPane = null
    (whiteboard.parent as? ViewGroup)?.removeView(whiteboard)
    workspaceHost.removeAllViews()
    // PDF fullscreen hides the board (GONE) inside the split layout; it must be visible again on its own.
    whiteboard.visibility = View.VISIBLE
    if (state == null) {
        workspaceHost.addView(whiteboard, FrameLayout.LayoutParams(-1, -1))
        whiteboard.sceneChanged()
        return
    }
    val reader = PdfPane(this, state, media, { board }, { activateBoard(it) }, { projectChanged() }, { projectChanged() }, { pdfMenu() }, {
        savePens()
        refreshDock()
    })
    pdfPane = reader
    val split = PdfSplitLayout(this, state, whiteboard, reader) { projectChanged() }
    workspaceHost.addView(split, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(78), dp(76), dp(12), dp(76)) })
}
internal fun MainActivity.refreshPdfPresentation() {
    (pdfPane?.parent as? PdfSplitLayout)?.applyPresentation()
    workspaceHost.post {
        if (!destroyed) {
            pdfPane?.redraw()
            whiteboard.sceneChanged()
        }
    }
}
internal fun MainActivity.choosePdfImport() {
    navigationBuilder(tr("Open PDF", "بازکردن PDF"))
        .setItems(arrayOf(tr("PDF object on board", "PDF به‌صورت شیء روی تخته"), tr("Scrollable PDF beside board", "PDF قابل اسکرول کنار تخته"))) { _, i ->
            pendingPdfMode = i
            if (i == 1)withPdfExit { pick("application/pdf", 103) } else pick("application/pdf", 103)
        }.show().also { shown(it) }
}
internal fun MainActivity.openPdf(uri: android.net.Uri, flags: Int) {
    try {
        contentResolver.takePersistableUriPermission(uri, flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
    } catch (_: SecurityException) {}
    val mode = pendingPdfMode
    work({
        val asset = media.import(requireNotNull(contentResolver.openInputStream(uri)), "pdf")
        try {
            if (mode == 0) {
                val item = Item(kind = "pdf", asset = asset, pageCount = media.pdfCount(asset), w = 560f)
                val image = requireNotNull(media.image(item, true))
                item.h = item.w * image.height / image.width
                item to null
            } else {
                val sizes = media.pdfSizes(asset)
                val title = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst())c.getString(0)else "PDF" } ?: "PDF"
                val state = PdfWorkspaceState(asset = asset, sourceUri = uri.toString(), title = title.take(250), originalHash = PdfExport.hash(media.file(asset).inputStream()))
                state.sheets = sizes.mapIndexed { i, (w, h) ->
                    PdfSheet(w, h, Page(background = "plain", items = mutableListOf(Item(kind = "pdf", asset = asset, pdfPage = i, pageCount = sizes.size, x = 0f, y = 0f, w = w, h = h, locked = true))))
                }.toMutableList()
                null to state
            }
        } catch (e: Exception) {
            media.file(asset).delete()
            throw e
        }
    }) { (item, state) ->
        if (item != null) {
            activateBoard(whiteboard)
            whiteboard.insert(item)
        } else if (state != null) {
            activateBoard(whiteboard)
            projectStore.editMetadata { projectStore.lesson.pdf = state }
            syncPdfWorkspace()
            projectChanged()
        }
    }
}
internal fun MainActivity.pdfMenu() {
    val state = projectStore.lesson.pdf ?: return
    val entries = listOf(
        tr("Save annotations to the original PDF", "ذخیرهٔ یادداشت‌ها روی همان PDF"), tr("Save as a new PDF", "ذخیره به‌صورت PDF جدید"),
        tr("Keep annotations in the project only", "یادداشت‌ها فقط در پروژه بمانند"), tr("Hand / writing mode", "حالت دست / نوشتن"),
        tr("Swap PDF and board sides", "تعویض سمت PDF و تخته"), tr("PDF fullscreen / split", "PDF تمام‌صفحه / کنار تخته"),
        tr("Copy page or region to board", "کپی صفحه یا بخشی از آن به تخته"),
        tr("Export board pages as PDF", "خروجی PDF از صفحات تخته"), tr("Close PDF workspace", "بستن بخش PDF")
    )
    choices(state.title + if (state.unsaved)" •"else "", entries) { i ->
        when (i) {
            0 -> savePdf(true)
            1 -> savePdf(false)
            2 -> {
                persist()
                toast(tr("Editable annotations are saved in the project", "یادداشت‌های قابل‌ویرایش در پروژه ذخیره می‌شوند"))
            }
            3 -> {
                pdfPane?.toggleHand()
                toast(if (pdfPane?.hand == true)tr("Hand: drag to scroll", "دست: برای اسکرول بکشید")else tr("Writing: use the selected tool", "نوشتن: از ابزار انتخاب‌شده استفاده کنید"))
            }
            4 -> {
                state.onRight = !state.onRight
                refreshPdfPresentation()
                projectChanged()
            }
            5 -> {
                state.fullscreen = !state.fullscreen
                refreshPdfPresentation()
                projectChanged()
            }
            6 -> copyPdfPage()
            7 -> choices(s("export_pdf"), listOf("current_page", "all_pages")) { n -> export("pdf", n == 1) { createDocument(it, "application/pdf") } }
            8 -> withPdfExit {
                activateBoard(whiteboard)
                projectStore.editMetadata { projectStore.lesson.pdf = null }
            }
        }
    }
}
internal fun MainActivity.withPdfExit(action: () -> Unit) {
    val state = projectStore.lesson.pdf
    if (state == null || !state.unsaved) {
        action()
        return
    }
    MaterialAlertDialogBuilder(this).setTitle(tr("PDF has unsaved annotations", "یادداشت‌های PDF ذخیره نشده‌اند"))
        .setMessage(tr("The project keeps editable notes. Choose whether to also save them into a PDF before leaving.", "پروژه یادداشت‌های قابل‌ویرایش را نگه می‌دارد. انتخاب کنید قبل از خروج در PDF هم ذخیره شوند یا نه."))
        .setPositiveButton(tr("Save PDF", "ذخیرهٔ PDF")) { _, _ ->
            MaterialAlertDialogBuilder(this).setTitle(tr("Save PDF", "ذخیرهٔ PDF")).setItems(arrayOf(tr("Original file", "همان فایل"), tr("New file", "فایل جدید"))) { _, i -> savePdf(i == 0, action) }.show()
        }.setNegativeButton(tr("Leave without PDF save", "خروج بدون ذخیره در PDF")) { _, _ ->
            persist()
            action()
        }
        .setNeutralButton(tr("Continue editing", "ادامهٔ کار"), null).show()
}
internal fun MainActivity.savePdf(overwrite: Boolean, then: (() -> Unit)? = null) {
    val live = projectStore.lesson.pdf ?: return
    if (board.hasActiveInteraction)return
    if (overwrite && live.sourceUri.isBlank()) {
        MaterialAlertDialogBuilder(this).setTitle(tr("Original file is unavailable", "فایل اصلی در دسترس نیست"))
            .setMessage(tr("This project was imported or its original file permission is unavailable. Save a new PDF.", "این پروژه وارد شده یا دسترسی به فایل اصلی موجود نیست. یک PDF جدید ذخیره کنید."))
            .setPositiveButton(tr("Save new PDF", "ذخیرهٔ PDF جدید")) { _, _ -> savePdf(false, then) }.setNegativeButton(s("cancel"), null).show()
        return
    }
    val snapshot = live.deepCopy()
    work({
        val output = File(cacheDir, "exports/PDF-${System.currentTimeMillis()}.pdf")
        PdfExport.write(media, snapshot, output)
        if (overwrite) {
            val uri = android.net.Uri.parse(snapshot.sourceUri)
            val backup = File(cacheDir, "exports/Original-${System.currentTimeMillis()}.pdf")
            requireNotNull(contentResolver.openInputStream(uri)).use { input -> backup.outputStream().use { boundedCopy(input, it, 128L * 1024 * 1024) } }
            check(PdfExport.hash(backup.inputStream()) == snapshot.originalHash) { tr("The original PDF changed outside the app. Save a new file to keep both versions.", "فایل اصلی بیرون از برنامه تغییر کرده؛ برای حفظ هر دو نسخه، فایل جدید ذخیره کنید.") }
            try {
                requireNotNull(contentResolver.openOutputStream(uri, "wt")).use { out ->
                    output.inputStream().use { it.copyTo(out) }
                    out.flush()
                }
                check(PdfExport.hash(requireNotNull(contentResolver.openInputStream(uri))) == PdfExport.hash(output.inputStream())) { "PDF write verification failed" }
            } catch (e: Exception) {
                try {
                    requireNotNull(contentResolver.openOutputStream(uri, "wt")).use { out -> backup.inputStream().use { it.copyTo(out) } }
                } catch (_: Exception) {}
                throw java.io.IOException(tr("Could not replace the original PDF. Recovery copies remain in the app; save a new PDF.", "جایگزینی PDF اصلی انجام نشد. نسخه‌های بازیابی داخل برنامه حفظ شده‌اند؛ PDF جدید ذخیره کنید."), e)
            }
            output to PdfExport.hash(output.inputStream())
        } else {
            output to ""
        }
    }) { (output, hash) ->
        if (overwrite) {
            live.savedRevision = snapshot.revision
            live.originalHash = hash
            projectChanged()
            toast(s("saved"))
            then?.invoke()
        } else {
            pendingPdfAsset = live.asset
            pendingPdfRevision = snapshot.revision
            afterPdfSave = then
            createDocument(output, "application/pdf")
        }
    }
}
internal fun MainActivity.copyPdfPage() {
    val state = projectStore.lesson.pdf ?: return
    val sheet = state.sheets[state.current]
    fun copy(region: android.graphics.RectF) {
        val page = sheet.page.copy(items = sheet.page.items.map { it.deepCopy() }.toMutableList())
        work({
            val scale = 1920f / maxOf(sheet.width, sheet.height)
            val full = Bitmap.createBitmap((sheet.width * scale).toInt().coerceAtLeast(1), (sheet.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(full)
                canvas.drawColor(Color.WHITE)
                canvas.scale(scale, scale)
                Renderer(media).scene(canvas, page, true)
                val left = (region.left * full.width).toInt().coerceIn(0, full.width - 1)
                val top = (region.top * full.height).toInt().coerceIn(0, full.height - 1)
                val w = ((region.right - region.left) * full.width).toInt().coerceIn(1, full.width - left)
                val h = ((region.bottom - region.top) * full.height).toInt().coerceIn(1, full.height - top)
                val crop = Bitmap.createBitmap(full, left, top, w, h)
                try {
                    Item(kind = "image", asset = media.save(crop), w = 560f, h = 560f * h / w)
                } finally {
                    if (crop !== full)crop.recycle()
                }
            } finally {
                full.recycle()
            }
        }) { item ->
            activateBoard(whiteboard)
            state.fullscreen = false
            refreshPdfPresentation()
            whiteboard.insert(item)
        }
    }
    MaterialAlertDialogBuilder(this).setTitle(tr("Copy PDF to board", "کپی PDF به تخته"))
        .setItems(arrayOf(tr("Whole current page", "تمام صفحهٔ فعلی"), tr("Select a region", "انتخاب بخشی از صفحه"))) { _, i ->
            if (i == 0) {
                copy(RectF(0f, 0f, 1f, 1f))
            } else {
                work({
                    val scale = 1536f / maxOf(sheet.width, sheet.height)
                    val bitmap = Bitmap.createBitmap((sheet.width * scale).toInt().coerceAtLeast(1), (sheet.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    canvas.scale(scale, scale)
                    Renderer(media).scene(canvas, sheet.page, true)
                    bitmap
                }) { bitmap ->
                    val selector = PdfRegionSelector(this, bitmap)
                    MaterialAlertDialogBuilder(this).setTitle(tr("Drag a rectangle", "یک مستطیل بکشید")).setView(selector)
                        .setPositiveButton(s("apply")) { _, _ -> copy(RectF(selector.region)) }.setNegativeButton(s("cancel"), null).show().setOnDismissListener { bitmap.recycle() }
                }
            }
        }.show()
}
