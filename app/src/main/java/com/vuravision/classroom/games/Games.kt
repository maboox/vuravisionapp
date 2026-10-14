package com.vuravision.classroom.games

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.*
import android.view.Gravity
import android.view.View
import android.widget.*
import kotlin.math.*
import kotlin.random.Random
import com.vuravision.classroom.ui.Gallery
import com.vuravision.classroom.ui.IconCatalog
import com.vuravision.classroom.ui.MUTED
import com.vuravision.classroom.ui.NAVY
import com.vuravision.classroom.ui.ORANGE
import com.vuravision.classroom.ui.PAPER
import com.vuravision.classroom.R
import com.vuravision.classroom.ui.TEAL
import com.vuravision.classroom.ui.button
import com.vuravision.classroom.ui.card
import com.vuravision.classroom.ui.column
import com.vuravision.classroom.ui.dp
import com.vuravision.classroom.ui.label
import com.vuravision.classroom.ui.navigationHeading
import com.vuravision.classroom.ui.pad
import com.vuravision.classroom.rename
import com.vuravision.classroom.ui.rounded
import com.vuravision.classroom.ui.row
import com.vuravision.classroom.ui.s
import com.vuravision.classroom.ui.scrollRow
import com.vuravision.classroom.ui.tr
import com.vuravision.classroom.ui.uiScale
import com.vuravision.classroom.board.reset

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

    /** Items 68 and 81: the teacher's own (or the AI's) questions, played as a two-player race, in order. */
    var custom: List<QuizQuestion>? = null

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
        custom?.takeIf { it.isNotEmpty() }?.let { list -> val q = list[(round - 1) % list.size]; question = q.prompt; options = q.options; correct = q.correct; return }
        if(key in QuizCatalog.keys) { val q=QuizCatalog.question(key,random);question=q.prompt;options=q.options;correct=q.correct;return }
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
        if(choice !in options.indices)return
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
                win = if(good.size==2 && times[0]==times[1])-1 else good.minByOrNull { times[it] } ?: -1
            }
        }
        if (win >= 0) scores[win]++
        result = if (win >= 0) "player${win+1}" else "tie"
        finishIfNeeded()
    }

    private fun finishIfNeeded() {
        if (round >= (custom?.size?.coerceIn(1, 30) ?: 10) || key == "tictac") finished = true
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
    val keys =
        NativeGames.keys + QuizCatalog.keys

    fun show(context: Context, back:(()->Unit)?=null) {
        val g=Gallery(context,context.s("games"),context.s("games_intro"),back)
        g.search.hint=context.tr("Search games","جستجوی بازی‌ها")
        var category="all"
        val cards=keys.mapIndexed{i,key->key to card(context,key){g.dialog.dismiss();open(context,key){show(context,back)}}}
        val filter=g.chipRow()
        lateinit var refresh:()->Unit
        refresh={
            val q=g.query.lowercase()
            val shown=cards.filter{(key,_)->
                (category=="all"||(category=="duel"&&key in NativeGames.keys)||(category=="quiz"&&key in QuizCatalog.keys))&&
                    (context.s(key)+" "+context.s(rule(key))).lowercase().contains(q)
            }.map{it.second}
            g.show(shown);g.count.text=context.tr("${shown.size} games","${shown.size} بازی")
            g.chips(filter,listOf("all" to context.tr("All","همه"),"duel" to context.tr("Two-player","دونفره"),"quiz" to context.tr("Math & puzzles","ریاضی و معما")),category){category=it;refresh()}
        }
        g.onSearch{refresh()};refresh();g.dialog.show()
    }
    private fun rule(key:String)="rule_"+when(key){"tap_race"->"tap";"math_race","custom_quiz"->"math";"even_odd"->"evenodd";else->if(key in QuizCatalog.keys)"math" else key}
    /** A card per game: who plays (two players or the class), the name, the rule in short, and a sample question for quizzes. */
    private fun card(context:Context,key:String,open:()->Unit):View{
        val duel=key in NativeGames.keys
        val content=context.column().apply{
            setPadding(context.dp(16),context.dp(14),context.dp(16),context.dp(16))
            addView(context.row().apply{
                addView(android.widget.ImageView(context).apply{setImageResource(IconCatalog.resource(if(duel)"games" else "math",true));setColorFilter(Color.WHITE)
                    background=rounded(if(duel)TEAL else NAVY,context.dp(14).toFloat());setPadding(context.dp(10),context.dp(10),context.dp(10),context.dp(10))},LinearLayout.LayoutParams(context.dp(48),context.dp(48)))
                addView(context.label(if(duel)context.tr("Two players","دو نفره")else context.tr("Whole class","کل کلاس"),12f,if(duel)TEAL else NAVY,true).apply{setPaddingRelative(context.dp(10),0,0,0)})
            })
            addView(context.label(context.s(key),19f,NAVY,true).apply{setPadding(context.dp(4),context.dp(10),context.dp(4),0)})
            addView(context.label(context.s(rule(key)),13f,MUTED).apply{maxLines=3;ellipsize=android.text.TextUtils.TruncateAt.END})
            if(key in QuizCatalog.keys||key=="math_race"){val example=GameEngine(key,Random(42)).apply{next(0)};addView(context.label(example.question,16f,TEAL,true))}
        }
        return context.card(content,cornerRadius=20,elevation=1).apply{isClickable=true;isFocusable=true;contentDescription=context.s(key);setOnClickListener{open()}}
    }

    /** A class quiz as a duel: same rules and screen as the math race, with the quiz's own questions. */
    fun duel(context: Context, title: String, questions: List<QuizQuestion>) = open(context, "custom_quiz", questions, title, null)
    fun open(context: Context, key: String, back:(()->Unit)?=null) = open(context, key, null, null, back)
    private fun open(context: Context, key: String, custom:List<QuizQuestion>?, title:String?, back:(()->Unit)?) {
        if(key in NativeGames.keys){NativeGames.open(context,key,back);return}
        val d = Dialog(context, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        val handler = Handler(Looper.getMainLooper())
        var game = GameEngine(key).apply { this.custom = custom }
        var face = context.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT
        var started = false
        var namePause:Long?=null;var pausedDuration=0L
        fun gameNow()=(namePause?:SystemClock.elapsedRealtime())-pausedDuration
        val nameLabels=mutableListOf<TextView>()
        var redraw:()->Unit={}
        var soundedRound=-1
        fun rename(i:Int){namePause=SystemClock.elapsedRealtime();GamePlayers.edit(context,i){pausedDuration+=SystemClock.elapsedRealtime()-(namePause?:SystemClock.elapsedRealtime());namePause=null;redraw()}}
        val root =
            context.column().apply {
                setBackgroundColor(PAPER)
                fitsSystemWindows = true
                pad(14)
            }
        val header = context.row()
        root.addView(context.navigationHeading(title?:context.s(key),back?.let{action->{d.dismiss();action()}}))
        val score = context.label("0 : 0", 27f, NAVY, true).apply { gravity = Gravity.CENTER }
        val names=context.row();repeat(2){i->val label=context.label(GamePlayers.name(context,i),16f,if(i==0)TEAL else ORANGE,true).apply{gravity=Gravity.CENTER;minimumHeight=context.dp(48);setOnClickListener{rename(i)}};nameLabels.add(label);names.addView(label,LinearLayout.LayoutParams(0,-2,1f))};root.addView(names)
        root.addView(score)
        root.addView(context.label(context.s("game_layout_help"),13f,MUTED))
        val area = context.column()
        val prompts = mutableListOf<TextView>()
        root.addView(area, LinearLayout.LayoutParams(-1, 0, 1f))
        val action = context.button(context.s("start"), true) {}
        root.addView(action)
        fun render() {
            nameLabels.forEachIndexed{i,v->v.text=GamePlayers.name(context,i)}
            val now = gameNow()
            score.text =
                "${game.scores[0]} : ${game.scores[1]}    ·    ${context.s("round")} ${game.round}"
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
                                        else -> if(key in QuizCatalog.keys) "math" else key
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
                // One sound per finished round: the class hears who got it without reading the screen.
                if(soundedRound!=game.round){soundedRound=game.round;Sounds.play(context,if(game.finished)Sounds.Cue.WIN else if(game.result=="tie")Sounds.Cue.TICK else Sounds.Cue.CORRECT)}
                area.addView(
                    context
                        .label(
                            "${context.s("winner")}: ${GamePlayers.display(context,if(game.finished)game.leader()else game.result)}",
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
                    context.label("${context.s("turn")}: ${GamePlayers.name(context,game.turn)}", 18f)
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
                    isMotionEventSplittingEnabled=true
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
                    }
                col.addView(
                    context.label(
                        GamePlayers.name(context,player),
                        14f,
                        if (player == 0) TEAL else ORANGE,
                        true,
                    ).apply{setOnClickListener{rename(player)}}
                )
                val prompt =
                    when {
                        key == "reaction" -> context.s(if (now < game.go) "wait" else "go")
                        key == "tap_race" ->
                            "${((15000-(now-game.start)).coerceAtLeast(0)+999)/1000} s"
                        else -> context.s(game.question)
                    }
                col.addView(
                    // Read from the back of the room: the question is the largest text on screen.
                    context.label(prompt, if(prompt.length>32)24f else 34f, NAVY, true).apply {
                        gravity = Gravity.CENTER
                        prompts.add(this)
                    }
                )
                if (game.answers[player] != null && !game.resolved)
                    col.addView(context.label(context.s("choice_locked"), 16f))
                val verticalOptions=game.options.size>2
                val opts = if(verticalOptions)context.column()else context.row()
                game.options.forEachIndexed { i, value ->
                    opts.addView(
                        context
                            .button(context.s(value), true) {
                                game.answer(player, i, gameNow())
                                if (game.resolved) render()
                                else {
                                    score.text =
                                        "${game.scores[0]} : ${game.scores[1]}    ·    ${context.s("round")} ${game.round}"
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
                                minHeight = context.dp(64);textSize=22f*context.uiScale()
                            },
                        (if (verticalOptions) LinearLayout.LayoutParams(-1, context.dp(72))
                        else LinearLayout.LayoutParams(0, context.dp(96), 1f)).apply {
                            setMargins(context.dp(4), context.dp(4), context.dp(4), context.dp(4))
                        },
                    )
                }
                col.addView(opts, LinearLayout.LayoutParams(-1, -2))
                players.addView(
                    ScrollView(context).apply {
                        isFillViewport=true
                        isVerticalScrollBarEnabled=false
                        addView(col)
                        if(face&&player==0)rotation=180f
                    },
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
                game = GameEngine(key).apply { this.custom = custom }
            }
            started = true
            game.next(gameNow())
            render()
        }
        val tick =
            object : Runnable {
                var lastState = ""

                override fun run() {
                    if (started) {
                        game.tick(gameNow())
                        val state = "${game.resolved}-${gameNow()>=game.go}"
                        if (key == "tap_race" && !game.resolved)
                            prompts.forEach {
                                it.text =
                                    "${((15000-(gameNow()-game.start)).coerceAtLeast(0)+999)/1000} s"
                            }
                        if (state != lastState) {
                            render()
                            lastState = state
                        }
                    }
                    handler.postDelayed(this, 50)
                }
            }
        redraw=::render
        render()
        // Layout and Close stay at the bottom: on a tall panel the top edge is out of reach.
        root.addView(context.scrollRow(header))
        d.setContentView(root)
        d.show()
        handler.post(tick)
        d.setOnDismissListener { handler.removeCallbacksAndMessages(null) }
    }
}
