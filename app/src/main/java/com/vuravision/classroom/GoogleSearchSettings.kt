package com.vuravision.classroom

import android.app.Activity
import android.content.Context
import android.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder

data class GoogleSearchSettings(val destination:String="ask",val adjacent:Boolean=false,val review:Boolean=true) {
    companion object {
        val destinations=listOf("ask","floating","browser")
        fun load(context:Context):GoogleSearchSettings {
            val p=context.getSharedPreferences("vura",0)
            return GoogleSearchSettings(p.getString("searchDestination","ask").orEmpty().takeIf{it in destinations}?:"ask",p.getBoolean("searchAdjacent",false),p.getBoolean("searchReview",true))
        }
    }
    fun save(context:Context){require(destination in destinations);context.getSharedPreferences("vura",0).edit().putString("searchDestination",destination).putBoolean("searchAdjacent",adjacent).putBoolean("searchReview",review).apply()}
    fun needsReview(candidates:List<String>,alwaysReview:Boolean):Boolean {
        val query=candidates.firstOrNull().orEmpty().trim()
        return destination=="ask" || review || query.isEmpty() || query.length>GoogleSearchQuery.MAX_LENGTH
    }
}

class GoogleSearchSettingsUi(private val activity:Activity) {
    private fun tr(en:String,fa:String)=if(activity.resources.configuration.locales[0].language=="fa")fa else en
    fun show(back:(()->Unit)?=null){
        val initial=GoogleSearchSettings.load(activity)
        val content=activity.column().apply{pad(16)}
        content.addView(activity.infoTitle(activity.s("google_search_settings"),tr("Choose a browser and decide independently whether to review the text. With review off, the first recognition result is searched directly.","مرورگر و بازبینی متن را مستقل انتخاب کنید. با بازبینی خاموش، اولین نتیجهٔ تشخیص مستقیم جست‌وجو می‌شود.")))
        val destination=Spinner(activity).apply{adapter=OptionAdapter(activity,listOf(tr("Ask each time","هر بار بپرس"),tr("Internal floating browser","مرورگر داخلی شناور"),tr("Default device browser","مرورگر پیش‌فرض دستگاه")));setSelection(GoogleSearchSettings.destinations.indexOf(initial.destination))};content.addView(destination)
        val adjacent=CheckBox(activity).apply{text=tr("Request external browser beside the board (if supported)","درخواست مرورگر خارجی کنار تخته (اگر دستگاه پشتیبانی کند)");isChecked=initial.adjacent};content.addView(adjacent)
        val review=Switch(activity).apply{text=tr("Review text before search","بررسی متن قبل از جست‌وجو");isChecked=initial.review};content.addView(review)
        val dialog=activity.backDialogBuilder(activity.s("google_search_settings"),back).setView(content).setNegativeButton(activity.s("cancel"),null).setOnCancelListener{back?.invoke()}.setPositiveButton(activity.s("apply")){_,_->GoogleSearchSettings(GoogleSearchSettings.destinations[destination.selectedItemPosition],adjacent.isChecked,review.isChecked).save(activity)}.show()
        Fonts.onShown(dialog)
    }
}
