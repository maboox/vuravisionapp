package com.vuravision.classroom.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.vuravision.classroom.R

// Semantic VuraVision tokens from the logo. Feature code should use these rather than ad-hoc colors.
// Calm chrome: NAVY marks the active tool and primary action; ORANGE marks selection and emphasis.
var NAVY = 0xff20194f.toInt()
var ORANGE = 0xffc76f00.toInt() // Text-safe brand orange; BRAND_ORANGE is for fills and the logo.
val BRAND_ORANGE = 0xfff8a529.toInt()
var PAPER = 0xfff6f5fa.toInt()
var MUTED = 0xff625e85.toInt()
var TEAL = 0xff0f716f.toInt()
var SURFACE = Color.WHITE
var SURFACE_VARIANT = 0xfff1f0f8.toInt()
var PRIMARY_CONTAINER = 0xffeceaf7.toInt()
var SECONDARY_CONTAINER = 0xffcdeeeB.toInt()
var OUTLINE = 0xffe4e2ef.toInt()
var OUTLINE_STRONG = 0xffb9b4cf.toInt()
var DANGER = 0xffb3261e.toInt()
var SUCCESS = 0xff287d58.toInt()

/** Text and icons drawn on a NAVY fill (active tool, primary button): white in the light theme, deep navy in the dark one. */
var ON_NAVY = Color.WHITE

// Content colors: what is drawn ON the board (pages, rulers, element cards, tables, charts) keeps the light-theme
// values whatever the interface theme is, because the board itself stays a white (or chalk) page.
val INK_NAVY = 0xff20194f.toInt()
val INK_ORANGE = 0xffc76f00.toInt()
val INK_PAPER = 0xfff6f5fa.toInt()
val INK_MUTED = 0xff625e85.toInt()
val INK_TEAL = 0xff0f716f.toInt()
val INK_SURFACE = Color.WHITE
val INK_SURFACE_VARIANT = 0xfff1f0f8.toInt()
val INK_OUTLINE = 0xffe4e2ef.toInt()
val INK_OUTLINE_STRONG = 0xffb9b4cf.toInt()

/**
 * Interface theme (item 24): light (default) or dark chrome. Only the interface changes; the board keeps its own
 * style (white, chalk or dark board). Applied once before any view is made; switching restarts the screen.
 */
object UiTheme {
    var dark = false
        private set
    fun load(c: Context) {
        dark = c.getSharedPreferences("vura", Context.MODE_PRIVATE).getString("uiTheme", "light") == "dark"
        if (dark) {
            NAVY = 0xffe4e0ff.toInt()
            ON_NAVY = 0xff17142e.toInt()
            ORANGE = 0xfff8a529.toInt()
            PAPER = 0xff121120.toInt()
            MUTED = 0xffaca7cf.toInt()
            TEAL = 0xff5cc8c2.toInt()
            SURFACE = 0xff1e1c30.toInt()
            SURFACE_VARIANT = 0xff28263d.toInt()
            PRIMARY_CONTAINER = 0xff302c52.toInt()
            SECONDARY_CONTAINER = 0xff17423f.toInt()
            OUTLINE = 0xff37344f.toInt()
            OUTLINE_STRONG = 0xff57537a.toInt()
            DANGER = 0xffff8a80.toInt()
            SUCCESS = 0xff74d39c.toInt()
        } else {
            NAVY = INK_NAVY
            ON_NAVY = Color.WHITE
            ORANGE = INK_ORANGE
            PAPER = INK_PAPER
            MUTED = INK_MUTED
            TEAL = INK_TEAL
            SURFACE = INK_SURFACE
            SURFACE_VARIANT = INK_SURFACE_VARIANT
            PRIMARY_CONTAINER = 0xffeceaf7.toInt()
            SECONDARY_CONTAINER = 0xffcdeeeB.toInt()
            OUTLINE = INK_OUTLINE
            OUTLINE_STRONG = INK_OUTLINE_STRONG
            DANGER = 0xffb3261e.toInt()
            SUCCESS = 0xff287d58.toInt()
        }
    }
    fun style() = if (dark)R.style.AppThemeDark else R.style.AppTheme
}

// Board colors (content, not chrome): the classic green chalkboard and a dark slate board.
val CHALKBOARD = 0xff20392f.toInt()
val DARK_BOARD = 0xff172a36.toInt()

// Laser pen: a saturated rose that stands out on white, green and dark boards alike.
val LASER_COLOR = 0xffff2d6f.toInt()

/** Connector arrows: a bright teal, readable on white, chalk and dark boards alike. */
val CONNECTOR = 0xff218c91.toInt()
const val LASER_HOLD = 1000L
const val LASER_GONE = 2000L

fun Context.s(key: String): String {
    val name = if (key == "new") "new_lesson" else key
    if (!name.matches(Regex("[a-z][a-z0-9_]*"))) return key
    val id = resources.getIdentifier(name, "string", packageName)
    return if (id == 0) key.replace('_', ' ') else getString(id)
}

/** The panel size sets the base scale; "Interface size" makes everything a step smaller or larger on top of it. */
fun Context.uiScale() = getSharedPreferences("vura", Context.MODE_PRIVATE).let { it.getFloat("uiScale", 1f).coerceIn(.75f, 1.5f) * it.getFloat("uiZoom", 1f).coerceIn(.8f, 1.35f) }

/** Item 32: "Reduce motion" in Settings, or animations turned off for the whole device. */
object Motion {
    @Volatile var reduced = false
    fun load(c: Context) {
        reduced = c.getSharedPreferences("vura", Context.MODE_PRIVATE).getBoolean("reduceMotion", false) ||
            android.provider.Settings.Global.getFloat(c.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    fun ms(duration: Long) = if (reduced)0L else duration
}
fun Context.dp(n: Int) = (n * resources.displayMetrics.density * uiScale() + .5f).toInt()

fun rounded(color: Int, radius: Float = 16f, stroke: Int = Color.TRANSPARENT) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radius
    setStroke(1, stroke)
}

fun Context.column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

fun Context.row() = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

fun Context.label(value: String, size: Float = 16f, color: Int = NAVY, bold: Boolean = false) = TextView(this).apply {
    text = value
    // Item 28: nothing smaller than 14sp; small captions were hard to read from the back of a classroom.
    textSize = maxOf(size, 14f) * uiScale()
    setTextColor(color)
    if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    Fonts.bind(this, bold)
    setPadding(dp(4), dp(4), dp(4), dp(4))
}

private fun Context.materialContext() = ContextThemeWrapper(this, UiTheme.style())

private fun enabledColors(enabled: Int, disabled: Int) = ColorStateList(
    arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
    intArrayOf(disabled, enabled)
)

fun Context.button(value: String, active: Boolean = false, action: () -> Unit) = MaterialButton(materialContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
    text = value
    textSize = 14f * uiScale()
    isAllCaps = false
    letterSpacing = 0f
    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    cornerRadius = dp(14)
    insetTop = 0
    insetBottom = 0
    strokeWidth = dp(1)
    strokeColor = enabledColors(if (active) NAVY else OUTLINE, OUTLINE)
    backgroundTintList = enabledColors(if (active) NAVY else SURFACE, SURFACE_VARIANT)
    rippleColor = ColorStateList.valueOf(if (active) 0x33ffffff else 0x1f2d2463)
    elevation = dp(if (active) 3 else 0).toFloat()
    val icon = when (value.firstOrNull()) {
        '✎' -> R.drawable.ph_highlighter
        '▱' -> R.drawable.ph_pen
        '↖' -> R.drawable.ph_cursor
        '✥' -> R.drawable.ph_move
        '△' -> R.drawable.ph_triangle
        '＋' -> R.drawable.ph_plus
        '✦' -> R.drawable.ph_sparkle
        '◷' -> R.drawable.ph_timer
        '☰' -> R.drawable.ph_menu
        '↶' -> R.drawable.ph_undo
        '↷' -> R.drawable.ph_redo
        '‹' -> R.drawable.ph_caret_left
        '›' -> R.drawable.ph_caret_right
        '⋯' -> R.drawable.ph_more
        '⇧' -> R.drawable.ph_share
        else -> null
    }
    if (icon != null) {
        text = value.drop(1).trim()
        setIconResource(icon)
        iconTint = enabledColors(if (active)ON_NAVY else NAVY, MUTED)
        iconSize = dp(22)
        iconPadding = if (text.isEmpty())0 else dp(7)
        iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        contentDescription = when (value.firstOrNull()) {
            '↶' -> s("undo")
            '↷' -> s("redo")
            '‹' -> s("previous")
            '›' -> s("next")
            '☰' -> s("tools")
            else -> text
        }
    }
    minWidth = dp(52)
    minimumWidth = dp(52)
    minHeight = dp(48)
    minimumHeight = dp(48)
    setTextColor(enabledColors(if (active) ON_NAVY else NAVY, MUTED))
    setPadding(dp(16), dp(8), dp(16), dp(8))
    setOnClickListener { action() }
    Fonts.bind(this, bold = true)
    layoutParams =
        LinearLayout.LayoutParams(-2, dp(48)).apply { setMargins(dp(4), dp(3), dp(4), dp(3)) }
}

fun Context.field(value: String = "", hintValue: String = "") = TextInputEditText(materialContext()).apply {
    setText(value)
    hint = hintValue
    textSize = 17f * uiScale()
    setTextColor(NAVY)
    setHintTextColor(MUTED)
    minHeight = dp(56)
    setPadding(dp(16), dp(12), dp(16), dp(12))
    background = rounded(SURFACE, dp(12).toFloat(), OUTLINE_STRONG)
    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(8), 0, dp(8)) }
    Fonts.bind(this)
}

fun Context.card(
    content: View,
    color: Int = SURFACE,
    cornerRadius: Int = 20,
    elevation: Int = 1,
    stroke: Int = OUTLINE
) = MaterialCardView(materialContext()).apply {
    radius = dp(cornerRadius).toFloat()
    cardElevation = dp(elevation).toFloat()
    setCardBackgroundColor(color)
    strokeColor = stroke
    strokeWidth = dp(1)
    addView(content)
}

fun Context.scrollRow(children: LinearLayout) = HorizontalScrollView(this).apply {
    isHorizontalScrollBarEnabled = false
    addView(children)
}

fun View.pad(n: Int) {
    setPadding(context.dp(n), context.dp(n), context.dp(n), context.dp(n))
}

fun Context.tr(en: String, fa: String) = if (resources.configuration.locales[0].language == "fa")fa else en
