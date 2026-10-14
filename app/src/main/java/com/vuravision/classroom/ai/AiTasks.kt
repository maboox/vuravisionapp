package com.vuravision.classroom.ai

import android.content.Context
import android.graphics.RectF
import org.json.JSONObject

/**
 * What the board asks the AI, and how the answers are read back. Prompts ask for short plain text or small JSON
 * objects, because answers end up written on a classroom board: no Markdown, no LaTeX, nothing a student can't read.
 */
object AiTasks {
    data class Check(val correct: Boolean, val summary: String, val mistakes: List<Pair<RectF, String>>)
    data class Steps(val steps: List<String>, val answer: String)
    data class Question(val text: String, val options: List<String>, val answer: Int)
    data class Node(val title: String, val children: List<Node>)
    data class Summary(val title: String, val points: List<String>, val homework: String)
    data class Group(val title: String, val notes: List<Int>)

    private fun persian(c: Context) = c.resources.configuration.locales[0].language == "fa"
    fun language(c: Context) = if (persian(c))"Persian (Farsi)" else "English"
    fun audience(grade: String) = when (grade) {
        "primary" -> "primary school students (ages 7 to 11)"
        "high" -> "high school students (grades 10 to 12)"
        else -> "middle school students (grades 7 to 9)"
    }

    /**
     * Teachers saw answers in English for Persian boards, and tasks about "handwriting" instead of the subject (a quiz on
     * Asian populations asked what handwriting is for). So: read first, work on the meaning, answer in its language.
     */
    fun system(c: Context, grade: String) = """
        You help a teacher during a lesson, for ${audience(grade)}. The image is a picture of part of the teacher's
        interactive whiteboard: handwriting, typed text, drawings, graphs or formulas.
        First read everything written in it. What is written is the lesson content: a topic, a question, an exercise or
        a student's work. Always work on its meaning and subject. Never treat the handwriting itself as the topic, never
        talk about handwriting, the image, translation or "identifying the text", unless the task asks for that.
        Language: answer in the language the content is written in (Persian content gets a Persian answer, English
        content an English answer). If the content has no words, answer in ${language(c)}. JSON keys stay in English.
        Be accurate and kind. If nothing readable is there, say so in one short sentence in that language.
        Never use Markdown, LaTeX commands, bullet symbols or emoji. Write mathematics with plain Unicode such as x², √, ≤, π, ÷.
    """.trimIndent()

    fun explain() = "Explain the subject written here so that a student understands it: what it means and why. At most 90 words in 2 to 4 short sentences."
    fun simplify() = "Say what is written here again in simpler words for younger students. Same language as the content. At most 70 words."
    fun translate() = "Translate what is written here: Persian content into English, any other language into Persian (Farsi). Reply with the translation only, without notes."
    fun check() = """
        Check whether this work is right: calculations, answers, facts and reasoning. Handwriting style and small spelling
        slips are not mistakes, unless the content is itself a spelling or grammar exercise. If the content is only a
        question or a topic (no answer to check), set "correct" to true and use the summary to say what a correct answer needs.
        Reply with JSON only: {"correct": true or false, "summary": "one short sentence",
        "mistakes": [{"box": [left, top, right, bottom], "note": "the fix in at most 12 words"}]}.
        Each box frames one mistake in image coordinates from 0 to 1 (0,0 is the top-left corner). At most 4 mistakes.
    """.trimIndent()
    fun steps() = """
        The content is an exercise or a question for the class (in mathematics, science, language or any subject).
        Solve it, or answer it, step by step, the way a teacher would on the board. Do not describe reading,
        identifying or translating the text: start with the first real step of the solution. Reply with JSON only:
        {"steps": ["step 1", "step 2", ...], "answer": "final answer or conclusion"}. 2 to 8 steps, each at most 20 words.
    """.trimIndent()
    fun transcribe() = """
        Transcribe the handwriting exactly as written, keeping line breaks; fix nothing. Write mathematics with plain Unicode.
        Reply with JSON only: {"text": "..."}.
    """.trimIndent()
    fun quiz(count: Int) = """
        Write $count multiple-choice questions for students about the subject of this content (for example, if it says
        "populations of Asian countries", ask about the populations of Asian countries). Never ask about handwriting,
        the board or the image. Each question has 4 short options and exactly one correct option; vary the correct position. Reply with JSON only: {"questions": [{"q": "...", "options": ["...","...","...","..."], "answer": index of the correct option from 0}]}.
    """.trimIndent()
    fun quizFromTopic(topic: String, count: Int) = "The topic is: \"${topic.take(300)}\". There is no image; use the topic. " + quiz(count)
    fun mindMap() = """
        Turn the subject of this content into a mind map of its main ideas (not about the handwriting or the image). Reply with JSON only: {"title": "...", "children": [{"title": "...", "children": [{"title": "..."}]}]}.
        3 to 5 main branches, at most 3 sub-branches each; every title at most 4 words.
    """.trimIndent()
    fun mindMapFromTopic(topic: String) = "The topic is: \"${topic.take(300)}\". There is no image; use the topic. " + mindMap()
    fun summary() = """
        The image shows the pages of today's lesson board, in order. Summarize the lesson for students.
        Reply with JSON only: {"title": "...", "points": ["...", ...], "homework": "one suggested practice task"}. 3 to 6 points, each at most 18 words.
    """.trimIndent()

    /** Text only: the notes' words are sent, never a picture of the board. */
    fun groups(notes: List<String>) = "Sort these sticky notes from a class brainstorm into 2 to 6 groups by meaning. " +
        "Give each group a title of at most 3 words.\nNotes:\n" + notes.mapIndexed { i, t -> "${i + 1}. " + t.replace('\n', ' ').trim().take(200) }.joinToString("\n") +
        "\nReply with JSON only: {\"groups\": [{\"title\": \"...\", \"notes\": [note numbers]}]}. Every note number appears in exactly one group."
    fun lab(name: String, description: String, values: String) = """
        The image shows the "$name" simulation in a classroom. What it models: $description
        Current settings: $values.
        In at most 80 words: say what the class is seeing right now and why, then ask the class one question to predict what happens if a setting changes.
    """.trimIndent()

    fun parseCheck(o: JSONObject): Check {
        val list = o.optJSONArray("mistakes")
        val mistakes = (0 until (list?.length() ?: 0)).mapNotNull { i ->
            val m = list!!.optJSONObject(i) ?: return@mapNotNull null
            val b = m.optJSONArray("box") ?: return@mapNotNull null
            if (b.length() < 4)return@mapNotNull null
            val v = (0..3).map { b.optDouble(it, Double.NaN).toFloat() }
            if (v.any { !it.isFinite() })return@mapNotNull null
            // Some models answer in pixels or percent; anything above 1 is scaled back into 0..1.
            val scale = v.maxOf { it }.let {
                if (it > 100f) {
                    1000f
                } else if (it > 1.5f) {
                    100f
                } else {
                    1f
                }
            }
            val r = RectF(v[0] / scale, v[1] / scale, v[2] / scale, v[3] / scale)
            r.sort()
            r.left = r.left.coerceIn(0f, 1f)
            r.right = r.right.coerceIn(0f, 1f)
            r.top = r.top.coerceIn(0f, 1f)
            r.bottom = r.bottom.coerceIn(0f, 1f)
            if (r.width() <= 0f || r.height() <= 0f)return@mapNotNull null
            r to m.optString("note").trim().take(120)
        }.take(4)
        return Check(o.optBoolean("correct", mistakes.isEmpty()), o.optString("summary").trim().take(240), mistakes)
    }
    fun parseSteps(o: JSONObject): Steps {
        val list = o.optJSONArray("steps") ?: throw AiException("format")
        val steps = (0 until list.length()).map { list.optString(it).trim() }.filter { it.isNotEmpty() }.take(10)
        if (steps.isEmpty())throw AiException("format")
        return Steps(steps, o.optString("answer").trim())
    }
    fun parseQuiz(o: JSONObject): List<Question> {
        val list = o.optJSONArray("questions") ?: o.optJSONArray("items") ?: throw AiException("format")
        val questions = (0 until list.length()).mapNotNull { i ->
            val q = list.optJSONObject(i) ?: return@mapNotNull null
            val options = q.optJSONArray("options")?.let { a -> (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotEmpty() } }.orEmpty().take(4)
            val answer = q.optInt("answer", -1)
            if (q.optString("q").isBlank() || options.size < 2 || answer !in options.indices || options.distinct().size != options.size) {
                null
            } else {
                Question(q.optString("q").trim(), options, answer)
            }
        }
        if (questions.isEmpty())throw AiException("format")
        return questions
    }
    fun parseMindMap(o: JSONObject): Node {
        fun node(j: JSONObject, depth: Int): Node = Node(
            j.optString("title").trim().take(60),
            if (depth >= 2)emptyList() else j.optJSONArray("children")?.let { a -> (0 until minOf(a.length(), 6)).mapNotNull { a.optJSONObject(it)?.let { c -> node(c, depth + 1) } }.filter { it.title.isNotEmpty() } }.orEmpty()
        )
        val root = node(o, 0)
        if (root.title.isEmpty() || root.children.isEmpty())throw AiException("format")
        return root
    }

    /** Each note lands in one group only; notes the AI forgot go to a last, untitled group, so nothing is lost. */
    fun parseGroups(o: JSONObject, count: Int): List<Group> {
        val list = o.optJSONArray("groups") ?: throw AiException("format")
        val used = mutableSetOf<Int>()
        val groups = (0 until list.length()).mapNotNull { i ->
            val g = list.optJSONObject(i) ?: return@mapNotNull null
            val a = g.optJSONArray("notes") ?: g.optJSONArray("items") ?: return@mapNotNull null
            val notes = (0 until a.length()).map { a.optInt(it, 0) - 1 }.filter { it in 0 until count && used.add(it) }
            if (notes.isEmpty())null else Group(g.optString("title").trim().take(40), notes)
        }.take(8)
        if (groups.isEmpty())throw AiException("format")
        val rest = (0 until count).filter { it !in groups.flatMap { g -> g.notes } }
        return if (rest.isEmpty())groups else groups + Group("", rest)
    }
    fun parseSummary(o: JSONObject): Summary {
        val list = o.optJSONArray("points") ?: throw AiException("format")
        val points = (0 until list.length()).map { list.optString(it).trim() }.filter { it.isNotEmpty() }.take(8)
        if (points.isEmpty())throw AiException("format")
        return Summary(o.optString("title").trim().take(80), points, o.optString("homework").trim().take(200))
    }
}
