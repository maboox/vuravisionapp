package com.vuravision.classroom

import android.app.*
import android.content.*
import android.content.res.Configuration
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.*
import android.view.*
import android.widget.*
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.*

class MainActivity : Activity() {
    val store = Store()
    lateinit var board: Board
    lateinit var media: Media
    private lateinit var files: LessonFiles
    private lateinit var root: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var status: TextView
    private lateinit var dock: LinearLayout
    private lateinit var pageLabel: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val recognition = Recognition()
    private val prefs by lazy { getSharedPreferences("vura", MODE_PRIVATE) }
    private var documentId = ""
    private var clipboard = listOf<Item>()
    private var pendingExport: File? = null
    private var sharing: Sharing? = null
    private var shareDialog: Dialog? = null
    private var taps = 0
    private val autosave = Runnable { persist() }
    private var dirty = false
    private var loading = false
    private var lastError = ""
    private var destroyed = false

    override fun attachBaseContext(base: Context) {
        val lang =
            base.getSharedPreferences("vura", MODE_PRIVATE).getString("language", "en") ?: "en"
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale(lang))
        super.attachBaseContext(base.createConfigurationContext(config))
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        media = Media(this)
        files = LessonFiles(media)
        board = Board(this, store, Renderer(media))
        board.profile =
            TouchProfile(
                prefs.getBoolean("calibrated", false),
                prefs.getFloat("thin", 10f),
                prefs.getFloat("palm", 50f),
                prefs.getBoolean("palmErase", false),
            )
        buildUI()
        media.ready = { handler.post { if (!destroyed) board.invalidate() } }
        media.error = { message -> handler.post { status.text = s("error") + ": " + message } }
        board.onSelection = { refreshDock() }
        store.changed = {
            dirty = true
            board.invalidate()
            refreshDock()
            refreshTitle()
            status.text = s("saving")
            handler.removeCallbacks(autosave)
            handler.postDelayed(autosave, 900)
        }
        documentId = prefs.getString("current", null) ?: newId()
        prefs.edit().putString("current", documentId).apply()
        val f = lessonFile()
        if (f.exists() || File(f.path + ".bak").exists()) loadFile(f, false)
        else {
            store.lesson.title = s("lesson")
            refreshTitle()
        }
    }

    private fun buildUI() {
        root =
            column().apply {
                setBackgroundColor(PAPER)
                fitsSystemWindows = true
            }
        setContentView(root)
        val header =
            row().apply {
                setBackgroundColor(NAVY)
                setPadding(dp(12), dp(5), dp(12), dp(5))
            }
        header.addView(
            label("V", 22f, 0xffffad80.toInt(), true).apply {
                gravity = Gravity.CENTER
                contentDescription = "VuraVision"
            },
            LinearLayout.LayoutParams(dp(40), dp(44)),
        )
        titleView =
            label("VuraVision", 17f, Color.WHITE, true).apply {
                setOnClickListener { rename() }
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
        header.addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(
            button(s("undo")) {
                store.undo()
                board.clearSelection()
            }
        )
        header.addView(
            button(s("redo")) {
                store.redo()
                board.clearSelection()
            }
        )
        header.addView(button(s("share")) { share() })
        header.addView(button("☰") { menu() }.apply { contentDescription = s("tools") })
        root.addView(header)
        val strip = row().apply { setPadding(dp(12), dp(2), dp(12), dp(2)) }
        strip.addView(label(s("workspace").uppercase(Locale.getDefault()), 10f, MUTED, true))
        status = label(s("ready"), 11f, MUTED)
        strip.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        pageLabel = label("", 12f, MUTED).apply { setOnClickListener { pages() } }
        strip.addView(pageLabel)
        strip.addView(button(s("fit")) { board.fit() })
        root.addView(strip)
        root.addView(board, LinearLayout.LayoutParams(-1, 0, 1f))
        dock = row().apply { pad(5) }
        root.addView(scrollRow(dock))
        refreshDock()
    }

    private fun refreshTitle() {
        titleView.text = store.lesson.title.ifBlank { s("lesson") }
        pageLabel.text =
            "${store.lesson.current+1} / ${store.lesson.pages.size}   ·   ${s("pages")}"
    }

    private fun refreshDock() {
        if (!::dock.isInitialized) return
        dock.removeAllViews()
        listOf("pen", "highlight", "erase", "select", "pan").forEach { k ->
            dock.addView(
                button(s(k), board.tool == k) {
                    board.tool = k
                    board.clearSelection()
                    refreshDock()
                }
            )
        }
        dock.addView(button(s("color")) { colors() })
        dock.addView(button(s("width")) { width() })
        dock.addView(button(s("insert")) { insert() })
        dock.addView(button(s("smart")) { smart() })
        if (board.chosen().isNotEmpty())
            dock.addView(button("${s("edit")} · ${board.chosen().size}", true) { editSelection() })
        dock.addView(button(s("pages")) { pages() })
    }

    private fun dialog(title: String, content: View): AlertDialog =
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(content)
            .setNegativeButton(s("close"), null)
            .create()
            .also {
                it.show()
                it.window?.setBackgroundDrawable(rounded(Color.WHITE, dp(20).toFloat()))
            }

    private fun choices(title: String, keys: List<String>, action: (Int) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(keys.map { s(it) }.toTypedArray()) { _, i -> action(i) }
            .setNegativeButton(s("cancel"), null)
            .show()
    }

    private fun confirm(title: String, action: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(title)
            .setNegativeButton(s("cancel"), null)
            .setPositiveButton(s("apply")) { _, _ -> action() }
            .show()
    }

    private fun input(
        title: String,
        value: String = "",
        multi: Boolean = false,
        action: (String) -> Unit,
    ) {
        val box = column().apply { pad(20) }
        val edit =
            field(value).apply {
                setSingleLine(!multi)
                maxLines = 8
            }
        box.addView(edit)
        val d =
            AlertDialog.Builder(this)
                .setTitle(title)
                .setView(box)
                .setNegativeButton(s("cancel"), null)
                .setPositiveButton(s("apply"), null)
                .create()
        d.show()
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                action(edit.text.toString())
                d.dismiss()
            } catch (e: Exception) {
                edit.error = e.message ?: s("error")
            }
        }
    }

    private fun error(e: Exception) {
        lastError = "${e.javaClass.simpleName}: ${e.message}"
        if (!destroyed) {
            status.text = s("error")
            AlertDialog.Builder(this)
                .setTitle(s("error"))
                .setMessage(lastError)
                .setPositiveButton(s("close"), null)
                .show()
        }
    }

    private fun <T> work(task: () -> T, done: (T) -> Unit) {
        if (loading) return
        loading = true
        val progress =
            AlertDialog.Builder(this)
                .setView(
                    row().apply {
                        pad(24)
                        addView(
                            ProgressBar(this@MainActivity),
                            LinearLayout.LayoutParams(dp(36), dp(36)),
                        )
                        addView(label(s("busy")).apply { pad(16) })
                    }
                )
                .setCancelable(false)
                .show()
        worker.execute {
            try {
                val result = task()
                handler.post {
                    loading = false
                    if (!destroyed) {
                        progress.dismiss()
                        done(result)
                    }
                }
            } catch (e: Exception) {
                handler.post {
                    loading = false
                    if (!destroyed) {
                        progress.dismiss()
                        error(e)
                    }
                }
            }
        }
    }

    private fun lessonFile(id: String = documentId) = File(filesDir, "lessons/$id.vura")

    private fun persist() {
        if (!dirty || destroyed) return
        dirty = false
        val doc = store.lesson.copyDeep()
        val target = lessonFile()
        worker.execute {
            try {
                files.atomic(doc, target)
                handler.post { if (!dirty && !destroyed) status.text = s("saved") }
            } catch (e: Exception) {
                handler.post {
                    dirty = true
                    if (!destroyed) {
                        lastError = e.message ?: ""
                        status.text = s("save_error")
                    }
                }
            }
        }
    }

    private fun loadFile(f: File, changeId: Boolean = true) {
        handler.removeCallbacks(autosave)
        persist()
        work({
            try {
                files.read(f.inputStream()) to false
            } catch (e: Exception) {
                val backup = File(f.path + ".bak")
                if (!backup.exists()) throw e
                files.read(backup.inputStream()) to true
            }
        }) { (doc, backup) ->
            if (changeId || backup) {
                documentId = if (backup) newId() else f.nameWithoutExtension
                prefs.edit().putString("current", documentId).apply()
            }
            store.replace(doc)
            board.clearSelection()
            board.reset()
            if (backup) status.text = s("restored_backup")
        }
    }

    private fun rename() {
        input(s("lesson"), store.lesson.title) { value ->
            require(value.isNotBlank())
            store.edit { store.lesson.title = value.take(100) }
        }
    }

    private fun menu() {
        choices("VuraVision · β", listOf("files", "lab", "games", "tools", "settings", "help")) {
            when (it) {
                0 -> fileMenu()
                1 -> Labs.show(this) { bitmap -> insertBitmap(bitmap) }
                2 -> Games.show(this)
                3 -> classroomTools()
                4 -> settings()
                5 ->
                    dialog(
                        s("help"),
                        ScrollView(this).apply {
                            addView(label(s("help_text"), 16f).apply { pad(24) })
                        },
                    )
            }
        }
    }

    private fun fileMenu() {
        choices(
            s("files"),
            listOf("new", "open", "save", "recent", "export_pdf", "export_png", "export_jpg"),
        ) {
            when (it) {
                0 ->
                    confirm(s("new_confirm")) {
                        persist()
                        documentId = newId()
                        prefs.edit().putString("current", documentId).apply()
                        store.replace(Lesson(title = s("lesson")))
                        board.clearSelection()
                        board.reset()
                    }
                1 -> pick("application/octet-stream", 101)
                2 -> export("vura", true) { createDocument(it, "application/octet-stream") }
                3 -> recent()
                4 -> exportChoice("pdf")
                5 -> exportChoice("png")
                6 -> exportChoice("jpg")
            }
        }
    }

    private fun recent() {
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
                                        Lesson::class.java,
                                    )
                                    .title
                            }
                        }
                        .getOrDefault(f.name)
                "$name  ·  ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(f.lastModified()))}"
            }
        }) { names ->
            AlertDialog.Builder(this)
                .setTitle(s("recent"))
                .setItems(names.toTypedArray()) { _, i -> loadFile(list[i]) }
                .setNegativeButton(s("cancel"), null)
                .show()
        }
    }

    private fun colors() {
        val colors =
            listOf(
                    0xff243746,
                    0xffe46d38,
                    0xff167b79,
                    0xff6373c2,
                    0xffd35473,
                    0xffe5b633,
                    0xffeeeeee,
                    0xff000000,
                )
                .map { it.toInt() }
        val r = row().apply { pad(12) }
        val d = dialog(s("color"), r)
        colors.forEach { color ->
            r.addView(
                button("●") {
                        board.inkColor = color
                        if (board.chosen().isNotEmpty()) board.edit { it.color = color }
                        d.dismiss()
                    }
                    .apply {
                        setTextColor(color)
                        textSize = 28f
                    },
                LinearLayout.LayoutParams(0, dp(54), 1f),
            )
        }
    }

    private fun width() {
        val c = column().apply { pad(20) }
        val read = label("${board.inkWidth.toInt()}")
        val seek =
            SeekBar(this).apply {
                max = 19
                progress = board.inkWidth.toInt() - 1
                setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                            board.inkWidth = p + 1f
                            read.text = "${p+1}"
                        }

                        override fun onStartTrackingTouch(s: SeekBar?) {}

                        override fun onStopTrackingTouch(s: SeekBar?) {}
                    }
                )
            }
        c.addView(read)
        c.addView(seek)
        c.addView(button(s("apply")) { board.edit { it.width = board.inkWidth } })
        dialog(s("width"), c)
    }

    private fun insert() {
        choices(s("insert"), listOf("text", "sticky", "shape", "graph", "image", "pdf", "math")) {
            when (it) {
                0 -> addText(false)
                1 -> addText(true)
                2 ->
                    choices(
                        s("shape"),
                        listOf("rectangle", "circle", "triangle", "line", "arrow"),
                    ) { i ->
                        board.shape = listOf("rectangle", "circle", "triangle", "line", "arrow")[i]
                        board.tool = "shape"
                        board.clearSelection()
                        refreshDock()
                    }
                3 -> graph()
                4 -> pick("image/*", 102)
                5 -> pick("application/pdf", 103)
                6 -> math()
            }
        }
    }

    private fun addText(sticky: Boolean) {
        input(s(if (sticky) "sticky" else "text"), multi = true) { value ->
            require(value.isNotBlank())
            board.insert(
                Item(
                    kind = if (sticky) "sticky" else "text",
                    text = value,
                    width = if (sticky) 24f else 32f,
                    w = 380f,
                    h = 220f,
                    color = board.inkColor,
                )
            )
        }
    }

    private fun graph() {
        input(s("graph"), "sin(x); cos(x)", true) { value ->
            require(value.split(';').size in 1..3)
            value.split(';').forEach { MathTools().compile(it) }
            board.insert(Item(kind = "graph", text = value, w = 560f, h = 380f, domain = 10f))
        }
    }

    private fun math() {
        choices(s("math"), listOf("evaluate", "solve")) { i ->
            input(s(if (i == 0) "evaluate" else "solve"), if (i == 0) "2*(3+4)" else "2x+3=11") {
                value ->
                val answer =
                    if (i == 0) MathTools.format(MathTools().evaluate(value))
                    else MathTools().solve(value)
                board.insert(
                    Item(kind = "text", text = "$value\n= $answer", width = 28f, w = 420f, h = 150f)
                )
            }
        }
    }

    private fun editSelection() {
        val items = board.chosen()
        if (items.isEmpty()) return
        val one = items.singleOrNull()
        val keys = mutableListOf("delete", "duplicate", "copy", "lock", "front", "back", "color")
        if (one?.kind in listOf("text", "sticky", "graph")) keys.add("text")
        if (one?.kind in listOf("text", "sticky")) keys.add("text_size")
        if (one?.kind == "graph") keys.add("domain")
        if (one?.kind == "pdf") keys.addAll(listOf("previous", "next", "page_picker"))
        if (one?.kind in listOf("image", "pdf")) keys.add("crop")
        keys.add("paste")
        choices(s("edit"), keys) { i ->
            when (keys[i]) {
                "delete" -> board.delete()
                "copy" -> {
                    clipboard = items.map { it.deepCopy() }
                    toast(s("copy"))
                }
                "duplicate" -> {
                    store.edit {
                        items
                            .map {
                                it.deepCopy().apply {
                                    id = newId()
                                    x += 24
                                    y += 24
                                    locked = false
                                }
                            }
                            .forEach { store.page.items.add(it) }
                    }
                }
                "paste" -> paste()
                "lock" -> {
                    store.edit { items.forEach { it.locked = !it.locked } }
                }
                "front" -> {
                    store.edit {
                        store.page.items.removeAll(items.toSet())
                        store.page.items.addAll(items)
                    }
                }
                "back" -> {
                    store.edit {
                        store.page.items.removeAll(items.toSet())
                        store.page.items.addAll(0, items)
                    }
                }
                "color" -> colors()
                "text" ->
                    input(s("text"), one!!.text, true) { v ->
                        if (one.kind == "graph") {
                            require(v.split(';').size in 1..3)
                            v.split(';').forEach { MathTools().compile(it) }
                        }
                        board.edit { it.text = v }
                    }
                "text_size" ->
                    input(s("text_size"), one!!.width.toInt().toString()) { v ->
                        val n = v.toFloat()
                        require(n in 8f..120f)
                        board.edit { it.width = n }
                    }
                "domain" ->
                    input(s("domain"), one!!.domain.toString()) { v ->
                        val n = v.toFloat()
                        require(n in .1f..1000f)
                        board.edit { it.domain = n }
                    }
                "previous" -> board.edit { it.pdfPage = (it.pdfPage - 1).coerceAtLeast(0) }
                "next" ->
                    board.edit { it.pdfPage = (it.pdfPage + 1).coerceAtMost(it.pageCount - 1) }
                "page_picker" ->
                    input(s("page_picker"), "${one!!.pdfPage+1}") { v ->
                        val page = v.toInt()
                        require(page in 1..one.pageCount)
                        board.edit { it.pdfPage = page - 1 }
                    }
                "crop" -> crop(one!!)
            }
        }
    }

    private fun paste() {
        if (clipboard.isEmpty()) return
        val c = board.center()
        val bounds = contentBounds(clipboard)
        store.edit {
            val pasted =
                clipboard.map {
                    it.deepCopy().apply {
                        id = newId()
                        x += c.x - bounds.centerX()
                        y += c.y - bounds.centerY()
                        locked = false
                    }
                }
            store.page.items.addAll(pasted)
            board.selected.clear()
            board.selected.addAll(pasted.map { it.id })
        }
        board.tool = "select"
        refreshDock()
    }

    private fun crop(o: Item) {
        if (o.locked) return
        input(s("crop_help"), "0, 0, 100, 100") { value ->
            val n = value.split(',').map { it.trim().toFloat() }
            require(n.size == 4 && n.all { it in 0f..100f } && n[2] > n[0] && n[3] > n[1])
            work({
                val b = requireNotNull(media.image(o, true))
                val left = (b.width * n[0] / 100).toInt().coerceAtMost(b.width - 1)
                val top = (b.height * n[1] / 100).toInt().coerceAtMost(b.height - 1)
                val w = ((n[2] - n[0]) * b.width / 100).toInt().coerceIn(1, b.width - left)
                val h = ((n[3] - n[1]) * b.height / 100).toInt().coerceIn(1, b.height - top)
                media.save(Bitmap.createBitmap(b, left, top, w, h))
            }) { asset ->
                board.edit {
                    it.asset = asset
                    it.kind = "image"
                    it.w *= ((n[2] - n[0]) / 100)
                    it.h *= ((n[3] - n[1]) / 100)
                    it.pdfPage = 0
                    it.pageCount = 1
                }
            }
        }
    }

    private fun pages() {
        val c = column().apply { pad(16) }
        val list = row()
        store.lesson.pages.forEachIndexed { i, _ ->
            list.addView(
                button("${i+1}", i == store.lesson.current) {
                    store.edit { store.lesson.current = i }
                    board.clearSelection()
                    board.reset()
                }
            )
        }
        c.addView(scrollRow(list))
        val commands =
            listOf(
                "add_page",
                "duplicate",
                "delete",
                "move_left",
                "move_right",
                "background",
                "clear",
                "paste",
            )
        commands.chunked(4).forEach { chunk ->
            val r = row()
            chunk.forEach { k ->
                r.addView(
                    button(s(k)) {
                        when (k) {
                            "add_page" -> {
                                if (store.lesson.pages.size < 200)
                                    store.edit {
                                        store.lesson.pages.add(Page())
                                        store.lesson.current = store.lesson.pages.lastIndex
                                    }
                                board.reset()
                            }
                            "duplicate" -> {
                                if (store.lesson.pages.size < 200)
                                    store.edit {
                                        store.lesson.pages.add(
                                            store.lesson.current + 1,
                                            store.page.copy(
                                                id = newId(),
                                                items =
                                                    store.page.items
                                                        .map {
                                                            it.deepCopy().apply { id = newId() }
                                                        }
                                                        .toMutableList(),
                                            ),
                                        )
                                        store.lesson.current++
                                    }
                            }
                            "delete" -> {
                                if (store.lesson.pages.size > 1)
                                    confirm(s("clear_confirm")) {
                                        store.edit {
                                            store.lesson.pages.removeAt(store.lesson.current)
                                            store.lesson.current =
                                                store.lesson.current.coerceAtMost(
                                                    store.lesson.pages.lastIndex
                                                )
                                        }
                                        board.clearSelection()
                                    }
                            }
                            "move_left",
                            "move_right" -> {
                                val target = store.lesson.current + if (k == "move_left") -1 else 1
                                if (target in store.lesson.pages.indices)
                                    store.edit {
                                        java.util.Collections.swap(
                                            store.lesson.pages,
                                            store.lesson.current,
                                            target,
                                        )
                                        store.lesson.current = target
                                    }
                            }
                            "background" ->
                                choices(s(k), listOf("white", "dark", "dots", "grid", "ruled")) { i
                                    ->
                                    store.edit {
                                        store.page.background =
                                            listOf("white", "dark", "dots", "grid", "ruled")[i]
                                    }
                                }
                            "clear" ->
                                confirm(s("clear_confirm")) {
                                    store.edit { store.page.items.removeAll { !it.locked } }
                                    board.clearSelection()
                                }
                            "paste" -> paste()
                        }
                        refreshTitle()
                    }
                )
            }
            c.addView(scrollRow(r))
        }
        dialog(s("pages"), c)
    }

    private fun pick(mime: String, code: Int) {
        try {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = mime
                    if (code == 101) type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                },
                code,
            )
        } catch (e: Exception) {
            error(e)
        }
    }

    @Deprecated("Platform compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            101 ->
                work({ files.read(requireNotNull(contentResolver.openInputStream(uri))) }) { doc ->
                    persist()
                    documentId = newId()
                    prefs.edit().putString("current", documentId).apply()
                    store.replace(doc)
                    board.clearSelection()
                    board.fit()
                }
            102,
            103 ->
                work({
                    val asset =
                        media.import(
                            requireNotNull(contentResolver.openInputStream(uri)),
                            if (requestCode == 103) "pdf" else "img",
                        )
                    try {
                        val o =
                            Item(
                                kind = if (requestCode == 103) "pdf" else "image",
                                asset = asset,
                                w = 560f,
                                h = 400f,
                            )
                        if (requestCode == 103) o.pageCount = media.pdfCount(asset)
                        val b = requireNotNull(media.image(o, true))
                        o.h = o.w * b.height / b.width
                        o
                    } catch (e: Exception) {
                        media.file(asset).delete()
                        throw e
                    }
                }) {
                    board.insert(it)
                }
            104 -> {
                val file = pendingExport ?: return
                work({
                    requireNotNull(contentResolver.openOutputStream(uri, "wt")).use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                }) {
                    toast(s("saved"))
                    pendingExport = null
                }
            }
        }
    }

    private fun exportChoice(ext: String) {
        if (ext == "pdf")
            choices(s("export_pdf"), listOf("current_page", "all_pages")) { i ->
                export(ext, i == 1) { createDocument(it, "application/pdf") }
            }
        else export(ext, false) { createDocument(it, "image/${if(ext=="jpg")"jpeg"else"png"}") }
    }

    private fun export(ext: String, all: Boolean, done: (File) -> Unit) {
        val doc = store.lesson.copyDeep()
        work({
            val folder = File(cacheDir, "exports").apply { mkdirs() }
            val target = File(folder, "VuraVision-${System.currentTimeMillis()}.$ext")
            val renderer = Renderer(media)
            if (ext == "vura") target.outputStream().use { files.write(doc, it) }
            else if (ext == "pdf") {
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
                    bitmap.compress(
                        if (ext == "jpg") Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG,
                        92,
                        it,
                    )
                }
                bitmap.recycle()
            }
            target
        }) {
            done(it)
        }
    }

    private fun createDocument(file: File, mime: String) {
        pendingExport = file
        try {
            startActivityForResult(
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    type = mime
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_TITLE, file.name)
                },
                104,
            )
        } catch (e: Exception) {
            error(e)
        }
    }

    private fun share() {
        export("pdf", true) { file ->
            try {
                sharing?.stop()
                val server = Sharing(file, "application/pdf")
                server.start(5000, false)
                val url = server.url()
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
                c.addView(
                    ImageView(this).apply {
                        setImageBitmap(Sharing.qr(url))
                        contentDescription = url
                    },
                    LinearLayout.LayoutParams(dp(230), dp(230)),
                )
                c.addView(label(url, 12f).apply { setTextIsSelectable(true) })
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
                    600000,
                )
                d.setOnDismissListener {
                    server.stop()
                    handler.removeCallbacks(update)
                    if (sharing === server) sharing = null
                }
            } catch (e: Exception) {
                error(e)
            }
        }
    }

    private fun smart() {
        val strokes = (board.chosen().ifEmpty { store.page.items }).filter { it.kind == "ink" }
        if (strokes.isEmpty()) {
            toast(s("empty_ink"))
            return
        }
        choices(s("smart"), listOf("recognize_en", "recognize_fa")) { i ->
            val lang = if (i == 0) "en-US" else "fa"
            recognition.installed(
                lang,
                { installed ->
                    if (!installed) {
                        confirm(s("model_required")) { models() }
                    } else {
                        status.text = s("busy")
                        recognition.recognize(
                            lang,
                            strokes,
                            { candidates ->
                                status.text = s("ready")
                                review(strokes, candidates)
                            },
                            { error(it) },
                        )
                    }
                },
                { error(it) },
            )
        }
    }

    private fun review(strokes: List<Item>, candidates: List<String>) {
        val c = column().apply { pad(18) }
        val edit = field(candidates.firstOrNull().orEmpty())
        c.addView(edit)
        val alternatives = row()
        candidates.take(3).forEach { candidate ->
            alternatives.addView(button(candidate) { edit.setText(candidate) })
        }
        c.addView(scrollRow(alternatives))
        val keep =
            CheckBox(this).apply {
                text = s("keep_ink")
                isChecked = true
            }
        c.addView(keep)
        val d = dialog(s("review"), c)
        c.addView(
            button(s("apply"), true) {
                val value = edit.text.toString()
                if (value.isNotBlank()) {
                    val b = contentBounds(strokes)
                    store.edit {
                        if (!keep.isChecked)
                            store.page.items.removeAll {
                                it.id in strokes.filter { !it.locked }.map { it.id }
                            }
                        store.page.items.add(
                            Item(
                                kind = "text",
                                x = b.left,
                                y = if (keep.isChecked) b.bottom + 24 else b.top,
                                w = max(320f, b.width()),
                                h = 180f,
                                width = 32f,
                                text = value,
                            )
                        )
                    }
                    d.dismiss()
                }
            }
        )
    }

    private fun models() {
        val c = column().apply { pad(20) }
        c.addView(label(s("model_info"), 14f))
        listOf("en-US" to "english", "fa" to "persian").forEach { (lang, key) ->
            val r = row()
            val state = label(s(key), 15f)
            r.addView(state, LinearLayout.LayoutParams(0, -2, 1f))
            fun update() {
                recognition.installed(
                    lang,
                    { state.text = "${s(key)} · ${s(if(it)"installed"else"not_installed")}" },
                    { state.text = s("error") },
                )
            }
            r.addView(
                button(s("install")) {
                    state.text = s("downloading")
                    recognition.download(
                        lang,
                        { update() },
                        {
                            error(it)
                            update()
                        },
                    )
                }
            )
            r.addView(
                button(s("remove_model")) { recognition.remove(lang, { update() }, { error(it) }) }
            )
            c.addView(r)
            update()
        }
        dialog(s("models"), c)
    }

    private fun settings() {
        choices(
            s("settings"),
            listOf("language", "models", "cache", "about", "engineering").filter {
                it != "engineering" || prefs.getBoolean("engineering", false)
            },
        ) {
            when (it) {
                0 ->
                    choices(s("language"), listOf("english", "persian")) { i ->
                        persist()
                        prefs.edit().putString("language", if (i == 0) "en" else "fa").apply()
                        recreate()
                    }
                1 -> models()
                2 -> {
                    media.clear()
                    board.invalidate()
                    toast(s("done"))
                }
                3 -> {
                    val v =
                        label("VuraVision 0.2.0 beta\n\n${s("about_text")}", 16f).apply {
                            pad(24)
                            setOnClickListener {
                                taps++
                                if (taps >= 7) {
                                    prefs.edit().putBoolean("engineering", true).apply()
                                    toast(s("unlocked"))
                                }
                            }
                        }
                    dialog(s("about"), v)
                }
                4 ->
                    if (prefs.getBoolean("engineering", false)) engineering()
                    else toast("7 × ${s("about")}")
            }
        }
    }

    private fun engineering() {
        choices(s("engineering"), listOf("touch_test", "thresholds", "report", "stress")) {
            when (it) {
                0 ->
                    dialog(
                        s("touch_test"),
                        TouchDiagnostics(this, board.profile).apply { minimumHeight = dp(400) },
                    )
                1 -> {
                    val c = column().apply { pad(20) }
                    c.addView(label(s("threshold_help"), 14f))
                    val thin = field(board.profile.thin.toString(), "Thin px")
                    val palm = field(board.profile.palm.toString(), "Palm px")
                    val enabled =
                        CheckBox(this).apply {
                            text = s("thresholds")
                            isChecked = board.profile.calibrated
                        }
                    val erase =
                        CheckBox(this).apply {
                            text = s("palm_erase")
                            isChecked = board.profile.palmErase
                        }
                    c.addView(thin)
                    c.addView(palm)
                    c.addView(enabled)
                    c.addView(erase)
                    val d = dialog(s("thresholds"), c)
                    c.addView(
                        button(s("apply")) {
                            val a = thin.text.toString().toFloatOrNull()
                            val b = palm.text.toString().toFloatOrNull()
                            if (a != null && b != null && a > 0 && b > a) {
                                board.profile =
                                    TouchProfile(enabled.isChecked, a, b, erase.isChecked)
                                prefs
                                    .edit()
                                    .putBoolean("calibrated", enabled.isChecked)
                                    .putFloat("thin", a)
                                    .putFloat("palm", b)
                                    .putBoolean("palmErase", erase.isChecked)
                                    .apply()
                                d.dismiss()
                            } else thin.error = s("error")
                        }
                    )
                }
                2 -> {
                    val report =
                        "VuraVision ${BuildConfig.VERSION_NAME}\n${Build.MANUFACTURER} ${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}\n${resources.displayMetrics}\nPointers: device-reported only\nObjects: ${store.page.items.size}\nLast error: $lastError\n" +
                            InputDevice.getDeviceIds().joinToString("\n") { id ->
                                InputDevice.getDevice(id)
                                    ?.let { "${it.name}: sources=${it.sources}" }
                                    .orEmpty()
                            }
                    val text =
                        label(report, 13f).apply {
                            pad(18)
                            setTextIsSelectable(true)
                        }
                    dialog(s("report"), ScrollView(this).apply { addView(text) })
                }
                3 -> {
                    store.edit {
                        repeat(300) { n ->
                            store.page.items.add(
                                Item(
                                    x = (n % 30) * 24f,
                                    y = (n / 30) * 30f,
                                    w = 20f,
                                    h = 20f,
                                    inkW = 20f,
                                    inkH = 20f,
                                    color = if (n % 2 == 0) ORANGE else TEAL,
                                    points =
                                        (0..30)
                                            .map { v ->
                                                Point(v * 20f / 30, 10 + sin(v * .4).toFloat() * 8)
                                            }
                                            .toMutableList(),
                                )
                            )
                        }
                    }
                    board.fit()
                }
            }
        }
    }

    private fun classroomTools() {
        choices(s("tools"), listOf("timer", "stopwatch", "dice", "scoreboard", "curtain")) { which
            ->
            val c =
                column().apply {
                    pad(24)
                    gravity = Gravity.CENTER
                }
            val display =
                label(if (which == 0) "05:00" else "0", 48f, NAVY, true).apply {
                    gravity = Gravity.CENTER
                }
            c.addView(display)
            val d =
                dialog(s(listOf("timer", "stopwatch", "dice", "scoreboard", "curtain")[which]), c)
            when (which) {
                0,
                1 -> {
                    var running = false
                    var elapsed = 0L
                    var since = 0L
                    var duration = 300000L
                    val tick =
                        object : Runnable {
                            override fun run() {
                                val t =
                                    elapsed +
                                        if (running) SystemClock.elapsedRealtime() - since else 0
                                val sec =
                                    (if (which == 0) (duration - t).coerceAtLeast(0) else t) / 1000
                                display.text = "%02d:%02d".format(sec / 60, sec % 60)
                                if (which == 0 && t >= duration) {
                                    elapsed = duration
                                    running = false
                                    display.setTextColor(ORANGE)
                                }
                                handler.postDelayed(this, 100)
                            }
                        }
                    handler.post(tick)
                    val r = row()
                    r.addView(
                        button(s("start")) {
                            if (!running) {
                                since = SystemClock.elapsedRealtime()
                                running = true
                            }
                        }
                    )
                    r.addView(
                        button(s("pause")) {
                            if (running) elapsed += SystemClock.elapsedRealtime() - since
                            running = false
                        }
                    )
                    r.addView(
                        button(s("reset")) {
                            running = false
                            elapsed = 0
                            display.setTextColor(NAVY)
                        }
                    )
                    if (which == 0)
                        r.addView(
                            button(s("minutes")) {
                                input(s("minutes"), "5") { v ->
                                    val min = v.toInt()
                                    require(min in 1..240)
                                    duration = min * 60000L
                                    elapsed = 0
                                    running = false
                                }
                            }
                        )
                    c.addView(r)
                    d.setOnDismissListener { handler.removeCallbacks(tick) }
                }
                2 -> {
                    c.addView(button(s("roll"), true) { display.text = "${(1..6).random()}" })
                }
                3 -> {
                    var a = 0
                    var b = 0
                    fun update() {
                        display.text = "$a : $b"
                    }
                    update()
                    val r = row()
                    r.addView(
                        button("− 1") {
                            a--
                            update()
                        }
                    )
                    r.addView(
                        button("+ 1") {
                            a++
                            update()
                        }
                    )
                    r.addView(
                        button("− 2") {
                            b--
                            update()
                        }
                    )
                    r.addView(
                        button("+ 2") {
                            b++
                            update()
                        }
                    )
                    c.addView(r)
                }
                4 -> {
                    d.dismiss()
                    val curtain =
                        Dialog(this, android.R.style.Theme_Material_NoActionBar_Fullscreen)
                    curtain.setContentView(
                        column().apply {
                            setBackgroundColor(NAVY)
                            gravity = Gravity.CENTER
                            addView(label("VuraVision", 36f, Color.WHITE, true))
                            addView(button(s("reveal")) { curtain.dismiss() })
                        }
                    )
                    curtain.show()
                    curtain.window?.setLayout(-1, -1)
                }
            }
        }
    }

    private fun insertBitmap(bitmap: Bitmap) {
        work({ media.save(bitmap) }) { name ->
            board.insert(
                Item(
                    kind = "image",
                    asset = name,
                    w = 620f,
                    h = 620f * bitmap.height / bitmap.width,
                )
            )
            bitmap.recycle()
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onStop() {
        handler.removeCallbacks(autosave)
        persist()
        super.onStop()
    }

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        sharing?.stop()
        shareDialog?.dismiss()
        worker.shutdown()
        media.close()
        super.onDestroy()
    }
}
