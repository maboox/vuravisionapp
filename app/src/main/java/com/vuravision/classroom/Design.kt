package com.vuravision.classroom

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*

val NAVY = 0xff20194f.toInt()
val ORANGE = 0xfff8a529.toInt()
val PAPER = 0xfff3f2fa.toInt()
val MUTED = 0xff60727a.toInt()
val TEAL = 0xff167b79.toInt()

fun Context.s(key: String): String {
    val name = if (key == "new") "new_lesson" else key
    if (!name.matches(Regex("[a-z][a-z0-9_]*"))) return key
    val id = resources.getIdentifier(name, "string", packageName)
    return if (id == 0) key.replace('_', ' ') else getString(id)
}

fun Context.dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()

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
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(4), dp(4), dp(4), dp(4))
    }

fun Context.button(value: String, active: Boolean = false, action: () -> Unit) =
    Button(this).apply {
        text = value
        textSize = 13f
        isAllCaps = false
        minWidth = dp(52)
        minimumWidth = dp(52)
        minHeight = dp(46)
        minimumHeight = dp(46)
        setTextColor(if (active) Color.WHITE else NAVY)
        background =
            rounded(
                if (active) NAVY else Color.WHITE,
                dp(12).toFloat(),
                if (active) NAVY else 0xffdfe5e4.toInt(),
            )
        setPadding(dp(14), dp(8), dp(14), dp(8))
        setOnClickListener { action() }
        layoutParams =
            LinearLayout.LayoutParams(-2, dp(46)).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
    }

fun Context.field(value: String = "", hintValue: String = "") =
    EditText(this).apply {
        setText(value)
        hint = hintValue
        textSize = 17f
        setTextColor(NAVY)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = rounded(PAPER, dp(8).toFloat())
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(6), 0, dp(6)) }
    }

fun Context.scrollRow(children: LinearLayout) =
    HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(children)
    }

fun View.pad(n: Int) {
    setPadding(context.dp(n), context.dp(n), context.dp(n), context.dp(n))
}
