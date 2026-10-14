package com.vuravision.classroom.voice

import com.vuravision.classroom.ui.s
import org.json.JSONArray
import org.json.JSONObject

/**
 * Voice commands (item 70): the assistant can act on the board, but only through this short, safe list. Each one
 * is reversible (Undo, or simply doing it again) and none deletes anything. The board is never edited freely.
 * The same list is declared to Gemini Live, OpenAI Realtime and the turn-based path (OpenRouter / AvalAI).
 */
object VoiceCommands {
    data class Call(val id: String, val name: String, val args: JSONObject)
    private class Param(val name: String, val type: String, val description: String, val choices: List<String> = emptyList())
    private class Command(val name: String, val description: String, val params: List<Param> = emptyList())
    private val commands = listOf(
        Command("go_to_page", "Move between board pages or add a page.", listOf(Param("where", "string", "next, previous or new", listOf("next", "previous", "new")))),
        Command("set_tool", "Switch the teacher's tool.", listOf(Param("tool", "string", "pen, highlighter, eraser or select", listOf("pen", "highlighter", "eraser", "select")))),
        Command("set_pen_color", "Change the pen color.", listOf(Param("color", "string", "ink color", listOf("black", "blue", "red", "green", "orange", "purple")))),
        Command("start_timer", "Show a countdown timer on the board.", listOf(Param("minutes", "integer", "1 to 60"))),
        Command("open", "Open a classroom tool.", listOf(Param("what", "string", "which tool", listOf("stopwatch", "spotlight", "laser", "name_picker", "labs", "games", "table", "class_quiz", "replay")))),
        Command("undo", "Undo the last change on the board.")
    )
    val names get() = commands.map { it.name }
    const val INSTRUCTION = "When the teacher asks you to do something on the board (next page, red pen, eraser, a five minute timer, open the spotlight…), call the matching function instead of describing it, then confirm in at most four words. Never call a function for a question about the lesson."
    private fun schema(c: Command, upper: Boolean): JSONObject {
        fun t(s: String) = if (upper)s.uppercase() else s
        val props = JSONObject()
        c.params.forEach { p -> props.put(p.name, JSONObject().put("type", t(p.type)).put("description", p.description).apply { if (p.choices.isNotEmpty())put("enum", JSONArray(p.choices)) }) }
        return JSONObject().put("type", t("object")).put("properties", props).put("required", JSONArray(c.params.map { it.name }))
    }

    /** Gemini Live: tools → functionDeclarations, OpenAPI types in upper case. */
    fun gemini() = JSONArray().put(JSONObject().put("functionDeclarations", JSONArray(commands.map { c -> JSONObject().put("name", c.name).put("description", c.description).apply { if (c.params.isNotEmpty())put("parameters", schema(c, true)) } })))

    /** OpenAI Realtime: flat function tools with JSON Schema. */
    fun openai() = JSONArray(commands.map { c -> JSONObject().put("type", "function").put("name", c.name).put("description", c.description).put("parameters", schema(c, false)) })

    /** For the turn-based path, which answers in JSON: the allowed commands written out for the model. */
    fun text() = commands.joinToString("; ") { c -> c.name + "(" + c.params.joinToString(", ") { p -> p.name + (if (p.choices.isNotEmpty())": " + p.choices.joinToString("|") else ": " + p.type) } + ")" }

    /** Rejects anything not on the list, and arguments outside their choices. */
    fun valid(call: Call): Boolean {
        val c = commands.firstOrNull { it.name == call.name } ?: return false
        return c.params.all { p ->
            when (p.type) {
                "integer" -> call.args.optInt(p.name, -1) in 1..60
                else -> call.args.optString(p.name).let { v -> v.isNotEmpty() && (p.choices.isEmpty() || v in p.choices) }
            }
        }
    }
}
