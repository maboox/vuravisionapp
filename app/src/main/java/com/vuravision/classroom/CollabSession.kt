package com.vuravision.classroom

import android.graphics.PointF
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom

/** What the session needs from the screen that owns the board. */
interface CollabHost {
    val activity: android.app.Activity
    val lessonStore: Store
    val whiteboard: Board
    val media: Media
    /** Shows another page because the person being followed moved there. */
    fun followPage(index: Int)
    /** A guest works on a copy of the shared lesson; the guest's own file is kept aside. */
    fun beginGuestDocument(lesson: Lesson)
    fun endGuestDocument(keepCopy: Boolean)
    fun sessionChanged()
    fun notify(message: String)
}

/** A person in the room, with what they are doing right now. */
class CollabPeer(val id: String, var profile: CollabProfile, var role: String, val isHost: Boolean) {
    var pageId = ""
    var touching = false
    var cursor: PointF? = null
    var cursorPane = 0
    var heardAt = 0L
    var live: List<Item> = emptyList()
    var selection: Set<String> = emptySet()
    var activity: RectF? = null
    var activityPage = ""
    var activityPane = 0
    var activityAt = 0L
    var view: RectF? = null
    var viewPage = ""
    var viewPane = 0
    var viewAt = 0L
}

class CollabKnock(val connection: CollabConnection, val profile: CollabProfile)

/**
 * A local room. The creator (host) keeps the authoritative lesson and relays every change;
 * guests keep a copy. Roles: "view" follows the host and cannot edit; "control" works on the
 * host's board as if standing at it (same page, view and Undo); "collaborate" works
 * independently with its own tools, page, view and Undo.
 */
class CollabSession private constructor(private val host: CollabHost, val isHost: Boolean) {
    companion object {
        const val VIEW = "view"; const val CONTROL = "control"; const val COLLABORATE = "collaborate"
        val ROLES = listOf(VIEW, CONTROL, COLLABORATE)
        const val HALO_MILLIS = 60_000L
        private const val CHUNK = 192 * 1024
        private const val MAX_ASSET = 64L * 1024 * 1024

        fun hostRoom(host: CollabHost): CollabSession = CollabSession(host, true).also { it.startHost() }
        fun join(host: CollabHost, invite: CollabInvite): CollabSession = CollabSession(host, false).also { it.startGuest(invite) }
    }

    private val main = Handler(Looper.getMainLooper())
    private val store get() = host.lessonStore
    private val board get() = host.whiteboard
    var me = CollabProfile.load(host.activity); private set
    val peers = LinkedHashMap<String, CollabPeer>()
    val knocks = mutableListOf<CollabKnock>()
    /** connecting · waiting · joined · ended */
    var status = "connecting"; private set
    var endReason = ""; private set
    var role = COLLABORATE; private set
    var defaultRole = VIEW
    var roomName = ""; private set
    val roomId: String = newId().take(8)
    val code: String = (100000 + SecureRandom().nextInt(900000)).toString()
    var invite: CollabInvite? = null; private set
    var hostProfile: CollabProfile? = null; private set
    var locks: Map<String, String> = emptyMap(); private set

    private var server: CollabServer? = null
    private val discovery = CollabDiscovery(host.activity)
    private val connections = LinkedHashMap<String, CollabConnection>()
    private val unidentified = mutableSetOf<CollabConnection>()
    private var hostConnection: CollabConnection? = null
    private var shadow: CollabSync.Shadow? = null
    private var dirty = false
    private var applying = false
    private var seq = 0
    private val pendingBySeq = HashMap<Int, Set<String>>()
    private val pendingCount = HashMap<String, Int>()
    private val published = HashSet<String>()
    private val requested = HashSet<String>()
    private val waitingFor = HashMap<String, MutableSet<String>>()
    private val incoming = HashMap<String, File>()
    private var lastPageId = ""
    private var lastPresence = ""
    private var lastPresenceAt = 0L
    private var lastView = ""
    private var lastPing = 0L
    private var myActivity: RectF? = null
    private var myActivityPane = 0
    private var myActivityAt = 0L
    private var myViewAt = 0L
    private var lastViewRect = ""

    val isActive get() = status != "ended"
    val joined get() = isHost || status == "joined"
    /** Guests in View mode cannot change anything. */
    val readOnly get() = !isHost && role == VIEW
    val follows get() = !isHost && role != COLLABORATE

    // ---------------------------------------------------------------- lifecycle

    private fun startHost() {
        roomName = me.displayName(host.activity)
        hostProfile = me
        val srv = CollabServer { socket ->
            val connection = CollabConnection(socket, ::onHostMessage, ::onHostClosed)
            unidentified.add(connection); connection.start()
            main.postDelayed({ if (connection in unidentified) { unidentified.remove(connection); connection.close() } }, 15_000)
        }
        server = srv; srv.start()
        invite = CollabInvite(CollabDiscovery.addresses(host.activity), srv.port, code, roomId, roomName)
        discovery.announce(srv.port, roomId, me)
        shadow = CollabSync.capture(store.lesson)
        lastPageId = store.page.id
        status = "joined"
        tick.run()
        host.sessionChanged()
    }

    fun refreshInvite() { server?.let { invite = CollabInvite(CollabDiscovery.addresses(host.activity), it.port, code, roomId, roomName) } }

    private fun startGuest(target: CollabInvite) {
        invite = target
        roomName = target.name
        fun attempt(index: Int) {
            if (status == "ended") return
            val address = target.hosts.getOrNull(index)
            if (address == null) { finish(host.activity.tr("Could not reach the room. Both devices must be on the same Wi-Fi or hotspot.", "اتصال به اتاق ممکن نشد. هر دو دستگاه باید روی یک Wi-Fi یا هات‌اسپات باشند.")); return }
            CollabConnection.connect(address, target.port, ::onGuestMessage, ::onGuestClosed) { connection ->
                if (connection == null) { attempt(index + 1); return@connect }
                if (status == "ended") { connection.close(); return@connect }
                hostConnection = connection; connection.start()
                connection.send(JsonObject().apply {
                    addProperty("t", "hello"); addProperty("proto", COLLAB_PROTOCOL); addProperty("id", me.id)
                    add("profile", CollabSync.gson.toJsonTree(me)); addProperty("code", target.code)
                })
            }
        }
        attempt(0)
        tick.run()
        host.sessionChanged()
    }

    /** Leaves (guest) or closes the room for everyone (host). */
    fun end(keepCopy: Boolean = true, reason: String = "") {
        if (status == "ended") return
        val wasGuestDocument = !isHost && status == "joined"
        status = "ended"; endReason = reason
        main.removeCallbacks(tick)
        if (isHost) {
            connections.values.forEach { it.send(JsonObject().apply { addProperty("t", "end") }); it.close() }
            unidentified.forEach { it.close() }
            knocks.forEach { it.connection.close() }
        } else hostConnection?.let { it.send(JsonObject().apply { addProperty("t", "bye") }); it.close() }
        connections.clear(); unidentified.clear(); knocks.clear(); peers.clear(); hostConnection = null
        server?.stop(); server = null; discovery.close()
        store.remoteHistory = null
        board.foreignLocks = emptyMap(); board.overlay = null; board.isEnabled = true; board.invalidate()
        incoming.values.forEach { it.delete() }; incoming.clear()
        if (wasGuestDocument) host.endGuestDocument(keepCopy)
        host.sessionChanged()
    }

    private fun finish(reason: String) { end(true, reason); if (reason.isNotBlank()) host.notify(reason) }

    // ---------------------------------------------------------------- host side

    private fun onHostMessage(connection: CollabConnection, m: JsonObject) {
        val type = m.get("t")?.asString ?: return
        if (type == "hello") { hello(connection, m); return }
        val peer = peers[connection.peerId]?.takeIf { connections[it.id] === connection } ?: return
        peer.heardAt = SystemClock.uptimeMillis()
        when (type) {
            "ops" -> {
                val body = m.getAsJsonObject("body") ?: return
                if (peer.role != VIEW) {
                    flush()
                    val touched = applyRemote(body, rebase = peer.role == COLLABORATE, checkpoint = peer.role == CONTROL)
                    markActivity(peer, touched)
                    val forward = JsonObject().apply { addProperty("t", "ops"); addProperty("from", peer.id); add("body", body) }
                    connections.forEach { (id, c) -> if (id != peer.id) c.send(forward) }
                    requestMissing(touched)
                } else connection.send(lessonMessage())
                connection.send(JsonObject().apply { addProperty("t", "ack"); add("seq", m.get("seq")) })
            }
            "presence" -> {
                presence(peer, m)
                m.addProperty("from", peer.id)
                connections.forEach { (id, c) -> if (id != peer.id) c.send(m) }
                recomputeLocks()
            }
            "page" -> if (peer.role == CONTROL) {
                val index = store.lesson.pages.indexOfFirst { it.id == m.get("id")?.asString }
                if (index >= 0 && index != store.lesson.current) host.followPage(index)
            }
            "view" -> if (peer.role == CONTROL) {
                applyView(m)
                lastView = viewSignature()
                m.addProperty("from", peer.id)
                connections.forEach { (id, c) -> if (id != peer.id && peers[id]?.role != COLLABORATE) c.send(m) }
            }
            "undo" -> if (peer.role == CONTROL) store.undo()
            "redo" -> if (peer.role == CONTROL) store.redo()
            "need" -> m.get("name")?.asString?.let { name ->
                if (host.media.dir.resolve(name).isFile) sendAsset(connection, name)
                else { waitingFor.getOrPut(name) { HashSet() }.add(peer.id); connections.forEach { (id, c) -> if (id != peer.id) c.send(m) } }
            }
            "asset" -> receiveAsset(peer.id, m)
            "sync" -> connection.send(lessonMessage())
            "profile" -> { runCatching { CollabSync.gson.fromJson(m.get("profile"), CollabProfile::class.java) }.getOrNull()?.let { peer.profile = it.copy(id = peer.id) }; broadcastPeers() }
            "bye" -> connection.close()
        }
    }

    private fun hello(connection: CollabConnection, m: JsonObject) {
        if (connection !in unidentified) return
        unidentified.remove(connection)
        if (m.get("proto")?.asInt != COLLAB_PROTOCOL) { deny(connection, "version"); return }
        val profile = runCatching { CollabSync.gson.fromJson(m.get("profile"), CollabProfile::class.java) }.getOrNull()
        val id = m.get("id")?.asString?.takeIf { it.isNotBlank() && it.length <= 64 && it != me.id }
        if (profile == null || id == null) { deny(connection, "invalid"); return }
        profile.id = id; profile.name = profile.name.take(40); if (profile.photo.length > 90_000) profile.photo = ""
        connection.peerId = id
        connections.remove(id)?.let { old -> peers.remove(id); old.close() }
        knocks.removeAll { it.profile.id == id }
        if (m.get("code")?.asString == code) admit(connection, profile)
        else {
            knocks.add(CollabKnock(connection, profile))
            connection.send(JsonObject().apply { addProperty("t", "wait") })
            host.notify(host.activity.tr("${profile.displayName(host.activity)} wants to join", "${profile.displayName(host.activity)} می‌خواهد وارد شود"))
            host.sessionChanged()
        }
    }

    private fun deny(connection: CollabConnection, reason: String) {
        connection.send(JsonObject().apply { addProperty("t", "denied"); addProperty("reason", reason) })
        main.postDelayed({ connection.close() }, 300)
    }

    fun answer(knock: CollabKnock, accept: Boolean) {
        if (!knocks.remove(knock)) return
        if (accept && knock.connection.isOpen) admit(knock.connection, knock.profile) else deny(knock.connection, "declined")
        host.sessionChanged()
    }

    private fun admit(connection: CollabConnection, profile: CollabProfile) {
        val peer = CollabPeer(profile.id, profile, defaultRole, false).apply { heardAt = SystemClock.uptimeMillis() }
        peers[peer.id] = peer; connections[peer.id] = connection
        flush()
        val doc = store.lesson.copyForSave().also { it.pdf = null }
        val pageId = store.page.id
        val view = viewMessage()
        connection.send {
            JsonObject().apply {
                addProperty("t", "welcome"); addProperty("you", peer.id); addProperty("role", peer.role)
                addProperty("room", roomName); add("host", CollabSync.gson.toJsonTree(me)); addProperty("hostId", me.id)
                add("lesson", CollabSync.gson.toJsonTree(doc)); addProperty("page", pageId); add("view", view)
            }
        }
        broadcastPeers(); sendLocks()
        host.notify(host.activity.tr("${profile.displayName(host.activity)} joined", "${profile.displayName(host.activity)} وارد شد"))
        host.sessionChanged()
    }

    private fun onHostClosed(connection: CollabConnection, reason: String?) {
        unidentified.remove(connection)
        if (knocks.removeAll { it.connection === connection }) host.sessionChanged()
        val id = connection.peerId
        if (connections[id] !== connection) return
        connections.remove(id)
        val peer = peers.remove(id)
        if (status == "ended") return
        recomputeLocks(); broadcastPeers(); board.invalidate()
        peer?.let { host.notify(host.activity.tr("${it.profile.displayName(host.activity)} left", "${it.profile.displayName(host.activity)} خارج شد")) }
        host.sessionChanged()
    }

    fun setRole(peerId: String, value: String) {
        val peer = peers[peerId] ?: return
        if (value !in ROLES || peer.role == value) return
        peer.role = value
        val connection = connections[peerId] ?: return
        connection.send(JsonObject().apply { addProperty("t", "role"); addProperty("role", value) })
        if (value != COLLABORATE) { connection.send(JsonObject().apply { addProperty("t", "page"); addProperty("id", store.page.id) }); connection.send(viewMessage().apply { addProperty("t", "view") }) }
        broadcastPeers(); recomputeLocks(); host.sessionChanged()
    }

    fun remove(peerId: String) {
        connections[peerId]?.let { it.send(JsonObject().apply { addProperty("t", "end"); addProperty("reason", "removed") }); main.postDelayed({ it.close() }, 300) }
    }

    private fun peersJson() = JsonArray().apply {
        add(JsonObject().apply { addProperty("id", me.id); add("profile", CollabSync.gson.toJsonTree(me)); addProperty("role", "host"); addProperty("host", true) })
        peers.values.forEach { p -> add(JsonObject().apply { addProperty("id", p.id); add("profile", CollabSync.gson.toJsonTree(p.profile)); addProperty("role", p.role); addProperty("host", false) }) }
    }

    private fun broadcastPeers() {
        val m = JsonObject().apply { addProperty("t", "peers"); add("list", peersJson()) }
        connections.values.forEach { it.send(m) }
    }

    private fun lessonMessage(): JsonObject {
        flush()
        val doc = store.lesson.copyForSave().also { it.pdf = null }
        return JsonObject().apply { addProperty("t", "lesson"); add("lesson", CollabSync.gson.toJsonTree(doc)); addProperty("page", store.page.id) }
    }

    private fun recomputeLocks() {
        if (!isHost) return
        val claims = LinkedHashMap<String, Set<String>>()
        claims[me.id] = board.selected.toSet()
        peers.values.forEach { if (it.role != VIEW) claims[it.id] = it.selection }
        val next = HashMap<String, String>()
        locks.forEach { (item, owner) -> if (claims[owner]?.contains(item) == true) next[item] = owner }
        claims.forEach { (owner, ids) -> ids.forEach { if (it !in next) next[it] = owner } }
        if (next != locks) { locks = next; sendLocks(); applyLocks() }
    }

    private fun sendLocks() {
        val m = JsonObject().apply { addProperty("t", "locks"); add("map", CollabSync.gson.toJsonTree(locks)) }
        connections.values.forEach { it.send(m) }
    }

    // ---------------------------------------------------------------- guest side

    private fun onGuestMessage(connection: CollabConnection, m: JsonObject) {
        if (connection !== hostConnection) return
        when (m.get("t")?.asString) {
            "wait" -> { status = "waiting"; host.sessionChanged() }
            "denied" -> finish(when (m.get("reason")?.asString) {
                "declined" -> host.activity.tr("The room owner declined the request.", "صاحب اتاق درخواست را نپذیرفت.")
                "version" -> host.activity.tr("Update VuraVision on both devices to the same version.", "VuraVision را روی هر دو دستگاه به یک نسخه به‌روز کنید.")
                else -> host.activity.tr("Could not join the room.", "ورود به اتاق ممکن نشد.")
            })
            "welcome" -> welcome(m)
            "ops" -> if (status == "joined") m.getAsJsonObject("body")?.let { body ->
                flush()
                val touched = applyRemote(body, rebase = role == COLLABORATE, checkpoint = false)
                peers[m.get("from")?.asString]?.let { markActivity(it, touched) }
                requestMissing(touched)
            }
            "ack" -> m.get("seq")?.asInt?.let { ack(it) }
            "presence" -> peers[m.get("from")?.asString]?.let { presence(it, m) }
            "peers" -> peers(m.getAsJsonArray("list"))
            "locks" -> { locks = runCatching { m.getAsJsonObject("map").entrySet().associate { it.key to it.value.asString } }.getOrDefault(emptyMap()); applyLocks() }
            "role" -> m.get("role")?.asString?.takeIf { it in ROLES }?.let { changeRole(it) }
            "page" -> if (follows) {
                val index = store.lesson.pages.indexOfFirst { it.id == m.get("id")?.asString }
                if (index >= 0 && index != store.lesson.current) { lastPageId = store.lesson.pages[index].id; host.followPage(index) }
            }
            "view" -> if (follows) { applyView(m); lastView = viewSignature() }
            "asset" -> receiveAsset("host", m)
            "need" -> m.get("name")?.asString?.let { name -> if (host.media.dir.resolve(name).isFile) sendAsset(connection, name) }
            "lesson" -> resync(m)
            "end" -> finish(if (m.get("reason")?.asString == "removed") host.activity.tr("The room owner removed you from the room.", "صاحب اتاق شما را از اتاق خارج کرد.") else host.activity.tr("The room was closed.", "اتاق بسته شد."))
        }
    }

    private fun welcome(m: JsonObject) {
        val lesson = runCatching { CollabSync.gson.fromJson(m.get("lesson"), Lesson::class.java).also { it.pdf = null; it.pages.forEach(CollabSync::sanitize); it.validate() } }.getOrNull()
        if (lesson == null) { finish(host.activity.tr("The shared board could not be opened.", "تختهٔ مشترک باز نشد.")); return }
        roomName = m.get("room")?.asString.orEmpty()
        hostProfile = runCatching { CollabSync.gson.fromJson(m.get("host"), CollabProfile::class.java) }.getOrNull()
        role = m.get("role")?.asString?.takeIf { it in ROLES } ?: VIEW
        lesson.current = lesson.pages.indexOfFirst { it.id == m.get("page")?.asString }.takeIf { it >= 0 } ?: 0
        applying = true
        try { host.beginGuestDocument(lesson) } finally { applying = false }
        shadow = CollabSync.capture(store.lesson); lastPageId = store.page.id
        status = "joined"
        changeRole(role, announce = false)
        m.getAsJsonObject("view")?.let { if (follows) applyView(it) }
        lastView = viewSignature()
        requestMissing(null)
        host.sessionChanged()
    }

    private fun resync(m: JsonObject) {
        val lesson = runCatching { CollabSync.gson.fromJson(m.get("lesson"), Lesson::class.java).also { it.pdf = null; it.pages.forEach(CollabSync::sanitize); it.validate() } }.getOrNull() ?: return
        val keep = store.page.id
        lesson.current = lesson.pages.indexOfFirst { it.id == (if (follows) m.get("page")?.asString else keep) }.takeIf { it >= 0 } ?: 0
        applying = true
        try { store.replace(lesson); store.clearHistory() } finally { applying = false }
        pendingBySeq.clear(); pendingCount.clear()
        shadow = CollabSync.capture(store.lesson); lastPageId = store.page.id
        board.clearSelection(); board.sceneChanged()
        requestMissing(null)
    }

    private fun changeRole(value: String, announce: Boolean = true) {
        val before = role; role = value
        store.remoteHistory = if (value == CONTROL) { command -> hostConnection?.send(JsonObject().apply { addProperty("t", command) }) } else null
        if (value != COLLABORATE) store.clearHistory()
        board.isEnabled = value != VIEW
        if (value == VIEW) board.clearSelection()
        if (announce && before != value) host.notify(host.activity.tr("You can now ", "اکنون می‌توانید ") + roleTitle(value).lowercase())
        host.sessionChanged()
    }

    fun roleTitle(value: String) = when (value) {
        VIEW -> host.activity.tr("View", "ببینید")
        CONTROL -> host.activity.tr("Control the board", "تخته را کنترل کنید")
        else -> host.activity.tr("Collaborate", "همکاری کنید")
    }

    private fun peers(list: JsonArray?) {
        val seen = HashSet<String>()
        list?.forEach { e ->
            val o = e.asJsonObject; val id = o.get("id")?.asString ?: return@forEach
            if (id == me.id) { o.get("role")?.asString?.takeIf { it in ROLES && it != role }?.let { changeRole(it) }; return@forEach }
            val profile = runCatching { CollabSync.gson.fromJson(o.get("profile"), CollabProfile::class.java) }.getOrNull() ?: return@forEach
            seen.add(id)
            val isHostPeer = o.get("host")?.asBoolean == true
            val peer = peers.getOrPut(id) { CollabPeer(id, profile, o.get("role")?.asString.orEmpty(), isHostPeer) }
            peer.profile = profile; peer.role = o.get("role")?.asString.orEmpty()
        }
        peers.keys.retainAll(seen)
        host.sessionChanged()
    }

    private fun ack(value: Int) {
        pendingBySeq.remove(value)?.forEach { key -> val n = (pendingCount[key] ?: 1) - 1; if (n <= 0) pendingCount.remove(key) else pendingCount[key] = n }
    }

    private fun onGuestClosed(connection: CollabConnection, reason: String?) {
        if (connection !== hostConnection || status == "ended") return
        finish(host.activity.tr("The connection to the room was lost.", "اتصال به اتاق قطع شد."))
    }

    fun updateProfile(profile: CollabProfile) {
        me = profile
        if (isHost) { broadcastPeers(); discovery.announce(server?.port ?: return, roomId, me) }
        else hostConnection?.send(JsonObject().apply { addProperty("t", "profile"); add("profile", CollabSync.gson.toJsonTree(me)) })
        host.sessionChanged()
    }

    // ---------------------------------------------------------------- synchronisation

    /** Called whenever the local lesson changed; changes are batched briefly. */
    fun localChanged() {
        if (applying || !joined || status == "ended") return
        dirty = true
        main.removeCallbacks(flushTask); main.postDelayed(flushTask, 40)
    }

    private val flushTask = Runnable { flush() }

    private fun flush() {
        main.removeCallbacks(flushTask)
        val sh = shadow ?: return
        if (!joined || status == "ended") return
        dirty = false
        pageChanged()
        if (readOnly) return
        // A value the document format cannot hold (e.g. NaN) must not crash the board; the file save reports it.
        val diff = try { CollabSync.diff(store.lesson, sh) } catch (_: Exception) { null } ?: return
        diff.touched[store.page.id]?.let { ids -> CollabSync.bounds(store.page, ids)?.let { myActivity = it; myActivityPane = store.page.items.firstOrNull { o -> o.id in ids }?.pane ?: 0; myActivityAt = SystemClock.uptimeMillis() } }
        if (isHost) {
            val m = JsonObject().apply { addProperty("t", "ops"); addProperty("from", me.id); add("body", diff.body) }
            connections.values.forEach { it.send(m) }
        } else {
            val connection = hostConnection ?: return
            diff.assets.filter { it !in published }.forEach { name -> if (host.media.dir.resolve(name).isFile) { published.add(name); sendAsset(connection, name) } }
            val number = ++seq
            pendingBySeq[number] = diff.pendingKeys
            diff.pendingKeys.forEach { pendingCount[it] = (pendingCount[it] ?: 0) + 1 }
            connection.send(JsonObject().apply { addProperty("t", "ops"); addProperty("seq", number); add("body", diff.body) })
        }
    }

    private fun pageChanged() {
        val id = store.page.id
        if (id == lastPageId) return
        lastPageId = id
        if (isHost) {
            val m = JsonObject().apply { addProperty("t", "page"); addProperty("id", id) }
            connections.forEach { (peerId, c) -> if (peers[peerId]?.role != COLLABORATE) c.send(m) }
        } else if (role == CONTROL) hostConnection?.send(JsonObject().apply { addProperty("t", "page"); addProperty("id", id) })
    }

    private fun applyRemote(body: JsonObject, rebase: Boolean, checkpoint: Boolean): Set<String> {
        val sh = shadow ?: return emptySet()
        applying = true
        try {
            if (checkpoint) store.checkpoint()
            val touched = try { CollabSync.apply(store.lesson, body, pendingCount.keys) } catch (e: Exception) {
                if (!isHost) hostConnection?.send(JsonObject().apply { addProperty("t", "sync") })
                return emptySet()
            }
            if (rebase) store.rebase { snapshot -> runCatching { CollabSync.apply(snapshot, body) } }
            runCatching { CollabSync.diff(store.lesson, sh) }
            board.selected.retainAll(store.page.items.map { it.id }.toSet())
            store.changed()
            return touched
        } finally { applying = false; lastPageId = store.page.id }
    }

    private fun markActivity(peer: CollabPeer, touched: Set<String>) {
        val page = store.lesson.pages.firstOrNull { p -> p.items.any { it.id in touched } } ?: return
        CollabSync.bounds(page, touched)?.let { peer.activity = it; peer.activityPage = page.id; peer.activityPane = page.items.firstOrNull { o -> o.id in touched }?.pane ?: 0; peer.activityAt = SystemClock.uptimeMillis() }
        board.invalidate()
    }

    // ---------------------------------------------------------------- assets

    private fun requestMissing(ids: Set<String>?) {
        if (isHost) return
        val names = if (ids == null) CollabSync.assetsIn(store.lesson) else store.lesson.pages.flatMap { p -> p.items.filter { it.id in ids }.map { it.asset } }.filter { it.isNotEmpty() }.toSet()
        names.filter { it.matches(Regex("[a-zA-Z0-9._-]+")) && !host.media.dir.resolve(it).isFile && requested.add(it) }
            .forEach { name -> hostConnection?.send(JsonObject().apply { addProperty("t", "need"); addProperty("name", name) }) }
    }

    private fun sendAsset(connection: CollabConnection, name: String) {
        if (!name.matches(Regex("[a-zA-Z0-9._-]+"))) return
        val file = host.media.dir.resolve(name)
        val length = file.length()
        if (!file.isFile || length <= 0 || length > MAX_ASSET) return
        val total = ((length + CHUNK - 1) / CHUNK).toInt()
        for (index in 0 until total) connection.send {
            val bytes = RandomAccessFile(file, "r").use { raf ->
                raf.seek(index.toLong() * CHUNK)
                ByteArray(minOf(CHUNK.toLong(), length - index.toLong() * CHUNK).toInt()).also { raf.readFully(it) }
            }
            JsonObject().apply { addProperty("t", "asset"); addProperty("name", name); addProperty("i", index); addProperty("n", total); addProperty("data", Base64.encodeToString(bytes, Base64.NO_WRAP)) }
        }
    }

    private fun receiveAsset(from: String, m: JsonObject) {
        val name = m.get("name")?.asString ?: return
        if (!name.matches(Regex("[a-zA-Z0-9._-]+")) || name.length > 120) return
        val target = host.media.dir.resolve(name)
        val index = m.get("i")?.asInt ?: return; val total = m.get("n")?.asInt ?: return
        val key = "$from/$name"
        if (target.isFile) { if (index == total - 1) incoming.remove(key)?.delete(); return }
        val part = if (index == 0) File(host.activity.cacheDir, "collab-${newId()}.part").also { incoming.put(key, it)?.delete() } else incoming[key] ?: return
        try {
            part.appendBytes(Base64.decode(m.get("data")?.asString.orEmpty(), Base64.NO_WRAP))
            if (part.length() > MAX_ASSET) { incoming.remove(key); part.delete(); return }
            if (index == total - 1) {
                incoming.remove(key)
                if (!target.isFile && !part.renameTo(target)) { part.copyTo(target, overwrite = false); part.delete() }
                host.media.clear(); board.sceneChanged()
                waitingFor.remove(name)?.forEach { id -> connections[id]?.let { sendAsset(it, name) } }
            }
        } catch (_: Exception) { incoming.remove(key); part.delete() }
    }

    // ---------------------------------------------------------------- presence & views

    private fun presence(peer: CollabPeer, m: JsonObject) {
        peer.heardAt = SystemClock.uptimeMillis()
        val before = peer.pageId to peer.touching
        peer.pageId = m.get("page")?.asString.orEmpty()
        peer.touching = m.get("touch")?.asBoolean == true
        peer.cursor = m.getAsJsonArray("cursor")?.takeIf { it.size() == 2 }?.let { PointF(it[0].asFloat, it[1].asFloat) }
        peer.cursorPane = m.get("pane")?.asInt ?: 0
        peer.selection = m.getAsJsonArray("sel")?.map { it.asString }?.toSet() ?: emptySet()
        peer.live = m.getAsJsonArray("live")?.mapIndexedNotNull { i, e -> runCatching { liveItem(peer.id, i, e.asJsonObject) }.getOrNull() } ?: emptyList()
        m.getAsJsonArray("act")?.takeIf { it.size() == 4 }?.let { a ->
            peer.activity = RectF(a[0].asFloat, a[1].asFloat, a[2].asFloat, a[3].asFloat); peer.activityPage = peer.pageId
            peer.activityPane = m.get("actPane")?.asInt ?: 0; peer.activityAt = SystemClock.uptimeMillis()
        }
        m.getAsJsonArray("view")?.takeIf { it.size() == 4 }?.let { a ->
            peer.view = RectF(a[0].asFloat, a[1].asFloat, a[2].asFloat, a[3].asFloat); peer.viewPage = peer.pageId
            peer.viewPane = m.get("viewPane")?.asInt ?: 0; peer.viewAt = SystemClock.uptimeMillis()
        }
        board.invalidate()
        if (before != (peer.pageId to peer.touching)) host.sessionChanged()
    }

    private fun liveItem(peerId: String, index: Int, o: JsonObject): Item {
        val points = o.getAsJsonArray("pts")?.let { a -> (0 until a.size() / 2).map { Point(a[it * 2].asFloat, a[it * 2 + 1].asFloat) }.toMutableList() } ?: mutableListOf()
        return Item(id = "live-$peerId-$index", kind = o.get("k")?.asString ?: "ink", shape = o.get("s")?.asString ?: "round",
            color = o.get("c")?.asInt ?: NAVY, width = o.get("w")?.asFloat ?: 4f, alpha = o.get("a")?.asInt ?: 255,
            pane = o.get("p")?.asInt ?: 0, x = o.get("x")?.asFloat ?: 0f, y = o.get("y")?.asFloat ?: 0f,
            w = o.get("ww")?.asFloat ?: 1f, h = o.get("hh")?.asFloat ?: 1f, flipX = o.get("fx")?.asBoolean == true, flipY = o.get("fy")?.asBoolean == true,
            dashLength = o.get("dl")?.asFloat ?: 0f, dashGap = o.get("dg")?.asFloat ?: 0f, points = points)
    }

    private fun livePayload(): JsonArray = JsonArray().apply {
        board.liveItems().filter { o -> listOf(o.x, o.y, o.w, o.h, o.width).all { it.isFinite() } && o.points.all { it.x.isFinite() && it.y.isFinite() } }.take(4).forEach { o ->
            add(JsonObject().apply {
                addProperty("k", o.kind); addProperty("s", o.shape); addProperty("c", o.color); addProperty("w", o.width); addProperty("a", o.alpha); addProperty("p", o.pane)
                addProperty("x", o.x); addProperty("y", o.y); addProperty("ww", o.w); addProperty("hh", o.h); addProperty("fx", o.flipX); addProperty("fy", o.flipY)
                addProperty("dl", o.dashLength); addProperty("dg", o.dashGap)
                if (o.kind == "ink") add("pts", JsonArray().also { a -> o.points.takeLast(800).forEach { p -> a.add(Math.round(p.x * 10) / 10f); a.add(Math.round(p.y * 10) / 10f) } })
            })
        }
    }

    private fun sendPresence(now: Long) {
        val page = store.page.id
        val touch = board.touching
        val cursor = board.touchWorld?.takeIf { it.x.isFinite() && it.y.isFinite() }
        val sel = if (board.store === store) board.selected.toList() else emptyList()
        val live = livePayload()
        val viewRect = board.viewRect(board.activePane).takeIf { r -> listOf(r.left, r.top, r.right, r.bottom).all { it.isFinite() } } ?: RectF()
        val viewKey = "%.0f,%.0f,%.0f,%.0f".format(viewRect.left, viewRect.top, viewRect.right, viewRect.bottom)
        if (viewKey != lastViewRect) { lastViewRect = viewKey; myViewAt = now }
        val signature = "$page|$touch|${cursor?.x?.toInt()},${cursor?.y?.toInt()}|$sel|${live.size()}:${board.liveItems().sumOf { it.points.size }}|$viewKey|$myActivityAt"
        if (signature == lastPresence && now - lastPresenceAt < 2000) return
        lastPresence = signature; lastPresenceAt = now
        val m = JsonObject().apply {
            addProperty("t", "presence"); addProperty("page", page); addProperty("touch", touch); addProperty("pane", board.touchPane)
            if (cursor != null) add("cursor", JsonArray().also { it.add(cursor.x); it.add(cursor.y) })
            add("sel", CollabSync.gson.toJsonTree(sel)); add("live", live)
            add("view", JsonArray().also { it.add(viewRect.left); it.add(viewRect.top); it.add(viewRect.right); it.add(viewRect.bottom) }); addProperty("viewPane", board.activePane)
            myActivity?.takeIf { now - myActivityAt < 1500 }?.let { r -> add("act", JsonArray().also { it.add(r.left); it.add(r.top); it.add(r.right); it.add(r.bottom) }); addProperty("actPane", myActivityPane) }
        }
        if (isHost) { m.addProperty("from", me.id); connections.values.forEach { it.send(m) }; recomputeLocks() }
        else hostConnection?.send(m)
    }

    private fun viewMessage(): JsonObject = JsonObject().apply {
        addProperty("t", "view"); addProperty("page", store.page.id)
        val density = board.resources.displayMetrics.density
        addProperty("w", board.width / density); addProperty("h", board.height / density)
        add("panes", JsonArray().also { a -> store.page.panes.forEach { p -> a.add(JsonArray().also { v -> v.add(p.zoom); v.add(p.tx); v.add(p.ty) }) } })
    }

    private fun viewSignature() = store.page.id + store.page.panes.joinToString { "%.3f,%.1f,%.1f".format(it.zoom, it.tx, it.ty) } + "|${board.width}x${board.height}"

    /** Shows the same region as the sender, fitted to this screen's size. */
    private fun applyView(m: JsonObject) {
        if (m.get("page")?.asString != store.page.id) return
        val density = board.resources.displayMetrics.density
        val hostW = m.get("w")?.asFloat ?: return; val hostH = m.get("h")?.asFloat ?: return
        val panes = m.getAsJsonArray("panes") ?: return
        if (hostW <= 0 || hostH <= 0 || board.width <= 0) return
        val n = store.page.panes.size
        if (panes.size() != n) return
        val localW = board.width / density / n; val localH = board.height / density; val remoteW = hostW / n
        store.page.panes.forEachIndexed { i, pane ->
            val v = panes[i].asJsonArray; val zoom = v[0].asFloat; val tx = v[1].asFloat; val ty = v[2].asFloat
            if (!zoom.isFinite() || zoom <= 0f) return@forEachIndexed
            val cx = (remoteW / 2 - tx) / zoom; val cy = (hostH / 2 - ty) / zoom
            val z = (zoom * minOf(localW / remoteW, localH / hostH)).coerceIn(.05f, 20f)
            pane.zoom = z; pane.tx = localW / 2 - cx * z; pane.ty = localH / 2 - cy * z
        }
        board.sceneChanged()
    }

    private fun applyLocks() {
        val colors = HashMap<String, Int>()
        locks.forEach { (item, owner) -> if (owner != me.id) colors[item] = (peers[owner]?.profile?.color ?: hostProfile?.color ?: NAVY) }
        board.foreignLocks = colors
        if (board.selected.removeAll(colors.keys)) board.onSelection()
        board.invalidate()
    }

    fun ownerOf(itemId: String): CollabProfile? {
        val owner = locks[itemId] ?: return null
        if (owner == me.id) return null
        return peers[owner]?.profile ?: if (owner == hostProfile?.id) hostProfile else null
    }

    private val tick = object : Runnable {
        override fun run() {
            if (status == "ended") return
            val now = SystemClock.uptimeMillis()
            if (joined) {
                if (dirty) flush()
                sendPresence(now)
                val view = viewSignature()
                if (view != lastView) {
                    lastView = view
                    if (isHost) { val m = viewMessage(); connections.forEach { (id, c) -> if (peers[id]?.role != COLLABORATE) c.send(m) } }
                    else if (role == CONTROL) hostConnection?.send(viewMessage())
                }
            }
            if (now - lastPing > 4000) {
                lastPing = now
                val ping = JsonObject().apply { addProperty("t", "ping") }
                val wall = System.currentTimeMillis()
                if (isHost) connections.values.toList().forEach { c -> if (wall - c.lastHeard > 45_000) c.close("timeout") else c.send(ping) }
                else hostConnection?.let { c -> if (status == "joined" && wall - c.lastHeard > 45_000) c.close("timeout") else c.send(ping) }
            }
            if (peers.values.any { it.touching || now - it.activityAt < HALO_MILLIS || now - it.viewAt < HALO_MILLIS }) board.invalidate()
            main.postDelayed(this, 80)
        }
    }

    fun everyone(): List<CollabPeer> = peers.values.toList()
    fun pageIndex(id: String) = store.lesson.pages.indexOfFirst { it.id == id }
    fun hostPeer(): CollabPeer? = peers.values.firstOrNull { it.isHost }
}
