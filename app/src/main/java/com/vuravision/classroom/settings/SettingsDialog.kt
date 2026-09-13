package com.vuravision.classroom.settings

import android.app.AlertDialog
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object SettingsDialog {
    fun show(c:Context){
        val items=arrayOf("English","فارسی","About / Engineering unlock")
        AlertDialog.Builder(c).setTitle("VuraVision Settings").setItems(items){_,i->when(i){
            0->AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
            1->AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fa"))
            else->AlertDialog.Builder(c).setTitle("VuraVision").setMessage("VuraVision Classroom Suite\nVersion 0.1.0\nEngineering Mode is available from the main menu in debug builds; release builds should hide it behind the 7-tap unlock in the next hardening pass.").setPositiveButton("OK",null).show()
        }}.setNegativeButton("Close",null).show()
    }
}
