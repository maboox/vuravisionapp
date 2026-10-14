package com.vuravision.classroom.ai

import android.content.Context
import org.json.JSONObject

/**
 * What the board asks the AI, and how the answers are read back. Prompts ask for short plain text or small JSON
 * objects, because answers end up written on a classroom board: no Markdown, no LaTeX, nothing a student can't read.
 */
object AiTasks {
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

    /** Persian letters in the content mean a Persian answer; the app's own language only when there are no words. */
    fun replyLanguage(c: Context, text: String) = when {
        text.any { it in '\u0600'..'\u06ff' } -> "Persian (Farsi)"
        text.any { it.isLetter() } -> "English"
        else -> language(c)
    }

    /**
     * The board is read on the device first (offline handwriting recognition plus typed text) and only that text is
     * sent: a picture made models answer in English, guess Arabic, or write about "handwriting" instead of the subject.
     */
    fun system(c: Context, grade: String) = """
        You help a teacher during a lesson, for ${audience(grade)}. You receive the text written on the teacher's
        interactive whiteboard, read by handwriting recognition, so it may contain small recognition errors: read it
        for its meaning. It is lesson content: a topic, a question, an exercise or a student's work. Always work on its
        subject. Never talk about handwriting, recognition, images or translation unless the task asks for that.
        Be accurate and kind. Never use Markdown, LaTeX commands, bullet symbols or emoji. Write mathematics with
        plain Unicode such as x², √, ≤, π, ÷. JSON keys stay in English.
    """.trimIndent()

    /** The task with the board's text and the reply language stated outright. */
    fun on(c: Context, text: String, task: String) = task + "\n\nThe content written on the teacher's board:\n\"\"\"\n" + text.take(4000) + "\n\"\"\"\n" +
        "Write your whole answer, including every JSON value, in ${replyLanguage(c, text)}."

    /** Explain and check in one: the explanation, then a verdict line when there is something that can be right or wrong. */
    fun explain() = """
        Explain this to a student in 2 to 4 short sentences, at most 90 words: what it means and why.
        Then, only if it contains an answer, a calculation, a solution or a claim that can be right or wrong, add one
        last line that starts with ✓ if it is right, or with ✗ followed by the correct version.
        If it is a question without an answer, end instead with one short question that helps the class think.
    """.trimIndent()
    fun simplify() = "Say this again in simpler words for younger students. At most 70 words."
    fun translate() = "Translate this: Persian into English, any other language into Persian (Farsi). Reply with the translation only, without notes."
    fun steps() = """
        This is an exercise or a question for the class (mathematics, science, language or any subject).
        Solve it, or answer it, step by step, the way a teacher would on the board. Start with the first real step.
        Reply with JSON only: {"steps": ["step 1", "step 2", ...], "answer": "final answer or conclusion"}.
        2 to 8 steps, each at most 20 words.
    """.trimIndent()
    fun quiz(count: Int) = """
        Write $count multiple-choice questions for students about the subject of this content (for example, if it says
        "populations of Asian countries", ask about the populations of Asian countries). Never ask about handwriting,
        recognition or the board. Each question has 4 short options and exactly one correct option; vary the correct position. Reply with JSON only: {"questions": [{"q": "...", "options": ["...","...","...","..."], "answer": index of the correct option from 0}]}.
    """.trimIndent()
    fun quizFromTopic(c: Context, topic: String, count: Int) = on(c, topic.take(300), "The content is a topic. " + quiz(count))
    fun mindMap() = """
        Turn the subject of this content into a mind map of its main ideas (about the subject itself). Reply with JSON only: {"title": "...", "children": [{"title": "...", "children": [{"title": "..."}]}]}.
        3 to 5 main branches, at most 3 sub-branches each; every title at most 4 words.
    """.trimIndent()
    fun mindMapFromTopic(c: Context, topic: String) = on(c, topic.take(300), "The content is a topic. " + mindMap())
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
