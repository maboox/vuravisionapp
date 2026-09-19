package com.vuravision.classroom
import kotlin.math.*
/** Explicit polynomial parser. No sample-based guesses about whether an equation is linear. */
object SmartMath {
 private class Poly(val c:DoubleArray){
  init{require(c.all{it.isFinite()}){"Numbers are too large"}}
  operator fun plus(b:Poly)=Poly(DoubleArray(3){c[it]+b.c[it]})
  operator fun unaryMinus()=Poly(DoubleArray(3){-c[it]})
  operator fun minus(b:Poly)=this+(-b)
  operator fun times(b:Poly):Poly {val out=DoubleArray(5);for(i in 0..2)for(j in 0..2)out[i+j]+=c[i]*b.c[j];require(out[3]==0.0&&out[4]==0.0){"Only linear and quadratic equations are supported"};return Poly(out.copyOf(3))}
  operator fun div(b:Poly):Poly{require(b.c[1]==0.0&&b.c[2]==0.0&&b.c[0]!=0.0){"Division must be by a nonzero constant"};return Poly(DoubleArray(3){c[it]/b.c[0]})}
 }
 private class Parser(val s:String){var i=0
  fun take(c:Char)=if(i<s.length&&s[i]==c){i++;true}else false
  fun parse():Poly {val p=expr();require(i==s.length){"Unexpected symbol near ${s.drop(i)}"};return p}
  fun expr():Poly{var p=term();while(i<s.length){p=when{take('+')->p+term();take('-')->p-term();else->return p}};return p}
  fun term():Poly{var p=unary();while(i<s.length){p=when{take('*')->p*unary();take('/')->p/unary();else->return p}};return p}
  fun unary():Poly=when{take('+')->unary();take('-')->-unary();else->power()}
  fun power():Poly{val p=atom();if(!take('^'))return p;val start=i;while(i<s.length&&s[i].isDigit())i++;val n=s.substring(start,i).toIntOrNull();require(n!=null&&n in 0..2){"Use powers 0, 1 or 2"};return when(n){0->Poly(doubleArrayOf(1.0,0.0,0.0));1->p;else->p*p}}
  fun atom():Poly{if(take('(')){val p=expr();require(take(')')){"Close the parenthesis"};return p};if(take('x'))return Poly(doubleArrayOf(0.0,1.0,0.0));val start=i;while(i<s.length&&(s[i].isDigit()||s[i]=='.'))i++;require(i>start){"Expected a number, x or ("};return Poly(doubleArrayOf(s.substring(start,i).toDouble(),0.0,0.0))}
 }
 fun solve(input:String):String{val s=MathTools().normalize(input);val sides=s.split('=');require(sides.size==2){"Use an equation such as 2x+3=11"};val c=(Parser(sides[0]).parse()-Parser(sides[1]).parse()).c;val a=c[2];val b=c[1];val d=c[0];val f=MathTools.Companion::format
  if(abs(a)<1e-12){if(abs(b)<1e-12)return if(abs(d)<1e-12)"All real x"else"No solution";return "x = ${f(-d/b)}"}
  val disc=b*b-4*a*d;require(disc.isFinite()){"Numbers are too large"};if(disc<0)return "No real roots";val r1=(-b-sqrt(disc))/(2*a);val r2=(-b+sqrt(disc))/(2*a);return if(abs(r1-r2)<1e-10)"x = ${f(r1)}"else"x = ${f(min(r1,r2))} or x = ${f(max(r1,r2))}"
 }
 fun result(input:String):String=if(input.contains('='))solve(input)else {require(!Regex("(?<![a-z])x(?![a-z])").containsMatchIn(MathTools().normalize(input))){"Use an equation to solve x, or Graph mode to plot a function"};MathTools.format(MathTools().evaluate(input))}
}
