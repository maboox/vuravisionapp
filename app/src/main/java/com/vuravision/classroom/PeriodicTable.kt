package com.vuravision.classroom

import android.content.Context
import android.graphics.*
import android.view.*
import android.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONArray
import org.json.JSONObject

object PeriodicTable {
    data class Element(val data:JSONObject,val fa:String,val col:Int,val row:Int,val group:Int,val period:Int){
        val number get()=data.getString("AtomicNumber").toInt()
        val symbol get()=data.getString("Symbol")
        val name get()=data.getString("Name")
        fun value(key:String)=data.optString(key).ifBlank{"—"}
    }
    private var cached:List<Element>?=null
    private val names="هیدروژن|هلیم|لیتیم|بریلیم|بور|کربن|نیتروژن|اکسیژن|فلوئور|نئون|سدیم|منیزیم|آلومینیوم|سیلیسیم|فسفر|گوگرد|کلر|آرگون|پتاسیم|کلسیم|اسکاندیم|تیتانیم|وانادیم|کروم|منگنز|آهن|کبالت|نیکل|مس|روی|گالیم|ژرمانیم|آرسنیک|سلنیم|برم|کریپتون|روبیدیم|استرانسیم|ایتریم|زیرکونیم|نیوبیم|مولیبدن|تکنسیم|روتنیم|رودیم|پالادیم|نقره|کادمیم|ایندیم|قلع|آنتیموان|تلوریم|ید|زنون|سزیم|باریم|لانتانیم|سریم|پرازئودیمیم|نئودیمیم|پرومتیم|ساماریم|یوروپیم|گادولینیم|تربیم|دیسپروزیم|هولمیم|اربیم|تولیم|ایتربیم|لوتتیم|هافنیم|تانتالیم|تنگستن|رنیم|اسمیم|ایریدیم|پلاتین|طلا|جیوه|تالیم|سرب|بیسموت|پولونیم|آستاتین|رادون|فرانسیم|رادیم|اکتینیم|توریم|پروتاکتینیم|اورانیم|نپتونیم|پلوتونیم|آمریسیم|کوریم|برکلیم|کالیفرنیم|اینشتینیم|فرمیم|مندلیفیم|نوبلیم|لارنسیم|رادرفوردیم|دوبنیم|سیبورگیم|بوریم|هاسیم|مایتنریم|دارمشتاتیم|رونتگنیم|کوپرنیسیم|نیهونیم|فلروویم|مسکوویم|لیورموریم|تنسین|اوگانسون".split('|')
    @Synchronized fun elements(c:Context):List<Element> {
        cached?.let{return it}
        val a=JSONArray(c.assets.open("science/elements.json").bufferedReader().use{it.readText()})
        return (0 until a.length()).map{i->val n=i+1;val position=position(n);Element(a.getJSONObject(i),names[i],position.first,position.second,if(n in 57..71 || n in 89..103)0 else position.first+1,when(n){in 1..2->1;in 3..10->2;in 11..18->3;in 19..36->4;in 37..54->5;in 55..86->6;else->7})}.also{require(it.size==118);cached=it}
    }
    fun position(n:Int):Pair<Int,Int> = when(n){
        1->0 to 0;2->17 to 0
        in 3..4->n-3 to 1;in 5..10->n+7 to 1
        in 11..12->n-11 to 2;in 13..18->n-1 to 2
        in 19..36->n-19 to 3;in 37..54->n-37 to 4
        in 55..56->n-55 to 5;in 72..86->n-69 to 5
        in 87..88->n-87 to 6;in 104..118->n-101 to 6
        in 57..71->n-55 to 8;else->n-87 to 9
    }
    private val colors=mapOf("Nonmetal" to 0xffcdeeeB.toInt(),"Noble gas" to 0xffdceaff.toInt(),"Alkali metal" to 0xffffd8be.toInt(),"Alkaline earth metal" to 0xffffecc2.toInt(),"Metalloid" to 0xffdaf2c7.toInt(),"Halogen" to 0xffd4e9df.toInt(),"Transition metal" to 0xffe8ddfa.toInt(),"Post-transition metal" to 0xffdce5ed.toInt(),"Lanthanide" to 0xfff8dcee.toInt(),"Actinide" to 0xffffd9db.toInt())
    fun family(c:Context,key:String)=when(key){"Nonmetal"->c.tr(key,"نافلز");"Noble gas"->c.tr(key,"گاز نجیب");"Alkali metal"->c.tr(key,"فلز قلیایی");"Alkaline earth metal"->c.tr(key,"قلیایی خاکی");"Metalloid"->c.tr(key,"شبه‌فلز");"Halogen"->c.tr(key,"هالوژن");"Transition metal"->c.tr(key,"فلز واسطه");"Post-transition metal"->c.tr(key,"فلز پس‌واسطه");"Lanthanide"->c.tr(key,"لانتانید");"Actinide"->c.tr(key,"اکتینید");else->c.tr(key,"نامشخص")}
    fun draw(canvas:Canvas,o:Item,c:Context){
        val p=Paint(Paint.ANTI_ALIAS_FLAG);val cw=o.w/18;val ch=o.h/11
        p.color=Color.WHITE;canvas.drawRoundRect(0f,0f,o.w,o.h,12f,12f,p)
        elements(c).forEach{e->val x=e.col*cw;val y=(e.row+1)*ch
            p.style=Paint.Style.FILL;p.color=colors[e.value("GroupBlock")]?:PAPER
            canvas.drawRoundRect(x+1,y+1,x+cw-1,y+ch-1,3f,3f,p)
            if(e.number==o.selectedElement){p.style=Paint.Style.STROKE;p.color=TEAL;p.strokeWidth=2f;canvas.drawRoundRect(x+1,y+1,x+cw-1,y+ch-1,3f,3f,p)}
            p.style=Paint.Style.FILL;p.color=NAVY;p.textSize=cw*.18f;canvas.drawText(e.number.toString(),x+cw*.08f,y+ch*.22f,p)
            p.textSize=cw*.36f;p.typeface=Typeface.DEFAULT_BOLD;canvas.drawText(e.symbol,x+(cw-p.measureText(e.symbol))/2,y+ch*.65f,p)
            p.typeface=Typeface.DEFAULT;p.textSize=cw*.16f;canvas.drawText(e.value("AtomicMass"),x+cw*.08f,y+ch*.9f,p)
        }
        p.textSize=ch*.26f;p.color=MUTED
        for(g in 1..18)canvas.drawText(g.toString(),(g-.6f)*cw,ch*.75f,p)
        canvas.drawText("57–71",2*cw,6.6f*ch,p);canvas.drawText("89–103",2*cw,7.6f*ch,p)
    }
    fun cardHeader(o:Item)=minOf(o.w-24f,112f).coerceAtLeast(40f)+24f
    fun drawCard(canvas:Canvas,o:Item,c:Context){
        val e=elements(c).firstOrNull{it.number==o.selectedElement}?:return
        val side=cardHeader(o)-24;val x=(o.w-side)/2;val y=12f
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=colors[e.value("GroupBlock")]?:PAPER}
        canvas.drawRoundRect(x,y,x+side,y+side,10f,10f,p)
        p.style=Paint.Style.STROKE;p.color=TEAL;p.strokeWidth=1.5f;canvas.drawRoundRect(x,y,x+side,y+side,10f,10f,p)
        p.style=Paint.Style.FILL;p.color=NAVY;p.typeface=Fonts.face(Fonts.en(o));p.textSize=side*.15f
        canvas.drawText(e.number.toString(),x+side*.1f,y+side*.22f,p)
        p.typeface=Fonts.face(Fonts.en(o),true);p.textSize=side*.42f;p.textAlign=Paint.Align.CENTER
        canvas.drawText(e.symbol,x+side/2,y+side*.66f,p)
        p.typeface=Fonts.face(Fonts.en(o));p.textSize=side*.13f;canvas.drawText(e.value("AtomicMass"),x+side/2,y+side*.9f,p)
    }
    fun hit(c:Context,o:Item,x:Float,y:Float):Element? {
        val (lx,ly)=o.local(x,y);val col=(lx/(o.w/18)).toInt();val row=(ly/(o.h/11)).toInt()-1
        return elements(c).firstOrNull{it.col==col&&it.row==row && lx>=0&&ly>=0&&lx<o.w&&ly<o.h}
    }
    private val examples=mapOf(
        1 to ("Water: H₂O" to "آب: H₂O"),6 to ("Diamond and graphite are forms of carbon." to "الماس و گرافیت شکل‌هایی از کربن‌اند."),7 to ("Nitrogen is a major component of air." to "نیتروژن بخش بزرگی از هوا را تشکیل می‌دهد."),8 to ("Oxygen is involved in respiration; water contains oxygen." to "اکسیژن در تنفس نقش دارد و در آب هم وجود دارد."),11 to ("Table salt: NaCl" to "نمک خوراکی: NaCl"),13 to ("Aluminium is used in cans and light alloys." to "آلومینیوم در قوطی‌ها و آلیاژهای سبک کاربرد دارد."),14 to ("Silicon is used in semiconductor chips." to "سیلیسیم در تراشه‌های نیمه‌رسانا کاربرد دارد."),17 to ("Chloride is present in table salt: NaCl." to "یون کلرید در نمک خوراکی NaCl وجود دارد."),20 to ("Calcium carbonate: CaCO₃" to "کربنات کلسیم: CaCO₃"),26 to ("Iron is a principal component of steel." to "آهن جزء اصلی فولاد است."),29 to ("Copper is used in electrical wiring." to "مس در سیم‌کشی برق کاربرد دارد."),47 to ("Silver is used in jewellery and electrical contacts." to "نقره در زیورآلات و اتصال‌های الکتریکی کاربرد دارد."),79 to ("Gold is used in jewellery and electronic contacts." to "طلا در زیورآلات و اتصال‌های الکترونیکی کاربرد دارد.")
    )
    fun details(c:Context,e:Element):String {
        val phase=when(e.value("StandardState")){"Gas"->c.tr("Gas","گاز");"Liquid"->c.tr("Liquid","مایع");"Solid"->c.tr("Solid","جامد");else->c.tr("Not established","تعیین نشده")}
        val example=examples[e.number]?.let{c.tr(it.first,it.second)}?:c.tr("Compare its family, period and electron configuration with neighbouring elements.","خانواده، دوره و آرایش الکترونی آن را با عناصر همسایه مقایسه کنید.")
        return "${e.symbol} — ${c.tr(e.name,e.fa)}\n"+
            c.tr("Atomic number","عدد اتمی")+": ${e.number}\n"+c.tr("Atomic mass (u)","جرم اتمی (u)")+": ${e.value("AtomicMass")}\n"+
            c.tr("Group / period","گروه / دوره")+": ${if(e.group==0)"f-block" else e.group} / ${e.period}\n"+
            family(c,e.value("GroupBlock"))+" · "+phase+"\n"+c.tr("Electron configuration","آرایش الکترونی")+": ${e.value("ElectronConfiguration")}\n"+
            c.tr("Oxidation states","عددهای اکسایش")+": ${e.value("OxidationStates")}\n"+
            c.tr("Electronegativity (Pauling)","الکترونگاتیوی (پائولینگ)")+": ${e.value("Electronegativity")}\n\n$example"
    }
    fun elementDialog(c:Context,e:Element,insert:(Item)->Unit){
        var close:()->Unit={}
        val body=c.column().apply{pad(16)};body.addView(c.label(details(c,e),16f).apply{setTextIsSelectable(true)})
        val compare=Spinner(c);compare.adapter=OptionAdapter(c,elements(c).map{"${it.symbol} · ${c.tr(it.name,it.fa)}"});compare.setSelection((e.number%118));body.addView(compare)
        body.addView(c.button(c.tr("Compare","مقایسه")){val other=elements(c)[compare.selectedItemPosition];val r=c.row();listOf(e,other).forEach{r.addView(c.label(details(c,it),14f),LinearLayout.LayoutParams(0,-2,1f))};MaterialAlertDialogBuilder(c).setTitle(c.tr("Element comparison","مقایسهٔ عناصر")).setView(ScrollView(c).apply{addView(r)}).setPositiveButton(c.s("close"),null).show().also{Fonts.onShown(it)}})
        body.addView(c.button(c.tr("Add element card to board","افزودن کارت عنصر به تخته")){insert(Item(kind="sticky",shape="element_card",selectedElement=e.number,text=details(c,e),width=15f,w=300f,h=380f,noteColor=colors[e.value("GroupBlock")]?:PAPER));close()})
        MaterialAlertDialogBuilder(c).setTitle("${e.symbol} · ${c.tr(e.name,e.fa)}").setView(ScrollView(c).apply{addView(body)}).setNegativeButton(c.s("close"),null).show().also{close=it::dismiss;Fonts.onShown(it)}
    }
    fun show(c:Context,insert:(Item)->Unit){
        var close:()->Unit={};val add:(Item)->Unit={insert(it);close()}
        val body=c.column().apply{pad(12)};val o=Item(kind="periodic",w=900f,h=550f)
        val table=object:View(c){
            override fun onMeasure(w:Int,h:Int){setMeasuredDimension(resolveSize(c.dp(900),w),resolveSize(c.dp(550),h))}
            override fun onDraw(canvas:Canvas){canvas.save();canvas.scale(width/o.w,height/o.h);draw(canvas,o,c);canvas.restore()}
            override fun onTouchEvent(e:MotionEvent):Boolean{if(e.actionMasked==MotionEvent.ACTION_UP){hit(c,o,e.x*o.w/width,e.y*o.h/height)?.let{o.selectedElement=it.number;invalidate();elementDialog(c,it,add)};performClick()};return true}
            override fun performClick():Boolean{super.performClick();return true}
        }
        val search=c.field(hintValue=c.tr("Name, symbol or atomic number","نام، نماد یا عدد اتمی"));body.addView(search)
        body.addView(c.button(c.tr("Find element","یافتن عنصر")){val q=search.text.toString().trim();val e=elements(c).firstOrNull{it.symbol.equals(q,true)||it.name.equals(q,true)||it.fa==q||it.number.toString()==q};if(e==null)search.error=c.tr("Element not found","عنصر پیدا نشد")else{o.selectedElement=e.number;table.invalidate();elementDialog(c,e,add)}})
        body.addView(HorizontalScrollView(c).apply{addView(table,ViewGroup.LayoutParams(c.dp(900),c.dp(550)))})
        val legend=c.row();colors.forEach{(family,color)->legend.addView(c.label(c.tr(family,PeriodicTable.family(c,family)),12f).apply{setBackgroundColor(color)})};body.addView(c.scrollRow(legend))
        body.addView(c.button(c.tr("Add interactive table to board","افزودن جدول تعاملی به تخته")){add(o.deepCopy())})
        body.addView(c.infoTitle(c.tr("Data source","منبع داده"),"PubChem / NCBI · 2026-10-05\n"+c.tr("Missing values are shown as —; f-block elements are shown separately. Atomic masses follow the source, including isotope masses where applicable.","دادهٔ ناموجود با — نشان داده می‌شود؛ عناصر بلوک f جدا آمده‌اند. جرم‌ها مطابق منبع‌اند و در موارد مربوط جرم ایزوتوپی نمایش داده می‌شود.")))
        MaterialAlertDialogBuilder(c).setTitle(c.s("periodic")).setView(ScrollView(c).apply{addView(body)}).setNegativeButton(c.s("close"),null).show().also{close=it::dismiss;Fonts.onShown(it)}
    }
}
