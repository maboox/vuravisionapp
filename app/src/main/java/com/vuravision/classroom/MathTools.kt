package com.vuravision.classroom

import kotlin.math.*
import net.objecthunter.exp4j.ExpressionBuilder

class MathTools {
    fun normalize(input: String): String {
        require(input.length <= 512) { "Expression too long" }
        var s =
            input
                .lowercase()
                .replace(" ", "")
                .removePrefix("y=")
                .replace("−", "-")
                .replace("×", "*")
                .replace("÷", "/")
                .replace("²", "^2")
                .replace("³", "^3")
                .replace("π", "pi")
        "۰۱۲۳۴۵۶۷۸۹".forEachIndexed { i, c -> s = s.replace(c, ('0'.code + i).toChar()) }
        return s.replace(Regex("(\\d|\\))(?=x|\\()"), "$1*")
    }

    fun compile(input: String): (Double) -> Double {
        val e = ExpressionBuilder(normalize(input)).variable("x").build()
        return { x -> e.setVariable("x", x).evaluate() }
    }

    fun evaluate(input: String): Double =
        compile(input)(0.0).also { require(it.isFinite()) { "Undefined result" } }

    fun solve(input: String): String {
        val s = normalize(input).replace("*x", "x")
        val match =
            Regex(
                    "([+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)?)x([+-](?:\\d+(?:\\.\\d+)?|\\.\\d+))?=([+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+))"
                )
                .matchEntire(s) ?: error("Use ax+b=c, for example 2x+4=12")
        val a =
            when (val v = match.groupValues[1]) {
                "",
                "+" -> 1.0
                "-" -> -1.0
                else -> v.toDouble()
            }
        val b = match.groupValues[2].toDoubleOrNull() ?: 0.0
        val c = match.groupValues[3].toDouble()
        require(a != 0.0) { "Coefficient must be nonzero" }
        return "x = ${format((c-b)/a)}"
    }

    companion object {
        fun format(v: Double) =
            if (abs(v - round(v)) < 1e-9) round(v).toLong().toString()
            else "%.6g".format(java.util.Locale.US, v)
    }
}
