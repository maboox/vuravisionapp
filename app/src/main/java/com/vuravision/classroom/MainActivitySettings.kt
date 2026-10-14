package com.vuravision.classroom
import android.app.*
import android.content.*
import android.graphics.*
import android.os.*
import android.view.*
import android.widget.*
import com.vuravision.classroom.board.Item
import com.vuravision.classroom.board.Page
import com.vuravision.classroom.board.Point
import com.vuravision.classroom.board.TextLayout
import com.vuravision.classroom.board.fit
import com.vuravision.classroom.board.reset
import com.vuravision.classroom.games.Sounds
import com.vuravision.classroom.room.DeviceProfiles
import com.vuravision.classroom.ui.AboutGesture
import com.vuravision.classroom.ui.CHALKBOARD
import com.vuravision.classroom.ui.DARK_BOARD
import com.vuravision.classroom.ui.Fonts
import com.vuravision.classroom.ui.MUTED
import com.vuravision.classroom.ui.Motion
import com.vuravision.classroom.ui.NAVY
import com.vuravision.classroom.ui.ORANGE
import com.vuravision.classroom.ui.OptionAdapter
import com.vuravision.classroom.ui.PAPER
import com.vuravision.classroom.ui.PatternGrid
import com.vuravision.classroom.ui.SettingRow
import com.vuravision.classroom.ui.SettingsCategory
import com.vuravision.classroom.ui.SettingsScreen
import com.vuravision.classroom.ui.Subjects
import com.vuravision.classroom.ui.TEAL
import com.vuravision.classroom.ui.TouchDiagnostics
import com.vuravision.classroom.ui.UiTheme
import com.vuravision.classroom.ui.button
import com.vuravision.classroom.ui.card
import com.vuravision.classroom.ui.column
import com.vuravision.classroom.ui.dp
import com.vuravision.classroom.ui.field
import com.vuravision.classroom.ui.infoTitle
import com.vuravision.classroom.ui.label
import com.vuravision.classroom.ui.pad
import com.vuravision.classroom.ui.rounded
import com.vuravision.classroom.ui.row
import com.vuravision.classroom.ui.s
import com.vuravision.classroom.ui.scrollRow
import com.vuravision.classroom.ui.sectionTitle
import com.vuravision.classroom.ui.switchRow
import com.vuravision.classroom.ui.tr
import com.vuravision.classroom.voice.VoiceSettings
import com.vuravision.classroom.voice.VoiceSettingsUi
import java.util.Locale
import kotlin.math.*

// The settings screen and its pages (split from MainActivity, item 11).

internal fun MainActivity.settings() = settingsAt(prefs.getString("settingsTab", null))
internal fun MainActivity.settingsAt(tab: String?) {
    menuReturn = null
    fun page(title: String, value: () -> String = { "" }, open: () -> Unit) = SettingRow.Page(title, value) {
        menuReturn = {}
        try {
            open()
        } finally {
            menuReturn = null
        }
    }
    fun roomSafe(action: () -> Unit) {
        if (rooms?.active == true)toast(s("room_leave_first"))else action()
    }
    fun restart(edit: SharedPreferences.Editor.() -> Unit) {
        persist()
        prefs.edit().apply(edit).apply()
        worker.execute { handler.post { if (!destroyed)recreate() } }
    }
    val providers = mapOf("gemini" to "Gemini", "openai" to "OpenAI", "openrouter" to "OpenRouter", "avalai" to "AvalAI")
    val categories = listOf(
        SettingsCategory(
            "general",
            tr("General", "عمومی"),
            "settings",
            listOf(
                SettingRow.Choice(s("language"), listOf("en" to "English", "fa" to "فارسی"), { prefs.getString("language", "en") ?: "en" }) { v -> roomSafe { restart { putString("language", v) } } },
                // Item 29: the panel's size, not "small/large": bigger panels are read from farther away, so controls grow with them.
                SettingRow.Choice(
                    tr("Panel size", "اندازهٔ نمایشگر"),
                    panelSizes.map { it.first to it.second },
                    { prefs.getFloat("uiScale", 1f).let { v -> panelSizes.minByOrNull { kotlin.math.abs(it.third - v) }!!.first } }
                ) { v -> roomSafe { restart { putFloat("uiScale", panelSizes.first { it.first == v }.third) } } },
                SettingRow.Choice(tr("Interface", "ظاهر برنامه"), listOf("light" to tr("Light", "روشن"), "dark" to tr("Dark", "تیره")), { if (UiTheme.dark)"dark" else "light" }) { v -> roomSafe { restart { putString("uiTheme", v) } } },
                SettingRow.Toggle(tr("Reduce motion", "کاهش حرکت"), tr("No sliding panels, shaking or confetti", "بدون لغزش پنل‌ها، لرزش و کاغذ رنگی"), { prefs.getBoolean("reduceMotion", false) }) { v ->
                    prefs.edit().putBoolean("reduceMotion", v).apply()
                    Motion.load(this)
                },
                SettingRow.Choice(
                    tr("Toolbar height on the side", "ارتفاع نوار ابزار کنار صفحه"),
                    listOf("top" to tr("Higher", "بالاتر"), "middle" to tr("Middle", "وسط"), "bottom" to tr("Lower", "پایین‌تر")),
                    { toolDock.position }
                ) { toolDock.position = it },
                page(s("fonts"), { Fonts.persian.first { it.id == Fonts.persianId }.let { tr(it.en, it.fa) } + " · " + Fonts.english.first { it.id == Fonts.englishId }.let { tr(it.en, it.fa) } }) { fontSettings() }
            )
        ),
        SettingsCategory(
            "input",
            tr("Pen and touch", "قلم و لمس"),
            "pen",
            listOf(
                SettingRow.Toggle(tr("Keep my tool after adding text or a note", "بعد از افزودن متن یا یادداشت، ابزار عوض نشود"), tr("Off: the new object is selected so it can be moved at once", "خاموش: شیء تازه انتخاب می‌شود تا فوراً جابه‌جا شود"), { prefs.getBoolean("keepTool", false) }) { v ->
                    prefs.edit().putBoolean("keepTool", v).apply()
                    whiteboard.keepToolAfterInsert = v
                    board.keepToolAfterInsert = v
                },
                SettingRow.Toggle(tr("Pen + touch (off: two tips)", "قلم + لمس (خاموش: دو سر قلم)"), null, { board.touchMode }) { v ->
                    if (!board.hasActiveInteraction) {
                        board.touchMode = v
                        prefs.edit().putBoolean("touchMode", v).apply()
                        refreshDock()
                    }
                },
                SettingRow.Toggle(s("multi_touch_short"), null, { board.profile.multiTouch }) { v ->
                    if (!board.hasActiveInteraction) {
                        board.profile.multiTouch = v
                        prefs.edit().putBoolean("multiTouch", v).apply()
                    }
                },
                SettingRow.Toggle(tr("Two-finger double tap: Undo", "دو بار ضربه با دو انگشت: بازگردانی"), tr("Hold the second tap and slide to browse history", "ضربهٔ دوم را نگه دارید و بکشید تا تاریخچه را مرور کنید"), { prefs.getBoolean("twoFingerUndo", true) }) { v ->
                    prefs.edit().putBoolean("twoFingerUndo", v).apply()
                    whiteboard.gestures.undoEnabled = v
                    board.gestures.undoEnabled = v
                    board.gestures.reset()
                },
                SettingRow.Toggle(tr("Pie menu (five fingers)", "منوی شعاعی (پنج انگشت)"), tr("Double tap with five fingers to open your shortcuts", "با پنج انگشت دو بار ضربه بزنید تا میان‌برهایتان باز شود"), { prefs.getBoolean("pieEnabled", false) }) { v ->
                    prefs.edit().putBoolean("pieEnabled", v).apply()
                    board.gestures.reset()
                    whiteboard.gestures.pieEnabled = v
                    board.gestures.pieEnabled = v
                },
                page(s("pie_settings")) { pieSettings() },
                page(s("calibration"), { tr("Pen tips, palm and real centimetres", "سر قلم، کف دست و سانتی‌متر واقعی") }) { calibrationSettings() }
            )
        ),
        SettingsCategory(
            "board",
            tr("Board", "تخته"),
            "background",
            listOf(
                SettingRow.Choice(
                    tr("Board style", "سبک تخته"),
                    listOf("white" to tr("Whiteboard", "وایت‌برد"), "chalk" to tr("Chalkboard", "تخته‌سبز"), "dark" to tr("Dark", "تیره")),
                    {
                        when (prefs.getInt("defaultBackground", Color.WHITE)) {
                            CHALKBOARD -> "chalk"
                            DARK_BOARD -> "dark"
                            else -> "white"
                        }
                    }
                ) { applyBoardStyle(it) },
                page(s("page_background"), { tr("For new pages", "برای صفحه‌های جدید") }) { defaultBackgroundSettings() },
                page(s("history_settings"), { tr("${HistorySettings.load(this)} steps", "${HistorySettings.load(this)} مرحله") }) { historySettings() }
            )
        ),
        SettingsCategory(
            "smart",
            tr("Smart", "هوشمند"),
            "sparkle",
            listOf(
                page(tr("AI service", "سرویس هوش"), { if (smartAi.configured())smartAi.settings.let { "${providerName(it.provider)} · ${it.model} · " + tr("${smartAi.usedToday()} of ${it.dailyLimit} today", "${smartAi.usedToday()} از ${it.dailyLimit} امروز") } else tr("Not set up", "راه‌اندازی نشده") }) { aiSettings() },
                page(s("voice_assistant"), { if (VoiceSettings.enabled(this))tr("On", "روشن") + " · " + (providers[VoiceSettings.load(this).provider] ?: VoiceSettings.load(this).provider)else tr("Off", "خاموش") }) { voiceSettings() },
                SettingRow.Toggle(tr("Voice commands", "فرمان صوتی"), tr("The assistant can turn pages, change pen and tool, start a timer or open a class tool when you ask; never deletes", "دستیار در صورت درخواست صفحه را عوض می‌کند، قلم و ابزار را تغییر می‌دهد، تایمر می‌گذارد یا ابزار کلاس باز می‌کند؛ هرگز چیزی پاک نمی‌کند"), { prefs.getBoolean("voiceCommands", true) }) { v -> prefs.edit().putBoolean("voiceCommands", v).apply() },
                page(s("models"), { tr("Handwriting recognition", "تشخیص دست‌خط") }) { models() },
                page(s("google_search_settings")) { GoogleSearchSettingsUi(this).show(menuReturn) }
            )
        ),
        SettingsCategory(
            "class",
            tr("Class", "کلاس"),
            "users",
            listOf(
                SettingRow.Choice(tr("I teach", "درس من"), Subjects.keys.map { it to Subjects.name(this, it) }, { Subjects.current(this) }) { v -> prefs.edit().putString("subject", v).apply() },
                SettingRow.Toggle(tr("Game sounds", "صدای بازی‌ها"), tr("Short sounds for right answers and winners", "صدای کوتاه برای پاسخ درست و برنده"), { Sounds.enabled(this) }) { prefs.edit().putBoolean("gameSounds", it).apply() },
                page(s("device_profile"), { DeviceProfiles.load(this).name }) { roomUi?.profile(menuReturn) },
                page(s("shared_room"), { tr("Share this board with devices on the same network", "اشتراک همین تخته با دستگاه‌های همان شبکه") }) { sharedRoom() }
            )
        ),
        SettingsCategory(
            "about",
            tr("About", "درباره"),
            "info",
            listOf(
                page(s("cache"), { tr("Frees memory used by images and PDF pages", "حافظهٔ تصاویر و صفحه‌های PDF را آزاد می‌کند") }) {
                    media.clear()
                    board.sceneChanged()
                    toast(s("done"))
                },
                page(s("about"), { "VuraVision ${BuildConfig.VERSION_NAME}" }) { about() }
            )
        )
    )
    settingsScreen = SettingsScreen(this, categories) { prefs.edit().putString("settingsTab", it).apply() }.also { it.show(tab) }
}

internal fun MainActivity.about() {
    val c = column().apply { pad(20) }
    val gesture = AboutGesture()
    var close: () -> Unit = {}
    c.addView(
        ImageView(this).apply {
            setImageResource(R.drawable.vura_brand)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "VuraVision logo"
            setOnClickListener {
                if (gesture.click(android.os.SystemClock.uptimeMillis())) {
                    prefs.edit().putBoolean("engineering", true).apply()
                    close()
                    engineering()
                }
            }
        },
        LinearLayout.LayoutParams(-1, dp(150))
    )
    c.addView(label("VuraVision ${BuildConfig.VERSION_NAME}\nBeyond Vision\n\n${s("about_text")}", 16f))
    close = dialog(s("about"), c)::dismiss
}

internal fun MainActivity.fontSettings() {
    val content = column().apply { pad(16) }
    content.addView(infoTitle(tr("Information", "اطلاعات"), tr("Choose independent fonts for Persian and English. New text, sticky notes and smart conversion keep these fonts in the project and PDF exports.", "فونت فارسی و انگلیسی را جدا انتخاب کنید. متن تازه، یادداشت و تبدیل هوشمند این فونت‌ها را در پروژه و خروجی PDF حفظ می‌کنند.")))
    var fa = Fonts.persianId
    var en = Fonts.englishId
    val faChoice = button("") {}
    val enChoice = button("") {}
    val preview = label("نمونهٔ فارسی: یادگیری با ویوراویژن\nEnglish: Learning with VuraVision\n۱۲۳۴۵۶۷۸۹۰ · 1234567890", 21f)
    fun refreshPreview() {
        faChoice.text = tr("Persian", "فارسی") + ": " + Fonts.persian.first { it.id == fa }.let { tr(it.en, it.fa) }
        enChoice.text = tr("English", "انگلیسی") + ": " + Fonts.english.first { it.id == en }.let { tr(it.en, it.fa) }
        Fonts.bind(preview, fontFa = fa, fontEn = en)
    }
    fun choose(families: List<Fonts.Family>, persian: Boolean) {
        val list = column().apply { pad(8) }
        val picker = dialog(tr("Choose font", "انتخاب فونت"), ScrollView(this).apply { addView(list) })
        families.forEach { family ->
            val row = column().apply { pad(10) }
            row.addView(label(tr(family.en, family.fa), 15f, NAVY, true))
            val sample = label(if (persian)"یادگیری، خلاقیت و نوشتن ۱۲۳"else "Learning, creativity and writing 123", 22f)
            // Preview each row with that family's face, not the current global choice.
            Fonts.bind(sample, fontFa = if (persian)family.id else fa, fontEn = if (persian)en else family.id)
            row.addView(sample)
            row.setOnClickListener {
                if (persian)fa = family.id else en = family.id
                refreshPreview()
                picker.dismiss()
            }
            list.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
    }
    faChoice.setOnClickListener { choose(Fonts.persian, true) }
    enChoice.setOnClickListener { choose(Fonts.english, false) }
    content.addView(sectionTitle(tr("Fonts for the board and the app", "فونت تخته و برنامه")))
    content.addView(faChoice, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(6) })
    content.addView(enChoice, LinearLayout.LayoutParams(-1, dp(52)))
    content.addView(sectionTitle(tr("Preview", "پیش‌نمایش")))
    content.addView(preview)
    val existing = CheckBox(this).apply { text = tr("Also apply to editable text on the active page (Undo available)", "روی متن‌های قابل‌ویرایش صفحهٔ فعال هم اعمال شود (قابل بازگردانی)") }
    content.addView(existing)
    val dialog = dialog(s("fonts"), ScrollView(this).apply { addView(content) })
    refreshPreview()
    content.addView(
        button(s("apply"), true) {
            if (board.hasActiveInteraction)return@button
            Fonts.choose(this, fa, en)
            if (existing.isChecked) {
                val items = store.page.items.filter { it.kind in listOf("text", "sticky") && !it.locked && store.page.editable(it) }
                if (items.isNotEmpty()) {
                    store.editMetadata {
                        items.forEach { item ->
                            val w = item.w
                            val h = item.h
                            Fonts.stamp(item, true)
                            TextLayout.fit(item)
                            if (item.kind == "sticky") {
                                item.w = maxOf(w, item.w)
                                item.h = maxOf(h, item.h)
                            }
                        }
                    }
                }
            }
            Fonts.applyTree(window.decorView)
            whiteboard.sceneChanged()
            pdfPane?.refreshText()
            dialog.dismiss()
            toast(s("saved"))
        }
    )
}

internal fun MainActivity.historySettings() {
    val c = column().apply { pad(16) }
    val values = listOf(10, 25, 50, 100, 200)
    c.addView(infoTitle(s("history_settings"), tr("History steps on this device; per author in a room. Larger limits use more memory.", "تعداد مراحل بازگردانی این دستگاه؛ در اتاق برای هر کاربر. مقدار بیشتر حافظهٔ بیشتری مصرف می‌کند.")))
    val pick = Spinner(this).apply {
        adapter = OptionAdapter(this@historySettings, values.map { it.toString() })
        setSelection(values.indexOf(HistorySettings.load(this@historySettings)).coerceAtLeast(0))
    }
    c.addView(pick)
    val d = dialog(s("history_settings"), c)
    c.addView(
        button(s("apply"), true) {
            val value = values[pick.selectedItemPosition]
            prefs.edit().putInt("historyLimit", value).apply()
            projectStore.historyLimit = value
            board.store.historyLimit = value
            pdfPane?.setHistoryLimit(value)
            rooms?.setHistoryLimit(value)
            d.dismiss()
        }
    )
}
internal fun MainActivity.engineering() {
    val keys = listOf("shared_room", "history_settings", "report", "stress", "advanced_board", "secret_studio", "maboox", "voice_behavior")
    choices(s("engineering"), keys) {
        when (keys[it]) {
            "history_settings" -> historySettings()
            "shared_room" -> sharedRoom()
            "advanced_board" -> advancedBoard()
            "secret_studio" -> secretStudio()
            "maboox" -> maboox()
            "voice_behavior" -> VoiceSettingsUi(this, menuReturn.also { menuReturn = null }).advanced { stopVoice() }
            "report" -> {
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
            "stress" -> {
                if (board.hasActiveInteraction)return@choices
                confirm(tr("Add 300 test strokes? Undo removes them.", "۳۰۰ خط آزمایشی اضافه شود؟ با بازگردانی حذف می‌شوند.")) {
                    store.editMetadata {
                        repeat(300) { n ->
                            store.page.items.add(
                                Item(
                                    layerId = store.page.activeLayerId,
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
                                        .toMutableList()
                                )
                            )
                        }
                    }
                    board.fit()
                }
            }
        }
    }
}

internal fun MainActivity.advancedBoard() {
    if (board.hasActiveInteraction) {
        toast(tr("Finish the stroke first", "ابتدا رسم را تمام کنید"))
        return
    }
    val c = column().apply { pad(20) }
    c.addView(label(tr("Behind the board", "پشت پردهٔ تخته"), 22f, NAVY, true))
    fun toggle(en: String, fa: String, initial: Boolean, change: (Boolean) -> Unit) {
        c.addView(
            Switch(this).apply {
                text = tr(en, fa)
                isChecked = initial
                setOnCheckedChangeListener { _, v -> change(v) }
            }
        )
    }
    toggle("Hold to recognize geometry", "تشخیص هندسی با مکث", board.holdRecognitionEnabled) { v ->
        board.holdRecognitionEnabled = v
        prefs.edit().putBoolean("holdRecognition", v).apply()
    }
    val delayLabel = label("")
    c.addView(delayLabel)
    fun delayText() {
        delayLabel.text = tr("Hold time: ${board.holdDelayMillis} ms", "زمان مکث: ${board.holdDelayMillis} میلی‌ثانیه")
    }
    delayText()
    c.addView(
        SeekBar(this).apply {
            max = 20
            progress = ((board.holdDelayMillis - 500) / 100).toInt()
            contentDescription = tr("Shape hold time", "زمان مکث تشخیص شکل")
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(v: SeekBar?) {}
                override fun onStopTrackingTouch(v: SeekBar?) {}
                override fun onProgressChanged(v: SeekBar?, n: Int, user: Boolean) {
                    if (user) {
                        board.holdDelayMillis = 500L + n * 100L
                        prefs.edit().putLong("holdDelay", board.holdDelayMillis).apply()
                        delayText()
                    }
                }
            })
        }
    )
    val toleranceLabel = label("")
    c.addView(toleranceLabel)
    fun toleranceText() {
        toleranceLabel.text = tr("Stationary pen tolerance: ${board.holdTolerance.toInt()}", "حساسیت ثابت‌ماندن قلم: ${board.holdTolerance.toInt()}")
    }
    toleranceText()
    c.addView(
        SeekBar(this).apply {
            max = 10
            progress = board.holdTolerance.toInt() - 2
            contentDescription = tr("Stationary pen tolerance", "حساسیت ثابت‌ماندن قلم")
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(v: SeekBar?) {}
                override fun onStopTrackingTouch(v: SeekBar?) {}
                override fun onProgressChanged(v: SeekBar?, n: Int, user: Boolean) {
                    if (user) {
                        board.holdTolerance = (n + 2).toFloat()
                        prefs.edit().putFloat("holdTolerance", board.holdTolerance).apply()
                        toleranceText()
                    }
                }
            })
        }
    )
    toggle("Snap pen to ruler / compass", "اتصال قلم به خط‌کش و پرگار", board.guideSnapEnabled) { v ->
        board.guideSnapEnabled = v
        prefs.edit().putBoolean("guideSnap", v).apply()
    }
    toggle("Reject palm instead of erasing", "نادیده‌گرفتن کف دست به‌جای پاک‌کردن", !board.profile.palmErase) { v ->
        board.profile.palmErase = !v
        prefs.edit().putBoolean("palmErase", !v).apply()
    }
    c.addView(infoTitle(tr("Information", "اطلاعات"), tr("Lower hold time responds faster. A larger tolerance accepts more small hand movements. These settings are saved on this device.", "زمان مکث کمتر، تشخیص سریع‌تر می‌دهد. مقدار حساسیت بزرگ‌تر، لرزش بیشتری را می‌پذیرد. تنظیمات روی همین دستگاه ذخیره می‌شوند.")))
    c.addView(
        button(tr("Reset advanced settings", "بازنشانی تنظیمات پیشرفته")) {
            board.holdRecognitionEnabled = true
            board.holdDelayMillis = 1000L
            board.holdTolerance = 4f
            board.guideSnapEnabled = true
            prefs.edit().remove("holdRecognition").remove("holdDelay").remove("holdTolerance").remove("guideSnap").apply()
            toast(tr("Defaults restored; reopen this panel to refresh controls", "مقادیر پیش‌فرض برگشت؛ برای نمایش مقادیر تازه پنل را دوباره باز کنید"))
        }
    )
    dialog(tr("Behind the board", "پشت پردهٔ تخته"), ScrollView(this).apply { addView(c) })
}
internal fun MainActivity.calibrationSettings() {
    val c = column().apply { pad(16) }
    c.addView(sectionTitle(tr("Pen and touch", "قلم و لمس")))
    c.addView(
        row().apply {
            addView(button(tr("Pen tips and palm", "سر قلم و کف دست")) { calibrateTips() }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(8) })
            addView(button(s("touch_test")) { dialog(s("touch_test"), TouchDiagnostics(this@calibrationSettings, board.profile).apply { minimumHeight = dp(320) }) }, LinearLayout.LayoutParams(0, dp(52), 1f))
        }
    )
    c.addView(sectionTitle(tr("Size on screen", "اندازه روی صفحه")))
    c.addView(infoTitle(tr("Real centimetres", "سانتی‌متر واقعی"), tr("Measure the line below with a physical ruler, then enter its length in cm. Calibration belongs to this display. Without calibration, geometry uses board units (u).", "خط زیر را با خطکش واقعی اندازه بگیرید و طولش را به سانتی‌متر وارد کنید. کالیبراسیون مخصوص همین نمایشگر است. بدون آن، واحد هندسی u است.")))
    val line = object : View(this) {
        override fun onDraw(c: Canvas) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = NAVY
                strokeWidth = 3f
            }
            c.drawLine(dp(16).toFloat(), height / 2f, width - dp(16).toFloat(), height / 2f, p)
            listOf(dp(16).toFloat(), width - dp(16).toFloat()).forEach { x -> c.drawLine(x, 8f, x, height - 8f, p) }
        }
    }
    c.addView(line, LinearLayout.LayoutParams(-1, dp(40)))
    val length = field(hintValue = tr("Measured line length (cm)", "طول واقعی خط (سانتی‌متر)"))
    length.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
    c.addView(length)
    val d = dialog(s("calibration"), c)
    c.addView(
        row().apply {
            addView(
                button(tr("Use board units", "استفاده از واحد تخته")) {
                    prefs.edit().remove("pixelsPerCm").apply()
                    board.sceneChanged()
                    d.dismiss()
                },
                LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(8) }
            )
            addView(
                button(s("apply"), true) {
                    val n = length.text.toString().toFloatOrNull()
                    val px = line.width - dp(32)
                    if (n == null || !n.isFinite() || n <= 0 || px <= 0 || px / n !in 5f..1000f) {
                        length.error = tr("Enter a valid measured length", "طول واقعی معتبر وارد کنید")
                        return@button
                    }
                    prefs.edit().putFloat("pixelsPerCm", px / n).apply()
                    board.sceneChanged()
                    d.dismiss()
                },
                LinearLayout.LayoutParams(0, dp(52), 1f)
            )
        },
        LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
    )
}

/** Defaults for new pages; every choice applies at once (no Apply button), like the rest of Settings. */
internal fun MainActivity.defaultBackgroundSettings() {
    val c = column().apply { pad(16) }
    var color = prefs.getInt("defaultBackground", Color.WHITE)
    c.addView(sectionTitle(tr("Pattern", "الگو")))
    val current = (prefs.getString("defaultPattern", "dots") ?: "dots").let { if (it in listOf("white", "dark"))"plain" else it }
    val grid = PatternGrid(this, board.renderer, patternChoices(), current, 4, { color }) { prefs.edit().putString("defaultPattern", it).apply() }
    c.addView(grid)
    c.addView(sectionTitle(tr("Color", "رنگ")))
    palette(c, color, true) {
        color = it
        prefs.edit().putInt("defaultBackground", it).apply()
        grid.refresh()
    }
    c.addView(
        switchRow(
            tr("New page uses the current page's background", "صفحهٔ جدید از پس‌زمینهٔ صفحهٔ فعلی استفاده کند"),
            prefs.getBoolean("inheritBackground", false),
            tr("Only the color and pattern are copied. Existing pages are unchanged. The first page of a new lesson uses the defaults above.", "فقط رنگ و طرح کپی می‌شوند. صفحه‌های موجود تغییر نمی‌کنند. صفحهٔ اول درس جدید از پیش‌فرض بالا استفاده می‌کند.")
        ) { prefs.edit().putBoolean("inheritBackground", it).apply() }
    )
    dialog(s("page_background"), ScrollView(this).apply { addView(c) })
}
internal fun MainActivity.pieSettings() {
    val c = column().apply { pad(16) }
    c.addView(
        switchRow(
            tr("Enable five-finger pie menu", "فعال‌سازی منوی پنج‌انگشتی"),
            prefs.getBoolean("pieEnabled", false),
            tr("Double tap with five fingers, lift your hand, then tap a shortcut. Long-press to change a shortcut. Movement and palm contact do not trigger the menu.", "دو بار سریع با پنج انگشت ضربه بزنید، دست را بردارید و میان‌بر را بزنید. برای تغییر میان‌بر، آن را نگه دارید. حرکت و تماس کف دست منو را باز نمی‌کنند.")
        ) { v ->
            prefs.edit().putBoolean("pieEnabled", v).apply()
            board.gestures.reset()
            whiteboard.gestures.pieEnabled = v
            board.gestures.pieEnabled = v
        }
    )
    c.addView(
        switchRow(
            tr("Also open it by holding two fingers still", "با نگه داشتن دو انگشت هم باز شود"),
            prefs.getBoolean("pieHold", false),
            tr("Half a second without moving. Pinch and two-finger Undo keep working, because they move or tap.", "نیم ثانیه بدون حرکت. بزرگ‌نمایی و بازگردانی دوانگشتی کار می‌کنند، چون حرکت یا ضربه‌اند.")
        ) { v ->
            prefs.edit().putBoolean("pieHold", v).apply()
            board.gestures.reset()
            whiteboard.gestures.pieHold = v
            board.gestures.pieHold = v
        }
    )
    c.addView(sectionTitle(tr("Shortcuts", "میان‌برها")))
    val slots = column()
    c.addView(slots)
    fun refresh() {
        slots.removeAllViews()
        pieSlots().forEachIndexed { i, key ->
            val r = row()
            r.addView(icon(if (key.isBlank())"insert"else key, if (key.isBlank())tr("Assign shortcut", "تعیین میان‌بر")else s(key)) { assignPie(i) })
            r.addView(button("${i + 1} · " + if (key.isBlank())tr("Empty", "خالی")else s(key)) { assignPie(i) }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(8) })
            slots.addView(r, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        }
    }
    refreshPieSettings = ::refresh
    refresh()
    dialog(s("pie_settings"), ScrollView(this).apply { addView(c) }).setOnDismissListener { refreshPieSettings = null }
}
internal fun MainActivity.models() {
    val c = column().apply { pad(20) }
    c.addView(label(tr("Offline Latin OCR is already included", "تشخیص لاتین آفلاین همراه برنامه نصب است"), 18f, TEAL, true))
    c.addView(infoTitle(tr("Information", "اطلاعات"), tr("No model download is needed for clear Latin characters and simple arithmetic. Google digital-ink models below are optional for handwriting; Persian still needs its language model.", "برای حروف واضح لاتین و محاسبات ساده نیازی به دانلود نیست. مدل‌های زیر برای دست‌نویس گوگل‌اند؛ تشخیص دست‌نویس فارسی همچنان به مدل زبان نیاز دارد.")))
    c.addView(infoTitle(tr("Information", "اطلاعات"), tr("Model downloads need access to dl.google.com over HTTPS. If the connection is refused, try another network and check VPN, proxy or firewall settings, then Retry. Offline Latin OCR remains available.", "دانلود مدل به دسترسی HTTPS به dl.google.com نیاز دارد. اگر اتصال رد شد، شبکهٔ دیگری را امتحان و تنظیمات VPN، پراکسی یا فایروال را بررسی کنید؛ سپس دوباره تلاش کنید. تشخیص لاتین آفلاین در دسترس است.")))
    c.addView(
        button(tr("Network settings", "تنظیمات شبکه")) {
            try {
                startActivity(Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS))
            } catch (e: Exception) {
                error(e)
            }
        }
    )
    c.addView(
        button(tr("Use offline Latin OCR", "استفاده از تشخیص لاتین آفلاین")) {
            smartSource = "offline"
            prefs.edit().putString("smartSource", smartSource).apply()
            toast(s("done"))
        }
    )
    listOf("en-US" to "english", "fa" to "persian").forEach { (lang, key) ->
        val card = column().apply {
            pad(14)
            background = rounded(PAPER, dp(14).toFloat())
        }
        c.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(12), 0, dp(12)) })
        card.addView(label(s(key), 18f, NAVY, true))
        val state = label(s("check_status"), 14f, MUTED)
        card.addView(state)
        val progress = ProgressBar(this).apply { visibility = View.GONE }
        card.addView(progress, LinearLayout.LayoutParams(dp(32), dp(32)))
        val r = row()
        card.addView(scrollRow(r))
        var raw = ""
        var busy = false
        val install = button(s("install")) {}
        r.addView(install)
        fun failed(e: Exception) {
            busy = false
            progress.visibility = View.GONE
            raw = android.util.Log.getStackTraceString(e)
            lastError = raw
            state.text = recognition.failure(this, e)
            install.isEnabled = true
            install.text = s("retry")
        }
        fun update() {
            recognition.installed(lang, { ready ->
                if (!destroyed) {
                    state.text = s(if (ready)"installed"else "not_installed")
                    install.text = s(if (ready)"installed"else "install")
                    install.isEnabled = !ready && !busy
                }
            }, ::failed)
        }
        fun download() {
            if (busy)return
            busy = true
            install.isEnabled = false
            progress.visibility = View.VISIBLE
            state.text = s("downloading")
            recognition.download(lang, {
                if (!destroyed) {
                    busy = false
                    progress.visibility = View.GONE
                    update()
                }
            }, { if (!destroyed)failed(it) })
        }
        install.setOnClickListener { download() }
        r.addView(
            button(tr("Retry download", "تلاش مجدد")) {
                if (!busy) {
                    recognition.installed(lang, { ready -> if (ready)update()else download() }, ::failed)
                }
            }
        )
        r.addView(button(s("check_status")) { if (!busy)update() })
        r.addView(
            button(tr("Details", "جزئیات")) {
                val details = "Model: $lang\nSDK: digital-ink 19.0.0\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}\nAPI: ${Build.VERSION.SDK_INT}\nLocale: ${Locale.getDefault()}\n\n${raw.ifBlank{tr("No error recorded","خطایی ثبت نشده است")}}"
                val body = column().apply { pad(16) }
                body.addView(label(details, 12f).apply { setTextIsSelectable(true) })
                body.addView(
                    button(s("copy")) {
                        (getSystemService(Context.CLIPBOARD_SERVICE)as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("VuraVision diagnostics", details))
                        toast(s("done"))
                    }
                )
                dialog(tr("Download diagnostics", "گزارش دانلود"), ScrollView(this).apply { addView(body) })
            }
        )
        update()
    }
    dialog(s("models"), ScrollView(this).apply { addView(c) })
}

/**
 * Board style: page color for new pages plus a first pen that reads on it (white chalk on green or dark boards).
 * The current page changes too, but only while it is empty: existing navy ink would vanish on a dark board.
 */
internal fun MainActivity.applyBoardStyle(style: String) {
    val background = when (style) {
        "chalk" -> CHALKBOARD
        "dark" -> DARK_BOARD
        else -> Color.WHITE
    }
    val ink = if (style == "white")NAVY else 0xfff4f4f0.toInt()
    val second = if (style == "white")0xffdc2626.toInt() else 0xfffff176.toInt()
    prefs.edit().putInt("defaultBackground", background).putInt("dockPen0Color", ink).putInt("dockPen1Color", second).apply()
    if (store.page.visiblePaneCount == 1 && board.tool == "pen" && prefs.getInt("dockPen", 0) < 2) {
        board.penColor = if (prefs.getInt("dockPen", 0) == 0)ink else second
        savePens()
    }
    val page = store.page
    if (board === whiteboard && page.items.none { it.pane in page.visiblePaneIndices }) {
        store.editMetadata { page.visiblePaneIndices.forEach { page.panes[it].background = background } }
    } else {
        toast(tr("Applies to new pages; this page already has content", "برای صفحه‌های جدید اعمال می‌شود؛ این صفحه محتوا دارد"))
    }
    board.sceneChanged()
    refreshDock()
}

/**
 * One settings screen with categories (item 44). On/off and few-option settings apply in place;
 * only settings that need room open a page, which returns here with its back arrow.
 */
internal val MainActivity.panelSizes get() = listOf(Triple("tablet", tr("Tablet / small", "تبلت / کوچک"), .8f), Triple("65", "65″", 1f), Triple("75", "75″", 1.1f), Triple("86", "86″", 1.25f), Triple("98", "98–110″", 1.4f))
