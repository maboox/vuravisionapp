package com.vuravision.classroom
import android.content.Context
import android.graphics.*
import kotlin.math.*
import kotlin.random.Random

/** Native Canvas activities derived from the supplied learning topics. SI quantities unless labelled otherwise. */
object NativeLabs {
 val ids=listOf("orbit","pendulum","projectile","spring","collision","standing","gas","molecule","states","ph","reaction","atom","solubility","halflife","waves","circle","fractal","golden","quadratic","tangent","fourier","galton","coins","dice","montecarlo","walk","clt","regression","incline","lens","circuit","doppler","buoyancy","heat","periodic","titration","electrolysis","diffusion","flame","equilibrium","primes","pythagoras","polypi","lissajous","series","pascal","monty","birthday","bayes","benford","median","streaks","freefall","interference","pressure","lightclock","bonding","catalyst","osmosis","density","collatz","modcircle","koch","sorting","sampling","ruin","simpson","markov")
 val keys=ids.map{"native_$it"}
 private fun ctl(name:String,min:Float,max:Float,v:Float)=LabControl(name,min,max,v)
 fun controls(full:String):List<LabControl> = when(full.removePrefix("native_")) {
  "orbit"->listOf(ctl("Mass / M☉",.2f,3f,1f),ctl("Radius / AU",.3f,3f,1f))
  "pendulum"->listOf(ctl("L (m)",.4f,4f,1f),ctl("g (m/s²)",1f,25f,9.81f))
  "projectile"->listOf(ctl("v (m/s)",5f,60f,25f),ctl("θ (°)",5f,85f,45f),ctl("g (m/s²)",1f,25f,9.81f))
  "spring"->listOf(ctl("m (kg)",.5f,5f,1f),ctl("k (N/m)",5f,100f,20f))
  "collision"->listOf(ctl("m₁ (kg)",1f,8f,2f),ctl("m₂ (kg)",1f,8f,4f),ctl("u₁ (m/s)",1f,8f,4f))
  "standing"->listOf(ctl("n",1f,8f,3f),ctl("Amplitude",.1f,1f,.7f))
  "gas"->ExtraLabs.controls("gas")
  "molecule"->listOf(ctl("Molecule: 1 H₂O / 2 CO₂ / 3 CH₄",1f,3f,1f),ctl("Rotation (°)",0f,360f,20f))
  "states"->listOf(ctl("T (°C), water at 1 atm",-30f,140f,20f))
  "ph"->listOf(ctl("pH",0f,14f,7f))
  "reaction"->listOf(ctl("T (K)",250f,600f,300f),ctl("Eₐ (kJ/mol)",10f,50f,25f))
  "atom","periodic"->listOf(ctl("Z",1f,20f,8f))
  "solubility"->listOf(ctl("Salt (g)",0f,80f,25f),ctl("Water (mL)",10f,200f,100f))
  "halflife"->listOf(ctl("Half-life (s)",1f,20f,5f),ctl("Initial atoms",20f,200f,100f))
  "waves","interference"->listOf(ctl("f₁ (Hz)",.5f,5f,2f),ctl("f₂ (Hz)",.5f,5f,2.5f),ctl("Phase (°)",0f,360f,0f))
  "circle"->listOf(ctl("θ (°)",0f,360f,45f))
  "fractal"->listOf(ctl("Depth",1f,9f,6f),ctl("Branch angle (°)",10f,60f,25f))
  "golden"->listOf(ctl("Turns",1f,5f,3f))
  "quadratic"->listOf(ctl("a",-3f,3f,1f),ctl("b",-6f,6f,0f),ctl("c",-9f,9f,-4f))
  "tangent"->listOf(ctl("x at tangent",-4f,4f,1f))
  "fourier"->listOf(ctl("Odd harmonics",1f,20f,5f))
  "galton"->listOf(ctl("Rows",3f,15f,10f),ctl("Balls",20f,1000f,300f))
  "coins"->listOf(ctl("P(head)",0f,1f,.5f),ctl("Trials",10f,1000f,300f))
  "dice"->listOf(ctl("Throws",10f,1000f,300f))
  "montecarlo"->listOf(ctl("Points",20f,2000f,400f))
  "walk"->listOf(ctl("Steps",10f,500f,150f),ctl("P(right)",0f,1f,.5f))
  "clt"->listOf(ctl("Sample size",1f,50f,10f),ctl("Samples",20f,500f,200f))
  "regression"->listOf(ctl("Slope",-3f,3f,1f),ctl("Noise",0f,4f,1f))
  "incline"->listOf(ctl("θ (°)",0f,70f,30f),ctl("μ",0f,1f,.2f))
  "lens"->ExtraLabs.controls("lens")
  "circuit"->ExtraLabs.controls("ohm")
  "doppler"->listOf(ctl("Source v (m/s)",-200f,200f,80f),ctl("f (Hz)",100f,1000f,440f))
  "buoyancy"->ExtraLabs.controls("buoyancy")
  "heat"->listOf(ctl("Left T (°C)",0f,100f,90f),ctl("Right T (°C)",0f,100f,10f),ctl("Diffusivity",.01f,.3f,.08f))
  "titration"->listOf(ctl("Added NaOH (mL)",0f,50f,10f),ctl("HCl (mol/L)",.01f,.2f,.1f))
  "electrolysis"->listOf(ctl("Current (A)",.1f,10f,2f))
  "diffusion"->listOf(ctl("D (relative)",.1f,3f,1f))
  "flame"->listOf(ctl("Salt: 1 Na / 2 K / 3 Cu / 4 Ca",1f,4f,1f))
  "equilibrium"->listOf(ctl("k forward",.1f,3f,1f),ctl("k reverse",.1f,3f,.5f))
  "primes"->listOf(ctl("N",20f,150f,100f))
  "pythagoras"->listOf(ctl("a",1f,10f,3f),ctl("b",1f,10f,4f))
  "polypi"->listOf(ctl("Sides",3f,100f,6f))
  "lissajous"->listOf(ctl("fx",1f,6f,3f),ctl("fy",1f,6f,2f),ctl("Phase (°)",0f,180f,90f))
  "series"->listOf(ctl("Ratio r",-.95f,.95f,.5f),ctl("Terms",1f,30f,8f))
  "pascal"->listOf(ctl("Rows",2f,10f,7f))
  "monty"->listOf(ctl("Trials",20f,1000f,300f))
  "birthday"->listOf(ctl("People",2f,80f,23f))
  "bayes"->listOf(ctl("Prevalence (%)",.1f,40f,1f),ctl("Sensitivity (%)",50f,100f,95f),ctl("Specificity (%)",50f,100f,95f))
  "benford"->listOf(ctl("Leading digit",1f,9f,1f))
  "median"->listOf(ctl("Outlier",5f,100f,10f))
  "streaks"->listOf(ctl("Trials",20f,300f,100f),ctl("P(head)",0f,1f,.5f))
  "freefall"->listOf(ctl("Height (m)",10f,100f,50f),ctl("Drag k/m (1/s)",0f,2f,.3f))
  "pressure"->listOf(ctl("Depth (m)",0f,30f,10f),ctl("ρ (kg/m³)",500f,1500f,1000f))
  "lightclock"->listOf(ctl("v/c",0f,.98f,.6f))
  "bonding"->listOf(ctl("Bond: 1 ionic / 2 covalent / 3 metallic",1f,3f,1f))
  "catalyst"->listOf(ctl("Eₐ (kJ/mol)",20f,100f,60f),ctl("Reduction (kJ/mol)",0f,19f,15f))
  "osmosis"->listOf(ctl("Left C (mol/L)",0f,2f,.2f),ctl("Right C (mol/L)",0f,2f,1f))
  "density"->listOf(ctl("Object ρ (kg/m³)",500f,1400f,900f))
  "collatz"->listOf(ctl("Start integer",2f,100f,27f))
  "modcircle"->listOf(ctl("Multiplier",2f,50f,2f),ctl("Points",10f,200f,100f))
  "koch"->listOf(ctl("Depth",0f,5f,3f))
  "sorting"->listOf(ctl("Bars",5f,30f,15f))
  "sampling"->listOf(ctl("Sample size",10f,1000f,100f),ctl("Population p",.05f,.95f,.5f))
  "ruin"->listOf(ctl("Initial capital",1f,19f,10f),ctl("P(win)",.1f,.9f,.5f))
  "simpson"->listOf(ctl("Separate groups",0f,1f,1f))
  "markov"->listOf(ctl("P(sun → rain)",.01f,.99f,.2f),ctl("P(rain → sun)",.01f,.99f,.4f))
  else->error("Unknown native lab: $full")
 }
 fun draw(c:Canvas,full:String,v:FloatArray,t:Double,seed:Int,context:Context){
  val key=full.removePrefix("native_");val a=v[0].toDouble();val b=v.getOrElse(1){1f}.toDouble();val d=v.getOrElse(2){0f}.toDouble()
  val g=LabGraphics(c);val random=Random(seed)
  fun f(x:Double)="%.3f".format(java.util.Locale.US,x)
  fun note(text:String)=g.caption(text)
  fun graph(y:(Double)->Double)=g.plot(-5.0,5.0,-5.0,5.0,y)
  when(key){
   "gas","lens","buoyancy"->ExtraLabs.draw(c,key,v,context,t)
   "circuit"->ExtraLabs.draw(c,"ohm",v,context,t)
   "orbit"->{val period=sqrt(b.pow(3)/a);val angle=t*2*PI/period;val radius=80+b*52
    g.ring(500.0,280.0,radius,MUTED);g.ball(500.0,280.0,22+a*9,ORANGE)
    g.ball(500+radius*cos(angle),280+radius*sin(angle),15.0,TEAL)
    note("Circular orbit: r=${f(b)} AU · M=${f(a)} M☉ · T=√(r³/M)=${f(period)} years")}
   "pendulum"->{val period=Physics.period(a,b);val theta=.3*cos(2*PI*t/period);val length=100+a*55
    g.line(500.0,90.0,500+length*sin(theta),90+length*cos(theta))
    g.ball(500+length*sin(theta),90+length*cos(theta),25.0,ORANGE)
    note("L=${f(a)} m · g=${f(b)} m/s² · T=2π√(L/g)=${f(period)} s")}
   "projectile"->{val th=b*PI/180;val range=a*a*sin(2*th)/d;val duration=2*a*sin(th)/d;val scale=700/max(range,a*a*sin(th).pow(2)/2/d);val time=t%duration;g.line(100.0,430.0,900.0,430.0);g.curve((0..150).map{val u=duration*it/150;100+a*cos(th)*u*scale to 430-(a*sin(th)*u-d*u*u/2)*scale});g.ball(100+a*cos(th)*time*scale,430-(a*sin(th)*time-d*time*time/2)*scale,12.0,ORANGE);note("Range = ${f(range)} m   Flight = ${f(duration)} s")}
   "spring"->{val omega=sqrt(b/a);val x=500+180*cos(omega*t);g.curve((0..40).map{100+(x-100)*it/40 to 260+if(it%2==0)15.0 else -15.0});g.ball(x,260.0,35.0,TEAL);note("T = 2π√(m/k) = ${f(2*PI/omega)} s")}
   "collision"->{val v1=(a-b)/(a+b)*d;val v2=2*a/(a+b)*d;val phase=t%6;val x1=if(phase<2)200+phase*100 else 400+(phase-2)*v1*25;val x2=if(phase<2)460.0 else 460+(phase-2)*v2*25;g.ball(x1,280.0,20+5*a,TEAL);g.ball(x2,280.0,20+5*b,ORANGE);note("Elastic collision, u₂ = 0: v₁ = ${f(v1)}, v₂ = ${f(v2)} m/s")}
   "standing"->{val n=a.roundToInt();g.curve((0..300).map{80+840.0*it/300 to 270-150*b*sin(n*PI*it/300)*cos(t*n)});note("Fixed ends: ${n-1} internal nodes · harmonic $n")}
   "molecule"->{val n=a.roundToInt();val angle=b*PI/180;val bonds=when(n){1->listOf(-52.25,52.25);2->listOf(0.0,180.0);else->listOf(0.0,120.0,240.0,300.0)};bonds.forEach{val q=it*PI/180+angle;g.line(500.0,270.0,500+150*cos(q),270+130*sin(q),MUTED,8f);g.ball(500+150*cos(q),270+130*sin(q),28.0,ORANGE)};g.ball(500.0,270.0,40.0,TEAL);note(when(n){1->"H₂O · 104.5° · schematic molecular model";2->"CO₂ · 180° · linear";else->"CH₄ · 109.5° · tetrahedral (2D schematic)"})}
   "states","diffusion"->{val spread=if(key=="diffusion")min(1.0,sqrt(t*a/8))else when{a<0->.04;a<100->.3;else->.9};repeat(80){i->val ox=200+(i%10)*60.0;val oy=150+(i/10)*35.0;val phase=i*2.4;g.ball((ox+sin(t*(1+spread)+phase)*120*spread).coerceIn(110.0,890.0),(oy+cos(t+phase)*90*spread).coerceIn(110.0,420.0),7.0,if(i%2==0)TEAL else ORANGE)};note(if(key=="diffusion")"Diffusion: characteristic distance ∝ √(Dt); schematic" else "Water at 1 atm: ${if(a<0)"solid"else if(a<100)"liquid"else"gas"} (schematic)")}
   "ph"->{g.block(150.0,150.0,700.0,220.0,Color.HSVToColor(floatArrayOf((a*18).toFloat(),.65f,.9f)));note("[H⁺] = 10⁻ᵖᴴ = ${"%.2e".format(10.0.pow(-a))} mol/L")}
   "reaction"->{val k=1e5*exp(-b*1000/(8.314*a));val fraction=1-exp(-k*t);g.bars(listOf(1-fraction,fraction),listOf("Reactant","Product"));note("First-order: k = A exp(−Eₐ/RT), A=10⁵ s⁻¹; k=${f(k)} s⁻¹")}
   "atom","periodic"->{val z=a.roundToInt();val shell=intArrayOf(min(z,2),min(max(z-2,0),8),min(max(z-10,0),8),max(z-18,0));g.ball(500.0,260.0,28.0,ORANGE);shell.forEachIndexed{i,n->if(n>0){val r=60+i*42.0;g.ring(500.0,260.0,r,MUTED);repeat(n){val q=2*PI*it/n+t*.3;g.ball(500+r*cos(q),260+r*sin(q),6.0,TEAL)}}};val symbols=listOf("H","He","Li","Be","B","C","N","O","F","Ne","Na","Mg","Al","Si","P","S","Cl","Ar","K","Ca");note("${symbols[z-1]} · Z=$z · neutral atom: $z electrons; shell schematic")}
   "solubility"->{val limit=.36*b;g.bars(listOf(min(a,limit),max(0.0,a-limit)),listOf("Dissolved g","Undissolved g"));note("NaCl at ~20°C: 36 g / 100 mL water; dissolved ${f(min(a,limit))} g")}
   "halflife"->{val now=t.coerceIn(0.0,5*a);val n=b*2.0.pow(-now/a);g.plot(0.0,5*a,0.0,b,{b*2.0.pow(-it/a)});g.ball(80+840*now/(5*a),450-360*n/b,11.0,ORANGE);note("N(t)=N₀·2^(−t/t½) · t=${f(t)} s · expected N=${f(n)}")}
   "waves","interference"->{g.curve((0..400).map{val x=it/400.0;80+840*x to 270-65*(sin(2*PI*a*x-t)+sin(2*PI*b*x-t+d*PI/180))});note("y = sin(2πf₁x−t) + sin(2πf₂x−t+φ)")}
   "circle"->{val q=a*PI/180;g.ring(500.0,260.0,160.0,MUTED);g.line(500.0,260.0,500+160*cos(q),260-160*sin(q),ORANGE);g.ball(500+160*cos(q),260-160*sin(q),10.0,TEAL);note("cos θ=${f(cos(q))}     sin θ=${f(sin(q))}")}
   "fractal"->{fun branch(x:Double,y:Double,length:Double,angle:Double,n:Int){if(n==0)return;val xx=x+length*cos(angle);val yy=y-length*sin(angle);g.line(x,y,xx,yy,TEAL,max(1f,n.toFloat()));branch(xx,yy,length*.7,angle+b*PI/180,n-1);branch(xx,yy,length*.7,angle-b*PI/180,n-1)};branch(500.0,450.0,110.0,PI/2,a.roundToInt());note("Binary fractal tree · length ratio 0.7 · depth ${a.roundToInt()}")}
   "golden"->{val phi=(1+sqrt(5.0))/2;val maxq=a*2*PI;g.curve((0..500).map{val q=maxq*it/500;val r=180*phi.pow((q-maxq)/(PI/2));500+r*cos(q) to 270-r*sin(q)});note("Golden spiral: radius × φ every quarter-turn; φ=${f(phi)}")}
   "quadratic"->{graph{a*it*it+b*it+d};val disc=b*b-4*a*d;note(if(abs(a)<.00001)"Linear case: y=${f(b)}x+${f(d)}"else if(disc<0)"Discriminant = ${f(disc)}: no real roots"else"Roots: ${f((-b-sqrt(disc))/(2*a))}, ${f((-b+sqrt(disc))/(2*a))}")}
   "tangent"->{graph{it*it};g.plot(-5.0,5.0,-5.0,5.0,{2*a*it-a*a},ORANGE,false);note("f(x)=x²; f′(${f(a)}) = ${f(2*a)}")}
   "fourier"->{val n=a.roundToInt();graph{x->(0 until n).sumOf{val k=2*it+1;(4/PI)*sin(k*x)/k}};note("Square-wave Fourier approximation · $n odd harmonics")}
   "galton"->{val rows=a.roundToInt();val counts=DoubleArray(rows+1);repeat(b.toInt()){var k=0;repeat(rows){if(random.nextBoolean())k++};counts[k]++};g.bars(counts.toList());note("Binomial distribution: $rows rows, p=0.5, ${b.toInt()} balls")}
   "coins"->{var heads=0;val n=b.toInt();g.plot(0.0,n.toDouble(),0.0,1.0,{a},ORANGE);val values=(1..n).map{if(random.nextDouble()<a)heads++;it.toDouble() to heads.toDouble()/it};g.data(values,0.0,n.toDouble(),0.0,1.0);note("Heads / trials = ${f(heads.toDouble()/n)} · expected ${f(a)}")}
   "dice"->{val count=DoubleArray(11);repeat(a.toInt()){count[random.nextInt(6)+random.nextInt(6)]++};g.bars(count.toList(),(2..12).map{it.toString()});note("Sum of two fair dice · ${a.toInt()} independent throws")}
   "montecarlo"->{var inside=0;g.block(320.0,100.0,320.0,320.0,0xffeeebff.toInt());repeat(a.toInt()){val x=random.nextDouble();val y=random.nextDouble();val hit=x*x+y*y<=1;if(hit)inside++;g.ball(320+x*320,420-y*320,2.0,if(hit)TEAL else ORANGE)};note("π ≈ 4·inside/N = ${f(4.0*inside/a.toInt())}")}
   "walk"->{var y=0.0;val n=a.toInt();val pts=(0 until n).map{y+=if(random.nextDouble()<b)1 else -1;it.toDouble() to y};g.data(pts,0.0,n.toDouble(),-n.toDouble(),n.toDouble());note("Biased random walk · P(right)=${f(b)} · final position=${f(y)}")}
   "clt"->{val n=a.toInt();val bins=DoubleArray(20);repeat(b.toInt()){val mean=(0 until n).sumOf{random.nextDouble()}/n;bins[(mean*20).toInt().coerceIn(0,19)]++};g.bars(bins.toList());note("Means of Uniform(0,1), n=$n; expected SE = ${f(sqrt(1.0/(12*n)))}")}
   "regression"->{val pts=(0..50).map{val x=it/5.0-5;x to (a*x+(random.nextDouble()-.5)*b)};g.scatter(pts,-5.0,5.0,-18.0,18.0);val fit=LabMath.fit(pts);g.plot(-5.0,5.0,-18.0,18.0,{fit.first*it+fit.second},ORANGE);note("OLS slope=${f(fit.first)}, intercept=${f(fit.second)}")}
   "incline"->{val q=a*PI/180;val acc=max(0.0,9.81*(sin(q)-b*cos(q)));g.line(170.0,130.0,850.0,130+260*sin(q),TEAL,5f);val u=min(1.0,acc*(t%6).pow(2)/60);g.ball(170+680*u,105+260*sin(q)*u,20.0,ORANGE);note("a=max(0,g(sin θ−μ cos θ))=${f(acc)} m/s²; from rest")}
   "doppler"->{val observed=b*343/(343-a);repeat(7){val r=20+(t*60+it*45)%300;g.ring(500-a/4,260.0,r,if(it%2==0)TEAL else MUTED)};g.ball(500.0,260.0,15.0,ORANGE);note("Stationary observer ahead: f′=f c/(c−v)=${f(observed)} Hz; c=343 m/s")}
   "heat"->{val mean=(a+b)/2;val left=mean+(a-mean)*exp(-d*t);val right=mean+(b-mean)*exp(-d*t);g.bars(listOf(left,right),listOf("Left °C","Right °C"));note("Two equal heat capacities: ΔT=ΔT₀ exp(−kt); mean=${f(mean)}°C")}
   "titration"->{val acid=b*.025;val base=.1*a/1000;val vol=.025+a/1000;val ph=if(abs(acid-base)<1e-10)7.0 else if(acid>base)-log10((acid-base)/vol)else 14+log10((base-acid)/vol);g.bars(listOf(acid*1000,base*1000),listOf("HCl mmol","NaOH mmol"));note("25 mL HCl + 0.1 M NaOH; ideal strong acid/base, pH=${f(ph)}")}
   "electrolysis"->{
    val mol=a*t/(2*96485.332);val fill=(mol/3e-4).coerceIn(0.0,1.0)
    g.ring(320.0,250.0,120.0,MUTED);g.ring(680.0,250.0,120.0,MUTED)
    g.block(245.0,405-220*fill,150.0,220*fill,0xff66c2b9.toInt())
    g.block(605.0,405-110*fill,150.0,110*fill,0xfff4bb6c.toInt())
    g.text("H₂",300.0,450.0,25f);g.text("O₂",660.0,450.0,25f)
    repeat(7){i->val rise=(t*70+i*43)%245;g.ball(320+(i%3-1)*28.0,405-rise,4.0,TEAL);g.ball(680+(i%3-1)*28.0,405-rise/2,4.0,ORANGE)}
    note("2H₂O → 2H₂ + O₂; n(H₂)=It/2F=${"%.2e".format(java.util.Locale.US,mol)} mol")
   }
   "flame"->{val n=a.roundToInt();val colors=listOf(0xffffbc20.toInt(),0xffb680ee.toInt(),0xff27b599.toInt(),0xffed6840.toInt());repeat(10){g.ball(500+sin(it.toDouble()+t)*it*3,400-it*23.0,65-it*4.0,colors[n-1])};note(listOf("Sodium: yellow","Potassium: lilac","Copper: blue-green","Calcium: orange-red")[n-1])}
   "equilibrium"->{val product=a/(a+b)*(1-exp(-(a+b)*t));g.bars(listOf(1-product,product),listOf("A fraction","B fraction"));note("A ⇌ B, first order; equilibrium B/A = kf/kr = ${f(a/b)}")}
   "primes"->{val n=a.toInt();for(i in 2..n){val prime=(2..sqrt(i.toDouble()).toInt()).none{i%it==0};val x=85.0+((i-2)%15)*55;val y=105.0+((i-2)/15)*34;g.ball(x,y,15.0,if(prime)TEAL else 0xffe5e1f3.toInt());g.text(i.toString(),x-8,y+5,13f,if(prime)Color.WHITE else MUTED)};note("Prime numbers have exactly two positive divisors")}
   "pythagoras"->{val scale=250/max(a,b);g.line(250.0,420.0,250+a*scale,420.0);g.line(250.0,420.0,250.0,420-b*scale);g.line(250.0,420-b*scale,250+a*scale,420.0,ORANGE);note("a²+b²=c² → c=${f(hypot(a,b))}")}
   "polypi"->{val n=a.roundToInt();g.ring(500.0,270.0,160.0,MUTED);g.curve((0..n).map{500+160*cos(it*2*PI/n) to 270+160*sin(it*2*PI/n)});note("${f(n*sin(PI/n))} < π < ${f(n*tan(PI/n))} · inscribed / circumscribed")}
   "lissajous"->{g.curve((0..600).map{val q=2*PI*it/600;500+280*sin(a.roundToInt()*q+d*PI/180) to 270-160*sin(b.roundToInt()*q)});note("x=sin(fx·t+φ), y=sin(fy·t)")}
   "series"->{val n=b.roundToInt();g.bars((0 until n).map{a.pow(it)});note("Σ rᵏ, k=0..${n-1}: ${(1-a.pow(n))/(1-a)}; limit=${f(1/(1-a))}")}
   "pascal"->{for(row in 0 until a.toInt()){var value=1L;for(k in 0..row){g.text(value.toString(),500-row*32.0+k*64,100+row*38.0,18f);value=value*(row-k)/(k+1)}};note("Each inner entry is the sum of the two entries above")}
   "monty"->{var stay=0;repeat(a.toInt()){if(random.nextInt(3)==0)stay++};g.bars(listOf(stay.toDouble(),a.toInt()-stay.toDouble()),listOf("Stay wins","Switch wins"));note("Host knows prize, always opens a goat: P(switch win)=2/3")}
   "birthday"->{val n=a.toInt();val prob=1-(0 until n).fold(1.0){acc,i->acc*(365-i)/365};g.bars(listOf(prob,1-prob),listOf("Shared birthday","All distinct"));note("$n people; P(shared birthday)=${f(prob*100)}%; uniform 365-day model")}
   "bayes"->{val prev=a/100;val se=b/100;val sp=d/100;val ppv=se*prev/(se*prev+(1-sp)*(1-prev));g.bars(listOf(se*prev,(1-sp)*(1-prev)),listOf("True positives","False positives"));note("P(condition | +)=${f(ppv*100)}% · teaching model, not clinical advice")}
   "benford"->{val digit=a.roundToInt().coerceIn(1,9);g.bars((1..9).map{log10(1+1.0/it)},(1..9).map{it.toString()},digit-1);note("Highlighted digit $digit: P(d)=log₁₀(1+1/d)=${f(log10(1+1.0/digit))}")}
   "median"->{val arr=listOf(2.0,3.0,4.0,5.0,a);g.bars(arr);note("Mean=${f(arr.average())}, median=${f(arr.sorted()[2])}; outliers shift the mean")}
   "streaks"->{var longest=0;var run=0;repeat(a.toInt()){val hit=random.nextDouble()<b;run=if(hit)run+1 else 0;longest=max(longest,run);g.ball(70+(it%40)*22.0,110+(it/40)*40.0,8.0,if(hit)TEAL else ORANGE)};note("Longest heads streak=$longest; streaks occur in independent sequences")}
   "freefall"->{val time=t%8;val fallen=if(b<1e-6).5*9.81*time*time else 9.81/b*(time-(1-exp(-b*time))/b);g.line(400.0,100.0,400.0,450.0,MUTED);g.ball(500.0,100+350*min(a,fallen)/a,20.0,ORANGE);note("Linear drag: dv/dt=g−kv/m; distance=${f(min(a,fallen))} m")}
   "pressure"->{val pressure=b*9.81*a/1000;val depth=110+330*a/30
    val blue=(245-(b-500)*.045).roundToInt().coerceIn(170,245)
    g.block(250.0,100.0,500.0,350.0,Color.rgb(194,blue,248))
    g.line(500.0,110.0,500.0,depth,MUTED,2f);g.ball(500.0,depth,15.0,ORANGE)
    g.block(730.0,420-pressure*.6,18.0,pressure*.6,TEAL)
    note("Gauge pressure ρgh=${f(pressure)} kPa; absolute adds atmospheric pressure")
   }
   "lightclock"->{val gamma=1/sqrt(1-a*a);val phase=t/gamma%2;val drift=a*95*sin(t*.6)
    g.line(350+drift,120.0,650+drift,120.0);g.line(350+drift,420.0,650+drift,420.0)
    g.line(500-a*100,120.0,500+a*100,420.0,TEAL,2f)
    g.ball(500+drift+a*55*phase,120+300*if(phase<1)phase else 2-phase,10.0,ORANGE)
    note("v/c=${f(a)} · γ=1/√(1−v²/c²)=${f(gamma)} · moving-clock pulse slows by γ")
   }
   "bonding"->{val n=a.roundToInt();g.ball(330.0,260.0,55.0,TEAL);g.ball(670.0,260.0,55.0,ORANGE);if(n==2){g.line(385.0,260.0,615.0,260.0,MUTED,8f);g.ball(485.0,260.0,8.0,NAVY);g.ball(515.0,260.0,8.0,NAVY)}else repeat(10){g.ball(250+it*50.0,330+sin(t+it)*20,5.0,NAVY)};note(when(n){1->"Ionic: electron transfer creates oppositely charged ions";2->"Covalent: atoms share electron pairs";else->"Metallic: positive ion lattice with delocalized electrons"})}
   "catalyst"->{g.plot(0.0,1.0,0.0,110.0,{a*sin(PI*it)},MUTED);g.plot(0.0,1.0,0.0,110.0,{(a-b)*sin(PI*it)},TEAL,false);note("Catalyst lowers Eₐ (${f(a)} → ${f(a-b)} kJ/mol), not equilibrium ΔG")}
   "osmosis"->{val level=70*tanh((b-a)*.7)*min(t/8,1.0)
    g.block(200.0,180+level,290.0,240-level,0xffd5edf7.toInt())
    g.block(510.0,180-level,290.0,240+level,0xffb7d7e7.toInt())
    g.line(500.0,120.0,500.0,440.0)
    repeat((a*12).roundToInt()){i->g.ball(225+(i%8)*30.0,390-(i/8)*25.0,4.0,TEAL)}
    repeat((b*12).roundToInt()){i->g.ball(535+(i%8)*30.0,390-(i/8)*25.0,4.0,ORANGE)}
    note("Ideal osmotic Δπ=RTΔC=${f(8.314*298*(b-a))} kPa; water rises on concentrated side")
   }
   "density"->{g.block(280.0,120.0,440.0,100.0,0xfff4cb78.toInt());g.block(280.0,220.0,440.0,100.0,0xffb6e2f4.toInt());g.block(280.0,320.0,440.0,100.0,0xffb99a80.toInt());val y=when{a<800->140.0;a<1000->220.0;a<1200->320.0;else->400.0};g.ball(500.0,y,22.0,NAVY);note("Schematic layers: 800 / 1000 / 1200 kg/m³; object=${f(a)} kg/m³")}
   "collatz"->{var n=a.toLong();val values=mutableListOf<Double>();repeat(400){if(n>1 || values.isEmpty()){values.add(n.toDouble());n=if(n%2L==0L)n/2 else 3*n+1}};values.add(1.0);g.data(values.mapIndexed{i,x->i.toDouble() to x},0.0,max(1,values.lastIndex).toDouble(),0.0,values.max());note("Even: n/2; odd: 3n+1 · steps=${values.size-1}; general conjecture unproved")}
   "modcircle"->{val n=b.roundToInt();val m=a.roundToInt();g.ring(500.0,260.0,180.0,MUTED);repeat(n){val q=2*PI*it/n;val r=2*PI*(it*m%n)/n;g.line(500+180*cos(q),260+180*sin(q),500+180*cos(r),260+180*sin(r),TEAL,1f)};note("Connect k → ${m}k mod $n")}
   "koch"->{var pts=listOf(300.0 to 390.0,700.0 to 390.0,500.0 to 44.0,300.0 to 390.0);repeat(a.toInt()){pts=pts.zipWithNext().flatMap{(u,w)->val dx=(w.first-u.first)/3;val dy=(w.second-u.second)/3;listOf(u,(u.first+dx) to (u.second+dy),(u.first+1.5*dx-sqrt(3.0)*dy/2) to (u.second+1.5*dy+sqrt(3.0)*dx/2),(u.first+2*dx) to (u.second+2*dy))}+pts.last()};g.curve(pts);note("Koch curve: segments ×4, each length ÷3 at each iteration")}
   "sorting"->{val n=a.toInt();val arr=MutableList(n){random.nextInt(10,100).toDouble()};var steps=(t*10).toInt().coerceAtMost(n*n);for(i in 0 until n)for(j in 0 until n-1-i)if(steps-->0&&arr[j]>arr[j+1]){val tmp=arr[j];arr[j]=arr[j+1];arr[j+1]=tmp};g.bars(arr);note("Bubble sort: compare adjacent values and swap if out of order")}
   "sampling"->{val se=sqrt(b*(1-b)/a);val bins=DoubleArray(20);repeat(200){val p=(0 until a.toInt()).count{random.nextDouble()<b}.toDouble()/a.toInt();bins[(p*20).toInt().coerceIn(0,19)]++};g.bars(bins.toList());note("Bernoulli sample proportion SE=√(p(1−p)/n)=${f(se)}")}
   "ruin"->{val capital=a.roundToInt();val ratio=(1-b)/b;val win=if(abs(b-.5)<1e-6)capital/20.0 else (1-ratio.pow(capital))/(1-ratio.pow(20));g.bars(listOf(win,1-win),listOf("Reach 20","Ruin at 0"));note("Gambler's ruin: initial=$capital, P(win step)=${f(b)}")}
   "simpson"->{val one=(0..15).map{val x=1+it/5.0;x to (5-.5*x+random.nextDouble()*.4)};val two=(0..15).map{val x=6+it/5.0;x to (12-.5*x+random.nextDouble()*.4)};g.scatter(one,0.0,10.0,0.0,12.0,TEAL);g.scatter(two,0.0,10.0,0.0,12.0,ORANGE);val sets=if(a>.5)listOf(one,two)else listOf(one+two);sets.forEach{val fit=LabMath.fit(it);g.plot(0.0,10.0,0.0,12.0,{x->fit.first*x+fit.second},NAVY,false)};note("Each group slopes down; combined data slopes up due to group differences")}
   "markov"->{val rain=a/(a+b);g.bars(listOf(1-rain,rain),listOf("Sun","Rain"))
    var wet=false;val steps=(t*2).toInt().coerceIn(0,2000)
    repeat(steps){wet=if(wet)random.nextDouble()>=b else random.nextDouble()<a}
    g.ball(if(wet)700.0 else 300.0,75.0,18.0,if(wet)TEAL else ORANGE)
    note("Current: ${if(wet)"rain"else"sun"} · stationary P(rain)=a/(a+b)=${f(rain)}")
   }
  }
 }
}

object LabMath {
 fun fit(pts:List<Pair<Double,Double>>):Pair<Double,Double>{val x=pts.map{it.first}.average();val y=pts.map{it.second}.average();val den=pts.sumOf{(it.first-x).pow(2)};val slope=if(den<1e-12)0.0 else pts.sumOf{(it.first-x)*(it.second-y)}/den;return slope to y-slope*x}
}
class LabGraphics(private val c:Canvas){
 private val p=Paint(Paint.ANTI_ALIAS_FLAG)
 fun text(s:String,x:Double,y:Double,size:Float=22f,color:Int=NAVY){p.color=color;p.style=Paint.Style.FILL;p.textSize=size;c.drawText(s,x.toFloat(),y.toFloat(),p)}
 fun caption(value:String){
  val words=value.split(' ');val lines=mutableListOf<String>();var current=""
  p.textSize=19f
  words.forEach{word->val candidate=if(current.isEmpty())word else "$current $word"
   if(current.isNotEmpty()&&p.measureText(candidate)>880f){lines.add(current);current=word}else current=candidate
  }
  if(current.isNotEmpty())lines.add(current)
  p.style=Paint.Style.FILL;p.color=0xfff5f8fa.toInt()
  c.drawRoundRect(44f,472f,956f,552f,14f,14f,p)
  lines.take(3).forEachIndexed{i,line->text(line,60.0,530.0-(min(lines.size,3)-1-i)*25,19f,NAVY)}
 }
 fun line(x:Double,y:Double,xx:Double,yy:Double,color:Int=TEAL,width:Float=3f){p.color=color;p.strokeWidth=width;p.style=Paint.Style.STROKE;c.drawLine(x.toFloat(),y.toFloat(),xx.toFloat(),yy.toFloat(),p)}
 fun ball(x:Double,y:Double,r:Double,color:Int){p.color=color;p.style=Paint.Style.FILL;c.drawCircle(x.toFloat(),y.toFloat(),r.toFloat(),p)}
 fun ring(x:Double,y:Double,r:Double,color:Int){p.color=color;p.strokeWidth=2f;p.style=Paint.Style.STROKE;c.drawCircle(x.toFloat(),y.toFloat(),r.toFloat(),p)}
 fun block(x:Double,y:Double,w:Double,h:Double,color:Int){p.color=color;p.style=Paint.Style.FILL;c.drawRoundRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),12f,12f,p)}
 fun curve(points:List<Pair<Double,Double>>,color:Int=TEAL){val path=Path();points.forEachIndexed{i,q->if(i==0)path.moveTo(q.first.toFloat(),q.second.toFloat())else path.lineTo(q.first.toFloat(),q.second.toFloat())};p.color=color;p.strokeWidth=3f;p.style=Paint.Style.STROKE;c.drawPath(path,p)}
 fun data(pts:List<Pair<Double,Double>>,xmin:Double,xmax:Double,ymin:Double,ymax:Double,color:Int=TEAL){c.save();c.clipRect(70f,65f,930f,455f);curve(pts.filter{it.first.isFinite()&&it.second.isFinite()}.map{80+840*(it.first-xmin)/(xmax-xmin) to 450-360*(it.second-ymin)/(ymax-ymin)},color);c.restore()}
 fun plot(xmin:Double,xmax:Double,ymin:Double,ymax:Double,f:(Double)->Double,color:Int=TEAL,axes:Boolean=true){if(axes){line(80.0,450.0,920.0,450.0,MUTED,1f);line(80.0,90.0,80.0,450.0,MUTED,1f);text("$xmin",80.0,480.0,14f);text("$xmax",860.0,480.0,14f)};data((0..300).map{val x=xmin+(xmax-xmin)*it/300;x to f(x)},xmin,xmax,ymin,ymax,color)}
 fun scatter(pts:List<Pair<Double,Double>>,xmin:Double,xmax:Double,ymin:Double,ymax:Double,color:Int=TEAL){pts.forEach{ball(80+840*(it.first-xmin)/(xmax-xmin),450-360*(it.second-ymin)/(ymax-ymin),4.0,color)}}
 fun bars(values:List<Double>,labels:List<String> = emptyList(),selected:Int=-1){
  if(values.isEmpty())return
  val high=max(1e-6,values.maxOf{abs(it)});val w=800.0/values.size
  val negatives=values.any{it<0};val baseline=if(negatives)300.0 else 420.0
  val maxHeight=if(negatives)140.0 else 300.0
  values.forEachIndexed{i,v->
   val height=maxHeight*abs(v)/high
   block(100+i*w,if(v>=0)baseline-height else baseline,w*.75,height,if(i==selected)0xffd97800.toInt()else if(i%2==0)TEAL else ORANGE)
   if(values.size<=12)text(labels.getOrElse(i){(i+1).toString()},100+i*w,470.0,16f)
  }
  if(negatives)line(80.0,baseline,920.0,baseline,MUTED,1f)
 }
}
