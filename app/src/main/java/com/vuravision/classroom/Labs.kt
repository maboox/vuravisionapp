package com.vuravision.classroom

import android.app.Dialog
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.*
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import kotlin.math.*

object Physics {
    fun flight(v: Double, angle: Double, g: Double): Pair<Double, Double> {
        val r = angle * PI / 180
        return v * v * sin(2 * r) / g to 2 * v * sin(r) / g
    }

    fun period(length: Double, g: Double) = 2 * PI * sqrt(length / g)

    fun springPeriod(mass: Double, k: Double) = 2 * PI * sqrt(mass / k)

    fun quadratic(a: Double, b: Double, c: Double, x: Double) = a * x * x + b * x + c

    fun hydrogen(ph: Double) = 10.0.pow(-ph)
}

data class LabControl(val label: String, val min: Float, val max: Float, val value: Float)

object Labs {
    val keys =
        NativeLabs.keys + listOf("energy", "triangle_area", "statistics", "dilution", "trig")

    fun show(context: Context, insert: (Bitmap) -> Unit) {
        val d = Dialog(context, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        val root =
            context.column().apply {
                setBackgroundColor(PAPER)
                fitsSystemWindows = true
                pad(20)
            }
        val header = context.row()
        header.addView(
            context.label(context.s("lab"), 28f, NAVY, true),
            LinearLayout.LayoutParams(0, -2, 1f),
        )
        header.addView(context.button(context.s("close")) { d.dismiss() })
        root.addView(header)
        root.addView(context.label(context.s("lab_intro"), 15f, MUTED))
        val search=context.field(hintValue=context.tr("Search experiments","جست‌وجوی آزمایش‌ها")).apply{setSingleLine(true)}
        root.addView(search)
        val filters=context.row();root.addView(context.scrollRow(filters))
        val topicPicker=Spinner(context)
        val topicNames=listOf(context.tr("All lesson topics","همهٔ موضوع‌های درس"))+LabCatalog.topics.map{LabCatalog.topicName(context,it)}
        topicPicker.adapter=ArrayAdapter(context,android.R.layout.simple_spinner_dropdown_item,topicNames)
        topicPicker.contentDescription=context.tr("Lesson topic","موضوع درس");root.addView(topicPicker)
        val count=context.label("",13f,MUTED);root.addView(count)
        val cards=mutableListOf<Pair<String,View>>()
        var subject="all"
        fun applyFilter(){
            val query=search.text.toString().trim()
            val chosen=topicPicker.selectedItemPosition-1
            var found=0
            cards.forEach{(key,card)->
                val topic=LabCatalog.topic(key)
                val matches=(subject=="all" || topic.subject==subject) && (chosen<0 || topic===LabCatalog.topics[chosen]) &&
                    (query.isEmpty() || listOf(context.s(key),context.s("ex_$key"),key,topic.en,topic.fa,LabCatalog.name(context,topic.subject)).any{it.contains(query,true)})
                card.visibility=if(matches)View.VISIBLE else View.GONE;if(matches)found++
            }
            count.text=context.tr("$found experiments","$found آزمایش")
            for(i in 0 until filters.childCount){val b=filters.getChildAt(i);b.alpha=if(b.tag==subject)1f else .55f}
        }
        (listOf("all")+LabCatalog.subjects).forEach{key->
            filters.addView(context.button(if(key=="all")context.tr("All subjects","همهٔ درس‌ها")else LabCatalog.name(context,key)){subject=key;applyFilter()}.apply{tag=key})
        }
        val list = context.column()
        val columns=when{context.resources.configuration.screenWidthDp<620->1;context.resources.configuration.screenWidthDp<1200->2;else->3}
        keys.chunked(columns).forEach { chunk ->
            val row = context.row()
            chunk.forEachIndexed { i, key ->
                val cardContent =
                    context.column().apply {
                        pad(18)
                        addView(context.label("${keys.indexOf(key)+1}".padStart(2,'0') + "  /  LAB", 11f, TEAL, true))
                        addView(LabView(context,key,controls(key).map{it.value}.toFloatArray()).apply{running=false},LinearLayout.LayoutParams(-1,context.dp(108)))
                        val topic=LabCatalog.topic(key)
                        addView(context.label(LabCatalog.name(context,topic.subject)+" · "+LabCatalog.topicName(context,topic),12f,TEAL))
                        addView(context.label(context.s(key), 21f, NAVY, true))
                        addView(context.label(context.s("ex_$key"), 13f, MUTED).apply{maxLines=3})
                    }
                val card = context.card(cardContent, cornerRadius=20, elevation=2).apply {
                    isClickable=true
                    isFocusable=true
                    setOnClickListener {
                        d.dismiss()
                        open(context, key, insert)
                    }
                }
                cards.add(key to card)
                row.addView(
                    card,
                    LinearLayout.LayoutParams(0,-2,1f).apply {
                        setMargins(context.dp(7), context.dp(7), context.dp(7), context.dp(7))
                    },
                )
            }
            list.addView(row)
        }
        root.addView(
            ScrollView(context).apply { addView(list) },
            LinearLayout.LayoutParams(-1, 0, 1f),
        )
        search.addTextChangedListener(object:TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){applyFilter()}
            override fun afterTextChanged(s:Editable?){}
        })
        topicPicker.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{
            override fun onItemSelected(parent:AdapterView<*>?,v:View?,position:Int,id:Long){applyFilter()}
            override fun onNothingSelected(parent:AdapterView<*>?){}
        }
        applyFilter()
        d.setContentView(root)
        d.show()
    }

    fun controls(key: String) =
        if(key in NativeLabs.keys)NativeLabs.controls(key)else if(key in ExtraLabs.keys)ExtraLabs.controls(key)else when (key) {
            "projectile" ->
                listOf(
                    LabControl("v (m/s)", 5f, 50f, 22f),
                    LabControl("θ (°)", 5f, 85f, 45f),
                    LabControl("g (m/s²)", 1f, 20f, 9.8f),
                )
            "pendulum" ->
                listOf(
                    LabControl("L (m)", .2f, 4f, 1.5f),
                    LabControl("g (m/s²)", 1f, 20f, 9.8f),
                    LabControl("θ₀ (°)", 2f, 20f, 12f),
                )
            "spring" ->
                listOf(
                    LabControl("m (kg)", .1f, 5f, 1f),
                    LabControl("k (N/m)", 1f, 50f, 12f),
                    LabControl("A (m)", .1f, 2f, 1f),
                )
            "waves" ->
                listOf(
                    LabControl("n", 1f, 6f, 2f),
                    LabControl("f (Hz)", .1f, 3f, 1f),
                    LabControl("A", .1f, 1f, .7f),
                )
            "quadratic" ->
                listOf(
                    LabControl("a", -3f, 3f, 1f),
                    LabControl("b", -5f, 5f, 0f),
                    LabControl("c", -5f, 5f, 0f),
                )
            "trig" -> listOf(LabControl("θ (°)", 0f, 360f, 45f))
            "ph" -> listOf(LabControl("pH", 0f, 14f, 7f))
            else -> listOf(LabControl("p(heads)", 0f, 1f, .5f), LabControl("N", 10f, 1000f, 100f))
        }

    fun open(context: Context, key: String, insert: (Bitmap) -> Unit) {
        val d = Dialog(context, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        val c =
            context.column().apply {
                setBackgroundColor(PAPER)
                fitsSystemWindows = true
                pad(14)
            }
        val controls = controls(key)
        val view = LabView(context, key, controls.map { it.value }.toFloatArray())
        val header = context.row()
        header.addView(
            context.label(context.s(key), 24f, NAVY, true),
            LinearLayout.LayoutParams(0, -2, 1f),
        )
        header.addView(
            context.button(context.s("add_board"), true) {
                insert(view.snapshot())
                d.dismiss()
            }
        )
        header.addView(context.button(context.s("close")) { d.dismiss() })
        c.addView(header)
        c.addView(view, LinearLayout.LayoutParams(-1, 0, 1f))
        val sliders=context.column()
        val sliderViews=mutableListOf<SeekBar>()
        controls.forEachIndexed { i, control ->
            val col = context.column()
            val label = context.label("${control.label} = ${fmt(control.value)}", 13f)
            col.addView(label)
            col.addView(
                SeekBar(context).apply {
                    sliderViews.add(this)
                    max=1000
                    progress =
                        ((control.value - control.min) / (control.max - control.min) * 1000).toInt()
                    setOnSeekBarChangeListener(
                        object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                                var v = control.min + (control.max - control.min) * p / 1000
                                if (control.label in listOf("n", "N")) v = round(v)
                                view.values[i] = v
                                label.text = "${control.label} = ${fmt(v)}"
                                if(u)view.time=0.0
                                view.invalidate()
                            }

                            override fun onStartTrackingTouch(s: SeekBar?) {}

                            override fun onStopTrackingTouch(s: SeekBar?) {}
                        }
                    )
                }
            )
            sliders.addView(col, LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,context.dp(3),0,context.dp(3))})
        }
        sliders.addView(context.label(context.s("ex_$key"), 13f, MUTED))
        c.addView(ScrollView(context).apply{addView(sliders);isFillViewport=false},LinearLayout.LayoutParams(-1,context.dp(if(context.resources.configuration.screenWidthDp<620)190 else 235)))
        val r = context.row()
        r.addView(
            context.button(context.s("pause")) {
                view.running = !view.running
                view.invalidate()
            }
        )
        r.addView(
            context.button(context.s("reset")) {
                view.time=0.0
                controls.forEachIndexed{i,v->sliderViews[i].progress=((v.value-v.min)/(v.max-v.min)*1000).toInt()}
                view.resample()
                view.invalidate()
            }
        )
        c.addView(r)
        d.setContentView(c)
        d.show()
        d.setOnDismissListener { view.running = false }
    }

    private fun fmt(v: Float) = "%.2f".format(java.util.Locale.US, v)
}

class LabView(context: Context, val key: String, val values: FloatArray) : View(context) {
    var running = true
    var time = 0.0
    private var last = 0L
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var sampleSeed=42
    private var trials = DoubleArray(1000) { Math.random() }

    fun resample() {
        sampleSeed++
        trials = DoubleArray(1000) { Math.random() }
    }

    fun snapshot(): Bitmap {
        val b = Bitmap.createBitmap(1200, 684, Bitmap.Config.ARGB_8888)
        render(Canvas(b), 1200, 684)
        return b
    }

    override fun onDraw(c: Canvas) {
        val now = SystemClock.elapsedRealtime()
        if (last != 0L && running) time += (now - last).coerceAtMost(50) / 1000.0
        last = now
        render(c, width, height)
        if(isAttachedToWindow&&running&&(key in listOf("projectile","pendulum","spring","waves") || key.removePrefix("native_") in listOf("orbit","pendulum","projectile","spring","collision","standing","gas","states","diffusion","reaction","halflife","waves","interference","atom","periodic","doppler","heat","electrolysis","flame","equilibrium","freefall","lightclock","bonding","sorting","osmosis","markov","circuit","incline")))postInvalidateOnAnimation()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        last = 0
        invalidate()
    }

    private fun line(
        c: Canvas,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        color: Int = NAVY,
        width: Float = 3f,
    ) {
        p.color = color
        p.strokeWidth = width
        p.style = Paint.Style.STROKE
        c.drawLine(x1, y1, x2, y2, p)
    }

    private fun text(
        c: Canvas,
        s: String,
        x: Float,
        y: Float,
        size: Float = 22f,
        color: Int = NAVY,
    ) {
        p.color = color
        p.textSize = size
        p.style = Paint.Style.FILL
        p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        c.drawText(s, x, y, p)
    }

    private fun circle(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        p.color = color
        p.style = Paint.Style.FILL
        c.drawCircle(x, y, r, p)
    }

    private fun path(c: Canvas, points: List<Pair<Float, Float>>, color: Int = ORANGE) {
        p.color = color
        p.strokeWidth = 4f
        p.style = Paint.Style.STROKE
        val path = Path()
        points.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
        c.drawPath(path, p)
    }

    private fun f(v: Double) = "%.2f".format(java.util.Locale.US, v)

    private fun render(c: Canvas, w: Int, h: Int) {
        c.drawColor(Color.WHITE)
        c.save()
        val scale = min(w / 1000f, h / 570f)
        c.translate((w - 1000 * scale) / 2, (h - 570 * scale) / 2)
        c.scale(scale, scale)
        text(c, context.s(key), 32f, 44f, 24f)
        if(key in NativeLabs.keys){NativeLabs.draw(c,key,values,time,sampleSeed,context);c.restore();return}
        val a = values[0].toDouble()
        val b = values.getOrElse(1) { 0f }.toDouble()
        val d = values.getOrElse(2) { 0f }.toDouble()
        when (key) {
            "projectile" -> {
                val (range, duration) = Physics.flight(a, b, d)
                val peak = a * a * sin(b * PI / 180).pow(2) / (2 * d)
                val sx=min(820/range,320/peak)
                val sy=sx
                line(c, 80f, 460f, 940f, 460f)
                line(c, 80f, 460f, 80f, 100f)
                val points =
                    (0..120).map { i ->
                        val t = duration * i / 120
                        ((80 + a * cos(b * PI / 180) * t * sx).toFloat()) to
                            ((460 - (a * sin(b * PI / 180) * t - .5 * d * t * t) * sy).toFloat())
                    }
                path(c, points)
                val t = time % duration
                circle(
                    c,
                    (80 + a * cos(b * PI / 180) * t * sx).toFloat(),
                    (460 - (a * sin(b * PI / 180) * t - .5 * d * t * t) * sy).toFloat(),
                    12f,
                    TEAL,
                )
                text(
                    c,
                    "R = ${f(range)} m     H = ${f(peak)} m     T = ${f(duration)} s",
                    80f,
                    520f,
                )
                text(c, "x (m)", 864f, 494f, 16f)
                text(c, "y (m)", 30f, 92f, 16f)
            }
            "pendulum" -> {
                val period = Physics.period(a, b)
                val angle = d * PI / 180 * cos(2 * PI * time / period)
                val length=95+a*58
                val x=(500+length*sin(angle)).toFloat()
                val y=(110+length*cos(angle)).toFloat()
                line(c, 340f, 110f, 660f, 110f, MUTED, 6f)
                line(c, 500f, 110f, x, y, NAVY, 4f)
                circle(c, x, y, 32f, ORANGE)
                circle(c, 500f, 110f, 8f, TEAL)
                text(c, "T = 2π√(L/g) = ${f(period)} s", 320f, 485f, 26f)
            }
            "spring" -> {
                val period = Physics.springPeriod(a, b)
                val x = d * cos(2 * PI * time / period)
                val end = (560 + x * 110).toFloat()
                line(c, 140f, 160f, 140f, 430f, MUTED, 8f)
                path(
                    c,
                    (0..40).map { i ->
                        (140 + (end - 180) * i / 40) to
                            (295 + if (i in 1..39) if (i % 2 == 0) 22f else -22f else 0f)
                    },
                    TEAL,
                )
                p.style = Paint.Style.FILL
                p.color = ORANGE
                c.drawRoundRect(end - 40, 250f, end + 60, 340f, 16f, 16f, p)
                text(c, "x = ${f(x)} m     F = ${f(-b*x)} N", 240f, 450f)
                text(c, "T = 2π√(m/k) = ${f(period)} s", 240f, 492f)
            }
            "waves" -> {
                line(c, 90f, 300f, 910f, 300f, MUTED, 2f)
                path(
                    c,
                    (0..400).map { i ->
                        (90 + i * 820f / 400) to
                            (300 - d * 150 * sin(a * PI * i / 400) * cos(2 * PI * b * time))
                                .toFloat()
                    },
                )
                for (i in 0..a.toInt()) circle(c, (90 + 820 * i / a).toFloat(), 300f, 7f, TEAL)
                text(c, "y = A sin(nπx/L) cos(2πft)     n = ${a.toInt()}", 210f, 500f)
            }
            "quadratic" -> {
                for (i in -5..5) {
                    line(c, 500 + i * 70f, 90f, 500 + i * 70f, 470f, 0xffe5ebea.toInt(), 1f)
                    line(c, 100f, 280 + i * 35f, 900f, 280 + i * 35f, 0xffe5ebea.toInt(), 1f)
                }
                line(c, 100f, 280f, 900f, 280f, MUTED, 2f)
                line(c, 500f, 90f, 500f, 470f, MUTED, 2f)
                c.save()
                c.clipRect(100f, 90f, 900f, 470f)
                path(
                    c,
                    (0..400).map { i ->
                        val x = (i - 200) / 35.0
                        (500 + x * 70).toFloat() to
                            (280 - Physics.quadratic(a, b, d, x) * 35).toFloat()
                    },
                )
                c.restore()
                text(c, "y = ${f(a)}x² + ${f(b)}x + ${f(d)}", 230f, 525f)
                text(c, "x", 913f, 282f, 16f)
                text(c, "y", 508f, 94f, 16f)
            }
            "trig" -> {
                val r = a * PI / 180
                val x = (370 + 165 * cos(r)).toFloat()
                val y = (285 - 165 * sin(r)).toFloat()
                p.color = 0xffdfe8e6.toInt()
                p.strokeWidth = 3f
                p.style = Paint.Style.STROKE
                c.drawCircle(370f, 285f, 165f, p)
                line(c, 160f, 285f, 575f, 285f, MUTED, 1f)
                line(c, 370f, 85f, 370f, 480f, MUTED, 1f)
                line(c, 370f, 285f, x, y, NAVY, 4f)
                line(c, 370f, 285f, x, 285f, ORANGE, 6f)
                line(c, x, 285f, x, y, TEAL, 6f)
                circle(c, x, y, 10f, NAVY)
                text(c, "cos θ = ${f(cos(r))}", 635f, 255f, 25f, ORANGE)
                text(c, "sin θ = ${f(sin(r))}", 635f, 305f, 25f, TEAL)
                text(c, "θ = ${f(a)}°", 635f, 355f)
            }
            "ph" -> {
                for (i in 0..139) {
                    p.color = Color.HSVToColor(floatArrayOf(i * 260f / 139, .68f, .88f))
                    p.style = Paint.Style.FILL
                    c.drawRect(80 + i * 6f, 220f, 86 + i * 6f, 300f, p)
                }
                val x = (80 + a * 60).toFloat()
                line(c, x, 200f, x, 324f, NAVY, 5f)
                text(c, "0", 80f, 355f)
                text(c, "7", 494f, 355f)
                text(c, "14", 890f, 355f)
                text(
                    c,
                    "[H⁺] = 10⁻ᵖᴴ = ${"%.2e".format(java.util.Locale.US,Physics.hydrogen(a))} mol/L",
                    210f,
                    435f,
                )
                text(c, "pH = ${f(a)}", 380f, 150f, 34f)
            }
            "coin" -> {
                val n = b.toInt().coerceIn(1, 1000)
                val heads = trials.take(n).count { it < a }
                val observed = heads.toFloat() / n
                p.style = Paint.Style.FILL
                p.color = TEAL
                c.drawRoundRect(260f, 440 - observed * 330, 400f, 440f, 10f, 10f, p)
                p.color = ORANGE
                c.drawRoundRect(600f, 440 - a.toFloat() * 330, 740f, 440f, 10f, 10f, p)
                text(c, "${heads} / $n", 270f, 490f)
                text(c, "p = ${f(a)}", 610f, 490f)
                text(
                    c,
                    "Observed = ${f(observed.toDouble())}     Expected = ${f(a)}",
                    235f,
                    88f,
                    23f,
                )
            }
            else -> ExtraLabs.draw(c,key,values,context,time)
        }
        c.restore()
    }
}
