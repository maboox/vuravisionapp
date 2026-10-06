package com.vuravision.classroom

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.*
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import kotlin.math.max
import kotlin.math.min

/** Draws other people on this board: live strokes and cursors, their selections, and fading halos. */
class CollabOverlay(private val session: CollabSession, private val board: Board) {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; isFakeBoldText = true }

    fun draw(c: Canvas, pane: Int, zoom: Float) {
        val page = board.store.page
        val now = SystemClock.uptimeMillis()
        val unit = 1f / zoom
        board.foreignLocks.forEach { (id, color) ->
            val item = page.items.firstOrNull { it.id == id && it.pane == pane } ?: return@forEach
            val b = itemBounds(item).apply { inset(-6 * unit, -6 * unit) }
            stroke.color = color; stroke.alpha = 255; stroke.strokeWidth = 2.5f * unit; stroke.pathEffect = null
            c.drawRoundRect(b, 6 * unit, 6 * unit, stroke)
            session.ownerOf(id)?.let { chip(c, it, b.left, b.top - 4 * unit, unit, 255, above = true) }
        }
        val independent = session.isHost || session.role == CollabSession.COLLABORATE
        session.everyone().forEach { peer ->
            val color = peer.profile.color
            val viewAge = now - peer.viewAt
            if (independent && (peer.role == CollabSession.COLLABORATE || peer.isHost) && peer.viewPage == page.id && peer.viewPane == pane && viewAge < CollabSession.HALO_MILLIS && !(peer.touching && now - peer.heardAt < 1500)) {
                peer.view?.let { v ->
                    val alpha = fade(viewAge, 110)
                    stroke.color = color; stroke.alpha = alpha; stroke.strokeWidth = 1.5f * unit
                    stroke.pathEffect = DashPathEffect(floatArrayOf(10 * unit, 7 * unit), 0f)
                    c.drawRoundRect(v, 10 * unit, 10 * unit, stroke); stroke.pathEffect = null
                    chip(c, peer.profile, v.left + 8 * unit, v.top + 8 * unit, unit, alpha, above = false)
                }
            }
            val age = now - peer.activityAt
            if (peer.activityPage == page.id && peer.activityPane == pane && age < CollabSession.HALO_MILLIS) {
                peer.activity?.let { r ->
                    val b = RectF(r).apply { inset(-14 * unit, -14 * unit) }
                    val alpha = fade(age, 220)
                    fill.color = color; fill.alpha = alpha / 9
                    c.drawRoundRect(b, 14 * unit, 14 * unit, fill)
                    stroke.color = color; stroke.alpha = alpha; stroke.strokeWidth = 2f * unit
                    c.drawRoundRect(b, 14 * unit, 14 * unit, stroke)
                    chip(c, peer.profile, b.left, b.top - 4 * unit, unit, max(alpha, 60), above = true)
                }
            }
            val fresh = now - peer.heardAt < 1500 && peer.pageId == page.id
            if (fresh) peer.live.filter { it.pane == pane }.forEach { board.renderer.draw(c, it) }
            val cursor = peer.cursor
            if (fresh && peer.touching && peer.cursorPane == pane && cursor != null) {
                fill.color = color; fill.alpha = 255
                c.drawCircle(cursor.x, cursor.y, 7 * unit, fill)
                stroke.color = Color.WHITE; stroke.alpha = 255; stroke.strokeWidth = 2 * unit
                c.drawCircle(cursor.x, cursor.y, 7 * unit, stroke)
                chip(c, peer.profile, cursor.x + 10 * unit, cursor.y + 10 * unit, unit, 255, above = false)
            }
        }
    }

    /** Fades in a curve so the halo stays clear for a while, then softly disappears over a minute. */
    private fun fade(age: Long, peak: Int): Int {
        val t = (age.toFloat() / CollabSession.HALO_MILLIS).coerceIn(0f, 1f)
        return (peak * (1f - t * t)).toInt().coerceIn(0, 255)
    }

    private fun chip(c: Canvas, profile: CollabProfile, x: Float, y: Float, unit: Float, alpha: Int, above: Boolean) {
        val label = (if (profile.emoji.isNotBlank()) profile.emoji + " " else "") + profile.name.ifBlank { "?" }.take(24)
        text.textSize = 12 * unit; text.alpha = alpha
        val w = text.measureText(label) + 12 * unit; val h = 20 * unit
        val top = if (above) y - h else y
        fill.color = profile.color; fill.alpha = alpha
        c.drawRoundRect(RectF(x, top, x + w, top + h), 10 * unit, 10 * unit, fill)
        c.drawText(label, x + 6 * unit, top + h / 2 - (text.ascent() + text.descent()) / 2, text)
    }
}

/**
 * Screens for connecting displays: a hub (profile, create, join), the room owner's
 * participant list, the join screen and a guest's session panel.
 */
class CollabUi(
    private val a: Activity,
    private val session: () -> CollabSession?,
    private val startHost: () -> Unit,
    private val join: (CollabInvite) -> Unit,
    private val pickPhoto: ((Bitmap) -> Unit) -> Unit,
    private val scanQr: ((String) -> Unit) -> Unit,
) {
    private var openDialog: AlertDialog? = null
    private var rebuild: (() -> Unit)? = null
    private var discovery: CollabDiscovery? = null

    private fun show(title: String, back: (() -> Unit)?, build: (LinearLayout) -> Unit, configure: (androidx.appcompat.app.AlertDialog.Builder) -> Unit = {}): AlertDialog {
        openDialog?.dismiss(); discovery?.close(); discovery = null
        val content = a.column().apply { pad(16) }
        val refresh = { content.removeAllViews(); build(content); Fonts.applyTree(content) }
        refresh()
        val builder = a.backDialogBuilder(title, back).setView(ScrollView(a).apply { addView(content) }).setNegativeButton(a.s("close"), null)
        configure(builder)
        val d = builder.create()
        // Dismiss callbacks arrive asynchronously; only the screen still on top may clean up.
        d.setOnDismissListener { if (openDialog === d) { openDialog = null; rebuild = null; discovery?.close(); discovery = null } }
        openDialog = d; rebuild = refresh
        d.show(); Fonts.onShown(d)
        return d
    }

    /** Keeps an open session screen current as people join, leave or change role. */
    fun refresh() { if (openDialog?.isShowing == true) rebuild?.invoke() }

    fun open(back: (() -> Unit)?) {
        val s = session()
        when {
            s == null || !s.isActive -> hub(back)
            s.isHost -> room(back)
            else -> guest(back)
        }
    }

    private fun card(parent: LinearLayout, body: View) {
        parent.addView(a.card(body.apply { pad(14) }, SURFACE, 18, 0), LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, a.dp(6), 0, a.dp(6)) })
    }

    private fun profileRow(parent: LinearLayout, edit: Boolean) {
        val me = CollabProfile.load(a)
        val r = a.row()
        r.addView(AvatarView(a, me), LinearLayout.LayoutParams(a.dp(56), a.dp(56)).apply { marginEnd = a.dp(12) })
        val text = a.column()
        text.addView(a.label(me.displayName(a), 18f, NAVY, true))
        text.addView(a.label(a.tr("Shown only to people in your room", "فقط برای افراد داخل اتاق نمایش داده می‌شود"), 13f, MUTED))
        r.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        if (edit) r.addView(a.button(a.tr("Edit", "ویرایش")) { editProfile() })
        card(parent, r)
    }

    private fun editProfile() {
        val reopen = { open(null) }
        openDialog?.dismiss()
        ProfileEditor.show(a, pickPhoto, reopen) { profile -> session()?.updateProfile(profile); reopen() }
    }

    private fun option(parent: LinearLayout, icon: String, title: String, detail: String, action: () -> Unit) {
        val r = a.row()
        r.addView(ImageView(a).apply { setImageDrawable(IconCatalog.drawable(a, icon)); background = rounded(PRIMARY_CONTAINER, a.dp(14).toFloat()); setPadding(a.dp(12), a.dp(12), a.dp(12), a.dp(12)) }, LinearLayout.LayoutParams(a.dp(52), a.dp(52)).apply { marginEnd = a.dp(14) })
        val text = a.column()
        text.addView(a.label(title, 17f, NAVY, true)); text.addView(a.label(detail, 13f, MUTED))
        r.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        r.isClickable = true; r.isFocusable = true; r.setOnClickListener { action() }
        r.contentDescription = title
        card(parent, r)
    }

    private fun hub(back: (() -> Unit)?) {
        show(a.tr("Connect displays", "اتصال نمایشگرها"), back, { c ->
            c.addView(a.label(a.tr("Preview · Work together with another VuraVision tablet or panel on the same Wi-Fi network or hotspot.", "پیش‌نمایش · با یک تبلت یا نمایشگر دیگر که VuraVision دارد و روی همان Wi-Fi یا هات‌اسپات است، با هم کار کنید."), 14f, MUTED))
            profileRow(c, true)
            option(c, "collaboration", a.tr("Create a room", "ساخت اتاق"), a.tr("Others join this board. You choose who can view, control it or collaborate.", "دیگران به این تخته می‌پیوندند. شما تعیین می‌کنید چه کسی ببیند، کنترل کند یا همکاری کند.")) { openDialog?.dismiss(); startHost() }
            option(c, "collab_discover", a.tr("Join a room", "پیوستن به اتاق"), a.tr("Pick a nearby room or scan its QR code. Your own lesson is kept safe.", "اتاق نزدیک را انتخاب کنید یا QR آن را اسکن کنید. درس خودتان محفوظ می‌ماند.")) { joinScreen { open(back) } }
        })
    }

    private fun roleButton(title: String, icon: String, active: Boolean, action: () -> Unit): MaterialButton = a.button(title, active, action).apply {
        setIconResource(IconCatalog.resource(icon)); iconSize = a.dp(18); iconPadding = a.dp(6)
        iconTint = ColorStateList.valueOf(if (active) Color.WHITE else NAVY)
        iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        textSize = 13f * a.uiScale(); setPadding(a.dp(10), 0, a.dp(12), 0)
        contentDescription = title
    }

    private fun roleName(role: String) = when (role) {
        CollabSession.VIEW -> a.tr("View", "مشاهده")
        CollabSession.CONTROL -> a.tr("Control", "کنترل")
        CollabSession.COLLABORATE -> a.tr("Collaborate", "همکاری")
        else -> a.tr("Room owner", "صاحب اتاق")
    }

    private fun roleIcon(role: String) = when (role) { CollabSession.VIEW -> "collab_view"; CollabSession.CONTROL -> "collab_control"; else -> "collab_collaborate" }

    private fun roleDetail(role: String) = when (role) {
        CollabSession.VIEW -> a.tr("Sees the room owner's board, page and view. Cannot change anything.", "تخته، صفحه و نمای صاحب اتاق را می‌بیند و چیزی را تغییر نمی‌دهد.")
        CollabSession.CONTROL -> a.tr("Works on the owner's board as if standing at it: same page and view, one shared Undo.", "روی تختهٔ صاحب اتاق کار می‌کند، انگار جلوی همان نمایشگر است: همان صفحه و نما و یک Undo مشترک.")
        else -> a.tr("Works independently with own tools, page, view and Undo. Others see who is drawing where.", "مستقل با ابزار، صفحه، نما و Undo خودش کار می‌کند. بقیه می‌بینند چه کسی کجا می‌کشد.")
    }

    private fun pageText(s: CollabSession, peer: CollabPeer): String {
        val page = s.pageIndex(peer.pageId)
        return if (page >= 0) a.tr("Page ${page + 1}", "صفحهٔ ${page + 1}") else ""
    }

    private fun personRow(parent: LinearLayout, s: CollabSession, peer: CollabPeer?, profile: CollabProfile, status: String, controls: Boolean) {
        val r = a.row().apply { setPadding(0, a.dp(6), 0, a.dp(6)) }
        r.addView(AvatarView(a, profile).apply { ring = peer?.touching == true }, LinearLayout.LayoutParams(a.dp(44), a.dp(44)).apply { marginEnd = a.dp(12) })
        val text = a.column()
        text.addView(a.label(profile.displayName(a), 16f, NAVY, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        text.addView(a.label(status, 13f, MUTED))
        r.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        if (controls && peer != null) r.addView(WorkspaceIcon(a, "close", a.tr("Remove from room", "خروج از اتاق")) { s.remove(peer.id) })
        parent.addView(r)
        if (controls && peer != null) {
            val roles = a.row()
            CollabSession.ROLES.forEach { role -> roles.addView(roleButton(roleName(role), roleIcon(role), peer.role == role) { s.setRole(peer.id, role) }) }
            parent.addView(a.scrollRow(roles))
        }
    }

    private fun room(back: (() -> Unit)?) {
        val s = session() ?: return
        s.refreshInvite()
        val qrUri = s.invite?.uri()
        val qr = qrUri?.let { runCatching { Sharing.qr(it) }.getOrNull() }
        show(a.tr("Room", "اتاق") + " · " + s.roomName, back, { c ->
            val s2 = session()?.takeIf { it.isActive } ?: run { c.addView(a.label(a.tr("The room is closed.", "اتاق بسته شد."), 15f, MUTED)); return@show }
            val top = a.row()
            if (qr != null) top.addView(ImageView(a).apply { setImageBitmap(qr); contentDescription = a.tr("Room QR code", "کد QR اتاق") }, LinearLayout.LayoutParams(a.dp(150), a.dp(150)).apply { marginEnd = a.dp(14) })
            val info = a.column()
            info.addView(a.label(a.tr("Room code", "کد اتاق"), 13f, MUTED))
            info.addView(a.label(s2.code.chunked(3).joinToString(" "), 30f, NAVY, true).apply { textDirection = View.TEXT_DIRECTION_LTR; layoutDirection = View.LAYOUT_DIRECTION_LTR; setTextIsSelectable(true) })
            val addresses = s2.invite?.hosts.orEmpty().map { "$it:${s2.invite?.port}" }
            info.addView(a.label(if (addresses.isEmpty()) a.tr("No Wi-Fi or hotspot network found", "شبکهٔ Wi-Fi یا هات‌اسپات پیدا نشد") else addresses.joinToString("\n"), 13f, if (addresses.isEmpty()) DANGER else MUTED).apply { textDirection = View.TEXT_DIRECTION_LTR; setTextIsSelectable(true) })
            info.addView(a.label(a.tr("On the other device: Connect displays → Join a room, then pick this room or scan the code.", "در دستگاه دیگر: اتصال نمایشگرها ← پیوستن به اتاق؛ سپس این اتاق را انتخاب یا کد را اسکن کنید."), 13f, MUTED))
            top.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            card(c, top)
            c.addView(a.label(a.tr("New people join as", "افراد تازه با این نقش وارد می‌شوند"), 15f, NAVY, true))
            val defaults = a.row()
            CollabSession.ROLES.forEach { role -> defaults.addView(roleButton(roleName(role), roleIcon(role), s2.defaultRole == role) { s2.defaultRole = role; refresh() }) }
            c.addView(a.scrollRow(defaults))
            c.addView(a.label(roleDetail(s2.defaultRole), 13f, MUTED))
            if (s2.knocks.isNotEmpty()) {
                c.addView(a.label(a.tr("Asking to join", "درخواست ورود"), 15f, NAVY, true).apply { setPadding(a.dp(4), a.dp(14), a.dp(4), a.dp(4)) })
                s2.knocks.toList().forEach { knock ->
                    val r = a.row()
                    r.addView(AvatarView(a, knock.profile), LinearLayout.LayoutParams(a.dp(44), a.dp(44)).apply { marginEnd = a.dp(12) })
                    r.addView(a.label(knock.profile.displayName(a), 16f, NAVY, true), LinearLayout.LayoutParams(0, -2, 1f))
                    r.addView(a.button(a.tr("Decline", "رد")) { s2.answer(knock, false) })
                    r.addView(a.button(a.tr("Let in", "ورود"), true) { s2.answer(knock, true) })
                    card(c, r)
                }
            }
            c.addView(a.label(a.tr("In this room", "داخل اتاق") + " · ${s2.peers.size + 1}", 15f, NAVY, true).apply { setPadding(a.dp(4), a.dp(14), a.dp(4), a.dp(4)) })
            val people = a.column()
            personRow(people, s2, null, s2.me, a.tr("You · room owner", "شما · صاحب اتاق"), false)
            if (s2.peers.isEmpty()) people.addView(a.label(a.tr("Nobody has joined yet.", "هنوز کسی وارد نشده است."), 14f, MUTED))
            s2.everyone().forEach { peer ->
                val doing = if (peer.touching && SystemClock.uptimeMillis() - peer.heardAt < 1500) a.tr("drawing now", "در حال کشیدن") else pageText(s2, peer)
                personRow(people, s2, peer, peer.profile, listOf(roleName(peer.role), doing).filter { it.isNotBlank() }.joinToString(" · "), true)
            }
            card(c, people)
            c.addView(a.button(a.tr("End room for everyone", "بستن اتاق برای همه")) {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(a).setMessage(a.tr("Close the room? Everyone will be disconnected. Your board stays as it is.", "اتاق بسته شود؟ اتصال همه قطع می‌شود و تختهٔ شما همین‌طور می‌ماند."))
                    .setNegativeButton(a.s("cancel"), null).setPositiveButton(a.tr("End room", "بستن اتاق")) { _, _ -> openDialog?.dismiss(); session()?.end() }.show()
            }.apply { setTextColor(DANGER) })
        }, { it.setNeutralButton(a.tr("Profile", "پروفایل")) { _, _ -> editProfile() } })
    }

    private fun guest(back: (() -> Unit)?) {
        val s = session() ?: return
        show(a.tr("Room", "اتاق") + (if (s.roomName.isNotBlank()) " · " + s.roomName else ""), back, { c ->
            val s2 = session()?.takeIf { it.isActive } ?: run { c.addView(a.label(a.tr("You are no longer in the room.", "دیگر داخل اتاق نیستید."), 15f, MUTED)); return@show }
            when (s2.status) {
                "connecting", "waiting" -> {
                    val r = a.row()
                    r.addView(ProgressBar(a), LinearLayout.LayoutParams(a.dp(36), a.dp(36)).apply { marginEnd = a.dp(12) })
                    r.addView(a.label(if (s2.status == "waiting") a.tr("Waiting for the room owner to let you in…", "منتظر اجازهٔ صاحب اتاق…") else a.tr("Connecting…", "در حال اتصال…"), 16f, NAVY, true), LinearLayout.LayoutParams(0, -2, 1f))
                    card(c, r)
                    c.addView(a.button(a.s("cancel")) { openDialog?.dismiss(); session()?.end(false) })
                    return@show
                }
            }
            val roleCard = a.row()
            roleCard.addView(ImageView(a).apply { setImageDrawable(IconCatalog.drawable(a, roleIcon(s2.role))); background = rounded(PRIMARY_CONTAINER, a.dp(14).toFloat()); setPadding(a.dp(12), a.dp(12), a.dp(12), a.dp(12)) }, LinearLayout.LayoutParams(a.dp(52), a.dp(52)).apply { marginEnd = a.dp(14) })
            val roleText = a.column()
            roleText.addView(a.label(a.tr("Your role: ", "نقش شما: ") + roleName(s2.role), 17f, NAVY, true))
            roleText.addView(a.label(roleDetail(s2.role), 13f, MUTED))
            roleCard.addView(roleText, LinearLayout.LayoutParams(0, -2, 1f))
            card(c, roleCard)
            c.addView(a.label(a.tr("In this room", "داخل اتاق") + " · ${s2.peers.size + 1}", 15f, NAVY, true).apply { setPadding(a.dp(4), a.dp(14), a.dp(4), a.dp(4)) })
            val people = a.column()
            personRow(people, s2, null, s2.me, a.tr("You", "شما"), false)
            s2.everyone().sortedByDescending { it.isHost }.forEach { peer ->
                val doing = if (peer.touching && SystemClock.uptimeMillis() - peer.heardAt < 1500) a.tr("drawing now", "در حال کشیدن") else pageText(s2, peer)
                personRow(people, s2, peer, peer.profile, listOf(roleName(peer.role), doing).filter { it.isNotBlank() }.joinToString(" · "), false)
            }
            card(c, people)
            val keep = CheckBox(a).apply { text = a.tr("Keep a copy of the shared board in Recent files", "یک نسخه از تختهٔ مشترک در فایل‌های اخیر بماند"); isChecked = true }
            c.addView(keep)
            c.addView(a.button(a.tr("Leave room", "خروج از اتاق")) { openDialog?.dismiss(); session()?.end(keep.isChecked) }.apply { setTextColor(DANGER) })
        }, { it.setNeutralButton(a.tr("Profile", "پروفایل")) { _, _ -> editProfile() } })
    }

    private fun joinScreen(back: (() -> Unit)?) {
        var rooms = emptyList<CollabRoom>()
        val finder = CollabDiscovery(a)
        show(a.tr("Join a room", "پیوستن به اتاق"), back, { c ->
            c.addView(a.label(a.tr("Rooms on this network", "اتاق‌های این شبکه"), 15f, NAVY, true))
            if (rooms.isEmpty()) {
                val r = a.row()
                r.addView(ProgressBar(a), LinearLayout.LayoutParams(a.dp(32), a.dp(32)).apply { marginEnd = a.dp(12) })
                r.addView(a.label(a.tr("Looking for rooms… Both devices need the same Wi-Fi or hotspot.", "در حال جست‌وجوی اتاق… هر دو دستگاه باید روی یک Wi-Fi یا هات‌اسپات باشند."), 14f, MUTED), LinearLayout.LayoutParams(0, -2, 1f))
                card(c, r)
            } else rooms.forEach { room ->
                val r = a.row()
                r.addView(AvatarView(a, room.profile), LinearLayout.LayoutParams(a.dp(48), a.dp(48)).apply { marginEnd = a.dp(12) })
                val text = a.column()
                text.addView(a.label(room.profile.displayName(a), 17f, NAVY, true))
                text.addView(a.label(a.tr("Asks the owner to let you in", "از صاحب اتاق اجازهٔ ورود می‌گیرد"), 13f, MUTED))
                r.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
                r.addView(a.button(a.tr("Join", "ورود"), true) { openDialog?.dismiss(); join(CollabInvite(listOf(room.host), room.port, "", room.roomId, room.profile.name)) })
                card(c, r)
            }
            val actions = a.row()
            actions.addView(roleButton(a.tr("Scan QR code", "اسکن کد QR"), "scan", false) { scanQr { text -> CollabInvite.parse(text)?.let { openDialog?.dismiss(); join(it) } } })
            actions.addView(roleButton(a.tr("Enter address", "واردکردن نشانی"), "address", false) { addressScreen() })
            c.addView(a.scrollRow(actions))
            profileRow(c, true)
        })
        discovery = finder
        finder.changed = { found -> rooms = found; refresh() }
        finder.browse()
    }

    private fun addressScreen() {
        val c = a.column().apply { pad(16) }
        c.addView(a.label(a.tr("The address and code are shown in the room on the other device.", "نشانی و کد در صفحهٔ اتاق روی دستگاه دیگر نمایش داده می‌شوند."), 14f, MUTED))
        val address = a.field(hintValue = "192.168.1.20:40123").apply { setSingleLine(true); textDirection = View.TEXT_DIRECTION_LTR; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI }
        val code = a.field(hintValue = a.tr("Room code (optional)", "کد اتاق (اختیاری)")).apply { setSingleLine(true); inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        c.addView(address); c.addView(code)
        val d = a.backDialogBuilder(a.tr("Enter address", "واردکردن نشانی"), null).setView(c).setNegativeButton(a.s("cancel"), null).setPositiveButton(a.tr("Join", "ورود"), null).create()
        d.show(); Fonts.onShown(d)
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val invite = CollabInvite.parseAddress(address.text.toString(), code.text.toString())
            if (invite == null) { address.error = a.tr("Use the form 192.168.1.20:40123", "به شکل 192.168.1.20:40123 وارد کنید"); return@setOnClickListener }
            d.dismiss(); openDialog?.dismiss(); join(invite)
        }
    }

    /** Small floating chip on the board: who is here and this display's role. */
    fun pill(): View? {
        val s = session()?.takeIf { it.isActive } ?: return null
        val r = a.row().apply { pad(4); setPaddingRelative(a.dp(6), a.dp(4), a.dp(12), a.dp(4)) }
        val people = listOf(s.me) + s.everyone().sortedByDescending { it.isHost }.map { it.profile }
        r.addView(a.avatarStack(people, 32, 4))
        val label = when {
            s.isHost -> a.tr("Room", "اتاق") + " · ${people.size}" + if (s.knocks.isNotEmpty()) "  •" + s.knocks.size else ""
            s.status != "joined" -> a.tr("Joining…", "در حال ورود…")
            else -> roleName(s.role)
        }
        r.addView(a.label(label, 14f, if (s.knocks.isNotEmpty()) ORANGE else NAVY, true).apply { maxLines = 1; setPadding(a.dp(8), 0, 0, 0) })
        r.isClickable = true; r.isFocusable = true
        r.contentDescription = a.tr("Room: ", "اتاق: ") + label
        r.setOnClickListener { open(null) }
        return r
    }

    fun close() { openDialog?.dismiss(); openDialog = null; discovery?.close(); discovery = null }
}
