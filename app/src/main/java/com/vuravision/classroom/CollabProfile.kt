package com.vuravision.classroom

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.min

/**
 * The person behind a display during a shared session. It is stored only on this
 * device and is shown to others only while the devices are connected together.
 */
data class CollabProfile(
    var id: String = "",
    var name: String = "",
    var color: Int = PROFILE_COLORS[0],
    var emoji: String = "",
    /** Small square JPEG (Base64), or empty for an emoji/initial avatar. */
    var photo: String = "",
) {
    fun displayName(context: Context) = name.ifBlank { context.tr("Guest", "مهمان") }
    fun initials() = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1) }.uppercase()

    companion object {
        val PROFILE_COLORS = listOf(
            0xff4262e8.toInt(), 0xffe45756.toInt(), 0xff0f9d8a.toInt(), 0xffd97800.toInt(), 0xff865ac7.toInt(),
            0xffec76ab.toInt(), 0xff268bd2.toInt(), 0xff2e9e4f.toInt(), 0xffb8860b.toInt(), 0xff5d6b7a.toInt(),
        )
        val EMOJIS = listOf(
            "🦉", "🦊", "🐼", "🐯", "🦁", "🐸", "🐙", "🦄", "🐝", "🐬", "🐢", "🦋",
            "🌟", "🚀", "🎨", "📚", "🧪", "🔭", "🧩", "🎵", "⚽", "🌈", "🍀", "🌻",
        )
        private const val PREFS = "vura"

        fun load(context: Context): CollabProfile {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            var id = prefs.getString("collabDeviceId", null)
            if (id.isNullOrBlank()) { id = newId(); prefs.edit().putString("collabDeviceId", id).apply() }
            val photoFile = photoFile(context)
            val photo = if (photoFile.isFile && photoFile.length() in 1..65536) Base64.encodeToString(photoFile.readBytes(), Base64.NO_WRAP) else ""
            return CollabProfile(
                id = id,
                name = prefs.getString("collabName", null) ?: Build.MODEL.orEmpty().take(24),
                color = prefs.getInt("collabColor", PROFILE_COLORS[(id.hashCode() and 0x7fffffff) % PROFILE_COLORS.size]),
                emoji = prefs.getString("collabEmoji", null) ?: EMOJIS[(id.hashCode() and 0x7fffffff) % EMOJIS.size],
                photo = photo,
            )
        }

        fun save(context: Context, profile: CollabProfile) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("collabName", profile.name.trim().take(40))
                .putInt("collabColor", profile.color)
                .putString("collabEmoji", profile.emoji)
                .apply()
            val file = photoFile(context)
            if (profile.photo.isBlank()) file.delete()
            else runCatching { file.writeBytes(Base64.decode(profile.photo, Base64.NO_WRAP)) }
        }

        private fun photoFile(context: Context) = File(context.filesDir, "collab-profile.jpg")

        /** Centre-crops and compresses a picked picture into a small avatar. */
        fun photoFrom(bitmap: Bitmap): String {
            val side = min(bitmap.width, bitmap.height)
            val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
            val small = Bitmap.createScaledBitmap(square, 128, 128, true)
            val out = ByteArrayOutputStream()
            small.compress(Bitmap.CompressFormat.JPEG, 82, out)
            if (small !== square) small.recycle()
            if (square !== bitmap) square.recycle()
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }

        fun decodePhoto(photo: String): Bitmap? = if (photo.isBlank()) null else runCatching {
            val bytes = Base64.decode(photo, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
}

/** Round avatar: photo, emoji or initials over the person's color, with an optional status ring. */
class AvatarView(context: Context, profile: CollabProfile? = null) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private var photo: Bitmap? = null
    private var photoKey = ""
    var profile: CollabProfile? = profile
        set(value) { field = value; refreshPhoto(); contentDescription = value?.displayName(context); invalidate() }
    var ring = false
        set(value) { field = value; invalidate() }

    init { refreshPhoto(); contentDescription = profile?.displayName(context) }

    private fun refreshPhoto() {
        val key = profile?.photo.orEmpty()
        if (key == photoKey) return
        photoKey = key; photo = CollabProfile.decodePhoto(key)
    }

    override fun onDraw(canvas: Canvas) {
        val p = profile ?: return
        val size = min(width, height).toFloat()
        val cx = width / 2f; val cy = height / 2f
        val inset = if (ring) size * .1f else 0f
        val radius = size / 2f - inset
        if (ring) { paint.style = Paint.Style.STROKE; paint.strokeWidth = size * .06f; paint.color = p.color; canvas.drawCircle(cx, cy, size / 2f - paint.strokeWidth / 2, paint) }
        paint.style = Paint.Style.FILL; paint.color = p.color
        canvas.drawCircle(cx, cy, radius, paint)
        val bitmap = photo
        if (bitmap != null) {
            val path = Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) }
            canvas.save(); canvas.clipPath(path)
            canvas.drawBitmap(bitmap, null, RectF(cx - radius, cy - radius, cx + radius, cy + radius), paint)
            canvas.restore()
        } else {
            val glyph = p.emoji.ifBlank { p.initials().ifBlank { "?" } }
            text.textSize = radius * (if (p.emoji.isNotBlank()) 1.05f else .8f)
            text.color = Color.WHITE
            val metrics = text.fontMetrics
            canvas.drawText(glyph, cx, cy - (metrics.ascent + metrics.descent) / 2, text)
        }
        paint.style = Paint.Style.STROKE; paint.strokeWidth = size * .035f; paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, radius, paint)
    }
}

/** Avatars overlapping like a small stack, as seen in shared design tools. */
fun Context.avatarStack(profiles: List<CollabProfile>, size: Int = 32, max: Int = 4): LinearLayout = row().apply {
    layoutDirection = View.LAYOUT_DIRECTION_LTR
    profiles.take(max).forEachIndexed { i, profile ->
        addView(AvatarView(context, profile), LinearLayout.LayoutParams(dp(size), dp(size)).apply { if (i > 0) marginStart = -dp(size / 4) })
    }
    if (profiles.size > max) addView(label("+${profiles.size - max}", 13f, NAVY, true).apply { gravity = Gravity.CENTER })
}

object ProfileEditor {
    /** [pickPhoto] opens the system picker and later delivers a decoded bitmap. */
    fun show(activity: android.app.Activity, pickPhoto: ((Bitmap) -> Unit) -> Unit, back: (() -> Unit)?, saved: (CollabProfile) -> Unit) {
        val profile = CollabProfile.load(activity)
        val c = activity.column().apply { pad(16) }
        c.addView(activity.label(activity.tr("Your name, color and picture appear only to people connected with you.", "نام، رنگ و تصویر شما فقط برای کسانی که با شما وصل هستند نمایش داده می‌شود."), 14f, MUTED))
        val preview = AvatarView(activity, profile.copy())
        c.addView(preview, LinearLayout.LayoutParams(activity.dp(96), activity.dp(96)).apply { gravity = Gravity.CENTER_HORIZONTAL; setMargins(0, activity.dp(10), 0, activity.dp(10)) })
        val name = activity.field(profile.name, activity.tr("Name shown to others", "نامی که دیگران می‌بینند")).apply { setSingleLine(true); filters = arrayOf(android.text.InputFilter.LengthFilter(40)) }
        c.addView(name)
        fun refresh() { preview.profile = profile.copy(name = name.text.toString()) }
        name.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) { refresh() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        c.addView(activity.label(activity.tr("Color", "رنگ"), 16f, NAVY, true))
        c.addView(activity.colorPalette(profile.color, CollabProfile.PROFILE_COLORS + profile.color, {
            ColorPickerDialog.show(activity, profile.color) { profile.color = it; refresh() }
        }) { profile.color = it; refresh() })
        c.addView(activity.label(activity.tr("Emoji", "اموجی"), 16f, NAVY, true))
        val emojiCells = mutableListOf<TextView>()
        fun paintEmoji() { emojiCells.forEach { it.background = rounded(if (it.text == profile.emoji && profile.photo.isBlank()) PRIMARY_CONTAINER else SURFACE, activity.dp(12).toFloat(), OUTLINE) } }
        CollabProfile.EMOJIS.chunked(8).forEach { chunk ->
            val r = activity.row()
            chunk.forEach { emoji ->
                val cell = TextView(activity).apply {
                    text = emoji; textSize = 24f; gravity = Gravity.CENTER; contentDescription = emoji
                    setOnClickListener { profile.emoji = emoji; profile.photo = ""; paintEmoji(); refresh() }
                }
                emojiCells.add(cell); r.addView(cell, LinearLayout.LayoutParams(0, activity.dp(48), 1f).apply { setMargins(activity.dp(2), activity.dp(2), activity.dp(2), activity.dp(2)) })
            }
            c.addView(r)
        }
        paintEmoji()
        val photoRow = activity.row()
        photoRow.addView(activity.button(activity.tr("Choose photo", "انتخاب عکس")) {
            pickPhoto { bitmap -> profile.photo = CollabProfile.photoFrom(bitmap); paintEmoji(); refresh() }
        })
        photoRow.addView(activity.button(activity.tr("Remove photo", "حذف عکس")) { profile.photo = ""; paintEmoji(); refresh() })
        c.addView(activity.scrollRow(photoRow))
        val d = activity.backDialogBuilder(activity.tr("Profile", "پروفایل"), back)
            .setView(ScrollView(activity).apply { addView(c) })
            .setNegativeButton(activity.s("cancel"), null)
            .setPositiveButton(activity.s("apply"), null)
            .create()
        d.show(); Fonts.onShown(d)
        d.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = name.text.toString().trim()
            if (value.isBlank()) { name.error = activity.tr("Enter a name", "یک نام وارد کنید"); return@setOnClickListener }
            profile.name = value
            CollabProfile.save(activity, profile)
            d.dismiss(); saved(CollabProfile.load(activity))
        }
    }
}
