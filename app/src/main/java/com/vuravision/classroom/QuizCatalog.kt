package com.vuravision.classroom
import kotlin.math.*
import kotlin.random.Random
data class QuizQuestion(val prompt:String,val options:List<String>,val correct:Int)
object QuizCatalog {
 val keys=listOf("subtract","multiply","divide","missing_number","squares","cubes","powers","square_root","fractions","percent","decimal","sequence","reverse_sequence","prime","divisible","remainder","gcd","lcm","perimeter","area","triangle_angle","clock_math","unit_length","mean")
 fun question(key:String,r:Random):QuizQuestion {
  val a=r.nextInt(2,13);val b=r.nextInt(2,10)
  fun numeric(prompt:String,answer:Int):QuizQuestion {val values=linkedSetOf(answer);while(values.size<3)values.add((answer+r.nextInt(-9,10)).coerceAtLeast(0));val choices=values.shuffled(r);return QuizQuestion(prompt,choices.map{it.toString()},choices.indexOf(answer))}
  fun gcd(x:Int,y:Int):Int {var m=x;var n=y;while(n!=0){val t=m%n;m=n;n=t};return m}
  return when(key) {
   "subtract"->numeric("${a+b} − $b = ?",a)
   "multiply"->numeric("$a × $b = ?",a*b)
   "divide"->numeric("${a*b} ÷ $b = ?",a)
   "missing_number"->numeric("? + $b = ${a+b}",a)
   "squares"->numeric("$a² = ?",a*a)
   "cubes"->numeric("${a%5+2}³ = ?",(a%5+2).let{it*it*it})
   "powers"->numeric("2^${b%5+1} = ?",1 shl (b%5+1))
   "square_root"->numeric("√${a*a} = ?",a)
   "fractions"->numeric("$a/$b + $b/$b = ?/$b",a+b)
   "percent"->numeric("${b*10}% × ${a*10} = ?",a*b)
   "decimal"->{val n=a/10.0;val v=listOf(n,n+.1,n+.2).shuffled(r);QuizQuestion("$a ÷ 10 = ?",v.map{"%.1f".format(java.util.Locale.US,it)},v.indexOf(n))}
   "sequence"->numeric("$a, ${a+b}, ${a+2*b}, ?",a+3*b)
   "reverse_sequence"->numeric("${a+3*b}, ${a+2*b}, ${a+b}, ?",a)
   "prime"->{val n=r.nextInt(2,70);QuizQuestion("$n · Prime? / عدد اول؟",listOf("yes","no"),if((2 until n).none{n%it==0})0 else 1)}
   "divisible"->{val n=r.nextInt(10,90);QuizQuestion("$n ÷ $b · Integer? / بخش‌پذیر؟",listOf("yes","no"),if(n%b==0)0 else 1)}
   "remainder"->numeric("${a*3} mod $b = ?",a*3%b)
   "gcd"->numeric("GCD / ب.م.م ($a, $b)",gcd(a,b))
   "lcm"->numeric("LCM / ک.م.م ($a, $b)",a*b/gcd(a,b))
   "perimeter"->numeric("▭ $a × $b · Perimeter / محیط؟",2*(a+b))
   "area"->numeric("▭ $a × $b · Area / مساحت؟",a*b)
   "triangle_angle"->numeric("△ $a°, ${b*10}°, ?",180-a-b*10)
   "clock_math"->numeric("$a h = ? min",a*60)
   "unit_length"->numeric("$a m = ? cm",a*100)
   "mean"->numeric("Mean / میانگین: $a, ${a+b}, ${a+2*b}",a+b)
   else->error("Unknown quiz: $key")
  }
 }
}
