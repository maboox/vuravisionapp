package com.vuravision.classroom

import android.app.*
import android.content.*
import android.content.res.Configuration
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.*

class MainActivity : Activity() {
    private val projectStore=Store()
    val store:Store get()=if(::board.isInitialized)board.store else projectStore
    private lateinit var whiteboard:Board
    private lateinit var workspaceHost:FrameLayout
    private var pdfPane:PdfPane?=null
    private var shownPdf:PdfWorkspaceState?=null
    private var pendingPdfMode=0
    private var afterPdfSave:(()->Unit)?=null
    private var pendingPdfRevision:Long?=null
    private var pendingPdfAsset:String?=null
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
    private lateinit var selectionBar:LinearLayout
    private lateinit var selectionHost:HorizontalScrollView
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
    private var artPlayer: ArtPlayer? = null
    private val autosave = Runnable { persist() }
    private var dirty = false
    private var loading = false
    private var lastError = ""
    private var destroyed = false
    private var voiceAssistant:VoiceAssistant?=null
    private var voiceScreen:VoiceScreen?=null
    private lateinit var voiceButton:com.google.android.material.button.MaterialButton
    private var voiceStarting=false
    private var voiceStartGeneration=0
    private var lastUserTouch=0L
    private val voiceWorker=Executors.newSingleThreadExecutor()
    private val voiceKeys by lazy{VoiceKeyStore(this)}
    private val voicePermissionRequest=7410

    override fun attachBaseContext(base: Context) {
        val lang =
            base.getSharedPreferences("vura", MODE_PRIVATE).getString("language", "en") ?: "en"
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale(lang))
        super.attachBaseContext(base.createConfigurationContext(config))
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        Fonts.initialize(this)
        pendingExport = prefs.getString("pendingExport", null)?.let { name ->
            File(cacheDir, "exports").resolve(File(name).name).takeIf { it.isFile && it.length()>0 }
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        media = Media(this)
        files = LessonFiles(media)
        board = Board(this, projectStore, Renderer(media))
        whiteboard=board
        board.profile =
            TouchProfile(
                true,
                prefs.getFloat("thin", 5f),
                prefs.getFloat("palm", 50f),
                prefs.getBoolean("palmErase", false),
                prefs.getBoolean("multiTouch",true),
                prefs.getFloat("thickWidth",10f),
            )
        board.profile.thickColor=prefs.getInt("thickColor",0xffe45756.toInt())
        board.eraserMode=prefs.getString("eraserMode","stroke")?:"stroke"
        board.eraserRadius=prefs.getFloat("eraserRadius",18f)
        board.eraseObjects=prefs.getBoolean("eraseObjects",true)
        board.erasePdfContent=prefs.getBoolean("erasePdfContent",false)
        board.pdfPalmErase=prefs.getBoolean("pdfPalmErase",false)
        board.penColor=prefs.getInt("penColor",NAVY)
        board.highlightColor=prefs.getInt("highlightColor",0xffffcf40.toInt())
        board.penWidth=prefs.getFloat("penWidth",4f)
        board.highlightWidth=prefs.getFloat("highlightWidth",6f)
        board.profile.thickStyle=prefs.getString("thickStyle","round")?:"round"
        board.dashLength=prefs.getFloat("dashLength",12f)
        board.dashGap=prefs.getFloat("dashGap",8f)
        board.penStyle=prefs.getString("penStyle","round")?:"round"
        board.touchMode=prefs.getBoolean("touchMode",false)
        smartSource=prefs.getString("smartSource","auto")?:"auto"
        board.smartMode=prefs.getString("smartMode","text")?:"text"
        board.holdRecognitionEnabled=prefs.getBoolean("holdRecognition",true)
        board.holdDelayMillis=prefs.getLong("holdDelay",1000L).coerceIn(500L,2500L)
        board.holdTolerance=prefs.getFloat("holdTolerance",4f).coerceIn(2f,12f)
        board.guideSnapEnabled=prefs.getBoolean("guideSnap",true)
        buildUI()
        Fonts.applyTree(window.decorView)
        media.assetReady = { asset -> handler.post { if(!destroyed){
            if(projectStore.page.items.any{it.asset==asset})whiteboard.sceneChanged()
            pdfPane?.mediaReady(asset)
        } } }
        media.error = { message -> handler.post { status.text = s("error") + ": " + message } }
        whiteboard.onActivate={activateBoard(whiteboard)}
        board.onSelection = { refreshDock() }
        board.onObjectActions={editSelection()}
        board.onMindAdd={node,sibling->mindNode(node,sibling)}
        board.onSmart={processSmart(it)}
        store.changed = {
            dirty = true
            if(!whiteboard.isCommitting)whiteboard.sceneChanged() else whiteboard.invalidate()
            refreshDock()
            refreshTitle()
            status.text = s("saving")
            handler.removeCallbacks(autosave)
            handler.postDelayed(autosave, 1800)
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

    private val chrome = mutableListOf<View>()
    private var focusMode = false
    private var sidePanel: View? = null
    private var sidePanelPage: Page? = null
    private var splitControls: FrameLayout? = null
    private var splitControlsPage: Page? = null
    private fun icon(key:String, title:String, active:Boolean=false, action:()->Unit)=WorkspaceIcon(this,key,title,active,action)
    private fun surface(view:View):View = view.apply {
        background=rounded(SURFACE,dp(12).toFloat(),OUTLINE)
        elevation=dp(4).toFloat()
    }
    private fun buildUI() {
        root=column().apply{fitsSystemWindows=true;setBackgroundColor(PAPER)}
        setContentView(root)
        canvasHost=FrameLayout(this)
        workspaceHost=FrameLayout(this)
        workspaceHost.addView(whiteboard,FrameLayout.LayoutParams(-1,-1))
        canvasHost.addView(workspaceHost,FrameLayout.LayoutParams(-1,-1))
        root.addView(canvasHost,LinearLayout.LayoutParams(-1,-1))
        floatingTools=ClassroomWidgets(this,canvasHost)
        fun floating(v:View, gravity:Int, top:Int=12, bottom:Int=12, width:Int=-2, height:Int=-2){
            surface(v);canvasHost.addView(v,FrameLayout.LayoutParams(width,height,gravity).apply{setMargins(dp(12),dp(top),dp(12),dp(bottom))});chrome.add(v)
        }
        val header=row().apply{pad(4)}
        header.addView(icon("menu",s("main_menu")){menu()}.apply{id=R.id.main_menu_button})
        header.addView(icon("files",s("files")){fileMenu()})
        header.addView(icon("undo",s("undo")){store.undo();board.clearSelection()})
        header.addView(icon("redo",s("redo")){store.redo();board.clearSelection()})
        header.addView(icon("share",s("share")){shareOptions()})
        floating(header,Gravity.TOP or Gravity.START)
        // Titles/status remain available in the document menu; canvas chrome uses icons.
        titleView=label("");status=label("");pageLabel=label("")
        dock=column().apply{pad(4)}
        val dockScroll=ScrollView(this).apply{isVerticalScrollBarEnabled=false;addView(dock)}
        floating(dockScroll,Gravity.TOP or Gravity.BOTTOM or Gravity.START,80,78,dp(56),-1)
        pageStrip=row().apply{pad(4)}
        floating(pageStrip,Gravity.BOTTOM or Gravity.START)
        selectionBar=row().apply{pad(4)}
        surface(selectionBar)
        selectionHost=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;addView(selectionBar);visibility=View.GONE}
        canvasHost.addView(selectionHost,FrameLayout.LayoutParams(-2,dp(56),Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply{setMargins(dp(78),dp(78),dp(12),0)})
        val focus=icon("fullscreen",tr("Hide / show toolbars","پنهان / نمایان کردن ابزارها")){
            focusMode=!focusMode
            window.decorView.systemUiVisibility=if(focusMode)View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY else View.SYSTEM_UI_FLAG_VISIBLE
            chrome.forEach{it.visibility=if(focusMode)View.GONE else View.VISIBLE}
            if(focusMode){closeSidePanel();selectionHost.visibility=View.GONE}else refreshSelectionBar()
            refreshSplitControls()
        }
        surface(focus)
        canvasHost.addView(focus,FrameLayout.LayoutParams(dp(48),dp(48),Gravity.BOTTOM or Gravity.END).apply{setMargins(dp(12),dp(12),dp(12),dp(12))})
        voiceButton=button(s("voice_assistant")){toggleVoice()}.apply{
            id=R.id.voice_assistant_button
            setIconResource(R.drawable.feather_mic);iconSize=dp(20)
            maxWidth=dp(300);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END
            contentDescription=s("voice_assistant")
        }
        // Independent control remains reachable when the drawing toolbars are hidden.
        canvasHost.addView(voiceButton,FrameLayout.LayoutParams(-2,dp(48),Gravity.BOTTOM or Gravity.RIGHT).apply{setMargins(dp(78),dp(12),dp(if(resources.configuration.layoutDirection==View.LAYOUT_DIRECTION_RTL)76 else 12),dp(72))})
        canvasHost.addOnLayoutChangeListener{_,l,t,r,b,ol,ot,or,ob->if(r-l!=or-ol||b-t!=ob-ot)refreshSplitControls()}
        refreshDock();refreshPages()
    }
    private fun closeSidePanel(){sidePanel?.let{canvasHost.removeView(it)};sidePanel=null;sidePanelPage=null}
    private fun showSidePanel(title:String, content:View){
        closeSidePanel()
        val panel=column().apply{pad(12)}
        val heading=row();heading.addView(label(title,18f,NAVY,true),LinearLayout.LayoutParams(0,-2,1f))
        heading.addView(icon("close",s("close")){closeSidePanel()});panel.addView(heading)
        panel.addView(ScrollView(this).apply{addView(content)},LinearLayout.LayoutParams(-1,0,1f))
        surface(panel);sidePanel=panel;sidePanelPage=store.page
        canvasHost.addView(panel,FrameLayout.LayoutParams(dp(340).coerceAtMost(resources.displayMetrics.widthPixels-dp(88)),-1,Gravity.END).apply{setMargins(dp(78),dp(76),dp(12),dp(76))})
    }

    private fun refreshTitle() {
        titleView.text = projectStore.lesson.title.ifBlank { s("lesson") }
        pageLabel.text =
            "${projectStore.lesson.current+1} / ${projectStore.lesson.pages.size}   ·   ${s("pages")}"
        refreshPages()
    }

    private fun refreshDock() {
        if(!::dock.isInitialized)return
        syncPdfWorkspace()
        if(sidePanelPage!=null && sidePanelPage!==store.page)closeSidePanel()
        if(splitControlsPage!==store.page)refreshSplitControls()
        refreshSelectionBar()
        val signature="${board.tool}:${store.page.id}:${board.touchMode}:${board.selected.joinToString()}:${board.chosen().joinToString{it.pdfPage.toString()}}"
        if(signature==dockSignature)return
        dockSignature=signature;dock.removeAllViews()
        listOf("select","pen","erase","pan","shape","text","smart","layers","split","touch","insert","tools").forEach { key ->
            val title=when(key){"layers"->tr("Layers","لایه‌ها");"split"->tr("Split board","تقسیم تخته");"touch"->if(board.touchMode)tr("Pen + touch","قلم + لمس")else tr("Two tips","دو سر قلم");else->s(key)}
            dock.addView(icon(key,title,board.tool==key || key=="touch"&&board.touchMode){
                when(key){
                    "layers"->layers();"split"->splitSettings();"text"->addText(false);"shape"->{pdfPane?.writingMode();shapes()};"smart"->{pdfPane?.writingMode();smart()}
                    "touch"->{if(!board.isDrawing){board.touchMode=!board.touchMode;prefs.edit().putBoolean("touchMode",board.touchMode).apply();dockSignature="";refreshDock()}}
                    "insert"->insert();"tools"->classroomTools()
                    else->{if(key!="pan")pdfPane?.writingMode();if(board.tool==key && key=="pen")penSettings() else if(board.tool==key && key=="erase")eraserSettings() else {board.tool=key;board.clearSelection();refreshDock()}}
                }
            },LinearLayout.LayoutParams(dp(48),dp(48)))
        }
    }
    private fun refreshSelectionBar(){
        if(!::selectionBar.isInitialized)return
        val items=board.chosen();selectionHost.visibility=if(items.isEmpty()||focusMode)View.GONE else View.VISIBLE
        selectionBar.removeAllViews();if(items.isEmpty())return
        fun icon(key:String,title:String=s(key),action:()->Unit){selectionBar.addView(ActionIcon(this,key,title,action),LinearLayout.LayoutParams(dp(48),dp(48)))}
        val pdf=items.singleOrNull()?.takeIf{it.kind=="pdf"}
        if(pdf!=null){
            icon("previous"){if(pdf.pdfPage>0)board.edit{it.pdfPage--}}
            selectionBar.addView(button("${pdf.pdfPage+1} / ${pdf.pageCount}"){input(s("page_picker"),"${pdf.pdfPage+1}"){v->val n=v.toInt();require(n in 1..pdf.pageCount);board.edit{it.pdfPage=n-1}}})
            icon("next"){if(pdf.pdfPage<pdf.pageCount-1)board.edit{it.pdfPage++}}
        }
        if(items.any{it.kind in listOf("ink","text","sticky")}){
            icon("text",tr("Convert to text · detect language","تبدیل به متن · تشخیص زبان")){board.smartMode="text";processSmart(board.chosen())}
            icon("formula",tr("Calculate / solve","محاسبه / حل")){board.smartMode="formula";processSmart(board.chosen())}
            selectionBar.addView(this.icon("convert",tr("Convert units","تبدیل واحد")){board.smartMode="convert";processSmart(board.chosen())})
            icon("graph",tr("Plot function","رسم تابع")){board.smartMode="graph";processSmart(board.chosen())}
        }
        if(items.singleOrNull()?.shape=="mindnode"){
            val node=items.single()
            icon("insert",tr("Add child","افزودن فرزند")){mindNode(node)}
            icon("insert",tr("Add sibling","افزودن هم‌سطح")){mindNode(node,true)}
            icon("text",tr("Add text","افزودن متن")){textEditor(node)}
        }
        if(items.any{it.kind=="ink"})icon("shape",tr("Recognize shape","تشخیص شکل")){board.smartMode="shape";processSmart(board.chosen())}
        icon("color"){colors()}
        icon("copy"){clipboard=MindMap.group(store.page,board.chosen()).map{it.deepCopy()};toast(s("copy"))}
        icon("duplicate"){
            val copies=duplicateItems(MindMap.group(store.page,board.chosen())).onEach{it.x+=24;it.y+=24;it.locked=false}
            store.editMetadata{store.page.items.addAll(copies)};board.selected.clear();board.selected.addAll(copies.map{it.id});refreshDock()
        }
        icon("delete"){board.delete()}
        icon("more",s("edit")){editSelection()}
    }
    private fun splitSettings(){
        if(board!==whiteboard){pdfMenu();return}
        MaterialAlertDialogBuilder(this).setTitle(tr("Split board","تقسیم تخته"))
            .setSingleChoiceItems(arrayOf(tr("One canvas","یک بخش"),tr("Two panels","دو بخش"),tr("Three panels","سه بخش"),tr("Four panels","چهار بخش")),store.page.panes.size-1){d,index->board.split(index+1);d.dismiss();refreshSplitControls()}.show()
    }
    private fun refreshSplitControls(){
        splitControls?.let{canvasHost.removeView(it)};splitControls=null;splitControlsPage=store.page
        val n=store.page.panes.size;if(n==1||focusMode||canvasHost.width==0)return
        val overlay=FrameLayout(this);splitControls=overlay
        canvasHost.addView(overlay,0.coerceAtLeast(canvasHost.childCount-1),FrameLayout.LayoutParams(-1,-1))
        val columns=if(n==4)2 else n;val rows=if(n==4)2 else 1
        store.page.panes.forEachIndexed{i,pane->
            val actions=row().apply{pad(2)};surface(actions)
            actions.addView(icon("pen",tr("Panel ${i+1}: pen","بخش ${i+1}: قلم")){
                val c=column().apply{pad(12)};palette(c,pane.color){color->store.editMetadata{pane.color=color}}
                slider(c,tr("Thickness","ضخامت"),pane.penWidth,undoable=true){width->pane.penWidth=width}
                val styles=row();c.addView(scrollRow(styles))
                listOf("round" to tr("Ink","جوهری"),"marker" to tr("Marker","ماژیک"),"dashed" to tr("Dashed","خط‌چین"),"highlight" to tr("Highlighter","هایلایتر")).forEach{(key,title)->
                    styles.addView(button(title){store.editMetadata{pane.penStyle=key};toast(title)})
                }
                slider(c,tr("Dash length","طول خط‌چین"),pane.dashLength,80,undoable=true){v->pane.dashLength=v}
                slider(c,tr("Dash gap","فاصلهٔ خط‌چین"),pane.dashGap,80,undoable=true){v->pane.dashGap=v}
                dialog(tr("Panel ${i+1}: pen","بخش ${i+1}: قلم"),ScrollView(this).apply{addView(c)})
            })
            actions.addView(icon("background",tr("Panel ${i+1}: background","بخش ${i+1}: پس‌زمینه")){
                backgroundSettings(i)
            })
            overlay.addView(actions,FrameLayout.LayoutParams(dp(100),dp(52)).apply{
                leftMargin=((i%columns+1)*canvasHost.width/columns-dp(112)).coerceAtLeast(dp(76))
                topMargin=(i/columns)*canvasHost.height/rows+dp(if(i/columns==0)142 else 12)
            })
        }
    }
    private fun refreshPages() {
        if(!::pageStrip.isInitialized)return
        val signature="${projectStore.lesson.current}:${projectStore.lesson.pages.joinToString{it.id}}:${projectStore.page.panes.size}"
        if(signature==pageSignature)return
        pageSignature=signature;pageStrip.removeAllViews()
        pageStrip.addView(icon("previous",s("previous")){switchPage(projectStore.lesson.current-1)})
        pageStrip.addView(icon("pages",s("pages")+" · ${projectStore.lesson.current+1}/${projectStore.lesson.pages.size}"){pages()})
        pageStrip.addView(icon("next",s("next")){switchPage(projectStore.lesson.current+1)})
        pageStrip.addView(icon("insert",s("add_page")){addPage()})
        pageStrip.addView(icon("fit",s("fit")){if(board===whiteboard)board.fit()else pdfPane?.fitWidth()})
        refreshSplitControls()
    }
    private fun switchPage(index:Int) {
        if(index !in projectStore.lesson.pages.indices || board.isDrawing)return
        activateBoard(whiteboard);closeSidePanel();projectStore.lesson.current=index;whiteboard.clearSelection();whiteboard.reset();projectStore.changed()
    }
    private fun addPage() {
        if(projectStore.lesson.pages.size>=200 || board.isDrawing)return
        activateBoard(whiteboard);projectStore.editMetadata{projectStore.lesson.pages.add(projectStore.lesson.current+1,Page());projectStore.lesson.current++}
        whiteboard.clearSelection();whiteboard.reset()
    }
    private fun savePens() {prefs.edit().putFloat("dashLength",board.dashLength).putFloat("dashGap",board.dashGap).putString("thickStyle",board.profile.thickStyle).putFloat("thickWidth",board.profile.thickWidth).putInt("thickColor",board.profile.thickColor).putInt("penColor",board.penColor).putInt("highlightColor",board.highlightColor).putFloat("penWidth",board.penWidth).putFloat("highlightWidth",board.highlightWidth).putString("penStyle",board.penStyle).apply()}
    private val backgroundSwatches get()=listOf(Color.WHITE,Color.BLACK,0xff172a36.toInt(),PAPER,0xfffff5dc.toInt(),0xffeaf7f3.toInt(),0xffe9f0ff.toInt(),0xfff7ecfc.toInt(),0xfff4e8df.toInt(),0xffdce9ef.toInt())
    private fun palette(c:LinearLayout,initial:Int,background:Boolean=false,changed:(Int)->Unit){
        val colors=if(background)backgroundSwatches else listOf(NAVY,Color.BLACK,Color.WHITE,0xffe45756.toInt(),ORANGE,0xffffcf40.toInt(),TEAL,0xff268bd2.toInt(),0xff865ac7.toInt(),0xffec76ab.toInt())
        val buttons=mutableListOf<Button>()
        var current=initial
        colors.chunked(5).forEach{chunk->c.addView(row().apply{chunk.forEach{color->
            val b=button(if(color==initial)"✓" else "●"){}.apply{setTextColor(if(Color.red(color)*.299+Color.green(color)*.587+Color.blue(color)*.114>160)NAVY else Color.WHITE);backgroundTintList=android.content.res.ColorStateList.valueOf(color);strokeColor=android.content.res.ColorStateList.valueOf(OUTLINE_STRONG);strokeWidth=dp(1);setPadding(0,0,0,0);minWidth=0;minimumWidth=0;textSize=22f;contentDescription=String.format(Locale.US,"#%06X",color and 0xffffff)}
            b.setOnClickListener{buttons.forEach{it.text="●"};b.text="✓";current=color;changed(color)};buttons.add(b)
            addView(b,LinearLayout.LayoutParams(0,dp(48),1f).apply{setMargins(dp(4),dp(4),dp(4),dp(4))})
        }})}
        val custom=button(tr("Custom color…","رنگ دلخواه…")){
            ColorPickerDialog.show(this,current){color->current=color;buttons.forEach{it.text=if(it.contentDescription==String.format(Locale.US,"#%06X",color and 0xffffff))"✓" else "●"};changed(color)}
        }
        c.addView(custom)
    }

    private fun backgroundSettings(paneIndex:Int=board.activePane){
        val page=store.page;val pane=page.panes[paneIndex.coerceIn(page.panes.indices)]
        val c=column().apply{pad(16)}
        c.addView(label(tr("Page pattern","الگوی صفحه"),17f,NAVY,true))
        c.addView(label(tr("The pattern applies to this page; color applies to the selected panel.","الگو برای این صفحه و رنگ برای بخش انتخابی اعمال می‌شود."),13f,MUTED))
        val patterns=listOf("plain" to tr("Plain","ساده"),"dots" to s("dots"),"grid" to s("grid"),"ruled" to s("ruled"),"hatch" to tr("Hatched","هاشور"))
        val patternRows=column();c.addView(patternRows)
        fun refreshPattern(){patternRows.removeAllViews();patterns.chunked(3).forEach{chunk->
            patternRows.addView(row().apply{chunk.forEach{(key,title)->
                addView(button(title,if(page.background in listOf("white","dark"))key=="plain" else page.background==key){
                    store.editMetadata{
                        if(page.background=="dark")page.panes.forEach{if(it.background==Color.WHITE)it.background=0xff172a36.toInt()}
                        page.background=key
                    }
                    refreshPattern()
                },LinearLayout.LayoutParams(0,dp(52),1f).apply{setMargins(dp(2),dp(4),dp(2),dp(4))})
            }})
        }}
        refreshPattern()
        c.addView(label(tr("Background color","رنگ پس‌زمینه"),17f,NAVY,true))
        val initial=if(page.background=="dark"&&pane.background==Color.WHITE)0xff172a36.toInt() else pane.background
        palette(c,initial,background=true){color->store.editMetadata{
            if(page.background=="dark"){
                page.panes.forEach{if(it.background==Color.WHITE)it.background=0xff172a36.toInt()}
                page.background="plain"
            }
            pane.background=color
        }}
        dialog(tr("Background · page ${store.lesson.current+1}","پس‌زمینه · صفحهٔ ${store.lesson.current+1}"),ScrollView(this).apply{addView(c)})
    }
    private fun slider(c:LinearLayout,name:String,value:Float,maxValue:Int=40,undoable:Boolean=false,changed:(Float)->Unit){
        val title=label("$name · ${value.toInt()}",15f,NAVY,true);title.setPadding(dp(4),dp(18),dp(4),dp(8));c.addView(title)
        c.addView(SeekBar(this).apply{max=maxValue-1;progress=value.toInt()-1;contentDescription=name;setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(v:SeekBar?,n:Int,user:Boolean){title.text="$name · ${n+1}";if(user){changed(n+1f);if(undoable)board.sceneChanged()}}
            override fun onStartTrackingTouch(v:SeekBar?){if(undoable)store.checkpoint()};override fun onStopTrackingTouch(v:SeekBar?){if(undoable)store.changed()}
        })})
    }
    private fun penSettings(){
        val c=column().apply{pad(20)};val body=column();var broad=false
        fun tipStyle()=if(broad)board.profile.thickStyle else board.penStyle
        val preview=object:View(this){override fun onDraw(canvas:Canvas){
            val style=tipStyle();val color=if(broad)board.profile.thickColor else board.penColor
            val width=if(broad)board.profile.thickWidth else board.penWidth
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{
                this.color=color;strokeWidth=width*resources.displayMetrics.density*(if(style=="highlight")4f else if(style=="marker")1.8f else 1f)
                alpha=if(style=="highlight")75 else 255;this.style=Paint.Style.STROKE
                strokeCap=if(style in listOf("marker","highlight"))Paint.Cap.SQUARE else Paint.Cap.ROUND
                if(style=="dashed")pathEffect=DashPathEffect(floatArrayOf(board.dashLength*resources.displayMetrics.density,board.dashGap*resources.displayMetrics.density),0f)
            }
            val path=Path();path.moveTo(30f,height*.7f);path.cubicTo(this.width*.3f,0f,this.width*.55f,height.toFloat(),this.width-30f,height*.3f);canvas.drawPath(path,paint)
        }}.apply{background=rounded(PAPER,dp(16).toFloat())}
        c.addView(label(tr("Pen studio","تنظیم قلم"),24f,NAVY,true))
        c.addView(label(tr("Set color, thickness and style independently for each tip.","رنگ، ضخامت و نوع هر سر قلم را جداگانه تنظیم کنید."),14f,MUTED))
        c.addView(preview,LinearLayout.LayoutParams(-1,dp(90)))
        fun refresh(){
            body.removeAllViews()
            body.addView(label(if(broad)tr("Broad tip","سر پهن")else tr("Fine tip","سر باریک"),18f,TEAL,true))
            body.addView(label(tr("Color","رنگ"),16f,NAVY,true))
            palette(body,if(broad)board.profile.thickColor else board.penColor){if(broad)board.profile.thickColor=it else board.penColor=it;savePens();preview.invalidate()}
            slider(body,tr("Thickness","ضخامت"),if(broad)board.profile.thickWidth else board.penWidth){if(broad)board.profile.thickWidth=it else board.penWidth=it;savePens();preview.invalidate()}
            body.addView(label(tr("Pen style","نوع قلم"),16f,NAVY,true))
            val styles=row();body.addView(scrollRow(styles))
            listOf("round" to tr("Ink","جوهری"),"marker" to tr("Marker","ماژیک"),"dashed" to tr("Dashed","خط‌چین"),"highlight" to tr("Highlighter","هایلایتر")).forEach{(key,title)->
                styles.addView(button(title,tipStyle()==key){if(broad)board.profile.thickStyle=key else board.penStyle=key;savePens();refresh();preview.invalidate()})
            }
            if(tipStyle()=="dashed"){
                slider(body,tr("Dash length","طول خط‌چین"),board.dashLength,80){board.dashLength=it;savePens();preview.invalidate()}
                slider(body,tr("Dash gap","فاصلهٔ خط‌چین"),board.dashGap,80){board.dashGap=it;savePens();preview.invalidate()}
            }
            body.addView(label(tr("Ink: round, opaque. Marker: square, opaque. Highlighter: wide and translucent. Dashed: a broken line.","جوهری: گرد و پررنگ. ماژیک: تخت و پررنگ. هایلایتر: پهن و نیمه‌شفاف. خط‌چین: خط منقطع."),14f,MUTED))
        }
        c.addView(row().apply{
            addView(button(tr("Fine tip","سر باریک")){broad=false;refresh();preview.invalidate()},LinearLayout.LayoutParams(0,dp(52),1f))
            addView(button(tr("Broad tip","سر پهن")){broad=true;refresh();preview.invalidate()},LinearLayout.LayoutParams(0,dp(52),1f))
        })
        c.addView(body);refresh();dialog(s("pen"),ScrollView(this).apply{addView(c)})
    }

    private fun layers(){
        if(sidePanel!=null){closeSidePanel();return}
        val content=column();val pageId=store.page.id
        fun refresh(){
            if(store.page.id!=pageId){closeSidePanel();return}
            content.removeAllViews()
            content.addView(label(tr("Top layer is in front. Drag the handle to reorder.","لایهٔ بالایی در جلو است. برای جابه‌جایی، دستگیره را بکشید."),13f,MUTED))
            content.addView(button(tr("Add layer","افزودن لایه")){if(store.page.layers.size<100){store.editMetadata{val l=Layer(name=tr("Layer","لایه")+" ${store.page.layers.size+1}");store.page.layers.add(l);store.page.activeLayerId=l.id};board.clearSelection();refresh()}})
            content.addView(button(tr("Unlock all on this page","بازکردن همهٔ قفل‌ها در این صفحه")){
                val count=board.unlockAll()
                toast(if(count>0)tr("Objects and layers unlocked on this page","قفل آبجکت‌ها و لایه‌های این صفحه باز شد")else tr("No locked objects or layers","آبجکت یا لایهٔ قفل‌شده‌ای نیست"));refresh()
            })
            store.page.layers.asReversed().forEach { layer ->
                val isPdfBaseLayer=board.pdfBaseId!=null && store.page.items.any{it.id==board.pdfBaseId && it.layerId==layer.id}
                val card=column().apply{pad(6);background=rounded(if(layer.id==store.page.activeLayerId)PRIMARY_CONTAINER else SURFACE,dp(10).toFloat(),OUTLINE)}
                content.addView(card,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(4),0,dp(4))})
                val head=row();card.addView(head)
                head.addView(label(layer.name,15f,NAVY,true).apply{
                    maxLines=2;setOnClickListener{store.editMetadata{store.page.activeLayerId=layer.id};board.clearSelection();refresh()}
                    setOnLongClickListener{input(tr("Layer name","نام لایه"),layer.name){v->require(v.isNotBlank()&&v.length<=200);store.editMetadata{layer.name=v};refresh()};true}
                },LinearLayout.LayoutParams(0,dp(52),1f))
                val handle=icon("drag",tr("Hold and drag to reorder","نگه دارید و برای جابه‌جایی بکشید")){}
                handle.setOnLongClickListener{it.startDragAndDrop(ClipData.newPlainText("layer",layer.id),View.DragShadowBuilder(it),layer.id,0)}
                head.addView(handle)
                card.setOnDragListener{v,event->
                    when(event.action){
                        DragEvent.ACTION_DRAG_STARTED->event.localState is String && store.page.layers.any{it.id==event.localState}
                        DragEvent.ACTION_DRAG_ENTERED->{v.alpha=.65f;true}
                        DragEvent.ACTION_DRAG_EXITED,DragEvent.ACTION_DRAG_ENDED->{v.alpha=1f;true}
                        DragEvent.ACTION_DROP->{v.alpha=1f;val from=store.page.layers.indexOfFirst{it.id==event.localState};val to=store.page.layers.indexOf(layer)
                            if(from>=0&&to>=0&&from!=to){store.editMetadata{store.page.layers.add(to,store.page.layers.removeAt(from))};refresh()};true}
                        else->true
                    }
                }
                val actions=row();card.addView(scrollRow(actions))
                actions.addView(icon(if(layer.visible)"visible"else"hidden",tr("Show / hide","نمایان / پنهان"),!layer.visible){store.editMetadata{layer.visible=!layer.visible};board.clearSelection();refresh()})
                actions.addView(icon(if(layer.locked)"lock"else"unlock",tr("Lock / unlock","قفل / بازکردن"),layer.locked){store.editMetadata{layer.locked=!layer.locked};board.clearSelection();refresh()})
                actions.addView(icon("up",tr("Move up","بالاتر")){val i=store.page.layers.indexOf(layer);if(i<store.page.layers.lastIndex){store.editMetadata{java.util.Collections.swap(store.page.layers,i,i+1)};refresh()}})
                actions.addView(icon("down",tr("Move down","پایین‌تر")){val i=store.page.layers.indexOf(layer);if(i>0){store.editMetadata{java.util.Collections.swap(store.page.layers,i,i-1)};refresh()}})
                actions.addView(icon("delete",tr("Delete layer","حذف لایه")){
                    if(store.page.layers.size>1 && !isPdfBaseLayer)confirm(tr("Delete layer and its contents?","لایه و محتوایش حذف شود؟")){
                        store.editMetadata{store.page.items.removeAll{it.layerId==layer.id};store.page.layers.remove(layer);if(store.page.activeLayerId==layer.id)store.page.activeLayerId=store.page.layers.last().id};board.clearSelection();refresh()
                    }
                }.apply{isEnabled=store.page.layers.size>1 && !isPdfBaseLayer;alpha=if(isEnabled)1f else .3f})
                val opacity=label(tr("Opacity","میزان کدری")+" ${(layer.opacity*100).toInt()}%",13f,MUTED);card.addView(opacity)
                card.addView(SeekBar(this).apply{max=100;progress=(layer.opacity*100).toInt();contentDescription=tr("Layer opacity","میزان کدری لایه");setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                    override fun onStartTrackingTouch(v:SeekBar?){store.checkpoint()}
                    override fun onProgressChanged(v:SeekBar?,n:Int,user:Boolean){if(user){layer.opacity=n/100f;opacity.text=tr("Opacity","میزان کدری")+" $n%";board.sceneChanged()}}
                    override fun onStopTrackingTouch(v:SeekBar?){store.changed();board.clearSelection()}
                })})
                if(board.chosen().isNotEmpty()&&!layer.locked&&layer.visible)card.addView(button(tr("Move selection here","انتقال انتخاب به این لایه")){val chosen=board.chosen();store.editMetadata{chosen.forEach{it.layerId=layer.id}};board.clearSelection();refresh()})
            }
        }
        refresh();showSidePanel(tr("Layers","لایه‌ها"),content)
    }
    private fun calibrateTips(){
        val c=column().apply{pad(20)}
        c.addView(label(tr("Contact width comes from the touch controller. Calibrate in raw pixels, not screen centimetres. Unknown/zero contact sizes use the fine tip.","اندازهٔ تماس را کنترلر لمس گزارش می‌کند. آستانه‌ها با پیکسل خام تنظیم می‌شوند، نه سانتی‌متر صفحه. اندازهٔ صفر یا نامشخص، سر باریک محسوب می‌شود."),14f,MUTED))
        c.addView(label(tr("Fine tip maximum contact width (px)","بیشترین عرض تماس سر باریک (پیکسل)"),14f));val thin=field(board.profile.thin.toString());c.addView(thin)
        c.addView(label(tr("Palm rejection starts at (px)","شروع تشخیص کف دست (پیکسل)"),14f));val palm=field(board.profile.palm.toString());c.addView(palm)
        val palmErase=CheckBox(this).apply{text=s("palm_erase");isChecked=board.profile.palmErase};c.addView(palmErase)
        c.addView(label(tr("Between these thresholds: broad-tip color and thickness. Set each tip's appearance in Pen settings.","بین این دو آستانه، رنگ و ضخامت سر پهن اعمال می‌شود. ظاهر هر دو سر را از تنظیمات قلم انتخاب کنید."),14f))
        c.addView(button(s("touch_test")){dialog(s("touch_test"),TouchDiagnostics(this,board.profile).apply{minimumHeight=dp(320)})})
        val d=dialog(tr("Dual-tip calibration","کالیبراسیون دو سر قلم"),ScrollView(this).apply{addView(c)})
        c.addView(button(s("apply"),true){val a=thin.text.toString().toFloatOrNull();val b=palm.text.toString().toFloatOrNull();if(a==null||b==null||!a.isFinite()||!b.isFinite()||a<=0||b<=a){thin.error=tr("Use 0 < fine < palm","باید ۰ < سر باریک < کف دست باشد");return@button};board.profile.calibrated=true;board.profile.thin=a;board.profile.palm=b;board.profile.palmErase=palmErase.isChecked;prefs.edit().putBoolean("calibrated",true).putFloat("thin",a).putFloat("palm",b).putBoolean("palmErase",palmErase.isChecked).apply();savePens();d.dismiss()})
    }
    private fun palmControls(c:LinearLayout){
        c.addView(label(tr("Palm erasing","پاک‌کردن با کف دست"),18f,NAVY,true))
        c.addView(Switch(this).apply{
            text=tr("Use palm as a temporary eraser","کف دست، پاک‌کن موقت باشد")
            isChecked=board.profile.palmErase
            setOnCheckedChangeListener{_,enabled->
                board.profile.palmErase=enabled
                prefs.edit().putBoolean("palmErase",enabled).apply()
            }
        })
        c.addView(label(tr("Requires a touch controller that reports contact width. If the palm is ignored or mistaken for a pen, calibrate its threshold. Eraser size and mode below also apply to the palm.","نیازمند گزارش اندازهٔ تماس توسط کنترلر لمس است. اگر کف دست تشخیص داده نشد یا مثل قلم عمل کرد، آستانه را تنظیم کنید. اندازه و حالت پاک‌کن برای کف دست هم اعمال می‌شود."),14f,MUTED))
        c.addView(button(tr("Calibrate palm / test touch","تنظیم کف دست / تست لمس")){calibrateTips()})
    }
    private fun eraserSettings(){
        val c=column().apply{pad(20)};palmControls(c);c.addView(label(tr("Stroke/object: remove an entire touched item. Area: remove only the region under the eraser, including text, shapes, images and PDFs. Undo restores it.","خط/شیء: تمام مورد لمس‌شده پاک می‌شود. ناحیه‌ای: فقط مسیر پاک‌کن روی خط، متن، شکل، تصویر یا PDF پاک می‌شود. با Undo قابل برگشت است."),14f,MUTED))
        val modes=RadioGroup(this);listOf("stroke" to tr("Whole stroke / object","کل خط / شیء"),"area" to tr("Area eraser","پاک‌کن ناحیه‌ای")).forEach{(key,title)->modes.addView(RadioButton(this).apply{text=title;isChecked=board.eraserMode==key;setOnClickListener{board.eraserMode=key;prefs.edit().putString("eraserMode",key).apply()}})};c.addView(modes)
        c.addView(CheckBox(this).apply{text=tr("On PDF: also cover original content","روی PDF: محتوای اصلی هم پوشانده شود");isChecked=board.erasePdfContent;setOnCheckedChangeListener{_,v->board.erasePdfContent=v;prefs.edit().putBoolean("erasePdfContent",v).apply()}})
        c.addView(CheckBox(this).apply{text=tr("Allow palm erasing inside PDF","پاک‌کردن با کف دست در بخش PDF");isChecked=board.pdfPalmErase;setOnCheckedChangeListener{_,v->board.pdfPalmErase=v;prefs.edit().putBoolean("pdfPalmErase",v).apply()}})
        c.addView(label(tr("Original PDF content is covered with white and can be restored with Undo. This is not permanent redaction. With two tips off, broad touch scrolls the PDF.","محتوای اصلی PDF با رنگ سفید پوشانده می‌شود و با بازگردانی برمی‌گردد؛ حذف محرمانه نیست. با دو سر قلم خاموش، تماس پهن PDF را اسکرول می‌کند."),14f,MUTED))
        slider(c,tr("Radius","شعاع"),board.eraserRadius,80){board.eraserRadius=it;prefs.edit().putFloat("eraserRadius",it).apply()}
        c.addView(CheckBox(this).apply{text=tr("Include objects (otherwise ink only)","روی اشیاء هم اعمال شود (وگرنه فقط دست‌نویس)");isChecked=board.eraseObjects;setOnCheckedChangeListener{_,v->board.eraseObjects=v;prefs.edit().putBoolean("eraseObjects",v).apply()}})
        dialog(s("erase"),ScrollView(this).apply{addView(c)})
    }
    private fun addText(sticky:Boolean){textEditor(null,sticky)}
    private fun textEditor(existing:Item?,sticky:Boolean=existing?.kind=="sticky"){
        if(existing?.locked==true)return
        val item=existing?.deepCopy()?:Item(kind=if(sticky)"sticky"else"text",width=if(sticky)24f else 32f,color=board.penColor)
        Fonts.stamp(item)
        val c=column().apply{pad(20)};val value=field(item.text,tr("Write your text…","متن را بنویسید…"));value.minLines=2;c.addView(value)
        Fonts.bind(value,item.bold,item.italic,Fonts.fa(item),Fonts.en(item))
        c.addView(CheckBox(this).apply{text=tr("Bold","پررنگ");isChecked=item.bold;setOnCheckedChangeListener{_,v->item.bold=v;Fonts.bind(value,item.bold,item.italic,Fonts.fa(item),Fonts.en(item))}})
        c.addView(CheckBox(this).apply{text=tr("Italic","مورب");isChecked=item.italic;setOnCheckedChangeListener{_,v->item.italic=v;Fonts.bind(value,item.bold,item.italic,Fonts.fa(item),Fonts.en(item))}})
        c.addView(label(tr("Alignment","تراز متن"),16f,NAVY,true));val align=Spinner(this);align.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf(tr("Start","ابتدای سطر"),tr("Center","وسط"),tr("End","انتهای سطر")));align.setSelection(listOf("start","center","end").indexOf(item.textAlign).coerceAtLeast(0));c.addView(align)
        c.addView(label(tr("Text color","رنگ متن"),16f,NAVY,true));palette(c,item.color){item.color=it;value.setTextColor(it)}
        slider(c,tr("Text size","اندازهٔ متن"),item.width,120){item.width=it}
        if(sticky){c.addView(label(tr("Note background","رنگ یادداشت"),16f,NAVY,true));palette(c,item.noteColor){item.noteColor=it;value.background=rounded(it,dp(8).toFloat())}}
        val d=dialog(s(if(sticky)"sticky"else"text"),ScrollView(this).apply{addView(c)})
        c.addView(button(s("apply"),true){val entered=value.text?.toString().orEmpty();if(entered.isBlank()){value.error=s("empty");return@button};item.text=entered;item.textAlign=listOf("start","center","end")[align.selectedItemPosition];val oldWidth=item.w;val oldHeight=item.h;TextLayout.fit(item)
            if(item.shape=="mindnode"){item.w=maxOf(oldWidth,item.w);item.h=maxOf(oldHeight,item.h)}
            if(existing==null)board.insert(item)else store.editMetadata{val index=store.page.items.indexOfFirst{it.id==existing.id};if(index>=0)store.page.items[index]=item};d.dismiss()})
    }
    private fun graph(initial:String="y=2x-5",existing:Item?=null){
        val c=column().apply{pad(20)};c.addView(label(tr("Enter y=f(x). Use x^2, sin(x), sqrt(x), abs(x); angles are radians. Separate up to 3 curves with ;. Both axes use the same scale.","تابع را به صورت y=f(x) وارد کنید. x^2، sin(x)، sqrt(x) و abs(x) مجازند؛ زاویه‌ها رادیانی‌اند. حداکثر ۳ تابع را با ; جدا کنید. مقیاس دو محور برابر است."),14f,MUTED))
        val field=field(existing?.text?:initial).apply{layoutDirection=View.LAYOUT_DIRECTION_LTR;textDirection=View.TEXT_DIRECTION_LTR};c.addView(field)
        val examples=row();listOf("y=2x-5","y=x^2","sin(x);cos(x)","sqrt(x)").forEach{v->examples.addView(button(v){field.setText(v)})};c.addView(scrollRow(examples))
        c.addView(label(tr("Half-range on the shorter axis","نیم‌بازهٔ محور کوتاه‌تر"),14f));val domain=field((existing?.domain?:10f).toString());c.addView(domain)
        val d=dialog(s("graph"),ScrollView(this).apply{addView(c)})
        c.addView(button(tr("Plot","رسم نمودار"),true){try{val text=field.text.toString();require(text.split(';').size in 1..3);text.split(';').forEach{MathTools().compile(it)};val span=domain.text.toString().toFloat();require(span in .1f..1000f);if(existing==null)board.insert(Item(kind="graph",text=text,w=560f,h=400f,domain=span))else store.editMetadata{existing.text=text;existing.domain=span};d.dismiss()}catch(e:Exception){field.error=e.message?:s("error")}})
    }

    private fun shapes() {
        val c=column().apply{pad(12)};val d=dialog(s("shape"),ScrollView(this).apply{addView(c)})
        c.addView(button(tr("Automatic shape recognition","تشخیص خودکار شکل")){board.tool="smart";board.smartMode="shape";board.clearSelection();refreshDock();d.dismiss()})
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
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(content)
            .setNegativeButton(s("close"), null)
            .create()
            .also { it.show();Fonts.onShown(it) }

    private fun choices(title: String, keys: List<String>, action: (Int) -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setItems(keys.map { s(it) }.toTypedArray()) { _, i -> action(i) }
            .setNegativeButton(s("cancel"), null)
            .show().also{Fonts.onShown(it)}
    }

    private fun confirm(title: String, action: () -> Unit) {
        MaterialAlertDialogBuilder(this)
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
            MaterialAlertDialogBuilder(this)
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
            MaterialAlertDialogBuilder(this)
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
            MaterialAlertDialogBuilder(this)
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
        val doc = projectStore.lesson.copyForSave()
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
                        toast(s("save_error"))
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
                files.read(f.inputStream(),true) to false
            } catch (e: Exception) {
                val backup = File(f.path + ".bak")
                if (!backup.exists()) throw e
                files.read(backup.inputStream(),true) to true
            }
        }) { (doc, backup) ->
            if (changeId || backup) {
                documentId = if (backup) newId() else f.nameWithoutExtension
                prefs.edit().putString("current", documentId).apply()
            }
            projectStore.replace(doc)
            whiteboard.clearSelection()
            whiteboard.reset()
            if (backup) {status.text = s("restored_backup");toast(s("restored_backup"))}
        }
    }

    private fun rename() {
        input(s("lesson"), projectStore.lesson.title) { value ->
            require(value.isNotBlank())
            projectStore.editMetadata { projectStore.lesson.title = value.take(100) }
        }
    }

    private fun menu() {
        choices(projectStore.lesson.title.ifBlank{"VuraVision"}+" · "+status.text, listOf("lab", "games", "settings", "help")) {
            when (it) {
                0 -> openExplorer(true)
                1 -> openExplorer(false)
                2 -> settings()
                3 -> quickGuide()
            }
        }
    }

    private fun quickGuide(){
        UserGuide.show(this,media){prefs.edit().putBoolean("engineering",true).apply();engineering()}
    }

    private fun fileMenu() {
        if(projectStore.lesson.pdf!=null){
            MaterialAlertDialogBuilder(this).setTitle(s("files")).setItems(arrayOf(tr("PDF file and workspace","فایل PDF و فضای مطالعه"),tr("Board projects and exports","پروژه‌ها و خروجی تخته"))){_,i->if(i==0)pdfMenu()else projectFileMenu()}.show()
        } else projectFileMenu()
    }
    private fun projectFileMenu() {
        activateBoard(whiteboard)
        choices(
            s("files"),
            listOf("new", "open", "save", "recent", "export_pdf", "export_png", "export_jpg", "rename"),
        ) {
            when (it) {
                0 ->
                    withPdfExit{confirm(s("new_confirm")) {
                        persist()
                        documentId = newId()
                        prefs.edit().putString("current", documentId).apply()
                        projectStore.replace(Lesson(title = s("lesson")))
                        whiteboard.clearSelection()
                        whiteboard.reset()
                    }
                    }
                1 -> withPdfExit{pick("application/octet-stream", 101)}
                2 -> export("vura", true) { createDocument(it, "application/octet-stream") }
                3 -> withPdfExit{recent()}
                4 -> exportChoice("pdf")
                5 -> exportChoice("png")
                6 -> exportChoice("jpg")
                7 -> rename()
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
            MaterialAlertDialogBuilder(this)
                .setTitle(s("recent"))
                .setItems(names.toTypedArray()) { _, i -> loadFile(list[i]) }
                .setNegativeButton(s("cancel"), null)
                .show()
        }
    }

    private fun colors() {
        val chosen=board.chosen()
        val initial=chosen.singleOrNull()?.let{if(it.kind=="sticky")it.noteColor else it.color}?:board.inkColor
        val c=column().apply{pad(16)}
        palette(c,initial){color->
            if(chosen.isEmpty()){board.inkColor=color;savePens()}
            else board.edit{item->
                if(item.kind=="sticky"){
                    item.noteColor=color
                    if(Color.luminance(color)<.25 && Color.luminance(item.color)<.35)item.color=Color.WHITE
                    else if(Color.luminance(color)>.65 && Color.luminance(item.color)>.65)item.color=NAVY
                }else item.color=color
            }
        }
        dialog(tr("Color","رنگ"),ScrollView(this).apply{addView(c)})
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
            0->addText(true);1->graph();2->pick("image/*",102);3->choosePdfImport();4->math()
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
        val keys = mutableListOf("delete", "duplicate", "copy", "lock", "front", "back", "color", "smart")
        if (one?.kind in listOf("text", "sticky", "graph")) keys.add("text")
        if(one?.shape=="mindnode")keys.add("add_branch")
        if(one?.shape in GeometryTools.keys)keys.add("draw_with_tool")
        if(one!=null && one.cuts.isNotEmpty())keys.add("restore_erased")
        if (one?.kind in listOf("text", "sticky")) keys.add("text_size")
        if (one?.kind == "graph") keys.add("domain")
        if (one?.kind == "pdf") keys.addAll(listOf("previous", "next", "page_picker"))
        if (one?.kind in listOf("image", "pdf")) keys.add("crop")
        keys.add("paste")
        choices(s("edit"), keys) { i ->
            when (keys[i]) {
                "smart" -> smartSettings()
                "add_branch" -> mindNode(one)
                "draw_with_tool" -> {if(one!=null&&!one.locked&&store.page.editable(one)){val result=GeometryTools.construction(one);store.editMetadata{store.page.items.add(result)}}}
                "delete" -> board.delete()
                "copy" -> {
                    clipboard = MindMap.group(store.page,items).map { it.deepCopy() }
                    toast(s("copy"))
                }
                "duplicate" -> {
                    store.editMetadata {
                        duplicateItems(MindMap.group(store.page,items))
                            .onEach {
                                it.apply {
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
                    store.editMetadata { items.forEach { it.locked = !it.locked } }
                }
                "front" -> {
                    store.editMetadata {
                        store.page.items.removeAll(items.toSet())
                        store.page.items.addAll(items)
                    }
                }
                "back" -> {
                    store.editMetadata {
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
        if(!store.page.canDraw())return
        if (clipboard.isEmpty()) return
        val c = board.center()
        val bounds = contentBounds(clipboard)
        store.editMetadata {
            val pasted =
                duplicateItems(clipboard).onEach {
                    it.apply {
                        layerId = store.page.activeLayerId
                        pane = board.activePane
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
        actions.addView(button(s("background")){backgroundSettings()})
        actions.addView(button(s("clear")){confirm(s("clear_confirm")){projectStore.editMetadata{projectStore.page.items.removeAll{!it.locked && projectStore.page.editable(it)}};whiteboard.clearSelection();d.dismiss()}})
        c.addView(scrollRow(actions))
        projectStore.lesson.pages.forEachIndexed{i,page->
            val r=row().apply{pad(6);background=rounded(if(i==projectStore.lesson.current)0xffeeebff.toInt()else PAPER,dp(12).toFloat())}
            r.addView(object:View(this){override fun onDraw(canvas:Canvas){board.renderer.page(canvas,page,width,height,false)}}.apply{setOnClickListener{switchPage(i);d.dismiss()}},LinearLayout.LayoutParams(dp(108),dp(64)))
            r.addView(button("${s("page_short")} ${i+1}",i==projectStore.lesson.current){switchPage(i);d.dismiss()},LinearLayout.LayoutParams(0,dp(50),1f))
            r.addView(button("⋯"){choices(s("pages"),listOf("duplicate","move_left","move_right","delete")){action->
                fun perform(){projectStore.editMetadata{when(action){
                    0->if(projectStore.lesson.pages.size<200){projectStore.lesson.pages.add(i+1,page.copy(id=newId(),layers=page.layers.map{it.copy()}.toMutableList(),panes=page.panes.map{it.copy()}.toMutableList(),items=duplicateItems(page.items).toMutableList()));projectStore.lesson.current=i+1}
                    1,2->{val target=i+if(action==1)-1 else 1;if(target in projectStore.lesson.pages.indices){java.util.Collections.swap(projectStore.lesson.pages,i,target);projectStore.lesson.current=target}}
                    3->if(projectStore.lesson.pages.size>1){projectStore.lesson.pages.removeAt(i);projectStore.lesson.current=projectStore.lesson.current.coerceAtMost(projectStore.lesson.pages.lastIndex)}
                }};whiteboard.clearSelection();whiteboard.reset();d.dismiss();pages()}
                if(action==3)confirm(s("clear_confirm")){perform()}else perform()
            }})
            c.addView(r,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(4),0,dp(4))})
        }
    }

    private fun activateBoard(target:Board){
        if(board!==target){target.copyToolsFrom(board);board=target;closeSidePanel()}
        target.onObjectActions={editSelection()}
        target.onSmart={processSmart(it)}
        target.onMindAdd={node,sibling->mindNode(node,sibling)}
        dockSignature="";refreshDock()
    }
    private fun projectChanged(){
        dirty=true;status.text=s("saving")
        handler.removeCallbacks(autosave);handler.postDelayed(autosave,1800)
    }
    private fun syncPdfWorkspace(){
        if(!::workspaceHost.isInitialized)return
        val state=projectStore.lesson.pdf
        if(shownPdf===state)return
        shownPdf=state
        if(board!==whiteboard){whiteboard.copyToolsFrom(board);board=whiteboard}
        pdfPane?.dispose();pdfPane=null
        (whiteboard.parent as? ViewGroup)?.removeView(whiteboard)
        workspaceHost.removeAllViews()
        if(state==null){workspaceHost.addView(whiteboard,FrameLayout.LayoutParams(-1,-1));return}
        val reader=PdfPane(this,state,media,{board},{activateBoard(it)},{projectChanged()},{projectChanged()},{pdfMenu()})
        pdfPane=reader
        val split=PdfSplitLayout(this,state,whiteboard,reader){projectChanged()}
        workspaceHost.addView(split,FrameLayout.LayoutParams(-1,-1).apply{setMargins(dp(78),dp(76),dp(12),dp(76))})
    }
    private fun refreshPdfPresentation(){
        (pdfPane?.parent as? PdfSplitLayout)?.applyPresentation()
    }
    private fun choosePdfImport(){
        MaterialAlertDialogBuilder(this).setTitle(tr("Open PDF","بازکردن PDF"))
            .setItems(arrayOf(tr("PDF object on board","PDF به‌صورت شیء روی تخته"),tr("Scrollable PDF beside board","PDF قابل اسکرول کنار تخته"))){_,i->
                pendingPdfMode=i
                if(i==1)withPdfExit{pick("application/pdf",103)}else pick("application/pdf",103)
            }.show()
    }
    private fun openPdf(uri:android.net.Uri,flags:Int){
        try{contentResolver.takePersistableUriPermission(uri,flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))}catch(_:SecurityException){}
        val mode=pendingPdfMode
        work({
            val asset=media.import(requireNotNull(contentResolver.openInputStream(uri)),"pdf")
            try{
                if(mode==0){
                    val item=Item(kind="pdf",asset=asset,pageCount=media.pdfCount(asset),w=560f)
                    val image=requireNotNull(media.image(item,true));item.h=item.w*image.height/image.width
                    item to null
                }else{
                    val sizes=media.pdfSizes(asset)
                    val title=contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use{c->if(c.moveToFirst())c.getString(0)else "PDF"}?:"PDF"
                    val state=PdfWorkspaceState(asset=asset,sourceUri=uri.toString(),title=title.take(250),originalHash=PdfExport.hash(media.file(asset).inputStream()))
                    state.sheets=sizes.mapIndexed{i,(w,h)->
                        PdfSheet(w,h,Page(background="plain",items=mutableListOf(Item(kind="pdf",asset=asset,pdfPage=i,pageCount=sizes.size,x=0f,y=0f,w=w,h=h,locked=true))))
                    }.toMutableList()
                    null to state
                }
            }catch(e:Exception){media.file(asset).delete();throw e}
        }){(item,state)->
            if(item!=null){activateBoard(whiteboard);whiteboard.insert(item)}
            else if(state!=null){activateBoard(whiteboard);projectStore.editMetadata{projectStore.lesson.pdf=state};syncPdfWorkspace();projectChanged()}
        }
    }
    private fun pdfMenu(){
        val state=projectStore.lesson.pdf?:return
        val choices=arrayOf(tr("Save annotations to the original PDF","ذخیرهٔ یادداشت‌ها روی همان PDF"),tr("Save as a new PDF","ذخیره به‌صورت PDF جدید"),
            tr("Keep annotations in the project only","یادداشت‌ها فقط در پروژه بمانند"),tr("Hand / writing mode","حالت دست / نوشتن"),
            tr("Swap PDF and board sides","تعویض سمت PDF و تخته"),tr("PDF fullscreen / split","PDF تمام‌صفحه / کنار تخته"),
            tr("Fit PDF to width","نمایش PDF متناسب با عرض"),tr("Copy page or region to board","کپی صفحه یا بخشی از آن به تخته"),
            tr("Export board pages as PDF","خروجی PDF از صفحات تخته"),tr("Close PDF workspace","بستن بخش PDF"))
        MaterialAlertDialogBuilder(this).setTitle(state.title+if(state.unsaved)" •"else"").setItems(choices){_,i->when(i){
            0->savePdf(true)
            1->savePdf(false)
            2->{persist();toast(tr("Editable annotations are saved in the project","یادداشت‌های قابل‌ویرایش در پروژه ذخیره می‌شوند"))}
            3->{pdfPane?.toggleHand();toast(if(pdfPane?.hand==true)tr("Hand: drag to scroll","دست: برای اسکرول بکشید")else tr("Writing: use the selected tool","نوشتن: از ابزار انتخاب‌شده استفاده کنید"))}
            4->{state.onRight=!state.onRight;refreshPdfPresentation();projectChanged()}
            5->{state.fullscreen=!state.fullscreen;refreshPdfPresentation();projectChanged()}
            6->pdfPane?.fitWidth()
            7->copyPdfPage()
            8->choices(s("export_pdf"),listOf("current_page","all_pages")){n->export("pdf",n==1){createDocument(it,"application/pdf")}}
            9->withPdfExit{activateBoard(whiteboard);projectStore.editMetadata{projectStore.lesson.pdf=null}}
        }}.setNegativeButton(s("close"),null).show()
    }
    private fun withPdfExit(action:()->Unit){
        val state=projectStore.lesson.pdf
        if(state==null || !state.unsaved){action();return}
        MaterialAlertDialogBuilder(this).setTitle(tr("PDF has unsaved annotations","یادداشت‌های PDF ذخیره نشده‌اند"))
            .setMessage(tr("The project keeps editable notes. Choose whether to also save them into a PDF before leaving.","پروژه یادداشت‌های قابل‌ویرایش را نگه می‌دارد. انتخاب کنید قبل از خروج در PDF هم ذخیره شوند یا نه."))
            .setPositiveButton(tr("Save PDF","ذخیرهٔ PDF")){_,_->
                MaterialAlertDialogBuilder(this).setTitle(tr("Save PDF","ذخیرهٔ PDF")).setItems(arrayOf(tr("Original file","همان فایل"),tr("New file","فایل جدید"))){_,i->savePdf(i==0,action)}.show()
            }.setNegativeButton(tr("Leave without PDF save","خروج بدون ذخیره در PDF")){_,_->persist();action()}
            .setNeutralButton(tr("Continue editing","ادامهٔ کار"),null).show()
    }
    private fun savePdf(overwrite:Boolean,then:(()->Unit)?=null){
        val live=projectStore.lesson.pdf?:return
        if(board.isDrawing)return
        if(overwrite && live.sourceUri.isBlank()){
            MaterialAlertDialogBuilder(this).setTitle(tr("Original file is unavailable","فایل اصلی در دسترس نیست"))
                .setMessage(tr("This project was imported or its original file permission is unavailable. Save a new PDF.","این پروژه وارد شده یا دسترسی به فایل اصلی موجود نیست. یک PDF جدید ذخیره کنید."))
                .setPositiveButton(tr("Save new PDF","ذخیرهٔ PDF جدید")){_,_->savePdf(false,then)}.setNegativeButton(s("cancel"),null).show();return
        }
        val snapshot=live.deepCopy()
        work({
            val output=File(cacheDir,"exports/PDF-${System.currentTimeMillis()}.pdf")
            PdfExport.write(media,snapshot,output)
            if(overwrite){
                val uri=android.net.Uri.parse(snapshot.sourceUri)
                val backup=File(cacheDir,"exports/Original-${System.currentTimeMillis()}.pdf")
                requireNotNull(contentResolver.openInputStream(uri)).use{input->backup.outputStream().use{boundedCopy(input,it,128L*1024*1024)}}
                check(PdfExport.hash(backup.inputStream())==snapshot.originalHash){tr("The original PDF changed outside the app. Save a new file to keep both versions.","فایل اصلی بیرون از برنامه تغییر کرده؛ برای حفظ هر دو نسخه، فایل جدید ذخیره کنید.")}
                try{
                    requireNotNull(contentResolver.openOutputStream(uri,"wt")).use{out->output.inputStream().use{it.copyTo(out)};out.flush()}
                    check(PdfExport.hash(requireNotNull(contentResolver.openInputStream(uri)))==PdfExport.hash(output.inputStream())){"PDF write verification failed"}
                }catch(e:Exception){
                    try{requireNotNull(contentResolver.openOutputStream(uri,"wt")).use{out->backup.inputStream().use{it.copyTo(out)}}}catch(_:Exception){}
                    throw java.io.IOException(tr("Could not replace the original PDF. Recovery copies remain in the app; save a new PDF.","جایگزینی PDF اصلی انجام نشد. نسخه‌های بازیابی داخل برنامه حفظ شده‌اند؛ PDF جدید ذخیره کنید."),e)
                }
                output to PdfExport.hash(output.inputStream())
            }else output to ""
        }){(output,hash)->
            if(overwrite){live.savedRevision=snapshot.revision;live.originalHash=hash;projectChanged();toast(s("saved"));then?.invoke()}
            else{pendingPdfAsset=live.asset;pendingPdfRevision=snapshot.revision;afterPdfSave=then;createDocument(output,"application/pdf")}
        }
    }
    private fun copyPdfPage(){
        val state=projectStore.lesson.pdf?:return
        val sheet=state.sheets[state.current]
        fun copy(region:android.graphics.RectF){
            val page=sheet.page.copy(items=sheet.page.items.map{it.deepCopy()}.toMutableList())
            work({
                val scale=1920f/maxOf(sheet.width,sheet.height)
                val full=Bitmap.createBitmap((sheet.width*scale).toInt().coerceAtLeast(1),(sheet.height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
                try{
                    val canvas=Canvas(full);canvas.drawColor(Color.WHITE);canvas.scale(scale,scale);Renderer(media).scene(canvas,page,true)
                    val left=(region.left*full.width).toInt().coerceIn(0,full.width-1);val top=(region.top*full.height).toInt().coerceIn(0,full.height-1)
                    val w=((region.right-region.left)*full.width).toInt().coerceIn(1,full.width-left);val h=((region.bottom-region.top)*full.height).toInt().coerceIn(1,full.height-top)
                    val crop=Bitmap.createBitmap(full,left,top,w,h)
                    try{Item(kind="image",asset=media.save(crop),w=560f,h=560f*h/w)}finally{if(crop!==full)crop.recycle()}
                }finally{full.recycle()}
            }){item->activateBoard(whiteboard);state.fullscreen=false;refreshPdfPresentation();whiteboard.insert(item)}
        }
        MaterialAlertDialogBuilder(this).setTitle(tr("Copy PDF to board","کپی PDF به تخته"))
            .setItems(arrayOf(tr("Whole current page","تمام صفحهٔ فعلی"),tr("Select a region","انتخاب بخشی از صفحه"))){_,i->
                if(i==0)copy(RectF(0f,0f,1f,1f))
                else work({
                    val scale=1536f/maxOf(sheet.width,sheet.height)
                    val bitmap=Bitmap.createBitmap((sheet.width*scale).toInt().coerceAtLeast(1),(sheet.height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
                    val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE);canvas.scale(scale,scale)
                    Renderer(media).scene(canvas,sheet.page,true);bitmap
                }){bitmap->
                    val selector=PdfRegionSelector(this,bitmap)
                    MaterialAlertDialogBuilder(this).setTitle(tr("Drag a rectangle","یک مستطیل بکشید")).setView(selector)
                        .setPositiveButton(s("apply")){_,_->copy(RectF(selector.region))}.setNegativeButton(s("cancel"),null).show().setOnDismissListener{bitmap.recycle()}
                }
            }.show()
    }
    @Deprecated("Platform compatibility")
    override fun onBackPressed(){withPdfExit{persist();super.onBackPressed()}}

    private fun pick(mime: String, code: Int) {
        try {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = mime
                    if (code == 101) type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    if(code==103)addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
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
        if (resultCode != RESULT_OK) {afterPdfSave=null;return}
        if(requestCode==105){val f=File(cacheDir,"lab-snapshot.png");if(f.exists()){val bitmap=BitmapFactory.decodeFile(f.path);if(bitmap!=null)insertBitmap(bitmap);f.delete()};return}
        val uri = data?.data ?: return
        when (requestCode) {
            101 ->
                work({ files.read(requireNotNull(contentResolver.openInputStream(uri))) }) { doc ->
                    persist()
                    documentId = newId()
                    prefs.edit().putString("current", documentId).apply()
                    projectStore.replace(doc)
                    activateBoard(whiteboard)
                    whiteboard.clearSelection()
                    whiteboard.fit()
                }
            103 -> openPdf(uri,data?.flags?:0)
            102 ->
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
                    check(file.isFile && file.length()>0) { "Export is missing. Your internal lesson is preserved; please export again." }
                    requireNotNull(contentResolver.openOutputStream(uri, "wt")).use { out ->
                        val count=file.inputStream().use { it.copyTo(out) }
                        check(count==file.length() && count>0) { "Incomplete export" }
                        out.flush()
                    }
                }) {
                    toast(s("saved"))
                    pendingExport = null
                    prefs.edit().remove("pendingExport").apply()
                    projectStore.lesson.pdf?.let{state->if(state.asset==pendingPdfAsset && pendingPdfRevision!=null){state.savedRevision=pendingPdfRevision!!;projectChanged()}}
                    pendingPdfAsset=null;pendingPdfRevision=null
                    afterPdfSave?.let{afterPdfSave=null;it()}
                }
            }
        }
    }

    private fun exportChoice(ext: String) {
        if(ext=="pdf" && projectStore.lesson.pdf!=null){pdfMenu();return}
        if (ext == "pdf")
            choices(s("export_pdf"), listOf("current_page", "all_pages")) { i ->
                export(ext, i == 1) { createDocument(it, "application/pdf") }
            }
        else export(ext, false) { createDocument(it, "image/${if(ext=="jpg")"jpeg"else"png"}") }
    }

    private fun export(ext: String, all: Boolean, done: (File) -> Unit) {
        val doc = projectStore.lesson.copyForSave()
        work({
            val folder = File(cacheDir, "exports").apply { mkdirs() }
            val target = File(folder, "VuraVision-${System.currentTimeMillis()}.$ext")
            val renderer = Renderer(media)
            if (ext == "vura") files.atomic(doc, target)
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
                    check(bitmap.compress(
                        if (ext == "jpg") Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG,
                        92,
                        it,
                    )) { "Image export failed" }
                }
                bitmap.recycle()
            }
            check(target.isFile && target.length()>0){"Export is empty"}
            target
        }) {
            done(it)
        }
    }

    private fun createDocument(file: File, mime: String) {
        pendingExport = file
        prefs.edit().putString("pendingExport", file.name).apply()
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

    private fun shareOptions(){
        val choices=arrayOf(tr("Send PDF via another app / cloud","فرستادن PDF با برنامهٔ دیگر / فضای ابری"),
            tr("QR for a public HTTPS link","QR برای لینک عمومی HTTPS"),
            tr("QR on the same local network","QR روی شبکهٔ محلی"))
        MaterialAlertDialogBuilder(this).setTitle(s("share"))
            .setItems(choices){_,index->when(index){0->sharePdf();1->publicQr();else->share()}}
            .setNegativeButton(s("cancel"),null).show()
    }
    private fun sharePdf(){
        export("pdf",true){file->try{
            val uri=FileProvider.getUriForFile(this,"$packageName.files",file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{
                type="application/pdf";putExtra(Intent.EXTRA_STREAM,uri)
                clipData=ClipData.newRawUri(file.name,uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },s("share")))
        }catch(e:Exception){error(e)}}
    }
    private fun publicQr(){
        input(tr("Paste a public HTTPS link to your uploaded PDF","لینک عمومی HTTPS فایل PDF بارگذاری‌شده را وارد کنید")) {raw->
            val url=raw.trim();val uri=android.net.Uri.parse(url)
            require(uri.scheme=="https" && !uri.host.isNullOrBlank() && uri.authority?.contains('@')!=true){
                tr("Enter a valid public HTTPS address","نشانی معتبر و عمومی HTTPS وارد کنید")
            }
            val c=column().apply{pad(20);gravity=Gravity.CENTER}
            c.addView(label(tr("Anyone with access to this public link can open it from another network. Check sharing permissions in the cloud app.","هر کسی که به این لینک عمومی دسترسی داشته باشد می‌تواند از شبکهٔ دیگر آن را باز کند. مجوزهای اشتراک را در برنامهٔ ابری بررسی کنید."),14f,MUTED))
            c.addView(ImageView(this).apply{setImageBitmap(Sharing.qr(url));contentDescription=url},LinearLayout.LayoutParams(dp(230),dp(230)))
            c.addView(label(url,12f).apply{setTextIsSelectable(true)})
            c.addView(button(s("copy")){(getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("VuraVision",url));toast(s("done"))})
            dialog(s("share"),c)
        }
    }
    private fun share() {
        export("pdf", true) { file ->
            try {
                shareDialog?.dismiss()
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
                c.addView(button(s("copy")){(getSystemService(CLIPBOARD_SERVICE)as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("VuraVision",address.text));toast(s("done"))})
                if(urls.size>1)c.addView(button(s("network_address")){MaterialAlertDialogBuilder(this).setItems(urls.toTypedArray()){_,index->address.text=urls[index];qr.setImageBitmap(Sharing.qr(urls[index]));qr.contentDescription=urls[index]}.show()})
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
                d.setOnDismissListener{handler.removeCallbacks(update);server.stop();if(sharing===server)sharing=null;shareDialog=null}
            } catch (e: Exception) {
                error(e)
            }
        }
    }

    private var smartSource="auto"
    private fun smartSettings(){
        val c=column().apply{pad(20)}
        c.addView(label(tr("Select ink, then use the actions above it to recognize text, solve, convert units, or recognize shapes. Drawing a loop is not needed.","دست‌نویس را انتخاب کنید و از نوار بالای آن متن، حل، تبدیل واحد یا تشخیص شکل را بزنید. نیازی به دور کشیدن نیست."),14f,MUTED))
        val keys=listOf("auto","offline","en-US","fa")
        val source=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf(tr("Auto · English / Persian","خودکار · انگلیسی / فارسی"),tr("Offline Latin","لاتین آفلاین"),tr("English handwriting","دست‌خط انگلیسی"),tr("Persian handwriting","دست‌خط فارسی")));setSelection(keys.indexOf(smartSource).coerceAtLeast(0))};c.addView(source)
        val review=Switch(this).apply{text=tr("Review before applying","بررسی نتیجه قبل از اعمال");isChecked=prefs.getBoolean("smartReview",false)};c.addView(review)
        val d=dialog(s("smart"),c)
        d.setOnDismissListener{smartSource=keys[source.selectedItemPosition];prefs.edit().putString("smartSource",smartSource).putBoolean("smartReview",review.isChecked).apply()}
        c.addView(button(s("models")){d.dismiss();models()})
        c.addView(label(tr("Auto uses installed handwriting models and falls back to bundled Latin OCR. Install Persian and English models below when needed.","حالت خودکار از مدل‌های دست‌نویس نصب‌شده استفاده می‌کند و در غیر این صورت سراغ تشخیص لاتین همراه برنامه می‌رود. مدل‌های فارسی و انگلیسی را در صورت نیاز نصب کنید."),13f,MUTED))
    }
    private fun applySmart(original:List<Item>,value:String,mode:String){
        require(value.isNotBlank()){"No recognition result"}
        require(original.all{v->store.page.items.any{it===v} && store.page.editable(v) && !v.locked}){"Selection changed"}
        val b=contentBounds(original);val first=original.first();val size=if(first.kind=="text")first.width else b.height().coerceIn(18f,64f)
        val o=when(mode){
            "convert"->Item(kind="text",text=UnitConversion.result(value),width=size)
            "formula"->{val clean=value.trim();val expression=clean.trimEnd('=',' ');val answer=if(expression.contains('='))SmartMath.steps(expression)else SmartMath.result(expression);Item(kind="text",text=if(expression.contains('='))answer else (if(clean.endsWith('='))"" else "= ")+answer,width=size)}
            "graph"->{value.split(';').forEach{MathTools().compile(it)};Item(kind="graph",text=value,w=560f,h=400f)}
            else->Item(kind="text",text=value,width=size)
        }
        o.color=first.color;o.layerId=first.layerId;o.pane=first.pane;if(o.kind=="text")TextLayout.fit(o)
        o.x=if(mode=="formula")b.right+16 else b.left;o.y=if(mode in listOf("graph","convert"))b.bottom+20 else b.top
        if(mode=="formula" && o.text.contains('\n')){o.x=b.left;o.y=b.bottom+20}
        store.editMetadata{if(mode=="text")store.page.items.removeAll(original.toSet());store.page.items.add(o)}
        board.selected.clear();board.selected.add(o.id);refreshDock()
    }
    private fun smart(){smartSettings()}
    private fun processSmart(items:List<Item>){
        val chosen=items.filter{store.page.editable(it)&&!it.locked&&it.kind in listOf("ink","text","sticky")};if(chosen.isEmpty())return
        val pageId=store.page.id;val mode=board.smartMode
        fun reviewResult(values:List<String>){if(destroyed)return;status.text=s("ready");if(store.page.id!=pageId){toast(tr("Return to the original page and try again","به صفحهٔ اصلی برگردید و دوباره تلاش کنید"));return};if(values.isEmpty()||prefs.getBoolean("smartReview",false))reviewSmart(chosen,values,mode)else try{applySmart(chosen,values.first(),mode)}catch(e:Exception){toast(e.message?:s("error"));reviewSmart(chosen,values,mode)}}
        if(mode=="shape"){store.editMetadata{chosen.filter{it.kind=="ink"}.forEach{o->ShapeRecognition.convert(o)?.let{store.page.items.remove(o);it.layerId=o.layerId;it.pane=o.pane;store.page.items.add(it)}}};board.clearSelection();return}
        if(chosen.all{it.kind!="ink"}){reviewResult(listOf(chosen.joinToString("\n"){it.text}));return}
        val strokes=chosen.filter{it.kind=="ink"};status.text=s("busy")
        fun fallback(e:Exception){lastError=android.util.Log.getStackTraceString(e);status.text=s("error");toast(recognition.failure(this,e));reviewResult(emptyList())}
        if(smartSource=="auto"){
            fun latin()=OfflineText.recognize(chosen,board.renderer,::reviewResult,::fallback)
            fun english(persian:List<String>){recognition.installed("en-US",{ready->
                if(ready)recognition.recognize("en-US",strokes,{english->
                    val faText=persian.firstOrNull().orEmpty()
                    reviewResult(if(faText.any{it in '\u0600'..'\u06ff'})persian+english else english+persian)
                },{if(persian.isNotEmpty())reviewResult(persian)else latin()})
                else if(persian.isNotEmpty())reviewResult(persian)else latin()
            },{if(persian.isNotEmpty())reviewResult(persian)else latin()})}
            recognition.installed("fa",{ready->if(ready)recognition.recognize("fa",strokes,::english,{english(emptyList())})else english(emptyList())},{english(emptyList())})
        }else if(smartSource=="offline")OfflineText.recognize(chosen,board.renderer,::reviewResult,::fallback)
        else recognition.installed(smartSource,{installed->if(installed)recognition.recognize(smartSource,strokes,::reviewResult,::fallback)else{status.text=s("model_required");reviewResult(emptyList());toast(s("model_required"))}},::fallback)
    }
    private fun reviewSmart(original:List<Item>,candidates:List<String>,mode:String){
        val pageId=store.page.id;val c=column().apply{pad(20)}
        c.addView(label(if(candidates.isEmpty())tr("No reliable recognition. Enter or correct the content below; your original ink is preserved until you apply.","نتیجهٔ قابل‌اعتماد پیدا نشد. متن را وارد یا اصلاح کنید؛ تا زمان اعمال، دست‌نویس اصلی حفظ می‌شود.")else tr("Review the recognized content","متن تشخیص‌داده‌شده را بررسی کنید"),14f,MUTED))
        val text=field(candidates.firstOrNull().orEmpty()).apply{if(mode!="text"){layoutDirection=View.LAYOUT_DIRECTION_LTR;textDirection=View.TEXT_DIRECTION_LTR}};c.addView(text)
        val options=row();candidates.take(3).forEach{v->options.addView(button(v.take(40)){text.setText(v)})};c.addView(scrollRow(options))
        val result=label("",19f,TEAL,true).apply{setTextIsSelectable(true)};c.addView(result)
        val keep=CheckBox(this).apply{this.text=s("keep_ink");isChecked=mode!="text";isEnabled=mode=="text"};c.addView(keep)
        fun prepared():Item{val value=text.text.toString();require(value.isNotBlank()){s("empty")};return when(mode){
            "convert"->Item(kind="text",text=UnitConversion.result(value),width=30f).also{TextLayout.fit(it)}
            "formula"->Item(kind="text",text="$value\n${SmartMath.result(value)}",width=30f).also{TextLayout.fit(it)}
            "graph"->{require(value.split(';').size in 1..3);value.split(';').forEach{MathTools().compile(it)};Item(kind="graph",text=value,w=560f,h=400f)}
            else->Item(kind="text",text=value,width=30f).also{TextLayout.fit(it)}
        }}
        c.addView(button(tr("Preview result","پیش‌نمایش نتیجه")){try{val item=prepared();result.text=if(item.kind=="graph")tr("Function is valid — Apply to plot","تابع معتبر است — برای رسم، اعمال را بزنید")else item.text;text.error=null}catch(e:Exception){text.error=e.message;result.text=""}})
        val d=dialog(s("review"),ScrollView(this).apply{addView(c)})
        c.addView(button(s("apply"),true){try{
            require(store.page.id==pageId){"Page changed"}
            if(mode!="text" || !keep.isChecked)applySmart(original,text.text.toString(),mode)
            else {val item=prepared();val first=original.first();item.layerId=first.layerId;item.pane=first.pane;item.color=first.color;require(original.all{v->store.page.items.any{it===v}&&store.page.editable(v)&&!v.locked}){"Selection changed"};val bounds=contentBounds(original);item.x=bounds.left;item.y=bounds.bottom+20;store.editMetadata{store.page.items.add(item)};board.selected.clear();board.selected.add(item.id);refreshDock()}
            d.dismiss()
        }catch(e:Exception){text.error=e.message}})
    }

    private fun models(){
        val c=column().apply{pad(20)}
        c.addView(label(tr("Offline Latin OCR is already included","تشخیص لاتین آفلاین همراه برنامه نصب است"),18f,TEAL,true))
        c.addView(label(tr("No model download is needed for clear Latin characters and simple arithmetic. Google digital-ink models below are optional for handwriting; Persian still needs its language model.","برای حروف واضح لاتین و محاسبات ساده نیازی به دانلود نیست. مدل‌های زیر برای دست‌نویس گوگل‌اند؛ تشخیص دست‌نویس فارسی همچنان به مدل زبان نیاز دارد."),14f,MUTED))
        c.addView(label(tr("Model downloads need access to dl.google.com over HTTPS. If the connection is refused, try another network and check VPN, proxy or firewall settings, then Retry. Offline Latin OCR remains available.","دانلود مدل به دسترسی HTTPS به dl.google.com نیاز دارد. اگر اتصال رد شد، شبکهٔ دیگری را امتحان و تنظیمات VPN، پراکسی یا فایروال را بررسی کنید؛ سپس دوباره تلاش کنید. تشخیص لاتین آفلاین در دسترس است."),14f,MUTED))
        c.addView(button(tr("Network settings","تنظیمات شبکه")){try{startActivity(Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS))}catch(e:Exception){error(e)}})
        c.addView(button(tr("Use offline Latin OCR","استفاده از تشخیص لاتین آفلاین")){smartSource="offline";prefs.edit().putString("smartSource",smartSource).apply();toast(s("done"))})
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
            r.addView(button(tr("Retry download","تلاش مجدد")){if(!busy){recognition.installed(lang,{ready->if(ready)update()else download()},::failed)}})
            r.addView(button(s("check_status")){if(!busy)update()})
            r.addView(button(tr("Details","جزئیات")){val details="Model: $lang\nSDK: digital-ink 19.0.0\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}\nAPI: ${Build.VERSION.SDK_INT}\nLocale: ${Locale.getDefault()}\n\n${raw.ifBlank{tr("No error recorded","خطایی ثبت نشده است")}}";val body=column().apply{pad(16)};body.addView(label(details,12f).apply{setTextIsSelectable(true)});body.addView(button(s("copy")){(getSystemService(CLIPBOARD_SERVICE)as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("VuraVision diagnostics",details));toast(s("done"))});dialog(tr("Download diagnostics","گزارش دانلود"),ScrollView(this).apply{addView(body)})})
            update()
        }
        dialog(s("models"),ScrollView(this).apply{addView(c)})
    }

    private fun inputSettings(){
        val c=column().apply{pad(16)}
        c.addView(Switch(this).apply{text=tr("Pen + touch (off: two tips)","قلم + لمس (خاموش: دو سر قلم)");isChecked=board.touchMode;setOnCheckedChangeListener{_,v->if(!board.isDrawing){board.touchMode=v;prefs.edit().putBoolean("touchMode",v).apply();dockSignature="";refreshDock()}}})
        c.addView(Switch(this).apply{text=s("multi_touch_short");isChecked=board.profile.multiTouch;setOnCheckedChangeListener{_,v->if(!board.isDrawing){board.profile.multiTouch=v;prefs.edit().putBoolean("multiTouch",v).apply()}}})
        dialog(s("input_controls"),c)
    }

    private fun settings() {
        val keys=mutableListOf("language","fonts","voice_assistant","ui_size","models","input_controls","help","cache","about")
        // Engineering is intentionally only reachable through the hidden guide gesture.
        choices(s("settings"),keys){index->when(keys[index]){
            "language"->choices(s("language"),listOf("english","persian")){i->persist();prefs.edit().putString("language",if(i==0)"en"else"fa").apply();worker.execute{handler.post{if(!destroyed)recreate()}}}
            "fonts"->fontSettings()
            "voice_assistant"->voiceSettings()
            "ui_size"->MaterialAlertDialogBuilder(this).setTitle(tr("Interface size","اندازهٔ رابط کاربری"))
                .setSingleChoiceItems(arrayOf(tr("Small","کوچک"),tr("Medium","متوسط"),tr("Large","بزرگ")),
                    listOf(.85f,1f,1.2f).indexOf(prefs.getFloat("uiScale",1f)).coerceAtLeast(0)){d,i->
                    persist();prefs.edit().putFloat("uiScale",listOf(.85f,1f,1.2f)[i]).apply();d.dismiss();worker.execute{handler.post{if(!destroyed)recreate()}}
                }.show()
            "models"->models()
            "input_controls"->inputSettings()
            "help"->quickGuide()
            "cache"->{media.clear();board.sceneChanged();toast(s("done"))}
            "about"->{val c=column().apply{pad(20)};c.addView(ImageView(this).apply{setImageResource(R.drawable.vura_brand);scaleType=ImageView.ScaleType.FIT_CENTER},LinearLayout.LayoutParams(-1,dp(150)));c.addView(label("VuraVision ${BuildConfig.VERSION_NAME}\nBeyond Vision\n\n${s("about_text")}",16f).apply{setOnClickListener{taps++;if(taps>=7){prefs.edit().putBoolean("engineering",true).apply();toast(s("unlocked"))}}});dialog(s("about"),c)}
            "engineering"->engineering()
        }}
    }

    private fun fontSettings(){
        val content=column().apply{pad(16)}
        content.addView(label(tr("Choose independent fonts for Persian and English. New text, sticky notes and smart conversion keep these fonts in the project and PDF exports.","فونت فارسی و انگلیسی را جدا انتخاب کنید. متن تازه، یادداشت و تبدیل هوشمند این فونت‌ها را در پروژه و خروجی PDF حفظ می‌کنند."),14f,MUTED))
        var fa=Fonts.persianId;var en=Fonts.englishId
        val faChoice=button(""){};val enChoice=button(""){}
        val preview=label("نمونهٔ فارسی: یادگیری با ویوراویژن\nEnglish: Learning with VuraVision\n۱۲۳۴۵۶۷۸۹۰ · 1234567890",21f)
        fun refreshPreview(){
            faChoice.text=tr("Persian","فارسی")+": "+Fonts.persian.first{it.id==fa}.let{tr(it.en,it.fa)}
            enChoice.text=tr("English","انگلیسی")+": "+Fonts.english.first{it.id==en}.let{tr(it.en,it.fa)}
            Fonts.bind(preview,fontFa=fa,fontEn=en)
        }
        fun choose(families:List<Fonts.Family>,persian:Boolean){
            val list=column().apply{pad(8)}
            val picker=dialog(tr("Choose font","انتخاب فونت"),ScrollView(this).apply{addView(list)})
            families.forEach{family->
                val row=column().apply{pad(10)}
                row.addView(label(tr(family.en,family.fa),15f,NAVY,true))
                val sample=label(if(persian)"یادگیری، خلاقیت و نوشتن ۱۲۳"else"Learning, creativity and writing 123",22f)
                // Preview each row with that family's face, not the current global choice.
                Fonts.bind(sample,fontFa=if(persian)family.id else fa,fontEn=if(persian)en else family.id)
                row.addView(sample);row.setOnClickListener{if(persian)fa=family.id else en=family.id;refreshPreview();picker.dismiss()}
                list.addView(row,LinearLayout.LayoutParams(-1,-2))
            }
        }
        faChoice.setOnClickListener{choose(Fonts.persian,true)};enChoice.setOnClickListener{choose(Fonts.english,false)}
        content.addView(faChoice);content.addView(enChoice);content.addView(preview)
        val existing=CheckBox(this).apply{text=tr("Also apply to editable text on the active page (Undo available)","روی متن‌های قابل‌ویرایش صفحهٔ فعال هم اعمال شود (قابل بازگردانی)")}
        content.addView(existing)
        val dialog=dialog(s("fonts"),ScrollView(this).apply{addView(content)})
        refreshPreview()
        content.addView(button(s("apply"),true){
            if(board.isDrawing)return@button
            Fonts.choose(this,fa,en)
            if(existing.isChecked){
                val items=store.page.items.filter{it.kind in listOf("text","sticky") && !it.locked && store.page.editable(it)}
                if(items.isNotEmpty())store.editMetadata{items.forEach{item->val w=item.w;val h=item.h;Fonts.stamp(item,true);TextLayout.fit(item);if(item.kind=="sticky"){item.w=maxOf(w,item.w);item.h=maxOf(h,item.h)}}}
            }
            Fonts.applyTree(window.decorView);whiteboard.sceneChanged();pdfPane?.refreshText()
            dialog.dismiss();toast(s("saved"))
        })
    }

    private fun engineering() {
        choices(s("engineering"), listOf("touch_test", "thresholds", "report", "stress", "advanced_board", "secret_studio", "maboox", "voice_behavior")) {
            when (it) {
                4 -> advancedBoard()
                5 -> secretStudio()
                6 -> maboox()
                7 -> VoiceSettingsUi(this).advanced{stopVoice()}
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
                    if(board.isDrawing)return@choices
                    confirm(tr("Add 300 test strokes? Undo removes them.","۳۰۰ خط آزمایشی اضافه شود؟ با بازگردانی حذف می‌شوند.")) {
                    store.editMetadata {
                        repeat(300) { n ->
                            store.page.items.add(
                                Item(
                                    layerId = store.page.activeLayerId,
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
    }

    private fun advancedBoard(){
        if(board.isDrawing){toast(tr("Finish the stroke first","ابتدا رسم را تمام کنید"));return}
        val c=column().apply{pad(20)}
        c.addView(label(tr("Behind the board","پشت پردهٔ تخته"),22f,NAVY,true))
        fun toggle(en:String,fa:String,initial:Boolean,change:(Boolean)->Unit){
            c.addView(Switch(this).apply{text=tr(en,fa);isChecked=initial;setOnCheckedChangeListener{_,v->change(v)}})
        }
        toggle("Hold to recognize geometry","تشخیص هندسی با مکث",board.holdRecognitionEnabled){v->board.holdRecognitionEnabled=v;prefs.edit().putBoolean("holdRecognition",v).apply()}
        val delayLabel=label("");c.addView(delayLabel)
        fun delayText(){delayLabel.text=tr("Hold time: ${board.holdDelayMillis} ms","زمان مکث: ${board.holdDelayMillis} میلی‌ثانیه")}
        delayText()
        c.addView(SeekBar(this).apply{max=20;progress=((board.holdDelayMillis-500)/100).toInt();contentDescription=tr("Shape hold time","زمان مکث تشخیص شکل");setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onStartTrackingTouch(v:SeekBar?){}
            override fun onStopTrackingTouch(v:SeekBar?){}
            override fun onProgressChanged(v:SeekBar?,n:Int,user:Boolean){if(user){board.holdDelayMillis=500L+n*100L;prefs.edit().putLong("holdDelay",board.holdDelayMillis).apply();delayText()}}
        })})
        val toleranceLabel=label("");c.addView(toleranceLabel)
        fun toleranceText(){toleranceLabel.text=tr("Stationary pen tolerance: ${board.holdTolerance.toInt()}","حساسیت ثابت‌ماندن قلم: ${board.holdTolerance.toInt()}")}
        toleranceText()
        c.addView(SeekBar(this).apply{max=10;progress=board.holdTolerance.toInt()-2;contentDescription=tr("Stationary pen tolerance","حساسیت ثابت‌ماندن قلم");setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onStartTrackingTouch(v:SeekBar?){}
            override fun onStopTrackingTouch(v:SeekBar?){}
            override fun onProgressChanged(v:SeekBar?,n:Int,user:Boolean){if(user){board.holdTolerance=(n+2).toFloat();prefs.edit().putFloat("holdTolerance",board.holdTolerance).apply();toleranceText()}}
        })})
        toggle("Snap pen to ruler / compass","اتصال قلم به خط‌کش و پرگار",board.guideSnapEnabled){v->board.guideSnapEnabled=v;prefs.edit().putBoolean("guideSnap",v).apply()}
        toggle("Reject palm instead of erasing","نادیده‌گرفتن کف دست به‌جای پاک‌کردن",!board.profile.palmErase){v->board.profile.palmErase=!v;prefs.edit().putBoolean("palmErase",!v).apply()}
        c.addView(label(tr("Lower hold time responds faster. A larger tolerance accepts more small hand movements. These settings are saved on this device.","زمان مکث کمتر، تشخیص سریع‌تر می‌دهد. مقدار حساسیت بزرگ‌تر، لرزش بیشتری را می‌پذیرد. تنظیمات روی همین دستگاه ذخیره می‌شوند."),14f,MUTED))
        c.addView(button(tr("Reset advanced settings","بازنشانی تنظیمات پیشرفته")){
            board.holdRecognitionEnabled=true;board.holdDelayMillis=1000L;board.holdTolerance=4f;board.guideSnapEnabled=true
            prefs.edit().remove("holdRecognition").remove("holdDelay").remove("holdTolerance").remove("guideSnap").apply()
            toast(tr("Defaults restored; reopen this panel to refresh controls","مقادیر پیش‌فرض برگشت؛ برای نمایش مقادیر تازه پنل را دوباره باز کنید"))
        })
        dialog(tr("Behind the board","پشت پردهٔ تخته"),ScrollView(this).apply{addView(c)})
    }
    private fun secretStudio(){
        val c=column().apply{pad(20)}
        c.addView(label(tr("The secret studio","آتلیهٔ مخفی"),24f,NAVY,true))
        c.addView(label(tr("Watch portraits drawn stroke by stroke with the board's own pens. Each drawing opens on a new page and can be edited, erased, saved or exported. Undo removes the new page. These are original stylized illustrations.","پرتره‌ها با قلم خود تخته، خط‌به‌خط رسم می‌شوند. هر نقاشی در صفحهٔ تازه باز می‌شود و قابل ویرایش، پاک‌کردن، ذخیره و خروجی است. بازگردانی، صفحهٔ نقاشی را حذف می‌کند. این‌ها تصویرسازی‌های گرافیکی اختصاصی‌اند."),15f,MUTED))
        val d=dialog(tr("The secret studio","آتلیهٔ مخفی"),ScrollView(this).apply{addView(c)})
        listOf("mona-vura" to tr("Mona × Vura · Renaissance remix","مونا × ویورا · برداشت رنسانسی"),"aurora" to tr("Aurora · anime portrait","آرورا · پرترهٔ انیمه")).forEach{(key,title)->
            c.addView(button(title){
                if(board.isDrawing){toast(tr("Finish the stroke first","ابتدا رسم را تمام کنید"));return@button}
                try{val artwork=Artwork.load(this,key);d.dismiss();artPlayer?.stop();artPlayer=ArtPlayer(this,board,store,artwork).also{it.start()}}catch(e:Exception){error(e)}
            })
        }
        c.addView(button(tr("A tiny surprise: constellation","سورپرایز کوچک: صورت فلکی")){
            if(board.isDrawing)return@button
            d.dismiss();val page=Page(background="plain",panes=mutableListOf(Pane(background=0xff152538.toInt())))
            val points=listOf(90f to 220f,210f to 140f,325f to 235f,425f to 150f,510f to 265f,410f to 360f,250f to 380f)
            store.editMetadata{
                store.lesson.pages.add(page);store.lesson.current=store.lesson.pages.lastIndex
                points.zipWithNext().forEach{(a,b)->page.items.add(Item(kind="ink",color=0xff97bdcc.toInt(),width=2f,w=600f,h=460f,inkW=600f,inkH=460f,points=mutableListOf(Point(a.first,a.second),Point(b.first,b.second))))}
                points.forEach{(x,y)->page.items.add(Item(kind="shape",shape="star",x=x-12,y=y-12,w=24f,h=24f,color=0xffffcc80.toInt(),width=2f))}
                page.items.add(Item(kind="text",text="Beyond Vision",x=180f,y=435f,w=300f,h=60f,color=Color.WHITE,width=24f))
            };board.reset();board.clearSelection();board.fit()
        })
        c.addView(label("Powered by Maboox",14f,TEAL,true))
    }
    private fun maboox(){
        val c=column().apply{pad(24)}
        c.addView(label("Powered by Maboox",25f,NAVY,true))
        c.addView(label(tr("A little craft behind every classroom.","کمی هنر، پشت هر کلاس."),16f,MUTED))
        c.addView(label("maboox@yahoo.com",18f,TEAL).apply{setTextIsSelectable(true);textDirection=View.TEXT_DIRECTION_LTR})
        c.addView(button(tr("Copy email","کپی ایمیل")){
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Maboox","maboox@yahoo.com"));toast(tr("Email copied","ایمیل کپی شد"))
        })
        dialog("Maboox",c)
    }

    private fun classroomTools() {
        val keys=listOf("timer","stopwatch","dice","scoreboard")+GeometryTools.keys+"mind_map"
        choices(s("tools"),keys){i->val key=keys[i];when{
            key=="mind_map"->mindNode(null)
            key in GeometryTools.keys->board.insert(Item(kind="shape",shape=key,w=if(key=="compass")120f else 360f,h=when(key){"ruler"->64f;"set_square","protractor"->180f;else->180f},color=board.penColor))
            else->floatingTools.open(key)
        }}
    }
    private fun mindNode(parent:Item?,sibling:Boolean=false){
        if(parent!=null&&(parent.locked||!store.page.editable(parent)))return
        if(parent==null && !store.page.canDraw()){toast(tr("Select an unlocked visible layer","یک لایهٔ نمایان و باز انتخاب کنید"));return}
        fun node(x:Float,y:Float,parentId:String="",layer:String=store.page.activeLayerId,pane:Int=board.activePane)=
            Item(kind="sticky",shape="mindnode",width=24f,w=180f,h=120f,x=x,y=y,
                noteColor=0xffe2f2f0.toInt(),parentNode=parentId,layerId=layer,pane=pane)
        val added=if(parent==null){
            val center=board.center();val root=node(center.x-200,center.y-60)
            listOf(root,node(root.x+root.w+90,root.y-85,root.id),node(root.x+root.w+90,root.y+85,root.id))
        }else{
            val parentId=if(sibling)parent.parentNode else parent.id
            val peers=store.page.items.filter{it.shape=="mindnode" && it.parentNode==parentId && it.pane==parent.pane}
            val x=if(sibling)parent.x else parent.x+parent.w+90
            val y=if(sibling)maxOf(parent.y+parent.h+24,peers.maxOfOrNull{it.y+it.h+24}?:parent.y)
                  else maxOf(parent.y,peers.maxOfOrNull{it.y+it.h+24}?:parent.y)
            listOf(node(x,y,parentId,parent.layerId,parent.pane))
        }
        store.editMetadata{store.page.items.addAll(added)}
        board.selected.clear();board.selected.add(added.first().id);board.tool="select";board.sceneChanged();refreshDock()
    }

    private fun openExplorer(lab:Boolean){
        if(lab)Labs.show(this){insertBitmap(it)}else Games.show(this)
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

    override fun onPause() {
        stopVoice()
        super.onPause()
    }

    override fun dispatchTouchEvent(event:MotionEvent):Boolean {
        lastUserTouch=SystemClock.uptimeMillis()
        return super.dispatchTouchEvent(event)
    }

    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==voicePermissionRequest){
            if(grantResults.firstOrNull()==android.content.pm.PackageManager.PERMISSION_GRANTED)toggleVoice()
            else toast(s("voice_permission_needed"))
        }
    }

    private fun voiceSettings(){
        VoiceSettingsUi(this).connection(voiceKeys,voiceWorker){stopVoice()}
    }
    private fun toggleVoice(){
        if(voiceStarting || voiceAssistant?.active==true){stopVoice();return}
        if(!voiceKeys.hasKey()){voiceSettings();return}
        if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO),voicePermissionRequest);return
        }
        val settings=VoiceSettings.load(this)
        val generation=++voiceStartGeneration
        voiceStarting=true;setVoiceState("voice_connecting")
        voiceWorker.execute{
            try{
                val key=voiceKeys.read();val knowledge=VoiceKnowledge.load(this)
                handler.post{
                    if(destroyed || !voiceStarting || generation!=voiceStartGeneration)return@post
                    voiceStarting=false
                    val assistant=VoiceAssistant(settings,key,knowledge,VoiceAudio(this),{value->setVoiceState(value)},{reason->
                        voiceScreen?.close();voiceScreen=null;voiceAssistant=null;setVoiceState("voice_assistant")
                        if(reason!=null)toast(s(reason))
                    },connected={voiceScreen?.refresh()})
                    voiceAssistant=assistant
                    voiceScreen=VoiceScreen(this,workspaceHost,settings.frameSeconds,{assistant.active && assistant.ready},
                        {board.isDrawing || SystemClock.uptimeMillis()-lastUserTouch<500},assistant::sendImage,{if(assistant.active)assistant.stop("voice_vision_error")})
                    assistant.start();voiceScreen?.start()
                }
            }catch(_:Exception){handler.post{if(!destroyed && voiceStarting && generation==voiceStartGeneration){voiceStarting=false;setVoiceState("voice_assistant");toast(s("voice_key_storage_error"))}}}
        }
    }
    private fun setVoiceState(value:String){
        if(!::voiceButton.isInitialized)return
        voiceButton.text=s(value)
        val active=value!="voice_assistant"
        voiceButton.setIconResource(if(active)R.drawable.feather_square else R.drawable.feather_mic)
        voiceButton.contentDescription=if(active)s(value)+". "+s("voice_stop")else s("voice_assistant")
        voiceButton.backgroundTintList=android.content.res.ColorStateList.valueOf(if(active)PRIMARY_CONTAINER else SURFACE)
    }
    private fun stopVoice(){
        ++voiceStartGeneration
        voiceStarting=false
        voiceScreen?.close();voiceScreen=null
        voiceAssistant?.stop();voiceAssistant=null
        setVoiceState("voice_assistant")
    }

    override fun onStop() {
        artPlayer?.pause()
        handler.removeCallbacks(autosave)
        persist()
        super.onStop()
    }

    override fun onDestroy() {
        destroyed = true
        stopVoice()
        voiceWorker.shutdown()
        artPlayer?.stop()
        floatingTools.closeAll()
        handler.removeCallbacksAndMessages(null)
        sharing?.stop()
        shareDialog?.dismiss()
        worker.shutdown()
        pdfPane?.dispose()
        media.close()
        super.onDestroy()
    }
}
