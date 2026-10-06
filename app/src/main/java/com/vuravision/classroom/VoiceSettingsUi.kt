package com.vuravision.classroom

import android.app.Activity
import android.app.Dialog
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.Executor

class VoiceSettingsUi(private val activity:Activity,private val back:(()->Unit)?=null) {
    private fun tr(en:String,fa:String)=if(activity.resources.configuration.locales[0].language=="fa")fa else en
    private fun show(title:String,body:View):Dialog {
        var shown:Dialog?=null
        return MaterialAlertDialogBuilder(activity).setCustomTitle(activity.navigationHeading(title,back?.let{action->{shown?.dismiss();action()}}))
            .setView(ScrollView(activity).apply{addView(body)}).setNegativeButton(activity.s("close"),null).setOnCancelListener{d->d.dismiss();back?.invoke()}.show().also{shown=it;Fonts.onShown(it)}
    }
    fun connection(keys:VoiceKeyStore,worker:Executor,changed:()->Unit)=connectionImpl({keys},worker,changed)
    fun connection(keys:VoiceKeys,worker:Executor,changed:()->Unit)=connectionImpl(keys::forProvider,worker,changed)
    private fun connectionImpl(keys:(String)->VoiceKeyStore,worker:Executor,changed:()->Unit){
        val initial=VoiceSettings.load(activity)
        val content=activity.column().apply{setPadding(activity.dp(16),activity.dp(8),activity.dp(16),activity.dp(8))}
        content.addView(activity.label(tr("Talk in Persian about the visible board and PDF. The assistant only observes and speaks. While active, microphone audio and workspace images go to your selected service. Each service has its own encrypted key. Internet, model access and quota/credit are required.","دربارهٔ تخته و PDF نمایان فارسی صحبت کنید. دستیار فقط می‌بیند و صحبت می‌کند. هنگام فعال‌بودن، صدا و تصویر فضای کار به سرویس انتخابی ارسال می‌شود. کلید هر سرویس جدا و رمزگذاری‌شده ذخیره می‌شود. اینترنت، دسترسی مدل و سهمیه یا اعتبار کافی لازم است."),14f,MUTED))
        content.addView(activity.label(tr("Service","سرویس"),14f,NAVY,true))
        val provider=Spinner(activity).apply{adapter=OptionAdapter(activity,listOf("Google Gemini","OpenAI","OpenRouter"));setSelection(VoiceSettings.providers.indexOf(initial.provider))};content.addView(provider)
        val status=activity.label("",14f);content.addView(status)
        val note=activity.label("",13f,MUTED);content.addView(note)
        content.addView(activity.label(tr("API key","کلید API"),14f,NAVY,true))
        val input=activity.field("").apply{
            hint=tr("API key for the selected service","کلید API سرویس انتخابی")
            setSingleLine(true)
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            transformationMethod=android.text.method.PasswordTransformationMethod.getInstance()
            imeOptions=imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            layoutDirection=View.LAYOUT_DIRECTION_LTR;textDirection=View.TEXT_DIRECTION_LTR
            gravity=android.view.Gravity.LEFT or android.view.Gravity.CENTER_VERTICAL
        };content.addView(input)
        content.addView(activity.label(tr("Leave an existing key blank to keep it. Delete key affects only the selected service.","برای حفظ کلید ذخیره‌شده کادر را خالی بگذارید. حذف کلید فقط روی سرویس انتخابی اعمال می‌شود."),13f,MUTED))
        content.addView(activity.label(tr("Conversation model ID","شناسهٔ مدل مکالمه"),14f,NAVY,true))
        val model=activity.field("").apply{setSingleLine(true);textDirection=View.TEXT_DIRECTION_LTR;layoutDirection=View.LAYOUT_DIRECTION_LTR};content.addView(model)
        val presets=activity.row();content.addView(activity.scrollRow(presets))
        content.addView(activity.label(tr("Voice","صدا"),14f,NAVY,true))
        val voice=Spinner(activity);content.addView(voice)
        val speechGroup=activity.column()
        speechGroup.addView(activity.label(tr("Speech model (OpenRouter)","مدل تولید صدا (OpenRouter)"),14f,NAVY,true))
        val speech=Spinner(activity).apply{adapter=OptionAdapter(activity,VoiceSettings.routerSpeechModels);setSelection(VoiceSettings.routerSpeechModels.indexOf(initial.routerSpeechModel).coerceAtLeast(0))};speechGroup.addView(speech);content.addView(speechGroup)
        fun selected()=VoiceSettings.providers[provider.selectedItemPosition]
        fun refresh(){
            val p=selected();input.setText("");input.error=null;model.error=null
            status.text=activity.s(if(keys(p).hasKey())"voice_key_saved"else"voice_key_missing")
            val voices=when(p){"openai"->VoiceSettings.openaiVoices;"openrouter"->VoiceSettings.routerVoices;else->VoiceSettings.voices}
            voice.adapter=OptionAdapter(activity,voices)
            voice.setSelection(voices.indexOf(when(p){"openai"->initial.openaiVoice;"openrouter"->initial.routerVoice;else->initial.voice}).coerceAtLeast(0))
            model.setText(when(p){"openai"->initial.openaiModel;"openrouter"->initial.routerModel;else->initial.model})
            speechGroup.visibility=if(p=="openrouter")View.VISIBLE else View.GONE
            note.text=when(p){
                "openai"->tr("OpenAI Realtime: live voice and images. Enter your own OpenAI API key; API usage is billed separately from ChatGPT.","OpenAI Realtime: صدا و تصویر زنده. کلید API خودتان را وارد کنید؛ مصرف API از اشتراک ChatGPT جداست.")
                "openrouter"->tr("Turn-based conversation: wait for Listening before speaking again. The model must accept audio + images. Recommended: google/gemini-3.5-flash-lite. Speech uses the separate model below; both requests use the OpenRouter key and may cost credit.","مکالمه نوبتی است؛ برای صحبت دوباره منتظر حالت شنیدن بمانید. مدل باید صدا و تصویر را دریافت کند. پیشنهاد: google/gemini-3.5-flash-lite. تولید صدا با مدل جداگانهٔ زیر است؛ هر دو درخواست از کلید OpenRouter استفاده می‌کنند و ممکن است اعتبار مصرف کنند.")
                else->tr("Google Gemini Live: live voice and images. Use your Google AI Studio API key.","Google Gemini Live: صدا و تصویر زنده. کلید API از Google AI Studio وارد کنید.")
            }
            presets.removeAllViews()
            val ids=when(p){"openai"->listOf("gpt-realtime","gpt-realtime-2");"openrouter"->listOf("google/gemini-3.5-flash-lite","qwen/qwen3.8-omni-flash");else->listOf(VoiceSettings.DEFAULT_MODEL)}
            ids.forEach{id->presets.addView(activity.button(id){model.setText(id)})}
        }
        provider.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{
            override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long){refresh()}
            override fun onNothingSelected(parent:AdapterView<*>?){}
        };refresh()
        val dialog=show(activity.s("voice_assistant"),content)
        val save=activity.button(tr("Save key","ذخیرهٔ کلید")){}
        val remove=activity.button(tr("Delete key","حذف کلید")){};content.addView(save);content.addView(remove)
        save.setOnClickListener{
            val p=selected();val keyStore=keys(p);val pasted=input.text?.toString().orEmpty();val value=VoiceApiKey.normalize(pasted)
            val id=model.text?.toString().orEmpty().trim().removePrefix("models/")
            val valid=when(p){"openai"->VoiceSettings.validOpenaiModel(id);"openrouter"->VoiceSettings.validRouterModel(id);else->VoiceSettings.validModel(id)}
            if(!valid){model.error=activity.s("voice_model_error");return@setOnClickListener}
            if(value.isEmpty() && !keyStore.hasKey()){input.error=activity.s("voice_key_missing");return@setOnClickListener}
            if(value.isNotEmpty() && !VoiceApiKey.isAcceptable(value)){input.error=activity.s("voice_key_invalid");return@setOnClickListener}
            input.error=null;model.error=null
            val chosenVoice=voice.selectedItem.toString()
            val updated=when(p){
                "openai"->initial.copy(provider=p,openaiModel=id,openaiVoice=chosenVoice)
                "openrouter"->initial.copy(provider=p,routerModel=id,routerVoice=chosenVoice,routerSpeechModel=VoiceSettings.routerSpeechModels[speech.selectedItemPosition])
                else->initial.copy(provider=p,model=id,voice=chosenVoice)
            }
            changed();save.isEnabled=false;remove.isEnabled=false;provider.isEnabled=false
            worker.execute{
                var success=true;try{if(value.isNotEmpty())keyStore.save(pasted)}catch(_:Exception){success=false}
                activity.runOnUiThread{
                    if(activity.isDestroyed || activity.isFinishing)return@runOnUiThread
                    if(success){updated.save(activity);changed();input.setText("");dialog.dismiss();Toast.makeText(activity,activity.s("saved"),Toast.LENGTH_SHORT).show()}
                    else{save.isEnabled=true;remove.isEnabled=true;provider.isEnabled=true;status.text=activity.s("voice_key_storage_error")}
                }
            }
        }
        remove.setOnClickListener{changed();keys(selected()).clear();input.setText("");status.text=activity.s("voice_key_missing")}
    }
    fun advanced(changed:()->Unit){
        val initial=VoiceSettings.load(activity)
        val content=activity.column().apply{setPadding(activity.dp(16),activity.dp(8),activity.dp(16),activity.dp(8))}
        content.addView(Switch(activity).apply{
            text=tr("Enable educational assistant","فعال‌سازی دستیار آموزشی")
            isChecked=VoiceSettings.enabled(activity)
            setOnCheckedChangeListener{_,value->VoiceSettings.setEnabled(activity,value);changed()}
        })
        content.addView(activity.label(tr("Off by default. Enabling reveals the assistant button and connection settings; disabling immediately stops microphone and screen sharing. Saved keys remain. The assistant introduces itself only as an educational assistant, stays patient and asks you to repeat unclear speech without echoing guesses.","پیش‌فرض خاموش است. فعال‌سازی دکمه و تنظیمات اتصال را نمایان می‌کند؛ خاموش کردن، میکروفن و ارسال تصویر را فوراً متوقف می‌کند. کلیدها حفظ می‌شوند. دستیار فقط خود را دستیار آموزشی معرفی می‌کند، صبور می‌ماند و برای صدای نامفهوم بدون تکرار حدس اشتباه درخواست تکرار می‌کند."),14f,MUTED))
        fun spinner(title:String,entries:List<String>,selected:Int):Spinner{
            content.addView(activity.label(title,14f,NAVY,true))
            return Spinner(activity).apply{adapter=OptionAdapter(activity,entries);setSelection(selected.coerceAtLeast(0));content.addView(this)}
        }
        val tone=spinner(tr("Tone","لحن"),listOf(tr("Warm and respectful","گرم و محترمانه"),tr("Formal","رسمی"),tr("Calm and measured","آرام و شمرده")),VoiceSettings.tones.indexOf(initial.tone))
        val length=spinner(tr("Response length","طول پاسخ"),listOf(tr("Short","کوتاه"),tr("Medium","متوسط"),tr("Detailed","مفصل")),VoiceSettings.lengths.indexOf(initial.length))
        content.addView(activity.label(tr("Additional behavior instructions","دستورهای تکمیلی رفتار"),14f,NAVY,true))
        val extra=activity.field(initial.extra).apply{minLines=3;gravity=android.view.Gravity.TOP;inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE};content.addView(extra)
        content.addView(activity.label(tr("Panel features and specifications","امکانات و مشخصات پنل"),14f,NAVY,true))
        val panel=activity.field(initial.panelNotes).apply{minLines=3;gravity=android.view.Gravity.TOP;inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE;hint=tr("Known features only, e.g. panel size, inputs, OPS and microphone","ویژگی‌های معلوم؛ مثل اندازه، ورودی‌ها، OPS و میکروفن")};content.addView(panel)
        val times=listOf(5,15,30,60)
        val timeout=spinner(tr("Maximum conversation duration (minutes)","حداکثر زمان مکالمه (دقیقه)"),times.map{it.toString()},times.indexOf(initial.sessionMinutes))
        val intervals=listOf(2,3,5,10)
        val frames=spinner(tr("Screen refresh interval (seconds)","فاصلهٔ تازه‌سازی تصویر (ثانیه)"),intervals.map{it.toString()},intervals.indexOf(initial.frameSeconds))
        val dialog=show(activity.s("voice_behavior"),content)
        content.addView(activity.button(activity.s("apply"),true){
            // Re-read connection settings so an open behavior dialog never reverts service/key choices.
            VoiceSettings.load(activity).copy(tone=VoiceSettings.tones[tone.selectedItemPosition],length=VoiceSettings.lengths[length.selectedItemPosition],extra=extra.text?.toString().orEmpty(),panelNotes=panel.text?.toString().orEmpty(),frameSeconds=intervals[frames.selectedItemPosition],sessionMinutes=times[timeout.selectedItemPosition]).save(activity)
            changed();dialog.dismiss();Toast.makeText(activity,activity.s("saved"),Toast.LENGTH_SHORT).show()
        })
        content.addView(activity.button(tr("Restore assistant defaults","بازنشانی رفتار پیش‌فرض دستیار")){
            VoiceSettings.load(activity).copy(tone="warm",length="short",extra="",panelNotes="",frameSeconds=2,sessionMinutes=15).save(activity)
            changed();dialog.dismiss();Toast.makeText(activity,activity.s("saved"),Toast.LENGTH_SHORT).show()
        })
    }
}
