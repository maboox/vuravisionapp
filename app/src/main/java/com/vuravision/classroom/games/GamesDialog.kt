package com.vuravision.classroom.games

import android.app.AlertDialog
import android.content.Context
import android.os.CountDownTimer
import android.widget.*
import kotlin.random.Random

object GamesDialog {
    fun show(c:Context){ val names=arrayOf("Tap Race","Math Race","Bigger Number","Reaction Race","Tic Tac Toe");AlertDialog.Builder(c).setTitle("VuraVision Games").setItems(names){_,i->when(i){0->tap(c);1->math(c);2->bigger(c);3->reaction(c);else->tic(c)}}.setNegativeButton("Close",null).show() }
    private fun tap(c:Context){var a=0;var b=0;val row=LinearLayout(c);val aa=Button(c).apply{text="Player A: 0"};val bb=Button(c).apply{text="Player B: 0"};row.addView(aa,LinearLayout.LayoutParams(0,220,1f));row.addView(bb,LinearLayout.LayoutParams(0,220,1f));aa.setOnClickListener{a++;aa.text="Player A: $a"};bb.setOnClickListener{b++;bb.text="Player B: $b"};AlertDialog.Builder(c).setTitle("Tap Race").setView(row).setPositiveButton("Finish",null).show()}
    private fun math(c:Context){val x=Random.nextInt(2,20);val y=Random.nextInt(2,20);val input=EditText(c).apply{inputType=2};AlertDialog.Builder(c).setTitle("Math Race: $x + $y = ?").setView(input).setPositiveButton("Check"){_,_->Toast.makeText(c,if(input.text.toString().toIntOrNull()==x+y)"Correct" else "Try again",Toast.LENGTH_SHORT).show()}.show()}
    private fun bigger(c:Context){val a=Random.nextInt(1,999);val b=Random.nextInt(1,999);AlertDialog.Builder(c).setTitle("Bigger Number").setMessage("Which is bigger?").setPositiveButton("$a"){_,_->Toast.makeText(c,if(a>b)"Correct" else "No",Toast.LENGTH_SHORT).show()}.setNegativeButton("$b"){_,_->Toast.makeText(c,if(b>a)"Correct" else "No",Toast.LENGTH_SHORT).show()}.show()}
    private fun reaction(c: Context) {
        val button = Button(c).apply {
            text = "Wait…"
            isEnabled = false
        }
        val dialog = AlertDialog.Builder(c)
            .setTitle("Reaction Race")
            .setView(button)
            .create()
        val startTime = longArrayOf(0L)

        object : CountDownTimer(Random.nextLong(1200, 3200), 1000) {
            override fun onTick(millisUntilFinished: Long) = Unit

            override fun onFinish() {
                button.isEnabled = true
                button.text = "TAP!"
                startTime[0] = System.currentTimeMillis()
            }
        }.start()

        button.setOnClickListener {
            if (startTime[0] > 0L) {
                button.text = "${System.currentTimeMillis() - startTime[0]} ms"
            }
        }
        dialog.show()
    }
    private fun tic(c:Context){val grid=GridLayout(c).apply{columnCount=3;rowCount=3};val cells=Array(9){Button(c).apply{text=" ";textSize=28f}};var turn="X";cells.forEach{btn->grid.addView(btn,GridLayout.LayoutParams().apply{width=180;height=150});btn.setOnClickListener{if(btn.text.toString().isBlank()){btn.text=turn;turn=if(turn=="X")"O" else "X"}}};AlertDialog.Builder(c).setTitle("Tic Tac Toe").setView(grid).setPositiveButton("Done",null).show()}
}
