package com.vuravision.classroom

import android.graphics.RectF
import com.google.gson.*
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * Field-level synchronisation of the board pages. Every device keeps a shadow of the
 * last state it shared; a local change is sent as the difference to that shadow.
 *
 * Per-view state is never shared: the current page, pane zoom/scroll and the active
 * layer stay local, so people can work on different parts of the same lesson.
 */
object CollabSync {
    val gson = Gson()
    private val itemFields = fields(Item::class.java)
    private val pageFields = fields(Page::class.java)
    private val lessonFields = fields(Lesson::class.java)
    private val VIEW_FIELDS = setOf("zoom", "tx", "ty")

    private fun fields(type: Class<*>): Map<String, Field> =
        type.declaredFields.filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
            .onEach { it.isAccessible = true }.associateBy { it.name }

    class PageShadow(var meta: JsonObject, var order: List<String>, val items: HashMap<String, Item>)
    class Shadow(var lessonMeta: JsonObject, var pageOrder: List<String>, val pages: LinkedHashMap<String, PageShadow>)

    /** Result of a local diff: the message body, ids touched on each page, and assets it references. */
    class Diff(val body: JsonObject, val touched: Map<String, Set<String>>, val assets: Set<String>, val pendingKeys: Set<String>)

    fun lessonMeta(lesson: Lesson) = JsonObject().apply {
        addProperty("title", lesson.title)
        add("customColors", gson.toJsonTree(lesson.customColors.orEmpty()))
    }

    fun pageMeta(page: Page) = JsonObject().apply {
        addProperty("background", page.background)
        add("layers", gson.toJsonTree(page.layers))
        add("panes", JsonArray().also { array ->
            page.panes.forEach { pane -> array.add(gson.toJsonTree(pane).asJsonObject.also { o -> VIEW_FIELDS.forEach { o.remove(it) } }) }
        })
    }

    private fun pageShadow(page: Page) = PageShadow(pageMeta(page), page.items.map { it.id }, HashMap(page.items.associate { it.id to it.deepCopy() }))

    fun capture(lesson: Lesson) = Shadow(lessonMeta(lesson), lesson.pages.map { it.id }, LinkedHashMap(lesson.pages.associate { it.id to pageShadow(it) }))

    /** Page as sent to others when it is new; keeps the sender's view as a sensible start. */
    fun pageJson(page: Page): JsonObject = gson.toJsonTree(page).asJsonObject

    fun itemJson(item: Item): JsonObject = gson.toJsonTree(item).asJsonObject

    private fun changedFields(old: JsonObject, now: JsonObject): JsonObject {
        val out = JsonObject()
        now.entrySet().forEach { (k, v) -> if (old.get(k) != v) out.add(k, v) }
        old.keySet().forEach { k -> if (!now.has(k)) out.add(k, JsonNull.INSTANCE) }
        return out
    }

    /** Compares [lesson] with [shadow], updates the shadow and returns what changed (or null). */
    fun diff(lesson: Lesson, shadow: Shadow): Diff? {
        val body = JsonObject(); val touched = HashMap<String, MutableSet<String>>(); val assets = HashSet<String>(); val keys = HashSet<String>()
        val meta = lessonMeta(lesson)
        val metaChange = changedFields(shadow.lessonMeta, meta)
        if (metaChange.size() > 0) { body.add("lesson", metaChange); metaChange.keySet().forEach { keys.add("L:$it") }; shadow.lessonMeta = meta }
        val ids = lesson.pages.map { it.id }
        if (ids != shadow.pageOrder) {
            body.add("pages", gson.toJsonTree(ids))
            val fresh = JsonArray()
            lesson.pages.filter { it.id !in shadow.pages }.forEach { page ->
                fresh.add(pageJson(page)); keys.add("np:${page.id}")
                page.items.forEach { o -> if (o.asset.isNotEmpty()) assets.add(o.asset) }
                shadow.pages[page.id] = pageShadow(page)
            }
            if (fresh.size() > 0) body.add("newPages", fresh)
            shadow.pages.keys.retainAll(ids.toSet()); shadow.pageOrder = ids; keys.add("pages")
        }
        val patches = JsonArray()
        lesson.pages.forEach { page ->
            val ps = shadow.pages[page.id] ?: return@forEach
            val patch = JsonObject()
            val pm = pageMeta(page)
            val pageChange = changedFields(ps.meta, pm)
            if (pageChange.size() > 0) { patch.add("meta", pageChange); pageChange.keySet().forEach { keys.add("m:${page.id}:$it") }; ps.meta = pm }
            val put = JsonArray(); val set = JsonArray(); val seen = HashSet<String>(page.items.size)
            val pageTouched = touched.getOrPut(page.id) { HashSet() }
            page.items.forEach { item ->
                seen.add(item.id)
                val old = ps.items[item.id]
                if (old == null) {
                    put.add(itemJson(item)); pageTouched.add(item.id); keys.add("i:${item.id}")
                    if (item.asset.isNotEmpty()) assets.add(item.asset)
                    ps.items[item.id] = item.deepCopy()
                } else if (old != item) {
                    val f = changedFields(itemJson(old), itemJson(item))
                    if (f.size() > 0) {
                        set.add(JsonObject().apply { addProperty("id", item.id); add("f", f) })
                        f.keySet().forEach { keys.add("i:${item.id}:$it") }
                        pageTouched.add(item.id)
                        if (f.has("asset") && item.asset.isNotEmpty()) assets.add(item.asset)
                    }
                    ps.items[item.id] = item.deepCopy()
                }
            }
            val removed = ps.items.keys.filter { it !in seen }
            if (removed.isNotEmpty()) { patch.add("del", gson.toJsonTree(removed)); removed.forEach { ps.items.remove(it) } }
            val order = page.items.map { it.id }
            if (order != ps.order) {
                // Appending new objects or deleting needs no order; restacking or inserting below does.
                val before = ps.order.toHashSet()
                val restacked = ps.order.filter { it in seen } != order.filter { it in before }
                val added = order.filter { it !in before }
                val insertedBelow = added.isNotEmpty() && order.takeLast(added.size) != added
                if (restacked || insertedBelow) { patch.add("order", gson.toJsonTree(order)); keys.add("o:${page.id}") }
                ps.order = order
            }
            if (put.size() > 0) patch.add("put", put)
            if (set.size() > 0) patch.add("set", set)
            if (patch.size() > 0) { patch.addProperty("p", page.id); patches.add(patch) }
            if (pageTouched.isEmpty()) touched.remove(page.id)
        }
        if (patches.size() > 0) body.add("patches", patches)
        if (body.size() == 0) return null
        return Diff(body, touched, assets, keys)
    }

    /** Applies a received change. Fields in [pending] are local edits not yet confirmed and win locally. */
    fun apply(lesson: Lesson, body: JsonObject, pending: Set<String> = emptySet()): Set<String> {
        val touched = HashSet<String>()
        body.getAsJsonObject("lesson")?.let { assignFields(lesson, it, lessonFields, Lesson::class.java) { k -> "L:$k" !in pending } }
        val currentId = lesson.pages.getOrNull(lesson.current)?.id
        body.getAsJsonArray("pages")?.let { array ->
            if ("pages" in pending) return@let
            val ids = array.map { it.asString }
            val known = lesson.pages.associateBy { it.id }.toMutableMap()
            body.getAsJsonArray("newPages")?.forEach { json ->
                val page = runCatching { gson.fromJson(json, Page::class.java) }.getOrNull() ?: return@forEach
                if (page.id !in known) { sanitize(page); known[page.id] = page; page.items.forEach { touched.add(it.id) } }
            }
            val ordered = ids.mapNotNull { known[it] }
            val localOnly = lesson.pages.filter { it.id !in ids && "np:${it.id}" in pending }
            val result = (ordered + localOnly).ifEmpty { lesson.pages.toList() }
            lesson.pages.clear(); lesson.pages.addAll(result.take(200))
        }
        body.getAsJsonArray("patches")?.forEach { element ->
            val patch = element.asJsonObject
            val page = lesson.pages.firstOrNull { it.id == patch.get("p")?.asString } ?: return@forEach
            patch.getAsJsonObject("meta")?.let { meta ->
                val views = page.panes.map { Triple(it.zoom, it.tx, it.ty) }
                val active = page.activeLayerId
                assignFields(page, meta, pageFields, Page::class.java) { k -> "m:${page.id}:$k" !in pending }
                page.panes.forEachIndexed { i, pane -> views.getOrNull(i)?.let { (z, x, y) -> pane.zoom = z; pane.tx = x; pane.ty = y } }
                page.activeLayerId = active
            }
            patch.getAsJsonArray("del")?.let { array ->
                val ids = array.map { it.asString }.toSet()
                if (page.items.removeAll { it.id in ids && "i:${it.id}" !in pending }) touched.addAll(ids)
            }
            patch.getAsJsonArray("put")?.forEach { json ->
                val fresh = runCatching { gson.fromJson(json, Item::class.java) }.getOrNull() ?: return@forEach
                val existing = page.items.firstOrNull { it.id == fresh.id }
                if (existing == null) page.items.add(fresh)
                else assignFields(existing, json.asJsonObject, itemFields, Item::class.java) { k -> "i:${fresh.id}:$k" !in pending }
                touched.add(fresh.id)
            }
            patch.getAsJsonArray("set")?.forEach { json ->
                val o = json.asJsonObject; val id = o.get("id")?.asString ?: return@forEach
                val existing = page.items.firstOrNull { it.id == id } ?: return@forEach
                assignFields(existing, o.getAsJsonObject("f") ?: return@forEach, itemFields, Item::class.java) { k -> "i:$id:$k" !in pending }
                touched.add(id)
            }
            patch.getAsJsonArray("order")?.let { array ->
                if ("o:${page.id}" in pending) return@let
                val order = array.map { it.asString }
                val index = order.withIndex().associate { it.value to it.index }
                val sorted = page.items.withIndex().sortedWith(compareBy({ index[it.value.id] ?: (order.size + it.index) }, { it.index })).map { it.value }
                page.items.clear(); page.items.addAll(sorted)
            }
            sanitize(page)
        }
        if (lesson.pages.isEmpty()) lesson.pages.add(Page())
        lesson.current = lesson.pages.indexOfFirst { it.id == currentId }.takeIf { it >= 0 } ?: lesson.current.coerceIn(lesson.pages.indices)
        return touched
    }

    /** Copies only the named JSON fields into [target], keeping its identity (renderer caches stay valid). */
    private fun <T : Any> assignFields(target: T, fieldsJson: JsonObject, fields: Map<String, Field>, type: Class<T>, allow: (String) -> Boolean) {
        val allowed = JsonObject()
        fieldsJson.entrySet().forEach { (k, v) -> if (k in fields && allow(k)) allowed.add(k, v) }
        if (allowed.size() == 0) return
        val fresh = runCatching { gson.fromJson(allowed, type) }.getOrNull() ?: return
        allowed.keySet().forEach { k ->
            val f = fields.getValue(k)
            val value = f.get(fresh)
            if (value == null && f.type.isPrimitive) return@forEach
            f.set(target, value)
        }
    }

    /** Remote data must never leave references that the board cannot draw. */
    fun sanitize(page: Page) {
        if (page.layers.isEmpty()) page.layers.add(Layer(id = "base", name = "Layer 1"))
        if (page.panes.isEmpty()) page.panes.add(Pane())
        while (page.panes.size > 4) page.panes.removeAt(page.panes.lastIndex)
        if (page.layers.none { it.id == page.activeLayerId }) page.activeLayerId = page.layers.last().id
        val layers = page.layers.map { it.id }.toSet(); val seen = HashSet<String>()
        page.items.removeAll { !seen.add(it.id) }
        page.items.forEach { o ->
            if (o.layerId !in layers) o.layerId = page.layers.first().id
            if (o.pane !in page.panes.indices) o.pane = 0
            if (o.pageCount < 1) o.pageCount = 1
            o.pdfPage = o.pdfPage.coerceIn(0, o.pageCount - 1)
        }
    }

    /** Bounds of items on [page] that were touched, for the fading "worked here" halo. */
    fun bounds(page: Page, ids: Set<String>): RectF? {
        val items = page.items.filter { it.id in ids }
        return if (items.isEmpty()) null else contentBounds(items)
    }

    fun assetsIn(lesson: Lesson): Set<String> = lesson.pages.flatMap { p -> p.items.map { it.asset } }.filter { it.isNotEmpty() }.toSet()
}
