package com.vuravision.classroom
import android.content.Context
import android.graphics.Color
import android.os.*
import android.view.*
import android.widget.*
class ClassroomWidgets(private val context:Context,private val host:FrameLayout) {
 private val handler=Handler(Looper.getMainLooper());private val widgets=mutableMapOf<String,View>()
 init {host.addOnLayoutChangeListener{_,_,_,_,_,_,_,_,_->widgets.values.forEach{v->v.x=v.x.coerceIn(0f,(host.width-v.width).coerceAtLeast(0).toFloat());v.y=v.y.coerceIn(0f,(host.height-v.height).coerceAtLeast(0).toFloat())}}}
 fun closeAll(){handler.removeCallbacksAndMessages(null);widgets.values.forEach{host.removeView(it)};widgets.clear()}
 fun open(key:String){
  widgets[key]?.let{it.bringToFront();return}
  val content=context.column().apply{pad(12)}
  val card=context.card(content,cornerRadius=22,elevation=8,stroke=OUTLINE_STRONG)
  val head=context.row();val drag=context.label("⠿  ${context.s(key)}",15f,NAVY,true);head.addView(drag,LinearLayout.LayoutParams(0,-2,1f))
  val body=context.column();var minimized=false;val tasks=mutableListOf<Runnable>()
  head.addView(context.button("−"){minimized=!minimized;body.visibility=if(minimized)View.GONE else View.VISIBLE}.apply{contentDescription=context.s("minimize")})
  head.addView(context.button("×"){tasks.forEach{handler.removeCallbacks(it)};host.removeView(card);widgets.remove(key)}.apply{contentDescription=context.s("close")})
  content.addView(head);content.addView(body)
  val display=context.label("0",36f,NAVY,true).apply{gravity=Gravity.CENTER;layoutDirection=View.LAYOUT_DIRECTION_LTR};body.addView(display)
  val buttons=context.row();body.addView(buttons)
  fun btn(text:String,action:()->Unit){buttons.addView(context.button(text,false,action),LinearLayout.LayoutParams(0,context.dp(48),1f))}
  when(key){
   "timer","stopwatch"->{
    var elapsed=0L;var since=0L;var running=false;var duration=300000L;display.text=if(key=="timer")"05:00"else"00:00"
    fun total()=elapsed+if(running)SystemClock.elapsedRealtime()-since else 0L
    val tick=object:Runnable{override fun run(){if(key=="timer"&&running&&total()>=duration){elapsed=duration;running=false;display.setTextColor(ORANGE);card.announceForAccessibility(context.s("timer_done"))};val sec=(if(key=="timer")(duration-total()).coerceAtLeast(0)else total())/1000;display.text="%02d:%02d".format(sec/60,sec%60);handler.postDelayed(this,200)}}
    tasks.add(tick);handler.post(tick)
    btn("▶"){if(!running&&(key!="timer"||elapsed<duration)){since=SystemClock.elapsedRealtime();running=true}}
    btn("Ⅱ"){elapsed=total();running=false};btn("↺"){running=false;elapsed=0;display.setTextColor(NAVY)}
    if(key=="timer"){val minutes=context.field("5",context.s("minutes")).apply{inputType=android.text.InputType.TYPE_CLASS_NUMBER;setSingleLine(true)};val row=context.row();row.addView(minutes,LinearLayout.LayoutParams(0,-2,1f));row.addView(context.button(context.s("minutes")){val n=minutes.text.toString().toIntOrNull();if(n!=null&&n in 1..240){duration=n*60000L;elapsed=0;running=false;display.setTextColor(NAVY)}else minutes.error="1–240"});body.addView(row)}
   }
   "dice"->{display.text="⚄";btn(context.s("roll")){display.text="${(1..6).random()}"}}
   // Name picker: names spin past, then one lands; nobody is picked twice until everyone has had a turn (fairness the class can see).
   "picker"->{
    val prefs=context.getSharedPreferences("vura",0);val used=mutableSetOf<String>()
    display.text="?";display.textSize=40f*context.uiScale()
    val names=context.field(prefs.getString("pickerNames","")?:"",context.s("picker_names")).apply{isSingleLine=false;minLines=3;maxLines=6
     inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE}
    btn(context.s("pick")){
     val list=names.text.toString().lines().map{it.trim()}.filter{it.isNotEmpty()}.distinct()
     prefs.edit().putString("pickerNames",list.joinToString("\n")).apply()
     if(list.size<2){names.error=context.s("picker_names");return@btn}
     var pool=list.filter{it !in used};if(pool.isEmpty()){used.clear();pool=list;Toast.makeText(context,context.s("picker_restart"),Toast.LENGTH_SHORT).show()}
     val chosen=pool.random();var steps=14;display.setTextColor(NAVY)
     val spin=object:Runnable{override fun run(){
      if(steps-->0){display.text=list.random();Sounds.play(context,Sounds.Cue.TICK);handler.postDelayed(this,60L+(14-steps)*14L)}
      else{display.text=chosen;display.setTextColor(TEAL);used.add(chosen);Sounds.play(context,Sounds.Cue.CORRECT);card.announceForAccessibility(chosen)}
     }}
     tasks.add(spin);handler.post(spin)
    }
    btn("↺"){used.clear();display.text="?";display.setTextColor(NAVY)}
    body.addView(names,LinearLayout.LayoutParams(-1,-2).apply{topMargin=context.dp(8)})
   }
   "scoreboard"->{var a=0;var b=0;display.text="0 : 0";btn("A +"){a++;display.text="$a : $b"};btn("B +"){b++;display.text="$a : $b"};btn("↺"){a=0;b=0;display.text="0 : 0"}}
   "curtain"->{display.text=context.s("curtain");display.setTextColor(ON_NAVY);display.setBackgroundColor(NAVY);display.layoutParams=LinearLayout.LayoutParams(-1,context.dp(220));btn(context.s("reveal")){display.visibility=if(display.visibility==View.VISIBLE)View.GONE else View.VISIBLE}}
  }
  val width=minOf(context.dp(if(key=="curtain")480 else 340),(host.width-context.dp(16)).coerceAtLeast(context.dp(240)))
  host.addView(card,FrameLayout.LayoutParams(width,-2,Gravity.TOP or Gravity.LEFT).apply{leftMargin=context.dp(16);topMargin=context.dp(16+widgets.size*24)});widgets[key]=card
  var dx=0f;var dy=0f
  drag.setOnTouchListener{_,e->when(e.actionMasked){MotionEvent.ACTION_DOWN->{card.bringToFront();dx=e.rawX-card.x;dy=e.rawY-card.y};MotionEvent.ACTION_MOVE->{card.x=(e.rawX-dx).coerceIn(0f,(host.width-card.width).coerceAtLeast(0).toFloat());card.y=(e.rawY-dy).coerceIn(0f,(host.height-card.height).coerceAtLeast(0).toFloat())};MotionEvent.ACTION_UP->drag.performClick()};true}
 }
}
