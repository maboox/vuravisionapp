package com.vuravision.classroom.room

import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import com.vuravision.classroom.ui.button
import com.vuravision.classroom.ui.s
import com.vuravision.classroom.board.center

/**
 * Answers from students' phones (item 83), on the classroom network only, with no app and no internet:
 * the panel serves one small page; phones open it from a QR, see the current question and tap A–D.
 * One vote per phone per question (a random id kept in the phone's browser); a phone may change its vote
 * until the teacher reveals the answer. The address carries a random token and the server stops with the quiz.
 */
class PollServer : NanoHTTPD("0.0.0.0", 0) {
    val token = ByteArray(9).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    data class Question(val id: Int, val text: String, val options: List<String>)
    @Volatile var question: Question? = null
        private set
    @Volatile var revealed: Int? = null
    private val votes = ConcurrentHashMap<String, Int>()
    private val seen = ConcurrentHashMap.newKeySet<String>()

    fun ask(id: Int, text: String, options: List<String>) {
        if (question?.id == id) return
        votes.clear(); revealed = null; question = Question(id, text.take(1000), options.take(4).map { it.take(300) })
    }
    fun clear() { question = null; votes.clear(); revealed = null }
    /** Votes per option for the current question. */
    fun tally(): IntArray { val n = question?.options?.size ?: 0; val t = IntArray(n); votes.values.forEach { if (it in 0 until n) t[it]++ }; return t }
    /** Phones that opened the page during this quiz. */
    val people get() = seen.size

    override fun serve(session: IHTTPSession): Response {
        val base = "/$token"
        return when {
            session.uri == base && session.method == Method.GET -> html(page())
            session.uri == "$base/state" -> { voter(session.parameters["v"]?.firstOrNull())?.let { if (seen.size < 500) seen.add(it) }; json(state()) }
            session.uri == "$base/vote" && session.method == Method.POST -> vote(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }.apply { addHeader("Cache-Control", "no-store"); addHeader("X-Content-Type-Options", "nosniff") }
    }
    private fun voter(raw: String?) = raw?.takeIf { it.matches(Regex("[a-z0-9]{8,40}")) }
    private fun vote(session: IHTTPSession): Response {
        try { session.parseBody(HashMap()) } catch (_: Exception) { return bad() }
        val p = session.parameters
        val id = voter(p["v"]?.firstOrNull()) ?: return bad()
        val q = question ?: return bad()
        val choice = p["c"]?.firstOrNull()?.toIntOrNull() ?: return bad()
        if (p["q"]?.firstOrNull()?.toIntOrNull() != q.id || choice !in q.options.indices || revealed != null) return bad()
        if (!votes.containsKey(id) && votes.size >= 500) return bad()
        seen.add(id); votes[id] = choice
        return json(state())
    }
    private fun state() = JSONObject().apply {
        val q = question
        put("id", q?.id ?: -1); put("text", q?.text ?: ""); put("options", JSONArray(q?.options ?: emptyList<String>()))
        put("revealed", revealed ?: -1); put("total", votes.size)
    }
    private fun bad() = newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Bad request")
    private fun json(o: JSONObject) = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", o.toString())
    private fun html(s: String) = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", s)
    private fun page() = """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>VuraVision</title><style>
body{margin:0;font:20px system-ui,sans-serif;background:#f6f5fa;color:#20194f;padding:20px}
h1{font-size:24px;line-height:1.4;margin:8px 0 20px}
button{display:flex;align-items:center;gap:14px;width:100%;margin:10px 0;padding:18px;border:2px solid #e4e2ef;border-radius:18px;background:#fff;font:inherit;color:inherit;text-align:start}
button b{flex:none;width:44px;height:44px;border-radius:12px;color:#fff;display:grid;place-items:center}
button.on{border-color:#20194f;box-shadow:0 0 0 3px #20194f inset}
button.right{background:#dcfce7;border-color:#16a34a}
#wait{color:#625e85;text-align:center;margin-top:40vh}
</style></head><body><div id="wait">… منتظر سؤال · Waiting for the question</div><div id="q" hidden><h1 id="t" dir="auto"></h1><div id="o"></div></div>
<script>
const base=location.pathname,tints=["#2563eb","#dc2626","#16a34a","#f59e0b"];
let me=localStorage.getItem("vv")||Math.random().toString(36).slice(2,14)+Date.now().toString(36);localStorage.setItem("vv",me);
let cur=-1,mine=-1;
async function tick(){try{const s=await (await fetch(base+"/state?v="+me,{cache:"no-store"})).json();draw(s)}catch(e){}setTimeout(tick,1200)}
function draw(s){
 document.getElementById("wait").hidden=s.id>=0;document.getElementById("q").hidden=s.id<0;if(s.id<0)return;
 if(s.id!==cur){cur=s.id;mine=-1}
 document.getElementById("t").textContent=s.text;const o=document.getElementById("o");o.innerHTML="";
 s.options.forEach((t,i)=>{const b=document.createElement("button");b.dir="auto";
  b.innerHTML='<b style="background:'+tints[i]+'">'+"ABCD"[i]+'</b><span></span>';b.querySelector("span").textContent=t;
  if(i===mine)b.className="on";if(s.revealed===i)b.className="right";b.disabled=s.revealed>=0;
  b.onclick=async()=>{mine=i;await fetch(base+"/vote?v="+me+"&q="+s.id+"&c="+i,{method:"POST"});draw(s)};o.appendChild(b)})}
tick();
</script></body></html>"""
}
