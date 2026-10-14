package com.vuravision.classroom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Short synthesized sounds for games: no audio files to ship, and each sound is under half a second so it never talks
 * over the teacher. Off with Settings → Class → "Game sounds" (a quiet exam room, a shared space).
 */
object Sounds {
    enum class Cue(val notes:List<Pair<Double,Int>>,val wave:String) {
        CORRECT(listOf(660.0 to 90,990.0 to 140),"sine"),
        WRONG(listOf(196.0 to 110,165.0 to 170),"square"),
        WIN(listOf(523.3 to 110,659.3 to 110,784.0 to 110,1046.5 to 260),"sine"),
        TICK(listOf(1200.0 to 30),"sine"),
    }
    private const val RATE=22050
    private val cache=mutableMapOf<Cue,ShortArray>()
    fun enabled(context:Context)=context.getSharedPreferences("vura",0).getBoolean("gameSounds",true)
    fun play(context:Context,cue:Cue){
        if(!enabled(context))return
        val pcm=cache.getOrPut(cue){synth(cue)}
        try{
            val track=AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size*2).build()
            track.write(pcm,0,pcm.size);track.play()
            // Released after it has played; a static track holds its own copy of the samples.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({try{track.release()}catch(_:Exception){}},pcm.size*1000L/RATE+200)
        }catch(_:Exception){/* No audio output (or a test device): silent is fine. */}
    }
    /** Notes with a soft attack and decay so they click less on cheap panel speakers. */
    fun synth(cue:Cue):ShortArray{
        val out=ArrayList<Short>()
        cue.notes.forEach{(freq,ms)->
            val n=RATE*ms/1000
            for(i in 0 until n){
                val t=i.toDouble()/RATE;val phase=2*PI*freq*t
                val raw=if(cue.wave=="square")(if(sin(phase)>=0)1.0 else -1.0)*.6 else sin(phase)
                val envelope=minOf(1.0,i/(RATE*.008))*exp(-3.0*i/n)
                out.add((raw*envelope*.45*Short.MAX_VALUE).toInt().toShort())
            }
        }
        return out.toShortArray()
    }
}

/**
 * Visual feedback drawn over a two-player game: the round winner's panel flashes green, the other side gives a short
 * shake, and a won match ends in confetti. Times come from the uptime clock, not the game clock, so a paused game's
 * celebration still finishes.
 */
class GameFx {
    private class Piece(var x:Float,var y:Float,val vx:Float,var vy:Float,val color:Int,val size:Float,var turn:Float,val spin:Float)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val pieces=mutableListOf<Piece>()
    private var confettiAt=0L
    private val flashAt=LongArray(2)
    private val shakeAt=LongArray(2)
    private val colors=listOf(0xfff8a529.toInt(),0xff0f716f.toInt(),0xff2563eb.toInt(),0xffdc2626.toInt(),0xff7c3aed.toInt(),0xff16a34a.toInt())
    private fun now()=SystemClock.uptimeMillis()
    /** A round ended: [winner] is 0 or 1, or -1 for a draw. */
    private var popAt=0L
    /** A springy bump right after a point: overshoots, settles back to 0 within 450 ms (a damped spring, not a fade). */
    fun pop():Float{val e=now()-popAt;return if(popAt==0L||e>450)0f else Spring.bounce(e/1000f)}
    fun round(winner:Int,panels:List<RectF>,match:Boolean){
        val t=now();if(winner in 0..1)popAt=t
        if(winner in 0..1){flashAt[winner]=t;shakeAt[1-winner]=t}
        if(match&&winner in 0..1&&!Motion.reduced){confettiAt=t;burst(panels[winner],90)}
    }
    private fun burst(r:RectF,count:Int){
        val random=Random(now())
        repeat(count){pieces.add(Piece(r.centerX()+random.nextFloat()*r.width()*.4f-r.width()*.2f,r.top+r.height()*.35f,
            (random.nextFloat()-.5f)*900f,-500f-random.nextFloat()*700f,colors[random.nextInt(colors.size)],8f+random.nextFloat()*10f,random.nextFloat()*360f,(random.nextFloat()-.5f)*720f))}
    }
    /** Sideways offset for a panel that just lost a round: a damped wobble over 350 ms. */
    fun shake(player:Int):Float{val e=now()-shakeAt[player];if(Motion.reduced||shakeAt[player]==0L||e>350)return 0f;return (sin(e/350.0*PI*6)*14*(1-e/350.0)).toFloat()}
    /** Drawn after a panel: a green wash that fades over 600 ms. */
    fun flash(c:Canvas,player:Int,r:RectF){
        val e=now()-flashAt[player];if(flashAt[player]==0L||e>600)return
        paint.style=Paint.Style.FILL;paint.color=0x16a34a or (((1-e/600f)*90).toInt() shl 24);c.drawRoundRect(r,14f,14f,paint)
    }
    fun confetti(c:Canvas,height:Int){
        if(pieces.isEmpty())return
        val e=(now()-confettiAt)/1000f
        if(e>2.2f){pieces.clear();return}
        // Real elapsed time, not a fixed 1/60 s: on a 120 Hz panel confetti used to fall twice as fast.
        val t=now();val dt=((if(lastFrame==0L)16L else t-lastFrame).coerceIn(1L,50L))/1000f;lastFrame=t
        pieces.forEach{p->p.vy+=1400f*dt;p.x+=p.vx*dt;p.y+=p.vy*dt;p.turn+=p.spin*dt
            paint.color=p.color;paint.alpha=(255*(1-(e/2.2f))).toInt().coerceIn(0,255)
            c.save();c.rotate(p.turn,p.x,p.y);c.drawRect(p.x-p.size/2,p.y-p.size/4,p.x+p.size/2,p.y+p.size/4,paint);c.restore()}
        pieces.removeAll{it.y>height+40};if(pieces.isEmpty())lastFrame=0L
    }
    private var lastFrame=0L
    /** True while anything is still moving, so the view keeps drawing frames. */
    val busy get()=pieces.isNotEmpty()||(popAt!=0L&&now()-popAt<450)||(0..1).any{(flashAt[it]!=0L&&now()-flashAt[it]<600)||(shakeAt[it]!=0L&&now()-shakeAt[it]<350)}
}

/** Item 52: shared motion pieces for labs and games: a damped spring and a short haptic tick on a win. */
object Spring {
    /** Damped oscillation from 1 to 0: about 450 ms, one overshoot, the "pop" of a score or a card. */
    fun bounce(seconds:Float,frequency:Float=3.2f,damping:Float=9f)=(kotlin.math.exp(-damping*seconds)*kotlin.math.cos(2*Math.PI.toFloat()*frequency*seconds)).toFloat()
}
object Haptics {
    /** A short confirmation buzz where the panel has a motor; nothing with "Reduce motion". */
    fun win(view:android.view.View){
        if(Motion.reduced)return
        view.performHapticFeedback(if(android.os.Build.VERSION.SDK_INT>=30)android.view.HapticFeedbackConstants.CONFIRM else android.view.HapticFeedbackConstants.LONG_PRESS)
    }
}
