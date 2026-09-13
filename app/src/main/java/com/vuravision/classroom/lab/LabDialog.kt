package com.vuravision.classroom.lab

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object LabDialog {
    fun show(context:Context){
        val names=arrayOf("Projectile Motion","Pendulum","Spring / Hooke's Law","Quadratic Function")
        AlertDialog.Builder(context).setTitle("VuraVision Lab").setItems(names){_,which->when(which){0->projectile(context);1->pendulum(context);2->spring(context);else->quadratic(context)}}.setNegativeButton("Close",null).show()
    }
    private fun shell(c:Context,title:String,formula:String,onChange:(Float)->String){
        val box=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL;setPadding(36,18,36,18)}; val result=TextView(c).apply{textSize=22f;text=formula};val seek=SeekBar(c).apply{max=100;progress=50};box.addView(result);box.addView(seek);seek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onProgressChanged(s:SeekBar?,p:Int,f:Boolean){result.text=formula+"\n"+onChange(p/10f)};override fun onStartTrackingTouch(s:SeekBar?){};override fun onStopTrackingTouch(s:SeekBar?){}});AlertDialog.Builder(c).setTitle(title).setView(box).setPositiveButton("Done",null).show()
    }
    private fun projectile(c:Context)=shell(c,"Projectile Motion","Range = v² sin(2θ) / g") { v -> "v=${"%.1f".format(v)} m/s, θ=45°, range=${"%.2f".format(v*v/9.81)} m" }
    private fun pendulum(c:Context)=shell(c,"Pendulum","T = 2π√(L/g)") { l -> val len=(l/5f).coerceAtLeast(.1f);"L=${"%.2f".format(len)} m, T=${"%.2f".format(2*PI*kotlin.math.sqrt(len/9.81))} s" }
    private fun spring(c:Context)=shell(c,"Hooke's Law","F = kx") { x -> "k=10 N/m, x=${"%.2f".format(x/10)} m, F=${"%.2f".format(x)} N" }
    private fun quadratic(c:Context)=shell(c,"Quadratic Function","y = x² - 4x + 3") { x -> "x=${"%.1f".format(x-5)}, y=${"%.2f".format((x-5)*(x-5)-4*(x-5)+3)}" }
}
