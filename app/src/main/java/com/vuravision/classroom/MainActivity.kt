package com.vuravision.classroom

import android.app.*
import android.content.*
import android.content.res.Configuration
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.*
import android.view.*
import android.widget.*
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.*

class MainActivity : Activity() {
    val store = Store()
    lateinit var board: Board
    lateinit var media: Media
    private lateinit var files: LessonFiles
    private lateinit var root: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var status: TextView
    private lateinit var dock: LinearLayout
    private lateinit var pageLabel: TextView
    private lateinit var pageStrip:LinearLayout
    private lateinit var canvasHost:FrameLayout
    private lateinit var floatingTools:ClassroomWidgets
    private var pageSignature=""
    private var dockSignature=""
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val recognition = Recognition()
    private val prefs by lazy { getSharedPreferences("vura", MODE_PRIVATE) }
    private var documentId = ""
    private var clipboard = listOf<Item>()
    private var pendingExport: File? = null
    private var sharing: Sharing? = null
    private var shareDialog: Dialog? = null
    private var taps = 0
    private val autosave = Runnable { persist() }
    private var dirty = false
    private var loading = false
    private var lastError = ""
    private var destroyed = false

    override fun attachBaseContext(base: Context) {
        val lang =
            base.getSharedPreferences("vura", MODE_PRIVATE).getString("language", "en") ?: "en"
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale(lang))
        super.attachBaseContext(base.createConfigurationContext(config))
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        media = Media(this)
        files = LessonFiles(media)
        board = Board(this, store, Renderer(media))
        board.profile =
            TouchProfile(
                prefs.getBoolean("calibrated", false),
                prefs.getFloat("thin", 10f),
                prefs.getFloat("palm", 50f),
                prefs.getBoolean("palmErase", false),
                prefs.getBoolean("multiTouch",true),
                prefs.getFloat("thickWidth",10f),
            )
        board.profile.thickColor=prefs.getInt("thickColor",0xffe45756.toInt())
        board.eraserMode=prefs.getString("eraserMode","stroke")?:"stroke"
        board.eraserRadius=prefs.getFloat("eraserRadius",18f)
        board.eraseObjects=prefs.getBoolean("eraseObjects",true)
        board.penColor=prefs.getInt("penColor",NAVY)
        board.highlightColor=prefs.getInt("highlightColor",0xffffcf40.toInt())
        board.penWidth=prefs.getFloat("penWidth",4f)
        board.highlightWidth=prefs.getFloat("highlightWidth",6f)
        board.penStyle=prefs.getString("penStyle","round")?:"round"
        smartSource=prefs.getString("smartSource","offline")?:"offline"
        board.smartMode=prefs.getString("smartMode","text")?:"text"
        buildUI()
        media.ready = { handler.post { if(!destroyed)board.sceneChanged() } }
        media.error = { message -> handler.post { status.text = s("error") + ": " + message } }
        board.onSelection = { refreshDock() }
        board.onObjectActions={editSelection()}
        board.onSmart={processSmart(it)}
        store.changed = {
            dirty = true
            if(!board.isCommitting)board.sceneChanged() else board.invalidate()
            refreshDock()
            refreshTitle()
            status.text = s("saving")
            handler.removeCallbacks(autosave)
            handler.postDelayed(autosave, 900)
        }
        documentId = prefs.getString("current", null) ?: newId()
        prefs.edit().putString("current", documentId).apply()
        val f = lessonFile()
        if (f.exists() || File(f.path + ".bak").exists()) loadFile(f, false)
        else {
            store.lesson.title = s("lesson")
            refreshTitle()
        }
    }

    private fun buildUI() {
        root =
            column().apply {
                setBackgroundColor(PAPER)
                fitsSystemWindows = true
            }
        setContentView(root)
        val header =
            row().apply {
                setBackgroundColor(NAVY)
                setPadding(dp(12), dp(5), dp(12), dp(5))
            }
        header.addView(
            ImageView(this).apply { setImageResource(R.drawable.vura_brand);scaleType=ImageView.ScaleType.CENTER_CROP;contentDescription="VuraVision" },
            LinearLayout.LayoutParams(dp(if(resources.configuration.screenWidthDp<600)48 else 72),dp(48)),
        )
        titleView =
            label("VuraVision", 17f, Color.WHITE, true).apply {
                setOnClickListener { rename() }
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
        header.addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(
            button("↶") {
                store.undo()
                board.clearSelection()
            }
        )
        header.addView(
            button("↷") {
                store.redo()
                board.clearSelection()
            }
        )
        if(resources.configuration.screenWidthDp>=720) {
            header.addView(button(s("lab_short")){openExplorer(true)})
            header.addView(button(s("games_short")){openExplorer(false)})
        }
        header.addView(button(if(resources.configuration.screenWidthDp<600)"⇧" else s("share_short")){share()}.apply{contentDescription=s("share")})
        header.addView(button("☰") { menu() }.apply { contentDescription = s("tools") })
        root.addView(header)
        val strip = row().apply { setPadding(dp(12), dp(2), dp(12), dp(2)) }
        if(resources.configuration.screenWidthDp>=720)strip.addView(label(s("workspace").uppercase(Locale.getDefault()),10f,MUTED,true))
        status = label(s("ready"), 11f, MUTED)
        strip.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        pageLabel = label("", 12f, MUTED).apply { setOnClickListener { pages() } }
        strip.addView(pageLabel)
        val touch=button(s(if(board.profile.multiTouch)"multi_touch_short" else "single_touch_short")){}
        touch.setOnClickListener {board.profile.multiTouch=!board.profile.multiTouch;prefs.edit().putBoolean("multiTouch",board.profile.multiTouch).apply();touch.text=s(if(board.profile.multiTouch)"multi_touch_short" else "single_touch_short")}
        strip.addView(touch)
        strip.addView(button(s("fit")){board.fit()})
        root.addView(strip)
        canvasHost=FrameLayout(this);canvasHost.addView(board,FrameLayout.LayoutParams(-1,-1))
        floatingTools=ClassroomWidgets(this,canvasHost)
        root.addView(canvasHost,LinearLayout.LayoutParams(-1,0,1f))
        pageStrip=row().apply{pad(3)};root.addView(scrollRow(pageStrip))
        dock = row().apply { pad(5) }
        root.addView(scrollRow(dock))
        refreshDock();refreshPages()
    }

    private fun refreshTitle() {
        titleView.text = store.lesson.title.ifBlank { s("lesson") }
        pageLabel.text =
            "${store.lesson.current+1} / ${store.lesson.pages.size}   ·   ${s("pages")}"
        refreshPages()
    }

    private fun refreshDock() {
        if(!::dock.isInitialized)return
        val signature="${board.tool}:${board.selected.joinToString()}:${board.chosen().joinToString{it.pdfPage.toString()}}"
        if(signature==dockSignature)return
        dockSignature=signature;dock.removeAllViews()
        val icons=mapOf("pen" to "✎","highlight" to "▰","erase" to "▱","select" to "↖","pan" to "✥")
        listOf("pen","highlight","erase","select","pan").forEach { k ->
            dock.addView(button("${icons[k]}  ${s(k)}",board.tool==k) {
                if(board.tool==k && k=="erase")eraserSettings()
                else if(board.tool==k && k in listOf("pen","highlight"))penSettings()
                else {board.tool=k;board.clearSelection();refreshDock()}
            })
            if(k=="highlight")dock.addView(button("△  ${s("shape")}",board.tool=="shape"){shapes()})
        }
        dock.addView(button("T  ${s("text")}"){addText(false)})
        dock.addView(button("＋  ${s("insert")}"){insert()})
        dock.addView(button("✦  ${s("smart")}",board.tool=="smart"){smart()})
        dock.addView(button("◷  ${s("tools")}"){classroomTools()})
        board.chosen().singleOrNull()?.takeIf{it.kind=="pdf"}?.let{pdf->
            dock.addView(button("‹ PDF"){board.edit{it.pdfPage=(it.pdfPage-1).coerceAtLeast(0)}})
            dock.addView(label("${pdf.pdfPage+1} / ${pdf.pageCount}",14f))
            dock.addView(button("PDF ›"){board.edit{it.pdfPage=(it.pdfPage+1).coerceAtMost(it.pageCount-1)}})
        }
        if(board.chosen().isNotEmpty())dock.addView(button("${s("edit")} · ${board.chosen().size}",true){editSelection()})
    }
    private fun refreshPages() {
        if(!::pageStrip.isInitialized)return
        val signature="${store.lesson.current}:${store.lesson.pages.joinToString{it.id}}"
        if(signature==pageSignature)return
        pageSignature=signature;pageStrip.removeAllViews()
        pageStrip.addView(button("＋ ${s("add_page")}",true){addPage()})
        pageStrip.addView(button("‹"){switchPage(store.lesson.current-1)}.apply{contentDescription=s("previous")})
        store.lesson.pages.forEachIndexed { i,_ -> pageStrip.addView(button("${s("page_short")} ${i+1}",i==store.lesson.current){switchPage(i)}.apply{setOnLongClickListener{pages();true}}) }
        pageStrip.addView(button("›"){switchPage(store.lesson.current+1)}.apply{contentDescription=s("next")})
        pageStrip.addView(button("⋯ ${s("pages")}"){pages()})
    }
    private fun switchPage(index:Int) {
        if(index !in store.lesson.pages.indices || board.isDrawing)return
        store.lesson.current=index;board.clearSelection();board.reset();store.changed()
    }
    private fun addPage() {
        if(store.lesson.pages.size>=200 || board.isDrawing)return
        store.edit{store.lesson.pages.add(store.lesson.current+1,Page());store.lesson.current++}
        board.clearSelection();board.reset()
    }
    private fun savePens() {prefs.edit().putFloat("thickWidth",board.profile.thickWidth).putInt("thickColor",board.profile.thickColor).putInt("penColor",board.penColor).putInt("highlightColor",board.highlightColor).putFloat("penWidth",board.penWidth).putFloat("highlightWidth",board.highlightWidth).putString("penStyle",board.penStyle).apply()}
    private fun palette(c:LinearLayout,initial:Int,changed:(Int)->Unit){
        val colors=listOf(NAVY,Color.BLACK,Color.WHITE,0xffe45756.toInt(),ORANGE,0xffffcf40.toInt(),TEAL,0xff268bd2.toInt(),0xff865ac7.toInt(),0xffec76ab.toInt())
        val buttons=mutableListOf<Button>()
        colors.chunked(5).forEach{chunk->c.addView(row().apply{chunk.forEach{color->
            val b=button(if(color==initial)"✓" else "●"){}.apply{setTextColor(if(color==Color.WHITE)NAVY else Color.WHITE);background=rounded(color,dp(12).toFloat(),0xffb5b0c7.toInt());textSize=22f;contentDescription=String.format(Locale.US,"#%06X",color and 0xffffff)}
            b.setOnClickListener{buttons.forEach{it.text="●"};b.text="✓";changed(color)};buttons.add(b)
            addView(b,LinearLayout.LayoutParams(0,dp(48),1f).apply{setMargins(dp(4),dp(4),dp(4),dp(4))})
        }})}
    }
    private fun slider(c:LinearLayout,name:String,value:Float,maxValue:Int=40,changed:(Float)->Unit){
        val title=label("$name · ${value.toInt()}",15f,NAVY,true);title.setPadding(dp(4),dp(18),dp(4),dp(8));c.addView(title)
        c.addView(SeekBar(this).apply{max=maxValue-1;progress=value.toInt()-1;contentDescription=name;setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(v:SeekBar?,n:Int,user:Boolean){title.text="$name · ${n+1}";if(user)changed(n+1f)}
            override fun onStartTrackingTouch(v:SeekBar?){};override fun onStopTrackingTouch(v:SeekBar?){}
        })})
    }
    private fun penSettings(){
        val c=column().apply{pad(18)};val body=column();var broad=false
        val preview=object:View(this){override fun onDraw(canvas:Canvas){val color=if(broad)board.profile.thickColor else board.inkColor;val width=if(broad)board.profile.thickWidth else board.inkWidth
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{this.color=color;strokeWidth=dp(width.toInt()).toFloat()*(if(board.tool=="highlight")4f else if(board.penStyle=="marker")1.8f else 1f);alpha=if(board.tool=="highlight")75 else 255;style=Paint.Style.STROKE;strokeCap=if(board.penStyle=="marker"||board.tool=="highlight")Paint.Cap.SQUARE else Paint.Cap.ROUND;if(board.penStyle=="dashed")pathEffect=DashPathEffect(floatArrayOf(25f,16f),0f)}
            val path=Path();path.moveTo(30f,height*.7f);path.cubicTo(this.width*.3f,0f,this.width*.55f,height.toFloat(),this.width-30f,height*.3f);canvas.drawPath(path,paint)
        }}.apply{background=rounded(PAPER,dp(14).toFloat())}
        c.addView(label(tr("Live preview","پیش‌نمایش زنده"),14f,MUTED));c.addView(preview,LinearLayout.LayoutParams(-1,dp(72)))
        fun refresh(){body.removeAllViews();if(board.tool!="highlight")body.addView(label(if(broad)tr("Broad-tip appearance","ظاهر سر پهن")else tr("Fine-tip appearance","ظاهر سر باریک"),16f,TEAL,true));body.addView(label(tr("Color","رنگ"),16f,NAVY,true));palette(body,if(broad)board.profile.thickColor else board.inkColor){if(broad)board.profile.thickColor=it else board.inkColor=it;savePens();preview.invalidate()}
            slider(body,tr("Thickness","ضخامت"),if(broad)board.profile.thickWidth else board.inkWidth){if(broad)board.profile.thickWidth=it else board.inkWidth=it;savePens();preview.invalidate()}
            if(board.tool!="highlight"){
                body.addView(label(tr("Tip style","نوع نوک قلم"),16f,NAVY,true));val styles=row();body.addView(styles)
                val labels=listOf("round" to tr("Ink pen","قلم جوهری"),"marker" to tr("Broad marker","ماژیک پهن"),"dashed" to tr("Dashed","خط‌چین"))
                labels.forEach{(key,title)->styles.addView(button(title,board.penStyle==key){board.penStyle=key;savePens();refresh();preview.invalidate()},LinearLayout.LayoutParams(0,dp(52),1f))}
                body.addView(label(tr("Ink: round and precise. Marker: square tip, 1.8× wider and opaque. Highlighter is a separate translucent tool.","جوهری: دقیق با نوک گرد. ماژیک: نوک تخت، پهنای ۱٫۸ برابر و پررنگ. هایلایتر ابزار جداگانه و نیمه‌شفاف است."),13f,MUTED))
                body.addView(button(tr("Dual-tip calibration","تنظیم تشخیص دو سر قلم")){calibrateTips()})
            }
        }
        if(board.tool!="highlight")c.addView(row().apply{
            addView(button(tr("Fine tip","سر باریک")){broad=false;refresh();preview.invalidate()},LinearLayout.LayoutParams(0,dp(50),1f))
            addView(button(tr("Broad tip","سر پهن")){broad=true;refresh();preview.invalidate()},LinearLayout.LayoutParams(0,dp(50),1f))
        })
        c.addView(body);refresh();dialog(s(board.tool),ScrollView(this).apply{addView(c)})
    }
    private fun calibrateTips(){
        val c=column().apply{pad(20)}
        c.addView(label(tr("Contact width comes from the touch controller. Calibrate in raw pixels, not screen centimetres. Unknown/zero contact sizes use the fine tip.","اندازهٔ تماس را کنترلر لمس گزارش می‌کند. آستانه‌ها با پیکسل خام تنظیم می‌شوند، نه سانتی‌متر صفحه. اندازهٔ صفر یا نامشخص، سر باریک محسوب می‌شود."),14f,MUTED))
        val enabled=CheckBox(this).apply{text=tr("Enable dual-tip detection","تشخیص دو سر قلم فعال باشد");isChecked=board.profile.calibrated};c.addView(enabled)
        c.addView(label(tr("Fine tip maximum contact width (px)","بیشترین عرض تماس سر باریک (پیکسل)"),14f));val thin=field(board.profile.thin.toString());c.addView(thin)
        c.addView(label(tr("Palm rejection starts at (px)","شروع تشخیص کف دست (پیکسل)"),14f));val palm=field(board.profile.palm.toString());c.addView(palm)
        val palmErase=CheckBox(this).apply{text=s("palm_erase");isChecked=board.profile.palmErase};c.addView(palmErase)
        c.addView(label(tr("Between these thresholds: broad-tip color and thickness. Set each tip's appearance in Pen settings.","بین این دو آستانه، رنگ و ضخامت سر پهن اعمال می‌شود. ظاهر هر دو سر را از تنظیمات قلم انتخاب کنید."),14f))
        c.addView(button(s("touch_test")){dialog(s("touch_test"),TouchDiagnostics(this,board.profile).apply{minimumHeight=dp(320)})})
        val d=dialog(tr("Dual-tip calibration","کالیبراسیون دو سر قلم"),ScrollView(this).apply{addView(c)})
        c.addView(button(s("apply"),true){val a=thin.text.toString().toFloatOrNull();val b=palm.text.toString().toFloatOrNull();if(a==null||b==null||!a.isFinite()||!b.isFinite()||a<=0||b<=a){thin.error=tr("Use 0 < fine < palm","باید ۰ < سر باریک < کف دست باشد");return@button};board.profile.calibrated=enabled.isChecked;board.profile.thin=a;board.profile.palm=b;board.profile.palmErase=palmErase.isChecked;prefs.edit().putBoolean("calibrated",enabled.isChecked).putFloat("thin",a).putFloat("palm",b).putBoolean("palmErase",palmErase.isChecked).apply();savePens();d.dismiss()})
    }
    private fun eraserSettings(){
        val c=column().apply{pad(20)};c.addView(label(tr("Stroke/object: remove an entire touched item. Area: remove only the region under the eraser, including text, shapes, images and PDFs. Undo restores it.","خط/شیء: تمام مورد لمس‌شده پاک می‌شود. ناحیه‌ای: فقط مسیر پاک‌کن روی خط، متن، شکل، تصویر یا PDF پاک می‌شود. با Undo قابل برگشت است."),14f,MUTED))
        val modes=RadioGroup(this);listOf("stroke" to tr("Whole stroke / object","کل خط / شیء"),"area" to tr("Area eraser","پاک‌کن ناحیه‌ای")).forEach{(key,title)->modes.addView(RadioButton(this).apply{text=title;isChecked=board.eraserMode==key;setOnClickListener{board.eraserMode=key;prefs.edit().putString("eraserMode",key).apply()}})};c.addView(modes)
        slider(c,tr("Radius","شعاع"),board.eraserRadius,80){board.eraserRadius=it;prefs.edit().putFloat("eraserRadius",it).apply()}
        c.addView(CheckBox(this).apply{text=tr("Include objects (otherwise ink only)","روی اشیاء هم اعمال شود (وگرنه فقط دست‌نویس)");isChecked=board.eraseObjects;setOnCheckedChangeListener{_,v->board.eraseObjects=v;prefs.edit().putBoolean("eraseObjects",v).apply()}})
        dialog(s("erase"),ScrollView(this).apply{addView(c)})
    }
    private fun addText(sticky:Boolean){textEditor(null,sticky)}
    private fun textEditor(existing:Item?,sticky:Boolean=existing?.kind=="sticky"){
        if(existing?.locked==true)return
        val item=existing?.deepCopy()?:Item(kind=if(sticky)"sticky"else"text",width=if(sticky)24f else 32f,color=board.penColor)
        val c=column().apply{pad(20)};val value=field(item.text,tr("Write your text…","متن را بنویسید…"));value.minLines=2;c.addView(value)
        c.addView(CheckBox(this).apply{text=tr("Bold","پررنگ");isChecked=item.bold;setOnCheckedChangeListener{_,v->item.bold=v;value.setTypeface(null,if(v)Typeface.BOLD else Typeface.NORMAL)}})
        c.addView(label(tr("Alignment","تراز متن"),16f,NAVY,true));val align=Spinner(this);align.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf(tr("Start","ابتدای سطر"),tr("Center","وسط"),tr("End","انتهای سطر")));align.setSelection(listOf("start","center","end").indexOf(item.textAlign).coerceAtLeast(0));c.addView(align)
        c.addView(label(tr("Text color","رنگ متن"),16f,NAVY,true));palette(c,item.color){item.color=it;value.setTextColor(it)}
        slider(c,tr("Text size","اندازهٔ متن"),item.width,120){item.width=it}
        if(sticky){c.addView(label(tr("Note background","رنگ یادداشت"),16f,NAVY,true));palette(c,item.noteColor){item.noteColor=it;value.background=rounded(it,dp(8).toFloat())}}
        val d=dialog(s(if(sticky)"sticky"else"text"),ScrollView(this).apply{addView(c)})
        c.addView(button(s("apply"),true){if(value.text.isBlank()){value.error=s("empty");return@button};item.text=value.text.toString();item.textAlign=listOf("start","center","end")[align.selectedItemPosition];TextLayout.fit(item)
            if(existing==null)board.insert(item)else store.edit{val index=store.page.items.indexOfFirst{it.id==existing.id};if(index>=0)store.page.items[index]=item};d.dismiss()})
    }
    private fun graph(initial:String="y=2x-5",existing:Item?=null){
        val c=column().apply{pad(20)};c.addView(label(tr("Enter y=f(x). Use x^2, sin(x), sqrt(x), abs(x); angles are radians. Separate up to 3 curves with ;. Both axes use the same scale.","تابع را به صورت y=f(x) وارد کنید. x^2، sin(x)، sqrt(x) و abs(x) مجازند؛ زاویه‌ها رادیانی‌اند. حداکثر ۳ تابع را با ; جدا کنید. مقیاس دو محور برابر است."),14f,MUTED))
        val field=field(existing?.text?:initial).apply{layoutDirection=View.LAYOUT_DIRECTION_LTR;textDirection=View.TEXT_DIRECTION_LTR};c.addView(field)
        val examples=row();listOf("y=2x-5","y=x^2","sin(x);cos(x)","sqrt(x)").forEach{v->examples.addView(button(v){field.setText(v)})};c.addView(scrollRow(examples))
        c.addView(label(tr("Half-range on the shorter axis","نیم‌بازهٔ محور کوتاه‌تر"),14f));val domain=field((existing?.domain?:10f).toString());c.addView(domain)
        val d=dialog(s("graph"),ScrollView(this).apply{addView(c)})
        c.addView(button(tr("Plot","رسم نمودار"),true){try{val text=field.text.toString();require(text.split(';').size in 1..3);text.split(';').forEach{MathTools().compile(it)};val span=domain.text.toString().toFloat();require(span in .1f..1000f);if(existing==null)board.insert(Item(kind="graph",text=text,w=560f,h=400f,domain=span))else store.edit{existing.text=text;existing.domain=span};d.dismiss()}catch(e:Exception){field.error=e.message?:s("error")}})
    }

    private fun shapes() {
        val c=column().apply{pad(12)};val d=dialog(s("shape"),ScrollView(this).apply{addView(c)})
        Shapes.keys.chunked(4).forEach{chunk->val r=row();chunk.forEach{key->
            val cell=column().apply{gravity=Gravity.CENTER;pad(5);background=rounded(PAPER,dp(10).toFloat())}
            cell.addView(object:View(this){private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=TEAL;style=Paint.Style.STROKE;strokeWidth=dp(2).toFloat()}
                override fun onDraw(canvas:Canvas){
                    val ratio=if(key in listOf("rectangle","rounded_rectangle","ellipse","trapezoid","parallelogram","line","arrow","double_arrow","speech"))1.8f else 1f
                    val previewWidth=minOf((width-dp(24)).toFloat(),(height-dp(16))*ratio).coerceAtLeast(1f)
                    val previewHeight=previewWidth/ratio
                    canvas.save();canvas.translate((width-previewWidth)/2f,(height-previewHeight)/2f)
                    Shapes.draw(canvas,key,previewWidth,previewHeight,paint);canvas.restore()
                }
            },LinearLayout.LayoutParams(-1,dp(56)))
            cell.addView(label(s(key),11f).apply{gravity=Gravity.CENTER})
            cell.setOnClickListener{board.shape=key;board.tool="shape";board.clearSelection();refreshDock();d.dismiss()}
            r.addView(cell,LinearLayout.LayoutParams(0,dp(96),1f).apply{setMargins(dp(3),dp(3),dp(3),dp(3))})
        };c.addView(r)}
    }

    private fun dialog(title: String, content: View): AlertDialog =
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(content)
            .setNegativeButton(s("close"), null)
            .create()
            .also {
                it.show()
                it.window?.setBackgroundDrawable(rounded(Color.WHITE, dp(20).toFloat()))
            }

    private fun choices(title: String, keys: List<String>, action: (Int) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(keys.map { s(it) }.toTypedArray()) { _, i -> action(i) }
            .setNegativeButton(s("cancel"), null)
            .show()
    }

    private fun confirm(title: String, action: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(title)
            .setNegativeButton(s("cancel"), null)
            .setPositiveButton(s("apply")) { _, _ -> action() }
            .show()
    }

    private fun input(
        title: String,
        value: String = "",
        multi: Boolean = false,
        action: (String) -> Unit,
    ) {
        val box = column().apply { pad(20) }
        val edit =
            field(value).apply {
                setSingleLine(!multi)
                maxLines = 8
            }
        box.addView(edit)
        val d =
            AlertDialog.Builder(this)
                .setTitle(title)
                .setView(box)
                .setNegativeButton(s("cancel"), null)
                .setPositiveButton(s("apply"), null)
                .create()
        d.show()
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                action(edit.text.toString())
                d.dismiss()
            } catch (e: Exception) {
                edit.error = e.message ?: s("error")
            }
        }
    }

    private fun error(e: Exception) {
        lastError = "${e.javaClass.simpleName}: ${e.message}"
        if (!destroyed) {
            status.text = s("error")
            AlertDialog.Builder(this)
                .setTitle(s("error"))
                .setMessage(lastError)
                .setPositiveButton(s("close"), null)
                .show()
        }
    }

    private fun <T> work(task: () -> T, done: (T) -> Unit) {
        if (loading) return
        loading = true
        val progress =
            AlertDialog.Builder(this)
                .setView(
                    row().apply {
                        pad(24)
                        addView(
                            ProgressBar(this@MainActivity),
                            LinearLayout.LayoutParams(dp(36), dp(36)),
                        )
                        addView(label(s("busy")).apply { pad(16) })
                    }
                )
                .setCancelable(false)
                .show()
        worker.execute {
            try {
                val result = task()
                handler.post {
                    loading = false
                    if (!destroyed) {
                        progress.dismiss()
                        done(result)
                    }
                }
            } catch (e: Exception) {
                handler.post {
                    loading = false
                    if (!destroyed) {
                        progress.dismiss()
                        error(e)
                    }
                }
            }
        }
    }

    private fun lessonFile(id: String = documentId) = File(filesDir, "lessons/$id.vura")

    private fun persist() {
        if(!dirty || destroyed)return
        if(board.isDrawing){handler.postDelayed(autosave,1000);return}
        dirty = false
        val doc = store.lesson.copyDeep()
        val target = lessonFile()
        worker.execute {
            try {
                files.atomic(doc, target)
                handler.post { if (!dirty && !destroyed) status.text = s("saved") }
            } catch (e: Exception) {
                handler.post {
                    dirty = true
                    if (!destroyed) {
                        lastError = e.message ?: ""
                        status.text = s("save_error")
                    }
                }
            }
        }
    }

    private fun loadFile(f: File, changeId: Boolean = true) {
        handler.removeCallbacks(autosave)
        persist()
        work({
            try {
                files.read(f.inputStream()) to false
            } catch (e: Exception) {
                val backup = File(f.path + ".bak")
                if (!backup.exists()) throw e
                files.read(backup.inputStream()) to true
            }
        }) { (doc, backup) ->
            if (changeId || backup) {
                documentId = if (backup) newId() else f.nameWithoutExtension
                prefs.edit().putString("current", documentId).apply()
            }
            store.replace(doc)
            board.clearSelection()
            board.reset()
            if (backup) status.text = s("restored_backup")
        }
    }

    private fun rename() {
        input(s("lesson"), store.lesson.title) { value ->
            require(value.isNotBlank())
            store.edit { store.lesson.title = value.take(100) }
        }
    }

    private fun menu() {
        choices("VuraVision", listOf("files", "lab", "games", "tools", "settings", "help")) {
            when (it) {
                0 -> fileMenu()
                1 -> openExplorer(true)
                2 -> openExplorer(false)
                3 -> classroomTools()
                4 -> settings()
                5 -> quickGuide()
            }
        }
    }

    private fun quickGuide(){
        val c=column().apply{pad(20)};var taps=0
        val title=label(s("help"),24f,NAVY,true);c.addView(title)
        val d=dialog("VuraVision",ScrollView(this).apply{addView(c)})
        title.setOnClickListener{taps++;if(taps==3){prefs.edit().putBoolean("engineering",true).apply();d.dismiss();engineering()}}
        c.addView(label(s("help_text"),16f))
        c.addView(label(tr("Re-tap Pen or Eraser to configure it. Select an item, then tap it again for actions. Swipe vertically on a PDF to turn pages; drag horizontally to move it. Smart: choose a mode, then circle your writing. Shape mode converts each completed drawing automatically.","برای تنظیم قلم یا پاک‌کن، دوباره روی ابزار فعال بزنید. شیء را انتخاب و دوباره لمس کنید تا عملیات باز شود. روی PDF عمودی بکشید تا صفحه عوض شود؛ کشیدن افقی آن را جابه‌جا می‌کند. در Smart حالت را انتخاب و دور نوشته خط بکشید. حالت شکل هر ترسیم را خودکار تبدیل می‌کند."),16f))
    }

    private fun fileMenu() {
        choices(
            s("files"),
            listOf("new", "open", "save", "recent", "export_pdf", "export_png", "export_jpg"),
        ) {
            when (it) {
                0 ->
                    confirm(s("new_confirm")) {
                        persist()
                        documentId = newId()
                        prefs.edit().putString("current", documentId).apply()
                        store.replace(Lesson(title = s("lesson")))
                        board.clearSelection()
                        board.reset()
                    }
                1 -> pick("application/octet-stream", 101)
                2 -> export("vura", true) { createDocument(it, "application/octet-stream") }
                3 -> recent()
                4 -> exportChoice("pdf")
                5 -> exportChoice("png")
                6 -> exportChoice("jpg")
            }
        }
    }

    private fun recent() {
        val list =
            File(filesDir, "lessons")
                .listFiles()
                ?.filter { it.extension == "vura" }
                ?.sortedByDescending { it.lastModified() }
                ?.take(30)
                .orEmpty()
        if (list.isEmpty()) {
            toast(s("empty"))
            return
        }
        work({
            list.map { f ->
                val name =
                    runCatching {
                            java.util.zip.ZipFile(f).use { z ->
                                com.google.gson
                                    .Gson()
                                    .fromJson(
                                        z.getInputStream(z.getEntry("document.json")).reader(),
                                        Lesson::class.java,
                                    )
                                    .title
                            }
                        }
                        .getOrDefault(f.name)
                "$name  ·  ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(f.lastModified()))}"
            }
        }) { names ->
            AlertDialog.Builder(this)
                .setTitle(s("recent"))
                .setItems(names.toTypedArray()) { _, i -> loadFile(list[i]) }
                .setNegativeButton(s("cancel"), null)
                .show()
        }
    }

    private fun colors() {
        val colors =
            listOf(
                    0xff243746,
                    0xffe46d38,
                    0xff167b79,
                    0xff6373c2,
                    0xffd35473,
                    0xffe5b633,
                    0xffeeeeee,
                    0xff000000,
                )
                .map { it.toInt() }
        val r = row().apply { pad(12) }
        val d = dialog(s("color"), r)
        colors.forEach { color ->
            r.addView(
                button("●") {
                        board.inkColor = color
                        if (board.chosen().isNotEmpty()) board.edit { it.color = color }
                        d.dismiss()
                    }
                    .apply {
                        setTextColor(color)
                        textSize = 28f
                    },
                LinearLayout.LayoutParams(0, dp(54), 1f),
            )
        }
    }

    private fun width() {
        val c = column().apply { pad(20) }
        val read = label("${board.inkWidth.toInt()}")
        val seek =
            SeekBar(this).apply {
                max = 19
                progress = board.inkWidth.toInt() - 1
                setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                            board.inkWidth = p + 1f
                            read.text = "${p+1}"
                        }

                        override fun onStartTrackingTouch(s: SeekBar?) {}

                        override fun onStopTrackingTouch(s: SeekBar?) {}
                    }
                )
            }
        c.addView(read)
        c.addView(seek)
        c.addView(button(s("apply")) { board.edit { it.width = board.inkWidth } })
        dialog(s("width"), c)
    }

    private fun insert() {
        choices(s("insert"), listOf("sticky", "graph", "image", "pdf", "math")) { when(it){
            0->addText(true);1->graph();2->pick("image/*",102);3->pick("application/pdf",103);4->math()
        }}
    }

    private fun math() {
        choices(s("math"), listOf("evaluate", "solve")) { i ->
            input(s(if (i == 0) "evaluate" else "solve"), if (i == 0) "2*(3+4)" else "2x+3=11") {
                value ->
                val answer =
                    if (i == 0) MathTools.format(MathTools().evaluate(value))
                    else SmartMath.solve(value)
                board.insert(
                    Item(kind = "text", text = "$value\n= $answer", width = 28f, w = 420f, h = 150f)
                )
            }
        }
    }

    private fun editSelection() {
        val items = board.chosen()
        if (items.isEmpty()) return
        val one = items.singleOrNull()
        val keys = mutableListOf("delete", "duplicate", "copy", "lock", "front", "back", "color")
        if (one?.kind in listOf("text", "sticky", "graph")) keys.add("text")
        if(one!=null && one.cuts.isNotEmpty())keys.add("restore_erased")
        if (one?.kind in listOf("text", "sticky")) keys.add("text_size")
        if (one?.kind == "graph") keys.add("domain")
        if (one?.kind == "pdf") keys.addAll(listOf("previous", "next", "page_picker"))
        if (one?.kind in listOf("image", "pdf")) keys.add("crop")
        keys.add("paste")
        choices(s("edit"), keys) { i ->
            when (keys[i]) {
                "delete" -> board.delete()
                "copy" -> {
                    clipboard = items.map { it.deepCopy() }
                    toast(s("copy"))
                }
                "duplicate" -> {
                    store.edit {
                        items
                            .map {
                                it.deepCopy().apply {
                                    id = newId()
                                    x += 24
                                    y += 24
                                    locked = false
                                }
                            }
                            .forEach { store.page.items.add(it) }
                    }
                }
                "paste" -> paste()
                "lock" -> {
                    store.edit { items.forEach { it.locked = !it.locked } }
                }
                "front" -> {
                    store.edit {
                        store.page.items.removeAll(items.toSet())
                        store.page.items.addAll(items)
                    }
                }
                "back" -> {
                    store.edit {
                        store.page.items.removeAll(items.toSet())
                        store.page.items.addAll(0, items)
                    }
                }
                "color" -> colors()
                "restore_erased" -> board.edit{it.cuts=emptyList()}
                "text" -> if(one!!.kind=="graph")graph(existing=one)else textEditor(one)
                "text_size" ->
                    input(s("text_size"), one!!.width.toInt().toString()) { v ->
                        val n = v.toFloat()
                        require(n in 8f..120f)
                        board.edit { it.width = n;TextLayout.fit(it) }
                    }
                "domain" ->
                    input(s("domain"), one!!.domain.toString()) { v ->
                        val n = v.toFloat()
                        require(n in .1f..1000f)
                        board.edit { it.domain = n }
                    }
                "previous" -> board.edit { it.pdfPage = (it.pdfPage - 1).coerceAtLeast(0) }
                "next" ->
                    board.edit { it.pdfPage = (it.pdfPage + 1).coerceAtMost(it.pageCount - 1) }
                "page_picker" ->
                    input(s("page_picker"), "${one!!.pdfPage+1}") { v ->
                        val page = v.toInt()
                        require(page in 1..one.pageCount)
                        board.edit { it.pdfPage = page - 1 }
                    }
                "crop" -> crop(one!!)
            }
        }
    }

    private fun paste() {
        if (clipboard.isEmpty()) return
        val c = board.center()
        val bounds = contentBounds(clipboard)
        store.edit {
            val pasted =
                clipboard.map {
                    it.deepCopy().apply {
                        id = newId()
                        x += c.x - bounds.centerX()
                        y += c.y - bounds.centerY()
                        locked = false
                    }
                }
            store.page.items.addAll(pasted)
            board.selected.clear()
            board.selected.addAll(pasted.map { it.id })
        }
        board.tool = "select"
        refreshDock()
    }

    private fun crop(o: Item) {
        if (o.locked) return
        input(s("crop_help"), "0, 0, 100, 100") { value ->
            val n = value.split(',').map { it.trim().toFloat() }
            require(n.size == 4 && n.all { it in 0f..100f } && n[2] > n[0] && n[3] > n[1])
            work({
                val b = requireNotNull(media.image(o, true))
                val left = (b.width * n[0] / 100).toInt().coerceAtMost(b.width - 1)
                val top = (b.height * n[1] / 100).toInt().coerceAtMost(b.height - 1)
                val w = ((n[2] - n[0]) * b.width / 100).toInt().coerceIn(1, b.width - left)
                val h = ((n[3] - n[1]) * b.height / 100).toInt().coerceIn(1, b.height - top)
                media.save(Bitmap.createBitmap(b, left, top, w, h))
            }) { asset ->
                board.edit {
                    it.asset = asset
                    it.kind = "image"
                    it.w *= ((n[2] - n[0]) / 100)
                    it.h *= ((n[3] - n[1]) / 100)
                    it.pdfPage = 0
                    it.pageCount = 1
                }
            }
        }
    }

    private fun pages() {
        val c=column().apply{pad(14)};val d=dialog(s("pages"),ScrollView(this).apply{addView(c)})
        val actions=row()
        actions.addView(button("＋ ${s("add_page")}",true){addPage();d.dismiss()})
        actions.addView(button(s("background")){choices(s("background"),listOf("white","dark","dots","grid","ruled")){i->store.edit{store.page.background=listOf("white","dark","dots","grid","ruled")[i]};d.dismiss()}})
        actions.addView(button(s("clear")){confirm(s("clear_confirm")){store.edit{store.page.items.removeAll{!it.locked}};board.clearSelection();d.dismiss()}})
        c.addView(scrollRow(actions))
        store.lesson.pages.forEachIndexed{i,page->
            val r=row().apply{pad(6);background=rounded(if(i==store.lesson.current)0xffeeebff.toInt()else PAPER,dp(12).toFloat())}
            r.addView(object:View(this){override fun onDraw(canvas:Canvas){board.renderer.page(canvas,page,width,height,false)}}.apply{setOnClickListener{switchPage(i);d.dismiss()}},LinearLayout.LayoutParams(dp(108),dp(64)))
            r.addView(button("${s("page_short")} ${i+1}",i==store.lesson.current){switchPage(i);d.dismiss()},LinearLayout.LayoutParams(0,dp(50),1f))
            r.addView(button("⋯"){choices(s("pages"),listOf("duplicate","move_left","move_right","delete")){action->
                fun perform(){store.edit{when(action){
                    0->if(store.lesson.pages.size<200){store.lesson.pages.add(i+1,page.copy(id=newId(),items=page.items.map{it.deepCopy().apply{id=newId()}}.toMutableList()));store.lesson.current=i+1}
                    1,2->{val target=i+if(action==1)-1 else 1;if(target in store.lesson.pages.indices){java.util.Collections.swap(store.lesson.pages,i,target);store.lesson.current=target}}
                    3->if(store.lesson.pages.size>1){store.lesson.pages.removeAt(i);store.lesson.current=store.lesson.current.coerceAtMost(store.lesson.pages.lastIndex)}
                }};board.clearSelection();board.reset();d.dismiss();pages()}
                if(action==3)confirm(s("clear_confirm")){perform()}else perform()
            }})
            c.addView(r,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(4),0,dp(4))})
        }
    }

    private fun pick(mime: String, code: Int) {
        try {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = mime
                    if (code == 101) type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                },
                code,
            )
        } catch (e: Exception) {
            error(e)
        }
    }

    @Deprecated("Platform compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        if(requestCode==104){val f=File(cacheDir,"lab-snapshot.png");if(f.exists()){val bitmap=BitmapFactory.decodeFile(f.path);if(bitmap!=null)insertBitmap(bitmap);f.delete()};return}
        val uri = data?.data ?: return
        when (requestCode) {
            101 ->
                work({ files.read(requireNotNull(contentResolver.openInputStream(uri))) }) { doc ->
                    persist()
                    documentId = newId()
                    prefs.edit().putString("current", documentId).apply()
                    store.replace(doc)
                    board.clearSelection()
                    board.fit()
                }
            102,
            103 ->
                work({
                    val asset =
                        media.import(
                            requireNotNull(contentResolver.openInputStream(uri)),
                            if (requestCode == 103) "pdf" else "img",
                        )
                    try {
                        val o =
                            Item(
                                kind = if (requestCode == 103) "pdf" else "image",
                                asset = asset,
                                w = 560f,
                                h = 400f,
                            )
                        if (requestCode == 103) o.pageCount = media.pdfCount(asset)
                        val b = requireNotNull(media.image(o, true))
                        o.h = o.w * b.height / b.width
                        o
                    } catch (e: Exception) {
                        media.file(asset).delete()
                        throw e
                    }
                }) {
                    board.insert(it)
                }
            104 -> {
                val file = pendingExport ?: return
                work({
                    requireNotNull(contentResolver.openOutputStream(uri, "wt")).use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                }) {
                    toast(s("saved"))
                    pendingExport = null
                }
            }
        }
    }

    private fun exportChoice(ext: String) {
        if (ext == "pdf")
            choices(s("export_pdf"), listOf("current_page", "all_pages")) { i ->
                export(ext, i == 1) { createDocument(it, "application/pdf") }
            }
        else export(ext, false) { createDocument(it, "image/${if(ext=="jpg")"jpeg"else"png"}") }
    }

    private fun export(ext: String, all: Boolean, done: (File) -> Unit) {
        val doc = store.lesson.copyDeep()
        work({
            val folder = File(cacheDir, "exports").apply { mkdirs() }
            val target = File(folder, "VuraVision-${System.currentTimeMillis()}.$ext")
            val renderer = Renderer(media)
            if (ext == "vura") target.outputStream().use { files.write(doc, it) }
            else if (ext == "pdf") {
                val pdf = PdfDocument()
                try {
                    val pages = if (all) doc.pages else listOf(doc.pages[doc.current])
                    pages.forEachIndexed { i, page ->
                        val p =
                            pdf.startPage(PdfDocument.PageInfo.Builder(1190, 842, i + 1).create())
                        renderer.page(p.canvas, page, 1190, 842, true)
                        pdf.finishPage(p)
                    }
                    target.outputStream().use { pdf.writeTo(it) }
                } finally {
                    pdf.close()
                }
            } else {
                val bitmap = Bitmap.createBitmap(1920, 1200, Bitmap.Config.ARGB_8888)
                renderer.page(Canvas(bitmap), doc.pages[doc.current], 1920, 1200, true)
                target.outputStream().use {
                    bitmap.compress(
                        if (ext == "jpg") Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG,
                        92,
                        it,
                    )
                }
                bitmap.recycle()
            }
            target
        }) {
            done(it)
        }
    }

    private fun createDocument(file: File, mime: String) {
        pendingExport = file
        try {
            startActivityForResult(
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    type = mime
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_TITLE, file.name)
                },
                104,
            )
        } catch (e: Exception) {
            error(e)
        }
    }

    private fun share() {
        export("pdf", true) { file ->
            try {
                sharing?.stop()
                val server = Sharing(file, "application/pdf")
                server.start(5000, false)
                val urls=server.urls(this)
                val url=urls.firstOrNull()
                if (url == null) {
                    server.stop()
                    toast(s("no_network"))
                    return@export
                }
                sharing = server
                val c =
                    column().apply {
                        pad(20)
                        gravity = Gravity.CENTER
                    }
                c.addView(label(s("share_help"), 15f))
                val qr=ImageView(this).apply{setImageBitmap(Sharing.qr(url));contentDescription=url}
                c.addView(qr,LinearLayout.LayoutParams(dp(230),dp(230)))
                val address=label(url,12f).apply{setTextIsSelectable(true)};c.addView(address)
                if(urls.size>1)c.addView(button(s("network_address")){AlertDialog.Builder(this).setItems(urls.toTypedArray()){_,index->address.text=urls[index];qr.setImageBitmap(Sharing.qr(urls[index]));qr.contentDescription=urls[index]}.show()})
                c.addView(label(s("share_troubleshoot"),12f,MUTED))
                val count = label("${s("downloads")}: 0", 12f, MUTED)
                c.addView(count)
                val d = dialog(s("share"), c)
                shareDialog = d
                val update =
                    object : Runnable {
                        override fun run() {
                            if (d.isShowing) {
                                count.text = "${s("downloads")}: ${server.downloads}"
                                handler.postDelayed(this, 1000)
                            }
                        }
                    }
                handler.post(update)
                handler.postDelayed(
                    {
                        if (sharing === server) {
                            server.stop()
                            d.dismiss()
                            toast(s("expired"))
                        }
                    },
                    1800000,
                )
                c.addView(button(s("stop_sharing")){server.stop();if(sharing===server)sharing=null;d.dismiss()})
                d.setOnDismissListener{handler.removeCallbacks(update)}
            } catch (e: Exception) {
                error(e)
            }
        }
    }

    private var smartSource="offline"
    private fun smart(){
        val c=column().apply{pad(20)}
        c.addView(label(tr("Choose what the smart pen should do","کار قلم هوشمند را انتخاب کنید"),18f,NAVY,true))
        c.addView(label(tr("Shape: draw normally; each completed stroke becomes geometry. Other modes: circle existing ink to recognize it. An open stroke remains ink. Review every recognition before applying.","شکل: عادی بکشید؛ هر خط کامل به شکل هندسی تبدیل می‌شود. حالت‌های دیگر: دور نوشتهٔ قبلی خط بکشید. خط باز، دست‌نویس باقی می‌ماند. نتیجه را قبل از اعمال بررسی کنید."),14f,MUTED))
        val source=Spinner(this);source.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf(tr("Offline Latin OCR · no download","تشخیص لاتین آفلاین · بدون دانلود"),tr("Google English handwriting","دست‌نویس انگلیسی گوگل"),tr("Google Persian handwriting","دست‌نویس فارسی گوگل")));source.setSelection(listOf("offline","en-US","fa").indexOf(smartSource).coerceAtLeast(0));c.addView(source)
        val d=dialog(s("smart"),ScrollView(this).apply{addView(c)})
        listOf("text" to tr("Text","متن"),"formula" to tr("Calculate / solve equation","محاسبه / حل معادله"),"graph" to tr("Plot function","رسم تابع"),"shape" to tr("Automatic shapes · offline","اشکال خودکار · آفلاین")).forEach{(key,title)->c.addView(button(title,board.smartMode==key){smartSource=listOf("offline","en-US","fa")[source.selectedItemPosition];board.smartMode=key;board.tool="smart";board.clearSelection();prefs.edit().putString("smartSource",smartSource).putString("smartMode",key).apply();refreshDock();status.text=title;d.dismiss()})}
        if(board.chosen().isNotEmpty())c.addView(button(tr("Process current selection","پردازش انتخاب فعلی")){smartSource=listOf("offline","en-US","fa")[source.selectedItemPosition];d.dismiss();processSmart(board.chosen())})
        c.addView(button(s("models")){d.dismiss();models()})
        c.addView(label(tr("Offline Latin OCR works best on clear separated characters. It does not recognize Persian or stacked fractions, integrals and general symbolic notation. Formula solving supports arithmetic and linear/quadratic equations in x.","تشخیص لاتین آفلاین برای حروف و اعداد واضح و جدا مناسب‌تر است. فارسی، کسر چندطبقه، انتگرال و نمادگذاری عمومی ریاضی را تشخیص نمی‌دهد. حل فرمول شامل محاسبات و معادلات خطی/درجه‌دو با x است."),13f,MUTED))
    }
    private fun processSmart(items:List<Item>){
        val chosen=items.filter{!it.locked&&it.kind in listOf("ink","text","sticky")};if(chosen.isEmpty())return
        val pageId=store.page.id;val mode=board.smartMode
        fun reviewResult(values:List<String>){if(destroyed)return;status.text=s("ready");if(store.page.id!=pageId){toast(tr("Return to the original page and try again","به صفحهٔ اصلی برگردید و دوباره تلاش کنید"));return};reviewSmart(chosen,values,mode)}
        if(mode=="shape"){store.edit{chosen.filter{it.kind=="ink"}.forEach{o->ShapeRecognition.convert(o)?.let{store.page.items.remove(o);store.page.items.add(it)}}};board.clearSelection();return}
        if(chosen.all{it.kind!="ink"}){reviewResult(listOf(chosen.joinToString("\n"){it.text}));return}
        val strokes=chosen.filter{it.kind=="ink"};status.text=s("busy")
        fun fallback(e:Exception){lastError=android.util.Log.getStackTraceString(e);status.text=s("error");toast(recognition.failure(this,e));reviewResult(emptyList())}
        if(smartSource=="offline")OfflineText.recognize(chosen,board.renderer,::reviewResult,::fallback)
        else recognition.installed(smartSource,{installed->if(installed)recognition.recognize(smartSource,strokes,::reviewResult,::fallback)else{status.text=s("model_required");reviewResult(emptyList());toast(s("model_required"))}},::fallback)
    }
    private fun reviewSmart(original:List<Item>,candidates:List<String>,mode:String){
        val pageId=store.page.id;val c=column().apply{pad(20)}
        c.addView(label(if(candidates.isEmpty())tr("No reliable recognition. Enter or correct the content below; your original ink is preserved until you apply.","نتیجهٔ قابل‌اعتماد پیدا نشد. متن را وارد یا اصلاح کنید؛ تا زمان اعمال، دست‌نویس اصلی حفظ می‌شود.")else tr("Review the recognized content","متن تشخیص‌داده‌شده را بررسی کنید"),14f,MUTED))
        val text=field(candidates.firstOrNull().orEmpty()).apply{if(mode!="text"){layoutDirection=View.LAYOUT_DIRECTION_LTR;textDirection=View.TEXT_DIRECTION_LTR}};c.addView(text)
        val options=row();candidates.take(3).forEach{v->options.addView(button(v.take(40)){text.setText(v)})};c.addView(scrollRow(options))
        val result=label("",19f,TEAL,true).apply{setTextIsSelectable(true)};c.addView(result)
        val keep=CheckBox(this).apply{this.text=s("keep_ink");isChecked=true};c.addView(keep)
        fun prepared():Item{val value=text.text.toString();require(value.isNotBlank()){s("empty")};return when(mode){
            "formula"->Item(kind="text",text="$value\n${SmartMath.result(value)}",width=30f).also{TextLayout.fit(it)}
            "graph"->{require(value.split(';').size in 1..3);value.split(';').forEach{MathTools().compile(it)};Item(kind="graph",text=value,w=560f,h=400f)}
            else->Item(kind="text",text=value,width=30f).also{TextLayout.fit(it)}
        }}
        c.addView(button(tr("Preview result","پیش‌نمایش نتیجه")){try{val item=prepared();result.text=if(item.kind=="graph")tr("Function is valid — Apply to plot","تابع معتبر است — برای رسم، اعمال را بزنید")else item.text;text.error=null}catch(e:Exception){text.error=e.message;result.text=""}})
        val d=dialog(s("review"),ScrollView(this).apply{addView(c)})
        c.addView(button(s("apply"),true){try{require(store.page.id==pageId){"Page changed"};val item=prepared();val bounds=contentBounds(original);item.x=bounds.left;item.y=if(keep.isChecked)bounds.bottom+20 else bounds.top;store.edit{if(!keep.isChecked)store.page.items.removeAll{v->!v.locked&&original.any{it.id==v.id}};store.page.items.add(item)};board.selected.clear();board.selected.add(item.id);refreshDock();d.dismiss()}catch(e:Exception){text.error=e.message}})
    }

    private fun models(){
        val c=column().apply{pad(20)}
        c.addView(label(tr("Offline Latin OCR is already included","تشخیص لاتین آفلاین همراه برنامه نصب است"),18f,TEAL,true))
        c.addView(label(tr("No model download is needed for clear Latin characters and simple arithmetic. Google digital-ink models below are optional for handwriting; Persian still needs its language model.","برای حروف واضح لاتین و محاسبات ساده نیازی به دانلود نیست. مدل‌های زیر برای دست‌نویس گوگل‌اند؛ تشخیص دست‌نویس فارسی همچنان به مدل زبان نیاز دارد."),14f,MUTED))
        if(!recognition.downloadManagerReady(this))c.addView(label(s("download_manager_disabled"),13f,ORANGE))
        listOf("en-US" to "english","fa" to "persian").forEach{(lang,key)->
            val card=column().apply{pad(14);background=rounded(PAPER,dp(14).toFloat())};c.addView(card,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(12),0,dp(12))})
            card.addView(label(s(key),18f,NAVY,true));val state=label(s("check_status"),14f,MUTED);card.addView(state)
            val progress=ProgressBar(this).apply{visibility=View.GONE};card.addView(progress,LinearLayout.LayoutParams(dp(32),dp(32)))
            val r=row();card.addView(scrollRow(r));var raw="";var busy=false
            val install=button(s("install")){};r.addView(install)
            fun failed(e:Exception){busy=false;progress.visibility=View.GONE;raw=android.util.Log.getStackTraceString(e);lastError=raw;state.text=recognition.failure(this,e);install.isEnabled=true;install.text=s("retry")}
            fun update(){recognition.installed(lang,{ready->if(!destroyed){state.text=s(if(ready)"installed"else"not_installed");install.text=s(if(ready)"installed"else"install");install.isEnabled=!ready&&!busy}},::failed)}
            fun download(){if(busy)return;busy=true;install.isEnabled=false;progress.visibility=View.VISIBLE;state.text=s("downloading");recognition.download(lang,{if(!destroyed){busy=false;progress.visibility=View.GONE;update()}},{if(!destroyed)failed(it)})}
            install.setOnClickListener{download()}
            r.addView(button(tr("Fresh retry","دانلود تازه")){if(!busy){recognition.remove(lang,{download()},::failed)}})
            r.addView(button(s("check_status")){if(!busy)update()})
            r.addView(button(tr("Details","جزئیات")){val details="Model: $lang\nSDK: digital-ink 19.0.0\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}\nAPI: ${Build.VERSION.SDK_INT}\nLocale: ${Locale.getDefault()}\n\n${raw.ifBlank{tr("No error recorded","خطایی ثبت نشده است")}}";val body=column().apply{pad(16)};body.addView(label(details,12f).apply{setTextIsSelectable(true)});body.addView(button(s("copy")){(getSystemService(CLIPBOARD_SERVICE)as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("VuraVision diagnostics",details));toast(s("done"))});dialog(tr("Download diagnostics","گزارش دانلود"),ScrollView(this).apply{addView(body)})})
            update()
        }
        dialog(s("models"),ScrollView(this).apply{addView(c)})
    }

    private fun settings() {
        val keys=mutableListOf("language","models","cache","about")
        if(prefs.getBoolean("engineering",false))keys.add("engineering")
        choices(s("settings"),keys){index->when(keys[index]){
            "language"->choices(s("language"),listOf("english","persian")){i->persist();prefs.edit().putString("language",if(i==0)"en"else"fa").apply();recreate()}
            "models"->models()
            "cache"->{media.clear();board.sceneChanged();toast(s("done"))}
            "about"->{val c=column().apply{pad(20)};c.addView(ImageView(this).apply{setImageResource(R.drawable.vura_brand);scaleType=ImageView.ScaleType.FIT_CENTER},LinearLayout.LayoutParams(-1,dp(150)));c.addView(label("VuraVision ${BuildConfig.VERSION_NAME}\n\n${s("about_text")}",16f).apply{setOnClickListener{taps++;if(taps>=7){prefs.edit().putBoolean("engineering",true).apply();toast(s("unlocked"))}}});dialog(s("about"),c)}
            "engineering"->engineering()
        }}
    }

    private fun engineering() {
        choices(s("engineering"), listOf("touch_test", "thresholds", "report", "stress")) {
            when (it) {
                0 ->
                    dialog(
                        s("touch_test"),
                        TouchDiagnostics(this, board.profile).apply { minimumHeight = dp(400) },
                    )
                1 -> calibrateTips()
                2 -> {
                    val report =
                        "VuraVision ${BuildConfig.VERSION_NAME}\n${Build.MANUFACTURER} ${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}\n${resources.displayMetrics}\nPointers: device-reported only\nObjects: ${store.page.items.size}\nLast error: $lastError\n" +
                            InputDevice.getDeviceIds().joinToString("\n") { id ->
                                InputDevice.getDevice(id)
                                    ?.let { "${it.name}: sources=${it.sources}" }
                                    .orEmpty()
                            }
                    val text =
                        label(report, 13f).apply {
                            pad(18)
                            setTextIsSelectable(true)
                        }
                    dialog(s("report"), ScrollView(this).apply { addView(text) })
                }
                3 -> {
                    store.edit {
                        repeat(300) { n ->
                            store.page.items.add(
                                Item(
                                    x = (n % 30) * 24f,
                                    y = (n / 30) * 30f,
                                    w = 20f,
                                    h = 20f,
                                    inkW = 20f,
                                    inkH = 20f,
                                    color = if (n % 2 == 0) ORANGE else TEAL,
                                    points =
                                        (0..30)
                                            .map { v ->
                                                Point(v * 20f / 30, 10 + sin(v * .4).toFloat() * 8)
                                            }
                                            .toMutableList(),
                                )
                            )
                        }
                    }
                    board.fit()
                }
            }
        }
    }

    private fun classroomTools() {choices(s("tools"),listOf("timer","stopwatch","dice","scoreboard","curtain")){which->floatingTools.open(listOf("timer","stopwatch","dice","scoreboard","curtain")[which])}}

    private fun openExplorer(lab:Boolean){
        val options=listOf(if(lab)tr("Discovery lab · 68 interactive experiments (Persian)","آزمایشگاه اکتشاف · ۶۸ آزمایش تعاملی")else tr("Arcade · 49 games","آرکید · ۴۹ بازی"),if(lab)tr("Core labs · English / Persian","آزمایش‌های پایه · فارسی / انگلیسی")else tr("Classroom quizzes & games","مسابقه‌های آموزشی کلاس"))
        AlertDialog.Builder(this).setTitle(s(if(lab)"lab"else"games")).setItems(options.toTypedArray()){_,i->if(i==0)startActivityForResult(Intent(this,ExploreActivity::class.java).putExtra("lab",lab),104)else if(lab)Labs.show(this){insertBitmap(it)}else Games.show(this)}.setNegativeButton(s("close"),null).show()
    }

    private fun insertBitmap(bitmap: Bitmap) {
        work({ media.save(bitmap) }) { name ->
            board.insert(
                Item(
                    kind = "image",
                    asset = name,
                    w = 620f,
                    h = 620f * bitmap.height / bitmap.width,
                )
            )
            bitmap.recycle()
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onStop() {
        handler.removeCallbacks(autosave)
        persist()
        super.onStop()
    }

    override fun onDestroy() {
        destroyed = true
        floatingTools.closeAll()
        handler.removeCallbacksAndMessages(null)
        sharing?.stop()
        shareDialog?.dismiss()
        worker.shutdown()
        media.close()
        super.onDestroy()
    }
}
