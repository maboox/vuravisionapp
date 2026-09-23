package com.vuravision.classroom

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

/** A small native HSV picker with an exact hex entry for colors outside the presets. */
object ColorPickerDialog {
    fun show(context:Context, initial:Int, onPick:(Int)->Unit):AlertDialog {
        val content=context.column().apply{pad(18)}
        val preview=context.label("",18f,NAVY,true).apply{
            minHeight=context.dp(58)
            contentDescription=context.tr("Selected color","رنگ انتخابی")
        }
        content.addView(preview)
        val hex=context.field(hintValue="#RRGGBB").apply{
            inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            imeOptions=EditorInfo.IME_ACTION_DONE
            filters=arrayOf(android.text.InputFilter.LengthFilter(7))
        }
        val hsv=FloatArray(3)
        Color.colorToHSV(initial,hsv)
        var updating=false
        val sliders=mutableListOf<SeekBar>()
        fun selected():Int=Color.HSVToColor(hsv)
        fun showColor(color:Int){
            val code=String.format(Locale.US,"#%06X",color and 0xffffff)
            preview.text=code
            preview.setTextColor(if(Color.luminance(color)>.45)NAVY else Color.WHITE)
            preview.background=rounded(color,context.dp(12).toFloat(),OUTLINE_STRONG)
        }
        fun parse(value:String):Int?=runCatching {
            require(value.matches(Regex("#[0-9a-fA-F]{6}")))
            Color.parseColor(value)
        }.getOrNull()
        listOf(
            Triple(context.tr("Hue","فام"),360,0),
            Triple(context.tr("Saturation","اشباع"),100,1),
            Triple(context.tr("Brightness","روشنایی"),100,2),
        ).forEach{(name,limit,index)->
            content.addView(context.label(name,14f,NAVY,true))
            val slider=SeekBar(context).apply{
                max=limit;progress=(hsv[index]*if(index==0)1f else 100f).toInt().coerceIn(0,limit)
                contentDescription=name
            }
            sliders.add(slider);content.addView(slider)
            slider.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(s:SeekBar?,value:Int,fromUser:Boolean){
                    if(updating||!fromUser)return
                    hsv[index]=if(index==0)value.toFloat() else value/100f
                    if(index==0){
                        if(hsv[1]<.05f){hsv[1]=1f;sliders.getOrNull(1)?.progress=100}
                        if(hsv[2]<.05f){hsv[2]=1f;sliders.getOrNull(2)?.progress=100}
                    }
                    val color=selected();showColor(color)
                    updating=true;hex.setText(String.format(Locale.US,"#%06X",color and 0xffffff));updating=false
                }
                override fun onStartTrackingTouch(s:SeekBar?){}
                override fun onStopTrackingTouch(s:SeekBar?){}
            })
        }
        hex.setText(String.format(Locale.US,"#%06X",initial and 0xffffff))
        showColor(initial)
        hex.addTextChangedListener(object:TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){
                if(updating)return
                parse(s.toString())?.let{color->
                    Color.colorToHSV(color,hsv);showColor(color)
                    updating=true
                    sliders.forEachIndexed{i,bar->bar.progress=(hsv[i]*if(i==0)1f else 100f).toInt()}
                    updating=false
                }
            }
            override fun afterTextChanged(s:Editable?){}
        })
        content.addView(context.label(context.tr("Hex color","کد رنگ"),14f,NAVY,true))
        content.addView(hex)
        val d=MaterialAlertDialogBuilder(context)
            .setTitle(context.tr("Custom color","رنگ دلخواه"))
            .setView(content)
            .setNegativeButton(context.s("cancel"),null)
            .setPositiveButton(context.s("apply"),null)
            .create()
        d.setOnShowListener{
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                val color=parse(hex.text?.toString().orEmpty())
                if(color==null){hex.error=context.tr("Enter a six-digit hex color, such as #27A890","کد شش‌رقمی مثل #27A890 وارد کنید");return@setOnClickListener}
                onPick(color);d.dismiss()
            }
        }
        d.show()
        return d
    }
}
