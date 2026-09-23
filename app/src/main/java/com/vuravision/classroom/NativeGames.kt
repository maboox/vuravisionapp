package com.vuravision.classroom
import android.app.Dialog
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.*
import android.widget.*
import kotlin.math.*
import kotlin.random.Random

object NativeGames {
 val ids=listOf("reaction","draw","potato","timing","stroop","tap","tug","swipe","numrace","sort","pin","tiles","gridlight","math","sum","chain","tfmath","evenodd","operator","compare","digits","dots","sheep","clock","simon","pattern","colormem","emojiseq","scramble","odd","letter","shade","arrow","rstroop","bigger","bigcircle","trap","evens","mole","hold","pong","ttt","c4","rps","penalty","pairs","hilo","dice","balloon")
 val keys=ids.map{"arc_$it"}
 fun open(context:Context,key:String){
  val d=Dialog(context,android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
  val root=context.column().apply{setBackgroundColor(PAPER);fitsSystemWindows=true;pad(12)}
  val header=context.row();header.addView(context.label(context.s(key),22f,NAVY,true),LinearLayout.LayoutParams(0,-2,1f));header.addView(context.button(context.s("close")){d.dismiss()});root.addView(header)
  root.addView(context.label(context.s("rule_$key"),14f,MUTED))
  val game=ArcadeView(context,key.removePrefix("arc_"));root.addView(game,LinearLayout.LayoutParams(-1,0,1f))
  val actions=context.row();actions.addView(context.button(context.tr("Start / next round","شروع / دور بعد"),true){game.startRound()});actions.addView(context.button(context.tr("Side / face-to-face","کنار هم / روبه‌رو")){game.face=!game.face;game.invalidate()});actions.addView(context.button(context.s("restart")){game.restart()});root.addView(context.scrollRow(actions))
  d.setContentView(root);d.setOnDismissListener{game.stop()};d.show()
 }
}

/** Deterministic challenges make both player panels equally difficult. No WebView or JS engine. */
class ArcadeRound(val key:String,val random:Random=Random.Default){
 var prompt="";var options=listOf<String>();var correct=0;var sequence=listOf<Int>();var colorIndex=0;var wordIndex=0;var target=0
 val colors=listOf(0xffe45756.toInt(),0xff287ecc.toInt(),0xff219c77.toInt(),0xffe6a52b.toInt())
 fun numbers(value:Int){val set=linkedSetOf(value);while(set.size<4)set.add(value+random.nextInt(-9,10));options=set.shuffled(random).map{it.toString()};correct=options.indexOf(value.toString())}
 fun prepare(){
  val a=random.nextInt(2,20);val b=random.nextInt(2,12)
  when(key){
   "math"->{val multiply=random.nextBoolean();prompt=if(multiply)"$a × $b = ?"else"$a + $b = ?";numbers(if(multiply)a*b else a+b)}
   "sum"->{val c=random.nextInt(2,20);sequence=listOf(a,b,c);prompt="Sum / جمع";numbers(a+b+c)}
   "chain"->{prompt="($a + $b) × 2 = ?";numbers((a+b)*2)}
   "tfmath"->{val truth=random.nextBoolean();prompt="$a + $b = ${a+b+if(truth)0 else 1}";options=listOf("✓","✕");correct=if(truth)0 else 1}
   "evenodd"->{prompt="$a";options=listOf("Even / زوج","Odd / فرد");correct=a%2}
   "operator"->{correct=random.nextInt(3);val value=when(correct){0->a+b;1->a-b;else->a*b};prompt="$a ? $b = $value";options=listOf("+","−","×")}
   "compare"->{val c=random.nextInt(2,20);val d=random.nextInt(2,12);prompt="Which is greater? / کدام بزرگ‌تر است؟";options=listOf("$a × $b","$c × $d","=");correct=if(a*b>c*d)0 else if(a*b<c*d)1 else 2}
   "digits"->{target=random.nextInt(10000,99990);prompt=target.toString();numbers(target)}
   "dots","sheep"->{target=random.nextInt(5,16);numbers(target);prompt="Count / بشمار"}
   "clock"->{target=random.nextInt(1,13);val minutes=if(random.nextBoolean())0 else 30;sequence=listOf(minutes);options=listOf("$target:${if(minutes==0)"00"else"30"}","${target%12+1}:${if(minutes==0)"00"else"30"}","$target:${if(minutes==0)"30"else"00"}","${(target+9)%12+1}:15").shuffled(random);correct=options.indexOf("$target:${if(minutes==0)"00"else"30"}");prompt="Time / ساعت"}
   "scramble"->{val words=listOf("PLANET","SCHOOL","PENCIL","WATER","SCIENCE","NUMBER","ORANGE","MUSIC");val answer=words.random(random);prompt=answer.toList().shuffled(random).joinToString(" ");options=(listOf(answer)+words.filter{it!=answer}.shuffled(random).take(3)).shuffled(random);correct=options.indexOf(answer)}
   "arrow"->{options=listOf("↑","→","↓","←");correct=random.nextInt(4);prompt=options[correct]}
   "bigger"->{options=listOf(a.toString(),(a+b).toString()).shuffled(random);correct=options.indexOf((a+b).toString());prompt="MAX"}
   "bigcircle"->{options=listOf(""," ");correct=random.nextInt(2);prompt="Larger circle / دایرهٔ بزرگ‌تر"}
   "stroop","rstroop","colormem"->{wordIndex=random.nextInt(4);colorIndex=random.nextInt(4);options=listOf("Red / قرمز","Blue / آبی","Green / سبز","Gold / طلایی");prompt=options[wordIndex];correct=if(key=="colormem")colorIndex else wordIndex;if(key=="stroop"){options=listOf("Match / برابر","Different / متفاوت");correct=if(wordIndex==colorIndex)0 else 1}}
   "odd","letter","shade"->{correct=random.nextInt(12);options=List(12){if(key=="letter")if(it==correct)"Q"else"O"else if(key=="odd")if(it==correct)"◇"else"○"else""};prompt="Find different / متفاوت را پیدا کن"}
   "numrace","sort"->{val values=if(key=="numrace")(1..12).toList()else List(6){it*10+random.nextInt(1,10)};options=values.shuffled(random).map{it.toString()};sequence=values.sorted().map{options.indexOf(it.toString())};prompt="Small → large / کوچک به بزرگ"}
   "pin"->{sequence=List(4){random.nextInt(10)};options=(0..9).map{it.toString()};prompt=sequence.joinToString("")}
   "simon","pattern","emojiseq"->{val n=if(key=="pattern")9 else 4;options=if(key=="emojiseq")listOf("★","●","▲","◆")else List(n){(it+1).toString()};sequence=if(key=="pattern")(0 until n).shuffled(random).take(4)else List(4){random.nextInt(n)};prompt="Remember / به خاطر بسپار"}
   "tiles"->{options=List(12){""};sequence=(0..11).shuffled(random).take(7);prompt="Clear lit tiles / خانه‌های روشن را بزن"}
   "gridlight","mole"->{options=List(9){""};sequence=List(40){random.nextInt(9)};prompt="Tap target / هدف را بزن"}
   "trap","evens"->{options=List(12){if(key=="trap")if(it%3==0)"●"else"★"else random.nextInt(1,30).toString()};sequence=options.indices.filter{if(key=="trap")options[it]=="★"else options[it].toInt()%2==0};prompt=if(key=="trap")"Stars only / فقط ستاره‌ها"else"Even numbers / اعداد زوج"}
   "pairs"->{options=(0..5).flatMap{listOf(it,it)}.shuffled(random).map{it.toString()};prompt="Match pairs / جفت‌ها را پیدا کن"}
   "rps"->{options=listOf("Rock / سنگ","Paper / کاغذ","Scissors / قیچی");prompt="Secret choice / انتخاب مخفی"}
   "penalty"->{options=listOf("Left / چپ","Centre / وسط","Right / راست");prompt="Striker vs keeper / مهاجم و دروازه‌بان"}
   "hilo"->{target=random.nextInt(1,14);var next=random.nextInt(1,14);while(next==target)next=random.nextInt(1,14);sequence=listOf(next);options=listOf("Higher / بالاتر","Lower / پایین‌تر");prompt="Card: $target"}
   "dice"->{options=listOf("Roll / تاس");prompt="Roll / تاس"}
   else->{options=listOf("TAP / بزن");prompt="Ready / آماده"}
  }
 }
}

class ArcadeView(context:Context,val key:String):View(context){
 var face=false
 private val p=Paint(Paint.ANTI_ALIAS_FLAG)
 private val random=Random.Default
 private var game=ArcadeRound(key)
 val scores=IntArray(2)
 private var progress=IntArray(2)
 private var choices=IntArray(2){-1}
 private val used=Array(2){mutableSetOf<Int>()}
 private val opened=Array(2){mutableListOf<Int>()}
 private var flipAt=LongArray(2)
 private var measure=DoubleArray(2){Double.NaN}
 private var pressed=LongArray(2)
 private var active=false
 private var ended=false
 private var round=0
 private var start=0L
 private var readyDelay=2000L
 private var pausedAt=0L
 private var last=0L
 private var message=""
 private var ballX=.5;private var ballY=.5;private var ballVX=.3;private var ballVY=.2
 private var paddles=doubleArrayOf(.5,.5)
 private var cells=IntArray(42){-1};private var turn=0
 private val contacts=mutableMapOf<Int,Int>()
 private val previous=mutableMapOf<Int,PointF>()
 private val timed=setOf("tap","swipe","mole","trap","evens")
 private val memory=setOf("digits","simon","pattern","colormem","emojiseq","sum","pin")
 private val sequenceKeys=setOf("numrace","sort","pin","simon","pattern","emojiseq","gridlight")
 private val wholePanelKeys=setOf("reaction","draw","potato","timing","tap","tug","swipe","hold","balloon")
 init{setBackgroundColor(Color.WHITE);isFocusable=true;contentDescription=context.s("arc_$key")}
 fun stop(){active=false;contacts.clear()}
 fun restart(){scores.fill(0);round=0;startRound(true)}
 fun startRound(force:Boolean=false){if(active&&!force)return;if(scores.any{it>=5})scores.fill(0);round++;game=ArcadeRound(key).apply{prepare()};progress.fill(0);choices.fill(-1);used.forEach{it.clear()};opened.forEach{it.clear()};flipAt.fill(0);measure.fill(Double.NaN);pressed.fill(0);cells.fill(-1);turn=0;message="";ended=false;active=true;start=SystemClock.elapsedRealtime();last=start;readyDelay=random.nextLong(1700,4300);ballX=.5;ballY=.5;ballVX=if(random.nextBoolean()).3 else -.3;ballVY=.2;paddles.fill(.5);invalidate()}
 private fun finish(winner:Int){if(!active)return;active=false;ended=true;if(winner>=0)scores[winner]++;message=if(winner<0)context.tr("Draw","مساوی")else context.s(if(winner==0)"player1"else"player2")+" · "+context.s("winner");if(scores.any{it>=5})message+=" · "+context.tr("Match complete","پایان مسابقه");invalidate()}
 private fun bothMeasured(){if(measure.all{it.isFinite()})finish(if(abs(measure[0]-measure[1])<.001)-1 else if(measure[0]<measure[1])0 else 1)}
 private fun panels():List<RectF> = if(face || width<height)listOf(RectF(8f,70f,width-8f,height/2f),RectF(8f,height/2f+8,width-8f,height-8f))else listOf(RectF(8f,70f,width/2f-4,height-8f),RectF(width/2f+4,70f,width-8f,height-8f))
 private fun local(x:Float,y:Float,player:Int):PointF{
  val r=panels()[player];val scale=min(r.width()/500f,r.height()/600f).coerceAtLeast(.001f)
  val left=r.left+(r.width()-500f*scale)/2;val top=r.top+(r.height()-600f*scale)/2
  // optionBounds uses 0..1 coordinates; mirror within that same range.
  val q=PointF((x-left)/(500f*scale),(y-top)/(600f*scale))
  if(face&&player==0){q.x=1f-q.x;q.y=1f-q.y};return q
 }
 private fun text(c:Canvas,value:String,x:Float,y:Float,size:Float,color:Int=NAVY){p.color=color;p.style=Paint.Style.FILL;p.textSize=size;p.textAlign=Paint.Align.CENTER;c.drawText(value,x,y,p)}
 private fun rect(c:Canvas,r:RectF,color:Int){p.color=color;p.style=Paint.Style.FILL;c.drawRoundRect(r,14f,14f,p)}
 private fun circle(c:Canvas,x:Float,y:Float,r:Float,color:Int){p.color=color;p.style=Paint.Style.FILL;c.drawCircle(x,y,r,p)}
 private fun optionText(c:Canvas,label:String,r:RectF){
  val lines=if(" / " in label)label.split(" / ",limit=2)else listOf(label)
  val maxWidth=(r.width()-20f).coerceAtLeast(40f)
  val size=lines.fold(25f){current,line->
   p.textSize=current;p.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
   min(current,(current*maxWidth/p.measureText(line).coerceAtLeast(1f)).coerceIn(13f,25f))
  }
  lines.forEachIndexed{index,line->text(c,line,r.centerX(),r.centerY()+(index-(lines.size-1)/2f)*size*1.15f+size*.35f,size,NAVY)}
 }
 private fun optionBounds(index:Int,count:Int):RectF{val cols=if(count>6)3 else if(count==1)1 else 2;val rows=ceil(count.toDouble()/cols).toInt();val w=.88f/cols;val h=.48f/rows;return RectF(.06f+(index%cols)*w,.45f+(index/cols)*h,.06f+(index%cols+1)*w-.02f,.45f+(index/cols+1)*h-.02f)}
 override fun onDraw(c:Canvas){
  val now=SystemClock.elapsedRealtime();val elapsed=(now-start)/1000.0
  if(active){
   if(key in timed && elapsed>=if(key=="mole")15 else 10)finish(if(progress[0]==progress[1])-1 else if(progress[0]>progress[1])0 else 1)
   if(key=="potato"&&now-start>=readyDelay+3500)finish(1-turn)
   if(key=="balloon")for(i in 0..1)if(pressed[i]>0 && now-pressed[i]>readyDelay+1500){measure[i]=1e6;pressed[i]=0;bothMeasured()}
   if(key=="pong"){val dt=((now-last)/1000.0).coerceIn(0.0,.05);ballX+=ballVX*dt;ballY+=ballVY*dt;if(ballY<.03||ballY>.97){ballY=ballY.coerceIn(.03,.97);ballVY=-ballVY};if(ballX<.065&&ballVX<0&&abs(ballY-paddles[0])<.14){ballVX=abs(ballVX)*1.04;ballX=.065};if(ballX>.935&&ballVX>0&&abs(ballY-paddles[1])<.14){ballVX=-abs(ballVX)*1.04;ballX=.935};if(ballX<0)finish(1);if(ballX>1)finish(0)}
  }
  last=now
  text(c,"${scores[0]}   :   ${scores[1]}    ·    ${context.s("round")} $round",width/2f,42f,28f)
  if(key in setOf("ttt","c4")){drawBoard(c);if(active)postInvalidateOnAnimation();return}
  if(key=="pong"){rect(c,RectF(0f,70f,width.toFloat(),height.toFloat()),PAPER);val h=height-70f;rect(c,RectF(20f,70+(paddles[0].toFloat()-.12f)*h,34f,70+(paddles[0].toFloat()+.12f)*h),TEAL);rect(c,RectF(width-34f,70+(paddles[1].toFloat()-.12f)*h,width-20f,70+(paddles[1].toFloat()+.12f)*h),ORANGE);circle(c,(ballX*width).toFloat(),70+(ballY*h).toFloat(),12f,NAVY);if(!active)text(c,message.ifBlank{context.s("start")},width/2f,height/2f,28f);if(active)postInvalidateOnAnimation();return}
  panels().forEachIndexed{player,r->c.save();c.clipRect(r);rect(c,r,if(player==0)0xffeef8f7.toInt()else 0xfffff6e8.toInt());
   val scale=min(r.width()/500f,r.height()/600f).coerceAtLeast(.001f)
   c.translate(r.left+(r.width()-500f*scale)/2,r.top+(r.height()-600f*scale)/2)
   if(face&&player==0){c.translate(500f*scale,600f*scale);c.rotate(180f)}
   c.scale(scale,scale);drawPanel(c,player,now,elapsed);c.restore()}
  if(active&&isAttachedToWindow)postInvalidateOnAnimation()
 }
 private fun drawPanel(c:Canvas,i:Int,now:Long,elapsed:Double){
  val accent=if(i==0)TEAL else ORANGE;rect(c,RectF(0f,0f,500f,600f),if(i==0)0xffeef8f7.toInt()else 0xfffff6e8.toInt());text(c,context.s(if(i==0)"player1"else"player2"),250f,38f,24f,accent)
  if(!active){text(c,message.ifBlank{context.s("start")},250f,250f,24f);return}
  if(choices[i]>=0&&key in setOf("rps","penalty","dice","hilo")){text(c,context.tr("Choice locked","انتخاب ثبت شد"),250f,270f,25f);return}
  if(measure[i].isFinite()){text(c,context.tr("Done — wait","ثبت شد — صبر کنید"),250f,270f,24f);return}
  val revealing=key in memory&&elapsed<2.5
  var prompt=game.prompt
  if(key in setOf("reaction","draw"))prompt=if(now-start>=readyDelay)"NOW! / حالا!"else if(key=="draw"&&elapsed>1)listOf("NO! / نه!","SLOW! / آهسته!","WAIT / صبر")[(elapsed*4).toInt()%3]else"WAIT / صبر"
  if(key=="potato")prompt=if(turn==i)"PASS! / پاس بده"else"Wait / صبر"
  if(key in timed||key=="tug")prompt="${progress[i]}"
  if(key in memory&&!revealing)prompt=if(key=="sum")"SUM? / جمع؟"else"?"
  if(key=="sum"&&revealing)prompt=game.sequence[(elapsed/.83).toInt().coerceAtMost(2)].toString()
  if(key=="penalty")prompt=if(i==round%2)context.tr("Striker","مهاجم")else context.tr("Goalkeeper","دروازه‌بان")
  text(c,prompt,250f,100f,if(prompt.length>28)18f else 28f,if(key in setOf("stroop","rstroop"))game.colors[game.colorIndex]else NAVY)
  if(key in setOf("dots","sheep")){repeat(game.target){j->val x=65f+(j%6)*65;val y=145f+(j/6)*35;circle(c,x,y,10f,accent);if(key=="sheep"){circle(c,x+9,y-6,6f,NAVY);rect(c,RectF(x-5,y+6,x-2,y+16),NAVY)}};if(key=="sheep")repeat(3){j->circle(c,90f+j*110,242f,7f,ORANGE)}}
  if(key=="clock"){val min=game.sequence[0];p.color=NAVY;p.style=Paint.Style.STROKE;p.strokeWidth=3f;c.drawCircle(250f,180f,62f,p);fun hand(angle:Double,len:Float){c.drawLine(250f,180f,250+sin(angle).toFloat()*len,180-cos(angle).toFloat()*len,p)};hand(2*PI*(game.target+min/60.0)/12,35f);hand(2*PI*min/60,50f);p.style=Paint.Style.FILL}
  if(key=="timing"){rect(c,RectF(40f,175f,460f,205f),0xffdad6e7.toInt());rect(c,RectF(225f,170f,275f,210f),TEAL);circle(c,(250+200*sin(elapsed*3)).toFloat(),190f,11f,ORANGE)}
  if(key=="hold")text(c,if(pressed[i]>0)"…"else"Hold → 5 s → release",250f,190f,23f)
  if(key=="balloon"){val size=if(pressed[i]>0)min(75.0,20+(now-pressed[i])/65.0)else 20.0;circle(c,250f,185f,size.toFloat(),accent)}
  if(key=="colormem"&&revealing)rect(c,RectF(180f,145f,320f,240f),game.colors[game.colorIndex])
  if(key in sequenceKeys)text(c,"${progress[i]} / ${if(key=="gridlight")8 else game.sequence.size}",250f,235f,20f,MUTED)
  if(revealing && key in setOf("digits","sum","colormem"))return
  game.options.forEachIndexed{j,value->
   val rr=optionBounds(j,game.options.size);val r=RectF(rr.left*500,rr.top*600,rr.right*500,rr.bottom*600)
   var color=Color.WHITE;var label=value
   if(key in setOf("tiles","trap","evens")){if(j in used[i])color=0xffd6d9dc.toInt()else if(key=="tiles"&&j in game.sequence)color=accent}
   if(key in setOf("gridlight","mole")){val target=if(key=="mole")((elapsed*1.8).toInt()+i*0)%9 else game.sequence[progress[i].coerceAtMost(39)];color=if(j==target)accent else Color.WHITE;label=if(j==target)"●"else""}
   if(key=="shade")color=if(j==game.correct)0xff528bc8.toInt()else 0xff387bbd.toInt()
   if(key in setOf("simon","pattern","emojiseq")&&revealing){val visible=if(key=="pattern")j in game.sequence else j==game.sequence[((elapsed/0.6).toInt()).coerceAtMost(3)];if(visible)color=accent}
   if(key=="simon")color=if(revealing&&j==game.sequence[((elapsed/0.6).toInt()).coerceAtMost(3)])Color.WHITE else game.colors[j]
   if(key in setOf("colormem","rstroop"))color=game.colors[j]
   if(key=="pairs"){if(j !in used[i]&&j !in opened[i])label="?"else color=0xffc5e9d7.toInt()}
   if(j in used[i]&&key in sequenceKeys)color=0xffd6d9dc.toInt()
   rect(c,r,color)
   if(key=="bigcircle")circle(c,r.centerX(),r.centerY(),if(j==game.correct)55f else 32f,accent)
   else optionText(c,label,r)
  }
 }
 private fun drawBoard(c:Canvas){val cols=if(key=="ttt")3 else 7;val rows=if(key=="ttt")3 else 6;val size=min(width*.9f/cols,(height-150f)/rows);val left=(width-cols*size)/2;val top=100f;for(row in 0 until rows)for(col in 0 until cols){val idx=row*cols+col;rect(c,RectF(left+col*size+3,top+row*size+3,left+(col+1)*size-3,top+(row+1)*size-3),PAPER);if(cells[idx]>=0)circle(c,left+(col+.5f)*size,top+(row+.5f)*size,size*.3f,if(cells[idx]==0)TEAL else ORANGE)};text(c,if(active)context.s(if(turn==0)"player1"else"player2")else message.ifBlank{context.s("start")},width/2f,height-15f,22f)}
 private fun boardTap(x:Float,y:Float){val cols=if(key=="ttt")3 else 7;val rows=if(key=="ttt")3 else 6;val size=min(width*.9f/cols,(height-150f)/rows);val left=(width-cols*size)/2;val col=floor((x-left)/size).toInt();var row=floor((y-100)/size).toInt();if(col !in 0 until cols||row !in 0 until rows)return;if(key=="c4")row=(rows-1 downTo 0).firstOrNull{cells[it*cols+col]<0}?:return;val idx=row*cols+col;if(cells[idx]>=0)return;cells[idx]=turn;val needed=if(key=="ttt")3 else 4;for((dx,dy)in listOf(1 to 0,0 to 1,1 to 1,1 to -1)){var count=1;for(sign in listOf(-1,1)){var xx=col+dx*sign;var yy=row+dy*sign;while(xx in 0 until cols&&yy in 0 until rows&&cells[yy*cols+xx]==turn){count++;xx+=dx*sign;yy+=dy*sign}};if(count>=needed){finish(turn);return}};if((0 until cols*rows).all{cells[it]>=0})finish(-1)else turn=1-turn}
 private fun choose(i:Int,j:Int,now:Long){
  if(!active||j<0||choices[i]>=0||measure[i].isFinite())return
  val elapsed=(now-start)/1000.0
  if(key in memory&&elapsed<2.5)return
  when(key){
   "reaction","draw"->finish(if(now-start>=readyDelay)i else 1-i)
   "potato"->{if(turn==i)turn=1-i}
   "timing"->{measure[i]=abs(sin(elapsed*3));bothMeasured()}
   "tap","tug"->{progress[i]++;if(key=="tug"&&progress[i]-progress[1-i]>=20)finish(i)}
   "swipe","hold","balloon"->{}
   "mole"->{val target=(elapsed*1.8).toInt()%9;val stamp=(elapsed*1.8).toInt();if(j==target&&used[i].add(stamp))progress[i]++}
   "tiles","trap","evens"->{if(j in game.sequence&&used[i].add(j))progress[i]++ else if(j !in game.sequence){progress[i]=max(0,progress[i]-1)};if(key=="tiles"&&used[i].size==game.sequence.size)finish(i)}
   "pairs"->{if(j in used[i]||j in opened[i])return;if(opened[i].size==2){if(now-flipAt[i]<700)return;opened[i].clear()};opened[i].add(j);if(opened[i].size==2){flipAt[i]=now;if(game.options[opened[i][0]]==game.options[j]){used[i].addAll(opened[i]);opened[i].clear();if(used[i].size==12)finish(i)}}}
   "rps","penalty"->{choices[i]=j;if(choices.all{it>=0}){if(key=="rps")finish(if(choices[0]==choices[1])-1 else if((choices[0]-choices[1]+3)%3==1)0 else 1)else{val striker=round%2;finish(if(choices[0]==choices[1])1-striker else striker)}}}
   "dice"->{choices[i]=random.nextInt(1,7);if(choices.all{it>=0})finish(if(choices[0]==choices[1])-1 else if(choices[0]>choices[1])0 else 1)}
   "hilo"->{choices[i]=j;if(choices.all{it>=0}){val next=game.sequence[0];val correct=if(next>game.target)0 else 1;finish(if(next==game.target||choices[0]==choices[1])-1 else if(choices[0]==correct)0 else 1)}}
   in sequenceKeys->{if(j==game.sequence[progress[i]]){used[i].add(j);progress[i]++;if(progress[i]>=if(key=="gridlight")8 else game.sequence.size)finish(i)}else{progress[i]=0;used[i].clear()}}
   else->{if(j==game.correct)finish(i)else{choices[i]=j;if(choices[1-i]>=0)finish(-1)}}
  }
 }
 override fun onTouchEvent(e:MotionEvent):Boolean{
  if(!active)return true
  val idx=e.actionIndex;val pid=e.getPointerId(idx);val now=SystemClock.elapsedRealtime()
  when(e.actionMasked){
   MotionEvent.ACTION_DOWN,MotionEvent.ACTION_POINTER_DOWN->{requestUnbufferedDispatch(e);parent?.requestDisallowInterceptTouchEvent(true);if(key in setOf("ttt","c4")){boardTap(e.getX(idx),e.getY(idx));invalidate();return true};val i=if(key=="pong")if(e.getX(idx)<width/2)0 else 1 else panels().indexOfFirst{it.contains(e.getX(idx),e.getY(idx))};if(i<0)return true;contacts[pid]=i;previous[pid]=PointF(e.getX(idx),e.getY(idx));if(key in setOf("hold","balloon")&&pressed[i]==0L)pressed[i]=now
    if(key!="pong"){
     val q=local(e.getX(idx),e.getY(idx),i);val option=if(key in wholePanelKeys)0 else game.options.indices.firstOrNull{optionBounds(it,game.options.size).contains(q.x,q.y)}?:-1;choose(i,option,now)
    }}
   MotionEvent.ACTION_MOVE->{for(j in 0 until e.pointerCount){val id=e.getPointerId(j);val i=contacts[id]?:continue;val x=e.getX(j);val y=e.getY(j);if(key=="pong")paddles[i]=((y-70)/(height-70.0)).coerceIn(.12,.88);if(key=="swipe"){val old=previous[id]?:PointF(x,y);progress[i]+=hypot(x-old.x,y-old.y).toInt();previous[id]=PointF(x,y)}}}
   MotionEvent.ACTION_UP,MotionEvent.ACTION_POINTER_UP->{val i=contacts.remove(pid);previous.remove(pid);if(i!=null&&pressed[i]>0){if(key=="hold")measure[i]=abs((now-pressed[i])/1000.0-5);if(key=="balloon")measure[i]=-(now-pressed[i]).toDouble();pressed[i]=0;bothMeasured()};performClick()}
   MotionEvent.ACTION_CANCEL->{contacts.clear();previous.clear();pressed.fill(0);active=false;message=context.tr("Round interrupted. Start again.","دور متوقف شد. دوباره شروع کنید.")}
  };invalidate();return true
 }
 override fun performClick():Boolean{super.performClick();return true}
 override fun onWindowVisibilityChanged(visibility:Int){super.onWindowVisibilityChanged(visibility);if(visibility!=VISIBLE){if(pausedAt==0L)pausedAt=SystemClock.elapsedRealtime()}else if(pausedAt!=0L){val pause=SystemClock.elapsedRealtime()-pausedAt;start+=pause;readyDelay=readyDelay.coerceAtLeast(0);for(i in 0..1){if(pressed[i]>0)pressed[i]+=pause;if(flipAt[i]>0)flipAt[i]+=pause};last=SystemClock.elapsedRealtime();pausedAt=0;invalidate()}}
}
