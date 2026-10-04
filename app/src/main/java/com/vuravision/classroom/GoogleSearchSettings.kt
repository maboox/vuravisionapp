package com.vuravision.classroom

import android.app.Activity
import android.content.Context
import android.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder

data class GoogleSearchSettings(val destination:String="ask",val adjacent:Boolean=false) {
    companion object {
        val destinations=listOf("ask","floating","browser")
        fun load(context:Context):GoogleSearchSettings {
            val p=context.getSharedPreferences("vura",0)
            return GoogleSearchSettings(p.getString("searchDestination","ask").orEmpty().takeIf{it in destinations}?:"ask",p.getBoolean("searchAdjacent",false))
        }
    }
    fun save(context:Context){require(destination in destinations);context.getSharedPreferences("vura",0).edit().putString("searchDestination",destination).putBoolean("searchAdjacent",adjacent).apply()}
    fun needsReview(candidates:List<String>,alwaysReview:Boolean):Boolean {
        val query=candidates.firstOrNull().orEmpty().trim()
        return destination=="ask" || alwaysReview || candidates.distinct().size!=1 || query.isEmpty() || query.length>GoogleSearchQuery.MAX_LENGTH
    }
}

class GoogleSearchSettingsUi(private val activity:Activity) {
    private fun tr(en:String,fa:String)=if(activity.resources.configuration.locales[0].language=="fa")fa else en
    fun show(){
        val initial=GoogleSearchSettings.load(activity)
        val content=activity.column().apply{pad(16)}
        content.addView(activity.label(tr("Choose where Google searches open. A fixed destination skips the chooser. Ambiguous recognition still allows text correction. Enable smart review in recognition settings to review every query.","محل باز شدن جستجوی گوگل را انتخاب کنید. مقصد ثابت پنجرهٔ انتخاب را حذف می‌کند. برای تشخیص نامطمئن امکان اصلاح متن باقی می‌ماند. برای بررسی تمام جستجوها، بازبینی هوشمند را در تنظیمات تشخیص روشن کنید."),14f,MUTED))
        val destination=Spinner(activity).apply{adapter=ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,listOf(tr("Ask each time","هر بار بپرس"),tr("Internal floating browser","مرورگر داخلی شناور"),tr("Default device browser","مرورگر پیش‌فرض دستگاه")));setSelection(GoogleSearchSettings.destinations.indexOf(initial.destination))};content.addView(destination)
        val adjacent=CheckBox(activity).apply{text=tr("Request external browser beside the board (if supported)","درخواست مرورگر خارجی کنار تخته (اگر دستگاه پشتیبانی کند)");isChecked=initial.adjacent};content.addView(adjacent)
        val dialog=MaterialAlertDialogBuilder(activity).setTitle(activity.s("google_search_settings")).setView(content).setNegativeButton(activity.s("cancel"),null).setPositiveButton(activity.s("apply")){_,_->GoogleSearchSettings(GoogleSearchSettings.destinations[destination.selectedItemPosition],adjacent.isChecked).save(activity)}.show()
        Fonts.onShown(dialog)
    }
}
