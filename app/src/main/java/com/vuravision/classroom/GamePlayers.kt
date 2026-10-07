package com.vuravision.classroom

import android.content.Context
import android.text.InputFilter
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object GamePlayers {
    fun name(c:Context,index:Int)=c.getSharedPreferences("vura",0).getString("gamePlayer$index",null)?.takeIf{it.isNotBlank()}?:c.s("player${index+1}")
    fun display(c:Context,key:String)=when(key){"player1"->name(c,0);"player2"->name(c,1);else->c.s(key)}
    fun edit(c:Context,index:Int,done:()->Unit){
        val input=c.field(name(c,index),c.tr("Player name","نام بازیکن")).apply{filters=arrayOf(InputFilter.LengthFilter(24));setSingleLine(true);selectAll()}
        val body=c.column().apply{pad(16);addView(input)}
        val d=MaterialAlertDialogBuilder(c).setTitle(c.tr("Player name","نام بازیکن")).setView(body).setPositiveButton(c.s("apply"),null).setNeutralButton(c.s("reset")){_,_->c.getSharedPreferences("vura",0).edit().remove("gamePlayer$index").apply()}.setNegativeButton(c.s("cancel"),null).create()
        d.setOnDismissListener{done()};d.show();Fonts.onShown(d)
        d.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{val value=input.text.toString().trim();if(value.isBlank())input.error=c.s("empty")else{c.getSharedPreferences("vura",0).edit().putString("gamePlayer$index",value).apply();d.dismiss()}}
    }
}
