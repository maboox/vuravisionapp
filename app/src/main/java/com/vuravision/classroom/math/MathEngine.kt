package com.vuravision.classroom.math

import kotlin.math.*

interface MathEngine { fun solveLinear(expression:String): Result<String>; fun evaluate(expression:String,x:Double=0.0): Result<Double> }

class SimpleOfflineMathEngine:MathEngine {
    override fun solveLinear(expression:String):Result<String> = runCatching {
        // Supports ax+b=c and ax-b=c forms, intentionally strict and deterministic.
        val s=expression.replace(" ","").lowercase(); val parts=s.split("="); require(parts.size==2){"Expected one ="}
        val rhs=parts[1].toDouble(); val left=parts[0]; val xi=left.indexOf('x'); require(xi>=0){"Expected x"}
        val aText=left.substring(0,xi); val a=when(aText){"","+"->1.0;"-"->-1.0;else->aText.toDouble()}
        val bText=left.substring(xi+1); val b=if(bText.isBlank())0.0 else bText.toDouble()
        require(a!=0.0){"Coefficient cannot be zero"}; val x=(rhs-b)/a
        "x = ${format(x)}"
    }
    override fun evaluate(expression:String,x:Double):Result<Double> = runCatching { Parser(expression.replace("x","($x)")).parse() }
    private fun format(v:Double)=if(abs(v-v.roundToLong())<1e-10)v.roundToLong().toString() else "%.6f".format(v).trimEnd('0').trimEnd('.')

    private class Parser(private val s:String){ var i=0
        fun parse():Double{val v=expr();skip();require(i==s.length){"Unexpected token"};return v}
        private fun expr():Double{var v=term();while(true){skip();v=when{eat('+')->v+term();eat('-')->v-term();else->return v}}}
        private fun term():Double{var v=power();while(true){skip();v=when{eat('*')->v*power();eat('/')->v/power();else->return v}}}
        private fun power():Double{var v=unary();skip();if(eat('^'))v=v.pow(power());return v}
        private fun unary():Double{skip();return when{eat('+')->unary();eat('-')->-unary();eat('(')->expr().also{require(eat(')'))};else->number()}}
        private fun number():Double{skip();val st=i;while(i<s.length&&(s[i].isDigit()||s[i]=='.'))i++;require(i>st){"Number expected"};return s.substring(st,i).toDouble()}
        private fun skip(){while(i<s.length&&s[i].isWhitespace())i++}; private fun eat(c:Char):Boolean{skip();if(i<s.length&&s[i]==c){i++;return true};return false}
    }
}
