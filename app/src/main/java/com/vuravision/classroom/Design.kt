package com.vuravision.classroom

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText

// Semantic VuraVision tokens from the logo. Feature code should use these rather than ad-hoc colors.
// Calm chrome: NAVY marks the active tool and primary action; ORANGE marks selection and emphasis.
val NAVY = 0xff20194f.toInt()
val ORANGE = 0xffc76f00.toInt() // Text-safe brand orange; BRAND_ORANGE is for fills and the logo.
val BRAND_ORANGE = 0xfff8a529.toInt()
val PAPER = 0xfff6f5fa.toInt()
val MUTED = 0xff625e85.toInt()
val TEAL = 0xff0f716f.toInt()
val SURFACE = Color.WHITE
val SURFACE_VARIANT = 0xfff1f0f8.toInt()
val PRIMARY_CONTAINER = 0xffeceaf7.toInt()
val SECONDARY_CONTAINER = 0xffcdeeeB.toInt()
val OUTLINE = 0xffe4e2ef.toInt()
val OUTLINE_STRONG = 0xffb9b4cf.toInt()
val DANGER = 0xffb3261e.toInt()
val SUCCESS = 0xff287d58.toInt()
// Board colors (content, not chrome): the classic green chalkboard and a dark slate board.
val CHALKBOARD = 0xff20392f.toInt()
val DARK_BOARD = 0xff172a36.toInt()

fun Context.s(key: String): String {
    val name = if (key == "new") "new_lesson" else key
    if (!name.matches(Regex("[a-z][a-z0-9_]*"))) return key
    val id = resources.getIdentifier(name, "string", packageName)
    return if (id == 0) key.replace('_', ' ') else getString(id)
}

fun Context.uiScale()=getSharedPreferences("vura",Context.MODE_PRIVATE).getFloat("uiScale",1f).coerceIn(.75f,1.5f)
fun Context.dp(n: Int) = (n * resources.displayMetrics.density * uiScale() + .5f).toInt()

fun rounded(color: Int, radius: Float = 16f, stroke: Int = Color.TRANSPARENT) =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        setStroke(1, stroke)
    }

fun Context.column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

fun Context.row() =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

fun Context.label(value: String, size: Float = 16f, color: Int = NAVY, bold: Boolean = false) =
    TextView(this).apply {
        text = value
        textSize = size * uiScale()
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        Fonts.bind(this,bold)
        setPadding(dp(4), dp(4), dp(4), dp(4))
    }

private fun Context.materialContext() = ContextThemeWrapper(this, R.style.AppTheme)

private fun enabledColors(enabled: Int, disabled: Int) = ColorStateList(
    arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
    intArrayOf(disabled, enabled),
)

fun Context.button(value: String, active: Boolean = false, action: () -> Unit) =
    MaterialButton(materialContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
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
        val icon = when(value.firstOrNull()) {
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
        if(icon!=null){
            text=value.drop(1).trim()
            setIconResource(icon)
            iconTint = enabledColors(if(active)Color.WHITE else NAVY, MUTED)
            iconSize = dp(22)
            iconPadding = if(text.isEmpty())0 else dp(7)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            contentDescription=when(value.firstOrNull()){'↶'->s("undo");'↷'->s("redo");'‹'->s("previous");'›'->s("next");'☰'->s("tools");else->text}
        }
        minWidth = dp(52)
        minimumWidth = dp(52)
        minHeight = dp(48)
        minimumHeight = dp(48)
        setTextColor(enabledColors(if (active) Color.WHITE else NAVY, MUTED))
        setPadding(dp(16), dp(8), dp(16), dp(8))
        setOnClickListener { action() }
        Fonts.bind(this,bold=true)
        layoutParams =
            LinearLayout.LayoutParams(-2, dp(48)).apply { setMargins(dp(4), dp(3), dp(4), dp(3)) }
    }

fun Context.field(value: String = "", hintValue: String = "") =
    TextInputEditText(materialContext()).apply {
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
    stroke: Int = OUTLINE,
) = MaterialCardView(materialContext()).apply {
    radius = dp(cornerRadius).toFloat()
    cardElevation = dp(elevation).toFloat()
    setCardBackgroundColor(color)
    strokeColor = stroke
    strokeWidth = dp(1)
    addView(content)
}

fun Context.scrollRow(children: LinearLayout) =
    HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(children)
    }

fun View.pad(n: Int) {
    setPadding(context.dp(n), context.dp(n), context.dp(n), context.dp(n))
}

fun Context.tr(en:String,fa:String)=if(resources.configuration.locales[0].language=="fa")fa else en
