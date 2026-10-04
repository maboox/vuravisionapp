package com.vuravision.classroom

import android.app.Activity
import android.app.Dialog
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.Executor

class VoiceSettingsUi(private val activity:Activity) {
    private fun tr(en:String,fa:String)=if(activity.resources.configuration.locales[0].language=="fa")fa else en
    private fun show(title:String,body:View):Dialog=MaterialAlertDialogBuilder(activity).setTitle(title)
        .setView(ScrollView(activity).apply{addView(body)}).setNegativeButton(activity.s("close"),null).show().also{Fonts.onShown(it)}
    fun connection(keys:VoiceKeyStore,worker:Executor,changed:()->Unit){
        val content=activity.column().apply{setPadding(activity.dp(16),activity.dp(8),activity.dp(16),activity.dp(8))}
        content.addView(activity.label(tr("Talk in Persian about the visible board and PDF. The assistant only observes and speaks; it cannot edit. While active, your microphone audio and visible workspace images are sent to Google's Gemini service. Internet and an available Live API model/quota are required.","دربارهٔ تخته و PDF نمایان، فارسی صحبت کنید. دستیار فقط می‌بیند و صحبت می‌کند و دسترسی ویرایش ندارد. هنگام فعال‌بودن، صدای میکروفن و تصویر فضای کار به سرویس جمینای گوگل ارسال می‌شود. اینترنت، مدل Live در دسترس و سهمیهٔ کافی لازم است."),14f,MUTED))
        val status=activity.label(if(keys.hasKey())activity.s("voice_key_saved")else activity.s("voice_key_missing"),14f)
        content.addView(status)
        content.addView(activity.label(tr("Google API key","کلید API گوگل"),14f,NAVY,true))
        val input=activity.field("").apply{
            hint=tr("Google AI Studio API key","کلید API از Google AI Studio")
            // Single-line configuration replaces the transformation on older Android releases.
            // Configure it first, then explicitly restore password masking.
            setSingleLine(true)
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            transformationMethod=android.text.method.PasswordTransformationMethod.getInstance()
            imeOptions=imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            layoutDirection=View.LAYOUT_DIRECTION_LTR
            textDirection=View.TEXT_DIRECTION_LTR
            gravity=android.view.Gravity.LEFT or android.view.Gravity.CENTER_VERTICAL
        }
        content.addView(input)
        content.addView(activity.label(tr("If a key is already saved, leave this field empty to keep it. Use Delete key to remove it.","اگر کلید ذخیره شده، برای حفظ آن کادر را خالی بگذارید. برای حذف از دکمهٔ حذف کلید استفاده کنید."),13f,MUTED))
        val dialog=show(activity.s("voice_assistant"),content)
        val save=activity.button(tr("Save key","ذخیرهٔ کلید")){}
        val remove=activity.button(tr("Delete key","حذف کلید")){}
        content.addView(save);content.addView(remove)
        save.setOnClickListener{
            val pasted=input.text?.toString().orEmpty()
            val value=VoiceApiKey.normalize(pasted)
            if(value.isEmpty()){
                if(keys.hasKey()){dialog.dismiss();return@setOnClickListener}
                input.error=activity.s("voice_key_missing");return@setOnClickListener
            }
            if(!VoiceApiKey.isAcceptable(value)){input.error=activity.s("voice_key_invalid");return@setOnClickListener}
            input.error=null
            changed();save.isEnabled=false;remove.isEnabled=false
            worker.execute{
                var success=true;try{keys.save(pasted)}catch(_:Exception){success=false}
                activity.runOnUiThread{
                    if(activity.isDestroyed || activity.isFinishing)return@runOnUiThread
                    if(success){input.setText("");dialog.dismiss();Toast.makeText(activity,activity.s("saved"),Toast.LENGTH_SHORT).show()}
                    else{save.isEnabled=true;remove.isEnabled=true;status.text=activity.s("voice_key_storage_error")}
                }
            }
        }
        remove.setOnClickListener{changed();keys.clear();input.setText("");status.text=activity.s("voice_key_missing")}
    }
    fun advanced(changed:()->Unit){
        val initial=VoiceSettings.load(activity)
        val content=activity.column().apply{setPadding(activity.dp(16),activity.dp(8),activity.dp(16),activity.dp(8))}
        content.addView(activity.label(tr("Administrator controls. The default assistant speaks Persian, introduces itself as VuraVision's educational assistant, remains patient and guides before giving a requested final answer. Changes take effect on the next conversation.","تنظیمات مدیر. دستیار پیش‌فرض فارسی صحبت می‌کند، خود را دستیار آموزشی ویوراویژن معرفی می‌کند، صبور می‌ماند و پیش از جواب نهایی راهنمایی می‌کند. تغییرها در مکالمهٔ بعدی اعمال می‌شوند."),14f,MUTED))
        fun spinner(title:String,entries:List<String>,selected:Int):Spinner{
            content.addView(activity.label(title,14f,NAVY,true))
            return Spinner(activity).apply{adapter=ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,entries);setSelection(selected.coerceAtLeast(0));content.addView(this)}
        }
        val tone=spinner(tr("Tone","لحن"),listOf(tr("Warm and respectful","گرم و محترمانه"),tr("Formal","رسمی"),tr("Calm and measured","آرام و شمرده")),VoiceSettings.tones.indexOf(initial.tone))
        val length=spinner(tr("Response length","طول پاسخ"),listOf(tr("Short","کوتاه"),tr("Medium","متوسط"),tr("Detailed","مفصل")),VoiceSettings.lengths.indexOf(initial.length))
        val voice=spinner(tr("Voice","صدا"),VoiceSettings.voices,VoiceSettings.voices.indexOf(initial.voice))
        content.addView(activity.label(tr("Additional behavior instructions","دستورهای تکمیلی رفتار"),14f,NAVY,true))
        val extra=activity.field(initial.extra).apply{minLines=3;gravity=android.view.Gravity.TOP;inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE};content.addView(extra)
        content.addView(activity.label(tr("Panel features and specifications","امکانات و مشخصات پنل"),14f,NAVY,true))
        val panel=activity.field(initial.panelNotes).apply{minLines=3;gravity=android.view.Gravity.TOP;inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE;hint=tr("Known features only, e.g. panel size, inputs, OPS and microphone","ویژگی‌های معلوم؛ مثل اندازه، ورودی‌ها، OPS و میکروفن")};content.addView(panel)
        content.addView(activity.label(tr("Google Live model ID","شناسهٔ مدل Live گوگل"),14f,NAVY,true))
        val model=activity.field(initial.model).apply{setSingleLine(true);textDirection=View.TEXT_DIRECTION_LTR};content.addView(model)
        val times=listOf(5,15,30,60)
        val timeout=spinner(tr("Maximum conversation duration (minutes)","حداکثر زمان مکالمه (دقیقه)"),times.map{it.toString()},times.indexOf(initial.sessionMinutes))
        val intervals=listOf(2,3,5,10)
        val frames=spinner(tr("Screen refresh interval (seconds)","فاصلهٔ تازه‌سازی تصویر (ثانیه)"),intervals.map{it.toString()},intervals.indexOf(initial.frameSeconds))
        val dialog=show(activity.s("voice_behavior"),content)
        content.addView(activity.button(activity.s("apply"),true){
            val selected=model.text?.toString().orEmpty().trim().removePrefix("models/")
            if(!VoiceSettings.validModel(selected)){model.error=activity.s("voice_model_error");return@button}
            val updated=VoiceSettings(VoiceSettings.tones[tone.selectedItemPosition],VoiceSettings.lengths[length.selectedItemPosition],extra.text?.toString().orEmpty(),panel.text?.toString().orEmpty(),selected,VoiceSettings.voices[voice.selectedItemPosition],intervals[frames.selectedItemPosition],times[timeout.selectedItemPosition])
            updated.save(activity);changed();dialog.dismiss();Toast.makeText(activity,activity.s("saved"),Toast.LENGTH_SHORT).show()
        })
        content.addView(activity.button(tr("Restore assistant defaults","بازنشانی رفتار پیش‌فرض دستیار")){
            VoiceSettings().save(activity);changed();dialog.dismiss();Toast.makeText(activity,activity.s("saved"),Toast.LENGTH_SHORT).show()
        })
    }
}
