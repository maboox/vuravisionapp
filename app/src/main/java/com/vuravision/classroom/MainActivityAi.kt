package com.vuravision.classroom
import android.app.*
import android.content.*
import android.graphics.*
import android.os.*
import android.view.*
import android.widget.*
import com.vuravision.classroom.ai.AiException
import com.vuravision.classroom.ai.AiMessages
import com.vuravision.classroom.ai.AiSettings
import com.vuravision.classroom.ai.AiTasks
import com.vuravision.classroom.ai.SmartAi
import com.vuravision.classroom.board.Charts
import com.vuravision.classroom.board.Item
import com.vuravision.classroom.board.SavedQuestion
import com.vuravision.classroom.board.SavedQuiz
import com.vuravision.classroom.board.Tables
import com.vuravision.classroom.board.TextLayout
import com.vuravision.classroom.board.center
import com.vuravision.classroom.board.contentBounds
import com.vuravision.classroom.board.fit
import com.vuravision.classroom.board.reset
import com.vuravision.classroom.board.reveal
import com.vuravision.classroom.board.snapshotItems
import com.vuravision.classroom.games.ClassQuiz
import com.vuravision.classroom.games.QuizBuilder
import com.vuravision.classroom.games.Sounds
import com.vuravision.classroom.labs.Labs
import com.vuravision.classroom.room.Sharing
import com.vuravision.classroom.tools.StepReveal
import com.vuravision.classroom.ui.FlowRow
import com.vuravision.classroom.ui.Fonts
import com.vuravision.classroom.ui.INK_MUTED
import com.vuravision.classroom.ui.INK_NAVY
import com.vuravision.classroom.ui.INK_TEAL
import com.vuravision.classroom.ui.MUTED
import com.vuravision.classroom.ui.NAVY
import com.vuravision.classroom.ui.TEAL
import com.vuravision.classroom.ui.button
import com.vuravision.classroom.ui.column
import com.vuravision.classroom.ui.dp
import com.vuravision.classroom.ui.field
import com.vuravision.classroom.ui.label
import com.vuravision.classroom.ui.pad
import com.vuravision.classroom.ui.row
import com.vuravision.classroom.ui.s
import com.vuravision.classroom.ui.sectionTitle
import com.vuravision.classroom.ui.segmented
import com.vuravision.classroom.ui.tr
import com.vuravision.classroom.voice.VoiceApiKey
import com.vuravision.classroom.voice.VoiceSettings
import kotlin.math.*

// AI on the board: board actions, settings, charts and grouping (split from MainActivity, item 11).

/**
 * Runs one AI request off the main thread. The selection bar shows what is happening with Cancel, the board stays
 * usable, and any failure becomes one sentence saying what to do (never a dialog full of errors).
 */
internal fun <T> MainActivity.runAi(label: String, work: (SmartAi) -> T, done: (T) -> Unit) {
    if (!smartAi.configured()) {
        toast(AiMessages.text(this, "no_key"))
        aiSettings()
        return
    }
    val ticket = ++aiTicket
    aiBusyLabel = label
    selectionBarMode = "busy"
    refreshSelectionBar()
    if (board.chosen().isEmpty())toast(label)
    aiWorker.execute {
        val result = runCatching { work(smartAi) }
        handler.post {
            if (destroyed || ticket != aiTicket)return@post
            if (selectionBarMode == "busy")selectionBarMode = "main"
            result.onSuccess { done(it) }.onFailure { e -> toast(AiMessages.text(this, (e as? AiException)?.reason ?: "service")) }
            refreshSelectionBar()
        }
    }
}

/** The offline smart tools (no internet, no key): recognition, solving, plotting, units, search, shapes. */
internal fun MainActivity.offlineSmart(smart: Boolean, ink: Boolean, action: (String, String, String, () -> Unit) -> Unit) {
    if (smart) {
        action("text", tr("To text", "به متن"), tr("Convert to text · detect language", "تبدیل به متن · تشخیص زبان")) {
            board.smartMode = "text"
            processSmart(board.chosen())
        }
        action("formula", tr("Solve", "حل کن"), tr("Calculate / solve", "محاسبه / حل")) {
            board.smartMode = "formula"
            processSmart(board.chosen())
        }
        action("graph", tr("Plot", "نمودار"), tr("Plot function", "رسم تابع")) {
            board.smartMode = "graph"
            processSmart(board.chosen())
        }
        action("convert", tr("Units", "تبدیل واحد"), tr("Convert units", "تبدیل واحد")) {
            board.smartMode = "convert"
            processSmart(board.chosen())
        }
        action("search", tr("Search", "جستجو"), tr("Search Google", "جستجو در گوگل")) {
            board.smartMode = "search"
            processSmart(board.chosen())
        }
    }
    if (ink) {
        action("shape", tr("To shape", "به شکل"), tr("Recognize shape", "تشخیص شکل")) {
            board.smartMode = "shape"
            processSmart(board.chosen())
        }
    }
}
internal fun MainActivity.providerName(p: String) = mapOf("gemini" to "Gemini", "openai" to "OpenAI", "openrouter" to "OpenRouter", "avalai" to "AvalAI")[p] ?: p

/**
 * The AI service for board actions: which service, which model, the key (shared with the smart assistant, entered once),
 * the students' level the answers are written for, and a daily cap. "Test" sends one tiny request.
 */
internal fun MainActivity.aiSettings() {
    val c = column().apply { pad(16) }
    var s = AiSettings.load(this)
    val model = field(s.model).apply {
        setSingleLine(true)
        textDirection = View.TEXT_DIRECTION_LTR
    }
    val imageModel = field("").apply {
        setSingleLine(true)
        textDirection = View.TEXT_DIRECTION_LTR
    }
    val keyState = label("", 13f, MUTED)
    val key = field("", tr("API key", "کلید API")).apply {
        setSingleLine(true)
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        textDirection = View.TEXT_DIRECTION_LTR
    }
    val presets = FlowRow(this, dp(6))
    fun refresh() {
        model.setText(AiSettings.load(this).let { if (it.provider == s.provider)it.model else AiSettings.defaults.getValue(s.provider) })
        imageModel.setText(AiSettings.imageModel(this, s.provider))
        keyState.text = if (voiceKeys.forProvider(s.provider).hasKey()) {
            tr("A key is saved for this service (also used by the smart assistant). Leave empty to keep it.", "برای این سرویس کلید ذخیره شده است (دستیار هوشمند هم از آن استفاده می‌کند). برای حفظ آن خالی بگذارید.")
        } else {
            tr("No key yet for this service.", "هنوز کلیدی برای این سرویس نیست.")
        }
        presets.removeAllViews()
        val ids = when (s.provider) {
            "gemini" -> listOf("gemini-2.5-flash", "gemini-2.5-pro")
            "openai" -> listOf("gpt-4o-mini", "gpt-4.1-mini", "gpt-4o")
            "openrouter" -> listOf("google/gemini-2.5-flash", "openai/gpt-4o-mini")
            else -> listOf("gpt-4o-mini", "gemini-2.5-flash", "gpt-4.1-mini")
        }
        ids.forEach { id -> presets.addView(button(id) { model.setText(id) }, ViewGroup.LayoutParams(-2, dp(40))) }
    }
    c.addView(sectionTitle(tr("Service", "سرویس")))
    c.addView(
        segmented(listOf("gemini" to "Gemini", "openai" to "OpenAI", "openrouter" to "OpenRouter", "avalai" to "AvalAI"), s.provider) {
            s = s.copy(provider = it)
            refresh()
        }
    )
    c.addView(label(tr("AvalAI's address is the one set in the smart assistant page.", "آدرس AvalAI همان است که در صفحهٔ دستیار هوشمند تنظیم شده."), 12f, MUTED))
    c.addView(sectionTitle(tr("Model", "مدل")))
    c.addView(model)
    c.addView(presets)
    c.addView(sectionTitle(tr("Picture model", "مدل تصویر")))
    c.addView(imageModel)
    c.addView(sectionTitle(tr("Key", "کلید")))
    c.addView(keyState)
    c.addView(key)
    c.addView(sectionTitle(tr("Write answers for", "پاسخ‌ها برای")))
    c.addView(segmented(listOf("primary" to tr("Primary", "دبستان"), "middle" to tr("Grades 7–9", "متوسطهٔ اول"), "high" to tr("Grades 10–12", "متوسطهٔ دوم")), s.grade) { s = s.copy(grade = it) })
    c.addView(sectionTitle(tr("Requests per day on this panel", "سقف درخواست روزانهٔ این پنل")))
    c.addView(segmented(listOf("50" to "50", "150" to "150", "500" to "500"), s.dailyLimit.toString()) { s = s.copy(dailyLimit = it.toInt()) })
    c.addView(label(tr("Only what you select is sent, as one picture. Nothing is sent until you tap an AI action.", "فقط چیزی که انتخاب می‌کنید، به‌صورت یک تصویر، فرستاده می‌شود؛ تا یکی از کارهای هوشمند را نزنید چیزی ارسال نمی‌شود."), 12f, MUTED).apply { setPadding(dp(4), dp(10), dp(4), dp(4)) })
    refresh()
    fun store(): Boolean {
        val id = model.text.toString().trim().removePrefix("models/")
        if (!VoiceSettings.validCompatModel(id)) {
            model.error = s("voice_model_error")
            return false
        }
        val pasted = key.text.toString()
        if (pasted.isNotBlank()) {
            val v = VoiceApiKey.normalize(pasted)
            if (!VoiceApiKey.isAcceptable(v)) {
                key.error = s("voice_key_invalid")
                return false
            }
            try {
                voiceKeys.forProvider(s.provider).save(pasted)
            } catch (_: Exception) {
                key.error = s("voice_key_invalid")
                return false
            }
        }
        val picture = imageModel.text.toString().trim()
        if (!VoiceSettings.validCompatModel(picture)) {
            imageModel.error = s("voice_model_error")
            return false
        }
        s = s.copy(model = id)
        s.save(this)
        AiSettings.saveImageModel(this, s.provider, picture)
        SmartAi.clearCache()
        key.setText("")
        refresh()
        return true
    }
    val d = dialog(tr("AI service", "سرویس هوش"), ScrollView(this).apply { addView(c) })
    c.addView(
        row().apply {
            addView(
                button(s("apply"), true) {
                    if (store()) {
                        toast(s("saved"))
                        d.dismiss()
                        refreshSelectionBar()
                    }
                },
                LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(6) }
            )
            addView(button(tr("Test", "آزمایش")) { if (store())runAi(tr("Testing…", "در حال آزمایش…"), { ai -> ai.ask("Reply with the single word OK.", "Are you there?") }) { r -> toast(tr("Connected: ", "وصل شد: ") + r.take(40)) } }, LinearLayout.LayoutParams(0, dp(52), 1f))
        },
        LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
    )
}

/** Lets labs ask the AI about the running simulation, when a service is configured. */
internal fun MainActivity.installLabAssistant() {
    Labs.assistant = { name, description, values, picture, answer ->
        if (!smartAi.configured()) {
            picture.recycle()
            toast(AiMessages.text(this, "no_key"))
            answer(null)
        } else {
            aiWorker.execute {
                val result = runCatching {
                    try {
                        smartAi.ask(aiSystem(), AiTasks.lab(name, description, values), picture)
                    } finally {
                        picture.recycle()
                    }
                }
                handler.post {
                    if (destroyed)return@post
                    result.onSuccess { answer(it) }.onFailure { e ->
                        toast(AiMessages.text(this, (e as? AiException)?.reason ?: "service"))
                        answer(null)
                    }
                }
            }
        }
    }
}
internal fun MainActivity.cancelAi() {
    aiTicket++
    selectionBarMode = "main"
    refreshSelectionBar()
}
internal fun MainActivity.aiSystem() = AiTasks.system(this, smartAi.settings.grade)

/** Sends only the selection, as one picture; the bitmap is released as soon as the request is built. */
internal fun <T> MainActivity.aiOnSelection(label: String, ask: (SmartAi, android.graphics.Bitmap) -> T, done: (T, RectF) -> Unit) {
    val items = board.chosen()
    if (items.isEmpty())return
    val (bitmap, bounds) = board.snapshotItems(items)
    runAi(label, { ai ->
        try {
            ask(ai, bitmap)
        } finally {
            bitmap.recycle()
        }
    }) { done(it, bounds) }
}

/**
 * A free place for a [w]×[h] block near [near]: to its side first (or below when [below]), then the other options,
 * each nudged past anything already there, so AI answers never land on the teacher's own work.
 */
internal fun MainActivity.freeSpot(near: RectF, w: Float, h: Float, below: Boolean = false, extra: List<RectF> = emptyList()): PointF {
    val others = store.page.items.filter { it.pane == board.activePane }.map { contentBounds(listOf(it)) } + extra
    val gap = 32f
    val start = layoutDirection(near, w, gap)
    val options = if (below)listOf(PointF(near.left, near.bottom + gap), start, PointF(near.left, near.top - gap - h))else listOf(start, PointF(near.left, near.bottom + gap), PointF(near.left, near.top - gap - h))
    for (o in options) {
        var y = o.y
        repeat(16) {
            val r = RectF(o.x, y, o.x + w, y + h)
            val hit = others.filter { RectF.intersects(it, r) }.maxByOrNull { it.bottom }
            if (hit == null)return PointF(o.x, y)
            y = hit.bottom + gap
        }
    }
    return options.first()
}

/** Beside the work toward the reading direction's end: right in English, left in Persian. */
internal fun MainActivity.layoutDirection(near: RectF, w: Float, gap: Float) = if (resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL)PointF(near.left - gap - w, near.top)else PointF(near.right + gap, near.top)
internal fun MainActivity.aiNote(bounds: RectF, text: String, color: Int = 0xfffff6c7.toInt(), extra: List<RectF> = emptyList()): Item {
    val note = Item(kind = "sticky", text = text, width = 22f, w = 380f, h = 160f, noteColor = color, layerId = store.page.activeLayerId, pane = board.activePane)
    Fonts.stamp(note, true)
    TextLayout.fit(note)
    freeSpot(bounds, note.w, note.h, extra = extra).let {
        note.x = it.x
        note.y = it.y
    }
    return note
}

/** Adds the AI's result as one Undo step, selects its first part and brings it into view with the work it is about. */
internal fun MainActivity.addAndSelect(items: List<Item>, about: RectF? = null) {
    if (items.isEmpty())return
    store.editMetadata { store.page.items.addAll(items) }
    board.selected.clear()
    board.selected.add(items.first().id)
    board.tool = "select"
    val area = contentBounds(items)
    about?.let { area.union(it) }
    board.reveal(area)
    board.sceneChanged()
    refreshDock()
}
internal fun MainActivity.aiExplain(kind: String) {
    val prompt = when (kind) {
        "simplify" -> AiTasks.simplify()
        "translate" -> AiTasks.translate(this)
        else -> AiTasks.explain()
    }
    val label = when (kind) {
        "simplify" -> tr("Simplifying…", "در حال ساده‌سازی…")
        "translate" -> tr("Translating…", "در حال ترجمه…")
        else -> tr("Explaining…", "در حال توضیح…")
    }
    aiOnSelection(label, { ai, b -> ai.ask(aiSystem(), prompt, b) }) { text, bounds -> addAndSelect(listOf(aiNote(bounds, text)), bounds) }
}

/** Mistakes are circled in red on the work itself, each with a short fix beside it; one Undo removes them all. */
internal fun MainActivity.aiCheck() {
    aiOnSelection(tr("Checking…", "در حال بررسی…"), { ai, b -> AiTasks.parseCheck(ai.askJson(aiSystem(), AiTasks.check(), b)) }) { check, bounds ->
        val red = 0xffdc2626.toInt()
        val added = mutableListOf<Item>()
        check.mistakes.forEach { (r, note) ->
            val x = bounds.left + r.left * bounds.width() - 10f
            val y = bounds.top + r.top * bounds.height() - 10f
            val w = r.width() * bounds.width() + 20f
            val h = r.height() * bounds.height() + 20f
            added.add(Item(kind = "shape", shape = "ellipse", x = x, y = y, w = w, h = h, color = red, width = 4f, layerId = store.page.activeLayerId, pane = board.activePane))
            // The fix goes beside the work, in a free spot, numbered to match its circle.
            if (note.isNotBlank()) {
                val n = check.mistakes.indexOfFirst { it.first === r } + 1
                added.add(
                    Item(kind = "text", text = n.toString(), color = red, width = 22f, x = x + w - 8f, y = y - 30f, w = 40f, h = 40f, layerId = store.page.activeLayerId, pane = board.activePane).also {
                        Fonts.stamp(it, true)
                        TextLayout.fit(it)
                    }
                )
                val fix = Item(kind = "text", text = "$n. $note", color = red, width = 22f, w = 360f, h = 60f, layerId = store.page.activeLayerId, pane = board.activePane).also {
                    Fonts.stamp(it, true)
                    TextLayout.fit(it)
                }
                freeSpot(bounds, fix.w, fix.h, extra = added.filter { it.kind == "text" }.map { contentBounds(listOf(it)) }).let {
                    fix.x = it.x
                    fix.y = it.y
                }
                added.add(fix)
            }
        }
        if (check.mistakes.isEmpty()) {
            added.add(aiNote(bounds, (if (check.correct)"✓ " else "") + check.summary.ifBlank { tr("No mistakes found", "اشتباهی پیدا نشد") }, 0xffdcfce7.toInt()))
        } else {
            Sounds.play(this, Sounds.Cue.TICK)
        }
        addAndSelect(added, bounds)
        if (check.summary.isNotBlank())toast(check.summary)
    }
}

/** Steps appear on the board one at a time, under the work, when the teacher taps Next. */
internal fun MainActivity.aiSteps() {
    aiOnSelection(tr("Solving…", "در حال حل…"), { ai, b -> AiTasks.parseSteps(ai.askJson(aiSystem(), AiTasks.steps(), b)) }) { steps, bounds ->
        stepReveal?.let { canvasHost.removeView(it) }
        val start = freeSpot(bounds, maxOf(bounds.width(), 420f), 60f * (steps.steps.size + 1), below = true)
        var y = start.y
        val page = store.page
        val lines = steps.steps.mapIndexed { i, t -> "${i + 1}. $t" } + listOfNotNull(steps.answer.takeIf { it.isNotBlank() }?.let { "= $it" })
        stepReveal = StepReveal(this, lines, { i ->
            // Only onto the page the steps belong to; switching pages pauses the reveal.
            if (store.page === page) {
                val item = Item(
                    kind = "text", text = lines[i], color = if (i == lines.lastIndex && steps.answer.isNotBlank())TEAL else NAVY, width = 26f, x = start.x, y = y, w = maxOf(bounds.width(), 420f), h = 60f,
                    layerId = store.page.activeLayerId, pane = board.activePane
                ).also {
                    Fonts.stamp(it, true)
                    TextLayout.fit(it)
                }
                y += item.h + 10f
                store.editMetadata { store.page.items.add(item) }
                board.reveal(contentBounds(listOf(item)))
                board.sceneChanged()
            }
        }) {
            stepReveal?.let { canvasHost.removeView(it) }
            stepReveal = null
        }.also { canvasHost.addView(it, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(96) }) }
    }
}

/** Handwriting (Persian included) becomes typed text in place, through the AI's vision; the ink goes, Undo brings it back. */
internal fun MainActivity.aiCleanup() {
    val ink = board.chosen().filter { it.kind == "ink" && !it.locked && store.page.editable(it) }
    if (ink.isEmpty())return
    val (bitmap, bounds) = board.snapshotItems(ink)
    runAi(tr("Reading the handwriting…", "در حال خواندن دست‌خط…"), { ai ->
        try {
            ai.askJson(aiSystem(), AiTasks.transcribe(), bitmap).optString("text").trim()
        } finally {
            bitmap.recycle()
        }
    }) { text ->
        if (text.isBlank()) {
            toast(AiMessages.text(this, "format"))
            return@runAi
        }
        val item = Item(
            kind = "text", text = text, color = ink.first().color, width = (bounds.height() / maxOf(1, text.lines().size) * .55f).coerceIn(18f, 64f), x = bounds.left, y = bounds.top, w = maxOf(bounds.width(), 200f), h = bounds.height(),
            layerId = ink.first().layerId, pane = ink.first().pane
        ).also {
            Fonts.stamp(it, true)
            TextLayout.fit(it)
        }
        store.editMetadata {
            store.page.items.removeAll(ink.toSet())
            store.page.items.add(item)
        }
        board.selected.clear()
        board.selected.add(item.id)
        board.sceneChanged()
        refreshDock()
    }
}

/** A picture from a description (Add panel), placed in the free middle of the board. */
internal fun MainActivity.aiPicture() {
    input(tr("Describe the picture", "تصویر را توصیف کنید"), "") { text ->
        require(text.isNotBlank())
        runAi(tr("Drawing…", "در حال کشیدن…"), { ai -> ai.picture(text) }) { bitmap -> insertBitmap(bitmap) }
    }
}

/** The teacher's rough sketch becomes a clean illustration beside it; the sketch stays. */
internal fun MainActivity.aiSketchToPicture() {
    aiOnSelection(tr("Drawing from the sketch…", "در حال کشیدن از روی طرح…"), { ai, b -> ai.picture("", b) }) { bitmap, bounds ->
        work({ media.save(bitmap) }) { name ->
            val w = maxOf(bounds.width(), 420f)
            val h = w * bitmap.height / bitmap.width
            val item = Item(kind = "image", asset = name, w = w, h = h, layerId = store.page.activeLayerId, pane = board.activePane)
            freeSpot(bounds, w, h).let {
                item.x = it.x
                item.y = it.y
            }
            bitmap.recycle()
            addAndSelect(listOf(item), bounds)
        }
    }
}
internal fun MainActivity.groupableNotes(items: List<Item>) = items.filter { it.kind == "sticky" && it.shape !in listOf("mindnode", "element_card") && !it.locked }

/** After a brainstorm: the notes' words go to the AI, which sorts them into titled groups; the notes then move into columns. */
internal fun MainActivity.aiGroups() {
    val notes = groupableNotes(board.chosen())
    if (notes.size < 3)return
    val texts = notes.map { it.text }
    runAi(tr("Grouping ideas…", "در حال دسته‌بندی ایده‌ها…"), { ai -> AiTasks.parseGroups(ai.askJson(aiSystem(), AiTasks.groups(texts)), texts.size) }) { groups ->
        arrangeGroups(groups.map { g -> g.title.ifBlank { if (groups.size > 1)tr("Other", "سایر")else "" } to g.notes.map { notes[it] } })
    }
}

/** Offline: one column per note color, in the order the colors first appear. */
internal fun MainActivity.groupByColor(notes: List<Item>) {
    val byColor = notes.groupBy { it.noteColor }.values.toList()
    // A single color would make one tall column: tidy into a near-square grid instead.
    val columns = if (byColor.size > 1)byColor else notes.chunked(kotlin.math.ceil(kotlin.math.sqrt(notes.size.toDouble())).toInt())
    arrangeGroups(columns.map { "" to it })
}

/**
 * Moves the notes into one column per group, from the selection's reading-start corner, each under a bold title;
 * the notes themselves are kept as they are (text, color, size). One Undo puts everything back.
 */
internal fun MainActivity.arrangeGroups(groups: List<Pair<String, List<Item>>>) {
    val notes = groups.flatMap { it.second }.filter { n -> store.page.items.any { it === n } }
    if (notes.isEmpty())return
    val bounds = contentBounds(notes)
    val rtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    val column = notes.maxOf { it.w } + 48f
    val gap = 20f
    val titles = mutableListOf<Item>()
    store.editMetadata {
        groups.filter { g -> g.second.any { it in notes } }.forEachIndexed { i, (title, members) ->
            val x = if (rtl)bounds.right - (i + 1) * column + 48f else bounds.left + i * column
            var y = bounds.top
            if (title.isNotBlank()) {
                val t = Item(kind = "text", text = title, bold = true, width = 28f, x = x, y = y, w = column - 48f, h = 56f, layerId = store.page.activeLayerId, pane = board.activePane)
                Fonts.stamp(t, true)
                TextLayout.fit(t)
                titles.add(t)
                y += t.h + 16f
            }
            members.filter { it in notes }.forEach { n ->
                n.x = x
                n.y = y
                y += n.h + gap
            }
        }
        store.page.items.addAll(titles)
    }
    board.selected.clear()
    board.selected.addAll((notes + titles).map { it.id })
    board.tool = "select"
    board.reveal(contentBounds(notes + titles))
    board.sceneChanged()
    refreshSelectionBar()
    refreshDock()
}

/** A whole-class quiz from what is selected, or from a typed topic when nothing is. */
internal fun MainActivity.aiQuiz() {
    if (board.chosen().isNotEmpty()) {
        aiOnSelection(tr("Writing questions…", "در حال نوشتن سؤال‌ها…"), { ai, b -> AiTasks.parseQuiz(ai.askJson(aiSystem(), AiTasks.quiz(5), b)) }) { q, _ -> ClassQuiz.show(this, tr("Quiz", "آزمونک"), q, { keepQuiz(tr("Quiz", "آزمونک"), q) }) }
    } else {
        input(tr("Quiz topic", "موضوع آزمونک"), "") { topic ->
            require(topic.isNotBlank())
            runAi(tr("Writing questions…", "در حال نوشتن سؤال‌ها…"), { ai -> AiTasks.parseQuiz(ai.askJson(aiSystem(), AiTasks.quizFromTopic(topic, 5))) }) { q -> ClassQuiz.show(this, topic, q, { keepQuiz(topic, q) }) }
        }
    }
}

/** Quizzes live in the lesson file: written by hand or kept from the AI, and played again next lesson. */
internal fun MainActivity.saveQuiz(quiz: SavedQuiz) {
    projectStore.editMetadata {
        val list = projectStore.lesson.quizzes ?: mutableListOf<SavedQuiz>().also { projectStore.lesson.quizzes = it }
        val at = list.indexOfFirst { it.id == quiz.id }
        if (at >= 0) {
            list[at] = quiz
        } else if (list.size < 50) {
            list.add(quiz)
        }
    }
}
internal fun MainActivity.keepQuiz(title: String, questions: List<AiTasks.Question>) = saveQuiz(SavedQuiz(title = title.take(200), questions = questions.take(100).map { SavedQuestion(it.text.take(1000), it.options.map { o -> o.take(300) }, it.answer) }))
internal fun MainActivity.classQuizzes() = QuizBuilder.manager(
    this,
    { projectStore.lesson.quizzes.orEmpty().toList() },
    { saveQuiz(it) },
    { id -> projectStore.editMetadata { projectStore.lesson.quizzes?.removeAll { it.id == id } } },
    if (smartAi.configured())({ aiQuiz() })else null
)

/** A mind map of real mind-map nodes (editable, movable), beside the selection or in the middle of the board. */
internal fun MainActivity.aiMindMap() {
    fun build(root: AiTasks.Node, at: PointF) {
        val layer = store.page.activeLayerId
        val pane = board.activePane
        val items = mutableListOf<Item>()
        fun node(title: String, x: Float, y: Float, parent: String, color: Int) = Item(kind = "sticky", shape = "mindnode", text = title, width = 22f, w = 200f, h = 90f, x = x, y = y, noteColor = color, parentNode = parent, layerId = layer, pane = pane).also { Fonts.stamp(it, true) }
        val leaves = root.children.sumOf { maxOf(1, it.children.size) }
        var y = at.y - leaves * 55f
        val top = node(root.title, at.x, at.y - 45f, "", 0xffe2f2f0.toInt())
        items.add(top)
        root.children.forEach { branch ->
            val span = maxOf(1, branch.children.size) * 110f
            val b = node(branch.title, at.x + 300f, y + span / 2 - 45f, top.id, 0xffeceaf7.toInt())
            items.add(b)
            branch.children.forEachIndexed { i, leaf -> items.add(node(leaf.title, at.x + 600f, y + i * 110f, b.id, 0xfffff6e0.toInt())) }
            y += span
        }
        addAndSelect(items)
    }

    // The whole tree needs about 800 × (110 per leaf): find that much free room beside or below the selection.
    fun spot(root: AiTasks.Node, near: RectF): PointF {
        val h = maxOf(1, root.children.sumOf { maxOf(1, it.children.size) }) * 110f
        val p = freeSpot(near, 820f, h)
        return PointF(p.x, p.y + h / 2)
    }
    if (board.chosen().isNotEmpty()) {
        aiOnSelection(tr("Building the mind map…", "در حال ساخت نقشهٔ ذهنی…"), { ai, b -> AiTasks.parseMindMap(ai.askJson(aiSystem(), AiTasks.mindMap(), b)) }) { root, bounds -> build(root, spot(root, bounds)) }
    } else {
        input(tr("Mind map topic", "موضوع نقشهٔ ذهنی"), "") { topic ->
            require(topic.isNotBlank())
            runAi(tr("Building the mind map…", "در حال ساخت نقشهٔ ذهنی…"), { ai -> AiTasks.parseMindMap(ai.askJson(aiSystem(), AiTasks.mindMapFromTopic(topic))) }) { root ->
                val c = board.center()
                build(root, PointF(c.x - 400f, c.y))
            }
        }
    }
}

/** End of lesson: the pages go to the AI as one contact sheet; the summary becomes a new last page. */
internal fun MainActivity.aiSummary() {
    val pages = projectStore.lesson.pages.filter { it.items.isNotEmpty() }.takeLast(6)
    if (pages.isEmpty()) {
        toast(tr("The lesson has no content yet", "درس هنوز محتوایی ندارد"))
        return
    }
    val sheet = android.graphics.Bitmap.createBitmap(960, 810, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = Canvas(sheet)
    canvas.drawColor(Color.WHITE)
    pages.forEachIndexed { i, page ->
        canvas.save()
        canvas.translate((i % 2) * 480f, (i / 2) * 270f)
        canvas.clipRect(0f, 0f, 476f, 266f)
        board.renderer.page(canvas, page, 476, 266, true)
        canvas.restore()
    }
    runAi(tr("Summarizing the lesson…", "در حال خلاصه‌کردن درس…"), { ai ->
        try {
            AiTasks.parseSummary(ai.askJson(aiSystem(), AiTasks.summary(), sheet))
        } finally {
            sheet.recycle()
        }
    }) { summary ->
        if (projectStore.lesson.pages.size >= 200)return@runAi
        activateBoard(whiteboard)
        val page = newBoardPage(projectStore.page)
        val layer = page.activeLayerId
        fun text(t: String, size: Float, y: Float, color: Int = INK_NAVY) = Item(kind = "text", text = t, width = size, x = 120f, y = y, w = 1200f, h = size * 2, color = color, layerId = layer).also {
            Fonts.stamp(it, true)
            TextLayout.fit(it)
        }
        val title = text(summary.title.ifBlank { tr("Lesson summary", "خلاصهٔ درس") }, 48f, 80f)
        val points = text(summary.points.joinToString("\n") { "• $it" }, 28f, title.y + title.h + 30f)
        val items = mutableListOf(title, points)
        if (summary.homework.isNotBlank())items.add(text(tr("Practice: ", "تمرین: ") + summary.homework, 26f, points.y + points.h + 30f, INK_TEAL))
        // A QR of the summary text: students scan it with the phone camera and keep it, no network or app needed.
        val qrText = (listOf(title.text) + summary.points.map { "• $it" } + listOfNotNull(summary.homework.takeIf { it.isNotBlank() }?.let { tr("Practice: ", "تمرین: ") + it })).joinToString("\n").take(1100)
        val qrName = try {
            media.save(Sharing.qr(qrText))
        } catch (_: Exception) {
            null
        }
        if (qrName != null) {
            // Under the summary, where it is in view without panning; the caption beside it says what it is for.
            val top = items.maxOf { it.y + it.h } + 40f
            items.add(Item(kind = "image", asset = qrName, x = 120f, y = top, w = 240f, h = 240f, layerId = layer))
            items.add(
                text(tr("Scan with a phone camera to keep this summary", "با دوربین گوشی اسکن کنید تا این خلاصه را داشته باشید"), 22f, top + 100f, INK_MUTED).also {
                    it.x = 390f
                    it.w = 700f
                    TextLayout.fit(it)
                }
            )
        }
        page.items.addAll(items)
        projectStore.editMetadata {
            projectStore.lesson.pages.add(page)
            projectStore.lesson.current = projectStore.lesson.pages.lastIndex
        }
        whiteboard.clearSelection()
        whiteboard.reset()
        refreshPages()
        com.google.android.material.snackbar.Snackbar.make(canvasHost, tr("Summary added as the last page", "خلاصه به‌عنوان صفحهٔ آخر اضافه شد"), com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
            .setAction(tr("Share PDF with the class", "PDF برای کلاس")) { share() }.show()
    }
}

/** A picture from the search window: https or inline data only, at most 10 MB, scaled down to 2048 px, then placed in view. */
internal fun MainActivity.addWebImage(src: String) {
    work({
        val bytes = if (src.startsWith("data:image/")) {
            android.util.Base64.decode(src.substringAfter("base64,"), android.util.Base64.DEFAULT)
        } else {
            require(src.startsWith("https://")) { tr("Only secure (https) pictures can be added", "فقط تصویر امن (https) اضافه می‌شود") }
            okhttp3.OkHttpClient.Builder().callTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build().newCall(okhttp3.Request.Builder().url(src).build()).execute().use { r ->
                require(r.isSuccessful)
                val body = r.body ?: throw IllegalStateException("empty")
                require(body.contentLength() <= 10_000_000)
                body.byteStream().readNBytesCompat(10_000_001).also { require(it.size <= 10_000_000) }
            }
        }
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048)sample *= 2
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) ?: throw IllegalStateException(tr("Not a picture", "تصویر نیست"))
        val name = media.save(bitmap)
        val size = bitmap.width to bitmap.height
        bitmap.recycle()
        name to size
    }) { (name, size) ->
        val w = minOf(600f, size.first.toFloat().coerceAtLeast(120f))
        val item = Item(kind = "image", asset = name, w = w, h = w * size.second / size.first.coerceAtLeast(1))
        board.insert(item)
        refreshDock()
        toast(tr("Picture added", "تصویر اضافه شد"))
    }
}

/** Offline: a bar chart from the table's first numeric column, beside the table. */
internal fun MainActivity.chartFromTable(t: Item) {
    val spec = Charts.fromTable(Tables.cells(t)) ?: run {
        toast(tr("Add a column of numbers under a heading (two rows or more)", "یک ستون عدد زیر یک عنوان بنویسید (دست‌کم دو ردیف)"))
        return
    }
    insertChart(spec, contentBounds(listOf(t)))
}

/** A chart for any request ("rainfall in Tehran by month"), with data written by the AI. */
internal fun MainActivity.aiChart() {
    input(tr("What should the chart show?", "نمودار چه چیزی را نشان دهد؟"), "") { request ->
        require(request.isNotBlank())
        runAi(tr("Making the chart…", "در حال ساخت نمودار…"), { ai -> Charts.parse(ai.askJson(aiSystem(), Charts.prompt(request))) }) { spec ->
            val c = board.center()
            insertChart(spec, RectF(c.x - 350f, c.y - 225f, c.x - 350f, c.y - 225f))
        }
    }
}
internal fun MainActivity.insertChart(spec: Charts.Spec, near: RectF) {
    val dark = Color.luminance(store.page.panes.getOrNull(board.activePane)?.background ?: Color.WHITE) < .35
    val bitmap = Charts.render(spec, dark)
    work({ media.save(bitmap) }) { name ->
        val item = Item(kind = "image", asset = name, w = 700f, h = 450f, layerId = store.page.activeLayerId, pane = board.activePane)
        (if (near.width() > 0)freeSpot(near, item.w, item.h) else PointF(near.left, near.top)).let {
            item.x = it.x
            item.y = it.y
        }
        bitmap.recycle()
        addAndSelect(listOf(item), near.takeIf { it.width() > 0 })
    }
}
