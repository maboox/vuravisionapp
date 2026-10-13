package com.vuravision.classroom

import android.app.Dialog
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.*
import android.widget.*
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
    /**
     * Set by the board when an AI service is configured: (lab name, what it models, current settings, picture, answer).
     * The answer callback gets null on failure (the host has already told the teacher why).
     */
    var assistant:((String,String,String,Bitmap,(String?)->Unit)->Unit)?=null
    val keys =
        NativeLabs.keys + listOf("energy", "triangle_area", "statistics", "dilution", "trig", "periodic_table")

    fun show(context: Context, insert: (Bitmap) -> Unit) = show(context,insert,{})
    fun show(context:Context,insert:(Bitmap)->Unit,insertItem:(Item)->Unit)=show(context,insert,insertItem,null)
    fun show(context: Context, insert: (Bitmap) -> Unit, insertItem:(Item)->Unit, back:(()->Unit)?) {
        val g=Gallery(context,context.s("lab"),context.s("lab_intro"),back)
        g.search.hint=context.tr("Search experiments: pendulum, pH, probability…","جستجوی آزمایش: آونگ، pH، احتمال…")
        var subject="all";var topic="all";var level="all"
        val cards=keys.map{key->key to card(context,key){
            g.dialog.dismiss()
            if(key=="periodic_table")PeriodicTable.show(context,insertItem){show(context,insert,insertItem,back)}else open(context,key,insert){show(context,insert,insertItem,back)}
        }}
        val subjects=g.chipRow();val topics=g.chipRow();val levels=g.chipRow()
        lateinit var refresh:()->Unit
        refresh={
            val q=g.query
            val shown=cards.filter{(key,_)->
                val t=LabCatalog.topic(key)
                (subject=="all"||t.subject==subject)&&(topic=="all"||t.id==topic)&&(level=="all"||LabCatalog.level(key)==level)&&
                    (q.isEmpty()||listOf(context.s(key),context.s("ex_$key"),key,t.en,t.fa,LabCatalog.name(context,t.subject)).any{it.contains(q,true)})
            }.map{it.second}
            g.show(shown);g.count.text=context.tr("${shown.size} experiments","${shown.size} آزمایش")
            g.chips(subjects,listOf("all" to context.tr("All subjects","همهٔ درس‌ها"))+LabCatalog.subjects.map{it to LabCatalog.name(context,it)},subject){subject=it;topic="all";refresh()}
            // Topics only for the chosen subject: a few chips instead of a 14-item dropdown.
            topics.visibility=if(subject=="all")View.GONE else View.VISIBLE
            if(subject!="all")g.chips(topics,listOf("all" to context.tr("All topics","همهٔ موضوع‌ها"))+LabCatalog.topics.filter{it.subject==subject}.map{it.id to LabCatalog.topicName(context,it)},topic){topic=it;refresh()}
            g.chips(levels,listOf("all" to context.tr("All grades","همهٔ پایه‌ها"),"middle" to LabCatalog.levelName(context,"middle"),"high" to LabCatalog.levelName(context,"high")),level){level=it;refresh()}
        }
        g.onSearch{refresh()};refresh();g.dialog.show()
    }
    /** One catalog card: subject, topic and grade on top, a preview of the experiment, its name and what it teaches. */
    private fun card(context:Context,key:String,open:()->Unit):View{
        val topic=LabCatalog.topic(key)
        val content=context.column().apply{
            setPadding(context.dp(16),context.dp(14),context.dp(16),context.dp(16))
            addView(context.row().apply{
                addView(context.label(LabCatalog.name(context,topic.subject)+" · "+LabCatalog.topicName(context,topic),12f,TEAL,true),LinearLayout.LayoutParams(0,-2,1f))
                addView(context.label(LabCatalog.levelName(context,LabCatalog.level(key)),11f,MUTED).apply{background=rounded(SURFACE_VARIANT,context.dp(10).toFloat());setPadding(context.dp(8),context.dp(2),context.dp(8),context.dp(2))})
            })
            addView(LabView(context,key,controls(key).map{it.value}.toFloatArray()).apply{running=false;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},
                LinearLayout.LayoutParams(-1,context.dp(132)).apply{topMargin=context.dp(8);bottomMargin=context.dp(8)})
            addView(context.label(context.s(key),20f,NAVY,true))
            addView(context.label(context.s("ex_$key"),13f,MUTED).apply{maxLines=3;ellipsize=android.text.TextUtils.TruncateAt.END})
        }
        return context.card(content,cornerRadius=20,elevation=2).apply{isClickable=true;isFocusable=true;contentDescription=context.s(key);setOnClickListener{open()}}
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

    fun open(context:Context,key:String,insert:(Bitmap)->Unit)=open(context,key,insert,null)
    fun open(context: Context, key: String, insert: (Bitmap) -> Unit, back:(()->Unit)?) {
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
        c.addView(context.navigationHeading(context.s(key),back?.let{action->{d.dismiss();action()}}))
        header.removeViewAt(0)
        c.addView(view, LinearLayout.LayoutParams(-1, 0, 1f))
        val sliders=context.column()
        val sliderViews=mutableListOf<SeekBar>()
        controls.forEachIndexed { i, control ->
            val col = context.column()
            val label = context.label("${LabText.t(context,control.label)} = ${fmt(control.value)}", 13f)
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
                                if (integral(control)) v = round(v)
                                view.values[i] = v
                                label.text = "${LabText.t(context,control.label)} = ${fmt(v)}"
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
        // Item 54: drag across the picture itself to change the first setting (sideways) or the second (up/down).
        if(controls.isNotEmpty()){
            view.ranges=controls.map{it.min..it.max}
            view.dragHint=if(controls.size>1)context.tr("Drag on the picture: sideways for ${LabText.t(context,controls[0].label)}, up or down for ${LabText.t(context,controls[1].label)}",
                "روی تصویر بکشید: افقی برای ${LabText.t(context,controls[0].label)}، عمودی برای ${LabText.t(context,controls[1].label)}")
                else context.tr("Drag sideways on the picture for ${LabText.t(context,controls[0].label)}","برای ${LabText.t(context,controls[0].label)} روی تصویر افقی بکشید")
            view.onDrag={i,v->
                val control=controls[i];val value=if(integral(control))round(v)else v
                sliderViews[i].progress=((value-control.min)/(control.max-control.min)*1000).toInt()
                view.time=0.0;view.dragLabel="${LabText.t(context,control.label)} = ${fmt(view.values[i])}"
            }
        }
        sliders.addView(context.label(context.s("ex_$key"), 13f, MUTED))
        c.addView(ScrollView(context).apply{addView(sliders);isFillViewport=false},LinearLayout.LayoutParams(-1,context.dp(if(context.resources.configuration.screenWidthDp<620)190 else 235)))
        val r = context.row()
        // Play/pause names what a tap will do; slow motion lets the class follow fast motion (a collision, a falling ball).
        lateinit var pause:android.widget.Button
        pause=context.button(context.s("pause")) {
            view.running = !view.running
            pause.text=if(view.running)context.s("pause")else context.tr("Play","پخش")
            view.invalidate()
        }
        r.addView(pause)
        val speeds=listOf(1.0 to "1×",.5 to "½×",.25 to "¼×")
        lateinit var speed:android.widget.Button
        speed=context.button(context.tr("Speed 1×","سرعت 1×")){
            val next=speeds[(speeds.indexOfFirst{it.first==view.speed}+1)%speeds.size]
            view.speed=next.first;speed.text=context.tr("Speed ","سرعت ")+next.second
        }
        r.addView(speed)
        // "What are we seeing?": the AI describes the current state and asks the class a prediction question.
        assistant?.let{ask->
            lateinit var explain:android.widget.Button
            val answer=context.label("",17f,NAVY).apply{visibility=View.GONE;background=rounded(0xfffff6c7.toInt(),context.dp(14).toFloat());setPadding(context.dp(14),context.dp(10),context.dp(14),context.dp(10))}
            sliders.addView(answer,0)
            explain=context.button(context.tr("Explain (AI)","توضیح هوشمند"),true){
                explain.isEnabled=false;explain.text=context.tr("Thinking…","در حال فکر…")
                val values=controls.indices.joinToString(", "){i->"${controls[i].label} = ${fmt(view.values[i])}"}
                ask(context.s(key),context.s("ex_$key"),values,view.snapshot()){text->
                    explain.isEnabled=true;explain.text=context.tr("Explain (AI)","توضیح هوشمند")
                    if(text!=null){answer.text=text;answer.visibility=View.VISIBLE}
                }
            }
            r.addView(explain)
        }
        r.addView(
            context.button(context.s("reset")) {
                view.time=0.0
                controls.forEachIndexed{i,v->sliderViews[i].progress=((v.value-v.min)/(v.max-v.min)*1000).toInt()}
                view.resample()
                view.invalidate()
            }
        )
        c.addView(context.scrollRow(r))
        // Add to board and Close stay at the bottom: on a tall panel the top edge is out of reach.
        c.addView(context.scrollRow(header))
        d.setContentView(c)
        d.show()
        d.setOnDismissListener { view.running = false }
    }

    private fun fmt(v: Float) = if(v==round(v)&&kotlin.math.abs(v)<1e6)v.toInt().toString() else "%.2f".format(java.util.Locale.US, v)
    /** Counts and numbered choices move in whole steps (the lab rounds them anyway; the label showed 12.37 balls). */
    private val counts=setOf("n","N","Z","Rows","Balls","Trials","Throws","Points","Steps","Samples","Sample size","People","Terms","Sides","Turns",
        "Initial atoms","Bars","Depth","Odd harmonics","Start integer","Multiplier","Leading digit","Initial capital")
    private fun integral(control:LabControl)=control.label in counts||Regex("\\b1 .*/ 2 ").containsMatchIn(control.label)
}

class LabView(context: Context, val key: String, val values: FloatArray) : View(context) {
    private val visibleRect=Rect()
    private lateinit var frame:Runnable
    init { frame=Runnable{if(canAnimate())invalidate()} }
    private fun canAnimate()=running&&isAttachedToWindow&&isShown&&windowVisibility==VISIBLE&&hasWindowFocus()&&getGlobalVisibleRect(visibleRect)
    var running = true
        set(value){field=value;last=0;removeCallbacks(frame);if(value)invalidate()}
    override fun onWindowVisibilityChanged(visibility:Int){super.onWindowVisibilityChanged(visibility);last=0;if(::frame.isInitialized)removeCallbacks(frame);if(visibility==VISIBLE)invalidate()}
    override fun onVisibilityChanged(changedView:View,visibility:Int){super.onVisibilityChanged(changedView,visibility);last=0;if(::frame.isInitialized)removeCallbacks(frame);if(visibility==VISIBLE)invalidate()}
    override fun onWindowFocusChanged(hasWindowFocus:Boolean){super.onWindowFocusChanged(hasWindowFocus);last=0;if(::frame.isInitialized)removeCallbacks(frame);if(hasWindowFocus)invalidate()}
    override fun onDetachedFromWindow(){removeCallbacks(frame);last=0;super.onDetachedFromWindow()}
    var time = 0.0
    /** 1 = real time; ½ and ¼ for slow motion. */
    var speed = 1.0
    private var last = 0L
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var sampleSeed=42
    private var trials = DoubleArray(1000) { Math.random() }

    var ranges:List<ClosedFloatingPointRange<Float>> = emptyList()
    var onDrag:((Int,Float)->Unit)?=null
    /** Shown on the picture until the first drag; [dragLabel] shows the setting being changed while dragging. */
    var dragHint:String?=null
    var dragLabel:String?=null
    private var downX=0f;private var downY=0f;private var axis=-1;private var startValue=0f
    override fun onTouchEvent(e:MotionEvent):Boolean{
        val drag=onDrag?:return super.onTouchEvent(e)
        if(ranges.isEmpty())return super.onTouchEvent(e)
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{downX=e.x;downY=e.y;axis=-1;parent?.requestDisallowInterceptTouchEvent(true)}
            MotionEvent.ACTION_MOVE->{
                val dx=e.x-downX;val dy=e.y-downY
                if(axis<0){
                    if(hypot(dx,dy)<context.dp(12))return true
                    axis=if(abs(dx)>=abs(dy)||ranges.size<2)0 else 1;startValue=values[axis];dragHint=null
                }
                val r=ranges[axis];val rtl=layoutDirection==View.LAYOUT_DIRECTION_RTL
                // Same direction as the sliders below: they grow to the left in Persian.
                val delta=if(axis==0)(if(rtl)-dx else dx)/width.coerceAtLeast(1) else -dy/height.coerceAtLeast(1)
                drag(axis,(startValue+delta*(r.endInclusive-r.start)).coerceIn(r.start,r.endInclusive));invalidate()
            }
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{if(axis<0&&e.actionMasked==MotionEvent.ACTION_UP)performClick();axis=-1;dragLabel=null;invalidate()}
        }
        return true
    }
    override fun performClick():Boolean{super.performClick();return true}
    private fun chip(c:Canvas,text:String,top:Boolean){
        val textPaint=android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply{textSize=context.dp(15).toFloat();color=Color.WHITE;typeface=Typeface.DEFAULT_BOLD}
        val w=minOf(textPaint.measureText(text),width-context.dp(48).toFloat());val shown=android.text.TextUtils.ellipsize(text,textPaint,w,android.text.TextUtils.TruncateAt.END).toString()
        val pad=context.dp(12).toFloat();val h=context.dp(36).toFloat()
        val y=if(top)context.dp(12).toFloat() else height-h-context.dp(12)
        val r=RectF(width/2f-w/2-pad,y,width/2f+w/2+pad,y+h)
        p.style=Paint.Style.FILL;p.color=0xcc20194f.toInt();c.drawRoundRect(r,h/2,h/2,p)
        c.drawText(shown,width/2f-textPaint.measureText(shown)/2,r.centerY()-(textPaint.ascent()+textPaint.descent())/2,textPaint)
    }

    fun resample() {
        sampleSeed++;time=0.0
        trials = DoubleArray(1000) { Math.random() }
    }

    fun snapshot(): Bitmap {
        val b = Bitmap.createBitmap(1200, 684, Bitmap.Config.ARGB_8888)
        render(Canvas(b), 1200, 684)
        return b
    }

    override fun onDraw(c: Canvas) {
        val now = SystemClock.elapsedRealtime()
        if (last != 0L && canAnimate()) time += (now - last).coerceAtMost(50) / 1000.0 * speed
        last = now
        render(c, width, height)
        dragLabel?.let{chip(c,it,true)}?:dragHint?.let{chip(c,it,false)}
        if(canAnimate()&&((key.removePrefix("native_") in NativeLabs.growing&&time<NativeLabs.GROW_SECONDS+.2)||key in listOf("projectile","pendulum","spring","waves") || key.removePrefix("native_") in listOf("orbit","pendulum","projectile","spring","collision","standing","gas","states","diffusion","reaction","halflife","waves","interference","atom","periodic","doppler","heat","electrolysis","flame","equilibrium","freefall","lightclock","bonding","sorting","osmosis","markov","circuit","incline"))){removeCallbacks(frame);postDelayed(frame,16)}
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
        if(key=="periodic_table"){c.save();c.scale(w/900f,h/550f);PeriodicTable.draw(c,Item(kind="periodic",w=900f,h=550f),context);c.restore();return}
        c.drawColor(Color.WHITE)
        c.save()
        val scale = min(w / 1000f, h / 570f)
        c.translate((w - 1000 * scale) / 2, (h - 570 * scale) / 2)
        c.scale(scale, scale)
        text(c, context.s(key), 32f, 44f, 24f)
        // A still preview (gallery card) shows the finished picture; a running lab starts from zero and grows.
        if(key in NativeLabs.keys){NativeLabs.draw(c,key,values,if(running)time else 1e4,sampleSeed,context);c.restore();return}
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
