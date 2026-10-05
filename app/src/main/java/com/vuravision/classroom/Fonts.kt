package com.vuravision.classroom

import android.content.Context
import android.graphics.Typeface
import android.text.*
import android.text.style.MetricAffectingSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Local families, cached faces and independent fonts for Persian and Latin runs. */
object Fonts {
    data class Family(val id:String,val en:String,val fa:String,val regular:String?=null,val bold:String?=null,val italic:String?=null,val boldItalic:String?=null,val weight:Int=400,val system:String="sans-serif")
    val persian=listOf(
        Family("system","System sans","سنس سیستم"),
        Family("kahroba","Kahroba","کهربا","kahroba_regular.ttf","kahroba_bold.ttf"),
        Family("vazirmatn","Vazirmatn","وزیرمتن","vazirmatn_regular.ttf","vazirmatn_bold.ttf"),
        Family("sahel","Sahel","ساحل","sahel_regular.ttf","sahel_bold.ttf"),
        Family("shabnam","Shabnam","شبنم","shabnam_regular.ttf","shabnam_bold.ttf"),
        Family("nazanin","B Nazanin","بی‌نازنین","nazanin_regular.ttf","nazanin_bold.ttf"),
        Family("zar","B Zar","بی‌زر","zar_regular.ttf","zar_bold.ttf"),
        Family("titr","B Titr","بی‌تیتر","titr_bold.ttf","titr_bold.ttf",weight=700),
    )
    val english=listOf(
        Family("system","System sans","سنس سیستم"),
        Family("montserrat","Montserrat","مونتسرات","montserrat_regular.ttf","montserrat_bold.ttf"),
        Family("rubik","Rubik","روبیک","rubik_regular.ttf","rubik_bold.ttf","rubik_italic.ttf","rubik_bolditalic.ttf"),
        Family("bahnschrift","Bahnschrift","بان‌شریفت","bahnschrift_regular.ttf","bahnschrift_bold.ttf"),
        Family("serif","System serif","سریف سیستم",system="serif"),
        Family("mono","Monospace","تک‌عرض",system="monospace"),
    )
    private val families=(persian+english).associateBy{it.id}
    private var app:Context?=null
    var persianId="kahroba";private set
    var englishId="rubik";private set
    private val faces=java.util.concurrent.ConcurrentHashMap<String,Typeface>()
    private data class UiStyle(val bold:Boolean,val italic:Boolean,val fa:String?,val en:String?)
    private val bound=WeakHashMap<TextView,UiStyle>()
    private val watched=WeakHashMap<TextView,Boolean>()
    fun initialize(context:Context){
        val next=context.applicationContext
        if(app!==next){faces.clear();bound.clear();watched.clear();TextLayout.clearCache()}
        app=next
        val prefs=context.getSharedPreferences("vura",Context.MODE_PRIVATE)
        persianId=prefs.getString("fontFa","kahroba")?.takeIf{id->persian.any{it.id==id}}?:"system"
        englishId=prefs.getString("fontEn","rubik")?.takeIf{id->english.any{it.id==id}}?:"system"
    }
    fun choose(context:Context,fa:String,en:String){
        require(persian.any{it.id==fa} && english.any{it.id==en})
        context.getSharedPreferences("vura",Context.MODE_PRIVATE).edit().putString("fontFa",fa).putString("fontEn",en).apply()
        initialize(context)
    }
    fun stamp(item:Item,force:Boolean=false){
        if(item.kind!="text" && item.kind!="sticky")return
        if(force || item.fontFa.isNullOrBlank())item.fontFa=persianId
        if(force || item.fontEn.isNullOrBlank())item.fontEn=englishId
    }
    // Legacy projects keep their original system face until explicitly reformatted.
    fun fa(item:Item)=item.fontFa?.takeIf{it in families}?:"system"
    fun en(item:Item)=item.fontEn?.takeIf{it in families}?:"system"
    private fun script(c:Char):Boolean?=when(c){
        in '\u0600'..'\u06ff',in '\u0750'..'\u077f',in '\u08a0'..'\u08ff',in '\ufb50'..'\ufdff',in '\ufe70'..'\ufeff'->true
        in 'A'..'Z',in 'a'..'z',in '0'..'9',in '\u00c0'..'\u024f'->false
        else->null
    }
    fun primary(text:CharSequence):Boolean = text.firstNotNullOfOrNull{script(it)}?:false
    fun face(id:String,bold:Boolean=false,italic:Boolean=false):Typeface{
        val family=families[id]?:families.getValue("system")
        val style=when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val context=app
        if(family.regular==null || context==null)return Typeface.create(family.system,style)
        val key=family.id+":"+style
        return faces.getOrPut(key){
            val file=if(italic)(if(bold)family.boldItalic else family.italic)?: (if(bold)family.bold else family.regular) else if(bold)family.bold else family.regular
            val base=Typeface.Builder(context.assets,"fonts/"+(file?:family.regular)).setWeight(if(bold)700 else family.weight).setItalic(italic && (if(bold)family.boldItalic else family.italic)!=null).build()
            Typeface.create(base,style)
        }
    }
    class RunSpan(val familyId:String,val bold:Boolean,val italic:Boolean):MetricAffectingSpan(){
        override fun updateDrawState(tp:TextPaint){tp.typeface=face(familyId,bold,italic)}
        override fun updateMeasureState(tp:TextPaint){updateDrawState(tp)}
    }
    fun style(text:CharSequence,fa:String=persianId,en:String=englishId,bold:Boolean=false,italic:Boolean=false):CharSequence{
        if(text.isEmpty())return text
        if(fa==en){
            if(text is Spannable)text.getSpans(0,text.length,RunSpan::class.java).forEach{text.removeSpan(it)}
            return text
        }
        val result=if(text is Spannable)text else SpannableString(text)
        result.getSpans(0,result.length,RunSpan::class.java).forEach{result.removeSpan(it)}
        var language=primary(text);var start=0
        for(i in text.indices){val next=script(text[i])?:language
            if(next!=language){result.setSpan(RunSpan(if(language)fa else en,bold,italic),start,i,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);start=i;language=next}
        }
        result.setSpan(RunSpan(if(language)fa else en,bold,italic),start,text.length,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return result
    }
    fun bind(view:TextView,bold:Boolean=view.typeface?.isBold==true,italic:Boolean=view.typeface?.isItalic==true,fontFa:String?=null,fontEn:String?=null){
        if(app==null)initialize(view.context)
        bound[view]=UiStyle(bold,italic,fontFa,fontEn)
        fun update(target:TextView){
            val flags=bound[target]?:return
            val fa=flags.fa?:persianId;val en=flags.en?:englishId
            target.typeface=face(if(primary(target.text))fa else en,flags.bold,flags.italic)
            val styled=style(target.text,fa,en,flags.bold,flags.italic)
            if(styled!==target.text)target.text=styled
        }
        if(!watched.containsKey(view) && ((fontFa?:persianId)!="system" || (fontEn?:englishId)!="system")){
            watched[view]=true
            val weak=WeakReference(view);var updating=false
            view.addTextChangedListener(object:TextWatcher{
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){}
                override fun afterTextChanged(s:Editable?){if(!updating){updating=true;try{weak.get()?.let{update(it)}}finally{updating=false}}}
            })
        }
        update(view)
    }
    fun applyTree(view:View){
        if(view is TextView){val flags=bound[view];if(flags==null)bind(view)else bind(view,flags.bold,flags.italic,flags.fa,flags.en)}
        if(view is ViewGroup)(0 until view.childCount).forEach{applyTree(view.getChildAt(it))}
    }
    fun onShown(dialog:android.app.Dialog){
        val root=dialog.window?.decorView?:return
        applyTree(root)
        val listener=object:android.view.ViewTreeObserver.OnGlobalLayoutListener{
            override fun onGlobalLayout(){root.viewTreeObserver.removeOnGlobalLayoutListener(this);applyTree(root)}
        }
        root.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }
}
