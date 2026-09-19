package com.vuravision.classroom

import android.app.Dialog
import android.content.Context
import android.os.*
import android.view.Gravity
import android.widget.*
import kotlin.math.*
import kotlin.random.Random

class GameEngine(val key: String, private val random: Random = Random.Default) {
    val scores = IntArray(2)
    val answers = arrayOfNulls<Int>(2)
    val times = LongArray(2) { Long.MAX_VALUE }
    var round = 0
        private set

    var question = ""
        private set

    var options = listOf<String>()
        private set

    var correct = 0
        private set

    var resolved = false
        private set

    var finished = false
        private set

    var start = 0L
        private set

    var go = 0L
        private set

    var result = ""
        private set

    val cells = IntArray(9) { -1 }
    var turn = 0
        private set

    fun next(now: Long) {
        if (finished) return
        if (key == "tictac") {
            cells.fill(-1)
            turn = 0
        }
        round++
        answers.fill(null)
        times.fill(Long.MAX_VALUE)
        resolved = false
        result = ""
        start = now
        go = now + if (key == "reaction") random.nextLong(1500, 4501) else 0
        when (key) {
            "math_race" -> {
                val a = random.nextInt(2, 20)
                val b = random.nextInt(2, 20)
                val sum = a + b
                question = "$a + $b = ?"
                val values =
                    listOf(sum, sum + random.nextInt(1, 5), sum - random.nextInt(1, 5))
                        .shuffled(random)
                options = values.map { it.toString() }
                correct = values.indexOf(sum)
            }
            "bigger" -> {
                val a = random.nextInt(1, 100)
                var b = random.nextInt(1, 100)
                if (a == b) b++
                options = listOf(a.toString(), b.toString())
                question = "?"
                correct = if (a > b) 0 else 1
            }
            "even_odd" -> {
                val n = random.nextInt(1, 100)
                question = n.toString()
                options = listOf("even", "odd")
                correct = n % 2
            }
            "rps" -> {
                options = listOf("rock", "paper", "scissors")
                question = "choose"
            }
            else -> {
                options = listOf("tap")
                question = if (key == "timing") "3.000 s" else "tap"
            }
        }
    }

    fun answer(player: Int, choice: Int, now: Long) {
        if (player !in 0..1 || resolved || finished || key == "tictac") return
        tick(now)
        if (resolved) return
        if (key == "tap_race") {
            scores[player]++
            return
        }
        if (answers[player] != null) return
        answers[player] = choice
        times[player] = now
        if (key == "reaction" && now < go) {
            scores[player]--
            times[player] = Long.MAX_VALUE
        }
        if (answers.all { it != null }) resolve()
    }

    fun cell(index: Int) {
        if (key != "tictac" || resolved || index !in 0..8 || cells[index] != -1) return
        cells[index] = turn
        val win = winner(cells)
        if (win >= 0) {
            scores[win]++
            result = "player${win+1}"
            resolved = true
            finishIfNeeded()
        } else if (cells.none { it < 0 }) {
            result = "tie"
            resolved = true
            finishIfNeeded()
        } else turn = 1 - turn
    }

    fun tick(now: Long) {
        if (resolved || finished) return
        if (key == "tap_race" && now - start >= 15000) {
            resolved = true
            finished = true
            result = leader()
        } else if (
            key != "tictac" && key != "tap_race" && now - go >= if (key == "timing") 5000 else 10000
        )
            resolve()
    }

    private fun resolve() {
        if (resolved) return
        resolved = true
        var win = -1
        when (key) {
            "rps" -> {
                if (answers[0] != null && answers[1] != null) {
                    val a = answers[0]!!
                    val b = answers[1]!!
                    if (a != b) win = if ((a - b + 3) % 3 == 1) 0 else 1
                }
            }
            "reaction" -> {
                win =
                    when {
                        times[0] == times[1] -> -1
                        times[0] < times[1] -> 0
                        else -> 1
                    }
            }
            "timing" -> {
                val errors =
                    times.map {
                        if (it == Long.MAX_VALUE) Long.MAX_VALUE else abs(it - start - 3000)
                    }
                win =
                    when {
                        errors[0] == errors[1] -> -1
                        errors[0] < errors[1] -> 0
                        else -> 1
                    }
            }
            else -> {
                val good = (0..1).filter { answers[it] == correct }
                win = good.minByOrNull { times[it] } ?: -1
            }
        }
        if (win >= 0) scores[win]++
        result = if (win >= 0) "player${win+1}" else "tie"
        finishIfNeeded()
    }

    private fun finishIfNeeded() {
        if (round >= 10 || key == "tictac") finished = true
    }

    fun leader() =
        when {
            scores[0] > scores[1] -> "player1"
            scores[1] > scores[0] -> "player2"
            else -> "tie"
        }

    companion object {
        fun winner(c: IntArray): Int {
            val lines =
                arrayOf(
                    intArrayOf(0, 1, 2),
                    intArrayOf(3, 4, 5),
                    intArrayOf(6, 7, 8),
                    intArrayOf(0, 3, 6),
                    intArrayOf(1, 4, 7),
                    intArrayOf(2, 5, 8),
                    intArrayOf(0, 4, 8),
                    intArrayOf(2, 4, 6),
                )
            return lines
                .firstOrNull { c[it[0]] >= 0 && c[it[0]] == c[it[1]] && c[it[1]] == c[it[2]] }
                ?.let { c[it[0]] } ?: -1
        }
    }
}

object Games {
    private val keys =
        listOf("reaction", "tap_race", "math_race", "bigger", "even_odd", "timing", "tictac", "rps")

    fun show(context: Context) {
        val d = Dialog(context, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        val root =
            context.column().apply {
                setBackgroundColor(PAPER)
                fitsSystemWindows = true
                pad(20)
            }
        val head = context.row()
        head.addView(
            context.label(context.s("games"), 28f, NAVY, true),
            LinearLayout.LayoutParams(0, -2, 1f),
        )
        head.addView(context.button(context.s("close")) { d.dismiss() })
        root.addView(head)
        root.addView(context.label(context.s("games_intro"), 15f, MUTED))
        val list = context.column()
        keys.forEachIndexed { i, key ->
            val card =
                context.row().apply {
                    pad(10)
                    background = rounded(android.graphics.Color.WHITE, 16f)
                }
            card.addView(context.label("${i+1}", 24f, ORANGE, true))
            val words = context.column()
            words.addView(context.label(context.s(key), 20f, NAVY, true))
            words.addView(
                context.label(
                    context.s(
                        "rule_" +
                            when (key) {
                                "tap_race" -> "tap"
                                "math_race" -> "math"
                                "even_odd" -> "evenodd"
                                else -> key
                            }
                    ),
                    13f,
                    MUTED,
                )
            )
            card.addView(words, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(
                context.button(context.s("start"), true) {
                    d.dismiss()
                    open(context, key)
                }
            )
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 5, 0, 5) })
        }
        root.addView(
            ScrollView(context).apply { addView(list) },
            LinearLayout.LayoutParams(-1, 0, 1f),
        )
        d.setContentView(root)
        d.show()
    }

    fun open(context: Context, key: String) {
        val d = Dialog(context, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        val handler = Handler(Looper.getMainLooper())
        var game = GameEngine(key)
        var face = false
        var started = false
        val root =
            context.column().apply {
                setBackgroundColor(PAPER)
                fitsSystemWindows = true
                pad(14)
            }
        val header = context.row()
        val heading = context.label(context.s(key), 23f, NAVY, true)
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)
        val score = context.label("0 : 0", 27f, NAVY, true).apply { gravity = Gravity.CENTER }
        root.addView(score)
        val area = context.column()
        val prompts = mutableListOf<TextView>()
        root.addView(area, LinearLayout.LayoutParams(-1, 0, 1f))
        val action = context.button(context.s("start"), true) {}
        root.addView(action)
        fun render() {
            val now = SystemClock.elapsedRealtime()
            score.text =
                "${context.s("player1")}   ${game.scores[0]} : ${game.scores[1]}   ${context.s("player2")}    ·    ${context.s("round")} ${game.round}"
            area.removeAllViews()
            prompts.clear()
            if (!started) {
                area.addView(
                    context
                        .label(
                            context.s(
                                "rule_" +
                                    when (key) {
                                        "tap_race" -> "tap"
                                        "math_race" -> "math"
                                        "even_odd" -> "evenodd"
                                        else -> key
                                    }
                            ),
                            24f,
                        )
                        .apply { gravity = Gravity.CENTER },
                    LinearLayout.LayoutParams(-1, -1),
                )
                action.text = context.s("start")
                return
            }
            action.text =
                context.s(if (game.finished) "restart" else if (game.resolved) "next" else "reset")
            if (game.resolved) {
                area.addView(
                    context
                        .label(
                            "${context.s("winner")}: ${context.s(if(game.finished)game.leader()else game.result)}",
                            24f,
                            TEAL,
                            true,
                        )
                        .apply { gravity = Gravity.CENTER }
                )
                if (key == "rps")
                    area.addView(
                        context
                            .label(
                                game.answers
                                    .mapIndexed { i, a ->
                                        "${i+1}: ${a?.let{context.s(game.options[it])}?:"—"}"
                                    }
                                    .joinToString("    "),
                                18f,
                            )
                            .apply { gravity = Gravity.CENTER }
                    )
            }
            if (key == "tictac") {
                area.addView(
                    context.label("${context.s("turn")}: ${context.s("player${game.turn+1}")}", 18f)
                )
                repeat(3) { r ->
                    val row = context.row()
                    repeat(3) { col ->
                        val i = r * 3 + col
                        row.addView(
                            context
                                .button(
                                    when (game.cells[i]) {
                                        0 -> "×"
                                        1 -> "○"
                                        else -> "·"
                                    }
                                ) {
                                    game.cell(i)
                                    render()
                                }
                                .apply {
                                    textSize = 36f
                                    isEnabled = !game.resolved && game.cells[i] < 0
                                },
                            LinearLayout.LayoutParams(0, -1, 1f),
                        )
                    }
                    area.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))
                }
                return
            }
            val players =
                LinearLayout(context).apply {
                    orientation = if (face) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                }
            for (player in 0..1) {
                val col =
                    context.column().apply {
                        pad(16)
                        gravity = Gravity.CENTER
                        background =
                            rounded(
                                if (player == 0) 0xffe9f1ef.toInt() else 0xffffeee3.toInt(),
                                24f,
                            )
                        if (face && player == 0) rotation = 180f
                    }
                col.addView(
                    context.label(
                        context.s("player${player+1}"),
                        14f,
                        if (player == 0) TEAL else ORANGE,
                        true,
                    )
                )
                val prompt =
                    when {
                        key == "reaction" -> context.s(if (now < game.go) "wait" else "go")
                        key == "tap_race" ->
                            "${((15000-(now-game.start)).coerceAtLeast(0)+999)/1000} s"
                        else -> context.s(game.question)
                    }
                col.addView(
                    context.label(prompt, 30f, NAVY, true).apply {
                        gravity = Gravity.CENTER
                        prompts.add(this)
                    }
                )
                if (game.answers[player] != null && !game.resolved)
                    col.addView(context.label(context.s("choice_locked"), 16f))
                val opts = context.row()
                game.options.forEachIndexed { i, value ->
                    opts.addView(
                        context
                            .button(context.s(value), true) {
                                game.answer(player, i, SystemClock.elapsedRealtime())
                                if (game.resolved) render()
                                else {
                                    score.text =
                                        "${context.s("player1")}   ${game.scores[0]} : ${game.scores[1]}   ${context.s("player2")}    ·    ${context.s("round")} ${game.round}"
                                    if (key != "tap_race") {
                                        for (j in 0 until opts.childCount) opts
                                            .getChildAt(j)
                                            .isEnabled = false
                                        col.addView(context.label(context.s("choice_locked"), 16f))
                                    }
                                }
                            }
                            .apply {
                                isEnabled =
                                    !game.resolved && game.answers[player] == null ||
                                        key == "tap_race" && !game.resolved
                                minHeight = context.dp(64)
                            },
                        LinearLayout.LayoutParams(0, context.dp(78), 1f).apply {
                            setMargins(context.dp(3), 0, context.dp(3), 0)
                        },
                    )
                }
                col.addView(opts, LinearLayout.LayoutParams(-1, -2))
                players.addView(
                    col,
                    if (face) LinearLayout.LayoutParams(-1, 0, 1f).apply { setMargins(4, 4, 4, 4) }
                    else LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(4, 4, 4, 4) },
                )
            }
            area.addView(players, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        header.addView(
            context.button(context.s("layout")) {
                face = !face
                render()
            }
        )
        header.addView(context.button(context.s("close")) { d.dismiss() })
        action.setOnClickListener {
            if (!started || game.finished || !game.resolved) {
                game = GameEngine(key)
            }
            started = true
            game.next(SystemClock.elapsedRealtime())
            render()
        }
        val tick =
            object : Runnable {
                var lastState = ""

                override fun run() {
                    if (started) {
                        game.tick(SystemClock.elapsedRealtime())
                        val state = "${game.resolved}-${SystemClock.elapsedRealtime()>=game.go}"
                        if (key == "tap_race" && !game.resolved)
                            prompts.forEach {
                                it.text =
                                    "${((15000-(SystemClock.elapsedRealtime()-game.start)).coerceAtLeast(0)+999)/1000} s"
                            }
                        if (state != lastState) {
                            render()
                            lastState = state
                        }
                    }
                    handler.postDelayed(this, 50)
                }
            }
        render()
        d.setContentView(root)
        d.show()
        handler.post(tick)
        d.setOnDismissListener { handler.removeCallbacksAndMessages(null) }
    }
}
